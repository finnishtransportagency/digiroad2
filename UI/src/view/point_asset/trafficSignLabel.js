(function(root) {

  root.TrafficSignLabel = function(groupingDistance) {
    SignsLabel.call(this, this.MIN_DISTANCE);
    var me = this;

    me.MIN_DISTANCE = groupingDistance;

    me.getSignType = function (sign) { return sign.type;};

    me.getPropertiesConfiguration = function () {
        return [
          {signValue: [1], image: 'images/traffic-signs/speed-limits/speedLimitSign.png', validation: validateSpeedLimitValues},
          {signValue: [2], image: 'images/traffic-signs/speed-limits/endOfSpeedLimitSign.png', validation: validateSpeedLimitValues, textColor: '#ABABAB'},
          {signValue: [3], image: 'images/traffic-signs/speed-limits/speedLimitZoneSign.png', validation: validateSpeedLimitValues},
          {signValue: [4], image: 'images/traffic-signs/speed-limits/endOfSpeedLimitZoneSign.png', validation: validateSpeedLimitValues, textColor: '#ABABAB'},
          {signValue: [5], image: 'images/traffic-signs/speed-limits/urbanAreaSign.png', height: 30},
          {signValue: [6], image: 'images/traffic-signs/speed-limits/endOfUrbanAreaSign.png', height: 30},
          {signValue: [7], image: 'images/traffic-signs/regulatory-signs/crossingSign.png'},
          {signValue: [8], image: 'images/traffic-signs/maximum-restrictions/maximumLengthSign.png', validation: validateMaximumRestrictions,  convertion: me.convertToMeters, unit: me.addMeters, offsetY: 5},
          {signValue: [9], image: 'images/traffic-signs/general-warning-signs/warningSign.png'},
          {signValue: [10], image: 'images/traffic-signs/prohibitions-and-restrictions/turningRestrictionLeftSign.png'},
          {signValue: [11], image: 'images/traffic-signs/prohibitions-and-restrictions/turningRestrictionRightSign.png'},
          {signValue: [12], image: 'images/traffic-signs/prohibitions-and-restrictions/uTurnRestrictionSign.png'},
          {signValue: [13], image: 'images/traffic-signs/prohibitions-and-restrictions/noVehicles.png'},
          {signValue: [14], image: 'images/traffic-signs/prohibitions-and-restrictions/noPowerDrivenVehiclesSign.png'},
          {signValue: [15], image: 'images/traffic-signs/prohibitions-and-restrictions/noLorriesSign.png'},
          {signValue: [16], image: 'images/traffic-signs/prohibitions-and-restrictions/noVehicleCombinationsSign.png'},
          {signValue: [17], image: 'images/traffic-signs/prohibitions-and-restrictions/noTractorSign.png'},
          {signValue: [18], image: 'images/traffic-signs/prohibitions-and-restrictions/noMotorCycleSign.png'},
          {signValue: [19], image: 'images/traffic-signs/prohibitions-and-restrictions/noMotorSledgesSign.png'},
          {signValue: [20], image: 'images/traffic-signs/prohibitions-and-restrictions/noDangerousGoodsSign.png'},
          {signValue: [21], image: 'images/traffic-signs/prohibitions-and-restrictions/noBusSign.png'},
          {signValue: [22], image: 'images/traffic-signs/prohibitions-and-restrictions/noMopedsSign.png'},
          {signValue: [23], image: 'images/traffic-signs/prohibitions-and-restrictions/noCycleSign.png'},
          {signValue: [24], image: 'images/traffic-signs/prohibitions-and-restrictions/noPedestrianSign.png'},
          {signValue: [25], image: 'images/traffic-signs/prohibitions-and-restrictions/noPedestrianOrCycleSign.png'},
          {signValue: [26], image: 'images/traffic-signs/prohibitions-and-restrictions/noHorsesSign.png'},
          {signValue: [27], image: 'images/traffic-signs/prohibitions-and-restrictions/noEntrySign.png'},
          {signValue: [28], image: 'images/traffic-signs/prohibitions-and-restrictions/overtakingProhibitedSign.png'},
          {signValue: [29], image: 'images/traffic-signs/prohibitions-and-restrictions/endOfOvertakingProhibitionSign.png'},
          {signValue: [30], image: 'images/traffic-signs/maximum-restrictions/maxWidthSign.png', validation: validateMaximumRestrictions, convertion: me.convertToMeters},
          {signValue: [31], image: 'images/traffic-signs/maximum-restrictions/maxHeightSign.png', validation: validateMaximumRestrictions, convertion: me.convertToMeters, unit: me.addMeters},
          {signValue: [32], image: 'images/traffic-signs/maximum-restrictions/totalWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons},
          {signValue: [33], image: 'images/traffic-signs/maximum-restrictions/trailerTruckWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons, offsetY: 5},
          {signValue: [34], image: 'images/traffic-signs/maximum-restrictions/axleWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons, offsetY: -1},
          {signValue: [35], image: 'images/traffic-signs/maximum-restrictions/bogieWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons,  offsetY: -1},
          {signValue: [36], image: 'images/traffic-signs/general-warning-signs/rightBendSign.png'},
          {signValue: [37], image: 'images/traffic-signs/general-warning-signs/leftBendSign.png'},
          {signValue: [38], image: 'images/traffic-signs/general-warning-signs/severalBendRightSign.png'},
          {signValue: [39], image: 'images/traffic-signs/general-warning-signs/severalBendLeftSign.png'},
          {signValue: [40], image: 'images/traffic-signs/general-warning-signs/dangerousDescentSign.png'},
          {signValue: [41], image: 'images/traffic-signs/general-warning-signs/steepAscentSign.png'},
          {signValue: [42], image: 'images/traffic-signs/general-warning-signs/unevenRoadSign.png'},
          {signValue: [43], image: 'images/traffic-signs/general-warning-signs/childrenSign.png'},
          {signValue: [45], image: 'images/traffic-signs/additional-panels/freeWidthSign.png', validation: validateAdditionalInfo, maxLabelLength: 11, additionalInfo: showAdditionalInfo, unit: me.addMeters, offsetX: 2, height: 30},
          {signValue: [46], image: 'images/traffic-signs/additional-panels/freeHeight.png', validation: validateAdditionalInfo, maxLabelLength: 10, additionalInfo: showAdditionalInfo, unit: me.addMeters, offsetX: 1, height: 40},
          {signValue: [47], image: 'images/traffic-signs/additional-panels/hazmatProhibitionA.png', height: 27},
          {signValue: [48], image: 'images/traffic-signs/additional-panels/hazmatProhibitionB.png', height: 27},
          {signValue: [49], image: 'images/traffic-signs/additional-panels/validPeriod.png', validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showPeriodTimeAdditionalInfo, height: 20},
          {signValue: [50], image: 'images/traffic-signs/additional-panels/validPeriod.png', validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showPeriodTimeAdditionalInfo, height: 20, convertion: me.convertToSaturdayDatePeriod},
          {signValue: [51], image: 'images/traffic-signs/additional-panels/validPeriod.png', validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showPeriodTimeAdditionalInfo, unit: me.addMinutes, height: 20},
          {signValue: [52], image: 'images/traffic-signs/additional-panels/passengerCar.png', height: 20},
          {signValue: [53], image: 'images/traffic-signs/additional-panels/bus.png', height: 20},
          {signValue: [54], image: 'images/traffic-signs/additional-panels/lorry.png', height: 20},
          {signValue: [55], image: 'images/traffic-signs/additional-panels/van.png', height: 20},
          {signValue: [56], image: 'images/traffic-signs/additional-panels/vehicleForHandicapped.png', height: 20},
          {signValue: [57], image: 'images/traffic-signs/additional-panels/motorCycle.png', height: 20},
          {signValue: [58], image: 'images/traffic-signs/additional-panels/cycle.png', height: 20},
          {signValue: [59], image: 'images/traffic-signs/additional-panels/parkingAgainstFee.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showPeriodDayAdditionalInfo, offsetX: 12, height: 40},
          {signValue: [60], image: 'images/traffic-signs/additional-panels/obligatoryUseOfParkingDisc.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showHourMinAdditionalInfo, unit: me.addMinutes, offsetX: 12, height: 33},
          {signValue: [61], image: 'images/traffic-signs/additional-panels/additionalPanelWithText.png', validation: validateAdditionalInfo, maxLabelLength: 19, additionalInfo: showAdditionalInfo, offsetX: 3, height: 25},
          {signValue: [62], image: 'images/traffic-signs/additional-panels/drivingInServicePurposesAllowed.png', height: 25},
          {signValue: [63], image: 'images/traffic-signs/regulatory-signs/busLane.png'},
          {signValue: [64], image: 'images/traffic-signs/regulatory-signs/busLaneEnds.png'},
          {signValue: [65], image: 'images/traffic-signs/regulatory-signs/tramLane.png'},
          {signValue: [66], image: 'images/traffic-signs/regulatory-signs/busStopForLocalTraffic.png'},
          {signValue: [68], image: 'images/traffic-signs/regulatory-signs/tramStop.png'},
          {signValue: [69], image: 'images/traffic-signs/regulatory-signs/taxiStation.png'},
          {signValue: [70], image: 'images/traffic-signs/mandatory-signs/compulsoryFootPath.png'},
          {signValue: [71], image: 'images/traffic-signs/mandatory-signs/compulsoryCycleTrack.png'},
          {signValue: [72], image: 'images/traffic-signs/mandatory-signs/combinedCycleTrackAndFootPath.png'},
          {signValue: [74], image: 'images/traffic-signs/mandatory-signs/directionToBeFollowed3.png'},
          {signValue: [77], image: 'images/traffic-signs/mandatory-signs/compulsoryRoundabout.png'},
          {signValue: [78], image: 'images/traffic-signs/mandatory-signs/passThisSide.png'},
          {signValue: [80], image: 'images/traffic-signs/prohibitions-and-restrictions/taxiStationZoneBeginning.png'},
          {signValue: [81], image: 'images/traffic-signs/prohibitions-and-restrictions/standingPlaceForTaxi.png'},
          {signValue: [82], image: 'images/traffic-signs/general-warning-signs/roadNarrows.png'},
          {signValue: [83], image: 'images/traffic-signs/general-warning-signs/twoWayTraffic.png'},
          {signValue: [84], image: 'images/traffic-signs/general-warning-signs/swingBridge.png'},
          {signValue: [85], image: 'images/traffic-signs/general-warning-signs/roadWorks.png'},
          {signValue: [86], image: 'images/traffic-signs/general-warning-signs/slipperyRoad.png'},
          {signValue: [87], image: 'images/traffic-signs/general-warning-signs/pedestrianCrossingWarningSign.png'},
          {signValue: [88], image: 'images/traffic-signs/general-warning-signs/cyclists.png'},
          {signValue: [89], image: 'images/traffic-signs/general-warning-signs/intersectionWithEqualRoads.png'},
          {signValue: [90], image: 'images/traffic-signs/general-warning-signs/lightSignals.png'},
          {signValue: [91], image: 'images/traffic-signs/general-warning-signs/tramwayLine.png'},
          {signValue: [92], image: 'images/traffic-signs/general-warning-signs/fallingRocks.png'},
          {signValue: [93], image: 'images/traffic-signs/general-warning-signs/crossWind.png'},
          {signValue: [94], image: 'images/traffic-signs/priority-and-give-way-signs/priorityRoad.png'},
          {signValue: [95], image: 'images/traffic-signs/priority-and-give-way-signs/endOfPriority.png'},
          {signValue: [96], image: 'images/traffic-signs/priority-and-give-way-signs/priorityOverOncomingTraffic.png'},
          {signValue: [97], image: 'images/traffic-signs/priority-and-give-way-signs/priorityForOncomingTraffic.png'},
          {signValue: [98], image: 'images/traffic-signs/priority-and-give-way-signs/giveWay.png'},
          {signValue: [99], image: 'images/traffic-signs/priority-and-give-way-signs/stop.png'},
          {signValue: [100], image: 'images/traffic-signs/prohibitions-and-restrictions/standingAndParkingProhibited.png'},
          {signValue: [101], image: 'images/traffic-signs/prohibitions-and-restrictions/parkingProhibited.png'},
          {signValue: [102], image: 'images/traffic-signs/prohibitions-and-restrictions/parkingProhibitedZone.png'},
          {signValue: [103], image: 'images/traffic-signs/prohibitions-and-restrictions/endOfParkingProhibitedZone.png'},
          {signValue: [104], image: 'images/traffic-signs/prohibitions-and-restrictions/alternativeParkingOddDays.png'},
          {signValue: [105], image: 'images/traffic-signs/regulatory-signs/parkingLot.png'},
          {signValue: [106], image: 'images/traffic-signs/regulatory-signs/oneWayRoad.png'},
          {signValue: [107], image: 'images/traffic-signs/regulatory-signs/motorway.png', height: 40},
          {signValue: [108], image: 'images/traffic-signs/regulatory-signs/motorwayEnds.png', height: 40},
          {signValue: [109], image: 'images/traffic-signs/regulatory-signs/residentialZone.png'},
          {signValue: [110], image: 'images/traffic-signs/regulatory-signs/endOfResidentialZone.png'},
          {signValue: [111], image: 'images/traffic-signs/regulatory-signs/pedestrianZone.png'},
          {signValue: [112], image: 'images/traffic-signs/regulatory-signs/endOfPedestrianZone.png'},
          {signValue: [113], image: 'images/traffic-signs/information-signs/noThroughRoad.png'},
          {signValue: [114], image: 'images/traffic-signs/information-signs/noThroughRoadRight.png'},
          {signValue: [115], image: 'images/traffic-signs/information-signs/symbolOfMotorway.png'},
          {signValue: [116], image: 'images/traffic-signs/information-signs/parking.png'},
          {signValue: [117], image: 'images/traffic-signs/information-signs/itineraryForIndicatedVehicleCategory.png'},
          {signValue: [118], image: 'images/traffic-signs/information-signs/itineraryForPedestrians.png'},
          {signValue: [119], image: 'images/traffic-signs/information-signs/itineraryForHandicapped.png'},
          {signValue: [120], image: 'images/traffic-signs/service-signs/locationSignForTouristService.png', height: 25},
          {signValue: [121], image: 'images/traffic-signs/service-signs/firstAid.png'},
          {signValue: [122], image: 'images/traffic-signs/service-signs/fillingStation.png'},
          {signValue: [123], image: 'images/traffic-signs/service-signs/restaurant.png'},
          {signValue: [124], image: 'images/traffic-signs/service-signs/publicLavatory.png'},
          {signValue: [125], image: 'images/traffic-signs/general-warning-signs/Moose.png'},
          {signValue: [126], image: 'images/traffic-signs/general-warning-signs/Reiner.png'},
          {signValue: [127], image: 'images/traffic-signs/general-warning-signs/IntersectionWithMinorRoad.png'},
          {signValue: [128], image: 'images/traffic-signs/general-warning-signs/IntersectionWithOneMinorRoad.png'},
          {signValue: [129], image: 'images/traffic-signs/general-warning-signs/IntersectionWithOneCrossMinorRoad.png'},
          {signValue: [130], image: 'images/traffic-signs/general-warning-signs/LevelCrossingWithoutGate.png'},
          {signValue: [131], image: 'images/traffic-signs/general-warning-signs/LevelCrossingWithGates.png'},
          {signValue: [132], image: 'images/traffic-signs/general-warning-signs/LevelCrossingWithOneTrack.png'},
          {signValue: [133], image: 'images/traffic-signs/general-warning-signs/LevelCrossingWithManyTracks.png'},
          {signValue: [134], image: 'images/traffic-signs/prohibitions-and-restrictions/AlternativeParkingEvenDays.png'},
          {signValue: [135], image: 'images/traffic-signs/mandatory-signs/CompulsoryTrackMotorSledges.png'},
          {signValue: [136], image: 'images/traffic-signs/mandatory-signs/CompulsoryTrackRidersHorseback.png'},
          {signValue: [137], image: 'images/traffic-signs/regulatory-signs/parkingLotAndAccessToTrain.png'},
          {signValue: [138], image: 'images/traffic-signs/additional-panels/DistanceCompulsoryStop.png', height: 40, validation: validateAdditionalInfo, maxLabelLength: 15, offsetY: 10, unit: me.addMeters},
          {signValue: [139], image: 'images/traffic-signs/additional-panels/HeightElectricLine.png', height: 40, validation: validateAdditionalInfo, maxLabelLength: 15, offsetX: 5, unit: me.addMeters},
          {signValue: [140], image: 'images/traffic-signs/additional-panels/SignAppliesBothDirections.png'},
          {signValue: [141], image: 'images/traffic-signs/additional-panels/SignAppliesBothDirectionsVertical.png'},
          {signValue: [142], image: 'images/traffic-signs/additional-panels/SignAppliesArrowDirections.png'},
          {signValue: [143], image: 'images/traffic-signs/additional-panels/RegulationBeginsFromSign.png'},
          {signValue: [144], image: 'images/traffic-signs/additional-panels/RegulationEndsToTheSign.png'},
          {signValue: [145], image: 'images/traffic-signs/additional-panels/validPeriod.png', height: 20, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showPeriodTimeAdditionalInfo,  textColor: '#ff0000'},
          {signValue: [146], image: 'images/traffic-signs/additional-panels/DirectionOfPriorityRoad.png'},
          {signValue: [147], image: 'images/traffic-signs/additional-panels/CrossingLogTransportRoad.png', height: 20},
          {signValue: [148], image: 'images/traffic-signs/additional-panels/DistanceWhichSignApplies.png', height: 35, validation: validateAdditionalInfo, maxLabelLength: 5, offsetY: 3, offsetX: 2, additionalInfo: showAdditionalInfo, unit: me.addKilometers},
          {signValue: [149], image: 'images/traffic-signs/additional-panels/DistanceFromSignToPointWhichSignApplies.png',  height: 35, validation: validateAdditionalInfo, maxLabelLength: 5, offsetY: 3, offsetX: 2, additionalInfo: showAdditionalInfo, unit: me.addMeters},
          {signValue: [150], image: 'images/traffic-signs/additional-panels/HusvagnCaravan.png', height: 20},
          {signValue: [151], image: 'images/traffic-signs/additional-panels/Moped.png', height: 20},
          {signValue: [152], image: 'images/traffic-signs/information-signs/AdvisorySignDetourLarge.png', height: 55},
          {signValue: [153], image: 'images/traffic-signs/information-signs/Detour.png'},
          {signValue: [154], image: 'images/traffic-signs/information-signs/RouteToBeFollowed.png', height: 35},
          {signValue: [155], image: 'images/traffic-signs/information-signs/InformationOnTrafficLanes.png'},
          {signValue: [156], image: 'images/traffic-signs/information-signs/BiDirectionalInformationOnTrafficLanes.png'},
          {signValue: [157], image: 'images/traffic-signs/information-signs/EndOfLane.png', height: 52},
          {signValue: [158], image: 'images/traffic-signs/information-signs/AdvanceDirectionSignAbove.png', height: 63, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, offsetY: -8, textColor: '#ffffff'},
          {signValue: [159], image: 'images/traffic-signs/information-signs/ExitSignAbove.png', height: 34, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [160], image: 'images/traffic-signs/information-signs/DirectionSign.png', height: 24, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [161], image: 'images/traffic-signs/information-signs/ExitSign.png', height: 34, validation: validateAdditionalInfo, maxLabelLength: 13, additionalInfo: showAdditionalInfo, textColor: '#ffffff', offsetX: -20},
          {signValue: [162], image: 'images/traffic-signs/information-signs/DirectionSignOnPrivateRoad.png', height: 24, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [163], image: 'images/traffic-signs/information-signs/LocationSign.png', height: 30, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [164], image: 'images/traffic-signs/information-signs/DirectionSignForLightTraffic.png', height: 24, validation: validateAdditionalInfo, maxLabelLength: 17, additionalInfo: showAdditionalInfo, textColor: '#ffffff', offsetX: 20},
          {signValue: [165], image: 'images/traffic-signs/information-signs/DirectionSignForDetourWithText.png', height: 20, validation: validateAdditionalInfo, maxLabelLength: 10, additionalInfo: showAdditionalInfo},
          {signValue: [166], image: 'images/traffic-signs/information-signs/DirectionSignForDetour.png', height: 20},
          {signValue: [167], image: 'images/traffic-signs/information-signs/DirectionSignForLocalPurposes.png', height: 28, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo},
          {signValue: [168], image: 'images/traffic-signs/information-signs/DirectionSignForMotorway.png', height: 25, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [169], image: 'images/traffic-signs/information-signs/accessParkingAndTrainSign.png', height: 20},
          {signValue: [170], image: 'images/traffic-signs/information-signs/RecommendedMaxSpeed.png', validation: validateAdditionalInfo, maxLabelLength: 3, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [171], image: 'images/traffic-signs/information-signs/SignShowingDistance.png', height: 34, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [172], image: 'images/traffic-signs/information-signs/PlaceName.png', height: 20, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [173], image: 'images/traffic-signs/information-signs/RoadNumberERoad.png', height: 23, validation: validateAdditionalInfo, maxLabelLength: 5, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [174], image: 'images/traffic-signs/information-signs/DirectionToTheNumberedRoad.png', validation: validateNumber, maxLabelLength: 5, textColor: '#ffffff'},
          {signValue: [175], image: 'images/traffic-signs/information-signs/RoadNumberPrimaryRoad.png', textColor: '#ffffff',  validation: validateNumber, maxLabelLength: 4 },
          {signValue: [176], image: 'images/traffic-signs/information-signs/RoadNumberRegionalOrSecondaryRoad.png', height: 25, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo},
          {signValue: [177], image: 'images/traffic-signs/information-signs/RoadNumberOtherRoad.png', height: 26, validation: validateAdditionalInfo, maxLabelLength: 8, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [178], image: 'images/traffic-signs/information-signs/AdvanceDirectionSignSmall.png', height: 24, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo, offsetX: -12, textColor: '#ffffff'},
          {signValue: [179], image: 'images/traffic-signs/information-signs/RoadForMotorVehicles.png'},
          {signValue: [180], image: 'images/traffic-signs/information-signs/Airport.png'},
          {signValue: [181], image: 'images/traffic-signs/information-signs/Ferry.png'},
          {signValue: [182], image: 'images/traffic-signs/information-signs/GoodsHarbour.png'},
          {signValue: [183], image: 'images/traffic-signs/information-signs/IndustrialArea.png'},
          {signValue: [184], image: 'images/traffic-signs/information-signs/RailwayStation.png'},
          {signValue: [185], image: 'images/traffic-signs/information-signs/BusStation.png'},
          {signValue: [186], image: 'images/traffic-signs/information-signs/ItineraryForDangerousGoodsTransport.png'},
          {signValue: [187], image: 'images/traffic-signs/information-signs/OverpassOrUnderpassWithSteps.png'},
          {signValue: [188], image: 'images/traffic-signs/information-signs/OverpassOrUnderpassWithoutSteps.png'},
          {signValue: [189], image: 'images/traffic-signs/information-signs/EmergencyExit.png'},
          {signValue: [190], image: 'images/traffic-signs/information-signs/DirectionToEmergencyExit.png', validation: validateNumber, maxLabelLength: 5, offsetX: 12, offsetY: 10, textColor: '#ffffff', unit: me.addMeters},
          {signValue: [191], image: 'images/traffic-signs/information-signs/AdvanceDirectionSignAboveSmall.png', height: 20, validation: validateNumber, maxLabelLength: 4, offsetX: -10, additionalInfo: showAdditionalInfo, textColor: '#ffffff'},
          {signValue: [192], image: 'images/traffic-signs/information-signs/AdvanceDirectionSign.png', height: 58},
          {signValue: [193], image: 'images/traffic-signs/information-signs/AdvisorySignDetour.png', height: 26, validation: validateAdditionalInfo, maxLabelLength: 15, offsetX: 15, additionalInfo: showAdditionalInfo},
          {signValue: [200], image: 'images/traffic-signs/general-warning-signs/EndInPierOrCliff.png' },
          {signValue: [201], image: 'images/traffic-signs/general-warning-signs/TrafficJam.png' },
          {signValue: [202], image: 'images/traffic-signs/general-warning-signs/Bumps.png' },
          {signValue: [203], image: 'images/traffic-signs/general-warning-signs/LooseStones.png' },
          {signValue: [204], image: 'images/traffic-signs/general-warning-signs/DangerousRoadSide.png' },
          {signValue: [205], image: 'images/traffic-signs/general-warning-signs/Pedestrians.png' },
          {signValue: [206], image: 'images/traffic-signs/general-warning-signs/CrossCountrySkiing.png'},
          {signValue: [207], image: 'images/traffic-signs/general-warning-signs/WildAnimals.png' },
          {signValue: [208], image: 'images/traffic-signs/general-warning-signs/IntersectionWithTwoMinorRoads.png'},
          {signValue: [209], image: 'images/traffic-signs/general-warning-signs/Roundabout.png' },
          {signValue: [210], image: 'images/traffic-signs/general-warning-signs/ApproachLevelCrossingThreeStrips.png' },
          {signValue: [211], image: 'images/traffic-signs/general-warning-signs/ApproachLevelCrossingTwoStrips.png' },
          {signValue: [212], image: 'images/traffic-signs/general-warning-signs/ApproachLevelCrossingOneStrip.png'},
          {signValue: [213], image: 'images/traffic-signs/general-warning-signs/LowFlyingPlanes.png' },
          {signValue: [214], image: 'images/traffic-signs/priority-and-give-way-signs/priorityForCyclistsCrossing.png' },
          {signValue: [215], image: 'images/traffic-signs/prohibitions-and-restrictions/NoCyclists.png' },
          {signValue: [216], image: 'images/traffic-signs/prohibitions-and-restrictions/NoCyclistsOrPedestrians.png' },
          {signValue: [217], image: 'images/traffic-signs/prohibitions-and-restrictions/OvertakingProhibitedByTruck.png' },
          {signValue: [218], image: 'images/traffic-signs/prohibitions-and-restrictions/EndProhibitionOfOvertakingByTruck.png' },
          {signValue: [219], image: 'images/traffic-signs/prohibitions-and-restrictions/ProhibitionOrRegulationPerLane.png' },
          {signValue: [220], image: 'images/traffic-signs/prohibitions-and-restrictions/LoadingPlace.png' },
          {signValue: [221], image: 'images/traffic-signs/prohibitions-and-restrictions/CustomsControl.png' },
          {signValue: [222], image: 'images/traffic-signs/prohibitions-and-restrictions/MandatoryStopForInspection.png' },
          {signValue: [223], image: 'images/traffic-signs/prohibitions-and-restrictions/MinimumDistanceBetweenVehicles.png' },
          {signValue: [224], image: 'images/traffic-signs/prohibitions-and-restrictions/NoStuddedTires.png' },
          {signValue: [225], image: 'images/traffic-signs/mandatory-signs/RightDirection.png' },
          {signValue: [226], image: 'images/traffic-signs/mandatory-signs/LeftDirection.png' },
          {signValue: [227], image: 'images/traffic-signs/mandatory-signs/StraightDirection.png' },
          {signValue: [228], image: 'images/traffic-signs/mandatory-signs/TurnLeft.png' },
          {signValue: [229], image: 'images/traffic-signs/mandatory-signs/StraightDirectionOrRightTurn.png' },
          {signValue: [230], image: 'images/traffic-signs/mandatory-signs/StraightDirectionOrLeftTurn.png' },
          {signValue: [231], image: 'images/traffic-signs/mandatory-signs/LeftTurnOrRightTurn.png' },
          {signValue: [232], image: 'images/traffic-signs/mandatory-signs/StraightDirectionOrRightOrLeftTurn.png' },
          {signValue: [233], image: 'images/traffic-signs/mandatory-signs/PassLeftSide.png' },
          {signValue: [234], image: 'images/traffic-signs/mandatory-signs/DividerOfTraffic.png' },
          {signValue: [235], image: 'images/traffic-signs/mandatory-signs/ParallelCycleTrackAndFootPath.png' },
          {signValue: [236], image: 'images/traffic-signs/mandatory-signs/ParallelCycleTrackAndFootPath2.png' },
          {signValue: [237], image: 'images/traffic-signs/mandatory-signs/MinimumSpeed.png', validation: validateSpeedLimitValues, textColor: '#ffffff' },
          {signValue: [238], image: 'images/traffic-signs/mandatory-signs/EndMinimumSpeed.png', validation: validateSpeedLimitValues, textColor: '#ffffff' },
          {signValue: [239], image: 'images/traffic-signs/regulatory-signs/parkingLotAndAccessToBus.png' },
          {signValue: [240], image: 'images/traffic-signs/regulatory-signs/parkingLotAndAccessToTram.png' },
          {signValue: [241], image: 'images/traffic-signs/regulatory-signs/parkingLotAndAccessToSubway.png' },
          {signValue: [242], image: 'images/traffic-signs/regulatory-signs/parkingLotAndAccessToPublicTransport.png' },
          {signValue: [243], image: 'images/traffic-signs/regulatory-signs/parkingDirectly.png' },
          {signValue: [244], image: 'images/traffic-signs/regulatory-signs/parkingOppositeEachOther.png' },
          {signValue: [245], image: 'images/traffic-signs/regulatory-signs/positioningAtAnAngle.png' },
          {signValue: [246], image: 'images/traffic-signs/regulatory-signs/roadDirection.png' },
          {signValue: [247], image: 'images/traffic-signs/regulatory-signs/busStopForLongDistanceTraffic.png' },
          {signValue: [248], image: 'images/traffic-signs/regulatory-signs/busAndTaxiLane.png' },
          {signValue: [249], image: 'images/traffic-signs/regulatory-signs/busAndTaxiLaneEnds.png' },
          {signValue: [250], image: 'images/traffic-signs/regulatory-signs/tramAndTaxiLane.png' },
          {signValue: [251], image: 'images/traffic-signs/regulatory-signs/tramLaneEnds.png' },
          {signValue: [252], image: 'images/traffic-signs/regulatory-signs/tramAndTaxiLaneEnds.png' },
          {signValue: [253], image: 'images/traffic-signs/regulatory-signs/bicycleLaneOnTheRight.png' },
          {signValue: [254], image: 'images/traffic-signs/regulatory-signs/bicycleLaneInTheMiddle.png' },
          {signValue: [255], image: 'images/traffic-signs/regulatory-signs/oneWayRoadLeftRight.png' },
          {signValue: [256], image: 'images/traffic-signs/regulatory-signs/expresswaySign.png' },
          {signValue: [257], image: 'images/traffic-signs/regulatory-signs/expresswayEnds.png' },
          {signValue: [258], image: 'images/traffic-signs/regulatory-signs/tunnelSign.png' },
          {signValue: [259], image: 'images/traffic-signs/regulatory-signs/tunnelEnds.png' },
          {signValue: [260], image: 'images/traffic-signs/regulatory-signs/SOSZoneSign.png' },
          {signValue: [261], image: 'images/traffic-signs/regulatory-signs/bicycleStreet.png' },
          {signValue: [262], image: 'images/traffic-signs/regulatory-signs/bicycleStreetEnds.png' },
          {signValue: [263], image: 'images/traffic-signs/regulatory-signs/laneMerge.png' },
          {signValue: [264], image: 'images/traffic-signs/information-signs/advanceDirectionSign2.png' },
          {signValue: [265], image: 'images/traffic-signs/information-signs/advanceDirectionSign3.png' },
          {signValue: [266], image: 'images/traffic-signs/information-signs/advanceDirectionSignSmall2.png' },
          {signValue: [267], image: 'images/traffic-signs/information-signs/advanceDirectionSignSmall3.png' },
          {signValue: [268], image: 'images/traffic-signs/information-signs/laneSpecificNavigationBoard.png' },
          {signValue: [269], image: 'images/traffic-signs/information-signs/trafficLanesWithSeparator.png' },
          {signValue: [270], image: 'images/traffic-signs/information-signs/increasedLaneNumber.png' },
          {signValue: [271], image: 'images/traffic-signs/information-signs/newLaneIncoming.png' },
          {signValue: [272], image: 'images/traffic-signs/information-signs/newLaneIncoming2.png' },
          {signValue: [273], image: 'images/traffic-signs/information-signs/compilationSign.png' },
          {signValue: [274], image: 'images/traffic-signs/information-signs/DirectionSignForDetour.png' },
          {signValue: [275], image: 'images/traffic-signs/information-signs/advanceLocationSign.png', height: 30, validation: validateAdditionalInfo, maxLabelLength: 15, offsetY: -6, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [276], image: 'images/traffic-signs/information-signs/accessParkingAndBusSign.png', height: 20 },
          {signValue: [277], image: 'images/traffic-signs/information-signs/accessParkingAndTramSign.png', height: 20 },
          {signValue: [278], image: 'images/traffic-signs/information-signs/accessParkingAndSubwaySign.png', height: 20 },
          {signValue: [279], image: 'images/traffic-signs/information-signs/accessParkingAndPublicTransportsSign.png', height: 20, validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo },
          {signValue: [280], image: 'images/traffic-signs/information-signs/directionSignForCyclistsWithoutDistances.png', height: 20, validation: validateAdditionalInfo, maxLabelLength: 10, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [281], image: 'images/traffic-signs/information-signs/directionSignForCyclistsWithDistances.png', height: 20, validation: validateAdditionalInfo, maxLabelLength: 10, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [282], image: 'images/traffic-signs/information-signs/advanceDirectionSignForCyclistsWithDistances.png', height: 60 },
          {signValue: [283], image: 'images/traffic-signs/information-signs/advanceDirectionSignForCyclistsWithoutDistances.png', height: 50 },
          {signValue: [284], image: 'images/traffic-signs/information-signs/distanceBoardForCyclists.png', height: 60 },
          {signValue: [285], image: 'images/traffic-signs/information-signs/placeNameForCyclists.png', height: 50, validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 13, additionalInfo: showAdditionalInfo, textColor: '#ffffff'  },
          {signValue: [286], image: 'images/traffic-signs/information-signs/noThroughRoadCyclists.png' },
          {signValue: [287], image: 'images/traffic-signs/information-signs/PlaceName.png' },
          {signValue: [288], image: 'images/traffic-signs/information-signs/PlaceName.png' },
          {signValue: [289], image: 'images/traffic-signs/information-signs/riverName.png', height: 20, validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 10, offsetX: 10, additionalInfo: showAdditionalInfo, textColor: '#ffffff'  },
          {signValue: [290], image: 'images/traffic-signs/information-signs/roadNumberOrdinaryRoad.png', validation: validateAdditionalInfo, maxLabelLength: 1, offsetY: 2, offsetX: 18, additionalInfo: showAdditionalInfo },
          {signValue: [291], image: 'images/traffic-signs/information-signs/exitNumber.png', height: 20, validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 5, offsetX: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [292], image: 'images/traffic-signs/information-signs/directionToTheNumberedPrimaryRoad.png', validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 2, offsetX: 2, additionalInfo: showAdditionalInfo, textColor: '#4169E1' },
          {signValue: [293], image: 'images/traffic-signs/information-signs/boat.png' },
          {signValue: [294], image: 'images/traffic-signs/information-signs/cargoTerminal.png' },
          {signValue: [295], image: 'images/traffic-signs/information-signs/largeRetailUnit.png' },
          {signValue: [296], image: 'images/traffic-signs/information-signs/parkingCovered.png' },
          {signValue: [297], image: 'images/traffic-signs/information-signs/center.png' },
          {signValue: [298], image: 'images/traffic-signs/information-signs/overpassWithSteps.png' },
          {signValue: [299], image: 'images/traffic-signs/information-signs/overpassWithoutSteps.png' },
          {signValue: [300], image: 'images/traffic-signs/information-signs/underpassForWheelchair.png' },
          {signValue: [301], image: 'images/traffic-signs/information-signs/overpassForWheelchair.png' },
          {signValue: [302], image: 'images/traffic-signs/information-signs/emergencyExitOnTheRight.png' },
          {signValue: [303], image: 'images/traffic-signs/information-signs/multipleExitRoute.png' },
          {signValue: [304], image: 'images/traffic-signs/service-signs/informationSignForServices.png', height: 40, validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 20, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [305], image: 'images/traffic-signs/service-signs/informationSignForServices2.png', height: 30, validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 12, offsetX: 15, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [306], image: 'images/traffic-signs/service-signs/advanceInformationSignForServices.png', validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 12, offsetX: -15, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [307], image: 'images/traffic-signs/service-signs/advanceLocationSignForTouristService.png', validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: -7, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [308], image: 'images/traffic-signs/service-signs/radioStationFrequency.png', height: 49, validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 16, offsetX: 2, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [309], image: 'images/traffic-signs/service-signs/informationPoint.png' },
          {signValue: [310], image: 'images/traffic-signs/service-signs/informationCentre.png' },
          {signValue: [311], image: 'images/traffic-signs/service-signs/breakdownService.png' },
          {signValue: [312], image: 'images/traffic-signs/service-signs/compressedNaturalGasStation.png' },
          {signValue: [313], image: 'images/traffic-signs/service-signs/chargingStation.png' },
          {signValue: [314], image: 'images/traffic-signs/service-signs/hydrogenFillingStation.png' },
          {signValue: [315], image: 'images/traffic-signs/service-signs/hotelOrMotel.png' },
          {signValue: [316], image: 'images/traffic-signs/service-signs/cafeteriaOrRefreshments.png' },
          {signValue: [317], image: 'images/traffic-signs/service-signs/hostel.png' },
          {signValue: [318], image: 'images/traffic-signs/service-signs/campingSite.png' },
          {signValue: [319], image: 'images/traffic-signs/service-signs/caravanSite.png' },
          {signValue: [320], image: 'images/traffic-signs/service-signs/picnicSite.png' },
          {signValue: [321], image: 'images/traffic-signs/service-signs/outingSite.png' },
          {signValue: [322], image: 'images/traffic-signs/service-signs/emergencyPhone.png' },
          {signValue: [323], image: 'images/traffic-signs/service-signs/extinguisher.png' },
          {signValue: [324], image: 'images/traffic-signs/service-signs/museumOrHistoricBuilding.png' },
          {signValue: [325], image: 'images/traffic-signs/service-signs/worldHeritageSite.png' },
          {signValue: [326], image: 'images/traffic-signs/service-signs/natureSite.png' },
          {signValue: [327], image: 'images/traffic-signs/service-signs/viewpoint.png' },
          {signValue: [328], image: 'images/traffic-signs/service-signs/zoo.png' },
          {signValue: [329], image: 'images/traffic-signs/service-signs/otherTouristAttraction.png' },
          {signValue: [330], image: 'images/traffic-signs/service-signs/swimmingPlace.png' },
          {signValue: [331], image: 'images/traffic-signs/service-signs/fishingPlace.png' },
          {signValue: [332], image: 'images/traffic-signs/service-signs/skiLift.png' },
          {signValue: [333], image: 'images/traffic-signs/service-signs/crossCountrySkiing.png' },
          {signValue: [334], image: 'images/traffic-signs/service-signs/golfCourse.png' },
          {signValue: [335], image: 'images/traffic-signs/service-signs/pleasureOrThemePark.png' },
          {signValue: [336], image: 'images/traffic-signs/service-signs/cottageAccommodation.png' },
          {signValue: [337], image: 'images/traffic-signs/service-signs/bedAndBreakfast.png' },
          {signValue: [338], image: 'images/traffic-signs/service-signs/directSale.png' },
          {signValue: [339], image: 'images/traffic-signs/service-signs/handicrafts.png' },
          {signValue: [340], image: 'images/traffic-signs/service-signs/farmPark.png' },
          {signValue: [341], image: 'images/traffic-signs/service-signs/horsebackRiding.png' },
          {signValue: [342], image: 'images/traffic-signs/service-signs/touristRouteTextOnly.png', validation: validateAdditionalInfo, maxLabelLength: 10, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [343], image: 'images/traffic-signs/service-signs/touristRoute.png', validation: validateAdditionalInfo, maxLabelLength: 10, offsetY: 22, additionalInfo: showAdditionalInfo, textColor: '#ffffff' },
          {signValue: [344], image: 'images/traffic-signs/service-signs/temporaryGuidanceSign.png' },
          {signValue: [345], image: 'images/traffic-signs/additional-panels/signAppliesToCrossingRoad.png' },
          {signValue: [346], image: 'images/traffic-signs/additional-panels/signAppliesDirectionOfTheArrow.png' },
          {signValue: [347], image: 'images/traffic-signs/additional-panels/signAppliesDirectionOfTheArrowWithDistance.png', validation: validateAdditionalInfo, maxLabelLength: 5, offsetY: 2, offsetX: 4, additionalInfo: showAdditionalInfo },
          {signValue: [348], image: 'images/traffic-signs/additional-panels/signAppliesDirectionOfTheArrowWithDistance2.png', validation: validateAdditionalInfo, maxLabelLength: 5, offsetY: 3, offsetX: 2, additionalInfo: showAdditionalInfo },
          {signValue: [349], image: 'images/traffic-signs/additional-panels/motorhome.png' },
          {signValue: [350], image: 'images/traffic-signs/additional-panels/motorSledges.png' },
          {signValue: [351], image: 'images/traffic-signs/additional-panels/tractor.png' },
          {signValue: [352], image: 'images/traffic-signs/additional-panels/lowEmissionVehicle.png', validation: validateAdditionalInfo, maxLabelLength: 2, offsetY: -4, offsetX: 15, additionalInfo: showAdditionalInfo },
          {signValue: [353], image: 'images/traffic-signs/additional-panels/parkingOnTopOfCurb.png' },
          {signValue: [354], image: 'images/traffic-signs/additional-panels/parkingOnTheEdgeOfTheCurb.png' },
          {signValue: [355], image: 'images/traffic-signs/additional-panels/tunnelCategory.png' },
          {signValue: [356], image: 'images/traffic-signs/additional-panels/obligatoryUseOfParkingDisc2.png', validation: validateAdditionalInfo, maxLabelLength: 10, offsetX: 8, additionalInfo: showAdditionalInfo, unit: me.addMinutes, textColor: '#ffffff' },
          {signValue: [357], image: 'images/traffic-signs/additional-panels/parkingAgainstFee.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showPeriodDayAdditionalInfo, offsetX: 12, height: 40},
          {signValue: [358], image: 'images/traffic-signs/additional-panels/chargingSite.png' },
          {signValue: [359], image: 'images/traffic-signs/additional-panels/DirectionOfPriorityRoad.png'},
          {signValue: [360], image: 'images/traffic-signs/additional-panels/DirectionOfPriorityRoad3.png'},
          {signValue: [361], image: 'images/traffic-signs/additional-panels/twoWayBikePath.png'},
          {signValue: [362], image: 'images/traffic-signs/additional-panels/twoWayBikePath2.png'},
          {signValue: [363], image: 'images/traffic-signs/additional-panels/emergencyPhoneAndExtinguisher.png'},
          {signValue: [371], image: 'images/traffic-signs/other-signs/directionToAvoidObstacle.png'},
          {signValue: [372], image: 'images/traffic-signs/other-signs/curveDirectionSign.png'},
          {signValue: [373], image: 'images/traffic-signs/other-signs/borderMarkOnTheLeft.png'},
          {signValue: [374], image: 'images/traffic-signs/other-signs/borderMarkOnTheRight.png'},
          {signValue: [375], image: 'images/traffic-signs/other-signs/heightBorder.png'},
          {signValue: [376], image: 'images/traffic-signs/other-signs/underpassHeight.png', height: 30, validation: validateAdditionalInfo, offsetY: 3, offsetX: -7, additionalInfo: showAdditionalInfo},
          {signValue: [377], image: 'images/traffic-signs/other-signs/trafficSignColumn.png'},
          {signValue: [378], image: 'images/traffic-signs/other-signs/trafficSignColumn2.png'},
          {signValue: [379], image: 'images/traffic-signs/other-signs/divergingRoadSign.png'},
          {signValue: [382], image: 'images/traffic-signs/other-signs/towAwayZone.png'},
          {signValue: [383], image: 'images/traffic-signs/other-signs/SOSInformationBoard.png'},
          {signValue: [384], image: 'images/traffic-signs/other-signs/automaticTrafficControl.png'},
          {signValue: [385], image: 'images/traffic-signs/other-signs/surveillanceCamera.png'},
          {signValue: [386], image: 'images/traffic-signs/other-signs/reindeerHerdingArea.png'},
          {signValue: [387], image: 'images/traffic-signs/other-signs/reindeerHerdingAreaWithoutText.png'},
          {signValue: [388], image: 'images/traffic-signs/other-signs/speedLimitInformation.png'},
          {signValue: [389], image: 'images/traffic-signs/other-signs/countryBorder.png'},
          {signValue: [390], image: 'images/traffic-signs/information-signs/itineraryForIndicatedVehicleCategory.png'}, /* should be truckRoute */
          {signValue: [391], image: 'images/traffic-signs/information-signs/PassengerCarRoute.png'}, /* should be PassengerCarRoute */
          {signValue: [392], image: 'images/traffic-signs/information-signs/busRoute.png'}, /* should be busRoute */
          {signValue: [393], image: 'images/traffic-signs/information-signs/vanRoute.png'}, /* should be vanRoute */
          {signValue: [394], image: 'images/traffic-signs/information-signs/motorcycleRoute.png'}, /* should be motorcycleRoute */
          {signValue: [395], image: 'images/traffic-signs/information-signs/mopedRoute.png'}, /* should be mopedRoute */
          {signValue: [396], image: 'images/traffic-signs/information-signs/tractorRoute.png'}, /* should be tractorRoute */
          {signValue: [397], image: 'images/traffic-signs/information-signs/motorhomeRoute.png'}, /* should be motorhomeRoute */
          {signValue: [398], image: 'images/traffic-signs/information-signs/bicycleRoute.png'}, /* should be bicycleRoute */
          {signValue: [399], image: 'images/traffic-signs/information-signs/endOfLane2.png' },
          {signValue: [400], image: 'images/traffic-signs/information-signs/regionalRoadNumber.png', validation: validateAdditionalInfo, maxLabelLength: 15, additionalInfo: showAdditionalInfo},
          {signValue: [401], image: 'images/traffic-signs/other-signs/textualMainSign.png', validation: validateAdditionalInfo, maxLabelLength: 19, additionalInfo: showAdditionalInfo, offsetX: 3, height: 25, textColor: '#ffffff'},
          {signValue: [402], image: 'images/traffic-signs/additional-panels/lightElectricVehicle.png'},
          {signValue: [403], image: 'images/traffic-signs/additional-panels/twoWaySidewalk.png'},
        ];
      };

      var showAdditionalInfo = function () {
        return _.isEmpty(this.value) ? this.additionalInfo : '';
      };

      var showHourMinAdditionalInfo = function () {
        var timePeriod = _.first(this.additionalInfo.match(/\d+\s*[h]{1}\s*\d+\s*[min]{3}|\d+\s*[h]{1}|\d+\s*[min]{3}/));
        return timePeriod ? timePeriod : this.additionalInfo ? this.additionalInfo : '';
      };

      var showPeriodTimeAdditionalInfo = function () {
        var firstPeriod = _.first(this.additionalInfo.match(/[(]?\d+\s*[-]{1}\s*\d+[)]?/));
        return firstPeriod ? firstPeriod : this.additionalInfo ? this.additionalInfo : '';
      };

      var showPeriodDayAdditionalInfo = function () {
        var counter = 3;
        var index = 0;
        var output = "";
        var timePeriods = this.additionalInfo ? this.additionalInfo.match(/[(]?\d+\s*[-]{1}\s*\d+[)]?/g) : [];

        while (index < counter && !_.isEmpty(timePeriods) && timePeriods.length > index) {

          output = output.concat((index > 0 ? '\n' : ""), timePeriods[index]);
          index++;
        }
        return output.length !== 0 ? output : this.additionalInfo ? this.additionalInfo : '';
      };

      var validateSpeedLimitValues = function () {
        return this.value && (this.value > 0 && this.value <= 120);
      };

      var validateMaximumRestrictions = function () {
        // Not specified the maximum restriction value
        return this.value && (this.value > 0 && this.value < 100000);
      };

      var validateAdditionalInfo = function () {
        var labelMaxLength = me.getLabelProperty(this).getMaxLength();
        return this.value || (this.additionalInfo && this.additionalInfo.length <= labelMaxLength);
      };

    var validateNumber = function () {
      var labelMaxLength = me.getLabelProperty(this).getMaxLength();
      return this.value && !isNaN(this.value) && this.value.length <= labelMaxLength;
    };

      this.renderFeaturesByPointAssets = function (pointAssets, zoomLevel) {
        return me.renderGroupedFeatures(pointAssets, zoomLevel, function (asset) {
          return me.getCoordinate(asset);
        });
      };

      this.renderGroupedFeatures = function (assets, zoomLevel, getPoint) {
        var zI = 0; //z index for the styles

        if (!this.isVisibleZoom(zoomLevel))
          return [];
        var groupedAssets = me.getGroupedFeatures(assets, zoomLevel);

        // Begin rendering grouped features with highest lat first (these will stay at lower z index)
        // To avoid overlapping when setting the z index of each feature styles
        var groupedAssetsSortedByLat = _.sortBy(groupedAssets, function (assets){return -_.head(assets).lat;});

        return _.flatten(_.chain(groupedAssetsSortedByLat).map(function (assets) {
          var imgPosition = {x: 0, y: me.stickPosition.y};
          return _.map(assets, function (asset) {
            var values = me.getValue(asset);
            if (values !== undefined) {
              var linkOutOfUse = asset.constructionType == 4;
              var styles = [];
              styles = styles.concat(me.getStickStyle(linkOutOfUse));
              _.map(values, function(value){
                imgPosition.y += me.getLabelProperty(value).getHeight();
                styles = styles.concat(me.getStyle(value, imgPosition, linkOutOfUse));
              });

              var suggestionInfo = getProperty(asset,"suggest_box");
              if(!_.isUndefined(suggestionInfo) && !!parseInt(suggestionInfo.propertyValue)){
                imgPosition.y += 40;
                styles = me.suggestionStyle(suggestionInfo, styles, imgPosition.y);
              }

              _.forEach(styles, function(style){style.setZIndex(zI);});
              zI++;
              var feature = me.createFeature(getPoint(asset));
              feature.setStyle(styles);
              feature.setProperties(_.omit(asset, 'geometry'));
              return feature;
            }
          });
        }).filter(function (feature) {
          return !_.isUndefined(feature);
        }).value());
      };

      this.createFeature = function (point) {
        return new ol.Feature(new ol.geom.Point(point));
      };

      var getProperty = function (asset, publicId) {
        return _.head(_.find(asset.propertyData, function (prop) {
          return prop.publicId === publicId;
        }).values);
      };

    var getProperties = function (asset, publicId) {
      return _.find(asset.propertyData, function (prop) {
        return prop.publicId === publicId;
      }).values;
    };

      me.getValue = function (asset) {
        if (_.isUndefined(getProperty(asset, "trafficSigns_type")))
          return;
        var value = getProperty(asset, "trafficSigns_value") ? getProperty(asset, "trafficSigns_value").propertyValue : '';
        var additionalInfo;
        if(getProperty(asset, "main_sign_text").propertyValue) {
          additionalInfo = getProperty(asset, "main_sign_text").propertyValue;
        } else additionalInfo = '';

        var panels = _.map(getProperties(asset, "additional_panel"), function(panel){
          var panelValue = panel.panelValue ? panel.panelValue : panel.text;
          return {
            value: panelValue,
            type: parseInt(panel.panelType),
            additionalInfo: ''
          };
        });
        panels.unshift({
          value: value,
          type: parseInt(getProperty(asset, "trafficSigns_type").propertyValue),
          additionalInfo: additionalInfo
        });
        return panels.reverse();
      };
    };
})(this);
