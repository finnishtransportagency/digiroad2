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
    LocationSignForTouristService, FirstAid, FillingStation, Restaurant, PublicLavatory, DistanceFromSignToPointWhichSignApplies, DistanceWhichSignApplies)

  def applyOTHValue(intValue: Int): TrafficSignType = {
    values.find(_.OTHvalue == intValue).getOrElse(Unknown)
  }

  def applyTRValue(intValue: Int): TrafficSignType = {
    values.find(_.TRvalue == intValue).getOrElse(Unknown)
  }

  def apply(TrafficSignTypeGroup: TrafficSignTypeGroup): Set[Int] = {
    values.filter(_.group == TrafficSignTypeGroup).map(_.OTHvalue)
  }

  case object Unknown extends TrafficSignType {
    override def group: TrafficSignTypeGroup = TrafficSignTypeGroup.Unknown

    override val OTHvalue = 999
    override val TRvalue = 99

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

  override val supportedAdditionalPanel  = Seq(DirectionOfPriorityRoad)

}

case object EndOfPriority extends PriorityAndGiveWaySigns {

  override val OTHvalue = 95
  override val TRvalue = 212
}

case object PriorityOverOncomingTraffic extends PriorityAndGiveWaySigns {

  override val OTHvalue = 96
  override val TRvalue = 221
}

case object PriorityForOncomingTraffic extends PriorityAndGiveWaySigns {

  override val OTHvalue = 97
  override val TRvalue = 222

}

case object GiveWay extends PriorityAndGiveWaySigns {

  override val OTHvalue = 98
  override val TRvalue = 231

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(DistanceCompulsoryStop, DirectionOfPriorityRoad)
}

case object Stop extends PriorityAndGiveWaySigns {

  override val OTHvalue = 99
  override val TRvalue = 232

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
}

case object EndSpeedLimit extends SpeedLimitsType {
  override val OTHvalue = 2
  override val TRvalue = 362
}

case object SpeedLimitZone extends SpeedLimitsType {
  override val OTHvalue = 3
  override val TRvalue = 363
}

case object EndSpeedLimitZone extends SpeedLimitsType {
  override val OTHvalue = 4
  override val TRvalue = 364
}

case object UrbanArea extends SpeedLimitsType {
  override val OTHvalue = 5
  override val TRvalue = 571
}

case object EndUrbanArea extends SpeedLimitsType {
  override val OTHvalue = 6
  override val TRvalue = 572
}

case object TelematicSpeedLimit extends SpeedLimitsType {
  override val OTHvalue = 44
  override val TRvalue = 0

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
}

case object ParkAndRide extends RegulatorySignsType { //TODO check that name
  override val OTHvalue = 137
  override val TRvalue = 520

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object BusLane extends RegulatorySignsType {
  override val OTHvalue = 63
  override val TRvalue = 541

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusLaneEnds extends RegulatorySignsType {
  override val OTHvalue = 64
  override val TRvalue = 542

}

case object TramLane extends RegulatorySignsType {
  override val OTHvalue = 65
  override val TRvalue = 543

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusStopForLocalTraffic extends RegulatorySignsType {
  override val OTHvalue = 66
  override val TRvalue = 531

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)

}

case object TramStop extends RegulatorySignsType {
  override val OTHvalue = 68
  override val TRvalue = 533

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object TaxiStation extends RegulatorySignsType {
  override val OTHvalue = 69
  override val TRvalue = 534

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object ParkingLot extends RegulatorySignsType {
  override val OTHvalue = 105
  override val TRvalue = 521

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign,ParkingAgainstFee,	ObligatoryUseOfParkingDisc)
}

case object OneWayRoad extends RegulatorySignsType {
  override val OTHvalue = 106
  override val TRvalue = 551
}

case object MotorwaySign extends RegulatorySignsType {
  override val OTHvalue = 107
  override val TRvalue = 561
}

case object MotorwayEnds extends RegulatorySignsType {
  override val OTHvalue = 108
  override val TRvalue = 562
}

case object ResidentialZone extends RegulatorySignsType {
  override val OTHvalue = 109
  override val TRvalue = 573
}

case object EndOfResidentialZone extends RegulatorySignsType {
  override val OTHvalue = 110
  override val TRvalue = 574
}

case object PedestrianZoneSign extends RegulatorySignsType {
  override val OTHvalue = 111
  override val TRvalue = 575
}

case object EndOfPedestrianZone extends RegulatorySignsType {
  override val OTHvalue = 112
  override val TRvalue = 576
}

case object BusStopForLongDistanceTraffic extends RegulatorySignsType {
  override val OTHvalue = 66
  override val TRvalue = 532

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
}

case object NoWidthExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 30
  override val TRvalue = 341

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)

}

case object MaxHeightExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 31
  override val TRvalue = 342
}

