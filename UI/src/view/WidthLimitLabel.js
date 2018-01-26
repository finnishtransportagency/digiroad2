(function(root) {

  root.WidthLimitLabel = function(){
    WeightLimitLabel.call(this);
    var me = this;

    this.getImage = function () {
      return 'images/greenLabeling.png';
    };

    this.renderFeatures = function (assets, zoomLevel, getPoint) {
      if (!me.isVisibleZoom(zoomLevel))
        return [];

      return _.chain(assets).
      map(function(asset){
        var style = me.getStyle(asset);
        var feature = me.createFeature(getPoint(asset));
        feature.setStyle(style);
        return feature;
      }).
      filter(function(feature){
        return !_.isUndefined(feature);
      }).
      value();
    };
  };
})(this);