window.TotalWeightLimitLayer = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedTotalWeightLimit = params.selectedTotalWeightLimit,
      roadCollection = params.roadCollection,
      geometryUtils = params.geometryUtils;

  var TotalWeightLimitCutter = function(vectorLayer, collection) {
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

    var isWithinCutThreshold = function(totalWeightLimitLink) {
      return totalWeightLimitLink && totalWeightLimitLink.distance < CUT_THRESHOLD;
    };

    var findNearestTotalWeightLimitLink = function(point) {
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
      var closestTotalWeightLimitLink = findNearestTotalWeightLimitLink(mousePoint);
      if (!closestTotalWeightLimitLink) {
        return;
      }
      var distanceObject = closestTotalWeightLimitLink.distanceObject;
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
      var nearest = findNearestTotalWeightLimitLink(mousePoint);

      if (!isWithinCutThreshold(nearest.distanceObject)) {
        return;
      }

      var points = _.chain(roadCollection.getPointsOfRoadLink(nearest.feature.attributes.roadLinkId))
                     .map(function(point) {
                       return new OpenLayers.Geometry.Point(point.x, point.y);
                     })
                     .value();
      var lineString = new OpenLayers.Geometry.LineString(points);
      var split = {splitMeasure: geometryUtils.calculateMeasureAtPoint(lineString, mousePoint)};
      _.merge(split, geometryUtils.splitByPoint(nearest.feature.geometry, mousePoint));

      collection.splitTotalWeightLimit(nearest.feature.attributes.id, nearest.feature.attributes.roadLinkId, split);
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

  var overlayStyleRule = _.partial(createZoomAndTypeDependentRule, 'overlay');
  var overlayStyleRules = [
    overlayStyleRule(9, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 1, strokeDashstyle: '1 6' }),
    overlayStyleRule(10, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 3, strokeDashstyle: '1 10' }),
    overlayStyleRule(11, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 7, strokeDashstyle: '1 18' }),
    overlayStyleRule(12, { strokeOpacity: 1.0, strokeColor: '#ffffff', strokeLinecap: 'square', strokeWidth: 14, strokeDashstyle: '1 32' })
  ];

  var validityDirectionStyleRules = [
    createZoomDependentOneWayRule(9, { strokeWidth: 2 }),
    createZoomDependentOneWayRule(10, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(11, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(12, { strokeWidth: 8 })
  ];

  var totalWeightLimitFeatureSizeLookup = {
    9: { strokeWidth: 3, pointRadius: 0 },
    10: { strokeWidth: 5, pointRadius: 13 },
    11: { strokeWidth: 9, pointRadius: 16 },
    12: { strokeWidth: 16, pointRadius: 20 }
  };

  var typeSpecificStyleLookup = {
    overlay: { strokeOpacity: 1.0 },
    other: { strokeOpacity: 0.7 },
    cutter: { externalGraphic: 'images/total-weight-limits/cursor-crosshair.svg', pointRadius: 11.5 }
  };

  var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
  var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
  browseStyleMap.addUniqueValueRules('default', 'zoomLevel', totalWeightLimitFeatureSizeLookup, uiState);
  browseStyleMap.addUniqueValueRules('default', 'type', typeSpecificStyleLookup);
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
  selectionStyle.addUniqueValueRules('default', 'zoomLevel', totalWeightLimitFeatureSizeLookup, uiState);
  selectionStyle.addUniqueValueRules('select', 'type', typeSpecificStyleLookup);
  selectionDefaultStyle.addRules(overlayStyleRules);
  selectionDefaultStyle.addRules(validityDirectionStyleRules);

  var vectorLayer = new OpenLayers.Layer.Vector('totalWeightLimit', { styleMap: browseStyleMap });
  vectorLayer.setOpacity(1);

  var totalWeightLimitCutter = new TotalWeightLimitCutter(vectorLayer, collection);

  var highlightTotalWeightLimitFeatures = function(feature) {
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
    highlightTotalWeightLimitFeatures(feature);
    vectorLayer.redraw();
  };

  var findFeatureById = function(id) {
    return _.find(vectorLayer.features, function(feature) { return feature.attributes.id === id; });
  };

  var totalWeightLimitOnSelect = function(feature) {
    setSelectionStyleAndHighlightFeature(feature);
    selectedTotalWeightLimit.open(feature.attributes.id);
  };

  var selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
    onSelect: totalWeightLimitOnSelect,
    onUnselect: function(feature) {
      if (selectedTotalWeightLimit.exists()) {
        selectedTotalWeightLimit.close();
      }
    }
  });
  map.addControl(selectControl);

  var handleTotalWeightLimitUnSelected = function(id) {
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
      totalWeightLimitCutter.activate();
    } else if (tool === 'Select') {
      totalWeightLimitCutter.deactivate();
      selectControl.activate();
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
    totalWeightLimitCutter.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  var bindEvents = function() {
    eventListener.listenTo(eventbus, 'totalWeightLimits:fetched', redrawTotalWeightLimits);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, 'totalWeightLimit:selected', handleTotalWeightLimitSelected);
    eventListener.listenTo(eventbus, 'totalWeightLimit:saved', handleTotalWeightLimitSaved);
    eventListener.listenTo(eventbus, 'totalWeightLimit:limitChanged', handleTotalWeightLimitChanged);
    eventListener.listenTo(eventbus, 'totalWeightLimit:cancelled totalWeightLimit:saved', handleTotalWeightLimitCancelled);
    eventListener.listenTo(eventbus, 'totalWeightLimit:unselected', handleTotalWeightLimitUnSelected);
  };

  var handleTotalWeightLimitSelected = function(selectedTotalWeightLimit) {
    if (selectedTotalWeightLimit.isNew()) {
      var feature = findFeatureById(selectedTotalWeightLimit.getId());
      setSelectionStyleAndHighlightFeature(feature);
    }
  };

  var handleTotalWeightLimitSaved = function(totalWeightLimit) {
    var feature = findFeatureById(totalWeightLimit.id);
    setSelectionStyleAndHighlightFeature(feature);
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var handleTotalWeightLimitChanged = function(selectedTotalWeightLimit) {
    selectControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    var selectedTotalWeightLimitFeatures = _.filter(vectorLayer.features, function(feature) { return feature.attributes.id === selectedTotalWeightLimit.getId(); });
    vectorLayer.removeFeatures(selectedTotalWeightLimitFeatures);
    drawTotalWeightLimits([selectedTotalWeightLimit.get()]);
  };

  var handleTotalWeightLimitCancelled = function() {
    selectControl.activate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    redrawTotalWeightLimits(collection.getAll());
  };

  var handleMapMoved = function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === 'totalWeightLimit') {
      vectorLayer.setVisibility(true);
      adjustStylesByZoomLevel(state.zoom);
      start();
      collection.fetch(state.bbox);
    } else if (selectedTotalWeightLimit.isDirty()) {
      new Confirm();
    } else {
      vectorLayer.setVisibility(false);
      stop();
    }
  };

  eventbus.on('map:moved', handleMapMoved);

  var redrawTotalWeightLimits = function(totalWeightLimits) {
    selectControl.deactivate();
    vectorLayer.removeAllFeatures();
    if (!selectedTotalWeightLimit.isDirty() && application.getSelectedTool() === 'Select') {
      selectControl.activate();
    }

    drawTotalWeightLimits(totalWeightLimits);
  };

  var drawTotalWeightLimits = function(totalWeightLimits) {
    var totalWeightLimitsWithType = _.map(totalWeightLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
    vectorLayer.addFeatures(lineFeatures(totalWeightLimitsWithType));

    if (selectedTotalWeightLimit.exists && selectedTotalWeightLimit.exists()) {
      selectControl.onSelect = function() {};
      var feature = _.find(vectorLayer.features, function(feature) { return feature.attributes.id === selectedTotalWeightLimit.getId(); });
      if (feature) {
        selectControl.select(feature);
        highlightTotalWeightLimitFeatures(feature);
      }
      selectControl.onSelect = totalWeightLimitOnSelect;
    }
  };

  var lineFeatures = function(totalWeightLimits) {
    return _.flatten(_.map(totalWeightLimits, function(totalWeightLimit) {
      return _.map(totalWeightLimit.links, function(link) {
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var totalWeightLimitWithRoadLinkId = _.cloneDeep(totalWeightLimit);
        totalWeightLimitWithRoadLinkId.roadLinkId = link.roadLinkId;
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), totalWeightLimitWithRoadLinkId);
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
