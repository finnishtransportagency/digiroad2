package fi.liikennevirasto.digiroad2

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
  def group: TrafficSignTypeGroup
  
  val value: Int

  val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq.empty[AdditionalPanelsType]

  def allowed(additionalPanelsType: AdditionalPanelsType) : Boolean = {
    supportedAdditionalPanel.contains(additionalPanelsType)
  }

  def source = Seq("CSVimport", "TRimport")
}
object TrafficSignType {
  val values = Set(PriorityRoad, EndOfPriority, PriorityOverOncomingTraffic, PriorityForOncomingTraffic, GiveWay, Stop, SpeedLimitSign, EndSpeedLimit, SpeedLimitZone, EndSpeedLimitZone, UrbanArea, EndUrbanArea,
    TelematicSpeedLimit, PedestrianCrossingSign, InfartsparkeringParkAndRide, BusLane, BusLaneEnds, TramLane, BusStopForLocalTraffic, TramStop, TaxiStation, ParkingLot,
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
    LocationSignForTouristService, FirstAid, FillingStation, Restaurant, PublicLavatory)


  def applyvalue(intValue: Int): TrafficSignType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  def apply(trafficSignTypeGroup: TrafficSignTypeGroup): Set[Int] = {
    values.filter(_.group == trafficSignTypeGroup).map(_.value)
  }

  case object Unknown extends TrafficSignType {
    override def group: TrafficSignTypeGroup = TrafficSignTypeGroup.PriorityAndGiveWaySigns

  
    override val value = 999

    override def source = Seq()
  }

}
trait PriorityAndGiveWaySigns extends TrafficSignType {
  override def group: TrafficSignTypeGroup = TrafficSignTypeGroup.PriorityAndGiveWaySigns
}

case object PriorityRoad extends PriorityAndGiveWaySigns {


  override val value = 211

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(DirectionOfPriorityRoad)

}

case object EndOfPriority extends PriorityAndGiveWaySigns {


  override val value = 212
}

case object PriorityOverOncomingTraffic extends PriorityAndGiveWaySigns {


  override val value = 221
}
case object PriorityForOncomingTraffic extends PriorityAndGiveWaySigns {


  override val value = 222

}
case object GiveWay extends PriorityAndGiveWaySigns {


  override val value = 231

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(DistanceCompulsoryStop, DirectionOfPriorityRoad)
}
case object Stop extends PriorityAndGiveWaySigns {


  override val value = 232

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(DistanceCompulsoryStop, DirectionOfPriorityRoad)

}

trait SpeedLimitsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.SpeedLimits
}

case object SpeedLimitSign extends SpeedLimitsType {

  override val value = 361
}

case object EndSpeedLimit extends SpeedLimitsType {

  override val value = 362
}

case object SpeedLimitZone extends SpeedLimitsType {

  override val value = 363
}

case object EndSpeedLimitZone extends SpeedLimitsType {

  override val value = 364
}

case object UrbanArea extends SpeedLimitsType {

  override val value = 571
}

case object EndUrbanArea extends SpeedLimitsType {

  override val value = 572
}

case object TelematicSpeedLimit extends SpeedLimitsType {

  override val value = 0

  override def source = Seq()
}


trait RegulatorySignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.RegulatorySigns
}

case object PedestrianCrossingSign extends RegulatorySignsType {

  override val value = 511
}


case object InfartsparkeringParkAndRide extends RegulatorySignsType { //TODO check that name

  override val value = 520

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}