case object MaxLadenExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 32
  override val TRvalue = 344
}

case object MaxMassCombineVehiclesExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 33
  override val TRvalue = 345
}

case object MaxTonsOneAxleExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 34
  override val TRvalue = 346

}

case object MaxTonsOnBogieExceeding extends MaximumRestrictionsType {
  override val OTHvalue = 35
  override val TRvalue = 347
}

trait GeneralWarningSignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.GeneralWarningSigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object Warning extends GeneralWarningSignsType {
  override val OTHvalue = 9
  override val TRvalue = 189

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(FreeHeight)

}

case object WRightBend extends GeneralWarningSignsType {
  override val OTHvalue = 36
  override val TRvalue = 111
}

case object WLeftBend extends GeneralWarningSignsType {
  override val OTHvalue = 37
  override val TRvalue = 112
}

case object WSeveralBendsRight extends GeneralWarningSignsType {
  override val OTHvalue = 38
  override val TRvalue = 113
}

case object WSeveralBendsLeft extends GeneralWarningSignsType {
  override val OTHvalue = 39
  override val TRvalue = 114
}

case object WDangerousDescent extends GeneralWarningSignsType {
  override val OTHvalue = 40
  override val TRvalue = 115
}

case object WSteepAscent extends GeneralWarningSignsType {
  override val OTHvalue = 41
  override val TRvalue = 116
}

case object WUnevenRoad extends GeneralWarningSignsType {
  override val OTHvalue = 42
  override val TRvalue = 141
}

case object WChildren extends GeneralWarningSignsType {
  override val OTHvalue = 43
  override val TRvalue = 152
}

case object RoadNarrows extends GeneralWarningSignsType {
  override val OTHvalue = 82
  override val TRvalue = 121

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(FreeWidth)

}

case object TwoWayTraffic extends GeneralWarningSignsType {
  override val OTHvalue = 83
  override val TRvalue = 122
}

case object SwingBridge extends GeneralWarningSignsType {
  override val OTHvalue = 84
  override val TRvalue = 131
}

case object RoadWorks extends GeneralWarningSignsType {
  override val OTHvalue = 85
  override val TRvalue = 142
}

case object SlipperyRoad extends GeneralWarningSignsType {
  override val OTHvalue = 86
  override val TRvalue = 144
}

case object PedestrianCrossingWarningSign extends GeneralWarningSignsType {
  override val OTHvalue = 87
  override val TRvalue = 151
}

case object Cyclists extends GeneralWarningSignsType {
  override val OTHvalue = 88
  override val TRvalue = 153
}

case object IntersectionWithEqualRoads extends GeneralWarningSignsType {
  override val OTHvalue = 89
  override val TRvalue = 161
}

case object IntersectionWithMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 127
  override val TRvalue = 162

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithOneMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 128
  override val TRvalue = 163

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithOneCrossMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 129
  override val TRvalue = 164

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object LightSignals extends GeneralWarningSignsType {
  override val OTHvalue = 90
  override val TRvalue = 165
}

case object TramwayLine extends GeneralWarningSignsType {
  override val OTHvalue = 91
  override val TRvalue = 167
}

case object FallingRocks extends GeneralWarningSignsType {
  override val OTHvalue = 92
  override val TRvalue = 181
}

case object CrossWind extends GeneralWarningSignsType {
  override val OTHvalue = 93
  override val TRvalue = 183
}

case object LevelCrossingWithoutGate extends GeneralWarningSignsType {
  override val OTHvalue = 130
  override val TRvalue = 171

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithGates extends GeneralWarningSignsType {
  override val OTHvalue = 131
  override val TRvalue = 172

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithOneTrack extends GeneralWarningSignsType {
  override val OTHvalue = 132
  override val TRvalue = 176

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithManyTracks extends GeneralWarningSignsType {
  override val OTHvalue = 133
  override val TRvalue = 177

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object Moose extends GeneralWarningSignsType {
  override val OTHvalue = 125
  override val TRvalue = 155
}

case object Reindeer extends GeneralWarningSignsType {
  override val OTHvalue = 126
  override val TRvalue = 156
}

trait ProhibitionsAndRestrictionsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.ProhibitionsAndRestrictions

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object NoLeftTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 10
  override val TRvalue = 332
}

case object NoRightTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 11
  override val TRvalue = 333
}

case object NoUTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 12
  override val TRvalue = 334
}

case object ClosedToAllVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 13
  override val TRvalue = 311
}

