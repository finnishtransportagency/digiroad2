window.TotalWeightLimitLayer = function(params) {
  var map = params.map,
    application = params.application,
    collection = params.collection,
    selectedWeightLimit = params.selectedWeightLimit,
    roadCollection = params.roadCollection,
    geometryUtils = params.geometryUtils,
    linearAsset = params.linearAsset,
    roadLayer = params.roadLayer,
    layerName = params.layerName;

  var WeightLimitCutter = function(vectorLayer, collection) {
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

    var isWithinCutThreshold = function(weightLimitLink) {
      return weightLimitLink && weightLimitLink.distance < CUT_THRESHOLD;
    };

    var findNearestWeightLimitLink = function(point) {
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
      var nearestWeightLimitLink = findNearestWeightLimitLink(mousePoint);
      if (!nearestWeightLimitLink) {
        return;
      }
      var distanceObject = nearestWeightLimitLink.distanceObject;
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
      var nearest = findNearestWeightLimitLink(mousePoint);

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

      collection.splitWeightLimit(nearest.feature.attributes.id, nearest.feature.attributes.roadLinkId, split);
      remove();
    };
  };

  var eventListener = _.extend({running: false}, eventbus);
  var uiState = { zoomLevel: 9 };

  var combineFilters = function(filters) {
    return new OpenLayers.Filter.Logical({ type: OpenLayers.Filter.Logical.AND, filters: filters });
  };

  var zoomLevelFilter = function(zoomLevel) {
    return new OpenLayers.Filter.Function({ evaluate: function() { return uiState.zoomLevel === zoomLevel; } });
  };

  var oneWayFilter = function() {
    return new OpenLayers.Filter.Comparison({ type: OpenLayers.Filter.Comparison.NOT_EQUAL_TO, property: 'sideCode', value: 1 });
  };

  var createZoomDependentOneWayRule = function(zoomLevel, style) {
    return new OpenLayers.Rule({
      filter: combineFilters([oneWayFilter(), zoomLevelFilter(zoomLevel)]),
      symbolizer: style
    });
  };

  var validityDirectionStyleRules = [
    createZoomDependentOneWayRule(9, { strokeWidth: 2 }),
    createZoomDependentOneWayRule(10, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(11, { strokeWidth: 4 }),
    createZoomDependentOneWayRule(12, { strokeWidth: 8 })
  ];

  var weightLimitFeatureSizeLookup = {
    9: { strokeWidth: 3 },
    10: { strokeWidth: 5 },
    11: { strokeWidth: 9 },
    12: { strokeWidth: 16 }
  };

  var styleLookup = {
    false: {strokeColor: '#ff0000'},
    true: {strokeColor: '#7f7f7c'}
  };

  var typeSpecificStyleLookup = {
    line: { strokeOpacity: 0.7 },
    cutter: { externalGraphic: 'images/cursor-crosshair.svg', pointRadius: 11.5 }
  };

  var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
  var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
  browseStyleMap.addUniqueValueRules('default', 'expired', styleLookup);
  browseStyleMap.addUniqueValueRules('default', 'zoomLevel', weightLimitFeatureSizeLookup, uiState);
  browseStyleMap.addUniqueValueRules('default', 'type', typeSpecificStyleLookup);
  browseStyle.addRules(validityDirectionStyleRules);

  var selectionDefaultStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
    strokeOpacity: 0.15
  }));
  var selectionSelectStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults({
    strokeOpacity: 0.7
  }));
  var selectionStyle = new OpenLayers.StyleMap({
    default: selectionDefaultStyle,
    select: selectionSelectStyle
  });
  selectionStyle.addUniqueValueRules('default', 'expired', styleLookup);
  selectionStyle.addUniqueValueRules('default', 'zoomLevel', weightLimitFeatureSizeLookup, uiState);
  selectionStyle.addUniqueValueRules('select', 'type', typeSpecificStyleLookup);
  selectionDefaultStyle.addRules(validityDirectionStyleRules);

  var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: browseStyleMap });
  vectorLayer.setOpacity(1);

  var weightLimitCutter = new WeightLimitCutter(vectorLayer, collection);

  var roadLayerStyleMap = new OpenLayers.StyleMap({
    "select": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeOpacity: 0.85,
      strokeColor: "#7f7f7c"
    })),
    "default": new OpenLayers.Style(OpenLayers.Util.applyDefaults({
      strokeColor: "#a4a4a2",
      strokeOpacity: 0.3
    }))
  });
  roadLayerStyleMap.addUniqueValueRules('default', 'zoomLevel', weightLimitFeatureSizeLookup, uiState);
  roadLayer.setLayerSpecificStyleMap(layerName, roadLayerStyleMap);

  var highlightWeightLimitFeatures = function(feature) {
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
    highlightWeightLimitFeatures(feature);
    vectorLayer.redraw();
  };

  var findUnpersistedWeightFeatures = function() {
    return _.filter(vectorLayer.features, function(feature) { return feature.attributes.id === null; });
  };

  var findRoadLinkFeaturesByRoadLinkId = function(id) {
    return _.filter(roadLayer.layer.features, function(feature) { return feature.attributes.roadLinkId === id; });
  };

  var findWeightFeaturesById = function(id) {
    return _.filter(vectorLayer.features, function(feature) { return feature.attributes.id === id; });
  };

  var weightLimitOnSelect = function(feature) {
    setSelectionStyleAndHighlightFeature(feature);
    if (feature.attributes.id) {
      selectedWeightLimit.open(feature.attributes.id);
    } else {
      selectedWeightLimit.create(feature.attributes.roadLinkId, feature.attributes.points);
    }
  };

  var selectControl = new OpenLayers.Control.SelectFeature([vectorLayer, roadLayer.layer], {
    onSelect: weightLimitOnSelect,
    onUnselect: function(feature) {
      if (selectedWeightLimit.exists()) {
        var id = selectedWeightLimit.getId();
        var expired = selectedWeightLimit.expired();
        selectedWeightLimit.close();
        if (expired) {
          vectorLayer.removeFeatures(_.filter(vectorLayer.features, function(feature) {
            return feature.attributes.id === id;
          }));
        }
      }
    }
  });
  map.addControl(selectControl);

  var handleWeightLimitUnSelected = function(id, roadLinkId) {
    var features = findWeightFeaturesById(id);
    if (_.isEmpty(features)) { features = findRoadLinkFeaturesByRoadLinkId(roadLinkId); }
    _.each(features, function(feature) {
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
      collection.fetch(boundingBox, selectedWeightLimit);
    }
  };

  var adjustStylesByZoomLevel = function(zoom) {
    uiState.zoomLevel = zoom;
    vectorLayer.redraw();
  };

  var changeTool = function(tool) {
    if (tool === 'Cut') {
      selectControl.deactivate();
      weightLimitCutter.activate();
    } else if (tool === 'Select') {
      weightLimitCutter.deactivate();
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
    weightLimitCutter.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  var bindEvents = function() {
    eventListener.listenTo(eventbus, 'totalWeightLimits:fetched', redrawWeightLimits);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, 'totalWeightLimit:selected', handleWeightLimitSelected);
    eventListener.listenTo(eventbus,
        'totalWeightLimit:limitChanged totalWeightLimit:expirationChanged',
        handleWeightLimitChanged);
    eventListener.listenTo(eventbus, 'totalWeightLimit:cancelled totalWeightLimit:saved', concludeUpdate);
    eventListener.listenTo(eventbus, 'totalWeightLimit:unselected', handleWeightLimitUnSelected);
  };

  var handleWeightLimitSelected = function(selectedWeightLimit) {
    if (selectedWeightLimit.isNew()) {
      var feature = _.first(findWeightFeaturesById(selectedWeightLimit.getId())) || _.first(findRoadLinkFeaturesByRoadLinkId(selectedWeightLimit.getRoadLinkId()));
      setSelectionStyleAndHighlightFeature(feature);
    }
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var weightLimitFeatureExists = function(selectedWeightLimit) {
    if (selectedWeightLimit.isNew() && selectedWeightLimit.isDirty()) {
      return !_.isEmpty(findUnpersistedWeightFeatures());
    } else {
      return !_.isEmpty(findWeightFeaturesById(selectedWeightLimit.getId()));
    }
  };

  var handleWeightLimitChanged = function(selectedWeightLimit) {
    selectControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    if (weightLimitFeatureExists(selectedWeightLimit)) {
      var selectedWeightLimitFeatures = getSelectedFeatures(selectedWeightLimit);
      vectorLayer.removeFeatures(selectedWeightLimitFeatures);
    }
    drawWeightLimits([selectedWeightLimit.get()]);
  };

  var concludeUpdate = function() {
    selectControl.activate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    redrawWeightLimits(collection.getAll());
  };

  var handleMapMoved = function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === layerName) {
      vectorLayer.setVisibility(true);
      adjustStylesByZoomLevel(state.zoom);
      start();
      collection.fetch(state.bbox, selectedWeightLimit);
    } else if (selectedWeightLimit.isDirty()) {
      new Confirm();
    } else {
      vectorLayer.setVisibility(false);
      stop();
    }
  };

  eventbus.on('map:moved', handleMapMoved);

  var redrawWeightLimits = function(weightLimits) {
    selectControl.deactivate();
    vectorLayer.removeAllFeatures();
    if (!selectedWeightLimit.isDirty() && application.getSelectedTool() === 'Select') {
      selectControl.activate();
    }

    drawWeightLimits(weightLimits);
  };

  var getSelectedFeatures = function(selectedWeightLimit) {
    if (selectedWeightLimit.isNew()) {
      if (selectedWeightLimit.isDirty()) {
        return findUnpersistedWeightFeatures();
      } else {
        return findRoadLinkFeaturesByRoadLinkId(selectedWeightLimit.getRoadLinkId());
      }
    } else {
      return findWeightFeaturesById(selectedWeightLimit.getId());
    }
  };

  var drawWeightLimits = function(weightLimits) {
    var weightLimitsWithType = _.map(weightLimits, function(limit) {
      return _.merge({}, limit, { type: 'line', expired: limit.expired + '' });
    });
    var weightLimitsWithAdjustments = _.map(weightLimitsWithType, linearAsset.offsetBySideCode);
    vectorLayer.addFeatures(lineFeatures(weightLimitsWithAdjustments));

    if (selectedWeightLimit.exists && selectedWeightLimit.exists()) {
      selectControl.onSelect = function() {};
      var feature = _.first(getSelectedFeatures(selectedWeightLimit));
      if (feature) {
        selectControl.select(feature);
        highlightWeightLimitFeatures(feature);
      }
      selectControl.onSelect = weightLimitOnSelect;
    }
  };

  var lineFeatures = function(weightLimits) {
    return _.flatten(_.map(weightLimits, function(weightLimit) {
      return _.map(weightLimit.links, function(link) {
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var weightLimitWithRoadLinkId = _.cloneDeep(weightLimit);
        weightLimitWithRoadLinkId.roadLinkId = link.roadLinkId;
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), weightLimitWithRoadLinkId);
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
