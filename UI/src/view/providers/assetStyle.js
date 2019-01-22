(function(root) {
  root.AssetStyle = function() {
    var me = this;

    this.lineFeatures = function(linearAssets) {
      return _.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return [point.x, point.y];
        });
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties(linearAsset);
        return feature;
      });
    };

    this.hasValue = function(linearAsset) {
      var id = linearAsset.id;
      var value = linearAsset.value;
      var nullOrUndefined = function(id){return _.isUndefined(id) || _.isNull(id);};
      var hasValueProperty = function(){return linearAsset.hasOwnProperty('value');};

      return (nullOrUndefined(id) && !_.isUndefined(value)) || (!nullOrUndefined(id) && !hasValueProperty()) ||  (!nullOrUndefined(id) && !_.isUndefined(value));
    };

    this.getNewFeatureProperties = function(linearAssets){
      var linearAssetsWithType = _.map(linearAssets, function(linearAsset) {
        var hasAsset = me.hasValue(linearAsset);
        var type =  me.isUnknown(linearAsset) ? { type: 'unknown' } : {type: 'line'};
        var sideCode = linearAsset.sideCode ? linearAsset.sideCode : 1;
        return _.merge({}, linearAsset, { hasAsset: hasAsset }, type, {sideCode: sideCode});
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
      return me.lineFeatures(me.getNewFeatureProperties(linearAssets));
    };

    this.renderOverlays = function(linearAssets){};
    this.isUnknown = function(linerAsset){};
    this.dottedLineFeatures = function(linearAssets){};
    this.limitSigns = function (speedLimits) {};
  };
})(this);