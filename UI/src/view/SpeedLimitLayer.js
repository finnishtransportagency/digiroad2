window.SpeedLimitLayer = function(map, application, collection, selectedSpeedLimit) {
  var splitLineStringByPoint = function(lineString, point) {
    var segments = _.reduce(lineString.getVertices(), function(acc, vertex, index, vertices) {
      if (index > 0) {
        var previousVertex = vertices[index - 1];
        var segmentGeometry = new OpenLayers.Geometry.LineString([previousVertex, vertex]);
        var distanceObject = segmentGeometry.distanceTo(point, { details: true });
        var segment = {
          distance: distanceObject.distance,
          splitPoint: {
            x: distanceObject.x0,
            y: distanceObject.y0
          },
          index: index - 1
        };
        return acc.concat([segment]);
      } else {
        return acc;
      }
    }, []);
    var splitSegment = _.head(_.sortBy(segments, 'distance'));
    var split = _.reduce(lineString.getVertices(), function(acc, vertex, index) {
      if (acc.firstSplit) {
        acc.firstSplitVertices.push({ x: vertex.x, y: vertex.y });
        if (index === splitSegment.index) {
          acc.firstSplitVertices.push({ x: splitSegment.splitPoint.x, y: splitSegment.splitPoint.y });
          acc.secondSplitVertices.push({ x: splitSegment.splitPoint.x, y: splitSegment.splitPoint.y });
          acc.firstSplit = false;
        }
      } else {
        acc.secondSplitVertices.push({ x: vertex.x, y: vertex.y });
      }
      return acc;
    }, {
      firstSplit: true,
      firstSplitVertices: [],
      secondSplitVertices: []
    });
    return [split.firstSplitVertices, split.secondSplitVertices];
  };

  var SpeedLimitCutter = function(vectorLayer, collection) {
    var scissorFeatures = [];
    var CUT_THRESHOLD = 20;

    var moveTo = function(x, y) {
      vectorLayer.removeFeatures(scissorFeatures);
      scissorFeatures = [new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(x, y), { type: 'cutter' })];
      vectorLayer.addFeatures(scissorFeatures);
    };

    var remove = function() {
      vectorLayer.removeFeatures(scissorFeatures);
      scissorFeatures = [];
    };

    var self = this;

    var clickHandler = function(evt) {
      if (application.getSelectedTool() === 'Cut') {
        self.cut(evt.xy);
      }
    };

    this.deactivate = function() {
      map.events.unregister('click', this, clickHandler);
      eventListener.stopListening(eventbus, 'map:mouseMoved');
      remove();
    };

    this.activate = function() {
      map.events.register('click', this, clickHandler);
      eventListener.listenTo(eventbus, 'map:mouseMoved', function(event) {
        if (application.getSelectedTool() === 'Cut') {
          self.updateByPosition(event.xy);
        }
      });
    };

    var isWithinCutThreshold = function(speedLimitLink) {
      return !collection.isDirty() &&
             speedLimitLink &&
             speedLimitLink.distance < CUT_THRESHOLD;
    };

    var findNearestSpeedLimitLink = function(point) {
      return _.chain(vectorLayer.features)
        .filter(function(feature) { return feature.geometry instanceof OpenLayers.Geometry.LineString; })
        .map(function(feature) {
          return {feature: feature,
                  distanceObject: feature.geometry.distanceTo(point, {details: true})};
        })
        .sortBy(function(x) {
          return x.distanceObject.distance;
        })
        .head()
        .value();
    };

    this.updateByPosition = function(position) {
      var lonlat = map.getLonLatFromPixel(position);
      var mousePoint = new OpenLayers.Geometry.Point(lonlat.lon, lonlat.lat);
      var closestSpeedLimitLink = findNearestSpeedLimitLink(mousePoint);
      if (!closestSpeedLimitLink) {
        return;
      }
      var distanceObject = closestSpeedLimitLink.distanceObject;
      if (isWithinCutThreshold(distanceObject)) {
        moveTo(distanceObject.x0, distanceObject.y0);
      } else {
        remove();
      }
    };

    this.cut = function(position) {
      var pixel = new OpenLayers.Pixel(position.x, position.y);
      var mouseLonLat = map.getLonLatFromPixel(pixel);
      var mousePoint = new OpenLayers.Geometry.Point(mouseLonLat.lon, mouseLonLat.lat);
      var nearest = findNearestSpeedLimitLink(mousePoint);

      if (!isWithinCutThreshold(nearest.distanceObject)) {
        return;
      }

      collection.splitSpeedLimit(nearest.feature.attributes.id, nearest.feature.attributes.position, splitLineStringByPoint(nearest.feature.geometry, mousePoint));
      remove();
    };
  };

  var eventListener = _.extend({running: false}, eventbus);
  var uiState = { zoomLevel: 9 };

  var combineFilters = function(filters) {
    return new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: filters });
  };

  var typeFilter = function(type) {
    return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type });
  };

  var zoomLevelFilter = function(zoomLevel) {
    return new OpenLayers.Filter.Function({ evaluate: function() { return uiState.zoomLevel === zoomLevel; } });
  };

  var oneWayFilter = function() {
    return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.NOT_EQUAL_TO, property: 'sideCode', value: 1 });
  };

  var createZoomAndTypeDependentRule = function(type, zoomLevel, style) {
     return new OpenLayers.Rule({
       filter: combineFilters([typeFilter(type), zoomLevelFilter(zoomLevel)]),
       symbolizer: style
     });
  };

  var createZoomDependentOneWayRule = function(zoomLevel, style) {
    return new OpenLayers.Rule({
      filter: combineFilters([oneWayFilter(), zoomLevelFilter(zoomLevel)]),
      symbolizer: style
    });
  };

  var createZoomAndTypeDependentOneWayRule = function(type, zoomLevel, style) {
    return new OpenLayers.Rule({
      filter: combineFilters([typeFilter(type), oneWayFilter(), zoomLevelFilter(zoomLevel)]),
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

  var oneWayOverlayStyleRule = _.partial(createZoomAndTypeDependentOneWayRule, 'overlay');
  var oneWayOverlayStyleRules = [
    oneWayOverlayStyleRule(9, { strokeDashstyle: '1 6' }),
    oneWayOverlayStyleRule(10, { strokeDashstyle: '1 10' }),
    oneWayOverlayStyleRule(11, { strokeDashstyle: '1 10' }),
    oneWayOverlayStyleRule(12, { strokeDashstyle: '1 16' })
  ];

  var validityDirectionStyleRules = [
    createZoomDependentOneWayRule(9, { strokeWidth: 2 }),
    createZoomDependentOneWayRule(10, { strokeWidth: 4 }),
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
    80:  { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/80.svg' },
    100: { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/100.svg' },
    120: { strokeColor: '#0011bb', externalGraphic: 'images/speed-limits/120.svg' }
  };

  var speedLimitFeatureSizeLookup = {
    9: { strokeWidth: 3, pointRadius: 0 },
    10: { strokeWidth: 5, pointRadius: 13 },
    11: { strokeWidth: 9, pointRadius: 16 },
    12: { strokeWidth: 16, pointRadius: 20 }
  };

  var typeSpecificStyleLookup = {
    overlay: { strokeOpacity: 1.0 },
    other: { strokeOpacity: 0.7 },
    cutter: { externalGraphic: 'images/speed-limits/cursor-crosshair.svg', pointRadius: 11.5 }
  };

  var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
  var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
  browseStyleMap.addUniqueValueRules('default', 'limit', speedLimitStyleLookup);
  browseStyleMap.addUniqueValueRules('default', 'zoomLevel', speedLimitFeatureSizeLookup, uiState);
  browseStyleMap.addUniqueValueRules('default', 'type', typeSpecificStyleLookup);
  browseStyle.addRules(overlayStyleRules);
  browseStyle.addRules(validityDirectionStyleRules);
  browseStyle.addRules(oneWayOverlayStyleRules);

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
  selectionStyle.addUniqueValueRules('select', 'type', typeSpecificStyleLookup);
  selectionDefaultStyle.addRules(overlayStyleRules);
  selectionDefaultStyle.addRules(validityDirectionStyleRules);
  selectionDefaultStyle.addRules(oneWayOverlayStyleRules);

  var vectorLayer = new OpenLayers.Layer.Vector('speedLimit', { styleMap: browseStyleMap });
  vectorLayer.setOpacity(1);

  var speedLimitCutter = new SpeedLimitCutter(vectorLayer, collection);

  var highlightSpeedLimitFeatures = function(feature) {
    _.each(vectorLayer.features, function(x) {
      if (x.attributes.id === feature.attributes.id) {
        selectControl.highlight(x);
      } else {
        selectControl.unhighlight(x);
      }
    });
  };



  var setSelectionStyleAndHighlightFeature = function(feature) {
    vectorLayer.styleMap = selectionStyle;
    highlightSpeedLimitFeatures(feature);
    vectorLayer.redraw();
  };

  var speedLimitOnSelect = function(feature) {
    setSelectionStyleAndHighlightFeature(feature);
    selectedSpeedLimit.open(feature.attributes.id);
  };

  var selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
    onSelect: speedLimitOnSelect,
    onUnselect: function(feature) {
      if (selectedSpeedLimit.exists()) {
        selectedSpeedLimit.close();
      }
    }
  });
  map.addControl(selectControl);

  eventbus.on('speedLimit:unselected', function(id) {
    _.each(_.filter(vectorLayer.features, function(feature) {
      return feature.attributes.id === id;
    }), function(feature) {
      selectControl.unhighlight(feature);
    });

    vectorLayer.styleMap = browseStyleMap;
    vectorLayer.redraw();
  });

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

  var changeTool = function(tool) {
    if (tool === 'Cut') {
      selectControl.deactivate();
      speedLimitCutter.activate();
    } else if (tool === 'Select') {
      speedLimitCutter.deactivate();
      selectControl.activate();
    }
  };

  var start = function() {
    if (!eventListener.running) {
      eventListener.running = true;
      eventListener.listenTo(eventbus, 'speedLimits:fetched', redrawSpeedLimits);
      eventListener.listenTo(eventbus, 'tool:changed', changeTool);
      changeTool(application.getSelectedTool());
    }
  };

  var stop = function() {
    selectControl.deactivate();
    speedLimitCutter.deactivate();
    eventListener.stopListening(eventbus, 'speedLimits:fetched');
    eventListener.stopListening(eventbus, 'tool:changed');
    eventListener.running = false;
  };

  eventbus.on('speedLimit:selected', function(selectedSpeedLimit) {
    if (selectedSpeedLimit.isNew()) {
      var feature = _.find(vectorLayer.features, function(feature) { return feature.attributes.id === selectedSpeedLimit.getId(); });
      setSelectionStyleAndHighlightFeature(feature);
    }
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

  eventbus.on('speedLimit:unselected', function() {
    map.events.unregister('click', vectorLayer, displayConfirmMessage);
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
    if (!selectedSpeedLimit.isDirty() && application.getSelectedTool() === 'Select') {
      selectControl.activate();
    }

    drawSpeedLimits(speedLimits);
  };

  var adjustSpeedLimitOffset = function(speedLimit) {
    if (speedLimit.sideCode === 1) {
      return speedLimit;
    }
    speedLimit.links = _.map(speedLimit.links, function(link) {
      link.points = _.map(link.points, function(point, index, geometry) {
        return offsetPoint(point, index, geometry, speedLimit.sideCode);
      });
      return link;
    });
    return speedLimit;
  };

  var offsetPoint = function(point, index, geometry, sideCode) {
    var scaleVector = function(vector, scalar) {
      return {x: vector.x * scalar, y: vector.y * scalar};
    };
    var sumVectors = function(vector1, vector2) {
      return {x: vector1.x + vector2.x, y: vector1.y + vector2.y};
    };
    var subtractVector = function(vector1, vector2) {
      return {x: vector1.x - vector2.x, y: vector1.y - vector2.y};
    };
    var normalVector = function(vector) {
      return {x: vector.y, y: -vector.x };
    };
    var length = function(vector) {
      return Math.sqrt(Math.pow(vector.x, 2) + Math.pow(vector.y, 2));
    };
    var unitVector = function(vector) {
      var vectorLength = length(vector);
      return {x: vector.x / vectorLength, y: vector.y / vectorLength};
    };

    var previousPoint = index > 0 ? geometry[index - 1] : point;
    var nextPoint = geometry[index + 1] || point;

    var directionVector = scaleVector(sumVectors(subtractVector(point, previousPoint), subtractVector(nextPoint, point)), 0.5);
    var normal = normalVector(directionVector);
    var sideCodeScalar = (2 * sideCode - 5) * -4;
    var offset = scaleVector(unitVector(normal), sideCodeScalar);
    return sumVectors(point, offset);
  };

  var drawSpeedLimits = function(speedLimits) {
    var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
    var speedLimitsWithAdjustments = _.map(speedLimitsWithType, adjustSpeedLimitOffset);
    var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithAdjustments, function(speedLimit) { return speedLimit.limit >= 70; });
    var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
    var highSpeedLimits = speedLimitsSplitAt70kmh[true];

    vectorLayer.addFeatures(lineFeatures(lowSpeedLimits));
    vectorLayer.addFeatures(dottedLineFeatures(highSpeedLimits));
    vectorLayer.addFeatures(limitSigns(speedLimitsWithAdjustments));

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
        var points = _.map(link.points, function(point) {
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
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var speedLimitWithPosition = _.clone(speedLimit);
        speedLimitWithPosition.position = link.position;
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), speedLimitWithPosition);
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
