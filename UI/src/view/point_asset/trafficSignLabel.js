(function(root) {

  root.TrafficSignLabel = function(groupingDistance) {
    AssetLabel.call(this, this.MIN_DISTANCE);
    var me = this;

    this.MIN_DISTANCE = groupingDistance;

    var backgroundStyle = function (trafficSign, counter) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getLabelProperty(trafficSign, counter).findImage(),
          anchor : [0.48, 1.75 + (counter)]
        }))
      });
    };

    this.getStickStyle = function () {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/traffic-signs/trafficSignStick.png',
          anchor : [0.5, 1]
        }))
      });
    };

    var getLabelProperty = function (trafficSign, counter) {

      var labelingProperties = [
        {signValue: [1], image: 'images/traffic-signs/speedLimitSign.png', validation: validateSpeedLimitValues},
        {signValue: [2], image: 'images/traffic-signs/endOfSpeedLimitSign.png', validation: validateSpeedLimitValues},
        {signValue: [3], image: 'images/traffic-signs/speedLimitZoneSign.png', validation: validateSpeedLimitValues},
        {signValue: [4], image: 'images/traffic-signs/endOfSpeedLimitZoneSign.png', validation: validateSpeedLimitValues},
        {signValue: [5], image: 'images/traffic-signs/urbanAreaSign.png', offsetY: -8 - (counter * 35)},
        {signValue: [6], image: 'images/traffic-signs/endOfUrbanAreaSign.png'},
        {signValue: [7], image: 'images/traffic-signs/crossingSign.png'},
        {signValue: [8], image: 'images/traffic-signs/maximumLengthSign.png', validation: validateMaximumRestrictions, offsetY: -38 - (counter * 35), convertion: convertToMeters, unit: addMeters},
        {signValue: [9], image: 'images/traffic-signs/warningSign.png'},
        {signValue: [10], image: 'images/traffic-signs/turningRestrictionLeftSign.png'},
        {signValue: [11], image: 'images/traffic-signs/turningRestrictionRightSign.png'},
        {signValue: [12], image: 'images/traffic-signs/uTurnRestrictionSign.png'},
        {signValue: [13], image: 'images/traffic-signs/noVehicles.png'},
        {signValue: [14], image: 'images/traffic-signs/noPowerDrivenVehiclesSign.png'},
        {signValue: [15], image: 'images/traffic-signs/noLorriesSign.png'},
        {signValue: [16], image: 'images/traffic-signs/noVehicleCombinationsSign.png'},
        {signValue: [17], image: 'images/traffic-signs/noTractorSign.png'},
        {signValue: [18], image: 'images/traffic-signs/noMotorCycleSign.png'},
        {signValue: [19], image: 'images/traffic-signs/noMotorSledgesSign.png'},
        {signValue: [20], image: 'images/traffic-signs/noDangerousGoodsSign.png'},
        {signValue: [21], image: 'images/traffic-signs/noBusSign.png'},
        {signValue: [22], image: 'images/traffic-signs/noMopedsSign.png'},
        {signValue: [23], image: 'images/traffic-signs/noCycleSign.png'},
        {signValue: [24], image: 'images/traffic-signs/noPedestrianSign.png'},
        {signValue: [25], image: 'images/traffic-signs/noPedestrianOrCycleSign.png'},
        {signValue: [26], image: 'images/traffic-signs/noHorsesSign.png'},
        {signValue: [27], image: 'images/traffic-signs/noEntrySign.png'},
        {signValue: [28], image: 'images/traffic-signs/overtakingProhibitedSign.png'},
        {signValue: [29], image: 'images/traffic-signs/endOfOvertakingProhibitionSign.png'},
        {signValue: [30], image: 'images/traffic-signs/maxWidthSign.png', validation: validateMaximumRestrictions, convertion: convertToMeters},
        {signValue: [31], image: 'images/traffic-signs/maxHeightSign.png', validation: validateMaximumRestrictions, convertion: convertToMeters, unit: addMeters},
        {signValue: [32], image: 'images/traffic-signs/totalWeightLimit.png', validation: validateMaximumRestrictions, convertion: convertToTons, unit: addTons},
        {signValue: [33], image: 'images/traffic-signs/trailerTruckWeightLimit.png', validation: validateMaximumRestrictions, offsetY: -38 - (counter * 35), convertion: convertToTons, unit: addTons},
        {signValue: [34], image: 'images/traffic-signs/axleWeightLimit.png', validation: validateMaximumRestrictions, offsetY: -46 - (counter * 35), convertion: convertToTons, unit: addTons },
        {signValue: [35], image: 'images/traffic-signs/bogieWeightLimit.png', validation: validateMaximumRestrictions, offsetY: -46 - (counter * 35), convertion: convertToTons, unit: addTons },
        {signValue: [36], image: 'images/traffic-signs/rightBendSign.png'},
        {signValue: [37], image: 'images/traffic-signs/leftBendSign.png'},
        {signValue: [38], image: 'images/traffic-signs/severalBendRightSign.png'},
        {signValue: [39], image: 'images/traffic-signs/severalBendLeftSign.png'},
        {signValue: [40], image: 'images/traffic-signs/dangerousDescentSign.png'},
        {signValue: [41], image: 'images/traffic-signs/steepAscentSign.png'},
        {signValue: [42], image: 'images/traffic-signs/unevenRoadSign.png'},
        {signValue: [43], image: 'images/traffic-signs/childrenSign.png'},
        {signValue: [45], image: 'images/traffic-signs/freeWidthSign.png', validation: validateAdditionalInfo, maxLabelLength: 11, isToShowAdditionalInfo: isToShowAdditionalInfo, offsetX: 2, offsetY: -38 - (counter * 35)},
        {signValue: [46], image: 'images/traffic-signs/freeHeight.png', validation: validateAdditionalInfo, maxLabelLength: 10, isToShowAdditionalInfo: isToShowAdditionalInfo, offsetX: 1, offsetY: -50 - (counter * 35)},
        {signValue: [47], image: 'images/traffic-signs/hazmatProhibitionA.png'},
        {signValue: [48], image: 'images/traffic-signs/hazmatProhibitionB.png'},
        {signValue: [49], image: 'images/traffic-signs/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 10, isToShowAdditionalInfo: showPartialAdditionalInfo(), offsetX: 1, offsetY: -35 - (counter * 35)},
        {signValue: [50], image: 'images/traffic-signs/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 10, isToShowAdditionalInfo: showPartialAdditionalInfo, offsetX: 1, offsetY: -35 - (counter * 35)},
        {signValue: [51], image: 'images/traffic-signs/defaultAdditionalPanelBox.png', validation: validateAdditionalInfo, maxLabelLength: 10, isToShowAdditionalInfo: isToShowAdditionalInfo, offsetX: 1, offsetY: -35 - (counter * 35)},
        {signValue: [52], image: 'images/traffic-signs/passengerCar.png'},
        {signValue: [53], image: 'images/traffic-signs/bus.png'},
        {signValue: [54], image: 'images/traffic-signs/lorry.png'},
        {signValue: [55], image: 'images/traffic-signs/van.png'},
        {signValue: [56], image: 'images/traffic-signs/vehicleForHandicapped.png'},
        {signValue: [57], image: 'images/traffic-signs/motorCycle.png'},
        {signValue: [58], image: 'images/traffic-signs/cycle.png'},
        {signValue: [59], image: 'images/traffic-signs/parkingAgainstFee.png', validation: validateAdditionalInfo, maxLabelLength: 10, isToShowAdditionalInfo: isToShowAdditionalInfo, offsetX: 12, offsetY: -50 - (counter * 35)},
        {signValue: [60], image: 'images/traffic-signs/obligatoryUseOfParkingDisc.png', validation: validateAdditionalInfo, maxLabelLength: 10, isToShowAdditionalInfo: isToShowAdditionalInfo, offsetX: 12, offsetY: -42 - (counter * 35)},
        {signValue: [61], image: 'images/traffic-signs/additionalPanelWithText.png', validation: validateAdditionalInfo, maxLabelLength: 19, isToShowAdditionalInfo: isToShowAdditionalInfo, offsetX: 3, offsetY: -30 - (counter * 35)},
        {signValue: [62], image: 'images/traffic-signs/drivingInServicePurposesAllowed.png', validation: validateAdditionalInfo, maxLabelLength: 13, isToShowAdditionalInfo: isToShowAdditionalInfo, offsetX: 2, offsetY: -50 - (counter * 35)},
        {signValue: [63], image: 'images/traffic-signs/busLane.png'},
        {signValue: [64], image: 'images/traffic-signs/busLane.png'},
        {signValue: [65], image: 'images/traffic-signs/busLane.png'},
        {signValue: [66], image: 'images/traffic-signs/busStopForLocalTraffic.png'},
        {signValue: [67], image: 'images/traffic-signs/busStopForLocalTraffic.png'},
        {signValue: [68], image: 'images/traffic-signs/busStopForLocalTraffic.png'},
        {signValue: [69], image: 'images/traffic-signs/busStopForLocalTraffic.png'},
        {signValue: [70], image: 'images/traffic-signs/compulsoryFootPath.png'},
        {signValue: [71], image: 'images/traffic-signs/compulsoryFootPath.png'},
        {signValue: [72], image: 'images/traffic-signs/compulsoryFootPath.png'},
        {signValue: [73], image: 'images/traffic-signs/compulsoryFootPath.png'},
        {signValue: [74], image: 'images/traffic-signs/directionToBeFollowed3.png'},
        {signValue: [75], image: 'images/traffic-signs/directionToBeFollowed3.png'},
        {signValue: [76], image: 'images/traffic-signs/directionToBeFollowed3.png'},
        {signValue: [77], image: 'images/traffic-signs/compulsoryRoundabout.png'},
        {signValue: [78], image: 'images/traffic-signs/passThisSide.png'},
        {signValue: [79], image: 'images/traffic-signs/dividerOfTraffic.png'},
        {signValue: [80], image: 'images/traffic-signs/taxiStationZoneBeginning.png'},
        {signValue: [81], image: 'images/traffic-signs/taxiStationZoneBeginning.png'},
        {signValue: [82], image: 'images/traffic-signs/roadNarrows.png'},
        {signValue: [83], image: 'images/traffic-signs/twoWayTraffic.png'},
        {signValue: [84], image: 'images/traffic-signs/swingBridge.png'},
        {signValue: [85], image: 'images/traffic-signs/roadWorks.png'},
        {signValue: [86], image: 'images/traffic-signs/slipperyRoad.png'},
        {signValue: [87], image: 'images/traffic-signs/pedestrianCrossingWarningSign.png'},
        {signValue: [88], image: 'images/traffic-signs/cyclists.png'},
        {signValue: [89], image: 'images/traffic-signs/intersectionWithEqualRoads.png'},
        {signValue: [90], image: 'images/traffic-signs/lightSignals.png'},
        {signValue: [91], image: 'images/traffic-signs/tramwayLine.png'},
        {signValue: [92], image: 'images/traffic-signs/fallingRocks.png'},
        {signValue: [93], image: 'images/traffic-signs/crossWind.png'},
        {signValue: [94], image: 'images/traffic-signs/priorityRoad.png'},
        {signValue: [95], image: 'images/traffic-signs/endOfPriority.png'},
        {signValue: [96], image: 'images/traffic-signs/priorityOverOncomingTraffic.png'},
        {signValue: [97], image: 'images/traffic-signs/priorityForOncomingTraffic.png'},
        {signValue: [98], image: 'images/traffic-signs/giveWay.png'},
        {signValue: [99], image: 'images/traffic-signs/stop.png'},
        {signValue: [100], image: 'images/traffic-signs/standingAndParkingProhibited.png'},
        {signValue: [101], image: 'images/traffic-signs/parkingProhibited.png'},
        {signValue: [102], image: 'images/traffic-signs/parkingProhibitedZone.png'},
        {signValue: [103], image: 'images/traffic-signs/endOfParkingProhibitedZone.png'},
        {signValue: [104], image: 'images/traffic-signs/alternativeParkingOddDays.png'},
        {signValue: [105], image: 'images/traffic-signs/parkingLot.png'},
        {signValue: [106], image: 'images/traffic-signs/oneWayRoad.png'},
        {signValue: [107], image: 'images/traffic-signs/motorway.png'},
        {signValue: [108], image: 'images/traffic-signs/motorwayEnds.png'},
        {signValue: [109], image: 'images/traffic-signs/residentialZone.png'},
        {signValue: [110], image: 'images/traffic-signs/endOfResidentialZone.png'},
        {signValue: [111], image: 'images/traffic-signs/pedestrianZone.png'},
        {signValue: [112], image: 'images/traffic-signs/endOfPedestrianZone.png'},
        {signValue: [113], image: 'images/traffic-signs/noThroughRoad.png'},
        {signValue: [114], image: 'images/traffic-signs/noThroughRoadRight.png'},
        {signValue: [115], image: 'images/traffic-signs/symbolOfMotorway.png'},
        {signValue: [116], image: 'images/traffic-signs/parking.png'},
        {signValue: [117], image: 'images/traffic-signs/itineraryForIndicatedVehicleCategory.png'},
        {signValue: [118], image: 'images/traffic-signs/itineraryForPedestrians.png'},
        {signValue: [119], image: 'images/traffic-signs/itineraryForHandicapped.png'},
        {signValue: [120], image: 'images/traffic-signs/locationSignForTouristService.png'},
        {signValue: [121], image: 'images/traffic-signs/firstAid.png'},
        {signValue: [122], image: 'images/traffic-signs/fillingStation.png'},
        {signValue: [123], image: 'images/traffic-signs/restaurant.png'},
        {signValue: [124], image: 'images/traffic-signs/publicLavatory.png'}
    ];

      var labelProperty = _.find(labelingProperties, function(properties) {
        return _.includes(properties.signValue, trafficSign.type);
      });


      function findImage() {
        return labelProperty && labelProperty.image ? labelProperty.image : 'images/traffic-signs/badValue.png';
      }

      function getTextOffsetX(){
        return labelProperty && labelProperty.offsetX ? labelProperty.offsetX :  0;
      }

      function getTextOffsetY(){
        return labelProperty && labelProperty.offsetY ? labelProperty.offsetY :  -45 - (counter * 35);
      }

      function getValidation(){
        return labelProperty && labelProperty.validation ? labelProperty.validation.call(trafficSign) : false ;
      }

      function getValue(){
        return labelProperty && labelProperty.convertion ? labelProperty.convertion.call(trafficSign) : trafficSign.value;
      }

      function getAdditionalInfo(){
        return labelProperty && labelProperty.isToShowAdditionalInfo ? labelProperty.isToShowAdditionalInfo.call(trafficSign) : '';
      }

      function getUnit() {
        return labelProperty && labelProperty.unit ? labelProperty.unit.call(trafficSign) : '';
      }

      function getMaxLength() {
        return labelProperty && labelProperty.maxLabelLength ? labelProperty.maxLabelLength : 20;
      }

      return {
        findImage: findImage,
        getTextOffsetX: getTextOffsetX,
        getTextOffsetY: getTextOffsetY,
        getValidation: getValidation,
        getValue : getValue,
        getUnit : getUnit,
        getAdditionalInfo: getAdditionalInfo,
        getMaxLength: getMaxLength

      };
    };

    var textStyle = function (trafficSign) {
      if (!getLabelProperty(trafficSign).getValidation())
        return '';
      return getLabelProperty(trafficSign).getValue() + getLabelProperty(trafficSign).getAdditionalInfo() + getLabelProperty(trafficSign).getUnit();
    };

    var addTons = function () {
      return ''.concat('t');
    };

    var addMeters = function() {
      return ''.concat('m');
    };

    var convertToTons = function(){
      return this.value / 1000;
    };

    var convertToMeters = function(){
      return this.value / 100;
    };

    var isToShowAdditionalInfo = function () {
      return _.isEmpty(this.value) ? this.additionalInfo : '';
    };

    var showPartialAdditionalInfo = function () {
      return _.isEmpty(this.value) ? _.first(this.additionalInfo ? this.additionalInfo.split(' ') : '') : '';
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

    this.getStyle = function (trafficSign, counter) {
      return [backgroundStyle(trafficSign, counter), new ol.style.Style({
        text: new ol.style.Text({
          text: textStyle(trafficSign),
          fill: new ol.style.Fill({
            color: '#000000'
          }),
          font: '12px sans-serif',
          offsetX: getLabelProperty(trafficSign, counter).getTextOffsetX(),
          offsetY: getLabelProperty(trafficSign, counter).getTextOffsetY()
        })
      })];
    };

    this.renderFeaturesByPointAssets = function(pointAssets, zoomLevel){
      return me.renderGroupedFeatures(pointAssets, zoomLevel, function(asset){
        return me.getCoordinate(asset);
      });
    };

    this.renderGroupedFeatures = function(assets, zoomLevel, getPoint){
      if(!this.isVisibleZoom(zoomLevel))
        return [];
      var groupedAssets = me.getGroupedFeatures(assets, zoomLevel);
      return _.flatten(_.chain(groupedAssets).map(function(assets){
        return _.map(assets, function(asset, index){
          var value = me.getValue(asset);
          if(value !== undefined){
            var styles = [];
            styles = styles.concat(me.getStickStyle());
            styles = styles.concat(me.getStyle(value, index));
            var feature = me.createFeature(getPoint(asset));
            feature.setStyle(styles);
            feature.setProperties(_.omit(asset, 'geometry'));
            return feature;
          }
        });
      }).filter(function(feature){ return !_.isUndefined(feature); }).value());
    };

    this.createFeature = function(point){
      return new ol.Feature(new ol.geom.Point(point));
    };

    var getProperty = function (asset, publicId) {
      return _.head(_.find(asset.propertyData, function (prop) {
        return prop.publicId === publicId;
      }).values);
    };

    this.getValue = function (asset) {
      if (_.isUndefined(getProperty(asset, "trafficSigns_type")))
        return;
      var value = getProperty(asset, "trafficSigns_value") ? getProperty(asset, "trafficSigns_value").propertyValue : '';
      var additionalInfo = getProperty(asset, "trafficSigns_info") ? getProperty(asset, "trafficSigns_info").propertyValue : '';
      return {value: value, type: parseInt(getProperty(asset, "trafficSigns_type").propertyValue), additionalInfo: additionalInfo};
    };
  };
})(this);
