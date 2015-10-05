window.LinearAssetLayer = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedLinearAsset = params.selectedLinearAsset,
      geometryUtils = params.geometryUtils,
      roadLayer = params.roadLayer,
      multiElementEventCategory = params.multiElementEventCategory,
      singleElementEventCategory = params.singleElementEventCategory,
      style = params.style,
      layerName = params.layerName;

  Layer.call(this, layerName, roadLayer);
  var me = this;
  me.minZoomForContent = zoomlevels.minZoomForAssets;

  var singleElementEvents = function() {
    return _.map(arguments, function(argument) { return singleElementEventCategory + ':' + argument; }).join(' ');
  };

  var multiElementEvent = function(eventName) {
    return multiElementEventCategory + ':' + eventName;
  };

  var LinearAssetCutter = function(eventListener, vectorLayer, collection) {
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
      var closestLinearAssetLink = findNearestLinearAssetLink(mousePoint);
      if (!closestLinearAssetLink) {
        return;
      }
      var distanceObject = closestLinearAssetLink.distanceObject;
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

      var calculateSplitProperties = function(nearestLinearAsset, point) {
        var lineString = pointsToLineString(nearestLinearAsset.points);
        var startMeasureOffset = nearestLinearAsset.startMeasure;
        var splitMeasure = geometryUtils.calculateMeasureAtPoint(lineString, point) + startMeasureOffset;
        var splitVertices = geometryUtils.splitByPoint(pointsToLineString(nearestLinearAsset.points), point);
        return _.merge({ splitMeasure: splitMeasure }, splitVertices);
      };

      var pixel = new OpenLayers.Pixel(point.x, point.y);
      var mouseLonLat = map.getLonLatFromPixel(pixel);
      var mousePoint = new OpenLayers.Geometry.Point(mouseLonLat.lon, mouseLonLat.lat);
      var nearest = findNearestLinearAssetLink(mousePoint);

      if (!isWithinCutThreshold(nearest.distanceObject)) {
        return;
      }

      var nearestLinearAsset = nearest.feature.attributes;
      var splitProperties = calculateSplitProperties(nearestLinearAsset, mousePoint);
      selectedLinearAsset.splitLinearAsset(nearestLinearAsset.id, splitProperties);

      remove();
    };
  };

  var uiState = { zoomLevel: 9 };

  var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: style.browsing });
  vectorLayer.setOpacity(1);
  vectorLayer.setVisibility(false);
  map.addLayer(vectorLayer);

  var indicatorLayer = new OpenLayers.Layer.Boxes('adjacentLinkIndicators');
  map.addLayer(indicatorLayer);

  var linearAssetCutter = new LinearAssetCutter(me.eventListener, vectorLayer, collection);

  var highlightMultipleLinearAssetFeatures = function() {
    var partitioned = _.groupBy(vectorLayer.features, function(feature) {
      return selectedLinearAsset.isSelected(feature.attributes);
    });
    var selected = partitioned[true];
    var unSelected = partitioned[false];
    _.each(selected, function(feature) { selectControl.highlight(feature); });
    _.each(unSelected, function(feature) { selectControl.unhighlight(feature); });
  };

  var highlightLinearAssetFeatures = function() {
    highlightMultipleLinearAssetFeatures();
  };

  var setSelectionStyleAndHighlightFeature = function() {
    vectorLayer.styleMap = style.selection;
    highlightLinearAssetFeatures();
    vectorLayer.redraw();
  };

  var linearAssetOnSelect = function(feature) {
    selectedLinearAsset.open(feature.attributes, feature.singleLinkSelect);
    setSelectionStyleAndHighlightFeature();
  };

  var linearAssetOnUnselect = function() {
    if (selectedLinearAsset.exists()) {
      selectedLinearAsset.close();
    }
  };

  var selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
    onSelect: linearAssetOnSelect,
    onUnselect: linearAssetOnUnselect
  });
  map.addControl(selectControl);
  var doubleClickSelectControl = new DoubleClickSelectControl(selectControl, map);

  var massUpdateHandler = new LinearAssetMassUpdate(map, vectorLayer, selectedLinearAsset, function(linearAssets) {
    selectedLinearAsset.openMultiple(linearAssets);

    LinearAssetMassUpdateDialog.show({
      count: selectedLinearAsset.count(),
      onCancel: cancelSelection,
      onSave: function(newLinearAsset) {
        selectedLinearAsset.saveMultiple(newLinearAsset);
        activateBrowseStyle();
        selectedLinearAsset.closeMultiple();
      },
      formElements: params.formElements,
      selectedAsset: selectedLinearAsset
    });
  });

  function cancelSelection() {
    selectedLinearAsset.closeMultiple();
    activateBrowseStyle();
    collection.fetch(map.getExtent());
  }

  var handleLinearAssetUnSelected = function(eventListener, selection) {
    _.each(_.filter(vectorLayer.features, function(feature) {
      return selection.isSelected(feature.attributes);
    }), function(feature) {
      selectControl.unhighlight(feature);
    });

    vectorLayer.styleMap = style.browsing;
    vectorLayer.redraw();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
  };

  var adjustStylesByZoomLevel = function(zoom) {
    uiState.zoomLevel = zoom;
    vectorLayer.redraw();
  };

  var changeTool = function(tool) {
    if (tool === 'Cut') {
      doubleClickSelectControl.deactivate();
      linearAssetCutter.activate();
    } else if (tool === 'Select') {
      linearAssetCutter.deactivate();
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

  var activateBrowseStyle = function() {
    _.each(vectorLayer.features, function(feature) {
      selectControl.unhighlight(feature);
    });
    vectorLayer.styleMap = style.browsing;
    vectorLayer.redraw();
  };

  var bindEvents = function(eventListener) {
    var linearAssetChanged = _.partial(handleLinearAssetChanged, eventListener);
    var linearAssetCancelled = _.partial(handleLinearAssetCancelled, eventListener);
    var linearAssetUnSelected = _.partial(handleLinearAssetUnSelected, eventListener);
    eventListener.listenTo(eventbus, multiElementEvent('fetched'), redrawLinearAssets);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, singleElementEvents('selected', 'multiSelected'), handleLinearAssetSelected);
    eventListener.listenTo(eventbus, singleElementEvents('saved'), handleLinearAssetSaved);
    eventListener.listenTo(eventbus, multiElementEvent('massUpdateSucceeded'), handleLinearAssetSaved);
    eventListener.listenTo(eventbus, singleElementEvents('valueChanged', 'separated'), linearAssetChanged);
    eventListener.listenTo(eventbus, singleElementEvents('cancelled', 'saved'), linearAssetCancelled);
    eventListener.listenTo(eventbus, singleElementEvents('unselect'), linearAssetUnSelected);
    eventListener.listenTo(eventbus, 'application:readOnly', updateMassUpdateHandlerState);
    eventListener.listenTo(eventbus, singleElementEvents('selectByMmlId'), selectLinearAssetByMmlId);
    eventListener.listenTo(eventbus, multiElementEvent('massUpdateFailed'), cancelSelection);
  };

  var handleLinearAssetSelected = function() {
    setSelectionStyleAndHighlightFeature();
  };

  var selectLinearAssetByMmlId = function(mmlId) {
    var feature = _.find(vectorLayer.features, function(feature) { return feature.attributes.mmlId === mmlId; });
    if (feature) {
      selectControl.select(feature);
    }
  };

  var handleLinearAssetSaved = function() {
    collection.fetch(map.getExtent());
    applicationModel.setSelectedTool('Select');
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var handleLinearAssetChanged = function(eventListener, selectedLinearAsset) {
    doubleClickSelectControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    var selectedLinearAssetFeatures = _.filter(vectorLayer.features, function(feature) { return selectedLinearAsset.isSelected(feature.attributes); });
    vectorLayer.removeFeatures(selectedLinearAssetFeatures);
    drawLinearAssets(selectedLinearAsset.get());
    decorateSelection();
  };

  this.layerStarted = function(eventListener) {
    bindEvents(eventListener);
    changeTool(application.getSelectedTool());
    updateMassUpdateHandlerState();
  };
  this.refreshView = function(event) {
    vectorLayer.setVisibility(true);
    adjustStylesByZoomLevel(map.getZoom);
    collection.fetch(map.getExtent()).then(function() {
      eventbus.trigger('layer:linearAsset:' + event);
    });
  };
  this.activateSelection = function() {
    updateMassUpdateHandlerState();
    doubleClickSelectControl.activate();
  };
  this.deactivateSelection = function() {
    updateMassUpdateHandlerState();
    doubleClickSelectControl.deactivate();
  };
  this.removeLayerFeatures = function() {
    vectorLayer.removeAllFeatures();
    indicatorLayer.clearMarkers();
  };

  var handleLinearAssetCancelled = function(eventListener) {
    doubleClickSelectControl.activate();
    eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    redrawLinearAssets(collection.getAll());
  };

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
      if (selectedLinearAsset.isSplit()) {
        return indicatorsForSplit();
      } else {
        return indicatorsForSeparation();
      }
    };

    _.forEach(indicators(), function(indicator) {
      indicatorLayer.addMarker(indicator);
    });
  };

  var redrawLinearAssets = function(linearAssetChains) {
    doubleClickSelectControl.deactivate();
    me.removeLayerFeatures();
    if (!selectedLinearAsset.isDirty() && application.getSelectedTool() === 'Select') {
      doubleClickSelectControl.activate();
    }

    var linearAssets = _.flatten(linearAssetChains);
    drawLinearAssets(linearAssets);
    decorateSelection();
  };

  var drawLinearAssets = function(linearAssets) {
    vectorLayer.addFeatures(style.renderFeatures(linearAssets));
  };

  var decorateSelection = function() {
    var offsetBySideCode = function(linearAsset) {
      return LinearAsset().offsetBySideCode(applicationModel.zoom.level, linearAsset);
    };

    if (selectedLinearAsset.exists()) {
      withoutOnSelect(function() {
        var feature = _.find(vectorLayer.features, function(feature) { return selectedLinearAsset.isSelected(feature.attributes); });
        if (feature) {
          selectControl.select(feature);
        }
        highlightMultipleLinearAssetFeatures();
      });

      if (selectedLinearAsset.isSplitOrSeparated()) {
        drawIndicators(_.map(_.cloneDeep(selectedLinearAsset.get()), offsetBySideCode));
      }
    }
  };

  var withoutOnSelect = function(f) {
    selectControl.onSelect = function() {};
    f();
    selectControl.onSelect = linearAssetOnSelect;
  };

  var reset = function() {
    selectControl.unselectAll();
    vectorLayer.styleMap = style.browsing;
    linearAssetCutter.deactivate();
  };

  var show = function(map) {
    vectorLayer.setVisibility(true);
    indicatorLayer.setVisibility(true);
    me.show(map);
  };

  var hideLayer = function() {
    reset();
    vectorLayer.setVisibility(false);
    indicatorLayer.setVisibility(false);
    me.stop();
    me.hide();
  };

  return {
    vectorLayer: vectorLayer,
    show: show,
    hide: hideLayer,
    minZoomForContent: me.minZoomForContent
  };
};
