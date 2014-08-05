window.LinearAssetLayer = function(backend) {
  backend = backend || Backend;
  var eventListener = _.extend({started: false}, eventbus);

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
      start();
      backend.getLinearAssets(boundingBox);
    }
  };

  var start = function() {
    if (!eventListener.started) {
      eventListener.started = true;
      eventListener.listenTo(eventbus, 'linearAssets:fetched', drawLinearAssets);
    }
  };

  var stop = function() {
    eventListener.stopListening(eventbus);
    eventListener.started = false;
  };

  eventbus.on('map:moved', function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === 'linearAsset') {
      start();
      backend.getLinearAssets(state.bbox);
    } else {
      vectorLayer.removeAllFeatures();
      stop();
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

  return {
    update: update,
    vectorLayer: vectorLayer
  };
};
