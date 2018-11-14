package fi.liikennevirasto.digiroad2.client.tierekisteri

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignType, TrafficSignTypeGroup}
import fi.liikennevirasto.digiroad2.util.TierekisteriAuthPropertyReader
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpRequestBase, _}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, StreamInput}
import org.slf4j.LoggerFactory

/**
  * Values for Road side (Puoli) enumeration
  */
sealed trait TRRoadSide {
  def value: String
  def propertyValues: Set[Int]
}
object TRRoadSide {
  val values = Set(Right, Left, Off, Unknown)

  def apply(value: String): TRRoadSide = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def propertyValues() : Set[Int] = {
    values.flatMap(_.propertyValues)
  }

  case object Right extends TRRoadSide { def value = "oikea"; def propertyValues = Set(1) }
  case object Left extends TRRoadSide { def value = "vasen"; def propertyValues = Set(2) }
  case object Off extends TRRoadSide { def value = "paassa"; def propertyValues = Set(99) } // Not supported by OTH
  case object Unknown extends TRRoadSide { def value = "ei tietoa"; def propertyValues = Set(0) }
}

/**
  * Values for traffic sign types enumeration
  */
sealed trait TRTrafficSignType {
  def value: Int
  def trafficSignType: TrafficSignType
  def source: Seq[String]
}
object TRTrafficSignType {
  //TODO Once the object is used for CSVimport too, create a generic one next time we work with those types
  val values = Set(SpeedLimit, EndSpeedLimit, SpeedLimitZone, EndSpeedLimitZone, UrbanArea, EndUrbanArea, PedestrianCrossing, MaximumLength, Warning, NoLeftTurn, NoRightTurn, NoUTurn,
    ClosedToAllVehicles, NoPowerDrivenVehicles, NoLorriesAndVans, NoVehicleCombinations, NoAgriculturalVehicles, NoMotorCycles, NoMotorSledges, NoVehiclesWithDangerGoods,
    NoBuses, NoMopeds, NoCyclesOrMopeds, NoPedestrians, NoPedestriansCyclesMopeds, NoRidersOnHorseback, NoEntry, OvertakingProhibited, EndProhibitionOfOvertaking,
    MaxWidthExceeding, MaxHeightExceeding, MaxLadenExceeding, MaxMassCombineVehiclesExceeding, MaxTonsOneAxleExceeding, MaxTonsOnBogieExceeding, WRightBend, WLeftBend,
    WSeveralBendsRight, WSeveralBendsLeft, WDangerousDescent, WSteepAscent, WUnevenRoad, WChildren, TelematicSpeedLimit, FreeWidth, FreeHeight, HazmatProhibitionA, HazmatProhibitionB, ValidMonFri, ValidSat,
    TimeLimit, PassengerCar, Bus, Lorry, Van, VehicleForHandicapped, MotorCycle, Cycle, ParkingAgainstFee, ObligatoryUseOfParkingDisc, AdditionalPanelWithText, DrivingInServicePurposesAllowed, BusLane,
    BusLaneEnds, TramLane, BusStopForLocalTraffic, BusStopForLongDistanceTraffic, TramStop, TaxiStation, CompulsoryFootPath, CompulsoryCycleTrack, CombinedCycleTrackAndFootPath, ParallelCycleTrackAndFootPath,
    ParallelCycleTrackAndFootPath2, DirectionToBeFollowed3, DirectionToBeFollowed4, DirectionToBeFollowed5, CompulsoryRoundabout, PassThisSide, DividerOfTraffic, TaxiStationZoneBeginning, StandingPlaceForTaxi,
    RoadNarrows, TwoWayTraffic, SwingBridge, RoadWorks, SlipperyRoad, PedestrianCrossingWarningSign, Cyclists, IntersectionWithEqualRoads, LightSignals, TramwayLine, FallingRocks, CrossWind, PriorityRoad, EndOfPriority,
    PriorityOverOncomingTraffic, PriorityForOncomingTraffic, GiveWay, Stop, ParkingLot, OneWayRoad, Motorway, MotorwayEnds, ResidentialZone, EndOfResidentialZone, PedestrianZone, EndOfPedestrianZone, NoThroughRoad, NoThroughRoadRight,
    SymbolOfMotorway, ItineraryForIndicatedVehicleCategory, ItineraryForPedestrians, ItineraryForHandicapped, LocationSignForTouristService, FirstAid, FillingStation, Restaurant, PublicLavatory, StandingAndParkingProhibited,
    ParkingProhibited, ParkingProhibitedZone, EndOfParkingProhibitedZone, AlternativeParkingOddDays, Parking)

