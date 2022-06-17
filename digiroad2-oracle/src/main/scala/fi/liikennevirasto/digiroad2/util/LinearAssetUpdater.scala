package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{AnimalWarnings, AxleWeightLimit, BogieWeightLimit, CareClass, CarryingCapacity, CyclingAndWalking, DamagedByThaw, EuropeanRoads, ExitNumbers, HazmatTransportProhibition, HeightLimit, LengthLimit, LitRoad, MassTransitLane, NumberOfLanes, ParkingProhibition, Prohibition, RoadWorksAsset, TotalWeightLimit, TrafficVolume, TrailerTruckWeightLimit, WidthLimit, WinterSpeedLimit}
import fi.liikennevirasto.digiroad2.client.vvh.RoadLinkClient
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer}

object LinearAssetUpdater {

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
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

  def updater(args: String) = {
    args match {
      case "update_animal_warnings" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(AnimalWarnings.typeId)
      case "update_care_class" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(CareClass.typeId)
      case "update_carrying_capacity" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(CarryingCapacity.typeId)
      case "update_damaged_by_thaw" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(DamagedByThaw.typeId)
      case "update_height_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(HeightLimit.typeId)
      case "update_length_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(LengthLimit.typeId)
      case "update_width_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(WidthLimit.typeId)
      case "update_total_weight_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(TotalWeightLimit.typeId)
      case "update_trailer_truck_weight_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(TrailerTruckWeightLimit.typeId)
      case "update_axle_weight_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(AxleWeightLimit.typeId)
      case "update_bogie_weight_limit" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(BogieWeightLimit.typeId)
      case "update_mass_transit_lane" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(MassTransitLane.typeId)
      case "update_parking_prohibition" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(ParkingProhibition.typeId)
      case "update_cycling_and_walking" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(CyclingAndWalking.typeId)
      case "update_lit_road" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(LitRoad.typeId)
      case "update_road_work_asset" => dynamicLinearAssetUpdateProcess.updateDynamicLinearAssets(RoadWorksAsset.typeId)
      case "update_number_of_lanes" => linearAssetUpdateProcess.updateLinearAssets(NumberOfLanes.typeId)
      case "update_traffic_volume" => linearAssetUpdateProcess.updateLinearAssets(TrafficVolume.typeId)
      case "update_winter_speed_limit" => linearAssetUpdateProcess.updateLinearAssets(WinterSpeedLimit.typeId)
      case "update_european_road_numbers" => linearAssetUpdateProcess.updateLinearAssets(EuropeanRoads.typeId)
      case "update_exit_numbers" => linearAssetUpdateProcess.updateLinearAssets(ExitNumbers.typeId)
      case "update_maintenance_roads" => maintenanceRoadUpdateProcess.updateMaintenanceRoads()
      case "update_paved_roads" => pavedRoadUpdateProcess.updatePavedRoads()
      case "update_prohibitions" => prohibitionUpdateProcess.updateProhibitions(Prohibition.typeId)
      case "update_hazmat_prohibitions" => prohibitionUpdateProcess.updateProhibitions(HazmatTransportProhibition.typeId)
      case "update_road_width" => roadWidthUpdateProcess.updateRoadWidth()
      case "update_speed_limits" => speedLimitUpdateProcess.updateSpeedLimits()
      case _ => throw new IllegalArgumentException("No such operation parameter.")
    }
  }
}