case object BusLane extends RegulatorySignsType {

  override val value = 541

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusLaneEnds extends RegulatorySignsType {

  override val value = 542

}

case object TramLane extends RegulatorySignsType {

  override val value = 543

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusStopForLocalTraffic extends RegulatorySignsType {

  override val value = 531

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)

}

case object TramStop extends RegulatorySignsType {

  override val value = 533

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object TaxiStation extends RegulatorySignsType {

  override val value = 534

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object ParkingLot extends RegulatorySignsType {

  override val value = 521

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign,ParkingAgainstFee,	ObligatoryUseOfParkingDisc)
}

case object OneWayRoad extends RegulatorySignsType {

  override val value = 551
}

case object MotorwaySign extends RegulatorySignsType {

  override val value = 561
}

case object MotorwayEnds extends RegulatorySignsType {

  override val value = 562
}

case object ResidentialZone extends RegulatorySignsType {

  override val value = 573
}

case object EndOfResidentialZone extends RegulatorySignsType {

  override val value = 574
}

case object PedestrianZoneSign extends RegulatorySignsType {

  override val value = 575
}

case object EndOfPedestrianZone extends RegulatorySignsType {

  override val value = 576
}

case object BusStopForLongDistanceTraffic extends RegulatorySignsType {

  override val value = 532

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat)
}

trait MaximumRestrictionsType extends TrafficSignType{
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.MaximumRestrictions
}

case object MaximumLength extends MaximumRestrictionsType {

  override val value = 343
}

case object NoWidthExceeding extends MaximumRestrictionsType {

  override val value = 341

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)

}

case object MaxHeightExceeding extends MaximumRestrictionsType {

  override val value = 342
}

case object MaxLadenExceeding extends MaximumRestrictionsType {

  override val value = 344
}

case object MaxMassCombineVehiclesExceeding extends MaximumRestrictionsType {

  override val value = 345
}

case object MaxTonsOneAxleExceeding extends MaximumRestrictionsType {

  override val value = 346

}

case object MaxTonsOnBogieExceeding extends MaximumRestrictionsType {

  override val value = 347
}

trait GeneralWarningSignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.GeneralWarningSigns
}

case object Warning extends GeneralWarningSignsType {

  override val value = 189

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(FreeHeight)

}

case object WRightBend extends GeneralWarningSignsType {

  override val value = 111
}

case object WLeftBend extends GeneralWarningSignsType {

  override val value = 112
}

case object WSeveralBendsRight extends GeneralWarningSignsType {

  override val value = 113
}

case object WSeveralBendsLeft extends GeneralWarningSignsType {

  override val value = 114
}

case object WDangerousDescent extends GeneralWarningSignsType {

  override val value = 115
}

case object WSteepAscent extends GeneralWarningSignsType {

  override val value = 116
}

case object WUnevenRoad extends GeneralWarningSignsType {

  override val value = 141
}

case object WChildren extends GeneralWarningSignsType {

  override val value = 152
}

case object RoadNarrows extends GeneralWarningSignsType {

  override val value = 121

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(FreeWidth)

}

case object TwoWayTraffic extends GeneralWarningSignsType {

  override val value = 122
}

case object SwingBridge extends GeneralWarningSignsType {

  override val value = 131
}

case object RoadWorks extends GeneralWarningSignsType {

  override val value = 142
}

case object SlipperyRoad extends GeneralWarningSignsType {

  override val value = 144
}

case object PedestrianCrossingWarningSign extends GeneralWarningSignsType {

  override val value = 151
}

case object Cyclists extends GeneralWarningSignsType {

  override val value = 153
}

case object IntersectionWithEqualRoads extends GeneralWarningSignsType {

  override val value = 161
}

