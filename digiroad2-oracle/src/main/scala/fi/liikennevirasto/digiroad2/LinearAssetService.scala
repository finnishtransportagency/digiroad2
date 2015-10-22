package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{MValueAdjustment, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
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

  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    val mmlIds = roadLinks.map(_.mmlId)

    val existingAssets = withDynTransaction {
      if (typeId == 190) {
        dao.fetchProhibitionsByMmlIds(typeId, mmlIds, valuePropertyId)
      } else {
        dao.fetchLinearAssetsByMmlIds(typeId, mmlIds, valuePropertyId)
      }
    }.filterNot(_.expired).groupBy(_.mmlId)

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

  def getPersistedAssetsByIds(ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      dao.fetchLinearAssetsByIds(ids, valuePropertyId)
    }
  }

  def update(ids: Seq[Long], value: Option[Int], expired: Boolean, username: String): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, expired, username)
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

  def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      newLinearAssets.map { newAsset =>
        val expired = false
        createWithoutTransaction(typeId, newAsset.mmlId, newAsset.value, expired, newAsset.sideCode, newAsset.startMeasure, newAsset.endMeasure, username)
      }
    }
  }

  def split(id: Long, splitMeasure: Double, existingValue: Option[Int], createdValue: Option[Int], username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val createdIdOption = splitWithoutTransaction(id, splitMeasure, createdValue, username, municipalityValidation)
      updateWithoutTransaction(Seq(id), existingValue, existingValue.isEmpty, username)
      (Seq(dao.fetchLinearAssetsByIds(Set(id), valuePropertyId).headOption.map(_.id)) ++ Seq(createdIdOption)).flatten
    }
  }

  def drop(ids: Set[Long]): Unit = {
    withDynTransaction {
      dao.floatLinearAssets(ids)
    }
  }

  def separate(id: Long, valueTowardsDigitization: Option[Int], valueAgainstDigitization: Option[Int], username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val existing = dao.fetchLinearAssetsByIds(Set(id), valuePropertyId).head
      val roadLink = roadLinkService.fetchVVHRoadlink(existing.mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode)
      updateWithoutTransaction(Seq(id), valueTowardsDigitization, valueTowardsDigitization.isEmpty, username)
      dao.updateSideCode(id, SideCode.TowardsDigitizing)
      val created = createWithoutTransaction(existing.typeId, existing.mmlId, valueAgainstDigitization, valueAgainstDigitization.isEmpty, SideCode.AgainstDigitizing.value, existing.startMeasure, existing.endMeasure, username)
      Seq(existing.id, created.id)
    }
  }

  private def updateWithoutTransaction(ids: Seq[Long], value: Option[Int], expired: Boolean, username: String): Seq[Long] = {
    def updateNumberProperty(assetId: Long, propertyId: Long, value: Int): Int = {
      sqlu"update number_property_value set value = $value where asset_id = $assetId and property_id = $propertyId".first
    }

    def updateValue(id: Long, value: Int, username: String): Option[Long] = {
      val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
      val assetsUpdated = Queries.updateAssetModified(id, username).first
      val propertiesUpdated = updateNumberProperty(id, propertyId, value)
      if (assetsUpdated == 1 && propertiesUpdated == 1) {
        Some(id)
      } else {
        None
      }
    }

    def updateExpiration(id: Long, expired: Boolean, username: String) = {
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

    ids.map { id =>
      val valueUpdate: Option[Long] = value.flatMap(updateValue(id, _, username))
      val expirationUpdate: Option[Long] = updateExpiration(id, expired, username)
      val updatedId = valueUpdate.orElse(expirationUpdate)
      updatedId.getOrElse(throw new scala.NoSuchElementException)
    }
  }

  private def createWithoutTransaction(typeId: Int, mmlId: Long, value: Option[Int], expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): PersistedLinearAsset = {
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

    value.foreach(dao.insertValue(id, valuePropertyId))

    dao.fetchLinearAssetsByIds(Set(id), valuePropertyId).head
  }

  private def splitWithoutTransaction(id: Long, splitMeasure: Double, optionalValue: Option[Int], username: String, municipalityValidation: (Int) => Unit) = {
    val linearAsset = dao.fetchLinearAssetsByIds(Set(id), valuePropertyId).head
    val roadLink = roadLinkService.fetchVVHRoadlink(linearAsset.mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    municipalityValidation(roadLink.municipalityCode)

    Queries.updateAssetModified(id, username).execute
    val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

    dao.updateMValues(id, existingLinkMeasures)
    optionalValue.map { value =>
      createWithoutTransaction(linearAsset.typeId, linearAsset.mmlId, Some(value), false, linearAsset.sideCode, createdLinkMeasures._1, createdLinkMeasures._2, username).id
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
