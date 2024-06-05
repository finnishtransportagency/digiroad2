package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopService
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util._
import fi.liikennevirasto.digiroad2.util.assetUpdater.LinearAssetUpdateProcess.{getClass, logger}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer, PointAssetOperations}
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.{Logger, LoggerFactory}

import scala.sys.exit

object PointAssetUpdateProcess {
  lazy val eventBus: DigiroadEventBus = new DummyEventBus
  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient()
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)

  lazy val viiteClient: SearchViiteClient = new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
  lazy val roadAddressService: RoadAddressService = new RoadAddressService(viiteClient)
  lazy val massTransitStopService: MassTransitStopService = {
    class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new MassTransitStopServiceWithDynTransaction(eventBus, roadLinkService, roadAddressService)
  }

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private def getAssetUpdater(typeId: Int): PointAssetUpdater = {
    val directionalPointAssets = List(DirectionalTrafficSigns.typeId, TrafficLights.typeId)
    typeId match {
      case id if id == MassTransitStopAsset.typeId =>
        new MassTransitStopUpdater(massTransitStopService)
      case id if id == TrafficSigns.typeId =>
        new TrafficSignUpdater(getPointAssetService(typeId))
      case id if directionalPointAssets.contains(id) =>
        new DirectionalPointAssetUpdater(getPointAssetService(typeId))
      case _ =>
        new PointAssetUpdater(getPointAssetService(typeId))
    }
  }

  private def getPointAssetService(typeId: Int): PointAssetOperations = {
    typeId match {
      case PedestrianCrossings.typeId => new PedestrianCrossingService(roadLinkService, eventBus)
      case Obstacles.typeId => new ObstacleService(roadLinkService)
      case RailwayCrossings.typeId => new RailwayCrossingService(roadLinkService)
      case DirectionalTrafficSigns.typeId => new DirectionalTrafficSignService(roadLinkService)
      case TrafficSigns.typeId => new TrafficSignService(roadLinkService, eventBus)
      case TrafficLights.typeId => new TrafficLightService(roadLinkService)
      case _ => throw new IllegalArgumentException("Invalid asset id")
    }
  }

  private def runUpdateProcess(typeId: Int): Unit = {
    getAssetUpdater(typeId).updatePointAssets(typeId)
  }

  def main(args: Array[String]): Unit = {
    if (!Digiroad2Properties.batchMode) {
      println("*******************************************************************************************")
      println("TURN batchMode true TO RUN POINT ASSET UPDATER")
      println("*******************************************************************************************")
      exit()
    }

    if (args.length < 1) {
      println("Usage: PointAssetUpdater <asset_name>")
    } else {
      val assetName = args(0)
      logger.info(s"Starting samuutus with parameter: $assetName")
      assetName match {
        case "pedestrian_crossing" => runUpdateProcess(PedestrianCrossings.typeId)
        case "obstacle" => runUpdateProcess(Obstacles.typeId)
        case "railway_crossing" => runUpdateProcess(RailwayCrossings.typeId)
        case "directional_traffic_sign" => runUpdateProcess(DirectionalTrafficSigns.typeId)
        case "traffic_sign" => runUpdateProcess(TrafficSigns.typeId)
        case "traffic_light" => runUpdateProcess(TrafficLights.typeId)
        case "mass_transit_stop" => runUpdateProcess(MassTransitStopAsset.typeId)
        case _ => throw new IllegalArgumentException("Invalid asset name")
      }
      logger.info(s"Ending samuutus with parameter: $assetName")
    }
  }
}
