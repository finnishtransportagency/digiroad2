package fi.liikennevirasto.digiroad2

import com.github.tototoshi.slick.MySQLJodaSupport._
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration.{DateTimePropertyFormat => DateTimeFormat}
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.slick.jdbc.{StaticQuery => Q}

case class NumericalLimitLink(id: Long, mmlId: Long, sideCode: Int, value: Option[Int], points: Seq[Point],
                              position: Option[Int] = None, towardsLinkChain: Option[Boolean] = None, expired: Boolean = false)

case class NumericalLimit(id: Long, value: Option[Int], expired: Boolean, endpoints: Set[Point],
                       modifiedBy: Option[String], modifiedDateTime: Option[String],
                       createdBy: Option[String], createdDateTime: Option[String],
                       numericalLimitLinks: Seq[NumericalLimitLink], typeId: Int)

trait NumericalLimitOperations {
  import GeometryDirection._

  val valuePropertyId: String = "mittarajoitus"

  def withDynTransaction[T](f: => T): T
  def roadLinkService: RoadLinkService = RoadLinkService

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  private def getLinksWithPositions(links: Seq[NumericalLimitLink]): Seq[NumericalLimitLink] = {
    def getLinkEndpoints(link: NumericalLimitLink): (Point, Point) = GeometryUtils.geometryEndpoints(link.points)
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val rawLink = chainedLink.rawLink
      val towardsLinkChain = chainedLink.geometryDirection match {
        case TowardsLinkChain => true
        case AgainstLinkChain => false
      }
      rawLink.copy(position = Some(chainedLink.linkPosition), towardsLinkChain = Some(towardsLinkChain))
    }
  }

  private def numericalLimitLinksById(id: Long): Seq[(Long, Long, Int, Option[Int], Seq[Point], Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)] = {
    val numericalLimits = sql"""
      select a.id, pos.mml_id, pos.side_code, s.value as value, pos.start_measure, pos.end_measure,
             a.modified_by, a.modified_date, a.created_by, a.created_date, case when a.valid_to <= sysdate then 1 else 0 end as expired,
             a.asset_type_id
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join property p on p.public_id = $valuePropertyId
        left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
        where a.id = $id
      """.as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)].list

    numericalLimits.map { case (segmentId, mmlId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) =>
      val roadLink = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      val points = GeometryUtils.truncateGeometry(roadLink.geometry, startMeasure, endMeasure)
      (segmentId, mmlId, sideCode, value, points, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId)
    }
  }

  def calculateEndPoints(links: List[(Point, Point)]): Set[Point] = {
    val endPoints = LinkChain(links, identity[(Point, Point)]).endPoints()
    Set(endPoints._1, endPoints._2)
  }

  private def fetchNumericalLimitsByMmlIds(assetTypeId: Int, mmlIds: Seq[Long]) = {
    MassQuery.withIds(mmlIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.mml_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.mml_id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)""".as[(Long, Long, Int, Int, Double, Double)].list
    }
  }

  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[NumericalLimitLink] = {
    withDynTransaction {
      val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
      val mmlIds = roadLinks.map(_.mmlId).toList

      val numericalLimits = fetchNumericalLimitsByMmlIds(typeId, mmlIds)

      val linkGeometries: Map[Long, (Seq[Point], Double, AdministrativeClass, Int)] =
        roadLinks.foldLeft(Map.empty[Long, (Seq[Point], Double, AdministrativeClass, Int)]) { (acc, roadLink) =>
          acc + (roadLink.mmlId -> (roadLink.geometry, roadLink.length, roadLink.administrativeClass, roadLink.functionalClass))
        }

      val numericalLimitsWithGeometry: Seq[NumericalLimitLink] = numericalLimits.map { link =>
        // Value is extracted separately since Scala does an implicit conversion from null to 0 in case of Ints
        val (assetId, mmlId, sideCode, _, startMeasure, endMeasure) = link
        val value = Option(link._4)
        val geometry = GeometryUtils.truncateGeometry(linkGeometries(mmlId)._1, startMeasure, endMeasure)
        NumericalLimitLink(assetId, mmlId, sideCode, value, geometry)
      }

      numericalLimitsWithGeometry.groupBy(_.id).mapValues(getLinksWithPositions).values.flatten.toSeq
    }
  }

  def getByMunicipality(typeId: Int, municipality: Int): (List[(Long, Long, Int, Int, Double, Double)], Map[Long, Seq[Point]]) = {
    withDynTransaction {
      val roadLinks = roadLinkService.fetchVVHRoadlinks(municipality)
      val mmlIds = roadLinks.map(_.mmlId).toList

      val numericalLimits = fetchNumericalLimitsByMmlIds(typeId, mmlIds)

      val linkGeometries: Map[Long, Seq[Point]] =
        roadLinks.foldLeft(Map.empty[Long, Seq[Point]]) { (acc, roadLink) =>
          acc + (roadLink.mmlId -> roadLink.geometry)
        }

      (numericalLimits, linkGeometries)
    }
  }

  private def getByIdWithoutTransaction(id: Long): Option[NumericalLimit] = {
    val links = numericalLimitLinksById(id)
    if (links.isEmpty) None
    else {
      val linkEndpoints: List[(Point, Point)] = links.map { link => GeometryUtils.geometryEndpoints(link._5) }.toList
      val limitEndpoints = calculateEndPoints(linkEndpoints)
      val head = links.head
      val (_, _, _, value, _, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) = head
      val numericalLimitLinks = links.map { case (_, mmlId, sideCode, _, points, _, _, _, _, _, _) =>
        NumericalLimitLink(id, mmlId, sideCode, value, points, None, None, expired)
      }
      Some(NumericalLimit(
            id, value, expired, limitEndpoints,
            modifiedBy, modifiedAt.map(DateTimeFormat.print),
            createdBy, createdAt.map(DateTimeFormat.print),
            getLinksWithPositions(numericalLimitLinks), typeId))
    }
  }

  def getById(id: Long): Option[NumericalLimit] = {
    withDynTransaction {
      getByIdWithoutTransaction(id)
    }
  }

  private def updateNumberProperty(assetId: Long, propertyId: Long, value: Int): Int =
    sqlu"update number_property_value set value = $value where asset_id = $assetId and property_id = $propertyId".first

  private def updateNumericalLimitValue(id: Long, value: Int, username: String): Option[Long] = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = updateNumberProperty(id, propertyId, value)
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  private def updateNumericalLimitExpiration(id: Long, expired: Boolean, username: String) = {
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = if (expired) {
      sqlu"update asset set valid_to = sysdate where id = $id".first
    } else {
      sqlu"update asset set valid_to = null where id = $id".first
    }
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  def updateNumericalLimit(id: Long, value: Option[Int], expired: Boolean, username: String): Option[Long] = {
    withDynTransaction {
      val valueUpdate: Option[Long] = value.flatMap(updateNumericalLimitValue(id, _, username))
      val expirationUpdate: Option[Long] = updateNumericalLimitExpiration(id, expired, username)
      val updatedId = valueUpdate.orElse(expirationUpdate)
      if (updatedId.isEmpty) dynamicSession.rollback()
      updatedId
    }
  }

  private def insertNumericalLimitValue(assetId: Long)(value: Int) = {
    val numberPropertyValueId = Sequences.nextPrimaryKeySeqValue
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
     sqlu"""
       insert into number_property_value(id, asset_id, property_id, value)
       values ($numberPropertyValueId, $assetId, $propertyId, $value)
     """.execute
  }

  private def createNumericalLimitWithoutTransaction(typeId: Int, mmlId: Long, value: Option[Int], expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): NumericalLimit = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if(expired) "sysdate" else "null"
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to)
        values ($id, $typeId, $username, sysdate, #$validTo)

        into lrm_position(id, start_measure, end_measure, mml_id, side_code)
        values ($lrmPositionId, $startMeasure, $endMeasure, $mmlId, $sideCode)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute

    value.foreach(insertNumericalLimitValue(id))

    getByIdWithoutTransaction(id).get
  }

  def createNumericalLimit(typeId: Int, mmlId: Long, value: Option[Int], username: String, municipalityValidation: Int => Unit): NumericalLimit = {
    val sideCode = 1
    val startMeasure = 0
    val expired = false
    val roadLink = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    municipalityValidation(roadLink.municipalityCode)
    withDynTransaction {
      createNumericalLimitWithoutTransaction(typeId, roadLink.mmlId, value, expired, sideCode, startMeasure, GeometryUtils.geometryLength(roadLink.geometry), username)
    }
  }

  def split(id: Long, mmlId: Long, splitMeasure: Double, value: Option[Int], expired: Boolean, username: String, municipalityValidation: Int => Unit): Seq[NumericalLimit] = {
    val roadLink = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    municipalityValidation(roadLink.municipalityCode)

    val limit: NumericalLimit = getById(id).get
    val createdId = withDynTransaction {
      Queries.updateAssetModified(id, username).execute
      val (startMeasure, endMeasure, sideCode) = OracleLinearAssetDao.getLinkGeometryData(id)
      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (startMeasure, endMeasure))

      OracleLinearAssetDao.updateMValues(id, existingLinkMeasures)
      createNumericalLimitWithoutTransaction(limit.typeId, mmlId, value, expired, sideCode.value, createdLinkMeasures._1, createdLinkMeasures._2, username).id
    }
    Seq(getById(id).get, getById(createdId).get)
  }
}

class NumericalLimitService(roadLinkServiceImpl: RoadLinkService) extends NumericalLimitOperations {
  def withDynTransaction[T](f: => T): T = Database.forDataSource(dataSource).withDynTransaction(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
}
