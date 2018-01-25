(function(root) {

  root.WidthLimitLabel = function(){
    WeightLimitLabel.call(this);
    var me = this;

    this.getImage = function () {
      return 'images/greenlabeling.png';
    };

    var getValues = function (asset) {
      //TODO check json format to return values
    };

    this.getValue = function (asset) {
      //TODO check json format to return value
    };

    var getTypeId = function (asset) {
      //TODO check json format to return typeId
    };


    this.renderFeatures = function (assets, zoomLevel, getPoint) {
      if (!me.isVisibleZoom(zoomLevel))
        return [];

      return _.chain(assets).
      map(function(asset){
        //TODO check json format to obtain values
        // var assetValue = me.getValue(asset);
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