package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.{DirectionalTrafficSigns, Obstacles, PedestrianCrossings, RailwayCrossings, TrafficLights, TrafficSigns}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{DirectionalTrafficSignService, ObstacleService, PedestrianCrossingService, RailwayCrossingService, TrafficLightService, TrafficSignService}
import fi.liikennevirasto.digiroad2.util._
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer, PointAssetOperations}

import scala.sys.exit

object PointAssetUpdateProcess {
  lazy val eventBus: DigiroadEventBus = new DummyEventBus
  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)

  private def getAssetUpdater(typeId: Int): PointAssetUpdater = {
    val directionalPointAssets = List(DirectionalTrafficSigns.typeId, TrafficSigns.typeId, TrafficLights.typeId)
    typeId match {
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

      assetName match {
        case "pedestrian_crossing" => runUpdateProcess(PedestrianCrossings.typeId)
        case "obstacle" => runUpdateProcess(Obstacles.typeId)
        case "railway_crossing" => runUpdateProcess(RailwayCrossings.typeId)
        case "directional_traffic_sign" => runUpdateProcess(DirectionalTrafficSigns.typeId)
        case "traffic_sign" => runUpdateProcess(TrafficSigns.typeId)
        case "traffic_light" => runUpdateProcess(TrafficLights.typeId)
        case _ => throw new IllegalArgumentException("Invalid asset name")
      }
    }
  }
}
