package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, PointAssetOperations}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetOnExpiredRoadLink, AssetsOnExpiredLinksDAO, MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset._
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
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

  private def getAssetsAndMapProperties(assetType:  AssetTypeInfo, workList: Seq[AssetOnExpiredLink], assetLinks: Seq[String]): Seq[AssetOnExpiredLinkWithAssetProperties] = {
    assetType.geometryType match {
      case "point" =>
        val dynamicPointAssetService = getDynamicPointAssetService(assetType.typeId)
        val allAssetsOnLinkId = dynamicPointAssetService.getPersistedAssetsByLinkIdsWithoutTransaction(assetLinks.toSet)
        workList.map(a => {
          val properties = allAssetsOnLinkId.filter(_.id == a.id)
          val json = write(properties)
          AssetOnExpiredLinkWithAssetProperties(a, json)
        })
      case "linear" =>
        val dynamicPointAssetService = getDynamicLinearAssetService(assetType.typeId)
        val allAssetsOnLinkId = dynamicPointAssetService.fetchExistingAssetsByLinksIdsString(assetType.typeId,assetLinks.toSet,Set(), newTransaction = false)
        workList.map(a => {
          val properties = allAssetsOnLinkId.filter(_.id == a.id)
          val json = write(properties)
          AssetOnExpiredLinkWithAssetProperties(a, json)
        })
      case _ =>
        logger.warn(s"Unsupported asset type: ${assetType.typeId}")
        Seq.empty[AssetOnExpiredLinkWithAssetProperties]
    }
  }

  def getAllWorkListAssets(newTransaction: Boolean = true): Seq[AssetOnExpiredLinkWithAssetProperties] = {
    if (newTransaction) withDynTransaction {
      val workListAssets = dao.fetchWorkListAssets()
      val groupedByAssetType = workListAssets.groupBy(a=>a.assetTypeId)
      groupedByAssetType.flatMap { assetsByType =>
        LogUtils.time(logger, s"Getting assets and mapping properties") {
          getAssetsAndMapProperties(AssetTypeInfo.apply(assetsByType._1), assetsByType._2, assetsByType._2.map(a => a.linkId))
        }
      }.toList
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
