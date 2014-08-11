window.SpeedLimitLayer = function(backend) {
  backend = backend || Backend;
  var eventListener = _.extend({started: false}, eventbus);

  var dottedOverlayStyle = {
    strokeWidth: 4,
    strokeColor: '#ffffff',
    strokeDashstyle: '1 12',
    strokeLinecap: 'square'
  };
  var speedLimitStyleLookup = {
    20:  { strokeColor: '#00ccdd', externalGraphic: 'images/speed-limits/20.svg' },
    30:  { strokeColor: '#ff55dd', externalGraphic: 'images/speed-limits/30.svg' },
    40:  { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/40.svg' },
    50:  { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/50.svg' },
    60:  { strokeColor: '#0011bb', externalGraphic: 'images/speed-limits/60.svg' },
    70:  { strokeColor: '#00ccdd', externalGraphic: 'images/speed-limits/70.svg' },
    80:  { strokeColor: '#ff55dd', externalGraphic: 'images/speed-limits/80.svg' },
    100: { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/100.svg' },
    120: { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/120.svg' }
  };
  var styleMap = new OpenLayers.StyleMap({
    default: new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeWidth: 6,
      strokeOpacity: 0.7,
      pointRadius: 20
    }))
  });
  styleMap.addUniqueValueRules('default', 'limit', speedLimitStyleLookup);
  var vectorLayer = new OpenLayers.Layer.Vector('speedLimit', { styleMap: styleMap });
  vectorLayer.setOpacity(1);

  var update = function(zoom, boundingBox) {
    if (zoomlevels.isInAssetZoomLevel(zoom)) {
      start(zoom);
      backend.getSpeedLimits(boundingBox);
    }
  };

  var start = function(zoom) {
    adjustLineWidths(zoom);
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
    if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === 'speedLimit') {
      start(state.zoom);
      backend.getSpeedLimits(state.bbox);
    } else {
      vectorLayer.removeAllFeatures();
      stop();
    }
  }, this);

  var adjustLineWidths = function(zoomLevel) {
    var widthBase = 2 + (zoomLevel - zoomlevels.minZoomForRoadLinks);
    var strokeWidth = widthBase * widthBase;
    styleMap.styles.default.defaultStyle.strokeWidth = strokeWidth;
    dottedOverlayStyle.strokeWidth = strokeWidth - 2;
    dottedOverlayStyle.strokeDashstyle = '1 ' + 2 * strokeWidth;
    vectorLayer.redraw();
  };

  var drawSpeedLimits = function(speedLimits) {
    var speedLimitsSplitAt70kmh = _.groupBy(speedLimits, function(speedLimit) { return speedLimit.limit >= 70; });
    var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
    var highSpeedLimits = speedLimitsSplitAt70kmh[true];

    vectorLayer.removeAllFeatures();
    vectorLayer.addFeatures(lineFeatures(lowSpeedLimits));
    vectorLayer.addFeatures(dottedLineFeatures(highSpeedLimits));
    vectorLayer.addFeatures(limitSigns(speedLimits));
  };

  var dottedLineFeatures = function(speedLimits) {
    var solidLines = lineFeatures(speedLimits);
    var dottedOverlay = lineFeatures(speedLimits, dottedOverlayStyle);
    return solidLines.concat(dottedOverlay);
  };

  var limitSigns = function(speedLimits) {
    return _.map(speedLimits, function(speedLimit) {
      var point = _.first(speedLimit.points);
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(point.x, point.y), speedLimit);
    });
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
