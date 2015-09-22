window.LinearAssetLayer2 = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedSpeedLimit = params.selectedSpeedLimit,
      geometryUtils = params.geometryUtils,
      linearAsset = params.linearAsset,
      roadLayer = params.roadLayer,
      multiElementEventCategory = params.multiElementEventCategory,
      singleElementEventCategory = params.singleElementEventCategory,
      style = params.style,
      layerName = 'linearAsset';

  Layer.call(this, layerName, roadLayer);
  var me = this;

  var singleElementEvents = function() {
    return _.map(arguments, function(argument) { return singleElementEventCategory + ':' + argument; }).join(' ');
  };

  var multiElementEvent = function(eventName) {
    return multiElementEventCategory + ':' + eventName;
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
        var splitMeasure = geometryUtils.calculateMeasureAtPoint(lineString, point) + startMeasureOffset;
        var splitVertices = geometryUtils.splitByPoint(pointsToLineString(nearestSpeedLimit.points), point);
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

  var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: style.browsing });
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
    vectorLayer.styleMap = style.selection;
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

    vectorLayer.styleMap = style.browsing;
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
    vectorLayer.styleMap = style.browsing;
    vectorLayer.redraw();
  };

  var activateSelectionStyle = function(selectedSpeedLimits) {
    vectorLayer.styleMap = style.selection;
    selectedSpeedLimit.openMultiple(selectedSpeedLimits);
    highlightMultipleSpeedLimitFeatures();
    vectorLayer.redraw();
  };

  var bindEvents = function() {
    eventListener.listenTo(eventbus, multiElementEvent('fetched'), redrawSpeedLimits);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, singleElementEvents('selected', 'multiSelected'), handleSpeedLimitSelected);
    eventListener.listenTo(eventbus, singleElementEvents('saved'), handleSpeedLimitSaved);
    eventListener.listenTo(eventbus, multiElementEvent('massUpdateSucceeded'), handleSpeedLimitSaved);
    eventListener.listenTo(eventbus, singleElementEvents('valueChanged', 'separated'), handleSpeedLimitChanged);
    eventListener.listenTo(eventbus, singleElementEvents('cancelled', 'saved'), handleSpeedLimitCancelled);
    eventListener.listenTo(eventbus, singleElementEvents('unselect'), handleSpeedLimitUnSelected);
    eventListener.listenTo(eventbus, 'application:readOnly', updateMassUpdateHandlerState);
    eventListener.listenTo(eventbus, singleElementEvents('selectByMmlId'), selectSpeedLimitByMmlId);
    eventListener.listenTo(eventbus, multiElementEvent('massUpdateFailed'), cancelSelection);
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
    decorateSelection();
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
      return me.mapOverLinkMiddlePoints(links, geometryUtils, function(link, middlePoint) {
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

      return me.mapOverLinkMiddlePoints(geometriesForIndicators, geometryUtils, function(link, middlePoint) {
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
    decorateSelection();
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
  };

  var decorateSelection = function() {
    if (selectedSpeedLimit.exists()) {
      withoutOnSelect(function() {
        var feature = _.find(vectorLayer.features, function(feature) { return selectedSpeedLimit.isSelected(feature.attributes); });
        if (feature) {
          selectControl.select(feature);
        }
        highlightMultipleSpeedLimitFeatures();
      });

      if (selectedSpeedLimit.isSplitOrSeparated()) {
        drawIndicators(_.map(_.cloneDeep(selectedSpeedLimit.get()), offsetBySideCode));
      }
    }
  };

  var withoutOnSelect = function(f) {
    selectControl.onSelect = function() {};
    f();
    selectControl.onSelect = speedLimitOnSelect;
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
      var signPosition = geometryUtils.calculateMidpointOfLineString(road);
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
    vectorLayer.styleMap = style.browsing;
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
