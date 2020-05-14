package fi.liikennevirasto.digiroad2

sealed trait TrafficSignTypeGroup{
  def value: Int
}
object TrafficSignTypeGroup{
  val values = Set(Unknown, SpeedLimits, RegulatorySigns, MaximumRestrictions, GeneralWarningSigns, ProhibitionsAndRestrictions, AdditionalPanels, MandatorySigns,
    PriorityAndGiveWaySigns, InformationSigns)

  def apply(intValue: Int):TrafficSignTypeGroup= {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object SpeedLimits extends TrafficSignTypeGroup{ def value = 1  }
  case object RegulatorySigns extends TrafficSignTypeGroup{ def value = 2 }
  case object MaximumRestrictions extends TrafficSignTypeGroup{ def value = 3 }
  case object GeneralWarningSigns extends TrafficSignTypeGroup{ def value = 4 }
  case object ProhibitionsAndRestrictions extends TrafficSignTypeGroup{ def value = 5 }
  case object AdditionalPanels extends TrafficSignTypeGroup{ def value = 6 }
  case object MandatorySigns extends TrafficSignTypeGroup{ def value = 7 }
  case object PriorityAndGiveWaySigns extends TrafficSignTypeGroup{ def value = 8 }
  case object InformationSigns extends TrafficSignTypeGroup{ def value = 9 }
  case object ServiceSigns extends TrafficSignTypeGroup{ def value = 10 }
  case object Unknown extends TrafficSignTypeGroup{ def value = 99 }
}

sealed trait TrafficSignType {

  val values = Seq()

  def group: TrafficSignTypeGroup

  val OTHvalue: Int

  val TRvalue: Int

  val NewLawCode: String

  val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq.empty[AdditionalPanelsType]

  def relevantAdditionalPanel = Seq.empty[AdditionalPanelsType]

  def allowed(additionalPanelsType: AdditionalPanelsType) : Boolean = {
    relevantAdditionalPanel.contains(additionalPanelsType)
  }

  def source = Seq("CSVimport", "TRimport")
}

object TrafficSignType {
  val values = Set(PriorityRoad, EndOfPriority, PriorityOverOncomingTraffic, PriorityForOncomingTraffic, GiveWay, Stop, SpeedLimitSign, EndSpeedLimit, SpeedLimitZone, EndSpeedLimitZone, UrbanArea, EndUrbanArea,
    TelematicSpeedLimit, PedestrianCrossingSign, ParkAndRide, BusLane, BusLaneEnds, TramLane, BusStopForLocalTraffic, TramStop, TaxiStation, ParkingLot,
    OneWayRoad, MotorwaySign, MotorwayEnds, ResidentialZone, EndOfResidentialZone, PedestrianZoneSign, EndOfPedestrianZone, BusStopForLongDistanceTraffic, MaximumLength, NoWidthExceeding,
    MaxHeightExceeding, MaxLadenExceeding, MaxMassCombineVehiclesExceeding, MaxTonsOneAxleExceeding, MaxTonsOnBogieExceeding, Warning, WRightBend, WLeftBend, WSeveralBendsRight,
    WSeveralBendsLeft, WDangerousDescent, WSteepAscent, WUnevenRoad, WChildren, RoadNarrows, TwoWayTraffic, SwingBridge, RoadWorks, SlipperyRoad, PedestrianCrossingWarningSign,
    Cyclists, IntersectionWithEqualRoads, IntersectionWithMinorRoad, IntersectionWithOneMinorRoad, IntersectionWithOneCrossMinorRoad, LightSignals, TramwayLine, FallingRocks, CrossWind,
    LevelCrossingWithoutGate, LevelCrossingWithGates, LevelCrossingWithOneTrack, LevelCrossingWithManyTracks, Moose, Reindeer, NoLeftTurn, NoRightTurn, NoUTurn, ClosedToAllVehicles,
    NoPowerDrivenVehicles, NoLorriesAndVans, NoVehicleCombinations, NoAgriculturalVehicles, NoMotorCycles, NoMotorSledges, NoVehiclesWithDangerGoods, NoBuses, NoMopeds, NoCyclesOrMopeds,
    NoPedestrians, NoPedestriansCyclesMopeds, NoRidersOnHorseback, NoEntry, OvertakingProhibited, EndProhibitionOfOvertaking, TaxiStationZoneBeginning, StandingPlaceForTaxi, StandingAndParkingProhibited,
    ParkingProhibited, ParkingProhibitedZone, EndOfParkingProhibitedZone, AlternativeParkingOddDays, AlternativeParkingEvenDays, CompulsoryFootPath, CompulsoryCycleTrack, CombinedCycleTrackAndFootPath,
    ParallelCycleTrackAndFootPath, ParallelCycleTrackAndFootPath2, DirectionToBeFollowed3, DirectionToBeFollowed4, DirectionToBeFollowed5, CompulsoryRoundabout, PassThisSide, DividerOfTraffic,
    CompulsoryTrackMotorSledges, CompulsoryTrackRidersHorseback, FreeWidth, FreeHeight, HeightElectricLine, SignAppliesBothDirections, SignAppliesBothDirectionsVertical, SignAppliesArrowDirections,
    RegulationBeginsFromSign, RegulationEndsToTheSign, HazmatProhibitionA, HazmatProhibitionB, ValidMonFri, ValidSat, ValidMultiplePeriod, TimeLimit, DistanceCompulsoryStop, DirectionOfPriorityRoad,
    CrossingLogTransportRoad, PassengerCar, Bus, Lorry, Van, VehicleForHandicapped, MotorCycle, Cycle, ParkingAgainstFee, ObligatoryUseOfParkingDisc, AdditionalPanelWithText,
    DrivingInServicePurposesAllowed, NoThroughRoad, NoThroughRoadRight, SymbolOfMotorway, Parking, ItineraryForIndicatedVehicleCategory, ItineraryForPedestrians, ItineraryForHandicapped,
    LocationSignForTouristService, FirstAid, FillingStation, Restaurant, PublicLavatory, DistanceFromSignToPointWhichSignApplies, DistanceWhichSignApplies,
    AdvanceDirectionSign, AdvanceDirectionSignSmall, AdvisorySignDetour, AdvisorySignDetourLarge, Detour, RouteToBeFollowed, InformationOnTrafficLanes, BiDirectionalInformationOnTrafficLanes,
    EndOfLane, AdvanceDirectionSignAbove, ExitSignAbove, DirectionSign, ExitSign, DirectionSignOnPrivateRoad, LocationSign, DirectionSignForLightTraffic, DirectionSignForDetourWithText, DirectionSignForDetour,
    DirectionSignForLocalPurposes, DirectionSignForMotorway, ParkAndRideFacilities, RecommendedMaxSpeed, SignShowingDistance, PlaceName, RoadNumberERoad, DirectionToTheNumberedRoad, RoadNumberPrimaryRoad,
    RoadNumberRegionalOrSecondaryRoad, RoadNumberOrdinaryRoad, RoadForMotorVehicles, Airport, Ferry, GoodsHarbour, IndustrialArea, RailwayStation, BusStation, ItineraryForDangerousGoodsTransport, OverpassOrUnderpassWithSteps,
    EmergencyExit, DirectionToEmergencyExit, AdvanceDirectionSignAboveSmall, OverpassOrUnderpassWithoutSteps, HusvagnCaravan, Moped)

