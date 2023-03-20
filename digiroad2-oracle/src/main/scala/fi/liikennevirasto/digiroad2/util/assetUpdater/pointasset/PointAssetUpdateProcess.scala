package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.{Obstacles, PedestrianCrossings, RailwayCrossings}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{ObstacleService, PedestrianCrossingService, RailwayCrossingService}
import fi.liikennevirasto.digiroad2.util._
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer, PointAssetOperations}

import scala.sys.exit

object PointAssetUpdateProcess {
  lazy val eventBus: DigiroadEventBus = new DummyEventBus
  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)

  private def getAssetUpdater(typeId: Int): PointAssetUpdater = {
    typeId match {
      case _ => new PointAssetUpdater(getPointAssetService(typeId))
    }
  }

  private def getPointAssetService(typeId: Int): PointAssetOperations = {
    typeId match {
      case PedestrianCrossings.typeId => new PedestrianCrossingService(roadLinkService, eventBus)
      case Obstacles.typeId => new ObstacleService(roadLinkService)
      case RailwayCrossings.typeId => new RailwayCrossingService(roadLinkService)
      case _ => throw new IllegalArgumentException("Invalid asset id")
    }
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
        case "pedestrian_crossing" => getAssetUpdater(PedestrianCrossings.typeId).updatePointAssets(PedestrianCrossings.typeId)
        case "obstacle" => getAssetUpdater(Obstacles.typeId).updatePointAssets(Obstacles.typeId)
        case "railway_crossing" => getAssetUpdater(RailwayCrossings.typeId).updatePointAssets(RailwayCrossings.typeId)
        case _ => throw new IllegalArgumentException("Invalid asset name")
      }
    }
  }
}
