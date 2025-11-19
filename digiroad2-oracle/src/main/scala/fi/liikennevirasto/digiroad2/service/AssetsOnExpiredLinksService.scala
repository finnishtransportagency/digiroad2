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
  private class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
    override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
  }

  private lazy val laneService = new LaneService(roadLinkService, eventbus, roadAddressService)

  private lazy val defaultDynamicLinearService: LinearAssetOperations =
    new DynamicLinearAssetService(roadLinkService, eventbus)

  private lazy val dynamicPointAssetServices: Map [Int, PointAssetOperations] = Map(
   PedestrianCrossings.typeId     -> new PedestrianCrossingService(roadLinkService, eventbus),
   Obstacles.typeId               -> new ObstacleService(roadLinkService),
   RailwayCrossings.typeId        -> new RailwayCrossingService(roadLinkService),
   DirectionalTrafficSigns.typeId -> new DirectionalTrafficSignService(roadLinkService),
   TrafficSigns.typeId            -> new TrafficSignService(roadLinkService, eventbus),
   TrafficLights.typeId           -> new TrafficLightService(roadLinkService),
   MassTransitStopAsset.typeId    -> new MassTransitStopServiceWithDynTransaction(eventbus, roadLinkService, roadAddressService)
  )

  private lazy val dynamicLinearAssetServices: Map[Int, LinearAssetOperations] = Map(
    HeightLimit.typeId                 -> new LinearHeightLimitService(roadLinkService, eventbus),
    LengthLimit.typeId                 -> new LinearLengthLimitService(roadLinkService, eventbus),
    WidthLimit.typeId                  -> new LinearWidthLimitService(roadLinkService, eventbus),
    TotalWeightLimit.typeId            -> new LinearTotalWeightLimitService(roadLinkService, eventbus),
    TrailerTruckWeightLimit.typeId     -> new LinearTrailerTruckWeightLimitService(roadLinkService, eventbus),
    AxleWeightLimit.typeId             -> new LinearAxleWeightLimitService(roadLinkService, eventbus),
    BogieWeightLimit.typeId            -> new LinearBogieWeightLimitService(roadLinkService, eventbus),
    MassTransitLane.typeId             -> new MassTransitLaneService(roadLinkService, eventbus),
    DamagedByThaw.typeId               -> new DamagedByThawService(roadLinkService, eventbus),
    RoadWorksAsset.typeId              -> new RoadWorkService(roadLinkService, eventbus),
    ParkingProhibition.typeId          -> new ParkingProhibitionService(roadLinkService, eventbus),
    CyclingAndWalking.typeId           -> new CyclingAndWalkingService(roadLinkService, eventbus),
    NumberOfLanes.typeId               -> new NumberOfLanesService(roadLinkService, eventbus),
    WinterSpeedLimit.typeId            -> new LinearAssetService(roadLinkService, eventbus),
    TrafficVolume.typeId               -> new LinearAssetService(roadLinkService, eventbus),
    MaintenanceRoadAsset.typeId        -> new MaintenanceService(roadLinkService, eventbus),
    PavedRoad.typeId                   -> new PavedRoadService(roadLinkService, eventbus),
    Prohibition.typeId                 -> new ProhibitionService(roadLinkService, eventbus),
    HazmatTransportProhibition.typeId  -> new HazmatTransportProhibitionService(roadLinkService, eventbus),
    RoadWidth.typeId                   -> new RoadWidthService(roadLinkService, eventbus),
    SpeedLimitAsset.typeId             -> new SpeedLimitService(eventbus, roadLinkService)
  )

  private def getDynamicLinearAssetService(typeId: Int): LinearAssetOperations =
    dynamicLinearAssetServices.getOrElse(typeId, defaultDynamicLinearService)

  private def getDynamicPointAssetService(typeId: Int): PointAssetOperations = {
    dynamicPointAssetServices.getOrElse(typeId, throw new IllegalArgumentException(s"Invalid asset type id $typeId"))
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
      val dynamicLinearAssetService = getDynamicLinearAssetService(assets.head.assetTypeId)

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
