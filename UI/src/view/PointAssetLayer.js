(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      collection = params.collection,
      map = params.map;

    Layer.call(this, 'pedestrianCrossing', roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;

    this.refreshView = function() {
      collection.fetch(map.getExtent()).then(function() {
      });
    };

    this.activateSelection = function() {
    };
  };
})(this);