case object IntersectionWithMinorRoad extends GeneralWarningSignsType {

  override val value = 162

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithOneMinorRoad extends GeneralWarningSignsType {

  override val value = 163

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithOneCrossMinorRoad extends GeneralWarningSignsType {

  override val value = 164

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object LightSignals extends GeneralWarningSignsType {

  override val value = 165
}

case object TramwayLine extends GeneralWarningSignsType {

  override val value = 167
}

case object FallingRocks extends GeneralWarningSignsType {

  override val value = 181
}

case object CrossWind extends GeneralWarningSignsType {

  override val value = 183
}

case object LevelCrossingWithoutGate extends GeneralWarningSignsType {

  override val value = 171

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithGates extends GeneralWarningSignsType {

  override val value = 172

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithOneTrack extends GeneralWarningSignsType {

  override val value = 176

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object LevelCrossingWithManyTracks extends GeneralWarningSignsType {

  override val value = 177

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
}

case object Moose extends GeneralWarningSignsType {

  override val value = 155
}

case object Reindeer extends GeneralWarningSignsType {

  override val value = 156
}


trait ProhibitionsAndRestrictionsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.ProhibitionsAndRestrictions
}

case object NoLeftTurn extends ProhibitionsAndRestrictionsType {

  override val value = 332
}

case object NoRightTurn extends ProhibitionsAndRestrictionsType {

  override val value = 333
}

case object NoUTurn extends ProhibitionsAndRestrictionsType {

  override val value = 334
}

case object ClosedToAllVehicles extends ProhibitionsAndRestrictionsType {

  override val value = 311
}

case object NoPowerDrivenVehicles extends ProhibitionsAndRestrictionsType {

  override val value = 312
}

case object NoLorriesAndVans extends ProhibitionsAndRestrictionsType {

  override val value = 313
}

case object NoVehicleCombinations extends ProhibitionsAndRestrictionsType {

  override val value = 314
}

case object NoAgriculturalVehicles extends ProhibitionsAndRestrictionsType {

  override val value = 315
}

case object NoMotorCycles extends ProhibitionsAndRestrictionsType {

  override val value = 316
}

case object NoMotorSledges extends ProhibitionsAndRestrictionsType {

  override val value = 317
}

case object NoVehiclesWithDangerGoods extends ProhibitionsAndRestrictionsType {

  override val value = 318
}

case object NoBuses extends ProhibitionsAndRestrictionsType {

  override val value = 319
}

case object NoMopeds extends ProhibitionsAndRestrictionsType {

  override val value = 321
}

case object NoCyclesOrMopeds extends ProhibitionsAndRestrictionsType {

  override val value = 322
}

case object NoPedestrians extends ProhibitionsAndRestrictionsType {

  override val value = 323
}

case object NoPedestriansCyclesMopeds extends ProhibitionsAndRestrictionsType {

  override val value = 324
}

case object NoRidersOnHorseback extends ProhibitionsAndRestrictionsType {

  override val value = 325
}

case object NoEntry extends ProhibitionsAndRestrictionsType {

  override val value = 331
}

case object OvertakingProhibited extends ProhibitionsAndRestrictionsType {

  override val value = 351
}

case object EndProhibitionOfOvertaking extends ProhibitionsAndRestrictionsType {

  override val value = 352
}

case object TaxiStationZoneBeginning extends ProhibitionsAndRestrictionsType {

  override val value = 375
}

case object StandingPlaceForTaxi extends ProhibitionsAndRestrictionsType {

  override val value = 376
}

case object StandingAndParkingProhibited extends ProhibitionsAndRestrictionsType {

  override val value = 371

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingProhibited extends ProhibitionsAndRestrictionsType {


  override val value = 372

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign, ParkingAgainstFee,  ObligatoryUseOfParkingDisc)
}

case object ParkingProhibitedZone extends ProhibitionsAndRestrictionsType {

  override val value = 373
}

case object EndOfParkingProhibitedZone extends ProhibitionsAndRestrictionsType {

  override val value = 374
}

case object AlternativeParkingOddDays extends ProhibitionsAndRestrictionsType {

  override val value = 381

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object AlternativeParkingEvenDays extends ProhibitionsAndRestrictionsType {

  override val value = 382

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}


trait MandatorySignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.MandatorySigns
}

case object CompulsoryFootPath extends MandatorySignsType {

  override val value = 421

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object CompulsoryCycleTrack extends MandatorySignsType {

  override val value = 422

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object CombinedCycleTrackAndFootPath extends MandatorySignsType {

  override val value = 423

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParallelCycleTrackAndFootPath extends MandatorySignsType {

  override val value = 424

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)

}

case object ParallelCycleTrackAndFootPath2 extends MandatorySignsType {

  override val value = 425

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object DirectionToBeFollowed3 extends MandatorySignsType {

  override val value = 413
}

case object DirectionToBeFollowed4 extends MandatorySignsType {

  override val value = 414
}

case object DirectionToBeFollowed5 extends MandatorySignsType {

  override val value = 415
}

case object CompulsoryRoundabout extends MandatorySignsType {

  override val value = 416
}

case object PassThisSide extends MandatorySignsType {

  override val value = 417
}

case object DividerOfTraffic extends MandatorySignsType {

  override val value = 418
}

case object CompulsoryTrackMotorSledges extends MandatorySignsType {

  override val value = 426

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object CompulsoryTrackRidersHorseback extends MandatorySignsType {

  override val value = 427

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign, ValidMonFri,	ValidSat,	ValidMultiplePeriod,
    ParkingAgainstFee,	ObligatoryUseOfParkingDisc)
}

trait AdditionalPanelsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.AdditionalPanels
}

case object FreeWidth extends AdditionalPanelsType {

  override val value = 821
}

case object FreeHeight extends AdditionalPanelsType {

  override val value = 822
}

case object HeightElectricLine extends AdditionalPanelsType {

  override val value = 823
}

case object SignAppliesBothDirections extends AdditionalPanelsType {

  override val value = 824
}

case object SignAppliesBothDirectionsVertical extends AdditionalPanelsType {

  override val value = 825
}


case object SignAppliesArrowDirections extends AdditionalPanelsType {

  override val value = 826
}

case object RegulationBeginsFromSign extends AdditionalPanelsType {

  override val value = 827
}

case object RegulationEndsToTheSign extends AdditionalPanelsType {

  override val value = 828
}

case object HazmatProhibitionA extends AdditionalPanelsType {

  override val value = 848
}

case object HazmatProhibitionB extends AdditionalPanelsType {

  override val value = 849
}

case object ValidMonFri extends AdditionalPanelsType {

  override val value = 851
}

case object ValidSat extends AdditionalPanelsType {

  override val value = 852
}

case object ValidMultiplePeriod extends AdditionalPanelsType {

  override val value = 853
}

case object TimeLimit extends AdditionalPanelsType {

  override val value = 854
}

case object DistanceCompulsoryStop
  extends AdditionalPanelsType {

  override val value = 816
}

case object DirectionOfPriorityRoad extends AdditionalPanelsType {

  override val value = 861
}

case object CrossingLogTransportRoad extends AdditionalPanelsType {

  override val value = 862
}

case object PassengerCar  extends AdditionalPanelsType {

  override val value = 831
}
case object Bus  extends AdditionalPanelsType {

  override val value = 832
}
case object Lorry  extends AdditionalPanelsType {

  override val value = 833
}
case object Van  extends AdditionalPanelsType {

  override val value = 834
}
case object VehicleForHandicapped  extends AdditionalPanelsType {

  override val value = 836
}
case object MotorCycle  extends AdditionalPanelsType {

  override val value = 841
}
case object Cycle  extends AdditionalPanelsType {

  override val value = 843
}
case object ParkingAgainstFee  extends AdditionalPanelsType {

  override val value = 855
}
case object ObligatoryUseOfParkingDisc  extends AdditionalPanelsType {

  override val value = 856
}
case object AdditionalPanelWithText  extends AdditionalPanelsType {

  override val value = 871
}
case object DrivingInServicePurposesAllowed  extends AdditionalPanelsType {

  override val value = 872
}

trait InformationSignsType extends TrafficSignType  {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.InformationSigns
}

case object NoThroughRoad extends InformationSignsType {

  override val value = 651
}
case object NoThroughRoadRight extends InformationSignsType {

  override val value = 652
}
case object SymbolOfMotorway extends InformationSignsType {

  override val value = 671
}
case object Parking  extends InformationSignsType {

  override val value = 677
}
case object ItineraryForIndicatedVehicleCategory extends InformationSignsType  {

  override val value = 681
}
case object ItineraryForPedestrians extends InformationSignsType {

  override val value = 682
}
case object ItineraryForHandicapped extends InformationSignsType {

  override val value = 683
}

trait ServiceSignsType extends TrafficSignType  {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.ServiceSigns
}

case object LocationSignForTouristService  extends ServiceSignsType {

  override val value = 704
}
case object FirstAid  extends ServiceSignsType {

  override val value = 715
}
case object FillingStation  extends ServiceSignsType {

  override val value = 722
}
case object Restaurant  extends ServiceSignsType {

  override val value = 724
}
case object PublicLavatory  extends ServiceSignsType {

  override val value = 726
}

