package fi.liikennevirasto.digiroad2

import com.github.tototoshi.slick.MySQLJodaSupport._
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration.{DateTimePropertyFormat => DateTimeFormat}
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.VVHRoadLinkWithProperties
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.slick.jdbc.{StaticQuery => Q}

case class LinearAssetLink(id: Long, mmlId: Long, sideCode: Int, value: Option[Int], points: Seq[Point],
                           position: Option[Int] = None, towardsLinkChain: Option[Boolean] = None, expired: Boolean = false)

case class LinearAsset(id: Long, value: Option[Int], expired: Boolean, endpoints: Set[Point],
                       modifiedBy: Option[String], modifiedDateTime: Option[String],
                       createdBy: Option[String], createdDateTime: Option[String],
                       linearAssetLink: LinearAssetLink, typeId: Int)

trait LinearAssetOperations {
  import GeometryDirection._

  val valuePropertyId: String = "mittarajoitus"

  def withDynTransaction[T](f: => T): T
  def roadLinkService: RoadLinkService

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  private def getLinksWithPositions(links: Seq[LinearAssetLink]): Seq[LinearAssetLink] = {
    def getLinkEndpoints(link: LinearAssetLink): (Point, Point) = GeometryUtils.geometryEndpoints(link.points)
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

  private def getLinkWithPosition(link: LinearAssetLink): LinearAssetLink = {
    link.copy(position = Some(0), towardsLinkChain = Some(true))
  }

  private def linearAssetLinkById(id: Long): Option[(Long, Long, Int, Option[Int], Seq[Point], Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)] = {
    val linearAssets = sql"""
      select a.id, pos.mml_id, pos.side_code, s.value as value, pos.start_measure, pos.end_measure,
             a.modified_by, a.modified_date, a.created_by, a.created_date, case when a.valid_to <= sysdate then 1 else 0 end as expired,
             a.asset_type_id
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join property p on p.public_id = $valuePropertyId
        left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
        where a.id = $id
      """.as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)].firstOption