  def applyOTHValue(intValue: Int): TrafficSignType = {
    values.find(_.OTHvalue == intValue).getOrElse(Unknown)
  }

  def applyTRValue(intValue: Int): TrafficSignType = {
    values.find(_.TRvalue == intValue).getOrElse(Unknown)
  }

  def applyNewLawCode(value: String): TrafficSignType = {
    values.find(_.NewLawCode == value).getOrElse(Unknown)
  }

  def apply(TrafficSignTypeGroup: TrafficSignTypeGroup): Set[Int] = {
    values.filter(_.group == TrafficSignTypeGroup).map(_.OTHvalue)
  }

  case object Unknown extends TrafficSignType {
    override def group: TrafficSignTypeGroup = TrafficSignTypeGroup.Unknown

    override val OTHvalue = 999
    override val TRvalue = 99
    override val NewLawCode = "99"

    override def source = Seq()
  }

}

trait PriorityAndGiveWaySigns extends TrafficSignType {
  override def group: TrafficSignTypeGroup = TrafficSignTypeGroup.PriorityAndGiveWaySigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object PriorityRoad extends PriorityAndGiveWaySigns {

  override val OTHvalue = 94
  override val TRvalue = 211
  override val NewLawCode = "B1"

  override val supportedAdditionalPanel  = Seq(DirectionOfPriorityRoad)

}

case object EndOfPriority extends PriorityAndGiveWaySigns {

  override val OTHvalue = 95
  override val TRvalue = 212
  override val NewLawCode = "B2"
}

case object PriorityOverOncomingTraffic extends PriorityAndGiveWaySigns {

  override val OTHvalue = 96
  override val TRvalue = 221
  override val NewLawCode = "B3"
}

case object PriorityForOncomingTraffic extends PriorityAndGiveWaySigns {

  override val OTHvalue = 97
  override val TRvalue = 222
  override val NewLawCode = "B4"

}

case object GiveWay extends PriorityAndGiveWaySigns {

  override val OTHvalue = 98
  override val TRvalue = 231
  override val NewLawCode = "B5"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(DistanceCompulsoryStop, DirectionOfPriorityRoad)
}

case object Stop extends PriorityAndGiveWaySigns {

  override val OTHvalue = 99
  override val TRvalue = 232
  override val NewLawCode = "B6"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(DistanceCompulsoryStop, DirectionOfPriorityRoad)

}

trait SpeedLimitsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.SpeedLimits

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object SpeedLimitSign extends SpeedLimitsType {
  override val OTHvalue = 1
  override val TRvalue = 361
  override val NewLawCode = "C32"
}

case object EndSpeedLimit extends SpeedLimitsType {
  override val OTHvalue = 2
  override val TRvalue = 362
  override val NewLawCode = "C33"
}

case object SpeedLimitZone extends SpeedLimitsType {
  override val OTHvalue = 3
  override val TRvalue = 363
  override val NewLawCode = "C34"
}

case object EndSpeedLimitZone extends SpeedLimitsType {
  override val OTHvalue = 4
  override val TRvalue = 364
  override val NewLawCode = "C35"
}

case object UrbanArea extends SpeedLimitsType {
  override val OTHvalue = 5
  override val TRvalue = 571
  override val NewLawCode = "E22"
}

case object EndUrbanArea extends SpeedLimitsType {
  override val OTHvalue = 6
  override val TRvalue = 572
  override val NewLawCode = "E23"
}

//TODO: Verify value
case object TelematicSpeedLimit extends SpeedLimitsType {
  override val OTHvalue = 44
  override val TRvalue = 0
  override val NewLawCode = "0"

  override def source = Seq()
}


trait RegulatorySignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.RegulatorySigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object PedestrianCrossingSign extends RegulatorySignsType {
  override val OTHvalue = 7
  override val TRvalue = 511
  override val NewLawCode = "E1"
}

case object ParkAndRide extends RegulatorySignsType { //TODO check that name
  override val OTHvalue = 137
  override val TRvalue = 520
  override val NewLawCode = "E3" //TODO: new sign implements 4 different types

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object BusLane extends RegulatorySignsType {
  override val OTHvalue = 63
  override val TRvalue = 541
  override val NewLawCode = "E9" //TODO: subtypes

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusLaneEnds extends RegulatorySignsType {
  override val OTHvalue = 64
  override val TRvalue = 542
  override val NewLawCode = "E10" //TODO: subtypes

}

case object TramLane extends RegulatorySignsType {
  override val OTHvalue = 65
  override val TRvalue = 543
  override val NewLawCode = "E11" //TODO: subtypes

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusStopForLocalTraffic extends RegulatorySignsType {
  override val OTHvalue = 66
  override val TRvalue = 531
  override val NewLawCode = "E6" //TODO: same sign as the one on line :335

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)

}

case object TramStop extends RegulatorySignsType {
  override val OTHvalue = 68
  override val TRvalue = 533
  override val NewLawCode = "E7"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object TaxiStation extends RegulatorySignsType {
  override val OTHvalue = 69
  override val TRvalue = 534
  override val NewLawCode = "E8"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object ParkingLot extends RegulatorySignsType {
  override val OTHvalue = 105
  override val TRvalue = 521
  override val NewLawCode = "E4" //TODO: subtypes

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign,ParkingAgainstFee,	ObligatoryUseOfParkingDisc)
}

case object OneWayRoad extends RegulatorySignsType {
  override val OTHvalue = 106
  override val TRvalue = 551
  override val NewLawCode = "E14.1"
}

case object MotorwaySign extends RegulatorySignsType {
  override val OTHvalue = 107
  override val TRvalue = 561
  override val NewLawCode = "E15"
}

case object MotorwayEnds extends RegulatorySignsType {
  override val OTHvalue = 108
  override val TRvalue = 562
  override val NewLawCode = "E16"
}

case object ResidentialZone extends RegulatorySignsType {
  override val OTHvalue = 109
  override val TRvalue = 573
  override val NewLawCode = "E24"
}

case object EndOfResidentialZone extends RegulatorySignsType {
  override val OTHvalue = 110
  override val TRvalue = 574
  override val NewLawCode = "E25"
}

case object PedestrianZoneSign extends RegulatorySignsType {
  override val OTHvalue = 111
  override val TRvalue = 575
  override val NewLawCode = "E26"
}

case object EndOfPedestrianZone extends RegulatorySignsType {
  override val OTHvalue = 112
  override val TRvalue = 576
  override val NewLawCode = "E27"
}

case object BusStopForLongDistanceTraffic extends RegulatorySignsType {
  override val OTHvalue = 66
  override val TRvalue = 532
  override val NewLawCode = "E6" //TODO: same sign as the one on line :260

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat)
}

trait MaximumRestrictionsType extends TrafficSignType{
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.MaximumRestrictions

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object MaximumLength extends MaximumRestrictionsType {
  override val OTHvalue = 8
  override val TRvalue = 343
  override val NewLawCode = "C23"
}

case object NoWidthExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 30
  override val TRvalue = 341
  override val NewLawCode = "C21"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)

}

case object MaxHeightExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 31
  override val TRvalue = 342
  override val NewLawCode = "C22"
}

case object MaxLadenExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 32
  override val TRvalue = 344
  override val NewLawCode = "C24"
}

case object MaxMassCombineVehiclesExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 33
  override val TRvalue = 345
  override val NewLawCode = "C25"
}

