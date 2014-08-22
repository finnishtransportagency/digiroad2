var getKey = function(speedLimit) {
  return speedLimit.id + '-' + speedLimit.roadLinkId;
};

var SpeedLimitsCollection = function(backend) {
  var speedLimits = {};

  this.fetch = function(boundingBox) {
    backend.getSpeedLimits(boundingBox, function(fetchedSpeedLimits) {
      var selectedSpeedLimit = _.pick(speedLimits, function(speedLimit) { return speedLimit.isSelected; });
      speedLimits = _.merge(selectedSpeedLimit, _.reduce(fetchedSpeedLimits, function(acc, speedLimit) {
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

var SelectedSpeedLimit = function(collection, backend) {
  var current = null;

  this.open = function(id) {
    if (current) {
      current.isSelected = false;
    }
    current = collection.get(id);
    current.isSelected = true;
    backend.getSpeedLimit(current.id, function(speedLimit) {
      eventbus.trigger('speedLimit:selected', _.merge({}, current, speedLimit));
    });
  };

  this.close = function() {
    if (current) {
      current.isSelected = false;
      current = null;
      eventbus.trigger('speedLimit:unselected');
    }
  };

  this.exists = function() {
    return current !== null;
  };

  this.getKey = function() {
    return getKey(current);
  };

  this.getId = function() {
    return current.id;
  };
};

window.SpeedLimitLayer = function(map, backend) {
  var eventListener = _.extend({running: false}, eventbus);
  var collection = new SpeedLimitsCollection(backend);
  var selectedSpeedLimit = new SelectedSpeedLimit(collection, backend);
  var uiState = { zoomLevel: 9 };

  var createZoomAndTypeDependentRule = function(type, zoomLevel, style) {
     return new OpenLayers.Rule({
      filter: new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: [
        new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type }),
        new OpenLayers.Filter.Function({ evaluate: function() { return uiState.zoomLevel === zoomLevel; } })
      ] }),
      symbolizer: style
    });
  };

  var overlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay');
  var overlayStyleRules = [
    overlayStyleRule(9, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
    overlayStyleRule(10, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
    overlayStyleRule(11, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 7, strokeDashstyle: '1 18' }),
    overlayStyleRule(12, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' })
  ];

  var endpointStyleRule = _.partial(createZoomAndTypeDependentRule, 'endpoint');
  var endpointStyleRules = [
    endpointStyleRule(9, { graphicOpacity: 1.0, externalGraphic: 'images/speed-limits/selected.svg', pointRadius: 3 }),
    endpointStyleRule(10, { graphicOpacity: 1.0, externalGraphic: 'images/speed-limits/selected.svg', pointRadius: 5 }),
    endpointStyleRule(11, { graphicOpacity: 1.0, externalGraphic: 'images/speed-limits/selected.svg', pointRadius: 9 }),
    endpointStyleRule(12, { graphicOpacity: 1.0, externalGraphic: 'images/speed-limits/selected.svg', pointRadius: 16 })
  ];

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

  var speedLimitFeatureSizeLookup = {
    9: { strokeWidth: 3, pointRadius: 0 },
    10: { strokeWidth: 5, pointRadius: 13 },
    11: { strokeWidth: 9, pointRadius: 16 },
    12: { strokeWidth: 16, pointRadius: 20 }
  };

  var speedLimitFeatureOpacityLookup = {
    overlay: { strokeOpacity: 1.0 },
    other: { strokeOpacity: 0.7 }
  };

  var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
  var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
  browseStyleMap.addUniqueValueRules('default', 'limit', speedLimitStyleLookup);
  browseStyleMap.addUniqueValueRules('default', 'zoomLevel', speedLimitFeatureSizeLookup, uiState);
  browseStyleMap.addUniqueValueRules('default', 'type', speedLimitFeatureOpacityLookup);
  browseStyle.addRules(overlayStyleRules);

  var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
    strokeOpacity: 0.15,
    graphicOpacity: 0.3
  }));
  var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
    strokeOpacity: 0.7,
    graphicOpacity: 1.0
  }));
  var selectionStyle = new OpenLayers.StyleMap({
    default: selectionDefaultStyle,
    select: selectionSelectStyle
  });
  selectionStyle.addUniqueValueRules('default', 'limit', speedLimitStyleLookup);
  selectionStyle.addUniqueValueRules('default', 'zoomLevel', speedLimitFeatureSizeLookup, uiState);
  selectionStyle.addUniqueValueRules('select', 'type', speedLimitFeatureOpacityLookup);
  selectionDefaultStyle.addRules(overlayStyleRules);
  selectionDefaultStyle.addRules(endpointStyleRules);

  var vectorLayer = new OpenLayers.Layer.Vector('speedLimit', { styleMap: browseStyleMap });
  vectorLayer.setOpacity(1);

  var createSelectionEndPoints = function(points) {
    return _.map(points, function(point) {
      return new OpenLayers.Feature.Vector(
        new OpenLayers.Geometry.Point(point.x, point.y), {type: 'endpoint'});
    });
  };

  var highlightSpeedLimitFeatures = function(feature) {
    _.each(vectorLayer.features, function(x) {
      if (x.attributes.id === feature.attributes.id) {
        selectControl.highlight(x);
      } else {
        selectControl.unhighlight(x);
      }
    });
  };

  var selectionFeatures = [];
  var selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
    onSelect: function(feature) {
      if (feature.attributes.type === 'endpoint') {
        return false;
      }
      selectedSpeedLimit.open(getKey(feature.attributes));
    },
    onUnselect: function(feature) {
      if (selectedSpeedLimit.exists()) {
         _.each(selectionFeatures, function(feature) {
          feature.style = {display: 'none'};
        });
        _.each(_.filter(vectorLayer.features, function(feature) {
          return feature.attributes.id === selectedSpeedLimit.getId();
        }), function(feature) {
          selectControl.unhighlight(feature);
        });
        selectedSpeedLimit.close();
        vectorLayer.styleMap = browseStyleMap;
        vectorLayer.redraw();
      }
    }
  });
  map.addControl(selectControl);

  var update = function(zoom, boundingBox) {
    if (zoomlevels.isInAssetZoomLevel(zoom)) {
      adjustStylesByZoomLevel(zoom);
      start();
      collection.fetch(boundingBox);
    }
  };

  var adjustStylesByZoomLevel = function(zoom) {
    uiState.zoomLevel = zoom;
    vectorLayer.redraw();
  };

  var start = function() {
    if (!eventListener.running) {
      eventListener.running = true;
      eventListener.listenTo(eventbus, 'speedLimits:fetched', drawSpeedLimits);
      selectControl.activate();
    }
  };

  var stop = function() {
    selectControl.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  eventbus.on('speedLimit:selected', function(speedLimit) {
    var feature = _.find(vectorLayer.features, function(x) { return x.attributes.id === speedLimit.id; });
    vectorLayer.styleMap = selectionStyle;
    highlightSpeedLimitFeatures(feature);
    if (_.isArray(selectionFeatures)) {
      vectorLayer.removeFeatures(selectionFeatures);
    }
    selectionFeatures = createSelectionEndPoints(speedLimit.endpoints);
    vectorLayer.addFeatures(selectionFeatures);
    vectorLayer.redraw();
  });

  eventbus.on('map:moved', function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === 'speedLimit') {
      adjustStylesByZoomLevel(state.zoom);
      start();
      collection.fetch(state.bbox);
    } else {
      vectorLayer.removeAllFeatures();
      stop();
    }
  }, this);

  var drawSpeedLimits = function(speedLimits) {
    var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
    var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithType, function(speedLimit) { return speedLimit.limit >= 70; });
    var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
    var highSpeedLimits = speedLimitsSplitAt70kmh[true];

    selectControl.deactivate();
    vectorLayer.removeAllFeatures();
    selectControl.activate();

    vectorLayer.addFeatures(lineFeatures(lowSpeedLimits));
    vectorLayer.addFeatures(dottedLineFeatures(highSpeedLimits));
    vectorLayer.addFeatures(limitSigns(speedLimitsWithType));

    if (selectedSpeedLimit.exists()) {
      var selectedFeature = _.find(vectorLayer.features, function(feature) {
        return getKey(feature.attributes) === selectedSpeedLimit.getKey();
      });
      selectControl.select(selectedFeature);
    }
  };

  var dottedLineFeatures = function(speedLimits) {
    var solidLines = lineFeatures(speedLimits);
    var dottedOverlay = lineFeatures(_.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'overlay' }); }));
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
    vectorLayer.styleMap = browseStyleMap;
  };

  return {
    update: update,
    vectorLayer: vectorLayer,
    reset: reset
  };
};
