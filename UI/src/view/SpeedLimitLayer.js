window.SpeedLimitLayer = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedSpeedLimit = params.selectedSpeedLimit,
      roadCollection = params.roadCollection,
      geometryUtils = params.geometryUtils,
      backend = params.backend,
      linearAsset = params.linearAsset,
      roadLayer = params.roadLayer;
  Layer.call(this, 'speedLimit', roadLayer);
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
        .reject(function(feature) { return _.has(feature.attributes, 'generatedId') && collection.getUnknown(feature.attributes.generatedId); })
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

      var lineString = pointsToLineString(roadCollection.get(nearest.feature.attributes.mmlId).getPoints());
      var split = {splitMeasure: geometryUtils.calculateMeasureAtPoint(lineString, mousePoint)};
      _.merge(split, geometryUtils.splitByPoint(pointsToLineString(nearest.feature.attributes.points),
                                                mousePoint));

      selectedSpeedLimit.splitSpeedLimit(nearest.feature.attributes.id, nearest.feature.attributes.mmlId, split);
      remove();
    };
  };

  var showMassUpdateDialog = function(selectedSpeedLimits) {
    var SPEED_LIMITS = [120, 100, 80, 70, 60, 50, 40, 30, 20];
    var speedLimitOptionTags = _.map(SPEED_LIMITS, function(value) {
      var selected = value === 50 ? " selected" : "";
      return '<option value="' + value + '"' + selected + '>' + value + '</option>';
    });
    var confirmDiv =
      '<div class="modal-overlay mass-update-modal">' +
        '<div class="modal-dialog">' +
          '<div class="content">' +
            'Olet valinnut <%- speedLimitCount %> nopeusrajoitusta' +
          '</div>' +
          '<div class="form-group editable">' +
            '<label class="control-label">Rajoitus</label>' +
            '<select class="form-control">' + speedLimitOptionTags.join('') + '</select>' +
          '</div>' +
          '<div class="actions">' +
            '<button class="btn btn-primary save">Tallenna</button>' +
            '<button class="btn btn-secondary close">Peruuta</button>' +
          '</div>' +
        '</div>' +
      '</div>';

    var renderDialog = function() {
      $('.container').append(_.template(confirmDiv, {
        speedLimitCount: selectedSpeedLimit.count()
      }));
      var modal = $('.modal-dialog');
    };

    var activateBrowseStyle = function() {
      _.each(vectorLayer.features, function(feature) {
        selectControl.unhighlight(feature);
      });
      vectorLayer.styleMap = browseStyleMap;
      vectorLayer.redraw();
    };

    var activateSelectionStyle = function() {
      vectorLayer.styleMap = selectionStyle;
      selectedSpeedLimit.openMultiple(selectedSpeedLimits);
      highlightMultipleSpeedLimitFeatures();
      vectorLayer.redraw();
    };

    var bindEvents = function() {
      eventListener.listenTo(eventbus, 'speedLimits:massUpdateSucceeded speedLimits:massUpdateFailed', purge);
      eventListener.listenTo(eventbus, 'speedLimits:massUpdateSucceeded', function() {
        collection.fetch(map.getExtent());
      });

      $('.mass-update-modal .close').on('click', function() {
        activateBrowseStyle();
        purge();
      });
      $('.mass-update-modal .save').on('click', function() {
        var modal = $('.modal-dialog');
        modal.find('.actions button').attr('disabled', true);
        activateBrowseStyle();
        var value = parseInt($('.mass-update-modal select').val(), 10);
        selectedSpeedLimit.saveMultiple(value);
      });
    };

    var show = function() {
      purge();
      activateSelectionStyle();
      renderDialog();
      bindEvents();
    };

    var purge = function() {
      selectedSpeedLimit.closeMultiple();
      $('.mass-update-modal').remove();
      eventListener.stopListening(eventbus, 'speedLimits:massUpdateSucceeded speedLimits:massUpdateFailed');
    };
    show();
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
    oneWayOverlayStyleRule(15, { strokeDashstyle: '1 16' }),
  ];

  var validityDirectionStyleRules = [
    createZoomDependentOneWayRule(9, { strokeWidth: 2 }),
    createZoomDependentOneWayRule(10, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(11, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(12, { strokeWidth: 5 }),
    createZoomDependentOneWayRule(13, { strokeWidth: 5 }),
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

  var findFeatureById = function(id) {
    return _.find(vectorLayer.features, function(feature) { return feature.attributes.id === id; });
  };

  var speedLimitOnSelect = function(feature) {
    selectedSpeedLimit.open(feature.attributes);
    setSelectionStyleAndHighlightFeature();
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

  var pixelBoundsToCoordinateBounds = function(bounds) {
    var bottomLeft = map.getLonLatFromPixel(new OpenLayers.Pixel(bounds.left, bounds.bottom));
    var topRight = map.getLonLatFromPixel(new OpenLayers.Pixel(bounds.right, bounds.top));
    return new OpenLayers.Bounds(bottomLeft.lon, bottomLeft.lat, topRight.lon, topRight.lat);
  };

  var massUpdateSpeedLimits = function(bounds) {
    if (selectedSpeedLimit.isDirty()) {
      displayConfirmMessage();
    } else {
      var coordinateBounds = pixelBoundsToCoordinateBounds(bounds);
      var selectedSpeedLimits = _.chain(vectorLayer.features)
        .filter(function(feature) { return coordinateBounds.toGeometry().intersects(feature.geometry);})
        .map(function(feature) { return feature.attributes; })
        .value();
      if (selectedSpeedLimits.length > 0) {
        selectedSpeedLimit.close();
        showMassUpdateDialog(selectedSpeedLimits);
      }
    }
  };

  var getModifierKey = function() {
    if (navigator.platform.toLowerCase().indexOf('mac') === 0) {
      return OpenLayers.Handler.MOD_META;
    } else {
      return OpenLayers.Handler.MOD_CTRL;
    }
  };

  var boxControl = new OpenLayers.Control();
  map.addControl(boxControl);
  var boxHandler = new OpenLayers.Handler.Box(boxControl, { done: massUpdateSpeedLimits }, { keyMask: getModifierKey() });

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
      eventbus.once('roadLinks:fetched', function() {
        roadLayer.drawRoadLinks(roadCollection.getAll(), zoom);
        collection.fetch(boundingBox);
      });
      roadCollection.fetchFromVVH(map.getExtent(), map.getZoom());
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
    updateMultiSelectBoxHandlerState();
  };

  var updateMultiSelectBoxHandlerState = function() {
    if (!application.isReadOnly() && application.getSelectedTool() === 'Select') {
      boxHandler.activate();
    } else {
      boxHandler.deactivate();
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
    eventListener.listenTo(eventbus, 'speedLimit:unselect', handleSpeedLimitUnSelected);
    eventListener.listenTo(eventbus, 'application:readOnly', updateMultiSelectBoxHandlerState);
  };

  var handleSpeedLimitSelected = function(selectedSpeedLimit) {
    if (selectedSpeedLimit.isNew()) {
      setSelectionStyleAndHighlightFeature();
    }
  };

  var handleSpeedLimitSaved = function() {
    setSelectionStyleAndHighlightFeature();
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var handleSpeedLimitChanged = function(selectedSpeedLimit) {
    selectControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    var selectedSpeedLimitFeatures = _.filter(vectorLayer.features, function(feature) { return selectedSpeedLimit.isSelected(feature.attributes); });
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
      eventbus.once('roadLinks:fetched', function() {
        roadLayer.drawRoadLinks(roadCollection.getAll(), state.zoom);
        collection.fetch(state.bbox);
      });
      roadCollection.fetchFromVVH(map.getExtent(), map.getZoom());
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
      var feature = _.find(vectorLayer.features, function(feature) { return selectedSpeedLimit.isSelected(feature.attributes); });
      if (feature) {
        selectControl.select(feature);
      }
      highlightMultipleSpeedLimitFeatures();
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
        data.mmlId = link.mmlId;
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

  var hideLayer = function(map) {
    reset();
    map.removeLayer(vectorLayer);
    me.hide();
  };

  return {
    update: update,
    vectorLayer: vectorLayer,
    show: show,
    hide: hideLayer
  };
};
