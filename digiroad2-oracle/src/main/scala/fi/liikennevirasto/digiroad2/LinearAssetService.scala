package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{MValueAdjustment, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.slick.jdbc.{StaticQuery => Q}

object LinearAssetTypes {
  val ProhibitionAssetTypeId = 190
  val HazmatTransportProhibitionAssetTypeId = 210
  val EuropeanRoadAssetTypeId = 260
  val ExitNumberAssetTypeId = 270
  val numericValuePropertyId: String = "mittarajoitus"
  val europeanRoadPropertyId: String = "eurooppatienumero"
  val exitNumberPropertyId: String = "liittymänumero"
  def getValuePropertyId(typeId: Int) = typeId match {
    case EuropeanRoadAssetTypeId => europeanRoadPropertyId
    case ExitNumberAssetTypeId => exitNumberPropertyId
    case _ => numericValuePropertyId
  }
}

case class ChangedLinearAsset(linearAsset: PieceWiseLinearAsset, link: RoadLink)

trait LinearAssetOperations {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def roadLinkService: RoadLinkService
  def vvhClient: VVHClient
  def dao: OracleLinearAssetDao
  def eventBus: DigiroadEventBus

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  /**
    * Returns linear assets for Digiroad2Api /linearassets GET endpoint.
    * @param typeId
    * @param bounds
    * @param municipalities
    * @return
    */
  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks)
    LinearAssetPartitioner.partition(linearAssets, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  /**
    * Returns linear assets by municipality. Used by all IntegrationApi linear asset endpoints (except speed limits).
    * @param typeId
    * @param municipality
    * @return
    */
  def getByMunicipality(typeId: Int, municipality: Int): Seq[PieceWiseLinearAsset] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipality)
    getByRoadLinks(typeId, roadLinks)
  }

  private def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink]): Seq[PieceWiseLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets =
      withDynTransaction {
        typeId match {
          case LinearAssetTypes.ProhibitionAssetTypeId | LinearAssetTypes.HazmatTransportProhibitionAssetTypeId =>
            dao.fetchProhibitionsByLinkIds(typeId, linkIds, includeFloating = false)
          case LinearAssetTypes.EuropeanRoadAssetTypeId | LinearAssetTypes.ExitNumberAssetTypeId =>
            dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds, LinearAssetTypes.getValuePropertyId(typeId))
          case _ =>
            dao.fetchLinearAssetsByLinkIds(typeId, linkIds, LinearAssetTypes.numericValuePropertyId)
        }
      }.filterNot(_.expired).groupBy(_.linkId)

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, existingAssets, typeId)
    eventBus.publish("linearAssets:update", changeSet)
    filledTopology
  }

  /**
    * Returns linear assets by asset type and asset ids. Used by Digiroad2Api /linearassets POST and /linearassets DELETE endpoints.
    */
  def getPersistedAssetsByIds(typeId: Int, ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      typeId match {
        case LinearAssetTypes.EuropeanRoadAssetTypeId | LinearAssetTypes.ExitNumberAssetTypeId =>
          dao.fetchAssetsWithTextualValuesByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
        case LinearAssetTypes.ProhibitionAssetTypeId | LinearAssetTypes.HazmatTransportProhibitionAssetTypeId =>
          dao.fetchProhibitionsByIds(typeId, ids)
        case _ =>
          dao.fetchLinearAssetsByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
      }
    }
  }

  /**
    * Returns changed linear assets after given date. Used by ChangeApi /:assetType GET endpoint.
    */
  def getChanged(typeId: Int, since: DateTime): Seq[ChangedLinearAsset] = {
    val persistedLinearAssets = withDynTransaction {
      dao.getLinearAssetsChangedSince(typeId, since)
    }
    val roadLinks = roadLinkService.getRoadLinksFromVVH(persistedLinearAssets.map(_.linkId).toSet)

    persistedLinearAssets.flatMap { persistedLinearAsset =>
      roadLinks.find(_.linkId == persistedLinearAsset.linkId).map { roadLink =>
        val points = GeometryUtils.truncateGeometry(roadLink.geometry, persistedLinearAsset.startMeasure, persistedLinearAsset.endMeasure)
        val endPoints = GeometryUtils.geometryEndpoints(points)
        ChangedLinearAsset(
          linearAsset = PieceWiseLinearAsset(
            persistedLinearAsset.id, persistedLinearAsset.linkId, SideCode(persistedLinearAsset.sideCode), persistedLinearAsset.value, points, persistedLinearAsset.expired,
            persistedLinearAsset.startMeasure, persistedLinearAsset.endMeasure,
            Set(endPoints._1, endPoints._2), persistedLinearAsset.modifiedBy, persistedLinearAsset.modifiedDateTime,
            persistedLinearAsset.createdBy, persistedLinearAsset.createdDateTime, persistedLinearAsset.typeId, roadLink.trafficDirection,
          persistedLinearAsset.vvhTimeStamp, persistedLinearAsset.geomModifiedDate)
          ,
          link = roadLink
        )
      }
    }
  }

  /**
    * Expires linear asset. Used by Digiroad2Api /linearassets DELETE endpoint and Digiroad2Context.LinearAssetUpdater actor.
    */
  def expire(ids: Seq[Long], username: String): Seq[Long] = {
    withDynTransaction {
      ids.foreach(dao.updateExpiration(_, expired = true, username))
      ids
    }
  }

  /**
    * Saves updated linear asset from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def update(ids: Seq[Long], value: Value, username: String): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username)
    }
  }

  /**
    * Updates start and end measures after geometry change in VVH. Used by Digiroad2Context.LinearAssetUpdater actor.
    */
  def persistMValueAdjustments(adjustments: Seq[MValueAdjustment]): Unit = {
    withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
      }
    }
  }

  /**
    * Updates side codes. Used by Digiroad2Context.LinearAssetUpdater actor.
    */
  def persistSideCodeAdjustments(adjustments: Seq[SideCodeAdjustment]): Unit = {
    withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateSideCode(adjustment.assetId, adjustment.sideCode)
      }
    }
  }

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String): Seq[Long] = {
    withDynTransaction {
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, newAsset.startMeasure, newAsset.endMeasure, username)
      }
    }
  }

  /**
    * Saves linear asset when linear asset is split to two parts in UI (scissors icon). Used by Digiroad2Api /linearassets/:id POST endpoint.
    */
  def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val roadLink = vvhClient.fetchVVHRoadlink(linearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode)

      Queries.updateAssetModified(id, username).execute

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))
      dao.updateMValues(id, existingLinkMeasures)

      existingValue match {
        case None => dao.updateExpiration(id, expired = true, username)
        case Some(value) => updateWithoutTransaction(Seq(id), value, username)
      }

      val createdIdOption = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, createdLinkMeasures._1, createdLinkMeasures._2, username))

      Seq(id) ++ Seq(createdIdOption).flatten
    }
  }

  /**
    * Sets linear assets with no geometry as floating. Used by Used by Digiroad2Context.LinearAssetUpdater actor.
    */
  def drop(ids: Set[Long]): Unit = {
    withDynTransaction {
      dao.floatLinearAssets(ids)
    }
  }

  /**
    * Saves linear assets when linear asset is separated to two sides in UI. Used by Digiroad2Api /linearassets/:id/separate POST endpoint.
    */
  def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val existing = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val roadLink = vvhClient.fetchVVHRoadlink(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode)

      valueTowardsDigitization match {
        case None => dao.updateExpiration(id, expired = true, username)
        case Some(value) => updateWithoutTransaction(Seq(id), value, username)
      }

      dao.updateSideCode(id, SideCode.TowardsDigitizing)

      val created = valueAgainstDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.AgainstDigitizing.value, existing.startMeasure, existing.endMeasure, username))

      Seq(existing.id) ++ created
    }
  }

  private def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = sql"""select ID, ASSET_TYPE_ID from ASSET where ID in (#${ids.mkString(",")})""".as[(Long, Int)].list
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId)}

    ids.foreach { id =>
      val typeId = assetTypeById(id)
      value match {
        case NumericValue(intValue) =>
          dao.updateValue(id, intValue, LinearAssetTypes.numericValuePropertyId, username)
        case TextualValue(textValue) =>
          dao.updateValue(id, textValue, LinearAssetTypes.getValuePropertyId(typeId), username)
        case prohibitions: Prohibitions =>
          dao.updateProhibitionValue(id, prohibitions, username)
      }
    }

    ids
  }

  private def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, startMeasure, endMeasure, username)
    value match {
      case NumericValue(intValue) =>
        dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
      case TextualValue(textValue) =>
        dao.insertValue(id, LinearAssetTypes.getValuePropertyId(typeId), textValue)
      case prohibitions: Prohibitions =>
        dao.insertProhibitionValue(id, prohibitions)
    }
    id
  }
}

class LinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient)
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
}
