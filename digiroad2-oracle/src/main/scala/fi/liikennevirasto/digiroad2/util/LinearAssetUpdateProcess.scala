package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{AnimalWarnings, AxleWeightLimit, BogieWeightLimit, CareClass, CarryingCapacity, CyclingAndWalking, DamagedByThaw, EuropeanRoads, ExitNumbers, HazmatTransportProhibition, HeightLimit, LengthLimit, LitRoad, MaintenanceRoadAsset, MassTransitLane, NumberOfLanes, ParkingProhibition, PavedRoad, Prohibition, RoadWidth, RoadWorksAsset, TotalWeightLimit, TrafficVolume, TrailerTruckWeightLimit, WidthLimit, WinterSpeedLimit}
import fi.liikennevirasto.digiroad2.client.vvh.RoadLinkClient
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{CyclingAndWalkingService, DamagedByThawService, DynamicLinearAssetService, HazmatTransportProhibitionService, LinearAssetOperations, LinearAssetService, LinearAxleWeightLimitService, LinearBogieWeightLimitService, LinearHeightLimitService, LinearLengthLimitService, LinearTotalWeightLimitService, LinearTrailerTruckWeightLimitService, LinearWidthLimitService, MaintenanceService, MassTransitLaneService, NumberOfLanesService, ParkingProhibitionService, ProhibitionService, RoadWidthService, RoadWorkService, SpeedLimitService, TextValueLinearAssetService}
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer}

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
  lazy val speedLimitService = new SpeedLimitService(eventbus, roadLinkClient, roadLinkService)

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

  def dynamicLinearAssetUpdater(typeId: Int) = new DynamicLinearAssetUpdater(getDynamicLinearAssetService(typeId))

  def linearAssetUpdater(typeId: Int) = new LinearAssetUpdater(getLinearAssetService(typeId))

  lazy val maintenanceRoadUpdater = new MaintenanceRoadUpdater(maintenanceService)
  lazy val pavedRoadUpdater = new PavedRoadUpdater(pavedRoadService)
  lazy val prohibitionUpdater = new ProhibitionUpdater(prohibitionService)
  lazy val hazMatTransportProhibitionUpdater = new HazMatTransportProhibitionUpdater(hazMatTransportProhibitionService)
  lazy val roadWidthUpdater = new RoadWidthUpdater(roadWidthService)
  lazy val speedLimitUpdater = new SpeedLimitUpdater(eventbus, roadLinkClient, roadLinkService, speedLimitService)

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
        case "animal_warnings" => linearAssetUpdater(AnimalWarnings.typeId).updateLinearAssets(AnimalWarnings.typeId)
        case "care_class" => dynamicLinearAssetUpdater(CareClass.typeId).updateLinearAssets(CareClass.typeId)
        case "carrying_capacity" => dynamicLinearAssetUpdater(CarryingCapacity.typeId).updateLinearAssets(CarryingCapacity.typeId)
        case "damaged_by_thaw" => dynamicLinearAssetUpdater(DamagedByThaw.typeId).updateLinearAssets(DamagedByThaw.typeId)
        case "height_limit" => dynamicLinearAssetUpdater(HeightLimit.typeId).updateLinearAssets(HeightLimit.typeId)
        case "length_limit" => dynamicLinearAssetUpdater(LengthLimit.typeId).updateLinearAssets(LengthLimit.typeId)
        case "width_limit" => dynamicLinearAssetUpdater(WidthLimit.typeId).updateLinearAssets(WidthLimit.typeId)
        case "total_weight_limit" => dynamicLinearAssetUpdater(TotalWeightLimit.typeId).updateLinearAssets(TotalWeightLimit.typeId)
        case "trailer_truck_weight_limit" => dynamicLinearAssetUpdater(TrailerTruckWeightLimit.typeId).updateLinearAssets(TrailerTruckWeightLimit.typeId)
        case "axle_weight_limit" => dynamicLinearAssetUpdater(AxleWeightLimit.typeId).updateLinearAssets(AxleWeightLimit.typeId)
        case "bogie_weight_limit" => dynamicLinearAssetUpdater(BogieWeightLimit.typeId).updateLinearAssets(BogieWeightLimit.typeId)
        case "mass_transit_lane" => dynamicLinearAssetUpdater(MassTransitLane.typeId).updateLinearAssets(MassTransitLane.typeId)
        case "parking_prohibition" => dynamicLinearAssetUpdater(ParkingProhibition.typeId).updateLinearAssets(ParkingProhibition.typeId)
        case "cycling_and_walking" => dynamicLinearAssetUpdater(CyclingAndWalking.typeId).updateLinearAssets(CyclingAndWalking.typeId)
        case "lit_road" => dynamicLinearAssetUpdater(LitRoad.typeId).updateLinearAssets(LitRoad.typeId)
        case "road_work_asset" => dynamicLinearAssetUpdater(RoadWorksAsset.typeId).updateLinearAssets(RoadWorksAsset.typeId)
        case "number_of_lanes" => linearAssetUpdater(NumberOfLanes.typeId).updateLinearAssets(NumberOfLanes.typeId)
        case "traffic_volume" => linearAssetUpdater(TrafficVolume.typeId).updateLinearAssets(TrafficVolume.typeId)
        case "winter_speed_limit" => linearAssetUpdater(WinterSpeedLimit.typeId).updateLinearAssets(WinterSpeedLimit.typeId)
        case "european_roads" => linearAssetUpdater(EuropeanRoads.typeId).updateLinearAssets(EuropeanRoads.typeId)
        case "exit_numbers" => linearAssetUpdater(ExitNumbers.typeId).updateLinearAssets(ExitNumbers.typeId)
        case "maintenance_roads" => maintenanceRoadUpdater.updateLinearAssets(MaintenanceRoadAsset.typeId)
        case "paved_roads" => pavedRoadUpdater.updateLinearAssets(PavedRoad.typeId)
        case "prohibition" => prohibitionUpdater.updateLinearAssets(Prohibition.typeId)
        case "hazmat_prohibition" => hazMatTransportProhibitionUpdater.updateLinearAssets(HazmatTransportProhibition.typeId)
        case "road_width" => roadWidthUpdater.updateLinearAssets(RoadWidth.typeId)
        case "speed_limit" => speedLimitUpdater.updateSpeedLimits()
        case _ => throw new IllegalArgumentException("Invalid asset name.")
      }
    }
  }
}

