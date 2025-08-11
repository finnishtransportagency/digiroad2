package fi.liikennevirasto.digiroad2

sealed trait TrafficSignTypeGroup{
  def value: Int
}
object TrafficSignTypeGroup{
  val values = Set(Unknown, SpeedLimits, RegulatorySigns, MaximumRestrictions, GeneralWarningSigns, ProhibitionsAndRestrictions, AdditionalPanels, MandatorySigns,
    PriorityAndGiveWaySigns, InformationSigns, CycleAndWalkwaySigns, OtherSigns)

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
  case object CycleAndWalkwaySigns extends TrafficSignTypeGroup{ def value = 11 }
  case object OtherSigns extends TrafficSignTypeGroup{ def value = 12 }
  case object Unknown extends TrafficSignTypeGroup{ def value = 99 }
}

sealed trait TrafficSignType {

  val values = Seq()

  def group: TrafficSignTypeGroup

  //This is only used to put CycleAndWalkwaySigns group at the moment
  def additionalGroup: Option[TrafficSignTypeGroup] = None

  val OTHvalue: Int

  val TRvalue: Int

  val OldLawCode: Option[Int]

  val NewLawCode: String

  val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq.empty[AdditionalPanelsType]

  def relevantAdditionalPanel = Seq.empty[AdditionalPanelsType]

  def allowed(additionalPanelsType: AdditionalPanelsType) : Boolean = {
    relevantAdditionalPanel.contains(additionalPanelsType)
  }

  def isSpeedLimit: Boolean = false
  def source = Seq("CSVimport", "TRimport")
}

object TrafficSignType {
  val values = Set(PriorityRoad, EndOfPriority, PriorityOverOncomingTraffic, PriorityForOncomingTraffic, GiveWay, Stop, SpeedLimitSign, EndSpeedLimit, SpeedLimitZone, EndSpeedLimitZone, UrbanArea, EndUrbanArea,
    TelematicSpeedLimit, PedestrianCrossingSign, BusLane, BusLaneEnds, TramLane, BusStopForLocalTraffic, TramStop, TaxiStation, ParkingLot,
    OneWayRoad, MotorwaySign, MotorwayEnds, ResidentialZone, EndOfResidentialZone, PedestrianZoneSign, EndOfPedestrianZone, BusStopForLongDistanceTraffic, MaximumLength, NoWidthExceeding,
    MaxHeightExceeding, MaxLadenExceeding, MaxMassCombineVehiclesExceeding, MaxTonsOneAxleExceeding, MaxTonsOnBogieExceeding, Warning, WRightBend, WLeftBend, WSeveralBendsRight,
    WSeveralBendsLeft, WDangerousDescent, WSteepAscent, WUnevenRoad, WChildren, RoadNarrows, TwoWayTraffic, SwingBridge, RoadWorks, SlipperyRoad, PedestrianCrossingWarningSign,
    Cyclists, IntersectionWithEqualRoads, IntersectionWithMinorRoad, IntersectionWithOneMinorRoad, IntersectionWithOneCrossMinorRoad, LightSignals, TramwayLine, FallingRocks, CrossWind,
    LevelCrossingWithoutGate, LevelCrossingWithGates, LevelCrossingWithOneTrack, LevelCrossingWithManyTracks, Moose, Reindeer, NoLeftTurn, NoRightTurn, NoUTurn, ClosedToAllVehicles,
    NoPowerDrivenVehicles, NoLorriesAndVans, NoVehicleCombinations, NoAgriculturalVehicles, NoMotorCycles, NoMotorSledges, NoVehiclesWithDangerGoods, NoBuses, NoMopeds, NoCyclesOrMopeds,
    NoPedestrians, NoPedestriansCyclesMopeds, NoRidersOnHorseback, NoEntry, OvertakingProhibited, EndProhibitionOfOvertaking, TaxiStationZoneBeginning, StandingPlaceForTaxi, StandingAndParkingProhibited,
    ParkingProhibited, ParkingProhibitedZone, EndOfParkingProhibitedZone, AlternativeParkingOddDays, AlternativeParkingEvenDays, CompulsoryFootPath, CompulsoryCycleTrack, CombinedCycleTrackAndFootPath,
    ParallelCycleTrackAndFootPath, ParallelCycleTrackAndFootPath2, CompulsoryRoundabout, DividerOfTraffic,
    CompulsoryTrackMotorSledges, CompulsoryTrackRidersHorseback, FreeWidth, FreeHeight, HeightElectricLine, SignAppliesBothDirections, SignAppliesBothDirectionsVertical, SignAppliesArrowDirections,
    RegulationBeginsFromSign, RegulationEndsToTheSign, HazmatProhibitionA, HazmatProhibitionB, ValidMonFri, ValidSat, ValidMultiplePeriod, TimeLimit, DistanceCompulsoryStop, DirectionOfPriorityRoad,
    CrossingLogTransportRoad, PassengerCar, Bus, Lorry, Van, VehicleForHandicapped, MotorCycle, Cycle, ParkingAgainstFee, ObligatoryUseOfParkingDisc, AdditionalPanelWithText,
    DrivingInServicePurposesAllowed, NoThroughRoad, NoThroughRoadRight, SymbolOfMotorway, Parking, ItineraryForIndicatedVehicleCategory, ItineraryForPedestrians, AccessibleRoute,
    LocationSignForTouristService, FirstAid, FillingStation, Restaurant, PublicLavatory, DistanceFromSignToPointWhichSignApplies, DistanceWhichSignApplies,
    AdvanceDirectionSign, AdvanceDirectionSignSmall, AdvisorySignDetour, AdvisorySignDetourLarge, Detour, RouteToBeFollowed, InformationOnTrafficLanes, BiDirectionalInformationOnTrafficLanes,
    EndOfLane, AdvanceDirectionSignAbove, ExitSignAbove, DirectionSign, ExitSign, DirectionSignOnPrivateRoad, LocationSign, DirectionSignForDetourWithText, DirectionSignForDetour,
    DirectionSignForLocalPurposes, DirectionSignForMotorway, RecommendedMaxSpeed, SignShowingDistance, PlaceName, DirectionToTheNumberedRoad, RoadNumberPrimaryRoad,
    RoadForMotorVehicles, Airport, Ferry, GoodsHarbour, IndustrialArea, RailwayStation, BusStation, ItineraryForDangerousGoodsTransport,
    AdvanceDirectionSignAboveSmall, HusvagnCaravan, Moped,
    PriorityForCyclistsCrossing, ParkingLotAndAccessToTrain, ParkingLotAndAccessToBus, ParkingLotAndAccessToTram, ParkingLotAndAccessToSubway,
    ParkingLotAndAccessToPublicTransport, ParkingDirectly, ParkingOppositeEachOther, PositioningAtAnAngle, RoadDirection,
    BusAndTaxiLane, BusAndTaxiLaneEnds, TramAndTaxiLane, TramLaneEnds, TramAndTaxiLaneEnds, BicycleLaneOnTheRight, BicycleLaneInTheMiddle,
    OneWayRoadLeftRight, ExpresswaySign, ExpresswayEnds, TunnelSign, TunnelEnds, SOSZoneSign, BicycleStreet, BicycleStreetEnds,
    LaneMerge, EndInPierOrCliff, TrafficJam, Bumps,LooseStones, DangerousRoadSide, Pedestrians, WCrossCountrySkiing, WildAnimals,
    IntersectionWithTwoMinorRoads, Roundabout, ApproachLevelCrossingThreeStrips, ApproachLevelCrossingTwoStrips, ApproachLevelCrossingOneStrip,
    LowFlyingPlanes, NoCyclists, NoCyclistsOrPedestrians,OvertakingProhibitedByTruck, EndProhibitionOfOvertakingByTruck,
    ProhibitionOrRegulationPerLane, LoadingPlace, CustomsControl, MandatoryStopForInspection, MinimumDistanceBetweenVehicles,
    NoStuddedTires, RightDirection, LeftDirection, StraightDirection, TurnRight, TurnLeft, StraightDirectionOrRightTurn,
    StraightDirectionOrLeftTurn, LeftTurnOrRightTurn, StraightDirectionOrRightOrLeftTurn, PassRightSide, PassLeftSide,
    MinimumSpeed, MinimumSpeedEnds, AdvanceDirectionSign2, AdvanceDirectionSign3, AdvanceDirectionSignSmall2, AdvanceDirectionSignSmall3,
    LaneSpecificNavigationBoard, TrafficLanesWithSeparator, IncreasedLaneNumber, NewLaneIncoming, NewLaneIncoming2, EndOfLane,
    EndOfLane2, CompilationSign, DirectionSignForDetour2, AdvanceLocationSign, AccessParkingAndTrainSign, AccessParkingAndBusSign,
    AccessParkingAndTramSign, AccessParkingAndSubwaySign, AccessParkingAndPublicTransportsSign, DirectionSignForPedestrians,
    DirectionSignForCyclistsWithoutDistances, DirectionSignForCyclistsWithDistances, AdvanceDirectionSignForCyclistsWithDistances, AdvanceDirectionSignForCyclistsWithoutDistances,
    DistanceBoardForCyclists, PlaceNameForCyclists, NoThroughRoadCyclist, PlaceName2, PlaceName3, RiverName, RoadNumberInternationalRoad,
    RoadNumberHighway, RoadNumberRegionalRoad, RoadNumberOtherRoad, RoadNumberRingRoad, ExitNumber, DirectionToTheNumberedPrimaryRoad,
    Boat, CargoTerminal, LargeRetailUnit, ParkingCovered, Center, TruckRoute, PassengerCarRoute, BusRoute, VanRoute, MotorcycleRoute,
    MopedRoute, TractorRoute, MotorHomeRoute, BicycleRoute, UnderpassWithSteps,  OverpassWithSteps, UnderpassWithoutSteps,
    OverpassWithoutSteps, UnderpassForWheelchair, OverpassForWheelchair, EmergencyExitOnTheLeft, EmergencyExitOnTheRight, SingleExitRoute,
    MultipleExitRoute, InformationSignForServices, InformationSignForServices2, AdvanceInformationSignForServices, AdvanceLocationSignForTouristService,
    RadioStationFrequency, InformationPoint, InformationCentre, BreakdownService, CompressedNaturalGasStation, ChargingStation, HydrogenFillingStation,
    HotelOrMotel, CafeteriaOrRefreshments, Hostel, CampingSite, CaravanSite, PicnicSite, OutingSite, EmergencyPhone, Extinguisher,
    MuseumOrHistoricBuilding, WorldHeritageSite, NatureSite, Viewpoint, Zoo, OtherTouristAttraction, SwimmingPlace, FishingPlace,
    SkiLift, CrossCountrySkiing, GolfCourse, PleasureOrThemePark, CottageAccommodation, BedAndBreakfast, DirectSale, Handicrafts,
    FarmPark, HorsebackRiding, TouristRouteTextOnly, TouristRoute, TemporaryGuidanceSign, SignAppliesToCrossingRoad, SignAppliesDirectionOfTheArrow,
    SignAppliesDirectionOfTheArrowWithDistance, SignAppliesDirectionOfTheArrowWithDistance2, Motorhome, MotorSledges, Tractor,
    LowEmissionVehicle, ParkingOnTopOfCurb, ParkingOnTheEdgeOfTheCurb, TunnelCategory, ObligatoryUseOfParkingDisc2, ParkingAgainstFee2,
    ChargingSite, DirectionOfPriorityRoad2, DirectionOfPriorityRoad3, TwoWayBikePath, TwoWayBikePath2, EmergencyPhoneAndExtinguisher,
    DirectionToAvoidObstacle, CurveDirectionSign, BorderMarkOnTheLeft, BorderMarkOnTheRight, HeightBorder, UnderpassHeight, TrafficSignColumn,
    TrafficSignColumn2, DivergingRoadSign, TowAwayZone, SOSInformationBoard, AutomaticTrafficControl, SurveillanceCamera, ReindeerHerdingArea,
    ReindeerHerdingAreaWithoutText, SpeedLimitInformation, CountryBorder
  )

