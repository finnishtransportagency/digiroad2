(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer;

    Layer.call(this, 'pedestrianCrossing', roadLayer);
  };
})(this);