    linearAssets.map { case (segmentId, mmlId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) =>
      val roadLink = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      val points = GeometryUtils.truncateGeometry(roadLink.geometry, startMeasure, endMeasure)
      (segmentId, mmlId, sideCode, value, points, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId)
    }
  }

  def calculateEndPoints(links: List[(Point, Point)]): Set[Point] = {
    val endPoints = LinkChain(links, identity[(Point, Point)]).endPoints()
    Set(endPoints._1, endPoints._2)
  }

  private def fetchLinearAssetsByMmlIds(assetTypeId: Int, mmlIds: Seq[Long]) = {
    MassQuery.withIds(mmlIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.mml_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure, a.created_date, a.modified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.mml_id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)""".as[(Long, Long, Int, Option[Int], Double, Double, Option[DateTime], Option[DateTime])].list
    }
  }

  def generateMissingLinearAssets(roadLinks: Seq[VVHRoadLinkWithProperties], linearAssets: List[(Long, Long, Int, Option[Int], Double, Double, Option[DateTime], Option[DateTime])]) = {
    val roadLinksWithoutAssets = roadLinks.filterNot(link => linearAssets.exists(linearAsset => linearAsset._2 == link.mmlId))

    roadLinksWithoutAssets.map { link =>
      (0L, link.mmlId, 1, None, 0.0, link.length, None, None)
    }
  }

  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[LinearAssetLink] = {
    withDynTransaction {
      val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
      val mmlIds = roadLinks.map(_.mmlId).toList

      val existingAssets = fetchLinearAssetsByMmlIds(typeId, mmlIds)
      val generatedLinearAssets = generateMissingLinearAssets(roadLinks, existingAssets)
      val linearAssets = existingAssets ++ generatedLinearAssets

      val linkGeometries: Map[Long, (Seq[Point], Double, AdministrativeClass, Int)] =
        roadLinks.foldLeft(Map.empty[Long, (Seq[Point], Double, AdministrativeClass, Int)]) { (acc, roadLink) =>
          acc + (roadLink.mmlId -> (roadLink.geometry, roadLink.length, roadLink.administrativeClass, roadLink.functionalClass))
        }

      val linearAssetsWithGeometry: Seq[LinearAssetLink] = linearAssets.map { link =>
        val (assetId, mmlId, sideCode, value, startMeasure, endMeasure, _, _) = link
        val geometry = GeometryUtils.truncateGeometry(linkGeometries(mmlId)._1, startMeasure, endMeasure)
        LinearAssetLink(assetId, mmlId, sideCode, value, geometry)
      }

      linearAssetsWithGeometry.groupBy(_.id).mapValues(getLinksWithPositions).values.flatten.toSeq
    }
  }

  def getByMunicipality(typeId: Int, municipality: Int): (List[(Long, Long, Int,  Option[Int], Double, Double, Option[DateTime], Option[DateTime])], Map[Long, Seq[Point]]) = {
    withDynTransaction {
      val roadLinks = roadLinkService.fetchVVHRoadlinks(municipality)
      val mmlIds = roadLinks.map(_.mmlId).toList

      val linearAssets = fetchLinearAssetsByMmlIds(typeId, mmlIds)

      val linkGeometries: Map[Long, Seq[Point]] =
        roadLinks.foldLeft(Map.empty[Long, Seq[Point]]) { (acc, roadLink) =>
          acc + (roadLink.mmlId -> roadLink.geometry)
        }

      (linearAssets, linkGeometries)
    }
  }

  private def getByIdWithoutTransaction(id: Long): Option[LinearAsset] = {
    linearAssetLinkById(id).map { case (_, mmlId, sideCode, value, points, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) =>
      val linkEndpoints: (Point, Point) = GeometryUtils.geometryEndpoints(points)
      val linearAssetLink = LinearAssetLink(id, mmlId, sideCode, value, points, None, None, expired)
      LinearAsset(
        id, value, expired, Set(linkEndpoints._1, linkEndpoints._2),
        modifiedBy, modifiedAt.map(DateTimeFormat.print),
        createdBy, createdAt.map(DateTimeFormat.print),
        getLinkWithPosition(linearAssetLink), typeId)
    }
  }

  def getById(id: Long): Option[LinearAsset] = {
    withDynTransaction {
      getByIdWithoutTransaction(id)
    }
  }

  private def updateNumberProperty(assetId: Long, propertyId: Long, value: Int): Int =
    sqlu"update number_property_value set value = $value where asset_id = $assetId and property_id = $propertyId".first

  private def updateLinearAssetValue(id: Long, value: Int, username: String): Option[Long] = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = updateNumberProperty(id, propertyId, value)
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  private def updateLinearAssetExpiration(id: Long, expired: Boolean, username: String) = {
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

  def update(id: Long, value: Option[Int], expired: Boolean, username: String): Option[Long] = {
    withDynTransaction {
      val valueUpdate: Option[Long] = value.flatMap(updateLinearAssetValue(id, _, username))
      val expirationUpdate: Option[Long] = updateLinearAssetExpiration(id, expired, username)
      val updatedId = valueUpdate.orElse(expirationUpdate)
      if (updatedId.isEmpty) dynamicSession.rollback()
      updatedId
    }
  }

  private def createWithoutTransaction(typeId: Int, mmlId: Long, value: Option[Int], expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): LinearAsset = {
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

    value.foreach(OracleLinearAssetDao.insertValue(id, valuePropertyId))

    getByIdWithoutTransaction(id).get
  }

  def createNew(typeId: Int, mmlId: Long, value: Option[Int], username: String, municipalityValidation: Int => Unit): LinearAsset = {
    val sideCode = 1
    val startMeasure = 0
    val expired = false
    val roadLink = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    municipalityValidation(roadLink.municipalityCode)
    withDynTransaction {
      createWithoutTransaction(typeId, roadLink.mmlId, value, expired, sideCode, startMeasure, GeometryUtils.geometryLength(roadLink.geometry), username)
    }
  }

  def split(id: Long, mmlId: Long, splitMeasure: Double, value: Option[Int], expired: Boolean, username: String, municipalityValidation: Int => Unit): Seq[LinearAsset] = {
    val roadLink = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    municipalityValidation(roadLink.municipalityCode)

    val limit: LinearAsset = getById(id).get
    val createdId = withDynTransaction {
      Queries.updateAssetModified(id, username).execute
      val (startMeasure, endMeasure, sideCode) = OracleLinearAssetDao.getLinkGeometryData(id)
      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (startMeasure, endMeasure))

      OracleLinearAssetDao.updateMValues(id, existingLinkMeasures)
      createWithoutTransaction(limit.typeId, mmlId, value, expired, sideCode.value, createdLinkMeasures._1, createdLinkMeasures._2, username).id
    }
    Seq(getById(id).get, getById(createdId).get)
  }
}

class LinearAssetService(roadLinkServiceImpl: RoadLinkService) extends LinearAssetOperations {
  def withDynTransaction[T](f: => T): T = Database.forDataSource(dataSource).withDynTransaction(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
}
