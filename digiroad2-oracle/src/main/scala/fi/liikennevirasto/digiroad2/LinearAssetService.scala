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
    val linearAssets = getByRoadLinks(typeId, roadLinks)
    LinearAssetPartitioner.partition(linearAssets, roadLinks.groupBy(_.mmlId).mapValues(_.head))
  }

  def getByMunicipality(typeId: Int, municipality: Int): Seq[PieceWiseLinearAsset] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipality)
    getByRoadLinks(typeId, roadLinks)
  }

  private def getByRoadLinks(typeId: Int, roadLinks: Seq[VVHRoadLinkWithProperties]): Seq[PieceWiseLinearAsset] = {
    val mmlIds = roadLinks.map(_.mmlId)
    val existingAssets =
      withDynTransaction {
        if (typeId == 190) {
          dao.fetchProhibitionsByMmlIds(mmlIds)
        } else {
          dao.fetchLinearAssetsByMmlIds(typeId, mmlIds, valuePropertyId)
        }
      }.filterNot(_.expired).groupBy(_.mmlId)

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, existingAssets, typeId)
    eventBus.publish("linearAssets:update", changeSet)
    filledTopology
  }

  def getPersistedAssetsByIds(ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      dao.fetchLinearAssetsByIds(ids, valuePropertyId)
    }
  }

  def expire(ids: Seq[Long], username: String): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, NumericValue(0), expired = true, username)
    }
  }

  def update(ids: Seq[Long], value: Value, username: String): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, expired = false, username)
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
        createWithoutTransaction(typeId, newAsset.mmlId, Some(newAsset.value), expired, newAsset.sideCode, newAsset.startMeasure, newAsset.endMeasure, username)
      }
    }
  }

  def create(newProhibitions: Seq[NewProhibition], username: String): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      newProhibitions.map { newProhibition =>
        val setValueFn = (id: Long) => dao.insertProhibitionValue(id, Prohibitions(newProhibition.value))
        createWithoutTransaction(typeId = 190, newProhibition.mmlId, setValueFn, expired = false, newProhibition.sideCode, newProhibition.startMeasure, newProhibition.endMeasure, username)
      }
    }
  }

  def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = dao.fetchLinearAssetsByIds(Set(id), valuePropertyId).head
      val roadLink = roadLinkService.fetchVVHRoadlink(linearAsset.mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode)

      Queries.updateAssetModified(id, username).execute

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))
      dao.updateMValues(id, existingLinkMeasures)

      existingValue match {
        case None => updateWithoutTransaction(Seq(id), NumericValue(0), expired = true, username)
        case Some(value) => updateWithoutTransaction(Seq(id), value, expired = false, username)
      }

      val createdIdOption = createdValue match {
        case None => None
        case Some(NumericValue(created)) =>
          createdValue.map { value =>
            createWithoutTransaction(linearAsset.typeId, linearAsset.mmlId, Some(created), false, linearAsset.sideCode, createdLinkMeasures._1, createdLinkMeasures._2, username).id
          }
        case Some(Prohibitions(created)) =>
          createdValue.map { value =>
            val setValueFn = (id: Long) => dao.insertProhibitionValue(id, Prohibitions(created))
            createWithoutTransaction(linearAsset.typeId, linearAsset.mmlId, setValueFn, false, linearAsset.sideCode, createdLinkMeasures._1, createdLinkMeasures._2, username).id
          }
      }

      Seq(id) ++ Seq(createdIdOption).flatten
    }
  }

  def drop(ids: Set[Long]): Unit = {
    withDynTransaction {
      dao.floatLinearAssets(ids)
    }
  }

  def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val existing = dao.fetchLinearAssetsByIds(Set(id), valuePropertyId).head
      val roadLink = roadLinkService.fetchVVHRoadlink(existing.mmlId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode)

      valueTowardsDigitization match {
        case None => updateWithoutTransaction(Seq(id), NumericValue(0), expired = true, username)
        case Some(value) => updateWithoutTransaction(Seq(id), value, expired = false, username)
      }

      dao.updateSideCode(id, SideCode.TowardsDigitizing)

      val created = valueAgainstDigitization match {
        case None => Nil
        case Some(NumericValue(numeric)) =>
          val setValueFn = (id: Long) => dao.insertValue(id, valuePropertyId)(numeric)
          val created = createWithoutTransaction(existing.typeId, existing.mmlId, setValueFn, expired = false, SideCode.AgainstDigitizing.value, existing.startMeasure, existing.endMeasure, username)
          Seq(created.id)
        case Some(Prohibitions(prohibitions)) =>
          val setValueFn = (id: Long) => dao.insertProhibitionValue(id, Prohibitions(prohibitions))
          val created = createWithoutTransaction(existing.typeId, existing.mmlId, setValueFn, expired = false, SideCode.AgainstDigitizing.value, existing.startMeasure, existing.endMeasure, username)
          Seq(created.id)
      }

      Seq(existing.id) ++ created
    }
  }

  private def updateWithoutTransaction(ids: Seq[Long], value: Value, expired: Boolean, username: String): Seq[Long] = {
    val valueUpdate = value match {
      case Prohibitions(prohibitionValues) => dao.updateProhibitionValue(_: Long, Prohibitions(prohibitionValues), username)
      case NumericValue(intValue) => dao.updateValue(_: Long, intValue, valuePropertyId, username)
    }

    ids.map { id =>
      val expirationUpdate = dao.updateExpiration(id, expired, username)
      val updatedId = valueUpdate(id).orElse(expirationUpdate)
      updatedId.getOrElse(throw new scala.NoSuchElementException)
    }
  }

  private def createWithoutTransaction(typeId: Int, mmlId: Long, value: Option[Int], expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): PersistedLinearAsset = {
    val setValueFn = (id: Long) => value.foreach(dao.insertValue(id, valuePropertyId))
    createWithoutTransaction(typeId, mmlId, setValueFn, expired, sideCode, startMeasure, endMeasure, username)
  }

  private def createWithoutTransaction(typeId: Int, mmlId: Long, setValueFn: Long => Any, expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): PersistedLinearAsset = {
    val id = dao.createLinearAsset(typeId, mmlId, expired, sideCode, startMeasure, endMeasure, username)
    setValueFn(id)
    dao.fetchLinearAssetsByIds(Set(id), valuePropertyId).head
  }


}

class LinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao {
    override val roadLinkService: RoadLinkService = roadLinkServiceImpl
  }
  override def eventBus: DigiroadEventBus = eventBusImpl
}
