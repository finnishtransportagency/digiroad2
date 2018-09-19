(function(root) {

  root.TrafficSignLabel = function(groupingDistance) {
    SignsLabel.call(this, this.MIN_DISTANCE);
    var me = this;

    me.MIN_DISTANCE = groupingDistance;


    me.getSignType = function (sign) { return sign.type;};

    me.getPropertiesConfiguration = function (counter) {
      return [
        {signValue: [1], image: 'images/traffic-signs/speedLimitSign.png', validation: validateSpeedLimitValues},
        {signValue: [2], image: 'images/traffic-signs/endOfSpeedLimitSign.png', validation: validateSpeedLimitValues},
        {signValue: [3], image: 'images/traffic-signs/speedLimitZoneSign.png', validation: validateSpeedLimitValues},
        {signValue: [4], image: 'images/traffic-signs/endOfSpeedLimitZoneSign.png', validation: validateSpeedLimitValues},
        {signValue: [5], image: 'images/traffic-signs/urbanAreaSign.png', offset: -8 - (counter * 35)},
        {signValue: [6], image: 'images/traffic-signs/endOfUrbanAreaSign.png'},
        {signValue: [7], image: 'images/traffic-signs/crossingSign.png'},
        {signValue: [8], image: 'images/traffic-signs/maximumLengthSign.png', validation: validateMaximumRestrictions, offset: -38 - (counter * 35), convertion: me.convertToMeters, unit: me.addMeters},
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
        {signValue: [30], image: 'images/traffic-signs/maxWidthSign.png', validation: validateMaximumRestrictions, convertion: me.convertToMeters},
        {signValue: [31], image: 'images/traffic-signs/maxHeightSign.png', validation: validateMaximumRestrictions, convertion: me.convertToMeters, unit: me.addMeters},
        {signValue: [32], image: 'images/traffic-signs/totalWeightLimit.png', validation: validateMaximumRestrictions, convertion: me.convertToTons, unit: me.addTons},
        {signValue: [33], image: 'images/traffic-signs/trailerTruckWeightLimit.png', validation: validateMaximumRestrictions, offset: -38 - (counter * 35), convertion: me.convertToTons, unit: me.addTons},
        {signValue: [34], image: 'images/traffic-signs/axleWeightLimit.png', validation: validateMaximumRestrictions, offset: -46 - (counter * 35), convertion: me.convertToTons, unit: me.addTons },
        {signValue: [35], image: 'images/traffic-signs/bogieWeightLimit.png', validation: validateMaximumRestrictions, offset: -46 - (counter * 35), convertion: me.convertToTons, unit: me.addTons },
        {signValue: [36], image: 'images/traffic-signs/rightBendSign.png'},
        {signValue: [37], image: 'images/traffic-signs/leftBendSign.png'},
        {signValue: [38], image: 'images/traffic-signs/severalBendRightSign.png'},
        {signValue: [39], image: 'images/traffic-signs/severalBendLeftSign.png'},
        {signValue: [40], image: 'images/traffic-signs/dangerousDescentSign.png'},
        {signValue: [41], image: 'images/traffic-signs/steepAscentSign.png'},
        {signValue: [42], image: 'images/traffic-signs/unevenRoadSign.png'},
        {signValue: [43], image: 'images/traffic-signs/childrenSign.png'}
      ];
    };


    var validateSpeedLimitValues = function () {
      return this.value && (this.value > 0 && this.value <= 120);
    };

    var validateMaximumRestrictions = function () {
      // Not specified the maximum restriction value
      return this.value && (this.value > 0 && this.value < 100000);
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
      return {value: value, type: parseInt(getProperty(asset, "trafficSigns_type").propertyValue)};
    };
  };
})(this);
