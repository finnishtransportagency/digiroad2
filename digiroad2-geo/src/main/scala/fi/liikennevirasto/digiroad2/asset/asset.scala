package fi.liikennevirasto.digiroad2.asset

import fi.liikennevirasto.digiroad2.{Point, Vector3d}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.collection.mutable.ListBuffer
import scala.util.Try

sealed trait LinkGeomSource{
  def value: Int
}

//LINKIN LÄHDE (1 = tielinkkien rajapinta, 2 = täydentävien linkkien rajapinta, 3 = suunnitelmalinkkien rajapinta, 4 = jäädytettyjen linkkien rajapinta, 5 = historialinkkien rajapinta)

object LinkGeomSource{
  val values = Set(NormalLinkInterface, ComplimentaryLinkInterface , SuravageLinkInterface, FrozenLinkInterface, HistoryLinkInterface)

  def apply(intValue: Int): LinkGeomSource = values.find(_.value == intValue).getOrElse(Unknown)

  case object NormalLinkInterface extends LinkGeomSource {def value = 1;}
  case object ComplimentaryLinkInterface extends LinkGeomSource {def value = 2;}
  case object SuravageLinkInterface extends LinkGeomSource {def value = 3;}
  case object FrozenLinkInterface extends LinkGeomSource {def value = 4;}
  case object HistoryLinkInterface extends LinkGeomSource {def value = 5;}
  case object Unknown extends LinkGeomSource { def value = 99 }
}

sealed trait ConstructionType {
  def value: Int
}

object ConstructionType{
  val values = Set[ConstructionType](InUse, UnderConstruction, Planned, UnknownConstructionType)

  def apply(intValue: Int): ConstructionType = {
    values.find(_.value == intValue).getOrElse(InUse)
  }

  case object InUse extends ConstructionType { def value = 0 }
  case object UnderConstruction extends ConstructionType { def value = 1 }
  case object Planned extends ConstructionType { def value = 3 }
  case object UnknownConstructionType extends ConstructionType { def value = 99 }
}

sealed trait LinkType
{
  def value: Int
}
object LinkType {
  val values = Set(Motorway, MultipleCarriageway, SingleCarriageway, Freeway, Roundabout, SlipRoad,
                   RestArea, CycleOrPedestrianPath, PedestrianZone, ServiceOrEmergencyRoad, EnclosedTrafficArea,
                   TractorRoad, MotorwayServiceAccess, CableFerry, SpecialTransportWithoutGate, SpecialTransportWithGate, UnknownLinkType)

  def apply(value: Int): LinkType = {
    values.find(_.value == value).getOrElse(UnknownLinkType)
  }
}
case object Motorway extends LinkType { def value = 1 }
case object MultipleCarriageway extends LinkType { def value = 2 }
case object SingleCarriageway extends LinkType { def value = 3 }
case object Freeway extends LinkType { def value = 4 }
case object Roundabout extends LinkType { def value = 5 }
case object SlipRoad extends LinkType { def value = 6 }
case object RestArea extends LinkType { def value = 7 }
case object CycleOrPedestrianPath extends LinkType { def value = 8 }
case object PedestrianZone extends LinkType { def value = 9 }
case object ServiceOrEmergencyRoad extends LinkType { def value = 10 }
case object EnclosedTrafficArea extends LinkType { def value = 11 }
case object TractorRoad extends LinkType { def value = 12 }
case object MotorwayServiceAccess extends LinkType { def value = 13 }
case object SpecialTransportWithoutGate extends LinkType { def value = 14 }
case object SpecialTransportWithGate extends LinkType { def value = 15 }
case object CableFerry extends LinkType { def value = 21 }
case object UnknownLinkType extends LinkType { def value = 99 }

sealed trait AdministrativeClass {
  def value: Int
}
object AdministrativeClass {
  val values = Set(State, Municipality, Private, Unknown)

  def apply(value: Int): AdministrativeClass = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def apply(stringValue: String): AdministrativeClass = {
    values.find(_.toString == stringValue).getOrElse(Unknown)
  }
}
case object State extends AdministrativeClass { def value = 1 }
case object Municipality extends AdministrativeClass { def value = 2 }
case object Private extends AdministrativeClass { def value = 3 }
case object Unknown extends AdministrativeClass { def value = 99 }



sealed trait InformationSource {
  def value: Int
}

object InformationSource{
  val values = Set(RoadRegistry, MunicipalityMaintenainer, MmlNls, UnknownSource)

  def apply(value: Int): InformationSource = {
    values.find(_.value == value).getOrElse(UnknownSource)
  }
}

//1 = FTA/ Road registry (Liikennevirasto / Tierekisteri)
case object RoadRegistry extends InformationSource { def value = 1 }
//2 = Maintainer (municipality maintainer)
case object MunicipalityMaintenainer extends InformationSource { def value = 2 }
//3 = MML/NLS (Maanmittauslaitos)
case object MmlNls extends InformationSource { def value = 3 }