case object NoPowerDrivenVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 14
  override val TRvalue = 312
}

case object NoLorriesAndVans extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 15
  override val TRvalue = 313
}

case object NoVehicleCombinations extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 16
  override val TRvalue = 314
}

case object NoAgriculturalVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 17
  override val TRvalue = 315
}

case object NoMotorCycles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 18
  override val TRvalue = 316
}

case object NoMotorSledges extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 19
  override val TRvalue = 317
}

case object NoVehiclesWithDangerGoods extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 20
  override val TRvalue = 318
}

case object NoBuses extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 21
  override val TRvalue = 319
}

case object NoMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 22
  override val TRvalue = 321
}

case object NoCyclesOrMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 23
  override val TRvalue = 322
}

case object NoPedestrians extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 24
  override val TRvalue = 323
}

case object NoPedestriansCyclesMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 25
  override val TRvalue = 324
}

case object NoRidersOnHorseback extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 26
  override val TRvalue = 325
}

case object NoEntry extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 27
  override val TRvalue = 331
}

case object OvertakingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 28
  override val TRvalue = 351
}

case object EndProhibitionOfOvertaking extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 29
  override val TRvalue = 352
}

case object TaxiStationZoneBeginning extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 80
  override val TRvalue = 375
}

case object StandingPlaceForTaxi extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 81
  override val TRvalue = 376
}

case object StandingAndParkingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 100
  override val TRvalue = 371

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 101
  override val TRvalue = 372

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign, ParkingAgainstFee,  ObligatoryUseOfParkingDisc)
}

case object ParkingProhibitedZone extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 102
  override val TRvalue = 373
}

case object EndOfParkingProhibitedZone extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 103
  override val TRvalue = 374
}