case object MaxTonsOneAxleExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 34
  override val TRvalue = 346
  override val NewLawCode = "C26"

}

case object MaxTonsOnBogieExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 35
  override val TRvalue = 347
  override val NewLawCode = "C27"
}

trait GeneralWarningSignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.GeneralWarningSigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object Warning extends GeneralWarningSignsType {
  override val OTHvalue = 9
  override val TRvalue = 189
  override val NewLawCode = "A33"
  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(FreeHeight)

}

case object WRightBend extends GeneralWarningSignsType {
  override val OTHvalue = 36
  override val TRvalue = 111
  override val NewLawCode = "A1.1"
}

case object WLeftBend extends GeneralWarningSignsType {
  override val OTHvalue = 37
  override val TRvalue = 112
  override val NewLawCode = "A1.2"
}

case object WSeveralBendsRight extends GeneralWarningSignsType {
  override val OTHvalue = 38
  override val TRvalue = 113
  override val NewLawCode = "A2.1"
}

case object WSeveralBendsLeft extends GeneralWarningSignsType {
  override val OTHvalue = 39
  override val TRvalue = 114
  override val NewLawCode = "A2.2"
}

case object WDangerousDescent extends GeneralWarningSignsType {
  override val OTHvalue = 40
  override val TRvalue = 115
  override val NewLawCode = "A3.2"
}

case object WSteepAscent extends GeneralWarningSignsType {
  override val OTHvalue = 41
  override val TRvalue = 116
  override val NewLawCode = "A3.1"
}

case object WUnevenRoad extends GeneralWarningSignsType {
  override val OTHvalue = 42
  override val TRvalue = 141
  override val NewLawCode = "A9"
}

case object WChildren extends GeneralWarningSignsType {
  override val OTHvalue = 43
  override val TRvalue = 152
  override val NewLawCode = "A17"
}

case object RoadNarrows extends GeneralWarningSignsType {
  override val OTHvalue = 82
  override val TRvalue = 121
  override val NewLawCode = "A4"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(FreeWidth)

}

case object TwoWayTraffic extends GeneralWarningSignsType {
  override val OTHvalue = 83
  override val TRvalue = 122
  override val NewLawCode = "A5"
}

case object SwingBridge extends GeneralWarningSignsType {
  override val OTHvalue = 84
  override val TRvalue = 131
  override val NewLawCode = "A6"
}

case object RoadWorks extends GeneralWarningSignsType {
  override val OTHvalue = 85
  override val TRvalue = 142
  override val NewLawCode = "A11"
}

case object SlipperyRoad extends GeneralWarningSignsType {
  override val OTHvalue = 86
  override val TRvalue = 144
  override val NewLawCode = "A13"
}

case object PedestrianCrossingWarningSign extends GeneralWarningSignsType {
  override val OTHvalue = 87
  override val TRvalue = 151
  override val NewLawCode = "A15"
}

case object Cyclists extends GeneralWarningSignsType {
  override val OTHvalue = 88
  override val TRvalue = 153
  override val NewLawCode = "A18"
}

case object IntersectionWithEqualRoads extends GeneralWarningSignsType {
  override val OTHvalue = 89
  override val TRvalue = 161
  override val NewLawCode = "A21"
}

