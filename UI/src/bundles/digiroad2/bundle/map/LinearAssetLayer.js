window.LinearAssetLayer = function(map, backend) {
  backend = backend || Backend;

  var vectorLayer = new OpenLayers.Layer.Vector("linearAsset", {
    styleMap: new OpenLayers.StyleMap({
      "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
        strokeColor: "#B22222",
        strokeWidth: 8
      }))
    })
  });
  vectorLayer.setOpacity(1);

  var update = function(zoom, boundingBox) {
    if (zoomlevels.isInAssetZoomLevel(zoom)) {
      backend.getLinearAssets(boundingBox);
    }
  };

  eventbus.on('map:moved', function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === 'linearAsset') {
      backend.getLinearAssets(state.bbox);
    } else {
      vectorLayer.removeAllFeatures();
    }
  }, this);

  var drawLinearAssets = function(linearAssets) {
    vectorLayer.removeAllFeatures();
    var features = _.map(linearAssets, function(linearAsset) {
      var points = _.map(linearAsset.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), null);
    });
    vectorLayer.addFeatures(features);
  };

  eventbus.on('linearAssets:fetched', function(linearAssets) {
    if (zoomlevels.isInAssetZoomLevel(map.getZoom())) {
      drawLinearAssets(linearAssets);
    }
  }, this);

  return {
    update: update,
    vectorLayer: vectorLayer
  };
};
