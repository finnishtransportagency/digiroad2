package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{MValueAdjustment, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.slick.jdbc.{StaticQuery => Q}

trait LinearAssetOperations {
  val valuePropertyId: String = "mittarajoitus"

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def roadLinkService: RoadLinkService
  def dao: OracleLinearAssetDao
  def eventBus: DigiroadEventBus

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  private def linearAssetLinkById(id: Long): Option[(Long, Long, Int, Option[Int], Double, Double, Seq[Point], Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)] = {
    val linearAssets = dao.fetchLinearAssetsByIds(Set(id), valuePropertyId).headOption

    linearAssets.map { asset =>
      val roadLink = roadLinkService.fetchVVHRoadlink(asset.mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      val points = GeometryUtils.truncateGeometry(roadLink.geometry, asset.startMeasure, asset.endMeasure)
      (asset.id, asset.mmlId, asset.sideCode, asset.value, asset.startMeasure, asset.endMeasure, points, asset.modifiedBy, asset.modifiedDateTime, asset.createdBy, asset.createdDateTime, asset.expired, asset.typeId)
    }
  }

  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    val mmlIds = roadLinks.map(_.mmlId)

    val existingAssets = withDynTransaction {
      dao.fetchLinearAssetsByMmlIds(typeId, mmlIds, valuePropertyId)
        .filterNot(_.expired)
        .groupBy(_.mmlId)
    }

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, existingAssets, typeId)
    eventBus.publish("linearAssets:update", changeSet)

    LinearAssetPartitioner.partition(filledTopology, roadLinks.groupBy(_.mmlId).mapValues(_.head))
  }

  def getByMunicipality(typeId: Int, municipality: Int): Seq[PieceWiseLinearAsset] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipality)
    val mmlIds = roadLinks.map(_.mmlId).toList

    val linearAssets = withDynTransaction {
      dao.fetchLinearAssetsByMmlIds(typeId, mmlIds, valuePropertyId)
        .filterNot(_.expired)
        .groupBy(_.mmlId)
    }

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, linearAssets, typeId)
    eventBus.publish("linearAssets:update", changeSet)

    filledTopology
  }

  private def getByIdWithoutTransaction(id: Long): Option[PieceWiseLinearAsset] = {
    linearAssetLinkById(id).map { case (_, mmlId, sideCode, value, startMeasure, endMeasure, points, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) =>
      val linkEndpoints: (Point, Point) = GeometryUtils.geometryEndpoints(points)
      PieceWiseLinearAsset(
        id, mmlId, SideCode(sideCode), value, points, expired, startMeasure, endMeasure, Set(linkEndpoints._1, linkEndpoints._2),
        modifiedBy, modifiedAt,
        createdBy, createdAt, typeId)
    }
  }

  def getPersistedAssetsByIds(ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      dao.fetchLinearAssetsByIds(ids, valuePropertyId)
    }
  }

  def getById(id: Long): Option[PieceWiseLinearAsset] = {
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

  def update(ids: Seq[Long], value: Option[Int], expired: Boolean, username: String): Seq[Long] = {
    withDynTransaction {
      ids.map { id =>
        val valueUpdate: Option[Long] = value.flatMap(updateLinearAssetValue(id, _, username))
        val expirationUpdate: Option[Long] = updateLinearAssetExpiration(id, expired, username)
        val updatedId = valueUpdate.orElse(expirationUpdate)
        updatedId.getOrElse(throw new NoSuchElementException)
      }
    }
  }

  def persistMValueAdjustments(adjustments: Seq[MValueAdjustment]): Unit = {
    withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
      }
    }
  }

  def persistSideCodeAdjustments(adjustments: Seq[SideCodeAdjustment]): Unit = {
    withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateSideCode(adjustment.assetId, adjustment.sideCode)
      }
    }
  }

  private def createWithoutTransaction(typeId: Int, mmlId: Long, value: Option[Int], expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): PieceWiseLinearAsset = {
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

  def createNew(typeId: Int, mmlId: Long, value: Option[Int], username: String, municipalityValidation: Int => Unit): PieceWiseLinearAsset = {
    val sideCode = 1
    val startMeasure = 0
    val expired = false
    val roadLink = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    municipalityValidation(roadLink.municipalityCode)
    withDynTransaction {
      createWithoutTransaction(typeId, roadLink.mmlId, value, expired, sideCode, startMeasure, GeometryUtils.geometryLength(roadLink.geometry), username)
    }
  }

  def create(newLinearAssets: Seq[NewLimit], typeId: Int, value: Option[Int], username: String) = {
    withDynTransaction {
      newLinearAssets.map { newAsset =>
        val sideCode = 1
        val expired = false
        createWithoutTransaction(typeId, newAsset.mmlId, value, expired, sideCode, newAsset.startMeasure, newAsset.endMeasure, username)
      }
    }
  }

  def split(id: Long, mmlId: Long, splitMeasure: Double, value: Option[Int], expired: Boolean, username: String, municipalityValidation: Int => Unit): Seq[PieceWiseLinearAsset] = {
    val roadLink = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    municipalityValidation(roadLink.municipalityCode)

    val limit: PieceWiseLinearAsset = getById(id).get
    val createdId = withDynTransaction {
      Queries.updateAssetModified(id, username).execute
      val (startMeasure, endMeasure, sideCode) = OracleLinearAssetDao.getLinkGeometryData(id)
      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (startMeasure, endMeasure))

      OracleLinearAssetDao.updateMValues(id, existingLinkMeasures)
      createWithoutTransaction(limit.typeId, mmlId, value, expired, sideCode.value, createdLinkMeasures._1, createdLinkMeasures._2, username).id
    }
    Seq(getById(id).get, getById(createdId).get)
  }

  def drop(ids: Set[Long]): Unit = {
    withDynTransaction {
      dao.floatLinearAssets(ids)
    }
  }
}

class LinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao {
    override val roadLinkService: RoadLinkService = roadLinkServiceImpl
  }
  override def eventBus: DigiroadEventBus = eventBusImpl
}