case object IntersectionWithMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 127
  override val TRvalue = 162
  override val NewLawCode = "A22.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithOneMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 128
  override val TRvalue = 163
  override val NewLawCode = "A22.3"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithOneCrossMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 129
  override val TRvalue = 164
  override val NewLawCode = "A22.4"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object LightSignals extends GeneralWarningSignsType {
  override val OTHvalue = 90
  override val TRvalue = 165
  override val NewLawCode = "A23"
}

case object TramwayLine extends GeneralWarningSignsType {
  override val OTHvalue = 91
  override val TRvalue = 167
  override val NewLawCode = "A25"
}

case object FallingRocks extends GeneralWarningSignsType {
  override val OTHvalue = 92
  override val TRvalue = 181
  override val NewLawCode = "A30"
}

case object CrossWind extends GeneralWarningSignsType {
  override val OTHvalue = 93
  override val TRvalue = 183
  override val NewLawCode = "A32"
}

case object LevelCrossingWithoutGate extends GeneralWarningSignsType {
  override val OTHvalue = 130
  override val TRvalue = 171
  override val NewLawCode = "A26"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithGates extends GeneralWarningSignsType {
  override val OTHvalue = 131
  override val TRvalue = 172
  override val NewLawCode = "A27"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithOneTrack extends GeneralWarningSignsType {
  override val OTHvalue = 132
  override val TRvalue = 176
  override val NewLawCode = "A29.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithManyTracks extends GeneralWarningSignsType {
  override val OTHvalue = 133
  override val TRvalue = 177
  override val NewLawCode = "A29.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object Moose extends GeneralWarningSignsType {
  override val OTHvalue = 125
  override val TRvalue = 155
  override val NewLawCode = "A20.1"
}

case object Reindeer extends GeneralWarningSignsType {
  override val OTHvalue = 126
  override val TRvalue = 156
  override val NewLawCode = "A20.2"
}

trait ProhibitionsAndRestrictionsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.ProhibitionsAndRestrictions

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object NoLeftTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 10
  override val TRvalue = 332
  override val NewLawCode = "C18"
}

case object NoRightTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 11
  override val TRvalue = 333
  override val NewLawCode = "C19"
}

case object NoUTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 12
  override val TRvalue = 334
  override val NewLawCode = "C20"
}

case object ClosedToAllVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 13
  override val TRvalue = 311
  override val NewLawCode = "C1"
}

case object NoPowerDrivenVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 14
  override val TRvalue = 312
  override val NewLawCode = "C2"
}

case object NoLorriesAndVans extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 15
  override val TRvalue = 313
  override val NewLawCode = "C3"
}

case object NoVehicleCombinations extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 16
  override val TRvalue = 314
  override val NewLawCode = "C4"
}

case object NoAgriculturalVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 17
  override val TRvalue = 315
  override val NewLawCode = "C5" //TODO: new sign
}

case object NoMotorCycles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 18
  override val TRvalue = 316
  override val NewLawCode = "C6"
}

case object NoMotorSledges extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 19
  override val TRvalue = 317
  override val NewLawCode = "C7"
}

case object NoVehiclesWithDangerGoods extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 20
  override val TRvalue = 318
  override val NewLawCode = "C8"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HazmatProhibitionA, HazmatProhibitionB)
}

case object NoBuses extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 21
  override val TRvalue = 319
  override val NewLawCode = "C9"
}

case object NoMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 22
  override val TRvalue = 321
  override val NewLawCode = "C10"
}

case object NoCyclesOrMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 23
  override val TRvalue = 322
  override val NewLawCode = "C12"
}

case object NoPedestrians extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 24
  override val TRvalue = 323
  override val NewLawCode = "C13"
}

case object NoPedestriansCyclesMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 25
  override val TRvalue = 324
  override val NewLawCode = "C15"
}

case object NoRidersOnHorseback extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 26
  override val TRvalue = 325
  override val NewLawCode = "C16"
}

case object NoEntry extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 27
  override val TRvalue = 331
  override val NewLawCode = "C17"
}

case object OvertakingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 28
  override val TRvalue = 351
  override val NewLawCode = "C28"
}

case object EndProhibitionOfOvertaking extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 29
  override val TRvalue = 352
  override val NewLawCode = "C29"
}

case object TaxiStationZoneBeginning extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 80
  override val TRvalue = 375
  override val NewLawCode = "C41"
}

case object StandingPlaceForTaxi extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 81
  override val TRvalue = 376
  override val NewLawCode = "C42"
}

case object StandingAndParkingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 100
  override val TRvalue = 371
  override val NewLawCode = "C37"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 101
  override val TRvalue = 372
  override val NewLawCode = "C38"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign, ParkingAgainstFee,  ObligatoryUseOfParkingDisc)
}

case object ParkingProhibitedZone extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 102
  override val TRvalue = 373
  override val NewLawCode = "C39"
}

case object EndOfParkingProhibitedZone extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 103
  override val TRvalue = 374
  override val NewLawCode = "C40"
}

case object AlternativeParkingOddDays extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 104
  override val TRvalue = 381
  override val NewLawCode = "C44.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object AlternativeParkingEvenDays extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 134
  override val TRvalue = 382
  override val NewLawCode = "C44.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

