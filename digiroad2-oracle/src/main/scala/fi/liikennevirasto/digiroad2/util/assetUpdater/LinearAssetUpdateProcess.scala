package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChangeClient, RoadLinkClient}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.util._
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer}
import org.slf4j.LoggerFactory

import scala.sys.exit

object LinearAssetUpdateProcess {

  lazy val eventbus: DigiroadEventBus = new DummyEventBus
  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventbus, new DummySerializer)

  lazy val dynamicLinearAssetService = new DynamicLinearAssetService(roadLinkService, eventbus)
  lazy val linearAssetService = new LinearAssetService(roadLinkService, eventbus)
  lazy val maintenanceService = new MaintenanceService(roadLinkService, eventbus)
  lazy val pavedRoadService = new PavedRoadService(roadLinkService, eventbus)
  lazy val prohibitionService = new ProhibitionService(roadLinkService, eventbus)
  lazy val hazMatTransportProhibitionService = new HazmatTransportProhibitionService(roadLinkService, eventbus)
  lazy val roadWidthService = new RoadWidthService(roadLinkService, eventbus)
  lazy val speedLimitService = new SpeedLimitService(eventbus, roadLinkService)

  //TODO remove this tester when LinearAssetUpdaters work with new change sets
  private def testFetchChangesFromS3() = {
    val roadLinkChangeClient = new RoadLinkChangeClient
    val logger = LoggerFactory.getLogger(getClass)
    val changes = roadLinkChangeClient.getRoadLinkChanges()
    logger.info(s"fetched ${changes.size} changes")
    changes.foreach(c => logger.info(c.toString))
  }

  private def getLinearAssetService(typeId: Int): LinearAssetOperations = {
    typeId match {
      case EuropeanRoads.typeId | ExitNumbers.typeId => new TextValueLinearAssetService(roadLinkService, eventbus)
      case NumberOfLanes.typeId => new NumberOfLanesService(roadLinkService, eventbus)
      case _ => linearAssetService
    }
  }

  private def getDynamicLinearAssetService(typeId: Int): DynamicLinearAssetService = {
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
      case _ => dynamicLinearAssetService
    }
  }

  def getAssetUpdater(typeId: Int): LinearAssetUpdater = {
    typeId match {
      case MaintenanceRoadAsset.typeId => new MaintenanceRoadUpdater(maintenanceService)
      case PavedRoad.typeId => new PavedRoadUpdater(pavedRoadService)
      case Prohibition.typeId => new ProhibitionUpdater(prohibitionService)
      case HazmatTransportProhibition.typeId => new HazMatTransportProhibitionUpdater(hazMatTransportProhibitionService)
      case RoadWidth.typeId => new RoadWidthUpdater(roadWidthService)
      case SpeedLimitAsset.typeId => new SpeedLimitUpdater(speedLimitService)
      case NumberOfLanes.typeId => new LinearAssetUpdater(getLinearAssetService(typeId))
      case TrafficVolume.typeId => new LinearAssetUpdater(getLinearAssetService(typeId))
      case WinterSpeedLimit.typeId => new LinearAssetUpdater(getLinearAssetService(typeId))
      case EuropeanRoads.typeId => new LinearAssetUpdater(getLinearAssetService(typeId))
      case ExitNumbers.typeId => new LinearAssetUpdater(getLinearAssetService(typeId))
      case _ => new DynamicLinearAssetUpdater(getDynamicLinearAssetService(typeId))
    }
  }

  lazy val roadLinkPropertyUpdater = new RoadLinkPropertyUpdater

  def main(args: Array[String]): Unit = {
    val batchMode = Digiroad2Properties.batchMode
    if (!batchMode) {
      println("*******************************************************************************************")
      println("TURN batchMode true TO RUN LINEAR ASSET UPDATER")
      println("*******************************************************************************************")
      exit()
    }

    if (args.length < 1) {
      println("Usage: LinearAssetUpdater <asset_name>")
    } else {
      val assetName = args(0)

      assetName match {
        // position, value and side code
        case "animal_warnings" => getAssetUpdater(AnimalWarnings.typeId).updateLinearAssets(AnimalWarnings.typeId)
        case "care_class" => getAssetUpdater(CareClass.typeId).updateLinearAssets(CareClass.typeId)
        case "height_limit" => getAssetUpdater(HeightLimit.typeId).updateLinearAssets(HeightLimit.typeId)
        case "length_limit" => getAssetUpdater(LengthLimit.typeId).updateLinearAssets(LengthLimit.typeId)
        case "width_limit" => getAssetUpdater(WidthLimit.typeId).updateLinearAssets(WidthLimit.typeId)
        case "total_weight_limit" => getAssetUpdater(TotalWeightLimit.typeId).updateLinearAssets(TotalWeightLimit.typeId)
        case "trailer_truck_weight_limit" => getAssetUpdater(TrailerTruckWeightLimit.typeId).updateLinearAssets(TrailerTruckWeightLimit.typeId)
        case "axle_weight_limit" => getAssetUpdater(AxleWeightLimit.typeId).updateLinearAssets(AxleWeightLimit.typeId)
        case "bogie_weight_limit" => getAssetUpdater(BogieWeightLimit.typeId).updateLinearAssets(BogieWeightLimit.typeId)
        case "mass_transit_lane" => getAssetUpdater(MassTransitLane.typeId).updateLinearAssets(MassTransitLane.typeId)
        case "number_of_lanes" => getAssetUpdater(NumberOfLanes.typeId).updateLinearAssets(NumberOfLanes.typeId)
        case "winter_speed_limit" => getAssetUpdater(WinterSpeedLimit.typeId).updateLinearAssets(WinterSpeedLimit.typeId)
        case "paved_roads" => getAssetUpdater(PavedRoad.typeId).updateLinearAssets(PavedRoad.typeId)
        case "speed_limit" => getAssetUpdater(SpeedLimitAsset.typeId).updateLinearAssets(SpeedLimitAsset.typeId)

        //  position, value, side code and validation period
        case "road_work_asset" => getAssetUpdater(RoadWorksAsset.typeId).updateLinearAssets(RoadWorksAsset.typeId)
        case "parking_prohibition" => getAssetUpdater(ParkingProhibition.typeId).updateLinearAssets(ParkingProhibition.typeId)
        case "hazmat_prohibition" => getAssetUpdater(HazmatTransportProhibition.typeId).updateLinearAssets(HazmatTransportProhibition.typeId)
        case "prohibition" => getAssetUpdater(Prohibition.typeId).updateLinearAssets(Prohibition.typeId)


        // regulars, position and/or value
        case "road_width" => getAssetUpdater(RoadWidth.typeId).updateLinearAssets(RoadWidth.typeId)
        case "damaged_by_thaw" => getAssetUpdater(DamagedByThaw.typeId).updateLinearAssets(DamagedByThaw.typeId)
        case "carrying_capacity" => getAssetUpdater(CarryingCapacity.typeId).updateLinearAssets(CarryingCapacity.typeId)
        case "lit_road" => getAssetUpdater(LitRoad.typeId).updateLinearAssets(LitRoad.typeId)
        case "traffic_volume" => getAssetUpdater(TrafficVolume.typeId).updateLinearAssets(TrafficVolume.typeId)
        case "european_roads" => getAssetUpdater(EuropeanRoads.typeId).updateLinearAssets(EuropeanRoads.typeId)
        case "exit_numbers" => getAssetUpdater(ExitNumbers.typeId).updateLinearAssets(ExitNumbers.typeId)
        case "maintenance_roads" => getAssetUpdater(MaintenanceRoadAsset.typeId).updateLinearAssets(MaintenanceRoadAsset.typeId)
        
        case "cycling_and_walking" => getAssetUpdater(CyclingAndWalking.typeId).updateLinearAssets(CyclingAndWalking.typeId)
        
        case "road_link_properties" => roadLinkPropertyUpdater.updateProperties()
        //special case       
        case "lanes" => LaneUpdater.updateLanes()
        case "manoeuvres" => new ManouvreUpdater()
        case "test" => testFetchChangesFromS3()
        case _ => throw new IllegalArgumentException("Invalid asset name.")
      }
    }
  }
}
