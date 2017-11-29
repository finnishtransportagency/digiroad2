(function(root) {

  root.TrafficSignLabel = function() {
    AssetLabel.call(this);
    var me = this;

    var MIN_DISTANCE = 3;
    var populatedPoints = [];

    var backgroundStyle = function (trafficSign, counter) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getLabelProperty(trafficSign, counter).findImage(),
          anchor : [0.5, 1+(counter)]
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
        {signValue: [5], image:  'images/traffic-signs/urbanAreaSign.png'},
        {signValue: [6], image: 'images/traffic-signs/endOfUrbanAreaSign.png'},
        {signValue: [7], image: 'images/traffic-signs/crossingSign.png'},
        {signValue: [8], image: 'images/traffic-signs/maximum_length.png', validation: validateMaximumRestrictions, offset: -8 - (counter * 30),convertion: convertToMeters, unit: addMeters},
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
        {signValue: [29], image: 'images/traffic-signs/endOfOvertakingProhibitonSign.png'},
        {signValue: [30], image: 'images/traffic-signs/maxWidthSign.png', validation: validateMaximumRestrictions, convertion: convertToMeters},
        {signValue: [31], image: 'images/traffic-signs/maxHeightSign.png', validation: validateMaximumRestrictions, convertion: convertToMeters},
        {signValue: [32], image: 'images/traffic-signs/totalWeightLimit.png', validation: validateMaximumRestrictions, offset: -15 - (counter * 30), convertion: convertToTons, unit: addTons},
        {signValue: [33], image: 'images/traffic-signs/trailerTruckWeightLimit.png', validation: validateMaximumRestrictions, offset: -10 - (counter * 30), convertion: convertToTons, unit: addTons},
        {signValue: [34], image: 'images/traffic-signs/axleWeightLimit.png', validation: validateMaximumRestrictions, offset: -18 - (counter * 30), convertion: convertToTons, unit: addTons },
        {signValue: [35], image: 'images/traffic-signs/bogieWeightLimit.png', validation: validateMaximumRestrictions, offset: -18 - (counter * 30), convertion: convertToTons, unit: addTons },
        {signValue: [36], image: 'images/traffic-signs/rightBendSign.png'},
        {signValue: [37], image: 'images/traffic-signs/leftBendSign.png'},
        {signValue: [38], image: 'images/traffic-signs/severalBendRightSign.png'},
        {signValue: [39], image: 'images/traffic-signs/severalBendLeftSign.png'},
        {signValue: [40], image: 'images/traffic-signs/dangerousDescentSign.png'},
        {signValue: [41], image: 'images/traffic-signs/steepAscentSign.png'},
        {signValue: [42], image: 'images/traffic-signs/unevenRoadSign.png'},
        {signValue: [43], image: 'images/traffic-signs/childrenSign.png'}
      ];

      var labelProperty = _.find(labelingProperties, function(properties) {
          return _.contains(properties.signValue, trafficSign.type);
      });


      function findImage() {
        return labelProperty && labelProperty.image ? labelProperty.image : 'images/traffic-signs/badValue.png';
      }

      function getTextOffset(){
        return labelProperty && labelProperty.offset ? labelProperty.offset :  -15 - (counter * 30);
      }

      function getValidation(){
        return labelProperty && labelProperty.validation ? labelProperty.validation.call(trafficSign) : false ;
      }

      function getValue(){
        return labelProperty && labelProperty.convertion ? labelProperty.convertion.call(trafficSign) : trafficSign.value;
      }

      function getUnit() {
        return labelProperty && labelProperty.unit ? labelProperty.unit.call(trafficSign) : '';
      }

      return {
        findImage: findImage,
        getTextOffset: getTextOffset,
        getValidation: getValidation,
        getValue : getValue,
        getUnit : getUnit
      };
    };

    var textStyle = function (trafficSign) {
      if (!getLabelProperty(trafficSign).getValidation())
        return '';
      return getLabelProperty(trafficSign).getValue() + getLabelProperty(trafficSign).getUnit();
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

    var validateSpeedLimitValues = function () {
      return this.value && (this.value > 0 && this.value <= 120);
    };

    var validateMaximumRestrictions = function () {
      // Not specified the maximum restriction value
      return this.value && (this.value > 0 && this.value < 100000);
    };

    this.getStyle = function (trafficSign, counter) {
      return [backgroundStyle(trafficSign, counter), new ol.style.Style({
        text: new ol.style.Text({
          text: textStyle(trafficSign),
          fill: new ol.style.Fill({
            color: '#000000'
          }),
          font: '12px sans-serif',
          offsetX: 0,
          offsetY: getLabelProperty(trafficSign, counter).getTextOffset()
        })
      })];
    };

    this.renderFeaturesByPointAssets = function(pointAssets, zoomLevel){
      clearPoints();
      return me.renderFeatures(pointAssets, zoomLevel, function(asset){
        return me.getCoordinateForGrouping(asset);
      });
    };

    this.renderFeatures = function(assets, zoomLevel, getPoint){
      if(!this.isVisibleZoom(zoomLevel))
        return [];

      return _.chain(assets).
      map(function(asset){
        var trafficSign = me.getValue(asset);
        var assetLocation = getPoint(asset);
        if(trafficSign !== undefined){
          var styles = [];
          styles = styles.concat(me.getStickStyle());
          styles = styles.concat(me.getStyle(trafficSign, assetLocation[1]));
          var feature = me.createFeature(assetLocation[0]);
          feature.setStyle(styles);
          feature.setProperties(asset);
          return feature;
        }
      }).
      filter(function(feature){ return !_.isUndefined(feature); }).
      value();
    };

    this.createFeature = function(point){
      return new ol.Feature(new ol.geom.Point(point));
    };

    var getProperty = function (asset, publicId) {
      return _.first(_.find(asset.propertyData, function (prop) {
        return prop.publicId === publicId;
      }).values);
    };

    this.getValue = function (asset) {
      if (_.isUndefined(getProperty(asset, "trafficSigns_type")))
          return;
      var value = getProperty(asset, "trafficSigns_value") ? getProperty(asset, "trafficSigns_value").propertyValue : '';
      return {value : value, type: parseInt(getProperty(asset, "trafficSigns_type").propertyValue)};
    };

    var clearPoints = function () {
      populatedPoints = [];
    };

    var isInProximity = function (pointA, pointB) {
      return Math.sqrt(geometrycalculator.getSquaredDistanceBetweenPoints(pointA, pointB.coordinate)) < MIN_DISTANCE;
    };

    this.getCoordinateForGrouping = function(point){
      var assetCoordinate = {lon : point.lon, lat : point.lat};
      var assetCounter = {counter: 1};
      if(_.isEmpty(populatedPoints)){
        populatedPoints.push({coordinate: assetCoordinate, counter: 1});
      }else{
        var populatedPoint = _.find(populatedPoints, function (p) {
          return isInProximity(point, p);
        });
        if (!_.isUndefined(populatedPoint)) {
          assetCoordinate = populatedPoint.coordinate;
          assetCounter.counter = populatedPoint.counter + 1;
          populatedPoint.counter++;
        } else {
          populatedPoints.push({coordinate: assetCoordinate, counter: 1});
        }
      }
      return [[assetCoordinate.lon, assetCoordinate.lat], assetCounter.counter];
    };
  };
})(this);