  def applyOTHValue(intValue: Int): TrafficSignType = {
    values.find(_.OTHvalue == intValue).getOrElse(Unknown)
  }

  def applyTRValue(intValue: Int): TrafficSignType = {
    values.find(_.TRvalue == intValue).getOrElse(Unknown)
  }

  def applyNewLawCode(value: String): TrafficSignType = {
    values.filter(_.NewLawCode == value)
          .toSeq                // To be able to do the sortBy
          .sortBy(_.OTHvalue)   // Sort the elements by OTHvalue ASC
          .headOption           // To get first element of the sortBy inside an Option
          .getOrElse(Unknown)   // If previous result is empty then we return Unknown otherwise we return the first element of the sortBy
  }

  def apply(TrafficSignTypeGroup: TrafficSignTypeGroup): Set[Int] = {
    values.filter(_.group == TrafficSignTypeGroup).map(_.OTHvalue)
  }

  def applyAdditionalGroup(TrafficSignTypeGroup: TrafficSignTypeGroup): Set[String] = {
    values.filter(_.additionalGroup.contains(TrafficSignTypeGroup)).map(_.NewLawCode)
  }

  case object Unknown extends TrafficSignType {
    override def group: TrafficSignTypeGroup = TrafficSignTypeGroup.Unknown

    override val OTHvalue = 999
    override val TRvalue = 99
    override val OldLawCode: Option[Int] = Some(99)
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
  override val TRvalue = 21 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(211)
  override val NewLawCode = "B1"

  override val supportedAdditionalPanel  = Seq(DirectionOfPriorityRoad)
}

case object EndOfPriority extends PriorityAndGiveWaySigns {
  override val OTHvalue = 95
  override val TRvalue = 22 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(212)
  override val NewLawCode = "B2"
}

case object PriorityOverOncomingTraffic extends PriorityAndGiveWaySigns {
  override val OTHvalue = 96
  override val TRvalue = 23 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(221)
  override val NewLawCode = "B3"
}

case object PriorityForOncomingTraffic extends PriorityAndGiveWaySigns {
  override val OTHvalue = 97
  override val TRvalue = 24 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(222)
  override val NewLawCode = "B4"
}

case object GiveWay extends PriorityAndGiveWaySigns {
  override val OTHvalue = 98
  override val TRvalue = 25 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(231)
  override val NewLawCode = "B5"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(DistanceCompulsoryStop, DirectionOfPriorityRoad)
}

case object Stop extends PriorityAndGiveWaySigns {
  override val OTHvalue = 99
  override val TRvalue = 26 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(232)
  override val NewLawCode = "B6"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(DistanceCompulsoryStop, DirectionOfPriorityRoad)
}

case object PriorityForCyclistsCrossing extends PriorityAndGiveWaySigns {
  override val OTHvalue = 214
  override val TRvalue = 27 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None// There is no legacy code
  override val NewLawCode = "B7"
}


trait SpeedLimitsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.SpeedLimits

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)

  override def isSpeedLimit: Boolean = true
}

//TODO: Verify value
case object TelematicSpeedLimit extends SpeedLimitsType {
  override val OTHvalue = 44
  override val TRvalue = 0
  override val OldLawCode: Option[Int] = None
  override val NewLawCode = ""

  override def source = Seq()
}


trait RegulatorySignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.RegulatorySigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)

  override def isSpeedLimit: Boolean = {
    val speedLimitsSign = Seq(5, 6)
    speedLimitsSign.contains(OTHvalue)
  }
}

case object PedestrianCrossingSign extends RegulatorySignsType {
  override val OTHvalue = 7
  override val TRvalue = 51 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(511)
  override val NewLawCode = "E1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object ParkingLot extends RegulatorySignsType {
  override val OTHvalue = 105
  override val TRvalue = 52 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(521)
  override val NewLawCode = "E2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign,ParkingAgainstFee,	ObligatoryUseOfParkingDisc)
}

case object ParkingLotAndAccessToTrain extends RegulatorySignsType {
  override val OTHvalue = 137
  override val TRvalue = 5301 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(520)
  override val NewLawCode = "E3.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingLotAndAccessToBus extends RegulatorySignsType {
  override val OTHvalue = 239
  override val TRvalue = 5302 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E3.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingLotAndAccessToTram extends RegulatorySignsType {
  override val OTHvalue = 240
  override val TRvalue = 5303 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E3.3"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingLotAndAccessToSubway extends RegulatorySignsType {
  override val OTHvalue = 241
  override val TRvalue = 5304 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E3.4"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingLotAndAccessToPublicTransport extends RegulatorySignsType {
  override val OTHvalue = 242
  override val TRvalue = 5305 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E3.5"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingDirectly extends RegulatorySignsType {
  override val OTHvalue = 243
  override val TRvalue = 5401 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5211) // The previous way was "521 a"
  override val NewLawCode = "E4.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object ParkingOppositeEachOther extends RegulatorySignsType {
  override val OTHvalue = 244
  override val TRvalue = 5402 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5212) // The previous way was "521 b"
  override val NewLawCode = "E4.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object PositioningAtAnAngle extends RegulatorySignsType {
  override val OTHvalue = 245
  override val TRvalue = 5403 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5213) // The previous way was "521 c"
  override val NewLawCode = "E4.3"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object RoadDirection extends RegulatorySignsType {
  override val OTHvalue = 246
  override val TRvalue = 55 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(522)
  override val NewLawCode = "E5"
}

case object BusStopForLocalTraffic extends RegulatorySignsType {
  override val OTHvalue = 66
  override val TRvalue = 56 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(531)
  override val NewLawCode = "E6"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusStopForLongDistanceTraffic extends RegulatorySignsType {
  override val OTHvalue = 247
  override val TRvalue = 56 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(532)
  override val NewLawCode = "E6"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat)
}

case object TramStop extends RegulatorySignsType {
  override val OTHvalue = 68
  override val TRvalue = 57 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(533)
  override val NewLawCode = "E7"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object TaxiStation extends RegulatorySignsType {
  override val OTHvalue = 69
  override val TRvalue = 58 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(534)
  override val NewLawCode = "E8"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusLane extends RegulatorySignsType {
  override val OTHvalue = 63
  override val TRvalue = 5901 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5411) // The previous way was "541 a"
  override val NewLawCode = "E9.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusAndTaxiLane extends RegulatorySignsType {
  override val OTHvalue = 248
  override val TRvalue = 5902 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5412) // The previous way was "541 b"
  override val NewLawCode = "E9.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BusLaneEnds extends RegulatorySignsType {
  override val OTHvalue = 64
  override val TRvalue = 51001 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5421) // The previous way was "542 a"
  override val NewLawCode = "E10.1"
}

case object BusAndTaxiLaneEnds extends RegulatorySignsType {
  override val OTHvalue = 249
  override val TRvalue = 51002 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5422) // The previous way was "542 b"
  override val NewLawCode = "E10.2"

}

case object TramLane extends RegulatorySignsType {
  override val OTHvalue = 65
  override val TRvalue = 51101 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5431) // The previous way was "543 a"
  override val NewLawCode = "E11.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object TramAndTaxiLane extends RegulatorySignsType {
  override val OTHvalue = 250
  override val TRvalue = 51102 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5432) // The previous way was "543 b"
  override val NewLawCode = "E11.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object TramLaneEnds extends RegulatorySignsType {
  override val OTHvalue = 251
  override val TRvalue = 51201 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5441) // The previous way was "544 a"
  override val NewLawCode = "E12.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object TramAndTaxiLaneEnds extends RegulatorySignsType {
  override val OTHvalue = 252
  override val TRvalue = 51202 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(5442) // The previous way was "544 b"
  override val NewLawCode = "E12.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object BicycleLaneOnTheRight extends RegulatorySignsType {
  override val OTHvalue = 253
  override val TRvalue = 51301 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E13.1"
}

case object BicycleLaneInTheMiddle extends RegulatorySignsType {
  override val OTHvalue = 254
  override val TRvalue = 51302 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E13.2"
}

case object OneWayRoad extends RegulatorySignsType {
  override val OTHvalue = 106
  override val TRvalue = 51401 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(551)
  override val NewLawCode = "E14.1"
}

case object OneWayRoadLeftRight extends RegulatorySignsType {
  override val OTHvalue = 255
  override val TRvalue = 51402 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(551)
  override val NewLawCode = "E14.2"
}

case object MotorwaySign extends RegulatorySignsType {
  override val OTHvalue = 107
  override val TRvalue = 515 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(561)
  override val NewLawCode = "E15"
}

case object MotorwayEnds extends RegulatorySignsType {
  override val OTHvalue = 108
  override val TRvalue = 516 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(562)
  override val NewLawCode = "E16"
}

case object ExpresswaySign extends RegulatorySignsType {
  override val OTHvalue = 256
  override val TRvalue = 517 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(563)
  override val NewLawCode = "E17"
}

case object ExpresswayEnds extends RegulatorySignsType {
  override val OTHvalue = 257
  override val TRvalue = 518 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(564)
  override val NewLawCode = "E18"
}

case object TunnelSign extends RegulatorySignsType {
  override val OTHvalue = 258
  override val TRvalue = 519 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(565)
  override val NewLawCode = "E19"
}

case object TunnelEnds extends RegulatorySignsType {
  override val OTHvalue = 259
  override val TRvalue = 520 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(566)
  override val NewLawCode = "E20"
}

