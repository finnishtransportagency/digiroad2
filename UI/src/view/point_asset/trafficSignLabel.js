(function(root) {

  root.TrafficSignLabel = function(groupingDistance) {
    SignsLabel.call(this, this.MIN_DISTANCE);
    var me = this;

    me.MIN_DISTANCE = groupingDistance;

    me.getSignType = function (sign) { return sign.type;};

    me.getPropertiesConfiguration = function () {
        return [
          {signValue: [1], image: 'images/traffic-signs/speed-limits/speedLimitSign.png', validation: validateSpeedLimitValues},
          {signValue: [2], image: 'images/traffic-signs/speed-limits/endOfSpeedLimitSign.png', validation: validateSpeedLimitValues},
          {signValue: [3], image: 'images/traffic-signs/speed-limits/speedLimitZoneSign.png', validation: validateSpeedLimitValues},
          {signValue: [4], image: 'images/traffic-signs/speed-limits/endOfSpeedLimitZoneSign.png', validation: validateSpeedLimitValues},
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
          {signValue: [45], image: 'images/traffic-signs/additional-panels/freeWidthSign.png', validation: validateAdditionalInfo, maxLabelLength: 11, additionalInfo: showAdditionalInfo, offsetX: 2, height: 30},
          {signValue: [46], image: 'images/traffic-signs/additional-panels/freeHeight.png', validation: validateAdditionalInfo, maxLabelLength: 10, additionalInfo: showAdditionalInfo, offsetX: 1, height: 40},
          {signValue: [47], image: 'images/traffic-signs/additional-panels/hazmatProhibitionA.png', height: 27},
          {signValue: [48], image: 'images/traffic-signs/additional-panels/hazmatProhibitionB.png', height: 27},
          {signValue: [49], image: 'images/traffic-signs/additional-panels/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showPeriodTimeAdditionalInfo, offsetX: 1, height: 30},
          {signValue: [50], image: 'images/traffic-signs/additional-panels/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showPeriodTimeAdditionalInfo, offsetX: 1, height: 30},
          {signValue: [51], image: 'images/traffic-signs/additional-panels/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showHourMinAdditionalInfo, offsetX: 1, height: 30},
          {signValue: [52], image: 'images/traffic-signs/additional-panels/passengerCar.png', height: 20},
          {signValue: [53], image: 'images/traffic-signs/additional-panels/bus.png', height: 20},
          {signValue: [54], image: 'images/traffic-signs/additional-panels/lorry.png', height: 20},
          {signValue: [55], image: 'images/traffic-signs/additional-panels/van.png', height: 20},
          {signValue: [56], image: 'images/traffic-signs/additional-panels/vehicleForHandicapped.png', height: 20},
          {signValue: [57], image: 'images/traffic-signs/additional-panels/motorCycle.png', height: 20},
          {signValue: [58], image: 'images/traffic-signs/additional-panels/cycle.png', height: 20},
          {signValue: [59], image: 'images/traffic-signs/additional-panels/parkingAgainstFee.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showPeriodDayAdditionalInfo, offsetX: 12, height: 40},
          {signValue: [60], image: 'images/traffic-signs/additional-panels/obligatoryUseOfParkingDisc.png', validation: validateAdditionalInfo, maxLabelLength: 50, additionalInfo: showHourMinAdditionalInfo, offsetX: 12, height: 33},
          {signValue: [61], image: 'images/traffic-signs/additional-panels/additionalPanelWithText.png', validation: validateAdditionalInfo, maxLabelLength: 19, additionalInfo: showAdditionalInfo, offsetX: 3, height: 25},
          {signValue: [62], image: 'images/traffic-signs/additional-panels/drivingInServicePurposesAllowed.png', validation: validateAdditionalInfo, maxLabelLength: 13, additionalInfo: showAdditionalInfo, offsetX: 2, height: 28},
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
          {signValue: [79], image: 'images/traffic-signs/mandatory-signs/passThisSide.png'},
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
          {signValue: [106], image: 'images/traffic-signs/regulatory-signs/oneWayRoad.png', height: 17},
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
          {signValue: [124], image: 'images/traffic-signs/service-signs/publicLavatory.png'}
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
        var labelMaxLength = getLabelProperty(this).getMaxLength();
        return this.value || (this.additionalInfo && this.additionalInfo.length <= labelMaxLength);
      };

      this.getStyle = function (trafficSign, position) {
        return [backgroundStyle(trafficSign, position), new ol.style.Style({
          text: new ol.style.Text({
            text: textStyle(trafficSign),
            fill: new ol.style.Fill({
              color: '#000000'
            }),
            font: '12px sans-serif',
            offsetX: getLabelProperty(trafficSign).getTextOffsetX(),
            offsetY: getLabelProperty(trafficSign).getTextOffsetY() - position.y
          })
        })];
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
          var imgPosition = {x: 0, y: stickPosition.y};
          return _.map(assets, function (asset) {
            var value = me.getValue(asset);
            if (value !== undefined) {
              var styles = [];
              styles = styles.concat(me.getStickStyle());
              imgPosition.y += getLabelProperty(value).getHeight();
              styles = styles.concat(me.getStyle(value, imgPosition));
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

      me.getValue = function (asset) {
        if (_.isUndefined(getProperty(asset, "trafficSigns_type")))
          return;
        var value = getProperty(asset, "trafficSigns_value") ? getProperty(asset, "trafficSigns_value").propertyValue : '';
        var additionalInfo = getProperty(asset, "trafficSigns_info") ? getProperty(asset, "trafficSigns_info").propertyValue : '';
        return {
          value: value,
          type: parseInt(getProperty(asset, "trafficSigns_type").propertyValue),
          additionalInfo: additionalInfo
        };
      };
    };
})(this);
