var getKey = function(speedLimit) {
  return speedLimit.id + '-' + speedLimit.roadLinkId;
};

var SpeedLimitsCollection = function(backend) {
  var speedLimits = {};

  this.fetch = function(boundingBox) {
    backend.getSpeedLimits(boundingBox, function(fetchedSpeedLimits) {
      _.merge(speedLimits, _.reduce(fetchedSpeedLimits, function(acc, speedLimit) {
        acc[getKey(speedLimit)] = speedLimit;
        return acc;
      }, {}));
      eventbus.trigger('speedLimits:fetched', speedLimits);
    });
  };

  this.get = function(id) {
    return speedLimits[id];
  };
};

var SelectedSpeedLimit = function(collection) {
  var current = null;

  this.open = function(id) {
    if (current) {
      current.isSelected = false;
    }
    current = collection.get(id);
    current.isSelected = true;
    console.log('selected speed limit is:', current);
  };

  this.close = function() {
    if (current) {
      current.isSelected = false;
      current = null;
    }
  };

  this.exists = function() {
    return current !== null;
  };

  this.getKey = function() {
    return getKey(current);
  };

  this.getStartAndEndPoint = function() {
    return [_.first(current.points), _.last(current.points)];
  };
};

window.SpeedLimitLayer = function(map, backend) {
  var eventListener = _.extend({started: false}, eventbus);
  var collection = new SpeedLimitsCollection(backend);
  var selectedSpeedLimit = new SelectedSpeedLimit(collection);

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
  var browseStyle = new OpenLayers.StyleMap({
    default: new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeWidth: 6,
      strokeOpacity: 0.7,
      pointRadius: 20
    }))
  });
  browseStyle.addUniqueValueRules('default', 'limit', speedLimitStyleLookup);

  var selectionStyle = new OpenLayers.StyleMap({
    default: new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeWidth: 6,
      strokeOpacity: 0.3,
      graphicOpacity: 0.5
    })),
    select: new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.7,
      graphicOpacity: 1.0
    }))
  });
  selectionStyle.addUniqueValueRules('default', 'limit', speedLimitStyleLookup);

  var selectionEndPointStyle = {
    externalGraphic: 'images/speed-limits/selected.svg',
    pointRadius: 15
  };

  var vectorLayer = new OpenLayers.Layer.Vector('speedLimit', { styleMap: browseStyle });
  vectorLayer.setOpacity(1);

  var selectionFeatures;
  var createSelectionEndPoints = function(points) {
    return _.map(points, function(point) {
      return new OpenLayers.Feature.Vector(
        new OpenLayers.Geometry.Point(point.x, point.y), null, selectionEndPointStyle);
    });
  };

  var selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
    onSelect: function(feature) {
      selectedSpeedLimit.open(getKey(feature.attributes));
      vectorLayer.styleMap = selectionStyle;
      _.each(vectorLayer.features, function(x) {
        if (getKey(x.attributes) === getKey(feature.attributes)) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
      selectionFeatures = createSelectionEndPoints(selectedSpeedLimit.getStartAndEndPoint());
      vectorLayer.addFeatures(selectionFeatures);
      vectorLayer.redraw();
    },
    onUnselect: function() {
      if (selectedSpeedLimit.exists()) {
        vectorLayer.removeFeatures(selectionFeatures);
        _.each(_.filter(vectorLayer.features, function(feature) {
          return getKey(feature.attributes) === selectedSpeedLimit.getKey();
        }), function(feature) {
          selectControl.unhighlight(feature);
        });
      }
      selectedSpeedLimit.close();
      vectorLayer.styleMap = browseStyle;
      vectorLayer.redraw();
    }
  });
  map.addControl(selectControl);

  var update = function(zoom, boundingBox) {
    if (zoomlevels.isInAssetZoomLevel(zoom)) {
      start(zoom);
      collection.fetch(boundingBox);
    }
  };

  var zoomToSize = {10: 13,
                    11: 16,
                    12: 20};

  var adjustSigns = function(zoom) {
    var pointRadius = zoomToSize[zoom] || 0;
    browseStyle.styles.default.defaultStyle.pointRadius = pointRadius;
    selectionStyle.styles.default.defaultStyle.pointRadius = pointRadius;
  };

  var start = function(zoom) {
    adjustSigns(zoom);
    adjustLineWidths(zoom);
    selectionEndPointStyle.pointRadius = zoomToStrokeWidth[zoom];
    vectorLayer.redraw();
    if (!eventListener.started) {
      eventListener.started = true;
      eventListener.listenTo(eventbus, 'speedLimits:fetched', drawSpeedLimits);
    }
    selectControl.activate();
  };

  var stop = function() {
    selectControl.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.started = false;
  };

  eventbus.on('map:moved', function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === 'speedLimit') {
      start(state.zoom);
      collection.fetch(state.bbox);
    } else {
      vectorLayer.removeAllFeatures();
      stop();
    }
  }, this);

  var zoomToStrokeWidth = {9: 3,
                           10: 5,
                           11: 9,
                           12: 16};

  var adjustLineWidths = function(zoomLevel) {
    var strokeWidth = zoomToStrokeWidth[zoomLevel];
    browseStyle.styles.default.defaultStyle.strokeWidth = strokeWidth;
    selectionStyle.styles.default.defaultStyle.strokeWidth = strokeWidth;
    dottedOverlayStyle.strokeWidth = strokeWidth - 2;
    dottedOverlayStyle.strokeDashstyle = '1 ' + 2 * strokeWidth;
  };

  var drawSpeedLimits = function(speedLimits) {
    var speedLimitsSplitAt70kmh = _.groupBy(speedLimits, function(speedLimit) { return speedLimit.limit >= 70; });
    var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
    var highSpeedLimits = speedLimitsSplitAt70kmh[true];

    vectorLayer.removeAllFeatures();
    vectorLayer.addFeatures(lineFeatures(lowSpeedLimits));
    vectorLayer.addFeatures(dottedLineFeatures(highSpeedLimits));
    vectorLayer.addFeatures(limitSigns(speedLimits));

    if (selectedSpeedLimit.exists()) {
      var selectedFeature = _.find(vectorLayer.features, function(feature) {
        return getKey(feature.attributes) === selectedSpeedLimit.getKey();
      });
      selectControl.select(selectedFeature);
    }
  };

  var dottedLineFeatures = function(speedLimits) {
    var solidLines = lineFeatures(speedLimits);
    var dottedOverlay = lineFeatures(speedLimits, dottedOverlayStyle);
    return solidLines.concat(dottedOverlay);
  };

  var limitSigns = function(speedLimits) {
    return _.map(speedLimits, function(speedLimit) {
      var points = _.map(speedLimit.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      var road = new OpenLayers.Geometry.LineString(points);
      var signPosition = calculateMidpoint(road.getVertices());
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), speedLimit);
    });
  };

  var calculateMidpoint = function(road) {
    var roadLength = lineStringLength(road);
    var firstVertex = _.first(road);
    var optionalMidpoint = _.reduce(_.tail(road), function(acc, vertex) {
      if (acc.midpoint) return acc;
      var accumulatedDistance = acc.distanceTraversed + length(vertex, acc.previousVertex);
      if (accumulatedDistance < roadLength / 2) {
        return { previousVertex: vertex, distanceTraversed: accumulatedDistance };
      } else {
        return {
          midpoint: {
            x: acc.previousVertex.x + (((vertex.x - acc.previousVertex.x) / length(vertex, acc.previousVertex)) * (roadLength / 2 - acc.distanceTraversed)),
            y: acc.previousVertex.y + (((vertex.y - acc.previousVertex.y) / length(vertex, acc.previousVertex)) * (roadLength / 2 - acc.distanceTraversed))
          }
        };
      }
    }, {previousVertex: firstVertex, distanceTraversed: 0});
    if (optionalMidpoint.midpoint) return optionalMidpoint.midpoint;
    else return firstVertex;
  };

  var lineStringLength = function(lineString) {
    var firstVertex = _.first(lineString);
    var lengthObject = _.reduce(_.tail(lineString), function(acc, vertex) {
      return {
        previousVertex: vertex,
        totalLength: acc.totalLength + length(vertex, acc.previousVertex)
      };
    }, { previousVertex: firstVertex, totalLength: 0 });
    return lengthObject.totalLength;
  };

  var length = function(end, start) {
    return Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
  };

  var lineFeatures = function(speedLimits, customStyle) {
    return _.map(speedLimits, function(speedLimit) {
      var points = _.map(speedLimit.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), speedLimit, customStyle);
    });
  };

  var reset = function() {
    stop();
    selectControl.unselectAll();
    vectorLayer.styleMap = browseStyle;
  };

  return {
    update: update,
    vectorLayer: vectorLayer,
    reset: reset
  };
};
