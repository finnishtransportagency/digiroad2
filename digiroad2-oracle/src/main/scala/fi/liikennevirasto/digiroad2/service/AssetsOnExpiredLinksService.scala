package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, PersistedPointAsset, Point, PointAssetOperations}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetOnExpiredRoadLink, AssetsOnExpiredLinksDAO, MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset._
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.lane.PersistedLane
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopService
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, LogUtils}
import org.slf4j.LoggerFactory
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
case class AssetOnExpiredLink(id: Long, assetTypeId: Int, linkId: String, sideCode: Int, startMeasure: Double,
                              endMeasure: Double, geometry: Seq[Point], roadLinkExpiredDate: DateTime, nationalId: Option[Int])
case class AssetOnExpiredLinkWithAssetProperties(asset: AssetOnExpiredLink, properties: String)
class AssetsOnExpiredLinksService {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  protected def dao: AssetsOnExpiredLinksDAO = new AssetsOnExpiredLinksDAO
  implicit val formats = Serialization.formats(NoTypeHints)
  private val logger = LoggerFactory.getLogger(getClass)
  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }
  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient
  }
  private val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventbus)
  private val roadAddressService: RoadAddressService = new RoadAddressService
  private val dynamicLinearAssetService = new DynamicLinearAssetService(roadLinkService,eventbus)
  private val laneService = new LaneService(roadLinkService, eventbus, roadAddressService)
  private class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
    override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
  }
  private def getDynamicLinearAssetService(typeId: Int): LinearAssetOperations = {
    typeId match {
      case HeightLimit.typeId => new LinearHeightLimitService(roadLinkService, eventbus)
      case LengthLimit.typeId => new LinearLengthLimitService(roadLinkService, eventbus)
      case WidthLimit.typeId => new LinearWidthLimitService(roadLinkService, eventbus)
      case TotalWeightLimit.typeId => new LinearTotalWeightLimitService(roadLinkService, eventbus)
      case TrailerTruckWeightLimit.typeId => new LinearTrailerTruckWeightLimitService(roadLinkService, eventbus)
      case AxleWeightLimit.typeId => new LinearAxleWeightLimitService(roadLinkService, eventbus)
      case BogieWeightLimit.typeId => new LinearBogieWeightLimitService(roadLinkService, eventbus)
      case MassTransitLane.typeId => new MassTransitLaneService(roadLinkService, eventbus)
      case DamagedByThaw.typeId => new DamagedByThawService(roadLinkService, eventbus)
      case RoadWorksAsset.typeId => new RoadWorkService(roadLinkService, eventbus)
      case ParkingProhibition.typeId => new ParkingProhibitionService(roadLinkService, eventbus)
      case CyclingAndWalking.typeId => new CyclingAndWalkingService(roadLinkService, eventbus)
      case NumberOfLanes.typeId => new NumberOfLanesService(roadLinkService, eventbus)
      case WinterSpeedLimit.typeId => new LinearAssetService(roadLinkService, eventbus)
      case TrafficVolume.typeId => new LinearAssetService(roadLinkService, eventbus)
      case MaintenanceRoadAsset.typeId => new MaintenanceService(roadLinkService, eventbus)
      case PavedRoad.typeId =>  new PavedRoadService(roadLinkService, eventbus)
      case Prohibition.typeId =>  new ProhibitionService(roadLinkService, eventbus)
      case HazmatTransportProhibition.typeId => new HazmatTransportProhibitionService(roadLinkService, eventbus)
      case RoadWidth.typeId => new RoadWidthService(roadLinkService, eventbus)
      case SpeedLimitAsset.typeId =>   new SpeedLimitService(eventbus, roadLinkService)
      case _ => dynamicLinearAssetService
    }
  }
  private def getDynamicPointAssetService(typeId: Int): PointAssetOperations = {
    typeId match {
      case PedestrianCrossings.typeId => new PedestrianCrossingService(roadLinkService, eventbus)
      case Obstacles.typeId => new ObstacleService(roadLinkService)
      case RailwayCrossings.typeId => new RailwayCrossingService(roadLinkService)
      case DirectionalTrafficSigns.typeId => new DirectionalTrafficSignService(roadLinkService)
      case TrafficSigns.typeId => new TrafficSignService(roadLinkService, eventbus)
      case TrafficLights.typeId => new TrafficLightService(roadLinkService)
      case MassTransitStopAsset.typeId => new MassTransitStopServiceWithDynTransaction(eventbus, roadLinkService, roadAddressService)
      case _ => throw new IllegalArgumentException("Invalid asset id")
    }
  }

  /**
   *  Pair matching assets and serialize persisted properties
   */
  private def matchAssetsAndSerializeProps[T <: AnyRef](assets: Seq[AssetOnExpiredLink], persistedProps: Map[Long, T]): Seq[AssetOnExpiredLinkWithAssetProperties] = {
    assets.flatMap { asset =>
      persistedProps.get(asset.id).map { persisted =>
        AssetOnExpiredLinkWithAssetProperties(asset, write(persisted))
      }
    }
  }

  private def enrichLanesWithPersistedLaneProperties(assets: Seq[AssetOnExpiredLink]): Seq[AssetOnExpiredLinkWithAssetProperties] = {
    if (assets.isEmpty) return Seq.empty
    LogUtils.time(logger, s"Enriching assets and mapping properties for asset type: ${assets.head.assetTypeId}") {
      val linkIds = assets.map(_.linkId).distinct
      val assetIds = assets.map(_.id).toSet

      val persistedById: Map[Long, PersistedLane] = laneService.fetchExistingLanesByLinkIds(linkIds, Seq(), newTransaction = false)
        .filter(pl => assetIds.contains(pl.id))
        .map(pl => pl.id -> pl)
        .toMap

      matchAssetsAndSerializeProps(assets, persistedById)
    }
  }

  private def enrichAssetsWithPersistedLinearAssetProperties(assets: Seq[AssetOnExpiredLink]):Seq[AssetOnExpiredLinkWithAssetProperties] = {
    if (assets.isEmpty) return Seq.empty
    LogUtils.time(logger, s"Enriching assets and mapping properties for asset type: ${assets.head.assetTypeId}") {
      val linkIds = assets.map(_.linkId).toSet
      val assetIds = assets.map(_.id).toSet

      val persistedById: Map[Long, PersistedLinearAsset] = dynamicLinearAssetService
        .fetchExistingAssetsByLinksIdsString(assets.head.assetTypeId, linkIds, Set(), newTransaction = false)
        .filter(a => assetIds.contains(a.id))
        .map(a => a.id -> a)
        .toMap

      matchAssetsAndSerializeProps(assets, persistedById)
    }
  }

    private def enrichAssetsWithPersistedPointAssetProperties(assets: Seq[AssetOnExpiredLink]): Seq[AssetOnExpiredLinkWithAssetProperties] = {
      if (assets.isEmpty) return Seq.empty
      LogUtils.time(logger, s"Enriching assets and mapping properties for asset type: ${assets.head.assetTypeId}") {
        val dynamicPointAssetService = getDynamicPointAssetService(assets.head.assetTypeId)

        val linkIds = assets.map(_.linkId).toSet
        val assetIds = assets.map(_.id).toSet

        val persistedAssets = linkIds.flatMap(linkId => dynamicPointAssetService.getPersistedAssetsByLinkIdWithoutTransaction(linkId))
        val persistedAssetsFilteredById = persistedAssets.filter(asset => assetIds.contains(asset.id)).map(a => a.id -> a).toMap

        matchAssetsAndSerializeProps(assets, persistedAssetsFilteredById)
      }
    }

