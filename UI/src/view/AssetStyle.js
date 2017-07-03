(function(root) {
  root.AssetStyle = function() {
    var me = this;

    var lineFeatures = function(linearAssets) {
      return _.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return [point.x, point.y];
        });
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(linearAsset);
        return feature;
      });
    };

    var getNewFeatureProperties = function(linearAssets){
      var linearAssetsWithType = _.map(linearAssets, function(linearAsset) {
        var expired = _.isUndefined(linearAsset.value);
        var type =  isUnknown(linearAsset) ? { type: 'unknown' } : {type: 'line'};
        return _.merge({}, linearAsset, { expired: expired }, type);
      });
      var offsetBySideCode = function(linearAsset) {
        return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
      };
      var linearAssetsWithAdjustments = _.map(linearAssetsWithType, offsetBySideCode);
      return _.sortBy(linearAssetsWithAdjustments, function(asset) {
        return asset.expired ? -1 : 1;
      });
    };

    me.renderFeatures = function(linearAssets) {
      return lineFeatures(getNewFeatureProperties(linearAssets)).concat(renderFeatures(linearAssets));
    };

    //Used winter speed limits
    var renderFeatures = function(linearAssets) {
      var speedLimitsWithType = _.map(linearAssets, function(linearAsset) { return _.merge({}, linearAsset, { type: 'other' }); });
      var offsetBySideCode = function(linearAsset) {
        return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
      };
      var speedLimitsWithAdjustments = _.map(speedLimitsWithType, offsetBySideCode);
      var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithAdjustments, function(linearAsset) { return linearAsset.value >= 70; });
      return dottedLineFeatures(speedLimitsSplitAt70kmh[true]).concat(limitSigns(speedLimitsWithAdjustments));
    };

    //Used winter speed limits -for icons features
    var limitSigns = function(speedLimits) {
      return _.map(speedLimits, function(speedLimit) {
        var points = _.map(speedLimit.points, function(point) {
          return [point.x, point.y];
        });
        var road = new ol.geom.LineString(points);
        var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
        var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
        var attributes = _.merge(_.cloneDeep(_.omit(speedLimit, "geometry")), type);

        var feature = new ol.Feature(new ol.geom.Point([signPosition.x, signPosition.y]));
        feature.setProperties(attributes);
        return feature;
      });
    };

    //Used winter speed limits
    var dottedLineFeatures = function(linearAssets) {
      var solidLines = lineFeatures(linearAssets);
      var dottedOverlay = lineFeatures(_.map(linearAssets, function(linearAsset) { return _.merge({}, linearAsset, { type: 'overlay' }); }));
      return solidLines.concat(dottedOverlay);
    };

    //Used winter speed limits
    var isUnknown = function(linearAsset) {
      return !_.isNumber(linearAsset.value);
    };
  };
})(this);