  def apply(value: Int): TRTrafficSignType= {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def apply(trafficSignType: TrafficSignType): Int = {
    values.find(_.trafficSignType == trafficSignType).get.value
  }

  case object TelematicSpeedLimit extends TRTrafficSignType { def value = 0; def trafficSignType = TrafficSignType.TelematicSpeedLimit; def group = TrafficSignTypeGroup.SpeedLimits; def source = Seq() }
  case object SpeedLimit extends TRTrafficSignType { def value = 361; def trafficSignType = TrafficSignType.SpeedLimit; def group = TrafficSignTypeGroup.SpeedLimits; def source = Seq("CSVimport", "TRimport") }
  case object EndSpeedLimit extends TRTrafficSignType { def value = 362; def trafficSignType = TrafficSignType.EndSpeedLimit; def group = TrafficSignTypeGroup.SpeedLimits; def source = Seq("CSVimport", "TRimport") }
  case object SpeedLimitZone extends TRTrafficSignType { def value = 363; def trafficSignType = TrafficSignType.SpeedLimitZone; def group = TrafficSignTypeGroup.SpeedLimits; def source = Seq("CSVimport", "TRimport") }
  case object EndSpeedLimitZone extends TRTrafficSignType { def value = 364; def trafficSignType = TrafficSignType.EndSpeedLimitZone; def group = TrafficSignTypeGroup.SpeedLimits; def source = Seq("CSVimport", "TRimport") }
  case object UrbanArea extends TRTrafficSignType { def value = 571; def trafficSignType = TrafficSignType.UrbanArea; def group = TrafficSignTypeGroup.SpeedLimits; def source = Seq("CSVimport", "TRimport") }
  case object EndUrbanArea extends TRTrafficSignType { def value = 572; def trafficSignType = TrafficSignType.EndUrbanArea; def group = TrafficSignTypeGroup.SpeedLimits; def source = Seq("CSVimport", "TRimport") }
  case object PedestrianCrossing extends TRTrafficSignType { def value = 511; def trafficSignType = TrafficSignType.PedestrianCrossing; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object MaximumLength extends TRTrafficSignType { def value = 343; def trafficSignType = TrafficSignType.MaximumLength; def group = TrafficSignTypeGroup.MaximumRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object Warning extends TRTrafficSignType { def value = 189; def trafficSignType = TrafficSignType.Warning; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object NoLeftTurn extends TRTrafficSignType { def value = 332; def trafficSignType = TrafficSignType.NoLeftTurn; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoRightTurn extends TRTrafficSignType { def value = 333; def trafficSignType = TrafficSignType.NoRightTurn; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoUTurn extends TRTrafficSignType { def value = 334; def trafficSignType = TrafficSignType.NoUTurn; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object ClosedToAllVehicles extends TRTrafficSignType { def value = 311; def trafficSignType = TrafficSignType.ClosedToAllVehicles; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoPowerDrivenVehicles extends TRTrafficSignType { def value = 312; def trafficSignType = TrafficSignType.NoPowerDrivenVehicles; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoLorriesAndVans extends TRTrafficSignType { def value = 313; def trafficSignType = TrafficSignType.NoLorriesAndVans; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoVehicleCombinations extends TRTrafficSignType { def value = 314; def trafficSignType = TrafficSignType.NoVehicleCombinations; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoAgriculturalVehicles extends TRTrafficSignType { def value = 315; def trafficSignType = TrafficSignType.NoAgriculturalVehicles; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoMotorCycles extends TRTrafficSignType { def value = 316; def trafficSignType = TrafficSignType.NoMotorCycles; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoMotorSledges extends TRTrafficSignType { def value = 317; def trafficSignType = TrafficSignType.NoMotorSledges; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoVehiclesWithDangerGoods extends TRTrafficSignType { def value = 318; def trafficSignType = TrafficSignType.NoVehiclesWithDangerGoods; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoBuses extends TRTrafficSignType { def value = 319; def trafficSignType = TrafficSignType.NoBuses; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoMopeds extends TRTrafficSignType { def value = 321; def trafficSignType = TrafficSignType.NoMopeds; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoCyclesOrMopeds extends TRTrafficSignType { def value = 322; def trafficSignType = TrafficSignType.NoCyclesOrMopeds; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoPedestrians extends TRTrafficSignType { def value = 323; def trafficSignType = TrafficSignType.NoPedestrians; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoPedestriansCyclesMopeds extends TRTrafficSignType { def value = 324; def trafficSignType = TrafficSignType.NoPedestriansCyclesMopeds; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoRidersOnHorseback extends TRTrafficSignType { def value = 325; def trafficSignType = TrafficSignType.NoRidersOnHorseback; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object NoEntry extends TRTrafficSignType { def value = 331; def trafficSignType = TrafficSignType.NoEntry; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object OvertakingProhibited extends TRTrafficSignType { def value = 351; def trafficSignType = TrafficSignType.OvertakingProhibited; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object EndProhibitionOfOvertaking extends TRTrafficSignType { def value = 352; def trafficSignType = TrafficSignType.EndProhibitionOfOvertaking; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object MaxWidthExceeding extends TRTrafficSignType { def value = 341; def trafficSignType = TrafficSignType.NoWidthExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object MaxHeightExceeding extends TRTrafficSignType { def value = 342; def trafficSignType = TrafficSignType.MaxHeightExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object MaxLadenExceeding extends TRTrafficSignType { def value = 344; def trafficSignType = TrafficSignType.MaxLadenExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object MaxMassCombineVehiclesExceeding extends TRTrafficSignType { def value = 345; def trafficSignType = TrafficSignType.MaxMassCombineVehiclesExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object MaxTonsOneAxleExceeding extends TRTrafficSignType { def value = 346; def trafficSignType = TrafficSignType.MaxTonsOneAxleExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object MaxTonsOnBogieExceeding extends TRTrafficSignType { def value = 347; def trafficSignType = TrafficSignType.MaxTonsOnBogieExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object WRightBend extends TRTrafficSignType { def value = 111; def trafficSignType = TrafficSignType.WRightBend; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object WLeftBend extends TRTrafficSignType { def value = 112; def trafficSignType = TrafficSignType.WLeftBend; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object WSeveralBendsRight extends TRTrafficSignType { def value = 113; def trafficSignType = TrafficSignType.WSeveralBendsRight; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object WSeveralBendsLeft extends TRTrafficSignType { def value = 114; def trafficSignType = TrafficSignType.WSeveralBendsLeft; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object WDangerousDescent extends TRTrafficSignType { def value = 115; def trafficSignType = TrafficSignType.WDangerousDescent; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object WSteepAscent extends TRTrafficSignType { def value = 116; def trafficSignType = TrafficSignType.WSteepAscent; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object RoadNarrows extends TRTrafficSignType { def value = 121; def trafficSignType = TrafficSignType.RoadNarrows; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object TwoWayTraffic extends TRTrafficSignType { def value = 122; def trafficSignType = TrafficSignType.TwoWayTraffic; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object SwingBridge extends TRTrafficSignType { def value = 131; def trafficSignType = TrafficSignType.SwingBridge; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object WUnevenRoad extends TRTrafficSignType { def value = 141; def trafficSignType = TrafficSignType.WUnevenRoad; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object RoadWorks extends TRTrafficSignType { def value = 142; def trafficSignType = TrafficSignType.RoadWorks; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object SlipperyRoad extends TRTrafficSignType { def value = 144; def trafficSignType = TrafficSignType.SlipperyRoad; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object PedestrianCrossingWarningSign extends TRTrafficSignType { def value = 151; def trafficSignType = TrafficSignType.PedestrianCrossingWarningSign; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object WChildren extends TRTrafficSignType { def value = 152; def trafficSignType = TrafficSignType.WChildren; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object Cyclists extends TRTrafficSignType { def value = 153; def trafficSignType = TrafficSignType.Cyclists; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object IntersectionWithEqualRoads extends TRTrafficSignType { def value = 161; def trafficSignType = TrafficSignType.IntersectionWithEqualRoads; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object LightSignals extends TRTrafficSignType { def value = 165; def trafficSignType = TrafficSignType.LightSignals; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object TramwayLine extends TRTrafficSignType { def value = 167; def trafficSignType = TrafficSignType.TramwayLine; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object FallingRocks extends TRTrafficSignType { def value = 181; def trafficSignType = TrafficSignType.FallingRocks; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object CrossWind extends TRTrafficSignType { def value = 183; def trafficSignType = TrafficSignType.CrossWind; def group = TrafficSignTypeGroup.GeneralWarningSigns; def source = Seq("CSVimport", "TRimport") }
  case object PriorityRoad extends TRTrafficSignType { def value = 211; def trafficSignType = TrafficSignType.PriorityRoad; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def source = Seq("CSVimport", "TRimport") }
  case object EndOfPriority extends TRTrafficSignType { def value = 212; def trafficSignType = TrafficSignType.EndOfPriority; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def source = Seq("CSVimport", "TRimport") }
  case object PriorityOverOncomingTraffic extends TRTrafficSignType { def value = 221; def trafficSignType = TrafficSignType.PriorityOverOncomingTraffic; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def source = Seq("CSVimport", "TRimport") }
  case object PriorityForOncomingTraffic extends TRTrafficSignType { def value = 222; def trafficSignType = TrafficSignType.PriorityForOncomingTraffic; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def source = Seq("CSVimport", "TRimport") }
  case object GiveWay extends TRTrafficSignType { def value = 231; def trafficSignType = TrafficSignType.GiveWay; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def source = Seq("CSVimport", "TRimport") }
  case object Stop extends TRTrafficSignType { def value = 232; def trafficSignType = TrafficSignType.Stop; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def source = Seq("CSVimport", "TRimport") }
  case object StandingAndParkingProhibited extends TRTrafficSignType { def value = 371; def trafficSignType = TrafficSignType.StandingAndParkingProhibited; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object ParkingProhibited extends TRTrafficSignType { def value = 372; def trafficSignType = TrafficSignType.ParkingProhibited; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object ParkingProhibitedZone extends TRTrafficSignType { def value = 373; def trafficSignType = TrafficSignType.ParkingProhibitedZone; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object EndOfParkingProhibitedZone extends TRTrafficSignType { def value = 374; def trafficSignType = TrafficSignType.EndOfParkingProhibitedZone; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object TaxiStationZoneBeginning extends TRTrafficSignType { def value = 375; def trafficSignType = TrafficSignType.TaxiStationZoneBeginning; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object StandingPlaceForTaxi extends TRTrafficSignType { def value = 376; def trafficSignType = TrafficSignType.StandingPlaceForTaxi; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object AlternativeParkingOddDays extends TRTrafficSignType { def value = 381; def trafficSignType = TrafficSignType.AlternativeParkingOddDays; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def source = Seq("CSVimport", "TRimport") }
  case object DirectionToBeFollowed3 extends TRTrafficSignType { def value = 413; def trafficSignType = TrafficSignType.DirectionToBeFollowed3; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object DirectionToBeFollowed4 extends TRTrafficSignType { def value = 414; def trafficSignType = TrafficSignType.DirectionToBeFollowed3; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object DirectionToBeFollowed5 extends TRTrafficSignType { def value = 415; def trafficSignType = TrafficSignType.DirectionToBeFollowed3; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object CompulsoryRoundabout extends TRTrafficSignType { def value = 416; def trafficSignType = TrafficSignType.CompulsoryRoundabout; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object PassThisSide extends TRTrafficSignType { def value = 417; def trafficSignType = TrafficSignType.PassThisSide; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object DividerOfTraffic extends TRTrafficSignType { def value = 418; def trafficSignType = TrafficSignType.PassThisSide; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object CompulsoryFootPath extends TRTrafficSignType { def value = 421; def trafficSignType = TrafficSignType.CompulsoryFootPath; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object CompulsoryCycleTrack extends TRTrafficSignType { def value = 422; def trafficSignType = TrafficSignType.CompulsoryCycleTrack; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object CombinedCycleTrackAndFootPath extends TRTrafficSignType { def value = 423; def trafficSignType = TrafficSignType.CombinedCycleTrackAndFootPath; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object ParallelCycleTrackAndFootPath extends TRTrafficSignType { def value = 424; def trafficSignType = TrafficSignType.CombinedCycleTrackAndFootPath; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object ParallelCycleTrackAndFootPath2 extends TRTrafficSignType { def value = 425; def trafficSignType = TrafficSignType.CombinedCycleTrackAndFootPath; def group = TrafficSignTypeGroup.MandatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object ParkingLot extends TRTrafficSignType { def value = 521; def trafficSignType = TrafficSignType.ParkingLot; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object BusStopForLocalTraffic extends TRTrafficSignType { def value = 531; def trafficSignType = TrafficSignType.BusStopForLocalTraffic; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object BusStopForLongDistanceTraffic extends TRTrafficSignType { def value = 532; def trafficSignType = TrafficSignType.BusStopForLocalTraffic; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object TramStop extends TRTrafficSignType { def value = 533; def trafficSignType = TrafficSignType.TramStop; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object TaxiStation extends TRTrafficSignType { def value = 534; def trafficSignType = TrafficSignType.TaxiStation; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object BusLane extends TRTrafficSignType { def value = 541; def trafficSignType = TrafficSignType.BusLane; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object BusLaneEnds extends TRTrafficSignType { def value = 542; def trafficSignType = TrafficSignType.BusLaneEnds; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object TramLane extends TRTrafficSignType { def value = 543; def trafficSignType = TrafficSignType.TramLane; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object OneWayRoad extends TRTrafficSignType { def value = 551; def trafficSignType = TrafficSignType.OneWayRoad; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object Motorway extends TRTrafficSignType { def value = 561; def trafficSignType = TrafficSignType.Motorway; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object MotorwayEnds extends TRTrafficSignType { def value = 562; def trafficSignType = TrafficSignType.MotorwayEnds; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object ResidentialZone extends TRTrafficSignType { def value = 573; def trafficSignType = TrafficSignType.ResidentialZone; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object EndOfResidentialZone extends TRTrafficSignType { def value = 574; def trafficSignType = TrafficSignType.EndOfResidentialZone; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object PedestrianZone extends TRTrafficSignType { def value = 575; def trafficSignType = TrafficSignType.PedestrianZone; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object EndOfPedestrianZone extends TRTrafficSignType { def value = 576; def trafficSignType = TrafficSignType.EndOfPedestrianZone; def group = TrafficSignTypeGroup.RegulatorySigns; def source = Seq("CSVimport", "TRimport") }
  case object NoThroughRoad extends TRTrafficSignType { def value = 651; def trafficSignType = TrafficSignType.NoThroughRoad; def group = TrafficSignTypeGroup.InformationSigns; def source = Seq("CSVimport", "TRimport") }
  case object NoThroughRoadRight extends TRTrafficSignType { def value = 652; def trafficSignType = TrafficSignType.NoThroughRoadRight; def group = TrafficSignTypeGroup.InformationSigns; def source = Seq("CSVimport", "TRimport") }
  case object SymbolOfMotorway extends TRTrafficSignType { def value = 671; def trafficSignType = TrafficSignType.SymbolOfMotorway; def group = TrafficSignTypeGroup.InformationSigns; def source = Seq("CSVimport", "TRimport") }
  case object Parking extends TRTrafficSignType { def value = 677; def trafficSignType = TrafficSignType.Parking; def group = TrafficSignTypeGroup.InformationSigns; def source = Seq("CSVimport", "TRimport") }
  case object ItineraryForIndicatedVehicleCategory extends TRTrafficSignType { def value = 681; def trafficSignType = TrafficSignType.ItineraryForIndicatedVehicleCategory; def group = TrafficSignTypeGroup.InformationSigns; def source = Seq("CSVimport", "TRimport") }
  case object ItineraryForPedestrians extends TRTrafficSignType { def value = 682; def trafficSignType = TrafficSignType.ItineraryForPedestrians; def group = TrafficSignTypeGroup.InformationSigns; def source = Seq("CSVimport", "TRimport") }
  case object ItineraryForHandicapped extends TRTrafficSignType { def value = 683; def trafficSignType = TrafficSignType.ItineraryForHandicapped; def group = TrafficSignTypeGroup.InformationSigns; def source = Seq("CSVimport", "TRimport") }
  case object LocationSignForTouristService extends TRTrafficSignType { def value = 704; def trafficSignType = TrafficSignType.LocationSignForTouristService; def group = TrafficSignTypeGroup.ServiceSigns; def source = Seq("CSVimport", "TRimport") }
  case object FirstAid extends TRTrafficSignType { def value = 715; def trafficSignType = TrafficSignType.FirstAid; def group = TrafficSignTypeGroup.ServiceSigns; def source = Seq("CSVimport", "TRimport") }
  case object FillingStation extends TRTrafficSignType { def value = 722; def trafficSignType = TrafficSignType.FillingStation; def group = TrafficSignTypeGroup.ServiceSigns; def source = Seq("CSVimport", "TRimport") }
  case object Restaurant extends TRTrafficSignType { def value = 724; def trafficSignType = TrafficSignType.Restaurant; def group = TrafficSignTypeGroup.ServiceSigns; def source = Seq("CSVimport", "TRimport") }
  case object PublicLavatory extends TRTrafficSignType { def value = 726; def trafficSignType = TrafficSignType.PublicLavatory; def group = TrafficSignTypeGroup.ServiceSigns; def source = Seq("CSVimport", "TRimport") }
  case object FreeWidth extends TRTrafficSignType { def value = 821; def trafficSignType = TrafficSignType.FreeWidth; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object FreeHeight extends TRTrafficSignType { def value = 822; def trafficSignType = TrafficSignType.FreeHeight; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object PassengerCar extends TRTrafficSignType { def value = 831; def trafficSignType = TrafficSignType.PassengerCar; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object Bus extends TRTrafficSignType { def value = 832; def trafficSignType = TrafficSignType.Bus; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object Lorry extends TRTrafficSignType { def value = 833; def trafficSignType = TrafficSignType.Lorry; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object Van extends TRTrafficSignType { def value = 834; def trafficSignType = TrafficSignType.Van; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object VehicleForHandicapped extends TRTrafficSignType { def value = 836; def trafficSignType = TrafficSignType.VehicleForHandicapped; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object MotorCycle extends TRTrafficSignType { def value = 841; def trafficSignType = TrafficSignType.MotorCycle; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object Cycle extends TRTrafficSignType { def value = 843; def trafficSignType = TrafficSignType.Cycle; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object HazmatProhibitionA extends TRTrafficSignType { def value = 848; def trafficSignType = TrafficSignType.HazmatProhibitionA; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object HazmatProhibitionB extends TRTrafficSignType { def value = 849; def trafficSignType = TrafficSignType.HazmatProhibitionB; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object ValidMonFri extends TRTrafficSignType { def value = 851; def trafficSignType = TrafficSignType.ValidMonFri; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object ValidSat extends TRTrafficSignType { def value = 852; def trafficSignType = TrafficSignType.ValidSat; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object TimeLimit extends TRTrafficSignType { def value = 854; def trafficSignType = TrafficSignType.TimeLimit; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object ParkingAgainstFee extends TRTrafficSignType { def value = 855; def trafficSignType = TrafficSignType.ParkingAgainstFee; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object ObligatoryUseOfParkingDisc extends TRTrafficSignType { def value = 856; def trafficSignType = TrafficSignType.ObligatoryUseOfParkingDisc; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object AdditionalPanelWithText extends TRTrafficSignType { def value = 871; def trafficSignType = TrafficSignType.AdditionalPanelWithText; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object DrivingInServicePurposesAllowed extends TRTrafficSignType { def value = 872; def trafficSignType = TrafficSignType.DrivingInServicePurposesAllowed; def group = TrafficSignTypeGroup.AdditionalPanels; def source = Seq("CSVimport", "TRimport") }
  case object Unknown extends TRTrafficSignType { def value = 999999; def trafficSignType = TrafficSignType.Unknown; def group = TrafficSignTypeGroup.Unknown; def source = Seq() }
}

sealed trait TRLaneArrangementType {
  def value: Int
}
object TRLaneArrangementType {
  val values = Set(MassTransitLane)

  def apply(value: Int): TRLaneArrangementType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object MassTransitLane extends TRLaneArrangementType { def value = 5; }
  case object Unknown extends TRLaneArrangementType { def value = 99; }
}

sealed trait TRFrostHeavingFactorType {
  def value: Int
  def pavedRoadType: String
}
object TRFrostHeavingFactorType {
  val values = Set(VeryFrostHeaving, MiddleValue50to60, FrostHeaving, MiddleValue60to80, NoFrostHeaving)

  def apply(value: Int): TRFrostHeavingFactorType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object VeryFrostHeaving extends TRFrostHeavingFactorType { def value = 40; def pavedRoadType = "Very Frost Heaving";}
  case object MiddleValue50to60 extends TRFrostHeavingFactorType { def value = 50; def pavedRoadType = "Middle value 50...60";}
  case object FrostHeaving extends TRFrostHeavingFactorType { def value = 60; def pavedRoadType = "Frost Heaving";}
  case object MiddleValue60to80 extends TRFrostHeavingFactorType { def value = 70; def pavedRoadType = "Middle Value 60...80";}
  case object NoFrostHeaving extends TRFrostHeavingFactorType { def value = 80; def pavedRoadType = "No Frost Heaving";}
  case object Unknown extends TRFrostHeavingFactorType { def value = 999;  def pavedRoadType = "No information";}
}

case class TierekisteriError(content: Map[String, Any], url: String)

class TierekisteriClientException(response: String) extends RuntimeException(response)

class TierekisteriClientWarnings(response: String) extends RuntimeException(response)

trait TierekisteriClient{

  def tierekisteriRestApiEndPoint: String
  def tierekisteriEnabled: Boolean
  def client: CloseableHttpClient

  type TierekisteriType

  protected implicit val jsonFormats: Formats = DefaultFormats
  protected val dateFormat = "yyyy-MM-dd"
  protected val auth = new TierekisteriAuthPropertyReader
  protected lazy val logger = LoggerFactory.getLogger(getClass)

  def mapFields(data: Map[String, Any]): Option[TierekisteriType]

  def addAuthorizationHeader(request: HttpRequestBase) = {
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getOldAuthInBase64)
    request.addHeader("X-Authorization", "Basic " + auth.getAuthInBase64)

  }
  protected def request[T](url: String): Either[T, TierekisteriError] = {
    val request = new HttpGet(url)
    addAuthorizationHeader(request)
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode == HttpStatus.SC_NOT_FOUND) {
        return Right(null)
      } else if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        return Right(TierekisteriError(Map("error" -> ErrorMessageConverter.convertJSONToError(response), "content" -> response.getEntity.getContent), url))
      }
      Left(parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[T])
    } catch {
      case e: Exception => Right(TierekisteriError(Map("error" -> e.getMessage, "content" -> response.getEntity.getContent), url))
    } finally {
      response.close()
    }
  }

  protected def post(url: String, trEntity: TierekisteriType, createJson: (TierekisteriType) => StringEntity): Option[TierekisteriError] = {
    val request = new HttpPost(url)
    addAuthorizationHeader(request)
    request.setEntity(createJson(trEntity))
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      val reason = response.getStatusLine.getReasonPhrase
      if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        logger.warn("Tierekisteri error: " + url + " " + statusCode + " " + reason)
        val error = ErrorMessageConverter.convertJSONToError(response)
        logger.warn("Json from Tierekisteri: " + error)
        return Some(TierekisteriError(Map("error" -> error), url))
      }
      None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  protected def put(url: String, trEntity: TierekisteriType, createJson: (TierekisteriType) => StringEntity): Option[TierekisteriError] = {
    val request = new HttpPut(url)
    addAuthorizationHeader(request)
    request.setEntity(createJson(trEntity))
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      val reason = response.getStatusLine.getReasonPhrase
      if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        logger.warn("Tierekisteri error: " + url + " " + statusCode + " " + reason)
        val error = ErrorMessageConverter.convertJSONToError(response)
        logger.warn("Json from Tierekisteri: " + error)
        return Some(TierekisteriError(Map("error" -> error), url))
      }
      None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  protected def delete(url: String): Option[TierekisteriError] = {
    val request = new HttpDelete(url)
    request.setHeader("content-type","application/json")
    addAuthorizationHeader(request)
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      val reason = response.getStatusLine.getReasonPhrase
      if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        logger.warn("Tierekisteri error: " + url + " " + statusCode + " " + reason)
        val error = ErrorMessageConverter.convertJSONToError(response)
        logger.warn("Json from Tierekisteri: " + error)
        return Some(TierekisteriError(Map("error" -> error), url))
      }
      None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  protected def convertToLong(value: Option[String]): Option[Long] = {
    try {
      value.map(_.toLong)
    } catch {
      case e: NumberFormatException =>
        throw new TierekisteriClientException("Invalid value in response: Long expected, got '%s'".format(value))
    }
  }

  protected def convertToDouble(value: Option[String]): Option[Double] = {
    try {
      value.map(_.toDouble)
    } catch {
      case e: NumberFormatException =>
        throw new TierekisteriClientException("Invalid value in response: Double expected, got '%s'".format(value))
    }
  }

  protected def convertToInt(value: Option[String]): Option[Int] = {
    try {
      value.map(_.toInt)
    } catch {
      case e: NumberFormatException =>
        throw new TierekisteriClientException("Invalid value in response: Int expected, got '%s'".format(value))
    }
  }

  protected def convertToDate(value: Option[String]): Option[Date] = {
    try {
      value.map(dv => new SimpleDateFormat(dateFormat).parse(dv))
    } catch {
      case e: ParseException =>
        throw new TierekisteriClientException("Invalid value in response: Date expected, got '%s'".format(value))
    }
  }

  protected def convertDateToString(date: Option[Date]): Option[String] = {
    date.map(dv => convertDateToString(dv))
  }

  protected def convertDateToString(date: Date): String = {
    new SimpleDateFormat(dateFormat).format(date)
  }

  protected def getFieldValue(data: Map[String, Any], field: String): Option[String] = {
    try {
      data.get(field).map(_.toString) match {
        case Some(value) => Some(value)
        case _ => None
      }
    } catch {
      case ex: NullPointerException => None
    }
  }
  protected def getMandatoryFieldValue(data: Map[String, Any], field: String): Option[String] = {
    val fieldValue = getFieldValue(data, field)
    if (fieldValue.isEmpty)
      throw new TierekisteriClientException("Missing mandatory field in response '%s'".format(field))
    fieldValue
  }
}

object ErrorMessageConverter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  def convertJSONToError(response: CloseableHttpResponse) = {
    def inputToMap(json: StreamInput): Map[String, String] = {
      try {
        parse(json).values.asInstanceOf[Map[String, String]]
      } catch {
        case e: Exception => Map()
      }
    }
    def errorMessageFormat = "%d: %s"
    val message = inputToMap(StreamInput(response.getEntity.getContent)).getOrElse("message", "N/A")
    response.getStatusLine.getStatusCode match {
      case HttpStatus.SC_BAD_REQUEST => errorMessageFormat.format(HttpStatus.SC_BAD_REQUEST, message)
      case HttpStatus.SC_LOCKED => errorMessageFormat.format(HttpStatus.SC_LOCKED, message)
      case HttpStatus.SC_CONFLICT => errorMessageFormat.format(HttpStatus.SC_CONFLICT, message)
      case HttpStatus.SC_INTERNAL_SERVER_ERROR => errorMessageFormat.format(HttpStatus.SC_INTERNAL_SERVER_ERROR, message)
      case HttpStatus.SC_NOT_FOUND => errorMessageFormat.format(HttpStatus.SC_NOT_FOUND, message)
      case _ => "Unspecified error: %s".format(message)
    }
  }
}
