(function(root) {

  root.SuggestionLabel = function() {
    AssetLabel.call(this);
    var me = this;

    var getProperty = function (asset, publicId) {
      return _.head(_.find(asset.propertyData, function (prop) {
        return prop.publicId === publicId;
      }).values);
    };

    me.renderFeaturesByPointAssets = function(pointAssets, zoomLevel){
      return me.renderGroupedFeatures(pointAssets, zoomLevel, function(asset){
        return me.getCoordinate(asset);
      });
    };

    this.renderGroupedFeatures = function(assets, zoomLevel, getPoint){
      if(!this.isVisibleZoom(zoomLevel))
        return [];
      var groupedAssets = me.getGroupedFeatures(assets, zoomLevel);
      return _.flatten(_.chain(groupedAssets).map(function(assets){

        return _.map(assets, function(asset){
          var styles = [];
          styles = me.suggestionStyle(getProperty(asset,"suggest_box"), styles, {x:0.5 , y:0});

          var feature = me.createFeature(getPoint(asset));
          feature.setStyle(styles);
          feature.setProperties(_.omit(asset, 'geometry'));
          return feature;
        });
      }).filter(function(feature){ return !_.isUndefined(feature); }).value());
    };
  };
})(this);