case object SOSZoneSign extends RegulatorySignsType {
  override val OTHvalue = 260
  override val TRvalue = 521 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(567)
  override val NewLawCode = "E21"
}

case object UrbanArea extends RegulatorySignsType {
  override val OTHvalue = 5
  override val TRvalue = 522 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(571)
  override val NewLawCode = "E22"
}

case object EndUrbanArea extends RegulatorySignsType {
  override val OTHvalue = 6
  override val TRvalue = 523 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(572)
  override val NewLawCode = "E23"
}

case object ResidentialZone extends RegulatorySignsType {
  override val OTHvalue = 109
  override val TRvalue = 524 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(573)
  override val NewLawCode = "E24"
}

case object EndOfResidentialZone extends RegulatorySignsType {
  override val OTHvalue = 110
  override val TRvalue = 525 // UUSIASNR Value in TR
  override val OldLawCode = Some(574)
  override val NewLawCode = "E25"
}

case object PedestrianZoneSign extends RegulatorySignsType {
  override val OTHvalue = 111
  override val TRvalue = 526 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(575)
  override val NewLawCode = "E26"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object EndOfPedestrianZone extends RegulatorySignsType {
  override val OTHvalue = 112
  override val TRvalue = 527 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(576)
  override val NewLawCode = "E27"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object BicycleStreet extends RegulatorySignsType {
  override val OTHvalue = 261
  override val TRvalue = 528 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E28"
}

case object BicycleStreetEnds extends RegulatorySignsType {
  override val OTHvalue = 262
  override val TRvalue = 529 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E29"
}

case object LaneMerge extends RegulatorySignsType {
  override val OTHvalue = 263
  override val TRvalue = 530 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "E30"
}


trait GeneralWarningSignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.GeneralWarningSigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}


case object WRightBend extends GeneralWarningSignsType {
  override val OTHvalue = 36
  override val TRvalue = 1101 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(111)
  override val NewLawCode = "A1.1"
}

case object WLeftBend extends GeneralWarningSignsType {
  override val OTHvalue = 37
  override val TRvalue = 1102 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(112)
  override val NewLawCode = "A1.2"
}

case object WSeveralBendsRight extends GeneralWarningSignsType {
  override val OTHvalue = 38
  override val TRvalue = 1201 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(113)
  override val NewLawCode = "A2.1"
}

case object WSeveralBendsLeft extends GeneralWarningSignsType {
  override val OTHvalue = 39
  override val TRvalue = 1202 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(114)
  override val NewLawCode = "A2.2"
}

case object WSteepAscent extends GeneralWarningSignsType {
  override val OTHvalue = 41
  override val TRvalue = 1301 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(116)
  override val NewLawCode = "A3.1"
}

case object WDangerousDescent extends GeneralWarningSignsType {
  override val OTHvalue = 40
  override val TRvalue = 1302 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(115)
  override val NewLawCode = "A3.2"
}

case object RoadNarrows extends GeneralWarningSignsType {
  override val OTHvalue = 82
  override val TRvalue = 14 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(121)
  override val NewLawCode = "A4"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(FreeWidth)
}

case object TwoWayTraffic extends GeneralWarningSignsType {
  override val OTHvalue = 83
  override val TRvalue = 15 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(122)
  override val NewLawCode = "A5"
}

case object SwingBridge extends GeneralWarningSignsType {
  override val OTHvalue = 84
  override val TRvalue = 16 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(131)
  override val NewLawCode = "A6"
}

case object EndInPierOrCliff extends GeneralWarningSignsType {
  override val OTHvalue = 200
  override val TRvalue = 17 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(132)
  override val NewLawCode = "A7"
}

case object TrafficJam extends GeneralWarningSignsType {
  override val OTHvalue = 201
  override val TRvalue = 18 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(133)
  override val NewLawCode = "A8"
}

case object WUnevenRoad extends GeneralWarningSignsType {
  override val OTHvalue = 42
  override val TRvalue = 19 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(141)
  override val NewLawCode = "A9"
}

case object Bumps extends GeneralWarningSignsType {
  override val OTHvalue = 202
  override val TRvalue = 110 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(1411) // The previous way was "141 a"
  override val NewLawCode = "A10"
}

case object RoadWorks extends GeneralWarningSignsType {
  override val OTHvalue = 85
  override val TRvalue = 111 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(142)
  override val NewLawCode = "A11"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object LooseStones extends GeneralWarningSignsType {
  override val OTHvalue = 203
  override val TRvalue = 112 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(143)
  override val NewLawCode = "A12"
}

case object SlipperyRoad extends GeneralWarningSignsType {
  override val OTHvalue = 86
  override val TRvalue = 113 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(144)
  override val NewLawCode = "A13"
}

case object DangerousRoadSide extends GeneralWarningSignsType {
  override val OTHvalue = 204
  override val TRvalue = 114 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(147)
  override val NewLawCode = "A14"
}

case object PedestrianCrossingWarningSign extends GeneralWarningSignsType {
  override val OTHvalue = 87
  override val TRvalue = 115 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(151)
  override val NewLawCode = "A15"
}

case object Pedestrians extends GeneralWarningSignsType {
  override val OTHvalue = 205
  override val TRvalue = 116 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "A16"
}

case object WChildren extends GeneralWarningSignsType {
  override val OTHvalue = 43
  override val TRvalue = 117 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(152)
  override val NewLawCode = "A17"
}

case object Cyclists extends GeneralWarningSignsType {
  override val OTHvalue = 88
  override val TRvalue = 118 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(153)
  override val NewLawCode = "A18"
}

case object WCrossCountrySkiing extends GeneralWarningSignsType {
  override val OTHvalue = 206
  override val TRvalue = 119 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(154)
  override val NewLawCode = "A19"
}

case object Moose extends GeneralWarningSignsType {
  override val OTHvalue = 125
  override val TRvalue = 12001 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(155)
  override val NewLawCode = "A20.1"
}

case object Reindeer extends GeneralWarningSignsType {
  override val OTHvalue = 126
  override val TRvalue = 12002 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(156)
  override val NewLawCode = "A20.2"
}

case object WildAnimals extends GeneralWarningSignsType {
  override val OTHvalue = 207
  override val TRvalue = 12003 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "A20.3"
}

case object IntersectionWithEqualRoads extends GeneralWarningSignsType {
  override val OTHvalue = 89
  override val TRvalue = 121 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(161)
  override val NewLawCode = "A21"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object IntersectionWithMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 127
  override val TRvalue = 12201
  override val OldLawCode: Option[Int] = Some(162)
  override val NewLawCode = "A22.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithTwoMinorRoads extends GeneralWarningSignsType {
  override val OTHvalue = 208
  override val TRvalue = 12202 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "A22.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithOneMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 128
  override val TRvalue = 12203 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(163)
  override val NewLawCode = "A22.3"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object IntersectionWithOneCrossMinorRoad extends GeneralWarningSignsType {
  override val OTHvalue = 129
  override val TRvalue = 12204 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(164)
  override val NewLawCode = "A22.4"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(CrossingLogTransportRoad)
}

case object LightSignals extends GeneralWarningSignsType {
  override val OTHvalue = 90
  override val TRvalue = 123 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(165)
  override val NewLawCode = "A23"
}

case object Roundabout extends GeneralWarningSignsType {
  override val OTHvalue = 209
  override val TRvalue = 124 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(166)
  override val NewLawCode = "A24"
}

case object TramwayLine extends GeneralWarningSignsType {
  override val OTHvalue = 91
  override val TRvalue = 125 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(167)
  override val NewLawCode = "A25"
}

case object LevelCrossingWithoutGate extends GeneralWarningSignsType {
  override val OTHvalue = 130
  override val TRvalue = 126 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(171)
  override val NewLawCode = "A26"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object LevelCrossingWithGates extends GeneralWarningSignsType {
  override val OTHvalue = 131
  override val TRvalue = 127 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(172)
  override val NewLawCode = "A27"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object ApproachLevelCrossingThreeStrips extends GeneralWarningSignsType {
  override val OTHvalue = 210
  override val TRvalue = 12801
  override val OldLawCode: Option[Int] = Some(173)
  override val NewLawCode = "A28.1"
}

case object ApproachLevelCrossingTwoStrips extends GeneralWarningSignsType {
  override val OTHvalue = 211
  override val TRvalue = 12802 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(174)
  override val NewLawCode = "A28.2"
}

case object ApproachLevelCrossingOneStrip extends GeneralWarningSignsType {
  override val OTHvalue = 212
  override val TRvalue = 12803 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(175)
  override val NewLawCode = "A28.3"
}

case object LevelCrossingWithOneTrack extends GeneralWarningSignsType {
  override val OTHvalue = 132
  override val TRvalue = 12901 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(176)
  override val NewLawCode = "A29.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object LevelCrossingWithManyTracks extends GeneralWarningSignsType {
  override val OTHvalue = 133
  override val TRvalue = 12902 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(177)
  override val NewLawCode = "A29.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HeightElectricLine)
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object FallingRocks extends GeneralWarningSignsType {
  override val OTHvalue = 92
  override val TRvalue = 130 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(181)
  override val NewLawCode = "A30"
}

case object LowFlyingPlanes extends GeneralWarningSignsType {
  override val OTHvalue = 213
  override val TRvalue = 131 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(182)
  override val NewLawCode = "A31"
}

case object CrossWind extends GeneralWarningSignsType {
  override val OTHvalue = 93
  override val TRvalue = 132 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(183)
  override val NewLawCode = "A32"
}

case object Warning extends GeneralWarningSignsType {
  override val OTHvalue = 9
  override val TRvalue = 133 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(189)
  override val NewLawCode = "A33"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(FreeHeight)
}


trait ProhibitionsAndRestrictionsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.ProhibitionsAndRestrictions

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)

  override def isSpeedLimit : Boolean = {
    val speedLimitsSigns = Seq(1, 2, 3, 4)
    speedLimitsSigns.contains(OTHvalue)
  }

}

case object ClosedToAllVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 13
  override val TRvalue = 31 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(311)
  override val NewLawCode = "C1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object NoPowerDrivenVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 14
  override val TRvalue = 32 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(312)
  override val NewLawCode = "C2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object NoLorriesAndVans extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 15
  override val TRvalue = 33 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(313)
  override val NewLawCode = "C3"
}

case object NoVehicleCombinations extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 16
  override val TRvalue = 34 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(314)
  override val NewLawCode = "C4"
}

case object NoAgriculturalVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 17
  override val TRvalue = 35 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(315)
  override val NewLawCode = "C5"
}

case object NoMotorCycles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 18
  override val TRvalue = 36 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(316)
  override val NewLawCode = "C6"
}

