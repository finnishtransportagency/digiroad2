(function(root) {

  root.TrafficSignLabel = function(groupingDistance) {
    SignsLabel.call(this, this.MIN_DISTANCE);
    var me = this;

    me.MIN_DISTANCE = groupingDistance;

    me.getSignType = function (sign) { return sign.type;};

    me.getPropertiesConfiguration = function () {
        return [
          {signValue: [361], image: 'images/traffic-signs/speed-limits/speedLimitSign.png', validation: validateSpeedLimitValues},
          {signValue: [362], image: 'images/traffic-signs/speed-limits/endOfSpeedLimitSign.png', validation: validateSpeedLimitValues},
          {signValue: [363], image: 'images/traffic-signs/speed-limits/speedLimitZoneSign.png', validation: validateSpeedLimitValues},
          {signValue: [364], image: 'images/traffic-signs/speed-limits/endOfSpeedLimitZoneSign.png', validation: validateSpeedLimitValues},
          {signValue: [571], image: 'images/traffic-signs/speed-limits/urbanAreaSign.png', height: 30},
          {signValue: [572], image: 'images/traffic-signs/speed-limits/endOfUrbanAreaSign.png', height: 30},
          {signValue: [511], image: 'images/traffic-signs/regulatory-signs/crossingSign.png'},
          {signValue: [343], image: 'images/traffic-signs/maximum-restrictions/maximumLengthSign.png', validation: validateMaximumRestrictions,  convertion: me.convertToMeters, unit: me.addMeters, offsetY: 5},
          {signValue: [189], image: 'images/traffic-signs/general-warning-signs/warningSign.png'},
          {signValue: [332], image: 'images/traffic-signs/prohibitions-and-restrictions/turningRestrictionLeftSign.png'},
          {signValue: [333], image: 'images/traffic-signs/prohibitions-and-restrictions/turningRestrictionRightSign.png'},
          {signValue: [334], image: 'images/traffic-signs/prohibitions-and-restrictions/uTurnRestrictionSign.png'},
          {signValue: [311], image: 'images/traffic-signs/prohibitions-and-restrictions/noVehicles.png'},
          {signValue: [312], image: 'images/traffic-signs/prohibitions-and-restrictions/noPowerDrivenVehiclesSign.png'},
          {signValue: [313], image: 'images/traffic-signs/prohibitions-and-restrictions/noLorriesSign.png'},
          {signValue: [314], image: 'images/traffic-signs/prohibitions-and-restrictions/noVehicleCombinationsSign.png'},
          {signValue: [315], image: 'images/traffic-signs/prohibitions-and-restrictions/noTractorSign.png'},
          {signValue: [316], image: 'images/traffic-signs/prohibitions-and-restrictions/noMotorCycleSign.png'},
          {signValue: [317], image: 'images/traffic-signs/prohibitions-and-restrictions/noMotorSledgesSign.png'},
          {signValue: [318], image: 'images/traffic-signs/prohibitions-and-restrictions/noDangerousGoodsSign.png'},
          {signValue: [319], image: 'images/traffic-signs/prohibitions-and-restrictions/noBusSign.png'},
          {signValue: [321], image: 'images/traffic-signs/prohibitions-and-restrictions/noMopedsSign.png'},
          {signValue: [322], image: 'images/traffic-signs/prohibitions-and-restrictions/noCycleSign.png'},
          {signValue: [323], image: 'images/traffic-signs/prohibitions-and-restrictions/noPedestrianSign.png'},
          {signValue: [324], image: 'images/traffic-signs/prohibitions-and-restrictions/noPedestrianOrCycleSign.png'},
          {signValue: [325], image: 'images/traffic-signs/prohibitions-and-restrictions/noHorsesSign.png'},
          {signValue: [331], image: 'images/traffic-signs/prohibitions-and-restrictions/noEntrySign.png'},
          {signValue: [351], image: 'images/traffic-signs/prohibitions-and-restrictions/overtakingProhibitedSign.png'},
          {signValue: [352], image: 'images/traffic-signs/prohibitions-and-restrictions/endOfOvertakingProhibitionSign.png'},
          {signValue: [341], image: 'images/traffic-signs/maximum-restrictions/maxWidthSign.png', validation: validateMaximumRestrictions, convertion: me.convertToMeters},
          {signValue: [342], image: 'images/traffic-signs/maximum-restrictions/maxHeightSign.png', validation: validateMaximumRestrictions, convertion: me.convertToMeters, unit: me.addMeters},
          {signValue: [344], image: 'images/traffic-signs/maximum-restrictions/totalWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons},
          {signValue: [345], image: 'images/traffic-signs/maximum-restrictions/trailerTruckWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons, offsetY: 5},
          {signValue: [346], image: 'images/traffic-signs/maximum-restrictions/axleWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons, offsetY: -1},
          {signValue: [347], image: 'images/traffic-signs/maximum-restrictions/bogieWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons,  offsetY: -1},
          {signValue: [111], image: 'images/traffic-signs/general-warning-signs/rightBendSign.png'},
          {signValue: [112], image: 'images/traffic-signs/general-warning-signs/leftBendSign.png'},
          {signValue: [113], image: 'images/traffic-signs/general-warning-signs/severalBendRightSign.png'},
          {signValue: [114], image: 'images/traffic-signs/general-warning-signs/severalBendLeftSign.png'},
          {signValue: [115], image: 'images/traffic-signs/general-warning-signs/dangerousDescentSign.png'},
          {signValue: [116], image: 'images/traffic-signs/general-warning-signs/steepAscentSign.png'},
          {signValue: [141], image: 'images/traffic-signs/general-warning-signs/unevenRoadSign.png'},
          {signValue: [152], image: 'images/traffic-signs/general-warning-signs/childrenSign.png'},
          {signValue: [821], image: 'images/traffic-signs/additional-panels/freeWidthSign.png', validation: validateAdditionalInfo, maxLabelLength: 11, additionalInfo: showAdditionalInfo, offsetX: 2, height: 30},
          {signValue: [822], image: 'images/traffic-signs/additional-panels/freeHeight.png', validation: validateAdditionalInfo, maxLabelLength: 10, additionalInfo: showAdditionalInfo, offsetX: 1, height: 40},
          {signValue: [848], image: 'images/traffic-signs/additional-panels/hazmatProhibitionA.png', height: 27},
          {signValue: [849], image: 'images/traffic-signs/additional-panels/hazmatProhibitionB.png', height: 27},
          {signValue: [851], image: 'images/traffic-signs/additional-panels/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showPeriodTimeAdditionalInfo, offsetX: 1, height: 30},
          {signValue: [852], image: 'images/traffic-signs/additional-panels/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showPeriodTimeAdditionalInfo, offsetX: 1, height: 30},
          {signValue: [854], image: 'images/traffic-signs/additional-panels/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showHourMinAdditionalInfo, offsetX: 1, height: 30},
          {signValue: [831], image: 'images/traffic-signs/additional-panels/passengerCar.png', height: 20},
          {signValue: [832], image: 'images/traffic-signs/additional-panels/bus.png', height: 20},
          {signValue: [833], image: 'images/traffic-signs/additional-panels/lorry.png', height: 20},
          {signValue: [834], image: 'images/traffic-signs/additional-panels/van.png', height: 20},
          {signValue: [836], image: 'images/traffic-signs/additional-panels/vehicleForHandicapped.png', height: 20},
          {signValue: [841], image: 'images/traffic-signs/additional-panels/motorCycle.png', height: 20},
          {signValue: [843], image: 'images/traffic-signs/additional-panels/cycle.png', height: 20},
          {signValue: [855], image: 'images/traffic-signs/additional-panels/parkingAgainstFee.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showPeriodDayAdditionalInfo, offsetX: 12, height: 40},
          {signValue: [856], image: 'images/traffic-signs/additional-panels/obligatoryUseOfParkingDisc.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showHourMinAdditionalInfo, offsetX: 12, height: 33},
          {signValue: [871], image: 'images/traffic-signs/additional-panels/additionalPanelWithText.png', validation: validateAdditionalInfo, maxLabelLength: 19, additionalInfo: showAdditionalInfo, offsetX: 3, height: 25},
          {signValue: [872], image: 'images/traffic-signs/additional-panels/drivingInServicePurposesAllowed.png', validation: validateAdditionalInfo, maxLabelLength: 13, additionalInfo: showAdditionalInfo, offsetX: 2, height: 28},
          {signValue: [541], image: 'images/traffic-signs/regulatory-signs/busLane.png'},
          {signValue: [542], image: 'images/traffic-signs/regulatory-signs/busLaneEnds.png'},
          {signValue: [543], image: 'images/traffic-signs/regulatory-signs/tramLane.png'},
          {signValue: [531], image: 'images/traffic-signs/regulatory-signs/busStopForLocalTraffic.png'},
          {signValue: [533], image: 'images/traffic-signs/regulatory-signs/tramStop.png'},
          {signValue: [534], image: 'images/traffic-signs/regulatory-signs/taxiStation.png'},
          {signValue: [421], image: 'images/traffic-signs/mandatory-signs/compulsoryFootPath.png'},
          {signValue: [422], image: 'images/traffic-signs/mandatory-signs/compulsoryCycleTrack.png'},
          {signValue: [423], image: 'images/traffic-signs/mandatory-signs/combinedCycleTrackAndFootPath.png'},
          {signValue: [413], image: 'images/traffic-signs/mandatory-signs/directionToBeFollowed3.png'},
          {signValue: [416], image: 'images/traffic-signs/mandatory-signs/compulsoryRoundabout.png'},
          {signValue: [417], image: 'images/traffic-signs/mandatory-signs/passThisSide.png'},
          {signValue: [418], image: 'images/traffic-signs/mandatory-signs/passThisSide.png'},
          {signValue: [375], image: 'images/traffic-signs/prohibitions-and-restrictions/taxiStationZoneBeginning.png'},
          {signValue: [376], image: 'images/traffic-signs/prohibitions-and-restrictions/standingPlaceForTaxi.png'},
          {signValue: [121], image: 'images/traffic-signs/general-warning-signs/roadNarrows.png'},
          {signValue: [122], image: 'images/traffic-signs/general-warning-signs/twoWayTraffic.png'},
          {signValue: [131], image: 'images/traffic-signs/general-warning-signs/swingBridge.png'},
          {signValue: [142], image: 'images/traffic-signs/general-warning-signs/roadWorks.png'},
          {signValue: [144], image: 'images/traffic-signs/general-warning-signs/slipperyRoad.png'},
          {signValue: [151], image: 'images/traffic-signs/general-warning-signs/pedestrianCrossingWarningSign.png'},
          {signValue: [153], image: 'images/traffic-signs/general-warning-signs/cyclists.png'},
          {signValue: [161], image: 'images/traffic-signs/general-warning-signs/intersectionWithEqualRoads.png'},
          {signValue: [165], image: 'images/traffic-signs/general-warning-signs/lightSignals.png'},
          {signValue: [167], image: 'images/traffic-signs/general-warning-signs/tramwayLine.png'},
          {signValue: [181], image: 'images/traffic-signs/general-warning-signs/fallingRocks.png'},
          {signValue: [183], image: 'images/traffic-signs/general-warning-signs/crossWind.png'},
          {signValue: [211], image: 'images/traffic-signs/priority-and-give-way-signs/priorityRoad.png'},
          {signValue: [212], image: 'images/traffic-signs/priority-and-give-way-signs/endOfPriority.png'},
          {signValue: [221], image: 'images/traffic-signs/priority-and-give-way-signs/priorityOverOncomingTraffic.png'},
          {signValue: [222], image: 'images/traffic-signs/priority-and-give-way-signs/priorityForOncomingTraffic.png'},
          {signValue: [231], image: 'images/traffic-signs/priority-and-give-way-signs/giveWay.png'},
          {signValue: [232], image: 'images/traffic-signs/priority-and-give-way-signs/stop.png'},
          {signValue: [371], image: 'images/traffic-signs/prohibitions-and-restrictions/standingAndParkingProhibited.png'},
          {signValue: [372], image: 'images/traffic-signs/prohibitions-and-restrictions/parkingProhibited.png'},
          {signValue: [373], image: 'images/traffic-signs/prohibitions-and-restrictions/parkingProhibitedZone.png'},
          {signValue: [374], image: 'images/traffic-signs/prohibitions-and-restrictions/endOfParkingProhibitedZone.png'},
          {signValue: [381], image: 'images/traffic-signs/prohibitions-and-restrictions/alternativeParkingOddDays.png'},
          {signValue: [521], image: 'images/traffic-signs/regulatory-signs/parkingLot.png'},
          {signValue: [551], image: 'images/traffic-signs/regulatory-signs/oneWayRoad.png', height: 17},
          {signValue: [561], image: 'images/traffic-signs/regulatory-signs/motorway.png', height: 40},
          {signValue: [562], image: 'images/traffic-signs/regulatory-signs/motorwayEnds.png', height: 40},
          {signValue: [573], image: 'images/traffic-signs/regulatory-signs/residentialZone.png'},
          {signValue: [574], image: 'images/traffic-signs/regulatory-signs/endOfResidentialZone.png'},
          {signValue: [575], image: 'images/traffic-signs/regulatory-signs/pedestrianZone.png'},
          {signValue: [576], image: 'images/traffic-signs/regulatory-signs/endOfPedestrianZone.png'},
          {signValue: [651], image: 'images/traffic-signs/information-signs/noThroughRoad.png'},
          {signValue: [652], image: 'images/traffic-signs/information-signs/noThroughRoadRight.png'},
          {signValue: [671], image: 'images/traffic-signs/information-signs/symbolOfMotorway.png'},
          {signValue: [677], image: 'images/traffic-signs/information-signs/parking.png'},
          {signValue: [681], image: 'images/traffic-signs/information-signs/itineraryForIndicatedVehicleCategory.png'},
          {signValue: [682], image: 'images/traffic-signs/information-signs/itineraryForPedestrians.png'},
          {signValue: [683], image: 'images/traffic-signs/information-signs/itineraryForHandicapped.png'},
          {signValue: [704], image: 'images/traffic-signs/service-signs/locationSignForTouristService.png', height: 25},
          {signValue: [715], image: 'images/traffic-signs/service-signs/firstAid.png'},
          {signValue: [722], image: 'images/traffic-signs/service-signs/fillingStation.png'},
          {signValue: [724], image: 'images/traffic-signs/service-signs/restaurant.png'},
          {signValue: [726], image: 'images/traffic-signs/service-signs/publicLavatory.png'},
          {signValue: [155], image: 'images/traffic-signs/general-warning-signs/Moose.png'},
          {signValue: [156], image: 'images/traffic-signs/general-warning-signs/Reiner.png'},
          {signValue: [162], image: 'images/traffic-signs/general-warning-signs/IntersectionWithMinorRoad.png'},
          {signValue: [163], image: 'images/traffic-signs/general-warning-signs/IntersectionWithOneMinorRoad.png'},
          {signValue: [164], image: 'images/traffic-signs/general-warning-signs/IntersectionWithOneCrossMinorRoad.png'},
          {signValue: [171], image: 'images/traffic-signs/general-warning-signs/LevelCrossingWithoutGate.png'},
          {signValue: [172], image: 'images/traffic-signs/general-warning-signs/LevelCrossingWithGates.png'},
          {signValue: [176], image: 'images/traffic-signs/general-warning-signs/LevelCrossingWithOneTrack.png'},
          {signValue: [177], image: 'images/traffic-signs/general-warning-signs/LevelCrossingWithManyTracks.png'},
          {signValue: [382], image: 'images/traffic-signs/prohibitions-and-restrictions/AlternativeParkingEvenDays.png'},
          {signValue: [426], image: 'images/traffic-signs/mandatory-signs/CompulsoryTrackMotorSledges.png'},
          {signValue: [427], image: 'images/traffic-signs/mandatory-signs/CompulsoryTrackRidersHorseback.png'},
          {signValue: [520], image: 'images/traffic-signs/regulatory-signs/InfartsparkeringParkAndRide.png'},
          {signValue: [816], image: 'images/traffic-signs/additional-panels/DistanceCompulsoryStop.png'},
          {signValue: [823], image: 'images/traffic-signs/additional-panels/HeightElectricLine.png'},
          {signValue: [824], image: 'images/traffic-signs/additional-panels/SignAppliesBothDirections.png'},
          {signValue: [825], image: 'images/traffic-signs/additional-panels/SignAppliesBothDirectionsVertical.png'},
          {signValue: [826], image: 'images/traffic-signs/additional-panels/SignAppliesArrowDirections.png'},
          {signValue: [827], image: 'images/traffic-signs/additional-panels/RegulationBeginsFromSign.png'},
          {signValue: [828], image: 'images/traffic-signs/additional-panels/RegulationEndsToTheSign.png'},
          {signValue: [853], image: 'images/traffic-signs/additional-panels/ValidMultiplePeriod.png'},
          {signValue: [861], image: 'images/traffic-signs/additional-panels/DirectionOfPriorityRoad.png'},
          {signValue: [862], image: 'images/traffic-signs/additional-panels/CrossingLogTransportRoad.png'}
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
        var counter = 2;
        var index = 0;
        var output = "";
        var timePeriods = this.additionalInfo ? this.additionalInfo.match(/[(]?\d+\s*[-]{1}\s*\d+[)]?/g) : [];

        while (index < counter && timePeriods.length > index) {

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

      this.renderFeaturesByPointAssets = function (pointAssets, zoomLevel) {
        return me.renderGroupedFeatures(pointAssets, zoomLevel, function (asset) {
          return me.getCoordinate(asset);
        });
      };

      this.renderGroupedFeatures = function (assets, zoomLevel, getPoint) {
        if (!this.isVisibleZoom(zoomLevel))
          return [];
        var groupedAssets = me.getGroupedFeatures(assets, zoomLevel);
        return _.flatten(_.chain(groupedAssets).map(function (assets) {
          var imgPosition = {x: 0, y: me.stickPosition.y};
          return _.map(assets, function (asset) {
            var values = me.getValue(asset);
            if (values !== undefined) {
              var styles = [];
              styles = styles.concat(me.getStickStyle());
              _.map(values, function(value){
                imgPosition.y += me.getLabelProperty(value).getHeight();
                styles = styles.concat(me.getStyle(value, imgPosition));
              });
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
        var additionalInfo = getProperty(asset, "trafficSigns_info") ? getProperty(asset, "trafficSigns_info").propertyValue : '';
        var panels = _.map(getProperties(asset, "additional_panel"), function(panel){
          return {
            value: panel.panelValue,
            type: parseInt(panel.panelType),
            additionalInfo:  panel.panelInfo
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