// TODO use this when MassQuery is no longer slower option.

//  private def enrichAssetsWithPersistedPointAssetProperties(assets: Seq[AssetOnExpiredLink]): Seq[AssetOnExpiredLinkWithAssetProperties] = {
//    if (assets.isEmpty) return Seq.empty
//    LogUtils.time(logger, s"Enriching assets and mapping properties for asset type: ${assets.head.assetTypeId}") {
//      val dynamicPointAssetService = getDynamicPointAssetService(assets.head.assetTypeId)
//
//      val linkIds = assets.map(_.linkId).toSet
//      val assetIds = assets.map(_.id).toSet
//
//      val persistedById: Map[Long, PersistedPointAsset] = dynamicPointAssetService
//        .getPersistedAssetsByLinkIdsWithoutTransaction(linkIds)
//        .filter(a => assetIds.contains(a.id))
//        .map(a => a.id -> a)
//        .toMap
//
//      // Pair matching assets and serialize persisted properties
//      assets.flatMap { asset =>
//        persistedById.get(asset.id).map { persisted =>
//          AssetOnExpiredLinkWithAssetProperties(asset, write(persisted))
//        }
//      }
//    }
//  }

  def getAllWorkListAssets(newTransaction: Boolean = true): Seq[AssetOnExpiredLinkWithAssetProperties] = {
    if (newTransaction) withDynTransaction {
      val workListAssets = dao.fetchWorkListAssets()
      val workListAssetsGroupedByAssetTypeId = workListAssets.groupBy(_.assetTypeId)
      val enrichedAssets = workListAssetsGroupedByAssetTypeId.map { case (assetTypeId, assets) =>
        val assetType = AssetTypeInfo.apply(assetTypeId)
        if (assetType.geometryType == "point")
          enrichAssetsWithPersistedPointAssetProperties(assets)
        else if (assetType.geometryType == "linear" && assetType.typeId == Lanes.typeId)
          enrichLanesWithPersistedLaneProperties(assets)
        else if (assetType.geometryType == "linear")
          enrichAssetsWithPersistedLinearAssetProperties(assets)
        else {
          logger.error(s"Unknown asset type ${assets.head.assetTypeId} for ${assets.map(_.id)}")
          throw new Exception(s"Unknown asset type ${assets.head.assetTypeId} for ${assets.map(_.id)}")
        }
      }
      enrichedAssets.flatten.toSeq
    } else {
      dao.fetchWorkListAssets().map(asset => AssetOnExpiredLinkWithAssetProperties(asset, write(Map.empty[String, String])))
    }
  }
  def insertAssets(assets: Seq[AssetOnExpiredLink], newTransaction: Boolean = false): Unit = {
    if(newTransaction) withDynTransaction{
      assets.foreach(asset => dao.insertToWorkList(asset))
    }
    else assets.foreach(asset => dao.insertToWorkList(asset))
  }
  def expireAssetsByIdAndDeleteFromWorkList(assetIds: Set[Long], userName: String, newTransaction: Boolean = false): Set[Long] = {
    if(newTransaction) withDynTransaction{
      Queries.expireAssetsByIds(assetIds.toSeq, userName)
      dao.deleteFromWorkList(assetIds)
    }
    else {
      Queries.expireAssetsByIds(assetIds.toSeq, userName)
      dao.deleteFromWorkList(assetIds)
    }
    assetIds
  }
  def getAssetsOnExpiredRoadLinksById(ids: Set[Long]): Seq[AssetOnExpiredRoadLink] = {
    withDynTransaction {
      dao.getAssetsOnExpiredRoadLinksById(ids)
    }
  }
}