trait MandatorySignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.MandatorySigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object CompulsoryFootPath extends MandatorySignsType {
  override val OTHvalue = 70
  override val TRvalue = 421
  override val NewLawCode = "D4"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object CompulsoryCycleTrack extends MandatorySignsType {
  override val OTHvalue = 71
  override val TRvalue = 422
  override val NewLawCode = "D5"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object CombinedCycleTrackAndFootPath extends MandatorySignsType {
  override val OTHvalue = 72
  override val TRvalue = 423
  override val NewLawCode = "D6"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParallelCycleTrackAndFootPath extends MandatorySignsType {
  override val OTHvalue = 72
  override val TRvalue = 424
  override val NewLawCode = "D7.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object ParallelCycleTrackAndFootPath2 extends MandatorySignsType {
  override val OTHvalue = 72
  override val TRvalue = 425
  override val NewLawCode = "D7.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object DirectionToBeFollowed3 extends MandatorySignsType {
  override val OTHvalue = 74
  override val TRvalue = 413
  override val NewLawCode = "D1.4"
}

case object DirectionToBeFollowed4 extends MandatorySignsType {
  override val OTHvalue = 74
  override val TRvalue = 414
  override val NewLawCode = "D1.6"
}

case object DirectionToBeFollowed5 extends MandatorySignsType {
  override val OTHvalue = 74
  override val TRvalue = 415
  override val NewLawCode = "D1.8"
}

case object CompulsoryRoundabout extends MandatorySignsType {
  override val OTHvalue = 77
  override val TRvalue = 416
  override val NewLawCode = "D2"
}

case object PassThisSide extends MandatorySignsType {
  override val OTHvalue = 78
  override val TRvalue = 417
  override val NewLawCode = "D3" // TODO: new subtypes on the law code
}

case object DividerOfTraffic extends MandatorySignsType {
  override val OTHvalue = 78
  override val TRvalue = 418
  override val NewLawCode = "D3.3"
}

case object CompulsoryTrackMotorSledges extends MandatorySignsType {
  override val OTHvalue = 135
  override val TRvalue = 426
  override val NewLawCode = "D8"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object CompulsoryTrackRidersHorseback extends MandatorySignsType {
  override val OTHvalue = 136
  override val TRvalue = 427
  override val NewLawCode = "D9"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign, ValidMonFri,	ValidSat,	ValidMultiplePeriod,
    ParkingAgainstFee,	ObligatoryUseOfParkingDisc)
}

trait InformationSignsType extends TrafficSignType  {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.InformationSigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object NoThroughRoad extends InformationSignsType {
  override val OTHvalue = 113
  override val TRvalue = 651
  override val NewLawCode = "F24.1"
}

case object NoThroughRoadRight extends InformationSignsType {
  override val OTHvalue = 114
  override val TRvalue = 652
  override val NewLawCode = "F24.2"
}

case object SymbolOfMotorway extends InformationSignsType {
  override val OTHvalue = 115
  override val TRvalue = 671
  override val NewLawCode = "F37"
}

case object Parking extends InformationSignsType {
  override val OTHvalue = 116
  override val TRvalue = 677
  override val NewLawCode = "F46.1"
}

case object ItineraryForIndicatedVehicleCategory extends InformationSignsType  {
  override val OTHvalue = 117
  override val TRvalue = 681
  override val NewLawCode = "F50"
}

case object ItineraryForPedestrians extends InformationSignsType {
  override val OTHvalue = 118
  override val TRvalue = 682
  override val NewLawCode = "F52"
}

case object ItineraryForHandicapped extends InformationSignsType {
  override val OTHvalue = 119
  override val TRvalue = 683
  override val NewLawCode = "F53"
}

case object AdvanceDirectionSignSmall extends InformationSignsType {
  override val OTHvalue = 178
  override val TRvalue = 612
  override val NewLawCode = "F2.1"
}

case object AdvisorySignDetourLarge extends InformationSignsType {
  override val OTHvalue = 152
  override val TRvalue = 614
  override val NewLawCode = "F4.1"
}

case object Detour extends InformationSignsType {
  override val OTHvalue = 153
  override val TRvalue = 615
  override val NewLawCode = "F5"
}

case object RouteToBeFollowed extends InformationSignsType {
  override val OTHvalue = 154
  override val TRvalue = 616
  override val NewLawCode = "F6"
}

case object InformationOnTrafficLanes extends InformationSignsType {
  override val OTHvalue = 155
  override val TRvalue = 621
  override val NewLawCode = "F7.1"
}

case object BiDirectionalInformationOnTrafficLanes extends InformationSignsType {
  override val OTHvalue = 156
  override val TRvalue = 622
  override val NewLawCode = "F7.2"
}

case object EndOfLane extends InformationSignsType {
  override val OTHvalue = 157
  override val TRvalue = 623
  override val NewLawCode = "F8.1"
}

case object AdvanceDirectionSignAbove extends InformationSignsType {
  override val OTHvalue = 158
  override val TRvalue = 631
  override val NewLawCode = "F10"
}

case object ExitSignAbove extends InformationSignsType {
  override val OTHvalue = 159
  override val TRvalue = 633
  override val NewLawCode = "F12"
}

case object DirectionSign extends InformationSignsType {
  override val OTHvalue = 160
  override val TRvalue = 641
  override val NewLawCode = "F13" //TODO: Same sign as lines :1005 :1053 :1017 :1047
}

case object ExitSign extends InformationSignsType {
  override val OTHvalue = 161
  override val TRvalue = 642
  override val NewLawCode = "F14"
}

case object DirectionSignOnPrivateRoad extends InformationSignsType {
  override val OTHvalue = 162
  override val TRvalue = 643
  override val NewLawCode = "F13" //TODO: Same sign as lines :1005 :1053 :1017 :1047
}

case object LocationSign extends InformationSignsType {
  override val OTHvalue = 163
  override val TRvalue = 644
  override val NewLawCode = "F16"
}

case object DirectionSignForLightTraffic extends InformationSignsType {
  override val OTHvalue = 164
  override val TRvalue = 645
  override val NewLawCode = "F19" //TODO: missing subtypes new law code
}

case object DirectionSignForDetourWithText extends InformationSignsType {
  override val OTHvalue = 165
  override val TRvalue = 646
  override val NewLawCode = "F15" //TODO: same sign as lines :1035 :1041
}

case object DirectionSignForDetour extends InformationSignsType {
  override val OTHvalue = 166
  override val TRvalue = 647
  override val NewLawCode = "F15" //TODO: same sign as lines :1035 :1041
}

case object DirectionSignForLocalPurposes extends InformationSignsType {
  override val OTHvalue = 167
  override val TRvalue = 648
  override val NewLawCode = "F13" //TODO: Same sign as lines :1005 :1053 :1017 :1047
}

case object DirectionSignForMotorway extends InformationSignsType {
  override val OTHvalue = 168
  override val TRvalue = 649
  override val NewLawCode = "F13" //TODO: Same sign as lines :1005 :1053 :1017 :1047
}

case object ParkAndRideFacilities extends InformationSignsType {
  override val OTHvalue = 169
  override val TRvalue = 650
  override val NewLawCode = "F18.1"
}

case object RecommendedMaxSpeed extends InformationSignsType {
  override val OTHvalue = 170
  override val TRvalue = 653
  override val NewLawCode = "F25"
}

case object SignShowingDistance extends InformationSignsType {
  override val OTHvalue = 171
  override val TRvalue = 661
  override val NewLawCode = "F26"
}

case object PlaceName extends InformationSignsType {
  override val OTHvalue = 172
  override val TRvalue = 662
  override val NewLawCode = "F27.1"
}

case object RoadNumberERoad extends InformationSignsType {
  override val OTHvalue = 173
  override val TRvalue = 663
  override val NewLawCode = "F28"
}

case object DirectionToTheNumberedRoad extends InformationSignsType {
  override val OTHvalue = 174
  override val TRvalue = 667
  override val NewLawCode = "F35"
}

case object RoadNumberPrimaryRoad extends InformationSignsType {
  override val OTHvalue = 175
  override val TRvalue = 664
  override val NewLawCode = "F29"
}

case object RoadNumberRegionalOrSecondaryRoad extends InformationSignsType {
  override val OTHvalue = 176
  override val TRvalue = 665
  override val NewLawCode = "F30"
}

case object RoadNumberOrdinaryRoad extends InformationSignsType {
  override val OTHvalue = 177
  override val TRvalue = 666
  override val NewLawCode = "F32"
}

case object RoadForMotorVehicles extends InformationSignsType {
  override val OTHvalue = 179
  override val TRvalue = 672
  override val NewLawCode = "F38"
}

case object Airport extends InformationSignsType {
  override val OTHvalue = 180
  override val TRvalue = 673
  override val NewLawCode = "F39"
}

case object Ferry extends InformationSignsType {
  override val OTHvalue = 181
  override val TRvalue = 674
  override val NewLawCode = "F40"
}

case object GoodsHarbour extends InformationSignsType {
  override val OTHvalue = 182
  override val TRvalue = 675
  override val NewLawCode = "F42"
}

case object IndustrialArea extends InformationSignsType {
  override val OTHvalue = 183
  override val TRvalue = 676
  override val NewLawCode = "F44"
}

case object RailwayStation extends InformationSignsType {
  override val OTHvalue = 184
  override val TRvalue = 678
  override val NewLawCode = "F47"
}

case object BusStation extends InformationSignsType {
  override val OTHvalue = 185
  override val TRvalue = 679
  override val NewLawCode = "F48"
}

case object ItineraryForDangerousGoodsTransport extends InformationSignsType {
  override val OTHvalue = 186
  override val TRvalue = 684
  override val NewLawCode = "F51"
}

case object OverpassOrUnderpassWithSteps extends InformationSignsType {
  override val OTHvalue = 187
  override val TRvalue = 685
  override val NewLawCode = "F54.1"
}

case object OverpassOrUnderpassWithoutSteps extends InformationSignsType {
  override val OTHvalue = 188
  override val TRvalue = 686
  override val NewLawCode = "F55.1"
}

case object EmergencyExit extends InformationSignsType {
  override val OTHvalue = 189
  override val TRvalue = 690
  override val NewLawCode = "F56.1"
}

case object DirectionToEmergencyExit extends InformationSignsType {
  override val OTHvalue = 190
  override val TRvalue = 691
  override val NewLawCode = "F57.1"
}

case object AdvanceDirectionSignAboveSmall extends InformationSignsType {
  override val OTHvalue = 191
  override val TRvalue = 632
  override val NewLawCode = "F11"
}

case object AdvanceDirectionSign extends InformationSignsType {
  override val OTHvalue = 192
  override val TRvalue = 611
  override val NewLawCode = "F1.1"
}

case object AdvisorySignDetour extends InformationSignsType {
  override val OTHvalue = 193
  override val TRvalue = 613
  override val NewLawCode = "F4.2"
}

trait ServiceSignsType extends TrafficSignType  {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.ServiceSigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object LocationSignForTouristService  extends ServiceSignsType {
  override val OTHvalue = 120
  override val TRvalue = 704
  override val NewLawCode = "G4"
}

case object FirstAid  extends ServiceSignsType {
  override val OTHvalue = 121
  override val TRvalue = 715
  override val NewLawCode = "G9"
}

case object FillingStation  extends ServiceSignsType {
  override val OTHvalue = 122
  override val TRvalue = 722
  override val NewLawCode = "G11.1"
}

case object Restaurant  extends ServiceSignsType {
  override val OTHvalue = 123
  override val TRvalue = 724
  override val NewLawCode = "G13"
}

case object PublicLavatory  extends ServiceSignsType {
  override val OTHvalue = 124
  override val TRvalue = 726
  override val NewLawCode = "G15"
}


trait AdditionalPanelsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.AdditionalPanels
}

case object FreeWidth extends AdditionalPanelsType {
  override val OTHvalue = 45
  override val TRvalue = 821
  override val NewLawCode = "H6"
}

case object FreeHeight extends AdditionalPanelsType {
  override val OTHvalue = 46
  override val TRvalue = 822
  override val NewLawCode = "H7"
}

case object HeightElectricLine extends AdditionalPanelsType {
  override val OTHvalue = 139
  override val TRvalue = 823
  override val NewLawCode = "H8"
}

case object SignAppliesBothDirections extends AdditionalPanelsType {
  override val OTHvalue = 140
  override val TRvalue = 824
  override val NewLawCode = "H9.1"
}

case object SignAppliesBothDirectionsVertical extends AdditionalPanelsType {
  override val OTHvalue = 141
  override val TRvalue = 825
  override val NewLawCode = "H9.2"
}

case object SignAppliesArrowDirections extends AdditionalPanelsType {
  override val OTHvalue = 142
  override val TRvalue = 826
  override val NewLawCode = "H10" //TODO: same as signs :1275 :1281
}

case object RegulationBeginsFromSign extends AdditionalPanelsType {
  override val OTHvalue = 143
  override val TRvalue = 827
  override val NewLawCode = "H10" //TODO: same as signs :1275 :1281
}

case object RegulationEndsToTheSign extends AdditionalPanelsType {
  override val OTHvalue = 144
  override val TRvalue = 828
  override val NewLawCode = "H11"
}

case object HazmatProhibitionA extends AdditionalPanelsType {
  override val OTHvalue = 47
  override val TRvalue = 848
  override val NewLawCode = "H14"
}

case object HazmatProhibitionB extends AdditionalPanelsType {
  override val OTHvalue = 48
  override val TRvalue = 849
  override val NewLawCode = "H15"
}

case object ValidMonFri extends AdditionalPanelsType {
  override val OTHvalue = 49
  override val TRvalue = 851
  override val NewLawCode = "H17.1"
}

case object ValidSat extends AdditionalPanelsType {
  override val OTHvalue = 50
  override val TRvalue = 852
  override val NewLawCode = "H17.2"
}

case object ValidMultiplePeriod extends AdditionalPanelsType {
  override val OTHvalue = 145
  override val TRvalue = 853
  override val NewLawCode = "H17.3"
}

case object TimeLimit extends AdditionalPanelsType {
  override val OTHvalue = 51
  override val TRvalue = 854
  override val NewLawCode = "H18"
}

case object DistanceCompulsoryStop extends AdditionalPanelsType {
  override val OTHvalue = 138
  override val TRvalue = 816
  override val NewLawCode = "H5"
}

case object DirectionOfPriorityRoad extends AdditionalPanelsType {
  override val OTHvalue = 146
  override val TRvalue = 861
  override val NewLawCode = "H22.1"
}

case object CrossingLogTransportRoad extends AdditionalPanelsType {
  override val OTHvalue = 147
  override val TRvalue = 862
  override val NewLawCode = "99" //TODO: Deprecated
}

case object PassengerCar  extends AdditionalPanelsType {
  override val OTHvalue = 52
  override val TRvalue = 831
  override val NewLawCode = "H12.1"
}

case object Bus  extends AdditionalPanelsType {
  override val OTHvalue = 53
  override val TRvalue = 832
  override val NewLawCode = "H12.2"
}

case object Lorry  extends AdditionalPanelsType {
  override val OTHvalue = 54
  override val TRvalue = 833
  override val NewLawCode = "H12.3"
}

case object Van  extends AdditionalPanelsType {
  override val OTHvalue = 55
  override val TRvalue = 834
  override val NewLawCode = "H12.4"
}

case object VehicleForHandicapped  extends AdditionalPanelsType {
  override val OTHvalue = 56
  override val TRvalue = 836
  override val NewLawCode = "H12.7"
}

case object MotorCycle  extends AdditionalPanelsType {
  override val OTHvalue = 57
  override val TRvalue = 841
  override val NewLawCode = "H12.8"
}

case object Cycle  extends AdditionalPanelsType {
  override val OTHvalue = 58
  override val TRvalue = 843
  override val NewLawCode = "H12.10"
}

case object ParkingAgainstFee  extends AdditionalPanelsType {
  override val OTHvalue = 59
  override val TRvalue = 855
  override val NewLawCode = "H20"
}

case object ObligatoryUseOfParkingDisc  extends AdditionalPanelsType {
  override val OTHvalue = 60
  override val TRvalue = 856
  override val NewLawCode = "H19" //TODO: missing subtypes
}

case object AdditionalPanelWithText  extends AdditionalPanelsType {
  override val OTHvalue = 61
  override val TRvalue = 871
  override val NewLawCode = "H24"
}

case object DrivingInServicePurposesAllowed  extends AdditionalPanelsType {
  override val OTHvalue = 62
  override val TRvalue = 872
  override val NewLawCode = "H25"
}

case object DistanceWhichSignApplies extends AdditionalPanelsType {
  override val OTHvalue = 148
  override val TRvalue = 814
  override val NewLawCode = "H3"
}

case object DistanceFromSignToPointWhichSignApplies extends AdditionalPanelsType {
  override val OTHvalue = 149
  override val TRvalue = 815
  override val NewLawCode = "H4"
}

case object HusvagnCaravan extends AdditionalPanelsType {
  override val OTHvalue = 150
  override val TRvalue = 835
  override val NewLawCode = "H12.5"
}

case object Moped extends AdditionalPanelsType {
  override val OTHvalue = 151
  override val TRvalue = 842
  override val NewLawCode = "H12.9"
}


sealed trait UrgencyOfRepair {
  def value: Int
  def description: String
}
object UrgencyOfRepair {
  val values = Set(Unknown, VeryUrgent, Urgent, SomehowUrgent, NotUrgent )

  def apply(intValue: Int):UrgencyOfRepair = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object VeryUrgent extends UrgencyOfRepair { def value = 1; def description = "Erittäin kiireellinen"  }
  case object Urgent extends UrgencyOfRepair { def value = 2; def description = "kiireellinen" }
  case object SomehowUrgent extends UrgencyOfRepair { def value = 3; def description = "Jokseenkin kiireellinen" }
  case object NotUrgent extends UrgencyOfRepair { def value = 4; def description = "Ei kiireellinen" }
  case object Unknown extends UrgencyOfRepair { def value = 999; def description = "Ei tiedossa" }
}


sealed trait SignLifeCycle {
  def value: Int
  def description: String
}
object SignLifeCycle{
  val values = Set(Unknown, Planned, UnderConstruction, Realized, TemporarilyInUse, TemporarilyOutOfService, OutgoingPermanentDevice )

  def apply(intValue: Int):SignLifeCycle = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Planned extends SignLifeCycle { def value = 1; def description = "Suunnitteilla"  }
  case object UnderConstruction extends SignLifeCycle { def value = 2; def description = "Rakenteilla" }
  case object Realized extends SignLifeCycle { def value = 3; def description = "Toteutuma" }
  case object TemporarilyInUse extends SignLifeCycle { def value = 4; def description = "käytössä tilapäisesti" }
  case object TemporarilyOutOfService extends SignLifeCycle { def value = 5; def description = "Pois käytöstä tilapaisesti" }
  case object OutgoingPermanentDevice extends SignLifeCycle { def value = 99; def description = "Poistuva pysyvä laite" }
  case object Unknown extends SignLifeCycle { def value = 999; def description = "Ei tiedossa" }
}


sealed trait Structure {
  def value: Int
  def description: String
}
object Structure {
  val values = Set(Unknown, Pole, Wall, Bridge, Portal, BarBarrier, Other )

  def apply(intValue: Int):Structure = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Pole extends Structure { def value = 1; def description = "Tolppa"  }
  case object Wall extends Structure { def value = 2; def description = "Seinä" }
  case object Bridge extends Structure { def value = 3; def description = "Silta" }
  case object Portal extends Structure { def value = 4; def description = "Portaali" }
  case object BarBarrier extends Structure { def value = 5; def description = "Puomi tai muu esterakennelma" }
  case object Other extends Structure { def value = 6; def description = "muu" }
  case object Unknown extends Structure { def value = 999; def description = "Ei tiedossa" }
}


sealed trait Condition {
  def value: Int
  def description: String
}
object Condition {
  val values = Set(Unknown, VeryPoor, Poor, Fair, Good, VeryGood )

  def apply(intValue: Int):Condition = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object VeryPoor extends Condition { def value = 1; def description = "Erittäin huono"  }
  case object Poor extends Condition { def value = 2; def description = "Huono" }
  case object Fair extends Condition { def value = 3; def description = "Tyydyttävä" }
  case object Good extends Condition { def value = 4; def description = "Hyvä" }
  case object VeryGood extends Condition { def value = 5; def description = "Erittäin hyvä" }
  case object Unknown extends Condition { def value = 999; def description = "Ei tiedossa" }
}


sealed trait Size {
  def value: Int
  def description: String
}
object Size {
  val values = Set(Unknown, CompactSign, RegularSign, LargeSign )

  def apply(intValue: Int):Size = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object CompactSign extends Size { def value = 1; def description = "Pienikokoinen merkki"  }
  case object RegularSign extends Size { def value = 2; def description = "Normaalikokoinen merkki" }
  case object LargeSign extends Size { def value = 3; def description = "Suurikokoinen merkki" }
  case object Unknown extends Size { def value = 999; def description = "Ei tiedossa" }
}


sealed trait CoatingType {
  def value: Int
  def description: String
}
object CoatingType {
  val values = Set(Unknown, R1ClassSheeting, R2ClassSheeting, R3ClassSheeting )

  def apply(intValue: Int):CoatingType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object R1ClassSheeting extends CoatingType { def value = 1; def description = "R1-luokan kalvo"  }
  case object R2ClassSheeting extends CoatingType { def value = 2; def description = "R2-luokan kalvo" }
  case object R3ClassSheeting extends CoatingType { def value = 3; def description = "R3-luokan kalvo" }
  case object Unknown extends CoatingType { def value = 999; def description = "Ei tiedossa" }
}


sealed trait SignMaterial {
  def value: Int
  def description: String
}
object SignMaterial {
  val values = Set(Unknown, Plywood, Aluminum, Other )

  def apply(intValue: Int):SignMaterial = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Plywood extends SignMaterial { def value = 1; def description = "Vaneri"  }
  case object Aluminum extends SignMaterial { def value = 2; def description = "Alumiini" }
  case object Other extends SignMaterial { def value = 3; def description = "Muu" }
  case object Unknown extends SignMaterial { def value = 999; def description = "Ei tiedossa" }
}


sealed trait LocationSpecifier {
  def value: Int
  def description: String
}
object LocationSpecifier {
  val values = Set(Unknown, RightSideOfRoad, LeftSideOfRoad, AboveLane, TrafficIslandOrTrafficDivider, LengthwiseRelativeToTrafficFlow, OnRoadOrStreetNetwork)

  def apply(intValue: Int):LocationSpecifier = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object RightSideOfRoad extends LocationSpecifier { def value = 1; def description = "Väylän oikea puoli"  }
  case object LeftSideOfRoad extends LocationSpecifier { def value = 2; def description = "Väylän vasen puoli" }
  case object AboveLane extends LocationSpecifier { def value = 3; def description = "Kaistan yläpuolella" }
  case object TrafficIslandOrTrafficDivider extends LocationSpecifier { def value = 4; def description = "Keskisaareke tai liikenteenjakaja" }
  case object LengthwiseRelativeToTrafficFlow extends LocationSpecifier { def value = 5; def description = "Pitkittäin ajosuuntaan nähden" }

  /*English description: On road or street network, for example parking area or courtyard*/
  case object OnRoadOrStreetNetwork extends LocationSpecifier { def value = 6; def description = "Tie- ja katuverkon puolella, esimerkiksi parkkialueella tai piha-alueella" }
  case object Unknown extends LocationSpecifier { def value = 999; def description = "Ei tiedossa" }
}


sealed trait TypeOfDamage {
  def value: Int
  def description: String
}
object TypeOfDamage {
  val values = Set(Unknown, Rust, Battered, Paint, OtherDamage)

  def apply(intValue: Int):TypeOfDamage = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Rust extends TypeOfDamage { def value = 1; def description = "Ruostunut"  }
  case object Battered extends TypeOfDamage { def value = 2; def description = "Kolhiintunut" }
  case object Paint extends TypeOfDamage { def value = 3; def description = "Maalaus" }
  case object OtherDamage extends TypeOfDamage { def value = 4; def description = "Muu vauiro" }
  case object Unknown extends TypeOfDamage { def value = 999; def description = "Ei tiedossa" }
}
