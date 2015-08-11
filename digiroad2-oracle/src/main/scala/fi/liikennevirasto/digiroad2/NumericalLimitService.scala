package fi.liikennevirasto.digiroad2

import com.github.tototoshi.slick.MySQLJodaSupport._
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration.{DateTimePropertyFormat => DateTimeFormat}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.bonecpToInternalConnection
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

case class NumericalLimitLink(id: Long, roadLinkId: Long, sideCode: Int, value: Int, points: Seq[Point], position: Option[Int] = None, towardsLinkChain: Option[Boolean] = None, expired: Boolean = false)
case class NumericalLimit(id: Long, value: Int, expired: Boolean, endpoints: Set[Point],
                       modifiedBy: Option[String], modifiedDateTime: Option[String],
                       createdBy: Option[String], createdDateTime: Option[String],
                       numericalLimitLinks: Seq[NumericalLimitLink], typeId: Int)

trait NumericalLimitOperations {
  import GeometryDirection._

  val valuePropertyId: String = "mittarajoitus"

  def withDynTransaction[T](f: => T): T

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

  private def numericalLimitLinksById(id: Long): Seq[(Long, Long, Int, Int, Seq[Point], Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)] = {
    val numericalLimits = sql"""
      select a.id, pos.road_link_id, pos.side_code, s.value as value, pos.start_measure, pos.end_measure,
             a.modified_by, a.modified_date, a.created_by, a.created_date, case when a.valid_to <= sysdate then 1 else 0 end as expired,
             a.asset_type_id
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join property p on p.public_id = $valuePropertyId
        join number_property_value s on s.asset_id = a.id and s.property_id = p.id
        where a.id = $id
      """.as[(Long, Long, Int, Int, Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)].list

    numericalLimits.map { case (segmentId, roadLinkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) =>
      val points = RoadLinkService.getRoadLinkGeometry(roadLinkId, startMeasure, endMeasure)
      (segmentId, roadLinkId, sideCode, value, points, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId)
    }
  }

  def calculateEndPoints(links: List[(Point, Point)]): Set[Point] = {
    val endPoints = LinkChain(links, identity[(Point, Point)]).endPoints()
    Set(endPoints._1, endPoints._2)
  }

  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[NumericalLimitLink] = {
    withDynTransaction {
      val roadLinks = RoadLinkService.getRoadLinks(bounds, municipalities)
      val roadLinkIds = roadLinks.map(_.id).toList

      val numericalLimits = OracleArray.fetchNumericalLimitsByRoadLinkIds(roadLinkIds, typeId, valuePropertyId, bonecpToInternalConnection(dynamicSession.conn))

      val linkGeometries: Map[Long, (Seq[Point], Double, AdministrativeClass, Int)] =
      roadLinks.foldLeft(Map.empty[Long, (Seq[Point], Double, AdministrativeClass, Int)]) { (acc, roadLink) =>
          acc + (roadLink.id -> (roadLink.geometry, roadLink.length, roadLink.administrativeClass, roadLink.functionalClass))
        }

      val numericalLimitsWithGeometry: Seq[NumericalLimitLink] = numericalLimits.map { link =>
        val (assetId, roadLinkId, _, sideCode, value, startMeasure, endMeasure) = link
        val geometry = GeometryUtils.truncateGeometry(linkGeometries(roadLinkId)._1, startMeasure, endMeasure)
        NumericalLimitLink(assetId, roadLinkId, sideCode, value, geometry)
      }

      numericalLimitsWithGeometry.groupBy(_.id).mapValues(getLinksWithPositions).values.flatten.toSeq
    }
  }

  def getByMunicipality(typeId: Int, municipality: Int): Seq[Map[String, Any]] = {
    withDynTransaction {
      val roadLinks = RoadLinkService.getByMunicipality(municipality)
      val roadLinkIds = roadLinks.map(_._1).toList

      val numericalLimits = OracleArray.fetchNumericalLimitsByRoadLinkIds(roadLinkIds, typeId, valuePropertyId, bonecpToInternalConnection(dynamicSession.conn))

      val linkGeometries: Map[Long, Seq[Point]] =
        roadLinks.foldLeft(Map.empty[Long, Seq[Point]]) { (acc, roadLink) =>
          acc + (roadLink._1 -> roadLink._2)
        }

      numericalLimits.map { link =>
        val (assetId, roadLinkId, mmlId, sideCode, value, startMeasure, endMeasure) = link
        val geometry = GeometryUtils.truncateGeometry(linkGeometries(roadLinkId), startMeasure, endMeasure)
        Map("id" -> (assetId + "-" + mmlId),
          "points" -> geometry,
          "value" -> value,
          "side_code" -> sideCode,
          "mmlId" -> mmlId,
          "startMeasure" -> startMeasure,
          "endMeasure" -> endMeasure)
      }
    }
  }

