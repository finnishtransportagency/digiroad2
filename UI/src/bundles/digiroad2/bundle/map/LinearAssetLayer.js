window.LinearAssetLayer = function(backend) {
  backend = backend || Backend;
  var eventListener = _.extend({started: false}, eventbus);

  var speedLimitStyleLookup = {
    20: { strokeColor: '#00ccdd' },
    30: { strokeColor: '#ff55dd' },
    40: { strokeColor: '#11bb00' },
    50: { strokeColor: '#ff0000' },
    60: { strokeColor: '#0011bb' }
  };
  var styleMap = new OpenLayers.StyleMap({
    default: new OpenLayers.Style(OpenLayers.Util.applyDefaults({ strokeWidth: 6 }))
  });
  styleMap.addUniqueValueRules('default', 'limit', speedLimitStyleLookup);
  var vectorLayer = new OpenLayers.Layer.Vector('linearAsset', { styleMap: styleMap });
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
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), linearAsset);
    });
    vectorLayer.addFeatures(features);
  };

  return {
    update: update,
    vectorLayer: vectorLayer
  };
};
