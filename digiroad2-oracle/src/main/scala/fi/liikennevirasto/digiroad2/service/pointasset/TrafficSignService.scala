package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.SideCode._
import fi.liikennevirasto.digiroad2.client.tierekisteri.TRTrafficSignType
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.pointasset.{OracleTrafficSignDao, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}

case class IncomingTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimpleProperty], validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset

sealed trait TrafficSignTypeGroup {
  def value: Int
}
object TrafficSignTypeGroup {
  val values = Set(Unknown, SpeedLimits, RegulatorySigns, MaximumRestrictions, GeneralWarningSigns, ProhibitionsAndRestrictions, AdditionalPanels, MandatorySigns,
    PriorityAndGiveWaySigns, InformationSigns)

  def apply(intValue: Int): TrafficSignTypeGroup = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object SpeedLimits extends TrafficSignTypeGroup { def value = 1  }
  case object RegulatorySigns extends TrafficSignTypeGroup { def value = 2 }
  case object MaximumRestrictions extends TrafficSignTypeGroup { def value = 3 }
  case object GeneralWarningSigns extends TrafficSignTypeGroup { def value = 4 }
  case object ProhibitionsAndRestrictions extends TrafficSignTypeGroup { def value = 5 }
  case object AdditionalPanels extends TrafficSignTypeGroup { def value = 6 }
  case object MandatorySigns extends TrafficSignTypeGroup { def value = 7 }
  case object PriorityAndGiveWaySigns extends TrafficSignTypeGroup { def value = 8 }
  case object InformationSigns extends TrafficSignTypeGroup { def value = 9 }
  case object ServiceSigns extends TrafficSignTypeGroup { def value = 10 }
  case object Unknown extends TrafficSignTypeGroup { def value = 99 }
}

sealed trait TrafficSignType {
  def value: Int
  def group: TrafficSignTypeGroup
}
object TrafficSignType {
  val values = Set(Unknown, SpeedLimit, EndSpeedLimit, SpeedLimitZone, EndSpeedLimitZone, UrbanArea, EndUrbanArea, PedestrianCrossing, MaximumLength, Warning, NoLeftTurn,
    NoRightTurn, NoUTurn, ClosedToAllVehicles, NoPowerDrivenVehicles, NoLorriesAndVans, NoVehicleCombinations, NoAgriculturalVehicles, NoMotorCycles, NoMotorSledges,
    NoVehiclesWithDangerGoods, NoBuses, NoMopeds, NoCyclesOrMopeds, NoPedestrians, NoPedestriansCyclesMopeds, NoRidersOnHorseback, NoEntry, OvertakingProhibited,
    EndProhibitionOfOvertaking, NoWidthExceeding, MaxHeightExceeding, MaxLadenExceeding, MaxMassCombineVehiclesExceeding, MaxTonsOneAxleExceeding, MaxTonsOnBogieExceeding,
    WRightBend, WLeftBend, WSeveralBendsRight, WSeveralBendsLeft, WDangerousDescent, WSteepAscent, WUnevenRoad, WChildren, TelematicSpeedLimit, FreeWidth, FreeHeight,
    HazmatProhibitionA, HazmatProhibitionB, ValidMonFri, ValidSat, TimeLimit, PassengerCar, Bus, Lorry, Van, VehicleForHandicapped, MotorCycle, Cycle, ParkingAgainstFee,
    ObligatoryUseOfParkingDisc, AdditionalPanelWithText, DrivingInServicePurposesAllowed, BusLane, BusLaneEnds, TramLane, BusStopForLocalTraffic,
    TramStop, TaxiStation, CompulsoryFootPath, CompulsoryCycleTrack, CombinedCycleTrackAndFootPath, DirectionToBeFollowed3,
    CompulsoryRoundabout, PassThisSide, TaxiStationZoneBeginning, StandingPlaceForTaxi, RoadNarrows, TwoWayTraffic, SwingBridge,
    RoadWorks, SlipperyRoad, PedestrianCrossingWarningSign, Cyclists, IntersectionWithEqualRoads, LightSignals, TramwayLine, FallingRocks, CrossWind, PriorityRoad, EndOfPriority,
    PriorityOverOncomingTraffic, PriorityForOncomingTraffic, GiveWay, Stop, ParkingLot, OneWayRoad, Motorway, MotorwayEnds, ResidentialZone, EndOfResidentialZone, PedestrianZone,
    EndOfPedestrianZone, NoThroughRoad, NoThroughRoadRight, SymbolOfMotorway, ItineraryForIndicatedVehicleCategory, ItineraryForPedestrians, ItineraryForHandicapped,
    LocationSignForTouristService, FirstAid, FillingStation, Restaurant, PublicLavatory, StandingAndParkingProhibited, ParkingProhibited, ParkingProhibitedZone,
    EndOfParkingProhibitedZone, AlternativeParkingOddDays, Parking)