case object AlternativeParkingOddDays extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 104
  override val TRvalue = 381

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object AlternativeParkingEvenDays extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 134
  override val TRvalue = 382

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

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object CompulsoryCycleTrack extends MandatorySignsType {
  override val OTHvalue = 71
  override val TRvalue = 422

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object CombinedCycleTrackAndFootPath extends MandatorySignsType {
  override val OTHvalue = 72
  override val TRvalue = 423

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParallelCycleTrackAndFootPath extends MandatorySignsType {
  override val OTHvalue = 72
  override val TRvalue = 424

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object ParallelCycleTrackAndFootPath2 extends MandatorySignsType {
  override val OTHvalue = 72
  override val TRvalue = 425

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object DirectionToBeFollowed3 extends MandatorySignsType {
  override val OTHvalue = 74
  override val TRvalue = 413
}

case object DirectionToBeFollowed4 extends MandatorySignsType {
  override val OTHvalue = 74
  override val TRvalue = 414
}

case object DirectionToBeFollowed5 extends MandatorySignsType {
  override val OTHvalue = 74
  override val TRvalue = 415
}

case object CompulsoryRoundabout extends MandatorySignsType {
  override val OTHvalue = 77
  override val TRvalue = 416
}

case object PassThisSide extends MandatorySignsType {
  override val OTHvalue = 78
  override val TRvalue = 417
}

case object DividerOfTraffic extends MandatorySignsType {
  override val OTHvalue = 78
  override val TRvalue = 418
}

case object CompulsoryTrackMotorSledges extends MandatorySignsType {
  override val OTHvalue = 135
  override val TRvalue = 426

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object CompulsoryTrackRidersHorseback extends MandatorySignsType {
  override val OTHvalue = 136
  override val TRvalue = 427

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
}

case object NoThroughRoadRight extends InformationSignsType {
  override val OTHvalue = 114
  override val TRvalue = 652
}

case object SymbolOfMotorway extends InformationSignsType {
  override val OTHvalue = 115
  override val TRvalue = 671
}

case object Parking  extends InformationSignsType {
  override val OTHvalue = 116
  override val TRvalue = 677
}

case object ItineraryForIndicatedVehicleCategory extends InformationSignsType  {
  override val OTHvalue = 117
  override val TRvalue = 681
}

case object ItineraryForPedestrians extends InformationSignsType {
  override val OTHvalue = 118
  override val TRvalue = 682
}

case object ItineraryForHandicapped extends InformationSignsType {
  override val OTHvalue = 119
  override val TRvalue = 683
}


trait ServiceSignsType extends TrafficSignType  {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.ServiceSigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object LocationSignForTouristService  extends ServiceSignsType {
  override val OTHvalue = 120
  override val TRvalue = 704
}

case object FirstAid  extends ServiceSignsType {
  override val OTHvalue = 121
  override val TRvalue = 715
}

case object FillingStation  extends ServiceSignsType {
  override val OTHvalue = 122
  override val TRvalue = 722
}

case object Restaurant  extends ServiceSignsType {
  override val OTHvalue = 123
  override val TRvalue = 724
}

case object PublicLavatory  extends ServiceSignsType {
  override val OTHvalue = 124
  override val TRvalue = 726
}


trait AdditionalPanelsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.AdditionalPanels
}

case object FreeWidth extends AdditionalPanelsType {
  override val OTHvalue = 45
  override val TRvalue = 821
}

case object FreeHeight extends AdditionalPanelsType {
  override val OTHvalue = 46
  override val TRvalue = 822
}

case object HeightElectricLine extends AdditionalPanelsType {
  override val OTHvalue = 139
  override val TRvalue = 823
}

case object SignAppliesBothDirections extends AdditionalPanelsType {
  override val OTHvalue = 140
  override val TRvalue = 824
}

case object SignAppliesBothDirectionsVertical extends AdditionalPanelsType {
  override val OTHvalue = 141
  override val TRvalue = 825
}

case object SignAppliesArrowDirections extends AdditionalPanelsType {
  override val OTHvalue = 142
  override val TRvalue = 826
}

case object RegulationBeginsFromSign extends AdditionalPanelsType {
  override val OTHvalue = 143
  override val TRvalue = 827
}

case object RegulationEndsToTheSign extends AdditionalPanelsType {
  override val OTHvalue = 144
  override val TRvalue = 828
}

case object HazmatProhibitionA extends AdditionalPanelsType {
  override val OTHvalue = 47
  override val TRvalue = 848
}

case object HazmatProhibitionB extends AdditionalPanelsType {
  override val OTHvalue = 48
  override val TRvalue = 849
}

case object ValidMonFri extends AdditionalPanelsType {
  override val OTHvalue = 49
  override val TRvalue = 851
}

case object ValidSat extends AdditionalPanelsType {
  override val OTHvalue = 50
  override val TRvalue = 852
}

case object ValidMultiplePeriod extends AdditionalPanelsType {
  override val OTHvalue = 145
  override val TRvalue = 853
}

case object TimeLimit extends AdditionalPanelsType {
  override val OTHvalue = 51
  override val TRvalue = 854
}

case object DistanceCompulsoryStop extends AdditionalPanelsType {
  override val OTHvalue = 138
  override val TRvalue = 816
}

case object DirectionOfPriorityRoad extends AdditionalPanelsType {
  override val OTHvalue = 146
  override val TRvalue = 861
}

case object CrossingLogTransportRoad extends AdditionalPanelsType {
  override val OTHvalue = 147
  override val TRvalue = 862
}

case object PassengerCar  extends AdditionalPanelsType {
  override val OTHvalue = 52
  override val TRvalue = 831
}

case object Bus  extends AdditionalPanelsType {
  override val OTHvalue = 53
  override val TRvalue = 832
}

case object Lorry  extends AdditionalPanelsType {
  override val OTHvalue = 54
  override val TRvalue = 833
}

case object Van  extends AdditionalPanelsType {
  override val OTHvalue = 55
  override val TRvalue = 834
}

case object VehicleForHandicapped  extends AdditionalPanelsType {
  override val OTHvalue = 56
  override val TRvalue = 836
}

case object MotorCycle  extends AdditionalPanelsType {
  override val OTHvalue = 57
  override val TRvalue = 841
}

case object Cycle  extends AdditionalPanelsType {
  override val OTHvalue = 58
  override val TRvalue = 843
}

case object ParkingAgainstFee  extends AdditionalPanelsType {
  override val OTHvalue = 59
  override val TRvalue = 855
}

case object ObligatoryUseOfParkingDisc  extends AdditionalPanelsType {
  override val OTHvalue = 60
  override val TRvalue = 856
}

case object AdditionalPanelWithText  extends AdditionalPanelsType {
  override val OTHvalue = 61
  override val TRvalue = 871
}

case object DrivingInServicePurposesAllowed  extends AdditionalPanelsType {
  override val OTHvalue = 62
  override val TRvalue = 872
}

case object DistanceFromSignToPointWhichSignApplies extends AdditionalPanelsType {
  override val OTHvalue = 148
  override val TRvalue = 814
}

case object DistanceWhichSignApplies extends AdditionalPanelsType {
  override val OTHvalue = 149
  override val TRvalue = 815
}

case object HusvagnCaravan extends AdditionalPanelsType {
  override val OTHvalue = 150
  override val TRvalue = 835
}

case object Moped extends AdditionalPanelsType {
  override val OTHvalue = 151
  override val TRvalue = 842
}

