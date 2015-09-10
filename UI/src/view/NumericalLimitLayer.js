window.NumericalLimitLayer = function(params) {
  var map = params.map,
    application = params.application,
    collection = params.collection,
    selectedNumericalLimit = params.selectedNumericalLimit,
    roadCollection = params.roadCollection,
    geometryUtils = params.geometryUtils,
    linearAsset = params.linearAsset,
    roadLayer = params.roadLayer,
    layerName = params.layerName,
    multiElementEventCategory = params.multiElementEventCategory,
    singleElementEventCategory = params.singleElementEventCategory;
  Layer.call(this, layerName, roadLayer);
  var me = this;

  var NumericalLimitCutter = function(vectorLayer, collection) {
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

    var isWithinCutThreshold = function(numericalLimitLink) {
      return numericalLimitLink && numericalLimitLink.distance < CUT_THRESHOLD;
    };

    var findNearestNumericalLimitLink = function(point) {
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
      var nearestNumericalLimitLink = findNearestNumericalLimitLink(mousePoint);
      if (!nearestNumericalLimitLink) {
        return;
      }
      var distanceObject = nearestNumericalLimitLink.distanceObject;
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
      var nearest = findNearestNumericalLimitLink(mousePoint);

      if (!isWithinCutThreshold(nearest.distanceObject)) {
        return;
      }

      var points = _.chain(roadCollection.get([nearest.feature.attributes.links[0].mmlId])[0].getPoints())
                     .map(function(point) {
                       return new OpenLayers.Geometry.Point(point.x, point.y);
                     })
                     .value();
      var lineString = new OpenLayers.Geometry.LineString(points);
      var split = {splitMeasure: geometryUtils.calculateMeasureAtPoint(lineString, mousePoint)};
      _.merge(split, geometryUtils.splitByPoint(nearest.feature.geometry, mousePoint));

      collection.splitNumericalLimit(nearest.feature.attributes.id, nearest.feature.attributes.links[0].mmlId, split);
      remove();
    };
  };

  var singleElementEvents = function() {
    return _.map(arguments, function(argument) { return singleElementEventCategory + ':' + argument; }).join(' ');
  };

  var multiElementEvent = function(eventName) {
    return multiElementEventCategory + ':' + eventName;
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
    createZoomDependentOneWayRule(12, { strokeWidth: 8 }),
    createZoomDependentOneWayRule(13, { strokeWidth: 8 }),
    createZoomDependentOneWayRule(14, { strokeWidth: 8 }),
    createZoomDependentOneWayRule(15, { strokeWidth: 8 })
  ];

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
  browseStyleMap.addUniqueValueRules('default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup, uiState);
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
  selectionStyle.addUniqueValueRules('default', 'zoomLevel', RoadLayerSelectionStyle.linkSizeLookup, uiState);
  selectionStyle.addUniqueValueRules('select', 'type', typeSpecificStyleLookup);
  selectionDefaultStyle.addRules(validityDirectionStyleRules);

  var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: browseStyleMap });
  vectorLayer.setOpacity(1);

  var numericalLimitCutter = new NumericalLimitCutter(vectorLayer, collection);

  var styleMap = RoadLayerSelectionStyle.create(roadLayer, 0.3);
  roadLayer.setLayerSpecificStyleMap(layerName, styleMap);

  var highlightNumericalLimitFeatures = function(feature) {
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
    highlightNumericalLimitFeatures(feature);
    vectorLayer.redraw();
  };

  var findUnpersistedWeightFeatures = function() {
    return _.filter(vectorLayer.features, function(feature) { return feature.attributes.id === null; });
  };

  var findRoadLinkFeaturesByMmlId = function(id) {
    return _.filter(roadLayer.layer.features, function(feature) { return feature.attributes.mmlId === id; });
  };

  var findWeightFeaturesById = function(id) {
    return _.filter(vectorLayer.features, function(feature) { return feature.attributes.id === id; });
  };

  var numericalLimitOnSelect = function(feature) {
    setSelectionStyleAndHighlightFeature(feature);
    if (feature.attributes.id) {
      selectedNumericalLimit.open(feature.attributes.id);
    } else {
      selectedNumericalLimit.create(feature.attributes.mmlId, feature.attributes.points);
    }
  };

  var selectControl = new OpenLayers.Control.SelectFeature([vectorLayer, roadLayer.layer], {
    onSelect: numericalLimitOnSelect,
    onUnselect: function(feature) {
      if (selectedNumericalLimit.exists()) {
        var id = selectedNumericalLimit.getId();
        var expired = selectedNumericalLimit.expired();
        selectedNumericalLimit.close();
        if (expired) {
          vectorLayer.removeFeatures(_.filter(vectorLayer.features, function(feature) {
            return feature.attributes.id === id;
          }));
        }
      }
    }
  });
  map.addControl(selectControl);

  var handleNumericalLimitUnSelected = function(id, mmlId) {
    var features = findWeightFeaturesById(id);
    if (_.isEmpty(features)) { features = findRoadLinkFeaturesByMmlId(mmlId); }
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
      eventbus.once('roadLinks:fetched', function() {
        roadLayer.drawRoadLinks(roadCollection.getAll(), zoom);
        reselectNumericalLimit();
        collection.fetch(boundingBox, selectedNumericalLimit);
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
      numericalLimitCutter.activate();
    } else if (tool === 'Select') {
      numericalLimitCutter.deactivate();
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
    numericalLimitCutter.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  var bindEvents = function() {
    eventListener.listenTo(eventbus, multiElementEvent('fetched'), redrawNumericalLimits);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, singleElementEvents('selected'), handleNumericalLimitSelected);
    eventListener.listenTo(eventbus,
        singleElementEvents('limitChanged', 'expirationChanged'),
        handleNumericalLimitChanged);
    eventListener.listenTo(eventbus, singleElementEvents('cancelled', 'saved'), concludeUpdate);
    eventListener.listenTo(eventbus, singleElementEvents('unselected'), handleNumericalLimitUnSelected);
  };

  var handleNumericalLimitSelected = function(selectedNumericalLimit) {
    if (selectedNumericalLimit.isNew()) {
      var feature = _.first(findWeightFeaturesById(selectedNumericalLimit.getId())) || _.first(findRoadLinkFeaturesByMmlId(selectedNumericalLimit.getMmlId()));
      setSelectionStyleAndHighlightFeature(feature);
    }
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var numericalLimitFeatureExists = function(selectedNumericalLimit) {
    if (selectedNumericalLimit.isNew() && selectedNumericalLimit.isDirty()) {
      return !_.isEmpty(findUnpersistedWeightFeatures());
    } else {
      return !_.isEmpty(findWeightFeaturesById(selectedNumericalLimit.getId()));
    }
  };

  var handleNumericalLimitChanged = function(selectedNumericalLimit) {
    selectControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    if (numericalLimitFeatureExists(selectedNumericalLimit)) {
      var selectedNumericalLimitFeatures = getSelectedFeatures(selectedNumericalLimit);
      vectorLayer.removeFeatures(selectedNumericalLimitFeatures);
    }
    drawNumericalLimits([selectedNumericalLimit.get()]);
  };

  var concludeUpdate = function() {
    selectControl.activate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    redrawNumericalLimits(collection.getAll());
  };

  var handleMapMoved = function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === layerName) {
      vectorLayer.setVisibility(true);
      adjustStylesByZoomLevel(state.zoom);
      start();
      eventbus.once('roadLinks:fetched', function() {
        roadLayer.drawRoadLinks(roadCollection.getAll(), state.zoom);
        reselectNumericalLimit();
        collection.fetch(state.bbox, selectedNumericalLimit);
      });
      roadCollection.fetchFromVVH(map.getExtent(), map.getZoom());
    } else {
      vectorLayer.setVisibility(false);
      stop();
    }
  };

  eventbus.on('map:moved', handleMapMoved);

  var redrawNumericalLimits = function(numericalLimits) {
    selectControl.deactivate();
    vectorLayer.removeAllFeatures();
    if (!selectedNumericalLimit.isDirty() && application.getSelectedTool() === 'Select') {
      selectControl.activate();
    }

    drawNumericalLimits(numericalLimits);
  };

  var getSelectedFeatures = function(selectedNumericalLimit) {
    if (selectedNumericalLimit.isNew()) {
      if (selectedNumericalLimit.isDirty()) {
        return findUnpersistedWeightFeatures();
      } else {
        return findRoadLinkFeaturesByMmlId(selectedNumericalLimit.getMmlId());
      }
    } else {
      return findWeightFeaturesById(selectedNumericalLimit.getId());
    }
  };

  var reselectNumericalLimit = function() {
    if (selectedNumericalLimit.exists && selectedNumericalLimit.exists()) {
      selectControl.onSelect = function() {};
      var feature = _.first(getSelectedFeatures(selectedNumericalLimit));
      if (feature) {
        selectControl.select(feature);
        highlightNumericalLimitFeatures(feature);
      }
      selectControl.onSelect = numericalLimitOnSelect;
    }
  };

  var drawNumericalLimits = function(numericalLimits) {
    var numericalLimitsWithType = _.map(numericalLimits, function(limit) {
      return _.merge({}, limit, { type: 'line', expired: limit.expired + '' });
    });
    var offsetBySideCode = function(numericalLimit) {
      return linearAsset.offsetBySideCode(map.getZoom(), numericalLimit);
    };
    var numericalLimitsWithAdjustments = _.map(numericalLimitsWithType, offsetBySideCode);
    vectorLayer.addFeatures(lineFeatures(numericalLimitsWithAdjustments));

    reselectNumericalLimit();
  };

  var lineFeatures = function(numericalLimits) {
    return _.flatten(_.map(numericalLimits, function(numericalLimit) {
      return _.map(numericalLimit.links, function(link) {
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var numericalLimitWithRoadLinkId = _.cloneDeep(numericalLimit);
        numericalLimitWithRoadLinkId.roadLinkId = link.roadLinkId;
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), numericalLimitWithRoadLinkId);
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
