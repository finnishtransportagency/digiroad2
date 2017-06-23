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

    me.getNewFeatureProperties = function(linearAssets){
      var linearAssetsWithType = _.map(linearAssets, function(linearAsset) {
        var expired = _.isUndefined(linearAsset.value);
        var type =  isUnknown(linearAsset) ? { type: 'unknown' } : {type: 'line'};
        return _.merge({}, linearAsset, { expired: expired }, type);
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

    me.renderFeatures = function(linearAssets) {
      return lineFeatures(me.getNewFeatureProperties(linearAssets)).concat(renderFeatures(linearAssets));
    };

    //For winter speed limits
    var renderFeatures = function(linearAssets) {
      var speedLimitsWithType = _.map(linearAssets, function(linearAsset) { return _.merge({}, linearAsset, { type: 'other' }); });
      var offsetBySideCode = function(linearAsset) {
        return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
      };
      var speedLimitsWithAdjustments = _.map(speedLimitsWithType, offsetBySideCode);
      var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithAdjustments, function(linearAsset) { return linearAsset.value >= 70; });
      return dottedLineFeatures(speedLimitsSplitAt70kmh[true]);
    };

    //For winter speed limits
    var dottedLineFeatures = function(linearAssets) {
      var solidLines = lineFeatures(linearAssets);
      var dottedOverlay = lineFeatures(_.map(linearAssets, function(linearAsset) { return _.merge({}, linearAsset, { type: 'overlay' }); }));
      return solidLines.concat(dottedOverlay);
    };

    var isUnknown = function(linearAsset) {
      return !_.isNumber(linearAsset.value);
    };
  };
})(this);