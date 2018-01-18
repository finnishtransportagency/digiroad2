window.SpeedLimitLayer = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedSpeedLimit = params.selectedSpeedLimit,
      roadLayer = params.roadLayer,
      style = params.style,
      layerName = 'speedLimit',
      roadAddressInfoPopup= params.roadAddressInfoPopup,
      trafficSignReadOnlyLayer = params.trafficSignReadOnlyLayer;
  var isActive = false;
  var extraEventListener = _.extend({running: false}, eventbus);

  Layer.call(this, layerName, roadLayer);
  this.activateSelection = function() {
    selectToolControl.activate();
  };
  this.deactivateSelection = function() {
    selectToolControl.deactivate();
  };
  this.minZoomForContent = zoomlevels.minZoomForAssets;
  this.layerStarted = function(eventListener) {
    bindEvents(eventListener);
  };

  var uiState = { zoomLevel: 9 };

  var vectorSourceHistory = new ol.source.Vector();
  var vectorLayerHistory = new ol.layer.Vector({
    source : vectorSourceHistory,
    style : function(feature) {
      return style.historyStyle.getStyle( feature, {zoomLevel: uiState.zoomLevel});
    }
  });

  vectorLayerHistory.setOpacity(1);
  vectorLayerHistory.setVisible(false);

  var vectorSource = new ol.source.Vector();
  var vectorLayer = new ol.layer.Vector({
    source : vectorSource,
    style : function(feature) {
      return style.browsingStyle.getStyle( feature, {zoomLevel: uiState.zoomLevel});
    }
  });
  vectorLayer.set('name', layerName);
  vectorLayer.setOpacity(1);
  vectorLayer.setVisible(false);
  map.addLayer(vectorLayer);

  var indicatorVector = new ol.source.Vector({});
  var indicatorLayer = new ol.layer.Vector({
    source : indicatorVector
  });
  map.addLayer(indicatorLayer);
  indicatorLayer.setVisible(false);

  this.refreshView = function(event) {
    vectorLayer.setVisible(true);
    adjustStylesByZoomLevel(map.getView().getZoom());
    collection.fetch(map.getView().calculateExtent(map.getSize())).then(function() {
      eventbus.trigger('layer:speedLimit:' + event);
    });
    if (isActive) {
      showSpeedLimitsHistory();
    }
    trafficSignReadOnlyLayer.refreshView();
  };

  this.removeLayerFeatures = function() {
      vectorLayer.getSource().clear();
      indicatorLayer.getSource().clear();
      vectorLayerHistory.setVisible(false);
  };
  var me = this;

  var SpeedLimitCutter = function(vectorLayer, collection, eventListener) {
    var scissorFeatures = [];
    var CUT_THRESHOLD = 20;
    var vectorSource = vectorLayer.getSource();

    var moveTo = function(x, y) {
      scissorFeatures = [new ol.Feature({geometry: new ol.geom.Point([x, y]), type: 'cutter' })];
      selectToolControl.removeFeatures(function(feature) {
        return feature.getProperties().type === 'cutter';
      });
      selectToolControl.addNewFeature(scissorFeatures, true);
    };

    var remove = function() {
      selectToolControl.removeFeatures(function(feature) {
        return feature.getProperties().type === 'cutter';
      });
      scissorFeatures = [];
    };

    var self = this;

    var clickHandler = function(evt) {
      if (application.getSelectedTool() === 'Cut') {
        if (collection.isDirty()) {
          me.displayConfirmMessage();
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
          self.updateByPosition(event.coordinate);
        }
      });
    };

    var isWithinCutThreshold = function(speedLimitLink) {
      return speedLimitLink !== undefined && speedLimitLink < CUT_THRESHOLD;
    };

    var findNearestSpeedLimitLink = function(point) {
      return _.chain(vectorSource.getFeatures())
          .filter(function(feature) {
            return feature.getGeometry() instanceof ol.geom.LineString;
          })
          .reject(function(feature) {
            var properties = feature.getProperties();
            return _.has(properties, 'generatedId') && _.flatten(collection.getGroup(properties)).length > 0;
          })
          .map(function(feature) {
            var closestP = feature.getGeometry().getClosestPoint(point);
            var distanceBetweenPoints = GeometryUtils.distanceOfPoints(point, closestP);
            return {
              feature: feature,
              point: closestP,
              distance: distanceBetweenPoints
            };
          })
          .sortBy(function(nearest) {
            return nearest.distance;
          })
          .head()
          .value();
    };

    this.updateByPosition = function(mousePoint) {
      var closestSpeedLimitLink = findNearestSpeedLimitLink(mousePoint);
      if (!closestSpeedLimitLink) {
        return;
      }
      if (!editConstrains(closestSpeedLimitLink)) {
        if (isWithinCutThreshold(closestSpeedLimitLink.distance)) {
          moveTo(closestSpeedLimitLink.point[0], closestSpeedLimitLink.point[1]);
        } else {
          remove();
        }
      }
    };

    this.cut = function(mousePoint) {
      var pointsToLineString = function(points) {
        var coordPoints = _.map(points, function(point) { return [point.x, point.y]; });
        return new ol.geom.LineString(coordPoints);
      };

      var calculateSplitProperties = function(nearestSpeedLimit, point) {
        var lineString = pointsToLineString(nearestSpeedLimit.points);
        var startMeasureOffset = nearestSpeedLimit.startMeasure;
        var splitMeasure = GeometryUtils.calculateMeasureAtPoint(lineString, point) + startMeasureOffset;
        var splitVertices = GeometryUtils.splitByPoint(pointsToLineString(nearestSpeedLimit.points), point);
        return _.merge({ splitMeasure: splitMeasure }, splitVertices);
      };

      var nearest = findNearestSpeedLimitLink([mousePoint.x, mousePoint.y]);

      if (!isWithinCutThreshold(nearest.distance)) {
        return;
      }

      var nearestSpeedLimit = nearest.feature.getProperties();
      if(!editConstrains(nearestSpeedLimit)){
        var splitProperties = calculateSplitProperties(nearestSpeedLimit, mousePoint);
        selectedSpeedLimit.splitSpeedLimit(nearestSpeedLimit.id, splitProperties);

        remove();
      }
    };
  };

  var speedLimitCutter = new SpeedLimitCutter(vectorLayer, collection, me.eventListener);

  var OnSelect = function(evt) {
    if(evt.selected.length !== 0) {
      var feature = evt.selected[0];
      var properties = feature.getProperties();
      verifyClickEvent(properties, evt);
    }else{
      if (selectedSpeedLimit.exists()) {
        selectToolControl.clear();
        selectedSpeedLimit.close();
        trafficSignReadOnlyLayer.highLightLayer();
      }
    }
  };

  var verifyClickEvent = function(properties, evt){
    var singleLinkSelect = evt.mapBrowserEvent.type === 'dblclick';
    selectedSpeedLimit.open(properties, singleLinkSelect);
    highlightMultipleLinearAssetFeatures();
  };

  var highlightMultipleLinearAssetFeatures = function() {
    var selectedAsset = selectedSpeedLimit.get();
    selectToolControl.addSelectionFeatures(style.renderFeatures(selectedAsset));
    trafficSignReadOnlyLayer.unHighLightLayer();
  };

  var selectToolControl = new SelectToolControl(application, vectorLayer, map, {
    style: function(feature){ return style.browsingStyle.getStyle(feature, {zoomLevel: uiState.zoomLevel}); },
    onInteractionEnd: onInteractionEnd,
    onSelect: OnSelect,
    filterGeometry: function(feature) { return true; }
  });

  function onInteractionEnd(speedLimits) {
    if (selectedSpeedLimit.isDirty()) {
      me.displayConfirmMessage();
    } else {
      if (speedLimits.length > 0) {
        selectedSpeedLimit.close();
        showDialog(speedLimits);
      }
    }
  }
  var showDialog = function (speedLimits) {

    speedLimits = _.filter(speedLimits, function(asset){
      return !editConstrains(asset);
    });

    activateSelectionStyle(speedLimits);

    selectToolControl.addSelectionFeatures(style.renderFeatures(selectedSpeedLimit.get()));

       SpeedLimitMassUpdateDialog.show({
       count: selectedSpeedLimit.count(),
       onCancel: cancelSelection,
       onSave: function(newSpeedLimit) {
         selectedSpeedLimit.saveMultiple(newSpeedLimit);
         selectToolControl.clear();
         selectedSpeedLimit.closeMultiple();
       },
         validator: selectedSpeedLimit.validator,
         formElements: params.formElements
   });
  };
  function cancelSelection() {
    selectToolControl.clear();
    selectedSpeedLimit.closeMultiple();
    collection.fetch(map.getView().calculateExtent(map.getSize()));
  }

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
    vectorLayerHistory.setVisible(true);
  };

  var changeTool = function(tool) {
    if (tool === 'Cut') {
      selectToolControl.deactivate();
      speedLimitCutter.activate();
    } else if (tool === 'Select') {
      speedLimitCutter.deactivate();
      selectToolControl.activate();
    }
  };

  var activateSelectionStyle = function(selectedSpeedLimits) {
    selectedSpeedLimit.openMultiple(selectedSpeedLimits);
  };

  var bindEvents = function(eventListener) {
    eventListener.listenTo(eventbus, 'speedLimits:fetched', redrawSpeedLimits);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, 'speedLimit:saved speedLimits:massUpdateSucceeded', handleSpeedLimitSaved);
    eventListener.listenTo(eventbus, 'speedLimit:valueChanged speedLimit:separated', handleSpeedLimitChanged);
    eventListener.listenTo(eventbus, 'speedLimit:cancelled speedLimit:saved', handleSpeedLimitCancelled);
    eventListener.listenTo(eventbus, 'speedLimit:selectByLinkId', selectSpeedLimitByLinkId);
    eventListener.listenTo(eventbus, 'speedLimits:massUpdateFailed', cancelSelection);
    eventListener.listenTo(eventbus, 'speedLimits:drawSpeedLimitsHistory', drawSpeedLimitsHistory);
    eventListener.listenTo(eventbus, 'speedLimits:hideSpeedLimitsHistory', hideSpeedLimitsHistory);
    eventListener.listenTo(eventbus, 'speedLimits:showSpeedLimitsHistory', showSpeedLimitsHistory);
    eventListener.listenTo(eventbus, 'toggleWithRoadAddress', refreshSelectedView);
    eventListener.listenTo(eventbus, 'speedLimit:unselect', handleSpeedLimitUnselected);
  };

  var startListeningExtraEvents = function(){
    extraEventListener.listenTo(eventbus, 'speedLimits:hideSpeedLimitsComplementary', hideSpeedLimitsComplementary);
    extraEventListener.listenTo(eventbus, 'speedLimits:showSpeedLimitsComplementary', showSpeedLimitsComplementary);
  };

  var stopListeningExtraEvents = function(){
    extraEventListener.stopListening(eventbus);
  };

  var showSpeedLimitsHistory = function() {
    collection.fetchHistory(map.getView().calculateExtent(map.getSize()));
  };

  var hideSpeedLimitsHistory = function() {
    vectorLayerHistory.setVisible(false);
    isActive = false;
    vectorLayerHistory.getSource().clear();
  };

  var showSpeedLimitsComplementary = function() {
    collection.activeComplementary(true);
    trafficSignReadOnlyLayer.showTrafficSignsComplementary();
    me.refreshView();
  };

  var hideSpeedLimitsComplementary = function() {
    collection.activeComplementary(false);
    trafficSignReadOnlyLayer.hideTrafficSignsComplementary();
    me.refreshView();
  };

  var indexOf = function (layers, layer) {
    var length = layers.getLength();
    for (var i = 0; i < length; i++) {
      if (layer === layers.item(i)) {
        return i;
      }
    }
    return -1;
  };

  var drawSpeedLimitsHistory = function (historySpeedLimitChains) {
    isActive = true;
    vectorLayerHistory.set('name', layerName);

    var roadLinksLayerIndex = indexOf(map.getLayers(),_.find(map.getLayers().getArray(), function(item){ return item.get('name') == 'road';}));
    map.getLayers().insertAt(roadLinksLayerIndex, vectorLayerHistory);
    var historySpeedLimits = _.flatten(historySpeedLimitChains);

    drawSpeedLimits(historySpeedLimits, vectorLayerHistory);
  };

  var selectSpeedLimitByLinkId = function(linkId) {
    var feature = _.filter(vectorLayer.getSource().getFeatures(), function(feature) { return feature.getProperties().linkId === linkId; });
    if (feature) {
      selectToolControl.addSelectionFeatures(feature);
    }
  };

  var selectSpeedLimit = function(feature) {
    var features = _.filter(vectorLayer.getSource().getFeatures(), function(item) {
        return item.getProperties().id === feature.id && item.getProperties().linkId === feature.linkId ;
    });
    if (features) {
        selectToolControl.addSelectionFeatures(features);
    }
  };

  var handleSpeedLimitSaved = function() {
    collection.fetch(map.getView().calculateExtent(map.getSize()));
    applicationModel.setSelectedTool('Select');
  };

  var handleSpeedLimitChanged = function(selectedSpeedLimit) {
    selectToolControl.deactivate();
    me.eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
    me.eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    var selectedSpeedLimitFeatures = _.filter(vectorLayer.features, function(feature) { return selectedSpeedLimit.isSelected(feature.attributes); });
    selectToolControl.addSelectionFeatures(style.renderFeatures(selectedSpeedLimit.get()));
  };

  var handleSpeedLimitCancelled = function() {
    selectToolControl.addSelectionFeatures(style.renderFeatures(selectedSpeedLimit.get()));
    selectToolControl.activate();
    me.eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
    redrawSpeedLimits(collection.getAll());
    trafficSignReadOnlyLayer.highLightLayer();
  };

  var handleSpeedLimitUnselected = function () {
    selectToolControl.activate();
    me.eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
  };

    var drawIndicators = function(links) {
        var features = [];

        var markerContainer = function(link, position) {
            var style = new ol.style.Style({
                image : new ol.style.Icon({
                    src: 'images/center-marker2.svg',
                    anchor : [-0.45, 0.15]
                }),
                text : new ol.style.Text({
                    text : link.marker,
                    fill: new ol.style.Fill({
                        color: '#ffffff'
                    }),
                    offsetX : 23,
                    offsetY : 7.5,
                    font : '12px sans-serif'
                })
            });
            var marker = new ol.Feature({
                geometry : new ol.geom.Point([position.x, position.y])
            });
            marker.setStyle(style);
            features.push(marker);
        };

        var indicatorsForSplit = function() {
            return me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
                markerContainer(link, middlePoint);
            });
        };

        var indicatorsForSeparation = function() {
            var geometriesForIndicators = _.map(links, function(link) {
                var newLink = _.cloneDeep(link);
                newLink.points = _.drop(newLink.points, 1);
                return newLink;
            });

            return me.mapOverLinkMiddlePoints(geometriesForIndicators, function(link, middlePoint) {
                markerContainer(link, middlePoint);
            });
        };

        var indicators = function() {
            if (selectedSpeedLimit.isSplit()) {
                return indicatorsForSplit();
            } else {
                return indicatorsForSeparation();
            }
        };

        indicators();
        indicatorLayer.getSource().addFeatures(features);
    };

  var redrawSpeedLimits = function(speedLimitChains) {
    vectorSource.clear();
    selectToolControl.clear();
    selectToolControl.deactivate();
    indicatorLayer.getSource().clear();
    if (!selectedSpeedLimit.isDirty() && application.getSelectedTool() === 'Select') {
      selectToolControl.activate();
    }

    var speedLimits = _.flatten(speedLimitChains);
    drawSpeedLimits(speedLimits, vectorLayer);
  };

  var drawSpeedLimits = function(speedLimits, layerToUse) {
    var speedLimitsWithType = _.map(speedLimits, function(limit) { return _.merge({}, limit, { type: 'other' }); });
    var offsetBySideCode = function(speedLimit) {
      return GeometryUtils.offsetBySideCode(map.getView().getZoom(), speedLimit);
    };
    var speedLimitsWithAdjustments = _.map(speedLimitsWithType, offsetBySideCode);
    var speedLimitsSplitAt70kmh = _.groupBy(speedLimitsWithAdjustments, function(speedLimit) { return speedLimit.value >= 70; });
    var lowSpeedLimits = speedLimitsSplitAt70kmh[false];
    var highSpeedLimits = speedLimitsSplitAt70kmh[true];

    layerToUse.setVisible(true);
    layerToUse.getSource().addFeatures(lineFeatures(lowSpeedLimits));
    layerToUse.getSource().addFeatures(dottedLineFeatures(highSpeedLimits));
    layerToUse.getSource().addFeatures(limitSigns(speedLimitsWithAdjustments));

    if (selectedSpeedLimit.exists()) {
      selectToolControl.onSelect = function() {};
      var feature = _.filter(layerToUse.getSource().getFeatures(), function(feature) { return selectedSpeedLimit.isSelected(feature.getProperties()); });
      if (feature) {
        selectToolControl.addSelectionFeatures(feature);
      }
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
        return [point.x, point.y];
      });
      var road = new ol.geom.LineString(points);
      var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
      var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
      var properties = _.merge(_.cloneDeep(speedLimit), type);
      var feature = new ol.Feature(new ol.geom.Point([signPosition.x, signPosition.y]));
      feature.setProperties(_.omit(properties, 'geometry'));
      return feature;
    });
  };

  var lineFeatures = function(speedLimits) {
    return _.map(speedLimits, function(speedLimit) {
      var points = _.map(speedLimit.points, function(point) {
        return [point.x, point.y];
      });
      var type = isUnknown(speedLimit) ? { type: 'unknown' } : {};
      var properties = _.merge(_.cloneDeep(speedLimit), type);
      var feature = new ol.Feature(new ol.geom.LineString(points));
      feature.setProperties(_.omit(properties, 'geometry'));
      return feature;
    });
  };

  var reset = function() {
    vectorLayer.styleMap = style.browsingStyle;
    speedLimitCutter.deactivate();
  };

  var show = function(map) {
    startListeningExtraEvents();
    vectorLayer.setVisible(true);
    indicatorLayer.setVisible(true);
    roadAddressInfoPopup.start();
    me.show(map);
  };

  var hideReadOnlyLayer = function(){
    if(!_.isUndefined(trafficSignReadOnlyLayer)){
      trafficSignReadOnlyLayer.hide();
      trafficSignReadOnlyLayer.removeLayerFeatures();
    }
  };

  var hideLayer = function(map) {
    reset();
    selectToolControl.clear();
    hideReadOnlyLayer();
    selectedSpeedLimit.close();
    vectorLayer.setVisible(false);
    vectorLayerHistory.setVisible(false);
    indicatorLayer.setVisible(false);
    stopListeningExtraEvents();
    roadAddressInfoPopup.stop();
    me.stop();
    me.hide();
  };

  var refreshSelectedView = function(){
    if(applicationModel.getSelectedLayer() == layerName)
      me.refreshView();
  };

  var editConstrains = function(selectedAsset) {
    return false;
    //TODO revert this when DROTH-909
    //return selectedAsset.administrativeClass === 'State';
  };

  return {
    update: update,
    vectorLayer: vectorLayer,
    show: show,
    hide: hideLayer
  };
};