  private def getByIdWithoutTransaction(id: Long): Option[NumericalLimit] =  {
    val links = numericalLimitLinksById(id)
    if (links.isEmpty) None
    else {
      val linkEndpoints: List[(Point, Point)] = links.map { link => GeometryUtils.geometryEndpoints(link._5) }.toList
      val limitEndpoints = calculateEndPoints(linkEndpoints)
      val head = links.head
      val (_, _, _, value, _, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) = head
      val numericalLimitLinks = links.map { case (_, roadLinkId, sideCode, _, points, _, _, _, _, _, typeId) =>
        NumericalLimitLink(id, roadLinkId, sideCode, value, points, None, None, expired)
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
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption(valuePropertyId).get
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

  private def createNumericalLimitWithoutTransaction(typeId: Int, roadLinkId: Long, value: Int, expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): NumericalLimit = {
    val id = Sequences.nextPrimaryKeySeqValue
    val numberPropertyValueId = Sequences.nextPrimaryKeySeqValue
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).first(valuePropertyId)
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if(expired) "sysdate" else "null"
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to)
        values ($id, $typeId, $username, sysdate, #$validTo)

        into lrm_position(id, start_measure, end_measure, road_link_id, side_code)
        values ($lrmPositionId, $startMeasure, $endMeasure, $roadLinkId, $sideCode)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

        into number_property_value(id, asset_id, property_id, value)
        values ($numberPropertyValueId, $id, $propertyId, $value)
      select * from dual
    """.execute
    getByIdWithoutTransaction(id).get
  }

  def createNumericalLimit(typeId: Int, roadLinkId: Long, value: Int, username: String): NumericalLimit = {
    val sideCode = 1
    val startMeasure = 0
    val endMeasure = RoadLinkService.getRoadLinkLength(roadLinkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    withDynTransaction {
      createNumericalLimitWithoutTransaction(typeId, roadLinkId, value, false, sideCode, startMeasure, endMeasure, username)
    }
  }

  def split(id: Long, roadLinkId: Long, splitMeasure: Double, value: Int, expired: Boolean, username: String): Seq[NumericalLimit] = {
    withDynTransaction {
      val typeId = getByIdWithoutTransaction(id).get.typeId
      Queries.updateAssetModified(id, username).execute()
      val (startMeasure, endMeasure, sideCode) = OracleLinearAssetDao.getLinkGeometryData(id, roadLinkId)
      val links: Seq[(Long, Double, (Point, Point))] = OracleLinearAssetDao.getLinksWithLength(typeId, id).map { link =>
        val (roadLinkId, length, geometry) = link
        (roadLinkId, length, GeometryUtils.geometryEndpoints(geometry))
      }
      val (existingLinkMeasures, createdLinkMeasures, linksToMove) = GeometryUtils.createMultiSegmentSplit(splitMeasure, (roadLinkId, startMeasure, endMeasure), links)

      OracleLinearAssetDao.updateLinkStartAndEndMeasures(id, roadLinkId, existingLinkMeasures)
      val createdId = createNumericalLimitWithoutTransaction(typeId, roadLinkId, value, expired, sideCode, createdLinkMeasures._1, createdLinkMeasures._2, username).id
      if (linksToMove.nonEmpty) OracleLinearAssetDao.moveLinks(id, createdId, linksToMove.map(_._1))
      Seq(getByIdWithoutTransaction(id).get, getByIdWithoutTransaction(createdId).get)
    }
  }
}

object NumericalLimitService extends NumericalLimitOperations {
  def withDynTransaction[T](f: => T): T = Database.forDataSource(dataSource).withDynTransaction(f)
}
