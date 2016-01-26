window.SpeedLimitLayer = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedSpeedLimit = params.selectedSpeedLimit,
      roadLayer = params.roadLayer,
      layerName = 'speedLimit';

  Layer.call(this, layerName, roadLayer);
  var me = this;

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
        .reject(function(feature) { return _.has(feature.attributes, 'generatedId') && _.flatten(collection.getGroup(feature.attributes)).length > 0; })
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
      var pointsToLineString = function(points) {
        var openlayersPoints = _.map(points, function(point) { return new OpenLayers.Geometry.Point(point.x, point.y); });
        return new OpenLayers.Geometry.LineString(openlayersPoints);
      };

      var calculateSplitProperties = function(nearestSpeedLimit, point) {
        var lineString = pointsToLineString(nearestSpeedLimit.points);
        var startMeasureOffset = nearestSpeedLimit.startMeasure;
        var splitMeasure = GeometryUtils.calculateMeasureAtPoint(lineString, point) + startMeasureOffset;
        var splitVertices = GeometryUtils.splitByPoint(pointsToLineString(nearestSpeedLimit.points), point);
        return _.merge({ splitMeasure: splitMeasure }, splitVertices);
      };

      var pixel = new OpenLayers.Pixel(point.x, point.y);
      var mouseLonLat = map.getLonLatFromPixel(pixel);
      var mousePoint = new OpenLayers.Geometry.Point(mouseLonLat.lon, mouseLonLat.lat);
      var nearest = findNearestSpeedLimitLink(mousePoint);

      if (!isWithinCutThreshold(nearest.distanceObject)) {
        return;
      }

      var nearestSpeedLimit = nearest.feature.attributes;
      var splitProperties = calculateSplitProperties(nearestSpeedLimit, mousePoint);
      selectedSpeedLimit.splitSpeedLimit(nearestSpeedLimit.id, splitProperties);

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

  var unknownLimitStyleRule = new OpenLayers.Rule({
    filter: typeFilter('unknown'),
    symbolizer: { externalGraphic: 'images/speed-limits/unknown.svg' }
  });

  var overlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay');
  var overlayStyleRules = [
    overlayStyleRule(9, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
    overlayStyleRule(10, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
    overlayStyleRule(11, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 5, strokeDashstyle: '1 15' }),
    overlayStyleRule(12, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
    overlayStyleRule(13, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 8, strokeDashstyle: '1 22' }),
    overlayStyleRule(14, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' }),
    overlayStyleRule(15, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 12, strokeDashstyle: '1 28' })
  ];

  var oneWayOverlayStyleRule = _.partial(createZoomAndTypeDependentOneWayRule, 'overlay');
  var oneWayOverlayStyleRules = [
    oneWayOverlayStyleRule(9, { strokeDashstyle: '1 6' }),
    oneWayOverlayStyleRule(10, { strokeDashstyle: '1 10' }),
    oneWayOverlayStyleRule(11, { strokeDashstyle: '1 10' }),
    oneWayOverlayStyleRule(12, { strokeDashstyle: '1 16' }),
    oneWayOverlayStyleRule(13, { strokeDashstyle: '1 16' }),
    oneWayOverlayStyleRule(14, { strokeDashstyle: '1 16' }),
    oneWayOverlayStyleRule(15, { strokeDashstyle: '1 16' })
  ];

  var validityDirectionStyleRules = [
    createZoomDependentOneWayRule(9, { strokeWidth: 2 }),
    createZoomDependentOneWayRule(10, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(11, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(12, { strokeWidth: 5 }),
    createZoomDependentOneWayRule(13, { strokeWidth: 5 }),
    createZoomDependentOneWayRule(14, { strokeWidth: 8 }),
    createZoomDependentOneWayRule(15, { strokeWidth: 8 })
  ];

  var speedLimitStyleLookup = {
    20:  { strokeColor: '#00ccdd', externalGraphic: 'images/speed-limits/20.svg' },
    30:  { strokeColor: '#ff55dd', externalGraphic: 'images/speed-limits/30.svg' },
    40:  { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/40.svg' },
    50:  { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/50.svg' },
    60:  { strokeColor: '#0011bb', externalGraphic: 'images/speed-limits/60.svg' },
    70:  { strokeColor: '#00ccdd', externalGraphic: 'images/speed-limits/70.svg' },
    80:  { strokeColor: '#ff0000', externalGraphic: 'images/speed-limits/80.svg' },
    90:  { strokeColor: '#ff55dd', externalGraphic: 'images/speed-limits/90.svg' },
    100: { strokeColor: '#11bb00', externalGraphic: 'images/speed-limits/100.svg' },
    120: { strokeColor: '#0011bb', externalGraphic: 'images/speed-limits/120.svg' }
  };

  var speedLimitFeatureSizeLookup = {
    9: { strokeWidth: 3, pointRadius: 0 },
    10: { strokeWidth: 5, pointRadius: 10 },
    11: { strokeWidth: 7, pointRadius: 14 },
    12: { strokeWidth: 10, pointRadius: 16 },
    13: { strokeWidth: 10, pointRadius: 16 },
    14: { strokeWidth: 14, pointRadius: 22 },
    15: { strokeWidth: 14, pointRadius: 22 }
  };

  var typeSpecificStyleLookup = {
    overlay: { strokeOpacity: 1.0 },
    other: { strokeOpacity: 0.7 },
    unknown: { strokeColor: '#000000', strokeOpacity: 0.6, externalGraphic: 'images/speed-limits/unknown.svg' },
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
  selectionDefaultStyle.addRules([unknownLimitStyleRule]);

  var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: browseStyleMap });
  vectorLayer.setOpacity(1);
  map.addLayer(vectorLayer);

  var indicatorLayer = new OpenLayers.Layer.Boxes('adjacentLinkIndicators');
  map.addLayer(indicatorLayer);

  var speedLimitCutter = new SpeedLimitCutter(vectorLayer, collection);

  var highlightMultipleSpeedLimitFeatures = function() {
    var partitioned = _.groupBy(vectorLayer.features, function(feature) {
      return selectedSpeedLimit.isSelected(feature.attributes);
    });
    var selected = partitioned[true];
    var unSelected = partitioned[false];
    _.each(selected, function(feature) { selectControl.highlight(feature); });
    _.each(unSelected, function(feature) { selectControl.unhighlight(feature); });
  };

  var highlightSpeedLimitFeatures = function() {
    highlightMultipleSpeedLimitFeatures();
  };

  var setSelectionStyleAndHighlightFeature = function() {
    vectorLayer.styleMap = selectionStyle;
    highlightSpeedLimitFeatures();
    vectorLayer.redraw();
  };

  var speedLimitOnSelect = function(feature) {
    selectedSpeedLimit.open(feature.attributes, feature.singleLinkSelect);
    setSelectionStyleAndHighlightFeature();
  };

  var speedLimitOnUnselect = function() {
    if (selectedSpeedLimit.exists()) {
      selectedSpeedLimit.close();
    }
  };

  var selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
    onSelect: speedLimitOnSelect,
    onUnselect: speedLimitOnUnselect
  });
  map.addControl(selectControl);
  var doubleClickSelectControl = new DoubleClickSelectControl(selectControl, map);

  var massUpdateHandler = new LinearAssetMassUpdate(map, vectorLayer, selectedSpeedLimit, function(speedLimits) {
    activateSelectionStyle(speedLimits);

    SpeedLimitMassUpdateDialog.show({
      count: selectedSpeedLimit.count(),
      onCancel: cancelSelection,
      onSave: function(newSpeedLimit) {
        selectedSpeedLimit.saveMultiple(newSpeedLimit);
        activateBrowseStyle();
        selectedSpeedLimit.closeMultiple();
      }
    });
  });

  function cancelSelection() {
    selectedSpeedLimit.closeMultiple();
    activateBrowseStyle();
    collection.fetch(map.getExtent());
  }

  var handleSpeedLimitUnSelected = function(selection) {
    _.each(_.filter(vectorLayer.features, function(feature) {
      return selection.isSelected(feature.attributes);
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
      return collection.fetch(boundingBox);
    } else {
      return $.Deferred().resolve();
    }
  };

  var adjustStylesByZoomLevel = function(zoom) {
    uiState.zoomLevel = zoom;
    vectorLayer.redraw();
  };

  var changeTool = function(tool) {
    if (tool === 'Cut') {
      doubleClickSelectControl.deactivate();
      speedLimitCutter.activate();
    } else if (tool === 'Select') {
      speedLimitCutter.deactivate();
      doubleClickSelectControl.activate();
    }
    updateMassUpdateHandlerState();
  };

  var updateMassUpdateHandlerState = function() {
    if (!application.isReadOnly() &&
        application.getSelectedTool() === 'Select' &&
        application.getSelectedLayer() === layerName) {
      massUpdateHandler.activate();
    } else {
      massUpdateHandler.deactivate();
    }
  };

  var start = function() {
    if (!eventListener.running) {
      eventListener.running = true;
      bindEvents();
      changeTool(application.getSelectedTool());
      updateMassUpdateHandlerState();
    }
  };

  var stop = function() {
    doubleClickSelectControl.deactivate();
    updateMassUpdateHandlerState();
    speedLimitCutter.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  var activateBrowseStyle = function() {
    _.each(vectorLayer.features, function(feature) {
      selectControl.unhighlight(feature);
    });
    vectorLayer.styleMap = browseStyleMap;
    vectorLayer.redraw();
  };

  var activateSelectionStyle = function(selectedSpeedLimits) {
    vectorLayer.styleMap = selectionStyle;
    selectedSpeedLimit.openMultiple(selectedSpeedLimits);
    highlightMultipleSpeedLimitFeatures();
    vectorLayer.redraw();
  };

  var bindEvents = function() {
    eventListener.listenTo(eventbus, 'speedLimits:fetched', redrawSpeedLimits);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, 'speedLimit:selected speedLimit:multiSelected', handleSpeedLimitSelected);
    eventListener.listenTo(eventbus, 'speedLimit:saved speedLimits:massUpdateSucceeded', handleSpeedLimitSaved);
    eventListener.listenTo(eventbus, 'speedLimit:valueChanged speedLimit:separated', handleSpeedLimitChanged);
    eventListener.listenTo(eventbus, 'speedLimit:cancelled speedLimit:saved', handleSpeedLimitCancelled);
    eventListener.listenTo(eventbus, 'speedLimit:unselect', handleSpeedLimitUnSelected);
    eventListener.listenTo(eventbus, 'application:readOnly', updateMassUpdateHandlerState);
    eventListener.listenTo(eventbus, 'speedLimit:selectByMmlId', selectSpeedLimitByMmlId);
    eventListener.listenTo(eventbus, 'speedLimits:massUpdateFailed', cancelSelection);
  };

  var handleSpeedLimitSelected = function(selectedSpeedLimit) {
    setSelectionStyleAndHighlightFeature();
  };

  var selectSpeedLimitByMmlId = function(mmlId) {
    var feature = _.find(vectorLayer.features, function(feature) { return feature.attributes.mmlId === mmlId; });
    if (feature) {
      selectControl.select(feature);
    }
  };

  var handleSpeedLimitSaved = function() {
    collection.fetch(map.getExtent());
    applicationModel.setSelectedTool('Select');
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var handleSpeedLimitChanged = function(selectedSpeedLimit) {
    doubleClickSelectControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    var selectedSpeedLimitFeatures = _.filter(vectorLayer.features, function(feature) { return selectedSpeedLimit.isSelected(feature.attributes); });
    vectorLayer.removeFeatures(selectedSpeedLimitFeatures);
    drawSpeedLimits(selectedSpeedLimit.get());
  };

  var handleSpeedLimitCancelled = function() {
    doubleClickSelectControl.activate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    redrawSpeedLimits(collection.getAll());
  };

  var handleMapMoved = function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === layerName) {
      vectorLayer.setVisibility(true);
      adjustStylesByZoomLevel(state.zoom);
      start();
      collection.fetch(state.bbox).then(function() {
        eventbus.trigger('layer:speedLimit:moved');
      });
    } else {
      vectorLayer.setVisibility(false);
      stop();
      eventbus.trigger('layer:speedLimit:moved');
    }
  };

  // TODO: Stop listening to map:moved events when layer is stopped
  eventbus.on('map:moved', handleMapMoved);

  var drawIndicators = function(links) {
    var markerTemplate = _.template('<span class="marker"><%= marker %></span>');

    var markerContainer = function(position) {
      var bounds = OpenLayers.Bounds.fromArray([position.x, position.y, position.x, position.y]);
      return new OpenLayers.Marker.Box(bounds, "00000000");
    };

    var indicatorsForSplit = function() {
      return me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
        var box = markerContainer(middlePoint);
        var $marker = $(markerTemplate(link));
        $(box.div).html($marker);
        $(box.div).css({overflow: 'visible'});
        return box;
      });
    };

    var indicatorsForSeparation = function() {
      var geometriesForIndicators = _.map(links, function(link) {
        var newLink = _.cloneDeep(link);
        newLink.points = _.drop(newLink.points, 1);
        return newLink;
      });

      return me.mapOverLinkMiddlePoints(geometriesForIndicators, function(link, middlePoint) {
        var box = markerContainer(middlePoint);
        var $marker = $(markerTemplate(link)).css({position: 'relative', right: '14px', bottom: '11px'});
        $(box.div).html($marker);
        $(box.div).css({overflow: 'visible'});
        return box;
      });
    };

    var indicators = function() {
      if (selectedSpeedLimit.isSplit()) {
        return indicatorsForSplit();
      } else {
        return indicatorsForSeparation();
      }
    };

    _.forEach(indicators(), function(indicator) {
      indicatorLayer.addMarker(indicator);
    });
  };

  var redrawSpeedLimits = function(speedLimitChains) {
    doubleClickSelectControl.deactivate();
    vectorLayer.removeAllFeatures();
    indicatorLayer.clearMarkers();
    if (!selectedSpeedLimit.isDirty() && application.getSelectedTool() === 'Select') {
      doubleClickSelectControl.activate();
    }

    var speedLimits = _.flatten(speedLimitChains);
    drawSpeedLimits(speedLimits);
  };

  var drawSpeedLimits = function(speedLimits) {
    var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
    var offsetBySideCode = function(speedLimit) {
      return GeometryUtils.offsetBySideCode(map.getZoom(), speedLimit);
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
      var feature = _.find(vectorLayer.features, function(feature) { return selectedSpeedLimit.isSelected(feature.attributes); });
      if (feature) {
        selectControl.select(feature);
      }
      highlightMultipleSpeedLimitFeatures();
      selectControl.onSelect = speedLimitOnSelect;

      if (selectedSpeedLimit.isSplitOrSeparated()) {
        drawIndicators(_.map(_.cloneDeep(selectedSpeedLimit.get()), offsetBySideCode));
      }
    }
  };

  var dottedLineFeatures = function(speedLimits) {
    var solidLines = lineFeatures(speedLimits);
    var dottedOverlay = lineFeatures(_.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'overlay' }); }));
    return solidLines.concat(dottedOverlay);
  };

  var isUnknown = function(speedLimit) {
    return !_.isNumber(speedLimit.value);
  };

  var limitSigns = function(speedLimits) {
    return _.map(speedLimits, function(speedLimit) {
      var points = _.map(speedLimit.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      var road = new OpenLayers.Geometry.LineString(points);
      var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
      var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
      var attributes = _.merge(_.cloneDeep(speedLimit), type);
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
    });
  };

  var lineFeatures = function(speedLimits) {
    return _.map(speedLimits, function(speedLimit) {
      var points = _.map(speedLimit.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
      var attributes = _.merge(_.cloneDeep(speedLimit), type);
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
    });
  };

  var reset = function() {
    stop();
    selectControl.unselectAll();
    vectorLayer.styleMap = browseStyleMap;
  };

  var show = function(map) {
    vectorLayer.setVisibility(true);
    indicatorLayer.setVisibility(true);
    var layerUpdated = update(map.getZoom(), map.getExtent());
    layerUpdated.then(function() {
      eventbus.trigger('layer:speedLimit:shown');
    });
  };

  var hideLayer = function(map) {
    reset();
    vectorLayer.setVisibility(false);
    indicatorLayer.setVisibility(false);
    me.hide();
  };

  return {
    update: update,
    vectorLayer: vectorLayer,
    show: show,
    hide: hideLayer
  };
};
