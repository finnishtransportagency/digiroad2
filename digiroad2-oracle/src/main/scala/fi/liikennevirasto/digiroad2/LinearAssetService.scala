package fi.liikennevirasto.digiroad2

import com.github.tototoshi.slick.MySQLJodaSupport._
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration.{DateTimePropertyFormat => DateTimeFormat}
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.VVHRoadLinkWithProperties
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.slick.jdbc.{StaticQuery => Q}

case class LinearAsset(id: Long, mmlId: Long, sideCode: Int, value: Option[Int], points: Seq[Point], expired: Boolean,
                       endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[String],
                       createdBy: Option[String], createdDateTime: Option[String], typeId: Int)

case class PersistedLinearAsset(id: Long, mmlId: Long, sideCode: Int, value: Option[Int],
                         startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDateTime: Option[DateTime],
                         modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean)

object LinearAssetFiller {
  private val AllowedTolerance = 0.5

  private def adjustAsset(asset: PersistedLinearAsset, roadLink: VVHRoadLinkWithProperties): PersistedLinearAsset = {
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val lengthDifference = roadLinkLength - asset.endMeasure
    if (lengthDifference < AllowedTolerance)
      asset.copy(endMeasure = roadLinkLength)
    else
      asset
  }

  private def adjustTwoWaySegments(roadLink: VVHRoadLinkWithProperties, assets: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val twoWaySegments = assets.filter(_.sideCode == 1)
    if (twoWaySegments.length == 1 && assets.forall(_.sideCode == 1)) {
      val asset = assets.head
      val adjustedAsset = adjustAsset(asset, roadLink)
      Seq(adjustedAsset)
    } else {
      assets
    }
  }

  private def generateNonExistingLinearAssets(roadLink: VVHRoadLinkWithProperties, segmentsOnLink: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5}
    remainders.map { segment =>
      PersistedLinearAsset(0L, roadLink.mmlId, 1, None, segment._1, segment._2, None, None, None, None, false)
    }
  }

  def toLinearAsset(dbAssets: Seq[PersistedLinearAsset], roadLinkGeometry: Seq[Point], typeId: Int): Seq[LinearAsset] = {
    dbAssets.map { dbAsset =>
      val points = GeometryUtils.truncateGeometry(roadLinkGeometry, dbAsset.startMeasure, dbAsset.endMeasure)
      val endPoints = GeometryUtils.geometryEndpoints(points)
      LinearAsset(
        dbAsset.id, dbAsset.mmlId, dbAsset.sideCode, dbAsset.value, points, dbAsset.expired,
        Set(endPoints._1, endPoints._2), dbAsset.modifiedBy, dbAsset.modifiedDateTime.map(DateTimeFormat.print),
        dbAsset.createdBy, dbAsset.createdDateTime.map(DateTimeFormat.print), typeId)
    }
  }

  def fillTopology(topology: Seq[VVHRoadLinkWithProperties], linearAssets: Map[Long, Seq[PersistedLinearAsset]], typeId: Int): Seq[LinearAsset] = {
    topology.foldLeft(Seq.empty[LinearAsset]) { case (acc, roadLink) =>
      val existingAssets = acc
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink.mmlId, Nil)
      val adjustedAssets = adjustTwoWaySegments(roadLink, assetsOnRoadLink)

      val generatedLinearAssets = generateNonExistingLinearAssets(roadLink, adjustedAssets)
      existingAssets ++ toLinearAsset(generatedLinearAssets ++ adjustedAssets, roadLink.geometry, typeId)
    }
  }
}

trait LinearAssetOperations {
  val valuePropertyId: String = "mittarajoitus"

  def withDynTransaction[T](f: => T): T
  def roadLinkService: RoadLinkService

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
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

  private def fetchLinearAssetsByMmlIds(assetTypeId: Int, mmlIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    MassQuery.withIds(mmlIds.toSet) { idTableName =>
      val assets = sql"""
        select a.id, pos.mml_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.mml_id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)"""
        .as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean)].list
      assets.map { case(id, mmlId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired) =>
          PersistedLinearAsset(id, mmlId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired)
      }
    }
  }

  def generateMissingLinearAssets(roadLinks: Seq[VVHRoadLinkWithProperties], linearAssets: Seq[PersistedLinearAsset]) = {
    val roadLinksWithoutAssets = roadLinks.filterNot(link => linearAssets.exists(linearAsset => linearAsset.mmlId == link.mmlId))

    roadLinksWithoutAssets.map { link =>
      PersistedLinearAsset(0L, link.mmlId, 1, None, 0.0, link.length, None, None, None, None, false)
    }
  }

  private def generateLinearAssetsForHoles(roadLink: VVHRoadLinkWithProperties, segmentsOnLink: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5}
    remainders.map { segment =>
      PersistedLinearAsset(0L, roadLink.mmlId, 1, None, segment._1, segment._2, None, None, None, None, false)
    }
  }

  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[LinearAsset] = {
    withDynTransaction {
      val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
      val mmlIds = roadLinks.map(_.mmlId)

      val existingAssets = fetchLinearAssetsByMmlIds(typeId, mmlIds).groupBy(_.mmlId)

      LinearAssetFiller.fillTopology(roadLinks, existingAssets, typeId)
   }
  }

  def getByMunicipality(typeId: Int, municipality: Int): (Seq[PersistedLinearAsset], Map[Long, Seq[Point]]) = {
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
      LinearAsset(
        id, mmlId, sideCode, value, points, expired, Set(linkEndpoints._1, linkEndpoints._2),
        modifiedBy, modifiedAt.map(DateTimeFormat.print),
        createdBy, createdAt.map(DateTimeFormat.print), typeId)
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
