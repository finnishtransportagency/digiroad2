window.LinearAssetLayer = function(params) {
  var map = params.map,
    application = params.application,
    collection = params.collection,
    selectedLinearAsset = params.selectedLinearAsset,
    roadCollection = params.roadCollection,
    geometryUtils = params.geometryUtils,
    linearAssetsUtility = params.linearAsset,
    layerName = params.layerName,
    multiElementEventCategory = params.multiElementEventCategory,
    singleElementEventCategory = params.singleElementEventCategory;
  Layer.call(this, layerName, params.roadLayer);
  var me = this;

  var LinearAssetCutter = function(vectorLayer, collection) {
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

    var isWithinCutThreshold = function(linearAssetLink) {
      return linearAssetLink && linearAssetLink.distance < CUT_THRESHOLD;
    };

    var findNearestLinearAssetLink = function(point) {
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
      var nearestLinearAssetLink = findNearestLinearAssetLink(mousePoint);
      if (!nearestLinearAssetLink) {
        return;
      }
      var distanceObject = nearestLinearAssetLink.distanceObject;
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
      var nearest = findNearestLinearAssetLink(mousePoint);

      if (!isWithinCutThreshold(nearest.distanceObject)) {
        return;
      }

      var points = _.chain(roadCollection.get([nearest.feature.attributes.mmlId])[0].getPoints())
        .map(function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        })
        .value();
      var lineString = new OpenLayers.Geometry.LineString(points);
      var split = {splitMeasure: geometryUtils.calculateMeasureAtPoint(lineString, mousePoint)};
      _.merge(split, geometryUtils.splitByPoint(nearest.feature.geometry, mousePoint));

      collection.splitLinearAsset(nearest.feature.attributes.id, nearest.feature.attributes.mmlId, split);
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

  var linearAssetCutter = new LinearAssetCutter(vectorLayer, collection);

  var setSelectionStyleAndHighlightFeature = function(feature) {
    vectorLayer.styleMap = selectionStyle;
    vectorLayer.redraw();
  };

  function createTempId(asset) {
    return asset.mmlId.toString() + asset.startMeasure + asset.endMeasure;
  }
  var findUnpersistedWeightFeatures = function(id) {
    return _.filter(vectorLayer.features, function(feature) { return createTempId(feature.attributes) === id; });
  };

  var findRoadLinkFeaturesByMmlId = function(id) {
    return _.filter(vectorLayer.features, function(feature) { return feature.attributes.mmlId === id; });
  };

  var findWeightFeaturesById = function(id) {
    return _.filter(vectorLayer.features, function(feature) { return feature.attributes.id === id; });
  };

  var linearAssetOnSelect = function(feature) {
    setSelectionStyleAndHighlightFeature(feature);
    if (feature.attributes.id) {
      selectedLinearAsset.open(feature.attributes.id);
    } else {
      selectedLinearAsset.create(feature.attributes.mmlId, feature.attributes.points);
    }
  };

  var selectControl = new OpenLayers.Control.SelectFeature([vectorLayer], {
    onSelect: linearAssetOnSelect,
    onUnselect: function(feature) {
      if (selectedLinearAsset.exists()) {
        var id = selectedLinearAsset.getId();
        var expired = selectedLinearAsset.expired();
        selectedLinearAsset.close();
        if (expired) {
          vectorLayer.removeFeatures(_.filter(vectorLayer.features, function(feature) {
            return feature.attributes.id === id;
          }));
        }
      }
    }
  });
  map.addControl(selectControl);

  var handleLinearAssetUnSelected = function(id, mmlId) {
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
      reselectLinearAsset();
      collection.fetch(boundingBox, selectedLinearAsset);
    }
  };

  var adjustStylesByZoomLevel = function(zoom) {
    uiState.zoomLevel = zoom;
    vectorLayer.redraw();
  };

  var changeTool = function(tool) {
    if (tool === 'Cut') {
      selectControl.deactivate();
      linearAssetCutter.activate();
    } else if (tool === 'Select') {
      linearAssetCutter.deactivate();
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
    linearAssetCutter.deactivate();
    eventListener.stopListening(eventbus);
    eventListener.running = false;
  };

  var bindEvents = function() {
    eventListener.listenTo(eventbus, multiElementEvent('fetched'), redrawLinearAssets);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, singleElementEvents('selected'), handleLinearAssetSelected);
    eventListener.listenTo(eventbus,
      singleElementEvents('limitChanged', 'expirationChanged'),
      handleLinearAssetChanged);
    eventListener.listenTo(eventbus, singleElementEvents('cancelled', 'saved'), concludeUpdate);
    eventListener.listenTo(eventbus, singleElementEvents('unselected'), handleLinearAssetUnSelected);
  };

  var handleLinearAssetSelected = function(selectedLinearAsset) {
    if (selectedLinearAsset.isNew()) {
      var feature = _.first(findWeightFeaturesById(selectedLinearAsset.getId())) || _.first(findRoadLinkFeaturesByMmlId(selectedLinearAsset.getMmlId()));
      setSelectionStyleAndHighlightFeature(feature);
    }
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var handleLinearAssetChanged = function(selectedLinearAsset) {
    selectControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    _.each(vectorLayer.selectedFeatures, function(selectedFeature) {
      vectorLayer.removeFeatures(selectedFeature);
    });
    drawLinearAssets([selectedLinearAsset.get()]);
  };

  var concludeUpdate = function() {
    selectControl.activate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    redrawLinearAssets(collection.getAll());
  };

  var handleMapMoved = function(state) {
    if (zoomlevels.isInAssetZoomLevel(state.zoom) && state.selectedLayer === layerName) {
      vectorLayer.setVisibility(true);
      adjustStylesByZoomLevel(state.zoom);
      start();
      reselectLinearAsset();
      collection.fetch(state.bbox, selectedLinearAsset);
    } else {
      vectorLayer.setVisibility(false);
      stop();
    }
  };

  eventbus.on('map:moved', handleMapMoved);

  var redrawLinearAssets = function(linearAssets) {
    selectControl.deactivate();
    vectorLayer.removeAllFeatures();
    if (!selectedLinearAsset.isDirty() && application.getSelectedTool() === 'Select') {
      selectControl.activate();
    }

    drawLinearAssets(linearAssets);
  };

  var getSelectedFeatures = function(selectedLinearAsset) {
    if (selectedLinearAsset.isNew()) {
      if (selectedLinearAsset.isDirty()) {
        return findUnpersistedWeightFeatures(createTempId(selectedLinearAsset.get()));
      } else {
        return findRoadLinkFeaturesByMmlId(selectedLinearAsset.getMmlId());
      }
    } else {
      return findWeightFeaturesById(selectedLinearAsset.getId());
    }
  };

  var reselectLinearAsset = function() {
    if (selectedLinearAsset.exists()) {
      selectControl.onSelect = function() {};
      var feature = _.first(getSelectedFeatures(selectedLinearAsset));
      if (feature) {
        selectControl.select(feature);
      }
      selectControl.onSelect = linearAssetOnSelect;
    }
  };

  var drawLinearAssets = function(linearAssets) {
    var linearAssetsWithType = _.map(linearAssets, function(limit) {
      var expired = _.isUndefined(limit.id) || limit.expired;
      return _.merge({}, limit, { type: 'line', expired: expired + '' });
    });
    var offsetBySideCode = function(linearAsset) {
      return linearAssetsUtility.offsetBySideCode(map.getZoom(), linearAsset);
    };
    var linearAssetsWithAdjustments = _.map(linearAssetsWithType, offsetBySideCode);
    vectorLayer.addFeatures(lineFeatures(linearAssetsWithAdjustments));

    reselectLinearAsset();
  };

  var lineFeatures = function(linearAssets) {
    return _.flatten(_.map(linearAssets, function(linearAsset) {
        var points = _.map(linearAsset.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), linearAsset);
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
