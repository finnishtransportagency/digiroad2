window.SpeedLimitLayer = function(backend) {
  backend = backend || Backend;
  var eventListener = _.extend({started: false}, eventbus);

  var dottedOverlayStyle = {
    strokeWidth: 5,
    strokeColor: '#ffffff',
    strokeDashstyle: '1 12',
    strokeLinecap: 'square'
  };
  var speedLimitStyleLookup = {
    20:  { strokeColor: '#00ccdd' },
    30:  { strokeColor: '#ff55dd' },
    40:  { strokeColor: '#11bb00' },
    50:  { strokeColor: '#ff0000' },
    60:  { strokeColor: '#0011bb' },
    70:  { strokeColor: '#00ccdd' },
    80:  { strokeColor: '#ff55dd' },
    100: { strokeColor: '#11bb00' },
    120: { strokeColor: '#ff0000' }
  };
  var styleMap = new OpenLayers.StyleMap({
    default: new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeWidth: 6,
      strokeOpacity: 0.7
    }))
  });
  styleMap.addUniqueValueRules('default', 'limit', speedLimitStyleLookup);
  var vectorLayer = new OpenLayers.Layer.Vector('speedLimit', { styleMap: styleMap });
  vectorLayer.setOpacity(1);

  var update = function(zoom, boundingBox) {
    if (zoomlevels.isInAssetZoomLevel(zoom)) {
      start();
      backend.getSpeedLimits(boundingBox);
    }
  };

  var start = function() {
    if (!eventListener.started) {
      eventListener.started = true;
      eventListener.listenTo(eventbus, 'speedLimits:fetched', drawSpeedLimits);
    }
  };

  var stop = function() {
    eventListener.stopListening(eventbus);
    eventListener.started = false;
  };

  eventbus.on('map:moved', function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === 'speedLimit') {
      start();
      backend.getSpeedLimits(state.bbox);
    } else {
      vectorLayer.removeAllFeatures();
      stop();
    }
  }, this);

  var drawSpeedLimits = function(speedLimits) {
    var speedLimitsSplitAt70kmh = _.groupBy(speedLimits, function(speedLimit) { return speedLimit.limit >= 70; });
    var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
    var highSpeedLimits = speedLimitsSplitAt70kmh[true];

    vectorLayer.removeAllFeatures();
    vectorLayer.addFeatures(lineFeatures(lowSpeedLimits));
    vectorLayer.addFeatures(dottedLineFeatures(highSpeedLimits));
  };

  var dottedLineFeatures = function(speedLimits) {
    var solidLines = lineFeatures(speedLimits);
    var dottedOverlay = lineFeatures(speedLimits, dottedOverlayStyle);
    return solidLines.concat(dottedOverlay);
  };

  var lineFeatures = function(speedLimits, customStyle) {
    return _.map(speedLimits, function(speedLimit) {
      var points = _.map(speedLimit.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), speedLimit, customStyle);
    });
  };

  return {
    update: update,
    vectorLayer: vectorLayer
  };
};
