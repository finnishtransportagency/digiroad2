window.SpeedLimitLayer = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedSpeedLimit = params.selectedSpeedLimit,
      roadCollection = params.roadCollection,
      geometryUtils = params.geometryUtils,
      linearAsset = params.linearAsset;

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
        if (collection.isDirty()) {
          displayConfirmMessage();
        } else {
          self.cut(evt);
        }
      }
    };

    this.deactivate = function() {
      eventListener.stopListening(eventbus, 'map:clicked', clickHandler);
      eventListener.stopListening(eventbus, 'map:mouseMoved');
      remove();
    };

    this.activate = function() {
      eventListener.listenTo(eventbus, 'map:clicked', clickHandler);
      eventListener.listenTo(eventbus, 'map:mouseMoved', function(event) {
        if (application.getSelectedTool() === 'Cut' && !collection.isDirty()) {
          self.updateByPosition(event.xy);
        }
      });
    };

    var isWithinCutThreshold = function(speedLimitLink) {
      return speedLimitLink && speedLimitLink.distance < CUT_THRESHOLD;
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

    this.cut = function(point) {
      var pixel = new OpenLayers.Pixel(point.x, point.y);
      var mouseLonLat = map.getLonLatFromPixel(pixel);
      var mousePoint = new OpenLayers.Geometry.Point(mouseLonLat.lon, mouseLonLat.lat);
      var nearest = findNearestSpeedLimitLink(mousePoint);

      if (!isWithinCutThreshold(nearest.distanceObject)) {
        return;
      }

      var pointsToLineString = function(points) {
        return new OpenLayers.Geometry.LineString(
          _.map(points, function(point) {
                          return new OpenLayers.Geometry.Point(point.x, point.y);
                        }));
      };

      var lineString = pointsToLineString(roadCollection.get(nearest.feature.attributes.roadLinkId).getPoints());
      var split = {splitMeasure: geometryUtils.calculateMeasureAtPoint(lineString, mousePoint)};
      _.merge(split, geometryUtils.splitByPoint(pointsToLineString(nearest.feature.attributes.points),
                                                mousePoint));

      collection.splitSpeedLimit(nearest.feature.attributes.id, nearest.feature.attributes.roadLinkId, split);
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
    overlayStyleRule(12, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
    overlayStyleRule(13, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
    overlayStyleRule(14, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' }),
    overlayStyleRule(15, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' })
  ];

  var oneWayOverlayStyleRule = _.partial(createZoomAndTypeDependentOneWayRule, 'overlay');
  var oneWayOverlayStyleRules = [
    oneWayOverlayStyleRule(9, { strokeDashstyle: '1 6' }),
    oneWayOverlayStyleRule(10, { strokeDashstyle: '1 10' }),
    oneWayOverlayStyleRule(11, { strokeDashstyle: '1 10' }),
    oneWayOverlayStyleRule(12, { strokeDashstyle: '1 16' }),
    oneWayOverlayStyleRule(13, { strokeDashstyle: '1 16' }),
    oneWayOverlayStyleRule(14, { strokeDashstyle: '1 16' }),
    oneWayOverlayStyleRule(15, { strokeDashstyle: '1 16' }),
  ];

  var validityDirectionStyleRules = [
    createZoomDependentOneWayRule(9, { strokeWidth: 2 }),
    createZoomDependentOneWayRule(10, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(11, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(12, { strokeWidth: 8 }),
    createZoomDependentOneWayRule(13, { strokeWidth: 8 }),
    createZoomDependentOneWayRule(14, { strokeWidth: 8 }),
    createZoomDependentOneWayRule(15, { strokeWidth: 8 }),
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
    10: { strokeWidth: 5, pointRadius: 12 },
    11: { strokeWidth: 9, pointRadius: 14 },
    12: { strokeWidth: 16, pointRadius: 16 },
    13: { strokeWidth: 16, pointRadius: 20 },
    14: { strokeWidth: 16, pointRadius: 24 },
    15: { strokeWidth: 16, pointRadius: 24 }
  };

  var typeSpecificStyleLookup = {
    overlay: { strokeOpacity: 1.0 },
    other: { strokeOpacity: 0.7 },
    cutter: { externalGraphic: 'images/cursor-crosshair.svg', pointRadius: 11.5 }
  };

  var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
  var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
  browseStyleMap.addUniqueValueRules('default', 'value', speedLimitStyleLookup);
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
  selectionStyle.addUniqueValueRules('default', 'value', speedLimitStyleLookup);
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

  var findFeatureById = function(id) {
    return _.find(vectorLayer.features, function(feature) { return feature.attributes.id === id; });
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

  var logBounds = function(bounds) { console.log(bounds); };
  var boxControl = new OpenLayers.Control();
  map.addControl(boxControl);
  var boxHandler = new OpenLayers.Handler.Box(boxControl, { done: logBounds }, { keyMask: OpenLayers.Handler.MOD_CTRL });

  var handleSpeedLimitUnSelected = function(id) {
    _.each(_.filter(vectorLayer.features, function(feature) {
      return feature.attributes.id === id;
    }), function(feature) {
      selectControl.unhighlight(feature);
    });

    vectorLayer.styleMap = browseStyleMap;
    vectorLayer.redraw();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
  };

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
      boxHandler.deactivate();
      speedLimitCutter.activate();
    } else if (tool === 'Select') {
      speedLimitCutter.deactivate();
      selectControl.activate();
      boxHandler.activate();
    }
  };

  var start = function() {
    if (!eventListener.running) {
      eventListener.running = true;
      bindEvents();
      changeTool(application.getSelectedTool());
    }
  };

  var stop = function() {
    selectControl.deactivate();
    boxHandler.deactivate();
    speedLimitCutter.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  var bindEvents = function() {
    eventListener.listenTo(eventbus, 'speedLimits:fetched', redrawSpeedLimits);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, 'speedLimit:selected', handleSpeedLimitSelected);
    eventListener.listenTo(eventbus, 'speedLimit:saved', handleSpeedLimitSaved);
    eventListener.listenTo(eventbus, 'speedLimit:valueChanged', handleSpeedLimitChanged);
    eventListener.listenTo(eventbus, 'speedLimit:cancelled speedLimit:saved', handleSpeedLimitCancelled);
    eventListener.listenTo(eventbus, 'speedLimit:unselected', handleSpeedLimitUnSelected);
  };

  var handleSpeedLimitSelected = function(selectedSpeedLimit) {
    if (selectedSpeedLimit.isNew()) {
      var feature = findFeatureById(selectedSpeedLimit.getId());
      setSelectionStyleAndHighlightFeature(feature);
    }
  };

  var handleSpeedLimitSaved = function(speedLimit) {
    var feature = findFeatureById(speedLimit.id);
    setSelectionStyleAndHighlightFeature(feature);
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var handleSpeedLimitChanged = function(selectedSpeedLimit) {
    selectControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    var selectedSpeedLimitFeatures = _.filter(vectorLayer.features, function(feature) { return feature.attributes.id === selectedSpeedLimit.getId(); });
    vectorLayer.removeFeatures(selectedSpeedLimitFeatures);
    drawSpeedLimits([selectedSpeedLimit.get()]);
  };

  var handleSpeedLimitCancelled = function() {
    selectControl.activate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    redrawSpeedLimits(collection.getAll());
  };

  var handleMapMoved = function(state) {
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
  };

  eventbus.on('map:moved', handleMapMoved);

  var redrawSpeedLimits = function(speedLimits) {
    selectControl.deactivate();
    vectorLayer.removeAllFeatures();
    if (!selectedSpeedLimit.isDirty() && application.getSelectedTool() === 'Select') {
      selectControl.activate();
    }

    drawSpeedLimits(speedLimits);
  };

  var drawSpeedLimits = function(speedLimits) {
    var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
    var offsetBySideCode = function(speedLimit) {
      return linearAsset.offsetBySideCode(map.getZoom(), speedLimit);
    };
    var speedLimitsWithAdjustments = _.map(speedLimitsWithType, offsetBySideCode);
    var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithAdjustments, function(speedLimit) { return speedLimit.value >= 70; });
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
        var signPosition = geometryUtils.calculateMidpointOfLineString(road);
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), speedLimit);
      });
    }));
  };

  var lineFeatures = function(speedLimits) {
    return _.flatten(_.map(speedLimits, function(speedLimit) {
      return _.map(speedLimit.links, function(link) {
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var data = _.cloneDeep(speedLimit);
        data.roadLinkId = link.roadLinkId;
        data.points = link.originalPoints || points;
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), data);
      });
    }));
  };

  var reset = function() {
    stop();
    selectControl.unselectAll();
    vectorLayer.styleMap = browseStyleMap;
  };

  var show = function(map) {
    map.addLayer(vectorLayer);
    vectorLayer.setVisibility(true);
    update(map.getZoom(), map.getExtent());
  };

  var hide = function(map) {
    reset();
    map.removeLayer(vectorLayer);
  };

  return {
    update: update,
    vectorLayer: vectorLayer,
    show: show,
    hide: hide
  };
};