case object UnknownSource extends InformationSource { def value = 99 }


object FunctionalClass {
  val Unknown: Int = 99
}

sealed trait TrafficDirection {
  def value: Int
  def isOneWay =
    this == TrafficDirection.AgainstDigitizing ||
    this == TrafficDirection.TowardsDigitizing
}
object TrafficDirection {
  val values = Set(BothDirections, AgainstDigitizing, TowardsDigitizing, UnknownDirection)

  def apply(intValue: Int): TrafficDirection = {
    values.find(_.value == intValue).getOrElse(UnknownDirection)
  }

  def apply(optionalValue: Option[Int]): TrafficDirection = {
    optionalValue.map { value => values.find(_.value == value).getOrElse(UnknownDirection) }.getOrElse(UnknownDirection)
  }

  def apply(stringValue: String): TrafficDirection = {
    values.find(_.toString == stringValue).getOrElse(UnknownDirection)
  }

  def toSideCode(trafficDirection: TrafficDirection): SideCode = {
    trafficDirection match {
      case TowardsDigitizing => SideCode.TowardsDigitizing
      case AgainstDigitizing => SideCode.AgainstDigitizing
      case BothDirections => SideCode.BothDirections
      case UnknownDirection => SideCode.Unknown
    }
  }

  case object BothDirections extends TrafficDirection { def value = 2 }
  case object AgainstDigitizing extends TrafficDirection { def value = 3 }
  case object TowardsDigitizing extends TrafficDirection { def value = 4 }
  case object UnknownDirection extends TrafficDirection { def value = 99 }


}

sealed trait SideCode {
  def value: Int
}
object SideCode {
  val values = Set(BothDirections, TowardsDigitizing, AgainstDigitizing, Unknown)

  def apply(intValue: Int): SideCode = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  def switch(sideCode: SideCode): SideCode = {
    sideCode match {
      case TowardsDigitizing => AgainstDigitizing
      case AgainstDigitizing => TowardsDigitizing
      case _ => sideCode
    }
  }

  def toTrafficDirection(sideCode: SideCode): TrafficDirection = {
    sideCode match {
      case TowardsDigitizing => TrafficDirection.TowardsDigitizing
      case AgainstDigitizing => TrafficDirection.AgainstDigitizing
      case BothDirections => TrafficDirection.BothDirections
      case Unknown => TrafficDirection.UnknownDirection
    }
  }

  case object BothDirections extends SideCode { def value = 1 }
  case object TowardsDigitizing extends SideCode { def value = 2 }
  case object AgainstDigitizing extends SideCode { def value = 3 }
  case object Unknown extends SideCode { def value = 99 }
}

/**
  * Values for PavementClass types enumeration
  */
sealed trait PavementClass {
  def value: Int
  def typeDescription: String
}
object PavementClass {
  val values = Set(CementConcrete, Cobblestone, HardAsphalt, SoftAsphalt, GravelSurface, GravelWearLayer, OtherCoatings, Unknown)

