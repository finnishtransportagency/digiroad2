(function(root) {
  root.AssetStyle = function() {
    var me = this;

    var isUnknown = function(linearAsset) {
      return !_.isNumber(linearAsset.value);
    };

    var lineFeatures = function(linearAssets) {
      return _.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return [point.x, point.y];
        });
        //For winter speed limits
        var type = isUnknown(linearAsset) ? { type: 'unknown' } : {};

        var attributes = _.merge(_.cloneDeep(_.omit(linearAsset, "geometry")), type);
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(attributes);
        return feature;
      });
    };

    this.getNewFeatureProperties = function(linearAssets){
      var linearAssetsWithType = _.map(linearAssets, function(limit) {
        var expired = _.isUndefined(limit.value);
        return _.merge({}, limit, { type: 'line', expired: expired });
      });
      var offsetBySideCode = function(linearAsset) {
        return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
      };
      var linearAssetsWithAdjustments = _.map(linearAssetsWithType, offsetBySideCode);
      var sortedAssets = _.sortBy(linearAssetsWithAdjustments, function(asset) {
        return asset.expired ? -1 : 1;
      });
      return sortedAssets;
    };

    var dottedLineFeatures = function(speedLimits) {
      var solidLines = lineFeatures(speedLimits);
      var dottedOverlay = lineFeatures(_.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'overlay' }); }));
      return solidLines.concat(dottedOverlay);
    };

    this.renderFeatures = function(linearAssets) {
      return lineFeatures(this.getNewFeatureProperties(linearAssets)).concat(renderFeatures(linearAssets));
    };

    var renderFeatures = function(speedLimits) {
      var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
      var offsetBySideCode = function(speedLimit) {
        return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, speedLimit);
      };
      var speedLimitsWithAdjustments = _.map(speedLimitsWithType, offsetBySideCode);
      var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithAdjustments, function(speedLimit) { return speedLimit.value >= 70; });
      var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
      var highSpeedLimits = speedLimitsSplitAt70kmh[true];

      return dottedLineFeatures(highSpeedLimits);
    };
  };
})(this);