case object NoMotorSledges extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 19
  override val TRvalue = 37 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(317)
  override val NewLawCode = "C7"
}

case object NoVehiclesWithDangerGoods extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 20
  override val TRvalue = 38 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(318)
  override val NewLawCode = "C8"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(HazmatProhibitionA, HazmatProhibitionB)
}

case object NoBuses extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 21
  override val TRvalue = 39 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(319)
  override val NewLawCode = "C9"
}

case object NoMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 22
  override val TRvalue = 310 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(321)
  override val NewLawCode = "C10"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object NoCyclists extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 215
  override val TRvalue = 311 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "C11"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object NoCyclesOrMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 23
  override val TRvalue = 312 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(322)
  override val NewLawCode = "C12"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object NoPedestrians extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 24
  override val TRvalue = 313 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(323)
  override val NewLawCode = "C13"
}

case object NoCyclistsOrPedestrians extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 216
  override val TRvalue = 314 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "C14"
}

case object NoPedestriansCyclesMopeds extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 25
  override val TRvalue = 315 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(324)
  override val NewLawCode = "C15"
}

case object NoRidersOnHorseback extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 26
  override val TRvalue = 316 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(325)
  override val NewLawCode = "C16"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object NoEntry extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 27
  override val TRvalue = 317 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(331)
  override val NewLawCode = "C17"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object NoLeftTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 10
  override val TRvalue = 318 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(332)
  override val NewLawCode = "C18"
}

case object NoRightTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 11
  override val TRvalue = 319 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(333)
  override val NewLawCode = "C19"
}

case object NoUTurn extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 12
  override val TRvalue = 320 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(334)
  override val NewLawCode = "C20"
}

case object NoWidthExceeding extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 30
  override val TRvalue = 321 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(341)
  override val NewLawCode = "C21"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object MaxHeightExceeding extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 31
  override val TRvalue = 322 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(342)
  override val NewLawCode = "C22"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object MaximumLength extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 8
  override val TRvalue = 323 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(343)
  override val NewLawCode = "C23"
}

case object MaxLadenExceeding extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 32
  override val TRvalue = 324 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(344)
  override val NewLawCode = "C24"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object MaxMassCombineVehiclesExceeding extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 33
  override val TRvalue = 325 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(345)
  override val NewLawCode = "C25"
}

case object MaxTonsOneAxleExceeding extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 34
  override val TRvalue = 326 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(346)
  override val NewLawCode = "C26"

}

case object MaxTonsOnBogieExceeding extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 35
  override val TRvalue = 327 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(347)
  override val NewLawCode = "C27"
}

case object OvertakingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 28
  override val TRvalue = 328 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(351)
  override val NewLawCode = "C28"
}

case object EndProhibitionOfOvertaking extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 29
  override val TRvalue = 329 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(352)
  override val NewLawCode = "C29"
}

case object OvertakingProhibitedByTruck extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 217
  override val TRvalue = 330 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(353)
  override val NewLawCode = "C30"
}

case object EndProhibitionOfOvertakingByTruck extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 218
  override val TRvalue = 331 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(354)
  override val NewLawCode = "C31"
}

case object SpeedLimitSign extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 1
  override val TRvalue = 332 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(361)
  override val NewLawCode = "C32"
}

case object EndSpeedLimit extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 2
  override val TRvalue = 333 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(362)
  override val NewLawCode = "C33"
}

case object SpeedLimitZone extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 3
  override val TRvalue = 334 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(363)
  override val NewLawCode = "C34"
}

case object EndSpeedLimitZone extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 4
  override val TRvalue = 335 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(364)
  override val NewLawCode = "C35"
}

case object ProhibitionOrRegulationPerLane extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 219
  override val TRvalue = 336 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(365)
  override val NewLawCode = "C36"
}

case object StandingAndParkingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 100
  override val TRvalue = 337 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(371)
  override val NewLawCode = "C37"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParkingProhibited extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 101
  override val TRvalue = 338 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(372)
  override val NewLawCode = "C38"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign, ParkingAgainstFee,  ObligatoryUseOfParkingDisc)
}

case object ParkingProhibitedZone extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 102
  override val TRvalue = 339 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(373)
  override val NewLawCode = "C39"
}

case object EndOfParkingProhibitedZone extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 103
  override val TRvalue = 340 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(374)
  override val NewLawCode = "C40"
}

case object TaxiStationZoneBeginning extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 80
  override val TRvalue = 341 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(375)
  override val NewLawCode = "C41"
}

case object StandingPlaceForTaxi extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 81
  override val TRvalue = 342 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(376)
  override val NewLawCode = "C42"
}

case object LoadingPlace extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 220
  override val TRvalue = 343 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "C43"
}

case object AlternativeParkingOddDays extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 104
  override val TRvalue = 34401 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(381)
  override val NewLawCode = "C44.1"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object AlternativeParkingEvenDays extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 134
  override val TRvalue = 34402 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(382)
  override val NewLawCode = "C44.2"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object CustomsControl extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 221
  override val TRvalue = 345 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(391)
  override val NewLawCode = "C45"
}

case object MandatoryStopForInspection extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 222
  override val TRvalue = 346 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(392)
  override val NewLawCode = "C46"
}

case object MinimumDistanceBetweenVehicles extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 223
  override val TRvalue = 347 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(393)
  override val NewLawCode = "C47"
}

case object NoStuddedTires extends ProhibitionsAndRestrictionsType {
  override val OTHvalue = 224
  override val TRvalue = 348 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "C48"
}


trait MandatorySignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.MandatorySigns

  override def isSpeedLimit : Boolean = {
    val speedLimitsSigns = Seq(237, 238)
    speedLimitsSigns.contains(OTHvalue)
  }

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}


case object RightDirection extends MandatorySignsType {
  override val OTHvalue = 225
  override val TRvalue = 4101 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(411)
  override val NewLawCode = "D1.1"
}

case object LeftDirection extends MandatorySignsType {
  override val OTHvalue = 226
  override val TRvalue = 4102 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "D1.2"
}

case object StraightDirection extends MandatorySignsType {
  override val OTHvalue = 227
  override val TRvalue = 4103 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(412)
  override val NewLawCode = "D1.3"
}

case object TurnRight extends MandatorySignsType {
  override val OTHvalue = 74
  override val TRvalue = 4104 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(413)
  override val NewLawCode = "D1.4"
}

case object TurnLeft extends MandatorySignsType {
  override val OTHvalue = 228
  override val TRvalue = 4105 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "D1.5"
}

case object StraightDirectionOrRightTurn extends MandatorySignsType {
  override val OTHvalue = 229
  override val TRvalue = 4106 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(414)
  override val NewLawCode = "D1.6"
}

case object StraightDirectionOrLeftTurn extends MandatorySignsType {
  override val OTHvalue = 230
  override val TRvalue = 4107 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "D1.7"
}

case object LeftTurnOrRightTurn extends MandatorySignsType {
  override val OTHvalue = 231
  override val TRvalue = 4108 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(415)
  override val NewLawCode = "D1.8"
}

case object StraightDirectionOrRightOrLeftTurn extends MandatorySignsType {
  override val OTHvalue = 232
  override val TRvalue = 4109 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "D1.9"
}

case object CompulsoryRoundabout extends MandatorySignsType {
  override val OTHvalue = 77
  override val TRvalue = 42 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(416)
  override val NewLawCode = "D2"
}

case object PassRightSide extends MandatorySignsType {
  override val OTHvalue = 78
  override val TRvalue = 4301 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(417)
  override val NewLawCode = "D3.1"
}

case object PassLeftSide extends MandatorySignsType {
  override val OTHvalue = 233
  override val TRvalue = 4302 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(417)
  override val NewLawCode = "D3.2"
}

case object DividerOfTraffic extends MandatorySignsType {
  override val OTHvalue = 234
  override val TRvalue = 4303 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(418)
  override val NewLawCode = "D3.3"
}

case object CompulsoryFootPath extends MandatorySignsType {
  override val OTHvalue = 70
  override val TRvalue = 44 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(421)
  override val NewLawCode = "D4"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object CompulsoryCycleTrack extends MandatorySignsType {
  override val OTHvalue = 71
  override val TRvalue = 45 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(422)
  override val NewLawCode = "D5"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object CombinedCycleTrackAndFootPath extends MandatorySignsType {
  override val OTHvalue = 72
  override val TRvalue = 46 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(423)
  override val NewLawCode = "D6"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParallelCycleTrackAndFootPath extends MandatorySignsType {
  override val OTHvalue = 235
  override val TRvalue = 4701 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(424)
  override val NewLawCode = "D7.1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object ParallelCycleTrackAndFootPath2 extends MandatorySignsType {
  override val OTHvalue = 236
  override val TRvalue = 4702 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(425)
  override val NewLawCode = "D7.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object CompulsoryTrackMotorSledges extends MandatorySignsType {
  override val OTHvalue = 135
  override val TRvalue = 48 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(426)
  override val NewLawCode = "D8"

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign)
}

case object CompulsoryTrackRidersHorseback extends MandatorySignsType {
  override val OTHvalue = 136
  override val TRvalue = 49 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(427)
  override val NewLawCode = "D9"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)

  override val supportedAdditionalPanel: Seq[AdditionalPanelsType] = Seq(SignAppliesBothDirections, SignAppliesBothDirectionsVertical,
    SignAppliesArrowDirections, RegulationBeginsFromSign, RegulationEndsToTheSign, ValidMonFri,	ValidSat,	ValidMultiplePeriod,
    ParkingAgainstFee,	ObligatoryUseOfParkingDisc)
}

case object MinimumSpeed extends MandatorySignsType {
  override val OTHvalue = 237
  override val TRvalue = 410 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "D10"
}

case object MinimumSpeedEnds extends MandatorySignsType {
  override val OTHvalue = 238
  override val TRvalue = 411 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "D11"
}



trait InformationSignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.InformationSigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}


case object AdvanceDirectionSign extends InformationSignsType {
  override val OTHvalue = 192
  override val TRvalue = 6101 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(611)
  override val NewLawCode = "F1.1"
}

case object AdvanceDirectionSign2 extends InformationSignsType {
  override val OTHvalue = 264
  override val TRvalue = 6102 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F1.2"
}

case object AdvanceDirectionSign3 extends InformationSignsType {
  override val OTHvalue = 265
  override val TRvalue = 6103 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F1.3"
}

case object AdvanceDirectionSignSmall extends InformationSignsType {
  override val OTHvalue = 178
  override val TRvalue = 6201 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(612)
  override val NewLawCode = "F2.1"
}