  def apply(intValue: Int): TrafficSignType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  def apply(trafficSignTypeGroup: TrafficSignTypeGroup): Set[Int] = {
    values.filter(_.group == trafficSignTypeGroup).map(_.value)
  }

  case object SpeedLimit extends TrafficSignType { def value = 1;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object EndSpeedLimit extends TrafficSignType { def value = 2;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object SpeedLimitZone extends TrafficSignType { def value = 3;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object EndSpeedLimitZone extends TrafficSignType { def value = 4;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object UrbanArea extends TrafficSignType { def value = 5;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object EndUrbanArea extends TrafficSignType { def value = 6;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object PedestrianCrossing extends TrafficSignType { def value = 7;  def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object MaximumLength extends TrafficSignType { def value = 8;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object Warning extends TrafficSignType { def value = 9;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object NoLeftTurn extends TrafficSignType { def value = 10;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoRightTurn extends TrafficSignType { def value = 11;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoUTurn extends TrafficSignType { def value = 12;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object ClosedToAllVehicles extends TrafficSignType { def value = 13;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPowerDrivenVehicles extends TrafficSignType { def value = 14;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoLorriesAndVans extends TrafficSignType { def value = 15;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoVehicleCombinations extends TrafficSignType { def value = 16;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoAgriculturalVehicles extends TrafficSignType { def value = 17;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMotorCycles extends TrafficSignType { def value = 18;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMotorSledges extends TrafficSignType { def value = 19;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoVehiclesWithDangerGoods extends TrafficSignType { def value = 20;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoBuses extends TrafficSignType { def value = 21;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMopeds extends TrafficSignType { def value = 22;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoCyclesOrMopeds extends TrafficSignType { def value = 23;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPedestrians extends TrafficSignType { def value = 24;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPedestriansCyclesMopeds extends TrafficSignType { def value = 25;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoRidersOnHorseback extends TrafficSignType { def value = 26;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoEntry extends TrafficSignType { def value = 27;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object OvertakingProhibited extends TrafficSignType { def value = 28;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object EndProhibitionOfOvertaking extends TrafficSignType { def value = 29;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoWidthExceeding extends TrafficSignType { def value = 30;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxHeightExceeding extends TrafficSignType { def value = 31;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxLadenExceeding extends TrafficSignType { def value = 32;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxMassCombineVehiclesExceeding extends TrafficSignType { def value = 33;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxTonsOneAxleExceeding extends TrafficSignType { def value = 34;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxTonsOnBogieExceeding extends TrafficSignType { def value = 35;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object WRightBend extends TrafficSignType { def value = 36;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WLeftBend extends TrafficSignType { def value = 37;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WSeveralBendsRight extends TrafficSignType { def value = 38;  def group = TrafficSignTypeGroup.GeneralWarningSigns ; }
  case object WSeveralBendsLeft extends TrafficSignType { def value = 39;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WDangerousDescent extends TrafficSignType { def value = 40;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WSteepAscent extends TrafficSignType { def value = 41;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WUnevenRoad extends TrafficSignType { def value = 42;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WChildren extends TrafficSignType { def value = 43;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object TelematicSpeedLimit extends TrafficSignType { def value = 44;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object FreeWidth extends TrafficSignType { def value = 45;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object FreeHeight extends TrafficSignType { def value = 46;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object HazmatProhibitionA extends TrafficSignType { def value = 47;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object HazmatProhibitionB extends TrafficSignType { def value = 48;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object ValidMonFri extends TrafficSignType { def value = 49;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object ValidSat extends TrafficSignType { def value = 50;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object TimeLimit extends TrafficSignType { def value = 51;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object PassengerCar extends TrafficSignType { def value = 52;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object Bus extends TrafficSignType { def value = 53;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object Lorry extends TrafficSignType { def value = 54;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object Van extends TrafficSignType { def value = 55;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object VehicleForHandicapped extends TrafficSignType { def value = 56;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object MotorCycle extends TrafficSignType { def value = 57;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object Cycle extends TrafficSignType { def value = 58;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object ParkingAgainstFee extends TrafficSignType { def value = 59;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object ObligatoryUseOfParkingDisc extends TrafficSignType { def value = 60;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object AdditionalPanelWithText extends TrafficSignType { def value = 61;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object DrivingInServicePurposesAllowed extends TrafficSignType { def value = 62;  def group = TrafficSignTypeGroup.AdditionalPanels; }
  case object BusLane extends TrafficSignType { def value = 63;  def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object BusLaneEnds extends TrafficSignType { def value = 64;  def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object TramLane extends TrafficSignType { def value = 65;  def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object BusStopForLocalTraffic extends TrafficSignType { def value = 66;  def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object TramStop extends TrafficSignType { def value = 68;  def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object TaxiStation extends TrafficSignType { def value = 69;  def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object CompulsoryFootPath extends TrafficSignType { def value = 70;  def group = TrafficSignTypeGroup.MandatorySigns; }
  case object CompulsoryCycleTrack extends TrafficSignType { def value = 71;  def group = TrafficSignTypeGroup.MandatorySigns; }
  case object CombinedCycleTrackAndFootPath extends TrafficSignType { def value = 72;  def group = TrafficSignTypeGroup.MandatorySigns; }
  case object DirectionToBeFollowed3 extends TrafficSignType { def value = 74;  def group = TrafficSignTypeGroup.MandatorySigns; }
  case object CompulsoryRoundabout extends TrafficSignType { def value = 77;  def group = TrafficSignTypeGroup.MandatorySigns; }
  case object PassThisSide extends TrafficSignType { def value = 78;  def group = TrafficSignTypeGroup.MandatorySigns; }
  case object TaxiStationZoneBeginning extends TrafficSignType { def value = 80;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object StandingPlaceForTaxi extends TrafficSignType { def value = 81;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object RoadNarrows extends TrafficSignType { def value = 82; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object TwoWayTraffic extends TrafficSignType { def value = 83; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object SwingBridge extends TrafficSignType { def value = 84; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object RoadWorks extends TrafficSignType { def value = 85; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object SlipperyRoad extends TrafficSignType { def value = 86; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object PedestrianCrossingWarningSign extends TrafficSignType { def value = 87; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object Cyclists extends TrafficSignType { def value = 88; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object IntersectionWithEqualRoads extends TrafficSignType { def value = 89; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object LightSignals extends TrafficSignType { def value = 90; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object TramwayLine extends TrafficSignType { def value = 91; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object FallingRocks extends TrafficSignType { def value = 92; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object CrossWind extends TrafficSignType { def value = 93; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object PriorityRoad extends TrafficSignType { def value = 94; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; }
  case object EndOfPriority extends TrafficSignType { def value = 95; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; }
  case object PriorityOverOncomingTraffic extends TrafficSignType { def value = 96; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; }
  case object PriorityForOncomingTraffic extends TrafficSignType { def value = 97; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; }
  case object GiveWay extends TrafficSignType { def value = 98; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; }
  case object Stop extends TrafficSignType { def value = 99; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; }
  case object StandingAndParkingProhibited extends TrafficSignType { def value = 100; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object ParkingProhibited extends TrafficSignType { def value = 101; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object ParkingProhibitedZone extends TrafficSignType { def value = 102; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object EndOfParkingProhibitedZone extends TrafficSignType { def value = 103; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object AlternativeParkingOddDays extends TrafficSignType { def value = 104; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object ParkingLot extends TrafficSignType { def value = 105; def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object OneWayRoad extends TrafficSignType { def value = 106; def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object Motorway extends TrafficSignType { def value = 107; def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object MotorwayEnds extends TrafficSignType { def value = 108; def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object ResidentialZone extends TrafficSignType { def value = 109; def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object EndOfResidentialZone extends TrafficSignType { def value = 110; def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object PedestrianZone extends TrafficSignType { def value = 111; def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object EndOfPedestrianZone extends TrafficSignType { def value = 112; def group = TrafficSignTypeGroup.RegulatorySigns; }
  case object NoThroughRoad extends TrafficSignType { def value = 113; def group = TrafficSignTypeGroup.InformationSigns; }
  case object NoThroughRoadRight extends TrafficSignType { def value = 114; def group = TrafficSignTypeGroup.InformationSigns; }
  case object SymbolOfMotorway extends TrafficSignType { def value = 115; def group = TrafficSignTypeGroup.InformationSigns; }
  case object Parking extends TrafficSignType { def value = 116; def group = TrafficSignTypeGroup.InformationSigns; }
  case object ItineraryForIndicatedVehicleCategory extends TrafficSignType { def value = 117; def group = TrafficSignTypeGroup.InformationSigns; }
  case object ItineraryForPedestrians extends TrafficSignType { def value = 118; def group = TrafficSignTypeGroup.InformationSigns; }
  case object ItineraryForHandicapped extends TrafficSignType { def value = 119; def group = TrafficSignTypeGroup.InformationSigns; }
  case object LocationSignForTouristService extends TrafficSignType { def value = 120; def group = TrafficSignTypeGroup.ServiceSigns; }
  case object FirstAid extends TrafficSignType { def value = 121; def group = TrafficSignTypeGroup.ServiceSigns; }
  case object FillingStation extends TrafficSignType { def value = 122; def group = TrafficSignTypeGroup.ServiceSigns; }
  case object Restaurant extends TrafficSignType { def value = 123; def group = TrafficSignTypeGroup.ServiceSigns; }
  case object PublicLavatory extends TrafficSignType { def value = 124; def group = TrafficSignTypeGroup.ServiceSigns; }
  case object Unknown extends TrafficSignType { def value = 999;  def group = TrafficSignTypeGroup.Unknown; }
}

class TrafficSignService(val roadLinkService: RoadLinkService, val userProvider: UserProvider) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficSign
  type PersistedAsset = PersistedTrafficSign

  override def typeId: Int = 300

  override def setAssetPosition(asset: IncomingTrafficSign, geometry: Seq[Point], mValue: Double): IncomingTrafficSign = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  private val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val infoPublicId = "trafficSigns_info"

  private val additionalInfoTypeGroups =
    Set(
      TrafficSignTypeGroup.GeneralWarningSigns,
      TrafficSignTypeGroup.ProhibitionsAndRestrictions,
      TrafficSignTypeGroup.AdditionalPanels
    )

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedTrafficSign] = OracleTrafficSignDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: PersistedTrafficSign, floating: Boolean): PersistedTrafficSign = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingTrafficSign, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    withDynTransaction {
      OracleTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  def createFloating(asset: IncomingTrafficSign, username: String, municipality: Int): Long = {
    withDynTransaction {
      OracleTrafficSignDao.createFloating(asset, 0, username, municipality, VVHClient.createVVHTimeStamp(), LinkGeomSource.Unknown, floating = true)
    }
  }

  override def update(id: Long, updatedAsset: IncomingTrafficSign, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, geometry, municipality, username, linkSource, None, None)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingTrafficSign, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource, mValue: Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), geometry))
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if old.bearing != updatedAsset.bearing || (old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) =>
        expireWithoutTransaction(id)
        OracleTrafficSignDao.create(setAssetPosition(updatedAsset, geometry, value), value, username, municipality, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), linkSource, old.createdBy, old.createdAt)
      case _ =>
        OracleTrafficSignDao.update(id, setAssetPosition(updatedAsset, geometry, value), value, municipality, username, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), linkSource)
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    val updated = IncomingTrafficSign(adjustment.lon, adjustment.lat, adjustment.linkId,
      persistedAsset.propertyData.map(prop => SimpleProperty(prop.publicId, prop.values)).toSet,
      persistedAsset.validityDirection, persistedAsset.bearing)

    updateWithoutTransaction(adjustment.assetId, updated, roadLink.geometry, persistedAsset.municipalityCode,
      "vvh_generated", persistedAsset.linkSource, Some(adjustment.mValue), Some(adjustment.vvhTimeStamp))
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation), withMunicipality(municipalityCode))
  }

  def withMunicipalityAndGroup(municipalityCode: Int, trafficSignTypes: Set[Int])(query: String): String = {
    withFilter(s"""where a.asset_type_id = $typeId and a.municipality_code = $municipalityCode
                    and a.id in( select at.id
                        from asset at
                        join property p on at.asset_type_id = p.asset_type_id
                        join single_choice_value scv on scv.asset_id = at.id and scv.property_id = p.id and p.property_type = 'single_choice'
                        join enumerated_value ev on scv.enumerated_value_id = ev.id and ev.value in (${trafficSignTypes.mkString(",")})""")(query)

  }

  def getByMunicipalityAndGroup(municipalityCode: Int, groupName: TrafficSignTypeGroup): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    val assetTypes = TrafficSignType.apply(groupName)
    getByMunicipality(mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation), withMunicipalityAndGroup(municipalityCode, assetTypes))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {
    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.propertyData, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, persistedStop.validityDirection, persistedStop.bearing,
      persistedStop.linkSource)
  }

  def getAssetValidityDirection(bearing: Int): Int = {
    bearing >= 270 || bearing < 90 match {
      case true => AgainstDigitizing.value
      case false => TowardsDigitizing.value
    }
  }

  def getTrafficSignValidityDirection(assetLocation: Point, geometry: Seq[Point]): Int = {

    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(assetLocation.x, assetLocation.y, 0), geometry)
    val roadLinkPoint = GeometryUtils.calculatePointFromLinearReference(geometry, mValue)
    val linkBearing = GeometryUtils.calculateBearing(geometry)

    val lonDifference = assetLocation.x - roadLinkPoint.get.x
    val latDifference = assetLocation.y - roadLinkPoint.get.y

    (latDifference <= 0 && linkBearing <= 90) || (latDifference >= 0 && linkBearing > 270) match {
      case true => TowardsDigitizing.value
      case false => AgainstDigitizing.value
    }
  }

  def getAssetBearing(validityDirection: Int, geometry: Seq[Point]): Int = {
    val linkBearing = GeometryUtils.calculateBearing(geometry)
    GeometryUtils.calculateActualBearing(validityDirection, Some(linkBearing)).get
  }

  private def generateProperties(tRTrafficSignType: TRTrafficSignType, value: Any, additionalInfo: String) = {
    val signValue = value.toString
    val signAdditionalInfo = additionalInfo
    val trafficType = tRTrafficSignType.trafficSignType
    val typeProperty = SimpleProperty(typePublicId, Seq(PropertyValue(trafficType.value.toString)))
    val valueProperty = additionalInfoTypeGroups.exists(group => group == trafficType.group) match {
      case true => SimpleProperty(infoPublicId, Seq(PropertyValue(signAdditionalInfo)))
      case _ => SimpleProperty(valuePublicId, Seq(PropertyValue(signValue)))
    }

    Set(typeProperty, valueProperty)
  }

  def createFromCoordinates(lon: Long, lat: Long, trafficSignType: TRTrafficSignType, value: Option[Int], twoSided: Option[Boolean], trafficDirection: TrafficDirection, bearing: Option[Int], additionalInfo: Option[String]): Long = {
    val roadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(userProvider.getCurrentUser(), Point(lon, lat))
    if (roadLinks.nonEmpty) {
      val closestLink = roadLinks.minBy(r => GeometryUtils.minimumDistance(Point(lon, lat), r.geometry))
      val (vvhRoad, municipality) = (roadLinks.filter(_.administrativeClass != State), closestLink.municipalityCode)
      if (vvhRoad.isEmpty || vvhRoad.size > 1) {
        val asset = IncomingTrafficSign(lon, lat, 0, generateProperties(trafficSignType, value.getOrElse(""), additionalInfo.getOrElse("")), 0, None)
        createFloating(asset, userProvider.getCurrentUser().username, municipality)
      }
      else {
        roadLinkService.getRoadLinkFromVVH(closestLink.linkId).map { link =>
          val validityDirection =
            bearing match {
              case Some(assetBearing) if twoSided.getOrElse(false) => BothDirections.value
              case Some(assetBearing) => getAssetValidityDirection(assetBearing)
              case None if twoSided.getOrElse(false) => BothDirections.value
              case _ => getTrafficSignValidityDirection(Point(lon, lat), link.geometry)
            }
          val asset = IncomingTrafficSign(lon, lat, link.linkId, generateProperties(trafficSignType, value.getOrElse(""), additionalInfo.getOrElse("")), validityDirection, Some(GeometryUtils.calculateBearing(link.geometry)))
          create(asset, userProvider.getCurrentUser().username, link)
        }.getOrElse(0L)
      }
    } else {
      0L
    }
  }

  def getTrafficSignByRadius(position: Point, meters: Int, optGroupType: Option[TrafficSignTypeGroup]): Seq[PersistedTrafficSign] = {
    val assets = OracleTrafficSignDao.fetchByRadius(position, meters)
    optGroupType match {
      case Some(groupType) => assets.filter(asset => TrafficSignTypeGroup.apply(asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) == groupType)
      case _ => assets
    }
  }
}
