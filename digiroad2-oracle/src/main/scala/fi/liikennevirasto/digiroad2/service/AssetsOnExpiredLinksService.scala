package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, PointAssetOperations}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, AxleWeightLimit, BogieWeightLimit, CyclingAndWalking, DamagedByThaw, DirectionalTrafficSigns, HeightLimit, LengthLimit, MassTransitLane, MassTransitStopAsset, NumberOfLanes, Obstacles, ParkingProhibition, PedestrianCrossings, RailwayCrossings, RoadWorksAsset, TotalWeightLimit, TrafficLights, TrafficSigns, TrafficVolume, TrailerTruckWeightLimit, WidthLimit, WinterSpeedLimit}
import fi.liikennevirasto.digiroad2.dao.{AssetOnExpiredRoadLink, AssetsOnExpiredLinksDAO, MassTransitStopDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.{DirectionalTrafficSignService, ObstacleService, PedestrianCrossingService, RailwayCrossingService, TrafficLightService, TrafficSignService}
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.service.linearasset.{CyclingAndWalkingService, DamagedByThawService, DynamicLinearAssetService, LinearAssetOperations, LinearAssetService, LinearAxleWeightLimitService, LinearBogieWeightLimitService, LinearHeightLimitService, LinearLengthLimitService, LinearTotalWeightLimitService, LinearTrailerTruckWeightLimitService, LinearWidthLimitService, MassTransitLaneService, NumberOfLanesService, ParkingProhibitionService, RoadWorkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopService
import fi.liikennevirasto.digiroad2.util.GeometryTransform
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

  private def getPersistedLinearAssetAsJson(asset: AssetOnExpiredLink):String = {
    val dynamicLinearAssetService = getDynamicLinearAssetService(asset.assetTypeId)
    val allAssetsOnLinkId = dynamicLinearAssetService.fetchExistingAssetsByLinksIdsString(asset.assetTypeId, Set(asset.linkId), Set(), newTransaction = false)
    val persistedAsset = allAssetsOnLinkId.filter(_.id == asset.id)
    val json = write(persistedAsset)
    json
  }

  private def getPersistedPointAssetAsJson(asset: AssetOnExpiredLink):String = {
    val dynamicPointAssetService = getDynamicPointAssetService(asset.assetTypeId)
    val allAssetsOnLinkId = dynamicPointAssetService.getPersistedAssetsByLinkIdWithoutTransaction(asset.linkId)
    val persistedAsset = allAssetsOnLinkId.filter(_.id == asset.id)
    val json = write(persistedAsset)
    json
  }

  def getAllWorkListAssets(newTransaction: Boolean = true): Seq[AssetOnExpiredLinkWithAssetProperties] = {
    if (newTransaction) withDynTransaction {
      val workListAssets = dao.fetchWorkListAssets()
      val enrichedAssets = workListAssets.map { asset =>
        val assetType = AssetTypeInfo.apply(asset.assetTypeId)
        val additionalJson =
          if (assetType.geometryType == "point")
            getPersistedPointAssetAsJson(asset)
          else if (assetType.geometryType == "linear")
            getPersistedLinearAssetAsJson(asset)
          else {
            logger.warn(s"Unknown asset type ${asset.assetTypeId} for ${asset.id}")
            write(Map.empty[String, String])
          }
        AssetOnExpiredLinkWithAssetProperties(asset, additionalJson)
      }

      enrichedAssets
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
      Queries.expireAssetsById(assetIds, userName)
      dao.deleteFromWorkList(assetIds)
    }
    else {
      Queries.expireAssetsById(assetIds, userName)
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