case object AdvanceDirectionSignSmall2 extends InformationSignsType {
  override val OTHvalue = 266
  override val TRvalue = 6202 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F2.2"
}

case object AdvanceDirectionSignSmall3 extends InformationSignsType {
  override val OTHvalue = 267
  override val TRvalue = 6203 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F2.3"
}

case object LaneSpecificNavigationBoard extends InformationSignsType {
  override val OTHvalue = 268
  override val TRvalue = 63 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F3"
}

case object AdvisorySignDetourLarge extends InformationSignsType {
  override val OTHvalue = 152
  override val TRvalue = 6401 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(614)
  override val NewLawCode = "F4.1"
}

case object AdvisorySignDetour extends InformationSignsType {
  override val OTHvalue = 193
  override val TRvalue = 6402 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(613)
  override val NewLawCode = "F4.2"
}

case object Detour extends InformationSignsType {
  override val OTHvalue = 153
  override val TRvalue = 65 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(615)
  override val NewLawCode = "F5"
}

case object RouteToBeFollowed extends InformationSignsType {
  override val OTHvalue = 154
  override val TRvalue = 66 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(616)
  override val NewLawCode = "F6"
}

case object InformationOnTrafficLanes extends InformationSignsType {
  override val OTHvalue = 155
  override val TRvalue = 6701 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(621)
  override val NewLawCode = "F7.1"
}

case object BiDirectionalInformationOnTrafficLanes extends InformationSignsType {
  override val OTHvalue = 156
  override val TRvalue = 6702 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(622)
  override val NewLawCode = "F7.2"
}

case object TrafficLanesWithSeparator extends InformationSignsType {
  override val OTHvalue = 269
  override val TRvalue = 6703 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6225)
  override val NewLawCode = "F7.3"
}

case object IncreasedLaneNumber extends InformationSignsType {
  override val OTHvalue = 270
  override val TRvalue = 6704 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F7.4"
}

case object NewLaneIncoming extends InformationSignsType {
  override val OTHvalue = 271
  override val TRvalue = 6705 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F7.5"
}

case object NewLaneIncoming2 extends InformationSignsType {
  override val OTHvalue = 272
  override val TRvalue = 6706 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F7.6"
}

case object EndOfLane extends InformationSignsType {
  override val OTHvalue = 157
  override val TRvalue = 6801 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(623)
  override val NewLawCode = "F8.1"
}

case object EndOfLane2 extends InformationSignsType {
  override val OTHvalue = 399
  override val TRvalue = 6802 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F8.2"
}

case object CompilationSign extends InformationSignsType {
  override val OTHvalue = 273
  override val TRvalue = 69 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F9"
}

case object AdvanceDirectionSignAbove extends InformationSignsType {
  override val OTHvalue = 158
  override val TRvalue = 610 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(631)
  override val NewLawCode = "F10"
}

case object AdvanceDirectionSignAboveSmall extends InformationSignsType {
  override val OTHvalue = 191
  override val TRvalue = 611 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(632)
  override val NewLawCode = "F11"
}

case object ExitSignAbove extends InformationSignsType {
  override val OTHvalue = 159
  override val TRvalue = 612 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(633)
  override val NewLawCode = "F12"
}

case object DirectionSign extends InformationSignsType {
  override val OTHvalue = 160
  override val TRvalue = 613 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(641)
  override val NewLawCode = "F13"
}

case object DirectionSignOnPrivateRoad extends InformationSignsType {
  override val OTHvalue = 162
  override val TRvalue = 613 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(643)
  override val NewLawCode = "F13"
}

case object DirectionSignForLocalPurposes extends InformationSignsType {
  override val OTHvalue = 167
  override val TRvalue = 613 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(648)
  override val NewLawCode = "F13"
}

case object DirectionSignForMotorway extends InformationSignsType {
  override val OTHvalue = 168
  override val TRvalue = 613 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(649)
  override val NewLawCode = "F13"
}

case object ExitSign extends InformationSignsType {
  override val OTHvalue = 161
  override val TRvalue = 614 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(642)
  override val NewLawCode = "F14"
}

case object DirectionSignForDetourWithText extends InformationSignsType {
  override val OTHvalue = 165
  override val TRvalue = 613 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(646)
  override val NewLawCode = "F13"
}

case object DirectionSignForDetour extends InformationSignsType {
  override val OTHvalue = 166
  override val TRvalue = 615 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(647)
  override val NewLawCode = "F15"
}

case object DirectionSignForDetour2 extends InformationSignsType {
  override val OTHvalue = 274
  override val TRvalue = 615 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(921)
  override val NewLawCode = "F15"
}

case object LocationSign extends InformationSignsType {
  override val OTHvalue = 163
  override val TRvalue = 616 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(644)
  override val NewLawCode = "F16"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object AdvanceLocationSign extends InformationSignsType {
  override val OTHvalue = 275
  override val TRvalue = 617 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6441) // The previous way was "644 a"
  override val NewLawCode = "F17"
}

case object AccessParkingAndTrainSign extends InformationSignsType {
  override val OTHvalue = 169
  override val TRvalue = 61801 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(650)
  override val NewLawCode = "F18.1"
}

case object AccessParkingAndBusSign extends InformationSignsType {
  override val OTHvalue = 276
  override val TRvalue = 61802 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F18.2"
}

case object AccessParkingAndTramSign extends InformationSignsType {
  override val OTHvalue = 277
  override val TRvalue = 61803 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F18.3"
}

case object AccessParkingAndSubwaySign extends InformationSignsType {
  override val OTHvalue = 278
  override val TRvalue = 61804 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F18.4"
}

case object AccessParkingAndPublicTransportsSign extends InformationSignsType {
  override val OTHvalue = 279
  override val TRvalue = 61805 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F18.5"
}

case object DirectionSignForPedestrians extends InformationSignsType {
  override val OTHvalue = 164
  override val TRvalue = 619 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(645)
  override val NewLawCode = "F19"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object DirectionSignForCyclistsWithoutDistances extends InformationSignsType {
  override val OTHvalue = 280
  override val TRvalue = 62001 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(645)
  override val NewLawCode = "F20.1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object DirectionSignForCyclistsWithDistances extends InformationSignsType {
  override val OTHvalue = 281
  override val TRvalue = 62002 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(645)
  override val NewLawCode = "F20.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object AdvanceDirectionSignForCyclistsWithDistances extends InformationSignsType {
  override val OTHvalue = 282
  override val TRvalue = 62101 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F21.1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object AdvanceDirectionSignForCyclistsWithoutDistances extends InformationSignsType {
  override val OTHvalue = 283
  override val TRvalue = 62102 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F21.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object DistanceBoardForCyclists extends InformationSignsType {
  override val OTHvalue = 284
  override val TRvalue = 622 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F22"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object PlaceNameForCyclists extends InformationSignsType {
  override val OTHvalue = 285
  override val TRvalue = 623 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F23"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object NoThroughRoad extends InformationSignsType {
  override val OTHvalue = 113
  override val TRvalue = 62401 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(651)
  override val NewLawCode = "F24.1"
}

case object NoThroughRoadRight extends InformationSignsType {
  override val OTHvalue = 114
  override val TRvalue = 62402 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(652)
  override val NewLawCode = "F24.2"
}

case object NoThroughRoadCyclist extends InformationSignsType {
  override val OTHvalue = 286
  override val TRvalue = 62403 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F24.3"
}

case object RecommendedMaxSpeed extends InformationSignsType {
  override val OTHvalue = 170
  override val TRvalue = 625 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(653)
  override val NewLawCode = "F25"
}

case object SignShowingDistance extends InformationSignsType {
  override val OTHvalue = 171
  override val TRvalue = 626 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(661)
  override val NewLawCode = "F26"
}

case object PlaceName extends InformationSignsType {
  override val OTHvalue = 172
  override val TRvalue = 62701 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(662)
  override val NewLawCode = "F27.1"
}

case object PlaceName2 extends InformationSignsType {
  override val OTHvalue = 287
  override val TRvalue = 62701 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(10)
  override val NewLawCode = "F27.1"
}

case object PlaceName3 extends InformationSignsType {
  override val OTHvalue = 288
  override val TRvalue = 62701 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(11)
  override val NewLawCode = "F27.1"
}

case object RiverName extends InformationSignsType {
  override val OTHvalue = 289
  override val TRvalue = 62702 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F27.2"
}

case object RoadNumberInternationalRoad extends InformationSignsType {
  override val OTHvalue = 173
  override val TRvalue = 628 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(663)
  override val NewLawCode = "F28"
}

case object RoadNumberHighway extends InformationSignsType {
  override val OTHvalue = 175
  override val TRvalue = 629 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(664)
  override val NewLawCode = "F29"
}

case object RoadNumberPrimaryRoad extends InformationSignsType {
  override val OTHvalue = 176
  override val TRvalue = 630 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(665)
  override val NewLawCode = "F30"
}

case object RoadNumberRegionalRoad extends InformationSignsType {
  override val OTHvalue = 400
  override val TRvalue = 631 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6651) // The previous way was "665 a"
  override val NewLawCode = "F31"
}

case object RoadNumberOtherRoad extends InformationSignsType {
  override val OTHvalue = 177
  override val TRvalue = 632 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(666)
  override val NewLawCode = "F32"
}

case object RoadNumberRingRoad extends InformationSignsType {
  override val OTHvalue = 290
  override val TRvalue = 633 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F33"
}

case object ExitNumber extends InformationSignsType {
  override val OTHvalue = 291
  override val TRvalue = 634 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6679)
  override val NewLawCode = "F34"
}

case object DirectionToTheNumberedRoad extends InformationSignsType {
  override val OTHvalue = 174
  override val TRvalue = 635 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(667)
  override val NewLawCode = "F35"
}

case object DirectionToTheNumberedPrimaryRoad extends InformationSignsType {
  override val OTHvalue = 292
  override val TRvalue = 636 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F36"
}

case object SymbolOfMotorway extends InformationSignsType {
  override val OTHvalue = 115
  override val TRvalue = 637 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(671)
  override val NewLawCode = "F37"
}

case object RoadForMotorVehicles extends InformationSignsType {
  override val OTHvalue = 179
  override val TRvalue = 638 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(672)
  override val NewLawCode = "F38"
}

case object Airport extends InformationSignsType {
  override val OTHvalue = 180
  override val TRvalue = 639 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(673)
  override val NewLawCode = "F39"
}

case object Ferry extends InformationSignsType {
  override val OTHvalue = 181
  override val TRvalue = 640 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(674)
  override val NewLawCode = "F40"
}

case object Boat extends InformationSignsType {
  override val OTHvalue = 293
  override val TRvalue = 641 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F41"
}

case object GoodsHarbour extends InformationSignsType {
  override val OTHvalue = 182
  override val TRvalue = 642 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(675)
  override val NewLawCode = "F42"
}

case object CargoTerminal extends InformationSignsType {
  override val OTHvalue = 294
  override val TRvalue = 643 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F43"
}

case object IndustrialArea extends InformationSignsType {
  override val OTHvalue = 183
  override val TRvalue = 644 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(676)
  override val NewLawCode = "F44"
}

case object LargeRetailUnit extends InformationSignsType {
  override val OTHvalue = 295
  override val TRvalue = 645 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F45"
}

case object Parking extends InformationSignsType {
  override val OTHvalue = 116
  override val TRvalue = 64601 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(677)
  override val NewLawCode = "F46.1"
}

case object ParkingCovered extends InformationSignsType {
  override val OTHvalue = 296
  override val TRvalue = 64602 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6771) // The previous way was "677 a"
  override val NewLawCode = "F46.2"
}

