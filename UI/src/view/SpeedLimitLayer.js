window.SpeedLimitLayer = function(map, collection, selectedSpeedLimit) {
  var eventListener = _.extend({running: false}, eventbus);
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

  var createZoomDependentOneWayRule = function(zoomLevel, style) {
    return new OpenLayers.Rule({
      filter: new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: [
        new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.NOT_EQUAL_TO, property: 'sideCode', value: 1 }),
        new OpenLayers.Filter.Function({ evaluate: function() { return uiState.zoomLevel === zoomLevel; } })
      ] }),
      symbolizer: style
    })
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

  var validityDirectionStyleRules = [
    createZoomDependentOneWayRule(9, { strokeWidth: 1 }),
    createZoomDependentOneWayRule(10, { strokeWidth: 2 }),
    createZoomDependentOneWayRule(11, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(12, { strokeWidth: 8 })
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
  browseStyle.addRules(validityDirectionStyleRules);

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
  selectionDefaultStyle.addRules(validityDirectionStyleRules);

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

  var speedLimitOnSelect = function(feature) {
    if (feature.attributes.type === 'endpoint') {
      return false;
    }
    vectorLayer.styleMap = selectionStyle;
    highlightSpeedLimitFeatures(feature);
    vectorLayer.redraw();
    selectedSpeedLimit.open(feature.attributes.id);
  };

  var selectionFeatures = [];
  var selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
    onSelect: speedLimitOnSelect,
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
      eventListener.listenTo(eventbus, 'speedLimits:fetched', redrawSpeedLimits);
      selectControl.activate();
    }
  };

  var stop = function() {
    selectControl.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  eventbus.on('speedLimit:selected', function(selectedSpeedLimit) {
    vectorLayer.removeFeatures(selectionFeatures);
    selectionFeatures = createSelectionEndPoints(selectedSpeedLimit.getEndpoints());
    vectorLayer.addFeatures(selectionFeatures);
  });

  var displayConfirmMessage = function() { new Confirm(); };

  eventbus.on('speedLimit:limitChanged', function(selectedSpeedLimit) {
    selectControl.deactivate();
    map.events.unregister('click', vectorLayer, displayConfirmMessage);
    map.events.register('click', vectorLayer, displayConfirmMessage);
    var selectedSpeedLimitFeatures = _.filter(vectorLayer.features, function(feature) { return feature.attributes.id === selectedSpeedLimit.getId(); });
    vectorLayer.removeFeatures(selectedSpeedLimitFeatures);
    drawSpeedLimits([selectedSpeedLimit.get()]);
  });

  eventbus.on('speedLimit:cancelled speedLimit:saved', function() {
    selectControl.activate();
    map.events.unregister('click', vectorLayer, displayConfirmMessage);
    redrawSpeedLimits(collection.getAll());
  });

  eventbus.on('map:moved', function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === 'speedLimit') {
      vectorLayer.setVisibility(true);
      adjustStylesByZoomLevel(state.zoom);
      start();
      collection.fetch(state.bbox);
    } else if (selectedSpeedLimit.isDirty()) {
      new Confirm();
    } else {
      vectorLayer.setVisibility(false);
      stop();
    }
  }, this);

  var redrawSpeedLimits = function(speedLimits) {
    selectControl.deactivate();
    vectorLayer.removeAllFeatures();
    if (!selectedSpeedLimit.isDirty()) selectControl.activate();

    drawSpeedLimits(speedLimits);
  };

  var drawSpeedLimits = function(speedLimits) {
    var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
    var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithType, function(speedLimit) { return speedLimit.limit >= 70; });
    var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
    var highSpeedLimits = speedLimitsSplitAt70kmh[true];

    vectorLayer.addFeatures(lineFeatures(lowSpeedLimits));
    vectorLayer.addFeatures(dottedLineFeatures(highSpeedLimits));
    vectorLayer.addFeatures(limitSigns(speedLimitsWithType));
    vectorLayer.addFeatures(selectionFeatures);

    if (selectedSpeedLimit.exists()) {
      selectControl.onSelect = function() {};
      var feature = _.find(vectorLayer.features, function(feature) { return feature.attributes.id === selectedSpeedLimit.getId(); });
      if (feature) {
        selectControl.select(feature);
        highlightSpeedLimitFeatures(feature);
      }
      selectControl.onSelect = speedLimitOnSelect;
    }
  };

  var dottedLineFeatures = function(speedLimits) {
    var solidLines = lineFeatures(speedLimits);
    var dottedOverlay = lineFeatures(_.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'overlay' }); }));
    return solidLines.concat(dottedOverlay);
  };

  var limitSigns = function(speedLimits) {
    return _.flatten(_.map(speedLimits, function(speedLimit) {
      return _.map(speedLimit.links, function(link) {
        var points = _.map(link, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var road = new OpenLayers.Geometry.LineString(points);
        var signPosition = calculateMidpoint(road.getVertices());
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), speedLimit);
      });
    }));
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

  var lineFeatures = function(speedLimits) {
    return _.flatten(_.map(speedLimits, function(speedLimit) {
      return _.map(speedLimit.links, function(link) {
        var points = _.map(link, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), speedLimit);
      });
    }));
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
