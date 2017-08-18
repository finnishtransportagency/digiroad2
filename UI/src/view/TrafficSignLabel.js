(function(root) {

  root.TrafficSignLabel = function() {
    AssetLabel.call(this);
    var me = this;

    var propertyText = '';
    var populatedPoints = [];

    var backgroundStyle = function (value, counter) {

      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getImage(value),
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

    var getImage = function (value) {
        var images = {
          'images/traffic-signs/speedLimitSign.png':              {signValue: [1, 8]},
          'images/traffic-signs/endOfSpeedLimitSign.png':         {signValue: [2]},
          'images/traffic-signs/speedLimitZoneSign.png':          {signValue: [3]},
          'images/traffic-signs/endOfSpeedLimitZoneSign.png':     {signValue: [4]},
          'images/traffic-signs/urbanAreaSign.png':               {signValue: [5]},
          'images/traffic-signs/endOfUrbanAreaSign.png':          {signValue: [6]},
          'images/traffic-signs/crossingSign.png':                {signValue: [7]},
          'images/traffic-signs/warningSign.png':                 {signValue: [9]},
          'images/traffic-signs/turningRestrictionLeftSign.png':  {signValue: [10]},
          'images/traffic-signs/turningRestrictionRightSign.png': {signValue: [11]},
          'images/traffic-signs/uTurnRestrictionSign.png':        {signValue: [12]}
        };
        return _.findKey(images, function (image) {
          return _.contains(image.signValue, value);
        });
    };

    var textStyle = function (value) {
      if (!correctValue(value))
        return '';
      return "" + value;
    };

    var correctValue = function (value) {
      var valueLength = value.toString().length;
      if (!value || (valueLength > 3 || value < 0) || value > 120)
        return false;
      return true;
    };

    this.getStyle = function (value, counter) {
      return [backgroundStyle(value, counter), new ol.style.Style({
        text: new ol.style.Text({
          text: textStyle(propertyText),
          fill: new ol.style.Fill({
            color: '#000000'
          }),
          font: 'bold 12px sans-serif',
          offsetX: 0,
          offsetY: -15 - (counter * 30)
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
        var assetValue = me.getValue(asset);
        var assetLocation = getPoint(asset);
        if(assetValue !== undefined){
          var styles = [];
          styles = styles.concat(me.getStickStyle());
          styles = styles.concat(me.getStyle(assetValue, assetLocation[1]));
          var feature = me.createFeature(assetLocation[0]);
          feature.setStyle(styles);
          feature.setProperties(asset);
          return feature;
        }
      }).
      filter(function(feature){ return feature !== undefined; }).
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

    var handleValue = function (asset) {
      propertyText = '';
      if (_.isUndefined(getProperty(asset, "trafficSigns_type")))
        return;
      var trafficSignType = parseInt(getProperty(asset, "trafficSigns_type").propertyValue);
      if (trafficSignType < 7 || trafficSignType == 8)
        setProperty(asset);
      return trafficSignType;
    };

    var setProperty = function (asset) {
      var existingValue = getProperty(asset, "trafficSigns_value");
      if (existingValue)
        propertyText = existingValue.propertyValue;
    };

    this.getValue = function (asset) {
      return handleValue(asset);
    };

    var clearPoints = function () {
      populatedPoints = [];
    };

    var isInProximity = function (pointA, pointB) {
      return Math.sqrt(geometrycalculator.getSquaredDistanceBetweenPoints(pointA, pointB.coordinate)) < 6;
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