case object RailwayStation extends InformationSignsType {
  override val OTHvalue = 184
  override val TRvalue = 647 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(678)
  override val NewLawCode = "F47"
}

case object BusStation extends InformationSignsType {
  override val OTHvalue = 185
  override val TRvalue = 648 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(679)
  override val NewLawCode = "F48"
}

case object Center extends InformationSignsType {
  override val OTHvalue = 299
  override val TRvalue = 649 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F49"
}

case object ItineraryForIndicatedVehicleCategory extends InformationSignsType {
  override val OTHvalue = 117
  override val TRvalue = 650 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(681)
  override val NewLawCode = "F50"
}

case object TruckRoute extends InformationSignsType {
  override val OTHvalue = 390
  override val TRvalue = 65001 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6811)
  override val NewLawCode = "F50.1"
}

case object PassengerCarRoute extends InformationSignsType {
  override val OTHvalue = 391
  override val TRvalue = 65002 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6812)
  override val NewLawCode = "F50.2"
}

case object BusRoute extends InformationSignsType {
  override val OTHvalue = 392
  override val TRvalue = 65003 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6813)
  override val NewLawCode = "F50.3"
}

case object VanRoute extends InformationSignsType {
  override val OTHvalue = 393
  override val TRvalue = 65004 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6814)
  override val NewLawCode = "F50.4"
}

case object MotorcycleRoute extends InformationSignsType {
  override val OTHvalue = 394
  override val TRvalue = 65005 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6815)
  override val NewLawCode = "F50.5"
}

case object MopedRoute extends InformationSignsType {
  override val OTHvalue = 395
  override val TRvalue = 65006 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6816)
  override val NewLawCode = "F50.6"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object TractorRoute extends InformationSignsType {
  override val OTHvalue = 396
  override val TRvalue = 65007 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6817)
  override val NewLawCode = "F50.7"
}

case object MotorHomeRoute extends InformationSignsType {
  override val OTHvalue = 397
  override val TRvalue = 65008 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6818)
  override val NewLawCode = "F50.8"
}

case object BicycleRoute extends InformationSignsType {
  override val OTHvalue = 398
  override val TRvalue = 65009 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(6819)
  override val NewLawCode = "F50.9"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object ItineraryForDangerousGoodsTransport extends InformationSignsType {
  override val OTHvalue = 186
  override val TRvalue = 651 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(684)
  override val NewLawCode = "F51"
}

case object ItineraryForPedestrians extends InformationSignsType {
  override val OTHvalue = 118
  override val TRvalue = 652 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(682)
  override val NewLawCode = "F52"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object AccessibleRoute extends InformationSignsType {
  override val OTHvalue = 119
  override val TRvalue = 653 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(683)
  override val NewLawCode = "F53"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object UnderpassWithSteps extends InformationSignsType {
  override val OTHvalue = 187
  override val TRvalue = 65401 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(685)
  override val NewLawCode = "F54.1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object OverpassWithSteps extends InformationSignsType {
  override val OTHvalue = 298
  override val TRvalue = 65402 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F54.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object UnderpassWithoutSteps extends InformationSignsType {
  override val OTHvalue = 188
  override val TRvalue = 65501 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(686)
  override val NewLawCode = "F55.1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object OverpassWithoutSteps extends InformationSignsType {
  override val OTHvalue = 299
  override val TRvalue = 65502 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F55.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object UnderpassForWheelchair extends InformationSignsType {
  override val OTHvalue = 300
  override val TRvalue = 65503 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F55.3"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object OverpassForWheelchair extends InformationSignsType {
  override val OTHvalue = 301
  override val TRvalue = 65504 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F55.4"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object EmergencyExitOnTheLeft extends InformationSignsType {
  override val OTHvalue = 189
  override val TRvalue = 65601 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(690)
  override val NewLawCode = "F56.1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object EmergencyExitOnTheRight extends InformationSignsType {
  override val OTHvalue = 302
  override val TRvalue = 65602 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F56.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object SingleExitRoute extends InformationSignsType {
  override val OTHvalue = 190
  override val TRvalue = 65701 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(691)
  override val NewLawCode = "F57.1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object MultipleExitRoute extends InformationSignsType {
  override val OTHvalue = 303
  override val TRvalue = 65702 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "F57.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}



trait ServiceSignsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.ServiceSigns

  override def relevantAdditionalPanel: Seq[AdditionalPanelsType] = supportedAdditionalPanel ++
    Seq(AdditionalPanelWithText, DistanceWhichSignApplies, DistanceFromSignToPointWhichSignApplies, ValidMonFri, ValidSat, ValidMultiplePeriod)
}

case object InformationSignForServices extends ServiceSignsType {
  override val OTHvalue = 304
  override val TRvalue = 71 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(701)
  override val NewLawCode = "G1"
}

case object InformationSignForServices2 extends ServiceSignsType {
  override val OTHvalue = 305
  override val TRvalue = 72 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(702)
  override val NewLawCode = "G2"
}

case object AdvanceInformationSignForServices extends ServiceSignsType {
  override val OTHvalue = 306
  override val TRvalue = 73 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(703)
  override val NewLawCode = "G3"
}

case object LocationSignForTouristService  extends ServiceSignsType {
  override val OTHvalue = 120
  override val TRvalue = 74 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(704)
  override val NewLawCode = "G4"
}

case object AdvanceLocationSignForTouristService  extends ServiceSignsType {
  override val OTHvalue = 307
  override val TRvalue = 75 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7041) // The previous way was "704 a"
  override val NewLawCode = "G5"
}

case object RadioStationFrequency  extends ServiceSignsType {
  override val OTHvalue = 308
  override val TRvalue = 76 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(710)
  override val NewLawCode = "G6"
}

case object InformationPoint extends ServiceSignsType {
  override val OTHvalue = 309
  override val TRvalue = 77 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(711)
  override val NewLawCode = "G7"
}

case object InformationCentre  extends ServiceSignsType {
  override val OTHvalue = 310
  override val TRvalue = 78 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(712)
  override val NewLawCode = "G8"
}

case object FirstAid  extends ServiceSignsType {
  override val OTHvalue = 121
  override val TRvalue = 79 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(715)
  override val NewLawCode = "G9"
}

case object BreakdownService extends ServiceSignsType {
  override val OTHvalue = 311
  override val TRvalue = 710 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(721)
  override val NewLawCode = "G10"
}

case object FillingStation  extends ServiceSignsType {
  override val OTHvalue = 122
  override val TRvalue = 71101 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(722)
  override val NewLawCode = "G11.1"
}

case object CompressedNaturalGasStation  extends ServiceSignsType {
  override val OTHvalue = 312
  override val TRvalue = 71102 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "G11.2"
}

case object ChargingStation  extends ServiceSignsType {
  override val OTHvalue = 313
  override val TRvalue = 71103 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "G11.3"
}

case object HydrogenFillingStation  extends ServiceSignsType {
  override val OTHvalue = 314
  override val TRvalue = 71104 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "G11.4"
}

case object HotelOrMotel  extends ServiceSignsType {
  override val OTHvalue = 315
  override val TRvalue = 712 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(723)
  override val NewLawCode = "G12"
}

case object Restaurant  extends ServiceSignsType {
  override val OTHvalue = 123
  override val TRvalue = 713 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(724)
  override val NewLawCode = "G13"
}

case object CafeteriaOrRefreshments  extends ServiceSignsType {
  override val OTHvalue = 316
  override val TRvalue = 714 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(725)
  override val NewLawCode = "G14"
}

case object PublicLavatory  extends ServiceSignsType {
  override val OTHvalue = 124
  override val TRvalue = 715 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(726)
  override val NewLawCode = "G15"
}

case object Hostel  extends ServiceSignsType {
  override val OTHvalue = 317
  override val TRvalue = 716 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(731)
  override val NewLawCode = "G16"
}

case object CampingSite  extends ServiceSignsType {
  override val OTHvalue = 318
  override val TRvalue = 717 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(733)
  override val NewLawCode = "G17"
}

case object CaravanSite  extends ServiceSignsType {
  override val OTHvalue = 319
  override val TRvalue = 718 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(734)
  override val NewLawCode = "G18"
}

case object PicnicSite  extends ServiceSignsType {
  override val OTHvalue = 320
  override val TRvalue = 719 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(741)
  override val NewLawCode = "G19"
}

case object OutingSite  extends ServiceSignsType {
  override val OTHvalue = 321
  override val TRvalue = 720 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(742)
  override val NewLawCode = "G20"
}

case object EmergencyPhone  extends ServiceSignsType {
  override val OTHvalue = 322
  override val TRvalue = 721 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(791)
  override val NewLawCode = "G21"
}

case object Extinguisher extends ServiceSignsType {
  override val OTHvalue = 323
  override val TRvalue = 722 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(792)
  override val NewLawCode = "G22"
}

case object MuseumOrHistoricBuilding extends ServiceSignsType {
  override val OTHvalue = 324
  override val TRvalue = 723 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7721) // The previous way was "772 a"
  override val NewLawCode = "G23"
}

case object WorldHeritageSite extends ServiceSignsType {
  override val OTHvalue = 325
  override val TRvalue = 724 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7723) // The previous way was "772 b"
  override val NewLawCode = "G24"
}

case object NatureSite extends ServiceSignsType {
  override val OTHvalue = 326
  override val TRvalue = 725 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7722) // The previous way was "772 c"
  override val NewLawCode = "G25"
}

case object Viewpoint extends ServiceSignsType {
  override val OTHvalue = 327
  override val TRvalue = 726 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7724) // The previous way was "772 d"
  override val NewLawCode = "G26"
}

case object Zoo extends ServiceSignsType {
  override val OTHvalue = 328
  override val TRvalue = 727 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7725) // The previous way was "772 e"
  override val NewLawCode = "G27"
}