  def apply(value: Int): PavementClass = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object CementConcrete extends PavementClass { def value = 1; def typeDescription = "Cement Concrete";}
  case object Cobblestone extends PavementClass { def value = 2; def typeDescription = "Cobblestone";}
  case object HardAsphalt extends PavementClass { def value = 10; def typeDescription = "Hard Asphalt";}
  case object SoftAsphalt extends PavementClass { def value = 20; def typeDescription = "Soft Asphalt";}
  case object GravelSurface extends PavementClass { def value = 30; def typeDescription = "Gravel Surface";}
  case object GravelWearLayer extends PavementClass { def value = 40; def typeDescription = "Gravel Wear Layer";}
  case object OtherCoatings extends PavementClass { def value = 50; def typeDescription = "Other Coatings";}
  case object Unknown extends PavementClass { def value = 99;  def typeDescription = "Unknown";}
}

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
  def linkedWith: Seq[AssetTypeInfo]
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

  def linkedWith(intValue: Int) : Seq[AssetTypeInfo] = {
    TrafficSignType.apply(intValue).linkedWith
  }

  case object SpeedLimit extends TrafficSignType { def value = 1;  def group = TrafficSignTypeGroup.SpeedLimits; def linkedWith = Seq(); }
  case object EndSpeedLimit extends TrafficSignType { def value = 2;  def group = TrafficSignTypeGroup.SpeedLimits; def linkedWith = Seq(); }
  case object SpeedLimitZone extends TrafficSignType { def value = 3;  def group = TrafficSignTypeGroup.SpeedLimits; def linkedWith = Seq(); }
  case object EndSpeedLimitZone extends TrafficSignType { def value = 4;  def group = TrafficSignTypeGroup.SpeedLimits; def linkedWith = Seq(); }
  case object UrbanArea extends TrafficSignType { def value = 5;  def group = TrafficSignTypeGroup.SpeedLimits; def linkedWith = Seq(); }
  case object EndUrbanArea extends TrafficSignType { def value = 6;  def group = TrafficSignTypeGroup.SpeedLimits; def linkedWith = Seq(); }
  case object PedestrianCrossing extends TrafficSignType { def value = 7;  def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object MaximumLength extends TrafficSignType { def value = 8;  def group = TrafficSignTypeGroup.MaximumRestrictions; def linkedWith = Seq(); }
  case object Warning extends TrafficSignType { def value = 9;  def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object NoLeftTurn extends TrafficSignType { def value = 10;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Manoeuvres); }
  case object NoRightTurn extends TrafficSignType { def value = 11;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Manoeuvres); }
  case object NoUTurn extends TrafficSignType { def value = 12;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Manoeuvres); }
  case object ClosedToAllVehicles extends TrafficSignType { def value = 13;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoPowerDrivenVehicles extends TrafficSignType { def value = 14;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoLorriesAndVans extends TrafficSignType { def value = 15;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoVehicleCombinations extends TrafficSignType { def value = 16;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoAgriculturalVehicles extends TrafficSignType { def value = 17;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoMotorCycles extends TrafficSignType { def value = 18;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoMotorSledges extends TrafficSignType { def value = 19;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoVehiclesWithDangerGoods extends TrafficSignType { def value = 20;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object NoBuses extends TrafficSignType { def value = 21;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoMopeds extends TrafficSignType { def value = 22;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoCyclesOrMopeds extends TrafficSignType { def value = 23;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoPedestrians extends TrafficSignType { def value = 24;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoPedestriansCyclesMopeds extends TrafficSignType { def value = 25;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoRidersOnHorseback extends TrafficSignType { def value = 26;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(Prohibition); }
  case object NoEntry extends TrafficSignType { def value = 27;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object OvertakingProhibited extends TrafficSignType { def value = 28;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object EndProhibitionOfOvertaking extends TrafficSignType { def value = 29;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object NoWidthExceeding extends TrafficSignType { def value = 30;  def group = TrafficSignTypeGroup.MaximumRestrictions; def linkedWith = Seq(); }
  case object MaxHeightExceeding extends TrafficSignType { def value = 31;  def group = TrafficSignTypeGroup.MaximumRestrictions; def linkedWith = Seq(); }
  case object MaxLadenExceeding extends TrafficSignType { def value = 32;  def group = TrafficSignTypeGroup.MaximumRestrictions; def linkedWith = Seq(); }
  case object MaxMassCombineVehiclesExceeding extends TrafficSignType { def value = 33;  def group = TrafficSignTypeGroup.MaximumRestrictions; def linkedWith = Seq(); }
  case object MaxTonsOneAxleExceeding extends TrafficSignType { def value = 34;  def group = TrafficSignTypeGroup.MaximumRestrictions; def linkedWith = Seq(); }
  case object MaxTonsOnBogieExceeding extends TrafficSignType { def value = 35;  def group = TrafficSignTypeGroup.MaximumRestrictions; def linkedWith = Seq(); }
  case object WRightBend extends TrafficSignType { def value = 36;  def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object WLeftBend extends TrafficSignType { def value = 37;  def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object WSeveralBendsRight extends TrafficSignType { def value = 38;  def group = TrafficSignTypeGroup.GeneralWarningSigns ; def linkedWith = Seq(); }
  case object WSeveralBendsLeft extends TrafficSignType { def value = 39;  def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object WDangerousDescent extends TrafficSignType { def value = 40;  def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object WSteepAscent extends TrafficSignType { def value = 41;  def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object WUnevenRoad extends TrafficSignType { def value = 42;  def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object WChildren extends TrafficSignType { def value = 43;  def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object TelematicSpeedLimit extends TrafficSignType { def value = 44;  def group = TrafficSignTypeGroup.SpeedLimits; def linkedWith = Seq(); }
  case object FreeWidth extends TrafficSignType { def value = 45;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object FreeHeight extends TrafficSignType { def value = 46;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object HazmatProhibitionA extends TrafficSignType { def value = 47;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object HazmatProhibitionB extends TrafficSignType { def value = 48;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object ValidMonFri extends TrafficSignType { def value = 49;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object ValidSat extends TrafficSignType { def value = 50;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object TimeLimit extends TrafficSignType { def value = 51;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object PassengerCar extends TrafficSignType { def value = 52;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object Bus extends TrafficSignType { def value = 53;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object Lorry extends TrafficSignType { def value = 54;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object Van extends TrafficSignType { def value = 55;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object VehicleForHandicapped extends TrafficSignType { def value = 56;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object MotorCycle extends TrafficSignType { def value = 57;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object Cycle extends TrafficSignType { def value = 58;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object ParkingAgainstFee extends TrafficSignType { def value = 59;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object ObligatoryUseOfParkingDisc extends TrafficSignType { def value = 60;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object AdditionalPanelWithText extends TrafficSignType { def value = 61;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object DrivingInServicePurposesAllowed extends TrafficSignType { def value = 62;  def group = TrafficSignTypeGroup.AdditionalPanels; def linkedWith = Seq(); }
  case object BusLane extends TrafficSignType { def value = 63;  def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object BusLaneEnds extends TrafficSignType { def value = 64;  def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object TramLane extends TrafficSignType { def value = 65;  def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object BusStopForLocalTraffic extends TrafficSignType { def value = 66;  def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object TramStop extends TrafficSignType { def value = 68;  def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object TaxiStation extends TrafficSignType { def value = 69;  def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object CompulsoryFootPath extends TrafficSignType { def value = 70;  def group = TrafficSignTypeGroup.MandatorySigns; def linkedWith = Seq(); }
  case object CompulsoryCycleTrack extends TrafficSignType { def value = 71;  def group = TrafficSignTypeGroup.MandatorySigns; def linkedWith = Seq(); }
  case object CombinedCycleTrackAndFootPath extends TrafficSignType { def value = 72;  def group = TrafficSignTypeGroup.MandatorySigns; def linkedWith = Seq(); }
  case object DirectionToBeFollowed3 extends TrafficSignType { def value = 74;  def group = TrafficSignTypeGroup.MandatorySigns; def linkedWith = Seq(); }
  case object CompulsoryRoundabout extends TrafficSignType { def value = 77;  def group = TrafficSignTypeGroup.MandatorySigns; def linkedWith = Seq(); }
  case object PassThisSide extends TrafficSignType { def value = 78;  def group = TrafficSignTypeGroup.MandatorySigns; def linkedWith = Seq(); }
  case object TaxiStationZoneBeginning extends TrafficSignType { def value = 80;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object StandingPlaceForTaxi extends TrafficSignType { def value = 81;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object RoadNarrows extends TrafficSignType { def value = 82; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object TwoWayTraffic extends TrafficSignType { def value = 83; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object SwingBridge extends TrafficSignType { def value = 84; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object RoadWorks extends TrafficSignType { def value = 85; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object SlipperyRoad extends TrafficSignType { def value = 86; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object PedestrianCrossingWarningSign extends TrafficSignType { def value = 87; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object Cyclists extends TrafficSignType { def value = 88; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object IntersectionWithEqualRoads extends TrafficSignType { def value = 89; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object LightSignals extends TrafficSignType { def value = 90; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object TramwayLine extends TrafficSignType { def value = 91; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object FallingRocks extends TrafficSignType { def value = 92; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object CrossWind extends TrafficSignType { def value = 93; def group = TrafficSignTypeGroup.GeneralWarningSigns; def linkedWith = Seq(); }
  case object PriorityRoad extends TrafficSignType { def value = 94; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def linkedWith = Seq(); }
  case object EndOfPriority extends TrafficSignType { def value = 95; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def linkedWith = Seq(); }
  case object PriorityOverOncomingTraffic extends TrafficSignType { def value = 96; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def linkedWith = Seq(); }
  case object PriorityForOncomingTraffic extends TrafficSignType { def value = 97; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def linkedWith = Seq(); }
  case object GiveWay extends TrafficSignType { def value = 98; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def linkedWith = Seq(); }
  case object Stop extends TrafficSignType { def value = 99; def group = TrafficSignTypeGroup.PriorityAndGiveWaySigns; def linkedWith = Seq(); }
  case object StandingAndParkingProhibited extends TrafficSignType { def value = 100; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object ParkingProhibited extends TrafficSignType { def value = 101; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object ParkingProhibitedZone extends TrafficSignType { def value = 102; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object EndOfParkingProhibitedZone extends TrafficSignType { def value = 103; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object AlternativeParkingOddDays extends TrafficSignType { def value = 104; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; def linkedWith = Seq(); }
  case object ParkingLot extends TrafficSignType { def value = 105; def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object OneWayRoad extends TrafficSignType { def value = 106; def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object Motorway extends TrafficSignType { def value = 107; def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object MotorwayEnds extends TrafficSignType { def value = 108; def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object ResidentialZone extends TrafficSignType { def value = 109; def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object EndOfResidentialZone extends TrafficSignType { def value = 110; def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object PedestrianZone extends TrafficSignType { def value = 111; def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object EndOfPedestrianZone extends TrafficSignType { def value = 112; def group = TrafficSignTypeGroup.RegulatorySigns; def linkedWith = Seq(); }
  case object NoThroughRoad extends TrafficSignType { def value = 113; def group = TrafficSignTypeGroup.InformationSigns; def linkedWith = Seq(); }
  case object NoThroughRoadRight extends TrafficSignType { def value = 114; def group = TrafficSignTypeGroup.InformationSigns; def linkedWith = Seq(); }
  case object SymbolOfMotorway extends TrafficSignType { def value = 115; def group = TrafficSignTypeGroup.InformationSigns; def linkedWith = Seq(); }
  case object Parking extends TrafficSignType { def value = 116; def group = TrafficSignTypeGroup.InformationSigns; def linkedWith = Seq(); }
  case object ItineraryForIndicatedVehicleCategory extends TrafficSignType { def value = 117; def group = TrafficSignTypeGroup.InformationSigns; def linkedWith = Seq(); }
  case object ItineraryForPedestrians extends TrafficSignType { def value = 118; def group = TrafficSignTypeGroup.InformationSigns; def linkedWith = Seq(); }
  case object ItineraryForHandicapped extends TrafficSignType { def value = 119; def group = TrafficSignTypeGroup.InformationSigns; def linkedWith = Seq(); }
  case object LocationSignForTouristService extends TrafficSignType { def value = 120; def group = TrafficSignTypeGroup.ServiceSigns; def linkedWith = Seq(); }
  case object FirstAid extends TrafficSignType { def value = 121; def group = TrafficSignTypeGroup.ServiceSigns; def linkedWith = Seq(); }
  case object FillingStation extends TrafficSignType { def value = 122; def group = TrafficSignTypeGroup.ServiceSigns; def linkedWith = Seq(); }
  case object Restaurant extends TrafficSignType { def value = 123; def group = TrafficSignTypeGroup.ServiceSigns; def linkedWith = Seq(); }
  case object PublicLavatory extends TrafficSignType { def value = 124; def group = TrafficSignTypeGroup.ServiceSigns; def linkedWith = Seq(); }
  case object Unknown extends TrafficSignType { def value = 999;  def group = TrafficSignTypeGroup.Unknown; def linkedWith = Seq(); }
}


sealed trait ProhibitionClass {
  val values: Seq[Int]
  def trafficSign: TrafficSignType
}
object ProhibitionClass {
  val values = Set(ClosedToAllVehicles, NoPowerDrivenVehicles, NoLorriesAndVans, NoVehicleCombinations, NoAgriculturalVehicles, NoMotorCycles, NoMotorSledges, NoBuses,
                   NoMopeds, NoCyclesOrMopeds, NoPedestrians, NoPedestriansCyclesMopeds, NoRidersOnHorseback, Unknown)

  def apply(value: Int): ProhibitionClass = {
    val filtered = values.filter(_.values.contains(value))
    if(filtered.isEmpty) Unknown else filtered.minBy(_.values.size)
  }

  def fromTrafficSign(trafficSign: TrafficSignType): ProhibitionClass = {
    values.find(_.trafficSign.equals(trafficSign)).getOrElse(Unknown)
  }

  def toTrafficSign(prohibitionValue: ListBuffer[Int]): Seq[TrafficSignType] = {

      (if (prohibitionValue.intersect(NoCyclesOrMopeds.values).size == NoCyclesOrMopeds.values.size) {
        prohibitionValue --= NoCyclesOrMopeds.values
        Seq(NoCyclesOrMopeds.trafficSign)
      } else Seq()) ++
      (if (prohibitionValue.intersect(NoPedestriansCyclesMopeds.values).size == NoPedestriansCyclesMopeds.values.size) {
        prohibitionValue --= NoPedestriansCyclesMopeds.values
        Seq(NoPedestriansCyclesMopeds.trafficSign)
      } else Seq()) ++ prohibitionValue.map { a =>
      ProhibitionClass.apply(a).trafficSign
    }
  }

  case object ClosedToAllVehicles extends ProhibitionClass { val values = Seq(3); def trafficSign = TrafficSignType.ClosedToAllVehicles;}
  case object NoPowerDrivenVehicles extends ProhibitionClass { val values = Seq(2); def trafficSign = TrafficSignType.NoPowerDrivenVehicles;}
  case object NoLorriesAndVans extends ProhibitionClass { val values = Seq(6, 4); def trafficSign = TrafficSignType.NoLorriesAndVans;}
  case object NoVehicleCombinations extends ProhibitionClass { val values = Seq(13); def trafficSign = TrafficSignType.NoVehicleCombinations;}
  case object NoAgriculturalVehicles extends ProhibitionClass { val values = Seq(14); def trafficSign = TrafficSignType.NoAgriculturalVehicles;}
  case object NoMotorCycles extends ProhibitionClass { val values = Seq(9); def trafficSign = TrafficSignType.NoMotorCycles;}
  case object NoMotorSledges extends ProhibitionClass { val values = Seq(27); def trafficSign = TrafficSignType.NoMotorSledges;}
  case object NoBuses extends ProhibitionClass { val values = Seq(5);  def trafficSign = TrafficSignType.NoBuses;}
  case object NoMopeds extends ProhibitionClass { val values = Seq(10);  def trafficSign = TrafficSignType.NoMopeds;}
  case object NoCyclesOrMopeds extends ProhibitionClass { val values = Seq(10, 11); def trafficSign = TrafficSignType.NoCyclesOrMopeds;}
  case object NoPedestrians extends ProhibitionClass { val values = Seq(12); def trafficSign = TrafficSignType.NoPedestrians;}
  case object NoPedestriansCyclesMopeds extends ProhibitionClass { val values = Seq(10, 11, 12);  def trafficSign = TrafficSignType.NoPedestriansCyclesMopeds;}
  case object NoRidersOnHorseback extends ProhibitionClass { val values = Seq(26);  def trafficSign = TrafficSignType.NoRidersOnHorseback;}
  case object Unknown extends ProhibitionClass { val values = Seq(99);  def trafficSign = TrafficSignType.Unknown;}
}

trait NationalStop { val nationalId: Long }
trait RoadLinkStop {
  val linkId: Option[Long]
  val mValue: Option[Double]
}
trait TimeStamps {
  val created: Modification
  val modified: Modification
}
trait FloatingAsset {
  val id: Long
  val floating: Boolean
}
case class AssetType(id: Long, assetTypeName: String, geometryType: String)

object Asset {
  val DateTimePropertyFormat = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
  val DatePropertyFormat = DateTimeFormat.forPattern("dd.MM.yyyy")
  val DateTimePropertyFormatMs = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss,SSS")
}

abstract class AbstractProperty {
  def publicId: String
  def values: Seq[PropertyValue]
}

case class Modification(modificationTime: Option[DateTime], modifier: Option[String])
case class SimpleProperty(publicId: String, values: Seq[PropertyValue]) extends AbstractProperty
case class DynamicProperty(publicId: String, propertyType: String, required: Boolean = false, values: Seq[DynamicPropertyValue])
case class Property(id: Long, publicId: String, propertyType: String, required: Boolean = false, values: Seq[PropertyValue], numCharacterMax: Option[Int] = None) extends AbstractProperty
case class PropertyValue(propertyValue: String, propertyDisplayValue: Option[String] = None, checked: Boolean = false)
case class DynamicPropertyValue(value: Any)
case class ValidityPeriodValue(days: Int, startHour: Int, endHour: Int, startMinute: Int, endMinute: Int, periodType: Option[Int] = None)
case class EnumeratedPropertyValue(propertyId: Long, publicId: String, propertyName: String, propertyType: String, required: Boolean = false, values: Seq[PropertyValue]) extends AbstractProperty
case class Position(lon: Double, lat: Double, linkId: Long, bearing: Option[Int])

object ValidityPeriodValue {
  def fromMap(map: Map[String, Any]): ValidityPeriodValue = {
    ValidityPeriodValue(
        getPropertyValuesByPublicId("days", map),
        getPropertyValuesByPublicId("startHour", map),
        getPropertyValuesByPublicId("endHour", map),
        getPropertyValuesByPublicId("startMinute", map),
        getPropertyValuesByPublicId("endMinute", map),
        getOptionalPropertyValuesByPublicId("periodType", map))
  }

  def getPropertyValuesByPublicId(property: String, mapValue: Map[String, Any]): Int = {
    Try(mapValue(property).asInstanceOf[BigInt].toInt).getOrElse(mapValue(property).asInstanceOf[Int])
  }

  def getOptionalPropertyValuesByPublicId(property: String, mapValue: Map[String, Any]): Option[Int] = {
    mapValue.get(property) match {
      case Some(value) => Try(value.asInstanceOf[Option[BigInt]].map(_.toInt)).getOrElse(value.asInstanceOf[Option[Int]])
      case _ => None
    }
  }

  def toMap(value: ValidityPeriodValue):  Map[String, Any] = {
    Map(
      "days" -> value.days,
      "startHour" -> value.startHour,
      "endHour" -> value.endHour,
      "startMinute" -> value.startMinute,
      "endMinute" -> value.endMinute
    )
  }

  def duration(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int ): Int = {
    val startHourAndMinutes: Double = (startMinute / 60.0) + startHour
    val endHourAndMinutes: Double = (endMinute / 60.0) + endHour

    if (endHourAndMinutes > startHourAndMinutes) {
      Math.ceil(endHourAndMinutes - startHourAndMinutes).toInt
    } else {
      Math.ceil(24 - startHourAndMinutes + endHourAndMinutes).toInt
    }
  }
}

object PropertyTypes {
  val SingleChoice = "single_choice"
  val MultipleChoice = "multiple_choice"
  val Text = "text"
  val LongText = "long_text"
  val ReadOnlyText = "read_only_text"
  val ReadOnlyNumber = "read_only_number"
  val Date = "date"
  val ReadOnly = "read-only"
  val CheckBox = "checkbox"
  val Number = "number"
  val IntegerProp = "integer"
  val TimePeriod = "time_period"
}

object MassTransitStopValidityPeriod {
  val Past = "past"
  val Current = "current"
  val Future = "future"
}

case class BoundingRectangle(leftBottom: Point, rightTop: Point) {
  def diagonal: Vector3d = leftBottom - rightTop
  def area: Double = diagonal.x*diagonal.y
}

sealed trait AssetTypeInfo {
  val typeId: Int
  def geometryType: String
  val label: String
  val layerName: String
}
//TODO change the type to be optional since manoeuvre are stored in a separated table and geometry type can be a type and the label can be a toString override
object AssetTypeInfo {
  val values =  Set(MassTransitStopAsset, SpeedLimitAsset,TotalWeightLimit, TrailerTruckWeightLimit, AxleWeightLimit, BogieWeightLimit,
                    HeightLimit, LengthLimit, WidthLimit, LitRoad, PavedRoad, RoadWidth, DamagedByThaw,
                    NumberOfLanes, MassTransitLane, TrafficVolume, WinterSpeedLimit,
                    Prohibition, PedestrianCrossings, HazmatTransportProhibition, Obstacles,
                    RailwayCrossings, DirectionalTrafficSigns, ServicePoints, EuropeanRoads, ExitNumbers,
                    TrafficLights, MaintenanceRoadAsset, TrafficSigns, Manoeuvres, TrTrailerTruckWeightLimit, TrBogieWeightLimit, TrAxleWeightLimit,TrWeightLimit, TrHeightLimit, TrWidthLimit,
                    CareClass, CarryingCapacity, UnknownAssetTypeId)

  def apply(value: Int): AssetTypeInfo = {
    values.find(_.typeId == value).getOrElse(UnknownAssetTypeId)
  }

  def apply(stringValue: String): AssetTypeInfo = {
    values.find(_.toString == stringValue).getOrElse(UnknownAssetTypeId)
  }
}
case object MassTransitStopAsset extends AssetTypeInfo { val typeId = 10; def geometryType = "point"; val label = "MassTransitStop"; val layerName = "massTransitStop"}
case object SpeedLimitAsset extends AssetTypeInfo { val typeId = 20; def geometryType = "linear"; val label = "SpeedLimit"; val layerName = "speedLimits"}
case object TotalWeightLimit extends AssetTypeInfo { val typeId = 30; def geometryType = "linear"; val label = "TotalWeightLimit" ; val layerName = "totalWeightLimit"}
case object TrailerTruckWeightLimit extends AssetTypeInfo { val typeId = 40; def geometryType = "linear"; val label = "TrailerTruckWeightLimit"; val layerName = "trailerTruckWeightLimit" }
case object AxleWeightLimit extends AssetTypeInfo { val typeId = 50; def geometryType = "linear"; val label = "AxleWeightLimit"; val layerName = "axleWeightLimit" }
case object BogieWeightLimit extends AssetTypeInfo { val typeId = 60; def geometryType = "linear"; val label =  "BogieWeightLimit"; val layerName = "bogieWeightLimit" }
case object HeightLimit extends AssetTypeInfo { val typeId = 70; def geometryType = "linear"; val label = "HeightLimit"; val layerName = "heightLimit" }
case object LengthLimit extends AssetTypeInfo { val typeId = 80; def geometryType = "linear"; val label = "LengthLimit"; val layerName = "lengthLimit" }
case object WidthLimit extends AssetTypeInfo { val typeId = 90; def geometryType = "linear"; val label = "WidthLimit"; val layerName = "widthLimit" }
case object LitRoad extends AssetTypeInfo { val typeId = 100; def geometryType = "linear"; val label = "LitRoad"; val layerName = "litRoad" }
case object PavedRoad extends AssetTypeInfo { val typeId = 110; def geometryType = "linear"; val label = "PavedRoad"; val layerName = "pavedRoad" }
case object RoadWidth extends AssetTypeInfo { val typeId = 120; def geometryType = "linear"; val label =  "RoadWidth"; val layerName = "roadWidth"}
case object DamagedByThaw extends AssetTypeInfo { val typeId = 130; def geometryType = "linear"; val label = "DamagedByThaw"; val layerName = "roadDamagedByThaw" }
case object NumberOfLanes extends AssetTypeInfo { val typeId = 140; def geometryType = "linear"; val label = "NumberOfLanes"; val layerName = "numberOfLanes" }
case object MassTransitLane extends AssetTypeInfo { val typeId = 160; def geometryType = "linear"; val label = "MassTransitLane"; val layerName = "massTransitLanes"  }
case object TrafficVolume extends AssetTypeInfo { val typeId = 170; def geometryType = "linear"; val label = "TrafficVolume"; val layerName = "trafficVolume" }
case object WinterSpeedLimit extends AssetTypeInfo { val typeId = 180; def geometryType = "linear"; val label = "WinterSpeedLimit"; val layerName = "winterSpeedLimits"  }
case object Prohibition extends AssetTypeInfo { val typeId = 190; def geometryType = "linear"; val label = ""; val layerName = "prohibition" }
case object PedestrianCrossings extends AssetTypeInfo { val typeId = 200; def geometryType = "point"; val label = "PedestrianCrossings"; val layerName = "pedestrianCrossings" }
case object HazmatTransportProhibition extends AssetTypeInfo { val typeId = 210; def geometryType = "linear"; val label = ""; val layerName = "hazardousMaterialTransportProhibition" }
case object Obstacles extends AssetTypeInfo { val typeId = 220; def geometryType = "point"; val label = ""; val layerName = "obstacles" }
case object RailwayCrossings extends AssetTypeInfo { val typeId = 230; def geometryType = "point"; val label = ""; val layerName = "railwayCrossings" }
case object DirectionalTrafficSigns extends AssetTypeInfo { val typeId = 240; def geometryType = "point"; val label = ""; val layerName = "directionalTrafficSigns" }
case object ServicePoints extends AssetTypeInfo { val typeId = 250; def geometryType = "point"; val label = ""; val layerName = "servicePoints" }
case object EuropeanRoads extends AssetTypeInfo { val typeId = 260; def geometryType = "linear"; val label = ""; val layerName = "europeanRoads" }
case object ExitNumbers extends AssetTypeInfo { val typeId = 270; def geometryType = "linear"; val label = ""; val layerName = "exitNumbers" }
case object TrafficLights extends AssetTypeInfo { val typeId = 280; def geometryType = "point"; val label =  ""; val layerName = "trafficLights"}
case object MaintenanceRoadAsset extends AssetTypeInfo { val typeId = 290; def geometryType = "linear"; val label = ""; val layerName = "maintenanceRoad" }
case object TrafficSigns extends AssetTypeInfo { val typeId = 300; def geometryType = "point"; val label = ""; val layerName = "trafficSigns"}
case object StateSpeedLimit extends AssetTypeInfo { val typeId = 310; def geometryType = "linear"; val label = "StateSpeedLimit"; val layerName = "totalWeightLimit" }
case object UnknownAssetTypeId extends  AssetTypeInfo {val typeId = 99; def geometryType = ""; val label = ""; val layerName = ""}
case object TrWidthLimit extends  AssetTypeInfo {val typeId = 370; def geometryType = "point"; val label = "TrWidthLimit"; val layerName = "trWidthLimits"}
case object TrHeightLimit extends  AssetTypeInfo {val typeId = 360; def geometryType = "point"; val label = "TrHeightLimit"; val layerName = "trHeightLimits"}
case object TrTrailerTruckWeightLimit extends  AssetTypeInfo {val typeId = 330; def geometryType = "point"; val label = "TrTrailerTruckWeightLimit"; val layerName = "trWeightLimits"}
case object TrBogieWeightLimit extends  AssetTypeInfo {val typeId = 350; def geometryType = "point"; val label = "TrBogieWeightLimit"; val layerName = "trWeightLimits"}
case object TrAxleWeightLimit extends  AssetTypeInfo {val typeId = 340; def geometryType = "point"; val label = "TrAxleWeightLimit"; val layerName = "trWeightLimits"}
case object TrWeightLimit extends  AssetTypeInfo {val typeId = 320; def geometryType = "point"; val label = "TrWeightLimit"; val layerName = "trWeightLimits"}
case object Manoeuvres extends AssetTypeInfo { val typeId = 380; def geometryType = "linear"; val label = "Manoeuvre"; val layerName = "manoeuvre" }
case object CareClass extends  AssetTypeInfo {val typeId = 390; def geometryType = "linear"; val label = "CareClass"; val layerName = "careClass"}
case object CarryingCapacity extends AssetTypeInfo { val typeId = 400; def geometryType = "linear"; val label = "CarryingCapacity" ; val layerName = "carryingCapacity"}

object AutoGeneratedValues {
  val allAutoGeneratedValues =
    Seq(
      "dr1conversion",
      "dr1_conversion",
      "automatic_correction",
      "excel_data_migration",
      "automatic_generation",
      "vvh_generated",
      "vvh_modified",
      "vvh_mtkclass_default"
    )
}