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
        return _.merge({}, linearAsset, { hasAsset: hasAsset }, type);
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
      if(_.some(linearAssets, function(asset) { return asset.marker;}))
        return me.lineFeatures(me.getNewFeatureProperties(linearAssets));
      else
        return me.lineFeatures(me.getNewFeatureProperties(linearAssets)).concat(me.limitSigns(linearAssets));
    };
    
    me.getSuggested = function(linearAsset) {
      return (_.isUndefined(linearAsset.value) || _.isUndefined(linearAsset.value.isSuggested)) ? false : linearAsset.value.isSuggested;
    };
    
    me.limitSigns = function(linearAssets) {
      return _.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return [point.x, point.y];
        });
        var road = new ol.geom.LineString(points);
        var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
        var type =  {linkId: linearAsset.linkId, type: 'suggestion', suggested: me.getSuggested(linearAsset)};
        var attributes = _.merge(_.cloneDeep(_.omit(linearAsset, "geometry")), type);
      
        var feature = new ol.Feature(new ol.geom.Point([signPosition.x, signPosition.y]));
        feature.setProperties(attributes);
        return feature;
      });
    };
  
    this.renderOverlays = function(linearAssets){};
    this.isUnknown = function(linerAsset){};
    this.dottedLineFeatures = function(linearAssets){};
  
  };
})(this);