case object OtherTouristAttraction extends ServiceSignsType {
  override val OTHvalue = 329
  override val TRvalue = 728 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7726) // The previous way was "772 f"
  override val NewLawCode = "G28"
}

case object SwimmingPlace extends ServiceSignsType {
  override val OTHvalue = 330
  override val TRvalue = 729 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7731) // The previous way was "773 a"
  override val NewLawCode = "G29"
}

case object FishingPlace extends ServiceSignsType {
  override val OTHvalue = 331
  override val TRvalue = 730 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7732) // The previous way was "773 b"
  override val NewLawCode = "G30"
}

case object SkiLift extends ServiceSignsType {
  override val OTHvalue = 332
  override val TRvalue = 731 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7733) // The previous way was "773 c"
  override val NewLawCode = "G31"
}

case object CrossCountrySkiing extends ServiceSignsType {
  override val OTHvalue = 333
  override val TRvalue = 732 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "G32"
}

case object GolfCourse extends ServiceSignsType {
  override val OTHvalue = 334
  override val TRvalue = 733 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7734) // The previous way was "773 d"
  override val NewLawCode = "G33"
}

case object PleasureOrThemePark extends ServiceSignsType {
  override val OTHvalue = 335
  override val TRvalue = 734 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7735) // The previous way was "773 e"
  override val NewLawCode = "G34"
}

case object CottageAccommodation extends ServiceSignsType {
  override val OTHvalue = 336
  override val TRvalue = 75 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7741) // The previous way was "774 a"
  override val NewLawCode = "G35"
}

case object BedAndBreakfast extends ServiceSignsType {
  override val OTHvalue = 337
  override val TRvalue = 736 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7742) // The previous way was "774 b"
  override val NewLawCode = "G36"
}

case object DirectSale extends ServiceSignsType {
  override val OTHvalue = 338
  override val TRvalue = 737 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7743) // The previous way was "774 c"
  override val NewLawCode = "G37"
}

case object Handicrafts extends ServiceSignsType {
  override val OTHvalue = 339
  override val TRvalue = 738 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7744) // The previous way was "774 d"
  override val NewLawCode = "G38"
}

case object FarmPark extends ServiceSignsType {
  override val OTHvalue = 340
  override val TRvalue = 739 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7745) // The previous way was "774 e"
  override val NewLawCode = "G39"
}

case object HorsebackRiding extends ServiceSignsType {
  override val OTHvalue = 341
  override val TRvalue = 740 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7746) // The previous way was "774 f"
  override val NewLawCode = "G40"
}

case object TouristRouteTextOnly extends ServiceSignsType {
  override val OTHvalue = 342
  override val TRvalue = 74101 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7711) // The previous way was "771 a"
  override val NewLawCode = "G41.1"
}

case object TouristRoute extends ServiceSignsType {
  override val OTHvalue = 343
  override val TRvalue = 74102 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(7712) // The previous way was "771 b"
  override val NewLawCode = "G41.2"
}

case object TemporaryGuidanceSign extends ServiceSignsType {
  override val OTHvalue = 344
  override val TRvalue = 742 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "G42"
}



trait AdditionalPanelsType extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.AdditionalPanels
}

case object SignAppliesToCrossingRoad extends AdditionalPanelsType {
  override val OTHvalue = 345
  override val TRvalue = 81 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(811)
  override val NewLawCode = "H1"
}

case object SignAppliesDirectionOfTheArrow extends AdditionalPanelsType {
  override val OTHvalue = 346
  override val TRvalue = 8201 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(812)
  override val NewLawCode = "H2.1"
}

case object SignAppliesDirectionOfTheArrowWithDistance extends AdditionalPanelsType {
  override val OTHvalue = 347
  override val TRvalue = 8202 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(813)
  override val NewLawCode = "H2.2"
}

case object SignAppliesDirectionOfTheArrowWithDistance2 extends AdditionalPanelsType {
  override val OTHvalue = 348
  override val TRvalue = 8203 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "H2.3"
}

case object DistanceWhichSignApplies extends AdditionalPanelsType {
  override val OTHvalue = 148
  override val TRvalue = 83 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(814)
  override val NewLawCode = "H3"
}

case object DistanceFromSignToPointWhichSignApplies extends AdditionalPanelsType {
  override val OTHvalue = 149
  override val TRvalue = 84 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(815)
  override val NewLawCode = "H4"
}

case object DistanceCompulsoryStop extends AdditionalPanelsType {
  override val OTHvalue = 138
  override val TRvalue = 85 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(816)
  override val NewLawCode = "H5"
}

case object FreeWidth extends AdditionalPanelsType {
  override val OTHvalue = 45
  override val TRvalue = 86 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(821)
  override val NewLawCode = "H6"
}

case object FreeHeight extends AdditionalPanelsType {
  override val OTHvalue = 46
  override val TRvalue = 87 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(822)
  override val NewLawCode = "H7"
}

case object HeightElectricLine extends AdditionalPanelsType {
  override val OTHvalue = 139
  override val TRvalue = 88 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(823)
  override val NewLawCode = "H8"
}

case object SignAppliesBothDirections extends AdditionalPanelsType {
  override val OTHvalue = 140
  override val TRvalue = 8901 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(824)
  override val NewLawCode = "H9.1"
}

case object SignAppliesBothDirectionsVertical extends AdditionalPanelsType {
  override val OTHvalue = 141
  override val TRvalue = 8902 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(825)
  override val NewLawCode = "H9.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object SignAppliesArrowDirections extends AdditionalPanelsType {
  override val OTHvalue = 142
  override val TRvalue = 810 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(826)
  override val NewLawCode = "H10"
}

case object RegulationBeginsFromSign extends AdditionalPanelsType {
  override val OTHvalue = 143
  override val TRvalue = 810 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(827)
  override val NewLawCode = "H10"
}

case object RegulationEndsToTheSign extends AdditionalPanelsType {
  override val OTHvalue = 144
  override val TRvalue = 811 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(828)
  override val NewLawCode = "H11"
}

case object PassengerCar  extends AdditionalPanelsType {
  override val OTHvalue = 52
  override val TRvalue = 81201 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(831)
  override val NewLawCode = "H12.1"
}

case object Bus  extends AdditionalPanelsType {
  override val OTHvalue = 53
  override val TRvalue = 81202 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(832)
  override val NewLawCode = "H12.2"
}

case object Lorry  extends AdditionalPanelsType {
  override val OTHvalue = 54
  override val TRvalue = 81203 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(833)
  override val NewLawCode = "H12.3"
}

case object Van  extends AdditionalPanelsType {
  override val OTHvalue = 55
  override val TRvalue = 81204 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(834)
  override val NewLawCode = "H12.4"
}

case object HusvagnCaravan extends AdditionalPanelsType {
  override val OTHvalue = 150
  override val TRvalue = 81205 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(835)
  override val NewLawCode = "H12.5"
}

case object Motorhome extends AdditionalPanelsType {
  override val OTHvalue = 349
  override val TRvalue = 81206 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "H12.6"
}

case object VehicleForHandicapped  extends AdditionalPanelsType {
  override val OTHvalue = 56
  override val TRvalue = 81207 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(836)
  override val NewLawCode = "H12.7"
}

case object MotorCycle  extends AdditionalPanelsType {
  override val OTHvalue = 57
  override val TRvalue = 81208 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(841)
  override val NewLawCode = "H12.8"
}

case object Moped extends AdditionalPanelsType {
  override val OTHvalue = 151
  override val TRvalue = 81209 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(842)
  override val NewLawCode = "H12.9"
}

case object Cycle extends AdditionalPanelsType {
  override val OTHvalue = 58
  override val TRvalue = 812010 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(843)
  override val NewLawCode = "H12.10"
}

case object MotorSledges extends AdditionalPanelsType {
  override val OTHvalue = 350
  override val TRvalue = 812011 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "H12.11"
}

case object Tractor extends AdditionalPanelsType {
  override val OTHvalue = 351
  override val TRvalue = 812012 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "H12.12"
}

case object LowEmissionVehicle extends AdditionalPanelsType {
  override val OTHvalue = 352
  override val TRvalue = 812013 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "H12.13"
}

case object ParkingOnTopOfCurb extends AdditionalPanelsType {
  override val OTHvalue = 353
  override val TRvalue = 81301 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(845)
  override val NewLawCode = "H13.1"
}

case object ParkingOnTheEdgeOfTheCurb extends AdditionalPanelsType {
  override val OTHvalue = 354
  override val TRvalue = 81302 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(844)
  override val NewLawCode = "H13.2"
}

case object HazmatProhibitionA extends AdditionalPanelsType {
  override val OTHvalue = 47
  override val TRvalue = 814 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(848)
  override val NewLawCode = "H14"
}

case object HazmatProhibitionB extends AdditionalPanelsType {
  override val OTHvalue = 48
  override val TRvalue = 815 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(849)
  override val NewLawCode = "H15"
}

case object TunnelCategory extends AdditionalPanelsType {
  override val OTHvalue = 355
  override val TRvalue = 816 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "H16"
}

case object ValidMonFri extends AdditionalPanelsType {
  override val OTHvalue = 49
  override val TRvalue = 81701 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(851)
  override val NewLawCode = "H17.1"
}

case object ValidSat extends AdditionalPanelsType {
  override val OTHvalue = 50
  override val TRvalue = 81702 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(852)
  override val NewLawCode = "H17.2"
}

case object ValidMultiplePeriod extends AdditionalPanelsType {
  override val OTHvalue = 145
  override val TRvalue = 81703 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(853)
  override val NewLawCode = "H17.3"
}

case object TimeLimit extends AdditionalPanelsType {
  override val OTHvalue = 51
  override val TRvalue = 818 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(854)
  override val NewLawCode = "H18"
}

case object ObligatoryUseOfParkingDisc  extends AdditionalPanelsType {
  override val OTHvalue = 60
  override val TRvalue = 81901 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(8561) // The previous way was "856 a"
  override val NewLawCode = "H19.1"
}

case object ObligatoryUseOfParkingDisc2  extends AdditionalPanelsType {
  override val OTHvalue = 356
  override val TRvalue = 81902 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(8562) // The previous way was "856 b"
  override val NewLawCode = "H19.2"
}

case object ParkingAgainstFee  extends AdditionalPanelsType {
  override val OTHvalue = 59
  override val TRvalue = 820 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(8551) // The previous way was "855 a"
  override val NewLawCode = "H20"
}

case object ParkingAgainstFee2  extends AdditionalPanelsType {
  override val OTHvalue = 357
  override val TRvalue = 820 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(8552) // The previous way was "855 b"
  override val NewLawCode = "H20"
}

case object ChargingSite extends AdditionalPanelsType {
  override val OTHvalue = 358
  override val TRvalue = 821 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "H21"
}

