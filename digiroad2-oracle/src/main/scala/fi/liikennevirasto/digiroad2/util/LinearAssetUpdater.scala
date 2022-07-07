package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{AnimalWarnings, AxleWeightLimit, BogieWeightLimit, CareClass, CarryingCapacity, CyclingAndWalking, DamagedByThaw, EuropeanRoads, ExitNumbers, HazmatTransportProhibition, HeightLimit, LengthLimit, LitRoad, MassTransitLane, NumberOfLanes, ParkingProhibition, Prohibition, RoadWorksAsset, TotalWeightLimit, TrafficVolume, TrailerTruckWeightLimit, WidthLimit, WinterSpeedLimit}
import fi.liikennevirasto.digiroad2.client.vvh.RoadLinkClient
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer}

import scala.sys.exit

object LinearAssetUpdater {

  lazy val eventbus: DigiroadEventBus = {
    new DummyEventBus
  }

  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, eventbus, new DummySerializer)
  }

  lazy val dynamicLinearAssetUpdateProcess = new DynamicLinearAssetUpdateProcess(roadLinkService, eventbus)
  lazy val linearAssetUpdateProcess = new LinearAssetUpdateProcess(roadLinkService, eventbus)
  lazy val maintenanceRoadUpdateProcess = new MaintenanceRoadUpdateProcess(roadLinkService, eventbus)
  lazy val pavedRoadUpdateProcess = new PavedRoadUpdateProcess(roadLinkService, eventbus)
  lazy val prohibitionUpdateProcess = new ProhibitionUpdateProcess(roadLinkService, eventbus)
  lazy val roadWidthUpdateProcess = new RoadWidthUpdateProcess(roadLinkService, eventbus)
  lazy val speedLimitUpdateProcess = new SpeedLimitUpdateProcess(eventbus, roadLinkClient, roadLinkService)

  def main(args: Array[String]) = {
    val batchMode = Digiroad2Properties.batchMode
    if (!batchMode) {
      println("*******************************************************************************************")
      println("TURN batchMode true TO RUN LINEAR ASSET UPDATER")
      println("*******************************************************************************************")
      exit()
    }

    if (args.size < 1) {
      println("Usage: LinearAssetUpdater <asset_name>")
    } else {
      val assetName = args(0)

      assetName match {
        case "animal_warnings" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(AnimalWarnings.typeId)
        case "care_class" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(CareClass.typeId)
        case "carrying_capacity" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(CarryingCapacity.typeId)
        case "damaged_by_thaw" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(DamagedByThaw.typeId)
        case "height_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(HeightLimit.typeId)
        case "length_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(LengthLimit.typeId)
        case "width_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(WidthLimit.typeId)
        case "total_weight_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(TotalWeightLimit.typeId)
        case "trailer_truck_weight_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(TrailerTruckWeightLimit.typeId)
        case "axle_weight_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(AxleWeightLimit.typeId)
        case "bogie_weight_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(BogieWeightLimit.typeId)
        case "mass_transit_lane" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(MassTransitLane.typeId)
        case "parking_prohibition" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(ParkingProhibition.typeId)
        case "cycling_and_walking" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(CyclingAndWalking.typeId)
        case "lit_road" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(LitRoad.typeId)
        case "road_work_asset" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(RoadWorksAsset.typeId)
        case "number_of_lanes" => linearAssetUpdateProcess.updateLinearAssets(NumberOfLanes.typeId)
        case "traffic_volume" => linearAssetUpdateProcess.updateLinearAssets(TrafficVolume.typeId)
        case "winter_speed_limit" => linearAssetUpdateProcess.updateLinearAssets(WinterSpeedLimit.typeId)
        case "european_roads" => linearAssetUpdateProcess.updateLinearAssets(EuropeanRoads.typeId)
        case "exit_numbers" => linearAssetUpdateProcess.updateLinearAssets(ExitNumbers.typeId)
        case "maintenance_roads" => maintenanceRoadUpdateProcess.updateMaintenanceRoads()
        case "paved_roads" => pavedRoadUpdateProcess.updatePavedRoads()
        case "prohibition" => prohibitionUpdateProcess.updateProhibitions(Prohibition.typeId)
        case "hazmat_prohibition" => prohibitionUpdateProcess.updateProhibitions(HazmatTransportProhibition.typeId)
        case "road_width" => roadWidthUpdateProcess.updateRoadWidth()
        case "speed_limit" => speedLimitUpdateProcess.updateSpeedLimits()
        case _ => throw new IllegalArgumentException("Invalid asset name.")
      }
    }
  }
}