case object DirectionOfPriorityRoad extends AdditionalPanelsType {
  override val OTHvalue = 146
  override val TRvalue = 82201 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(861)
  override val NewLawCode = "H22.1"
}

case object DirectionOfPriorityRoad2 extends AdditionalPanelsType {
  override val OTHvalue = 359
  override val TRvalue = 82201 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(8611) // The previous way was "861 a"
  override val NewLawCode = "H22.1"
}

case object DirectionOfPriorityRoad3 extends AdditionalPanelsType {
  override val OTHvalue = 360
  override val TRvalue = 82202 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(8612) // The previous way was "861 b"
  override val NewLawCode = "H22.2"
}

case object TwoWayBikePath extends AdditionalPanelsType {
  override val OTHvalue = 361
  override val TRvalue = 82301 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(863)
  override val NewLawCode = "H23.1"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object TwoWayBikePath2 extends AdditionalPanelsType {
  override val OTHvalue = 362
  override val TRvalue = 82302 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "H23.2"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object AdditionalPanelWithText  extends AdditionalPanelsType {
  override val OTHvalue = 61
  override val TRvalue = 824 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(871)
  override val NewLawCode = "H24"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object DrivingInServicePurposesAllowed  extends AdditionalPanelsType {
  override val OTHvalue = 62
  override val TRvalue = 825 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(872)
  override val NewLawCode = "H25"
  override def additionalGroup: Option[TrafficSignTypeGroup] = Some(TrafficSignTypeGroup.CycleAndWalkwaySigns)
}

case object EmergencyPhoneAndExtinguisher  extends AdditionalPanelsType {
  override val OTHvalue = 363
  override val TRvalue = 826 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(880)
  override val NewLawCode = "H26"
}

case object CrossingLogTransportRoad extends AdditionalPanelsType {
  override val OTHvalue = 147
  override val TRvalue = 862
  override val OldLawCode: Option[Int] = Some(862)
  override val NewLawCode = "99" //TODO: Deprecated
}



trait OtherSigns extends TrafficSignType {
  def group: TrafficSignTypeGroup = TrafficSignTypeGroup.OtherSigns
}

case object DirectionToAvoidObstacle extends OtherSigns {
  override val OTHvalue = 371
  override val TRvalue = 95 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(9151)
  override val NewLawCode = "I5"
}

case object CurveDirectionSign extends OtherSigns {
  override val OTHvalue = 372
  override val TRvalue = 96 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(916)
  override val NewLawCode = "I6"
}

case object BorderMarkOnTheLeft extends OtherSigns {
  override val OTHvalue = 373
  override val TRvalue = 9701// UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(931)
  override val NewLawCode = "I7.1"
}

case object BorderMarkOnTheRight extends OtherSigns {
  override val OTHvalue = 374
  override val TRvalue = 9702 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(931)
  override val NewLawCode = "I7.2"
}

case object HeightBorder extends OtherSigns {
  override val OTHvalue = 375
  override val TRvalue = 98 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(935)
  override val NewLawCode = "I8"
}

case object UnderpassHeight extends OtherSigns {
  override val OTHvalue = 376
  override val TRvalue = 99 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(941)
  override val NewLawCode = "I9"
}

case object TrafficSignColumn extends OtherSigns {
  override val OTHvalue = 377
  override val TRvalue = 91001 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(932)
  override val NewLawCode = "I10.1"
}

case object TrafficSignColumn2 extends OtherSigns {
  override val OTHvalue = 378
  override val TRvalue = 91002 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "I10.2"
}

case object DivergingRoadSign extends OtherSigns {
  override val OTHvalue = 379
  override val TRvalue = 911 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(911)
  override val NewLawCode = "I11"
}

case object TowAwayZone extends OtherSigns {
  override val OTHvalue = 382
  override val TRvalue = 913 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "I13"
}

case object SOSInformationBoard extends OtherSigns {
  override val OTHvalue = 383
  override val TRvalue = 914 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "I14"
}

case object AutomaticTrafficControl extends OtherSigns {
  override val OTHvalue = 384
  override val TRvalue = 915 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(9901)
  override val NewLawCode = "I15"
}

case object SurveillanceCamera extends OtherSigns {
  override val OTHvalue = 385
  override val TRvalue = 916 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = None // There is no legacy code
  override val NewLawCode = "I16"
}

case object ReindeerHerdingArea extends OtherSigns {
  override val OTHvalue = 386
  override val TRvalue = 91701 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(95123)
  override val NewLawCode = "I17.1"
}

case object ReindeerHerdingAreaWithoutText extends OtherSigns {
  override val OTHvalue = 387
  override val TRvalue = 91702 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(95122)
  override val NewLawCode = "I17.2"
}

case object SpeedLimitInformation extends OtherSigns {
  override val OTHvalue = 388
  override val TRvalue = 918 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(95119)
  override val NewLawCode = "I18"
}

case object CountryBorder extends OtherSigns {
  override val OTHvalue = 389
  override val TRvalue = 919 // UUSIASNR Value in TR
  override val OldLawCode: Option[Int] = Some(9512)
  override val NewLawCode = "I19"
}



sealed trait UrgencyOfRepair {
  def value: Int
  def description: String
}
object UrgencyOfRepair {
  val values = Set(Unknown, VeryUrgent, Urgent, SomehowUrgent, NotUrgent )

  def apply(intValue: Int):UrgencyOfRepair = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: UrgencyOfRepair = Unknown

  case object VeryUrgent extends UrgencyOfRepair { def value = 1; def description = "Erittäin kiireellinen"  }
  case object Urgent extends UrgencyOfRepair { def value = 2; def description = "kiireellinen" }
  case object SomehowUrgent extends UrgencyOfRepair { def value = 3; def description = "Jokseenkin kiireellinen" }
  case object NotUrgent extends UrgencyOfRepair { def value = 4; def description = "Ei kiireellinen" }
  case object Unknown extends UrgencyOfRepair { def value = 99; def description = "Ei tiedossa" }
}

sealed trait Condition {
  def value: Int
  def description: String
}
object Condition {
  val values = Set(Unknown, VeryPoor, Poor, Fair, Good, VeryGood )

  def apply(intValue: Int):Condition = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: Condition = Unknown

  case object VeryPoor extends Condition { def value = 1; def description = "Erittäin huono"  }
  case object Poor extends Condition { def value = 2; def description = "Huono" }
  case object Fair extends Condition { def value = 3; def description = "Tyydyttävä" }
  case object Good extends Condition { def value = 4; def description = "Hyvä" }
  case object VeryGood extends Condition { def value = 5; def description = "Erittäin hyvä" }
  case object Unknown extends Condition { def value = 99; def description = "Ei tiedossa" }
}


sealed trait Size {
  def value: Int
  def description: String
}
object Size {
  val values = Set(Unknown, CompactSign, RegularSign, LargeSign )

  def apply(intValue: Int):Size = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: Size = RegularSign

  case object CompactSign extends Size { def value = 1; def description = "Pienikokoinen merkki"  }
  case object RegularSign extends Size { def value = 2; def description = "Normaalikokoinen merkki" }
  case object LargeSign extends Size { def value = 3; def description = "Suurikokoinen merkki" }
  case object Unknown extends Size { def value = 99; def description = "Ei tiedossa" }
}


sealed trait CoatingType {
  def value: Int
  def description: String
}
object CoatingType {
  val values = Set(Unknown, R1ClassSheeting, R2ClassSheeting, R3ClassSheeting )

  def apply(intValue: Int):CoatingType = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: CoatingType = Unknown

  case object R1ClassSheeting extends CoatingType { def value = 1; def description = "R1-luokan kalvo"  }
  case object R2ClassSheeting extends CoatingType { def value = 2; def description = "R2-luokan kalvo" }
  case object R3ClassSheeting extends CoatingType { def value = 3; def description = "R3-luokan kalvo" }
  case object Unknown extends CoatingType { def value = 99; def description = "Ei tiedossa" }
}


sealed trait SignMaterial {
  def value: Int
  def description: String
}
object SignMaterial {
  val values = Set(Unknown, Plywood, Aluminum, Other )

  def apply(intValue: Int):SignMaterial = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: SignMaterial = Aluminum

  case object Plywood extends SignMaterial { def value = 1; def description = "Vaneri"  }
  case object Aluminum extends SignMaterial { def value = 2; def description = "Alumiini" }
  case object Other extends SignMaterial { def value = 3; def description = "Muu" }
  case object Unknown extends SignMaterial { def value = 99; def description = "Ei tiedossa" }
}


sealed trait LocationSpecifier {
  def value: Int
  def description: String
}
object LocationSpecifier {
  val values = Set(Unknown, RightSideOfRoad, LeftSideOfRoad, AboveLane, TrafficIslandOrTrafficDivider, LengthwiseRelativeToTrafficFlow, OnRoadOrStreetNetwork)

  def apply(intValue: Int):LocationSpecifier = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: LocationSpecifier = Unknown

  case object RightSideOfRoad extends LocationSpecifier { def value = 1; def description = "Väylän oikea puoli"  }
  case object LeftSideOfRoad extends LocationSpecifier { def value = 2; def description = "Väylän vasen puoli" }
  case object AboveLane extends LocationSpecifier { def value = 3; def description = "Kaistan yläpuolella" }
  case object TrafficIslandOrTrafficDivider extends LocationSpecifier { def value = 4; def description = "Keskisaareke tai liikenteenjakaja" }
  case object LengthwiseRelativeToTrafficFlow extends LocationSpecifier { def value = 5; def description = "Pitkittäin ajosuuntaan nähden" }

  /*English description: On road or street network, for example parking area or courtyard*/
  case object OnRoadOrStreetNetwork extends LocationSpecifier { def value = 6; def description = "Tie tai katuverkon ulkopuolella, esimerkiksi parkkialueella tai piha-alueella" }
  case object Unknown extends LocationSpecifier { def value = 99; def description = "Ei tiedossa" }
}


sealed trait TypeOfDamage {
  def value: Int
  def description: String
}
object TypeOfDamage {
  val values = Set(Unknown, Rust, Battered, Paint, OtherDamage)

  def apply(intValue: Int):TypeOfDamage = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: TypeOfDamage = Unknown

  case object Rust extends TypeOfDamage { def value = 1; def description = "Ruostunut"  }
  case object Battered extends TypeOfDamage { def value = 2; def description = "Kolhiintunut" }
  case object Paint extends TypeOfDamage { def value = 3; def description = "Maalaus" }
  case object OtherDamage extends TypeOfDamage { def value = 4; def description = "Muu vaurio" }
  case object Unknown extends TypeOfDamage { def value = 99; def description = "Ei tiedossa" }
}
