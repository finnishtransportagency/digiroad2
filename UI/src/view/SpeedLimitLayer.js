window.SpeedLimitLayer = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedSpeedLimit = params.selectedSpeedLimit,
      roadLayer = params.roadLayer,
      style = params.style,
      layerName = 'speedLimit';
  var isActive = false;

  Layer.call(this, layerName, roadLayer);
  this.activateSelection = function() {
    selectToolControl.toggleDragBox();
    selectToolControl.activate();
  };
  this.deactivateSelection = function() {
    selectToolControl.toggleDragBox();
    selectToolControl.activate();
  };
  this.minZoomForContent = zoomlevels.minZoomForAssets;
  this.layerStarted = function(eventListener) {
    bindEvents(eventListener);
    changeTool(application.getSelectedTool());
  };
  this.refreshView = function(event) {
    vectorLayer.setVisible(true);
    adjustStylesByZoomLevel(map.getView().getZoom());
    collection.fetch(map.getView().calculateExtent(map.getSize())).then(function() {

        eventbus.trigger('layer:speedLimit:' + event);
      });
    };
    if (isActive) {
      showSpeedLimitsHistory();
    }

  this.removeLayerFeatures = function() {
      vectorLayer.getSource().clear();
      indicatorLayer.getSource().clear();
      vectorLayerHistory.setVisible(false);
  };
  var me = this;

  var SpeedLimitCutter = function(vectorLayer, collection, eventListener) {
    var scissorFeatures = [];
    var CUT_THRESHOLD = 20;

    var moveTo = function(x, y) {
      _.each(scissorFeatures, function(feature){
        vectorSource.removeFeature(feature);
      });
      scissorFeatures = [new ol.Feature({geometry: new ol.geom.Point([x, y]), type: 'cutter' })];
      vectorSource.getSource().addFeatures(scissorFeatures);
    };

    var remove = function() {
      //vectorLayer.removeFeatures(scissorFeatures);
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
          self.updateByPosition(event.coordinate);
        }
      });
    };

    var isWithinCutThreshold = function(speedLimitLink) {
      return speedLimitLink && speedLimitLink < CUT_THRESHOLD;
    };

    var findNearestSpeedLimitLink = function(point) {
      return _.chain(vectorSource.getFeatures())
          .filter(function(feature) { return feature.getGeometry() instanceof ol.geom.LineString; })
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
      var closestSpeedLimitLink = findNearestSpeedLimitLink([mousePoint.x, mousePoint.y]);
      if (!closestSpeedLimitLink) {
        return;
      }
      if (isWithinCutThreshold(closestSpeedLimitLink.distance)) {
        moveTo(closestSpeedLimitLink.point[0], closestSpeedLimitLink.point[1]);
      } else {
        remove();
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
      var splitProperties = calculateSplitProperties(nearestSpeedLimit, mousePoint);
      selectedSpeedLimit.splitSpeedLimit(nearestSpeedLimit.id, splitProperties);

      remove();
    };
  };

  var uiState = { zoomLevel: 9 };

  //var vectorLayerHistory = new OpenLayers.Layer.Vector(layerName, { styleMap: historyStyleMap});
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

  vectorLayer.setOpacity(1);
  vectorLayer.setVisible(false);
  map.addLayer(vectorLayer);

  var indicatorVector = new ol.source.Vector({});
  var indicatorLayer = new ol.layer.Vector({
    source : indicatorVector
  });
  map.addLayer(indicatorLayer);
  indicatorLayer.setVisible(false);

  var speedLimitCutter = new SpeedLimitCutter(vectorLayer, collection, me.eventListener);

  var highlightMultipleSpeedLimitFeatures = function() {
    var partitioned = _.groupBy(vectorLayer.features, function(feature) {
      return selectedSpeedLimit.isSelected(feature.attributes);
    });
    var selected = partitioned[true];
    var unSelected = partitioned[false];
    _.each(selected, function(feature) { selectControl.highlight(feature); });
  };

  var highlightSpeedLimitFeatures = function() {
    highlightMultipleSpeedLimitFeatures();
  };

  var setSelectionStyleAndHighlightFeature = function() {
    vectorLayer.styleMap = style.selectionStyle;
    highlightSpeedLimitFeatures();
    vectorLayer.redraw();
  };

  var speedLimitOnSelect = function(feature) {
    if(feature.selected.length !== 0) {
      selectedSpeedLimit.open(feature.selected[0].values_, true);
      setSelectionStyleAndHighlightFeature();
    }else {
      if (feature.selected.length === 0 && feature.deselected.length > 0) {
        if (selectedSpeedLimit.exists()) {
          selectedSpeedLimit.close();
        }
      }
    }
  };
  var OnSelect = function(feature) {
    if(feature.selected.length !== 0) {
      selectedSpeedLimit.open(feature.selected[0].values_, true);
    }else{
      if (selectedSpeedLimit.exists()) {
        selectedSpeedLimit.close();
      }
    }
  };

  var selectToolControl = new SelectAndDragToolControl(application, vectorLayer, map, {
    style: function(feature){ return feature.setStyle(style.browsingStyle.getStyle(feature, {zoomLevel: uiState.zoomLevel})); },
    onDragEnd: onDragEnd,
    onSelect: OnSelect,
    //backgroundOpacity: style.vectorOpacity
  });

  function onDragEnd(speedLimits) {
    if (selectedSpeedLimit.isDirty()) {
      displayConfirmMessage();
    } else {
      if (speedLimits.length > 0) {
        selectedSpeedLimit.close();
        showDialog(speedLimits);
      }
    }
  }
  var showDialog = function (speedLimits) {
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
    //activate.style.browsingStyle();
    collection.fetch(map.getView().calculateExtent(map.getSize()));
  }

  var handleSpeedLimitUnSelected = function(selection) {
    _.each(_.filter(vectorLayer.features, function(feature) {
      return selection.isSelected(feature.attributes);
    }), function(feature) {
      selectControl.unhighlight(feature);
    });

    vectorLayer.styleMap = style.browsingStyle;
    //vectorLayer.redraw();
    me.eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
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
    //vectorLayer.redraw();
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
    vectorLayer.styleMap = style.selectionStyle;
    selectedSpeedLimit.openMultiple(selectedSpeedLimits);
    highlightMultipleSpeedLimitFeatures();
  //  vectorLayer.redraw();
  };

  var bindEvents = function(eventListener) {
    eventListener.listenTo(eventbus, 'speedLimits:fetched', redrawSpeedLimits);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
   // eventListener.listenTo(eventbus, 'speedLimit:selected speedLimit:multiSelected', handleSpeedLimitSelected);
    eventListener.listenTo(eventbus, 'speedLimit:saved speedLimits:massUpdateSucceeded', handleSpeedLimitSaved);
    eventListener.listenTo(eventbus, 'speedLimit:valueChanged speedLimit:separated', handleSpeedLimitChanged);
    eventListener.listenTo(eventbus, 'speedLimit:cancelled speedLimit:saved', handleSpeedLimitCancelled);
    //eventListener.listenTo(eventbus, 'speedLimit:unselect', handleSpeedLimitUnSelected);
    //eventListener.listenTo(eventbus, 'application:readOnly', updateMassUpdateHandlerState);
    eventListener.listenTo(eventbus, 'speedLimit:selectByLinkId', selectSpeedLimitByLinkId);
    eventListener.listenTo(eventbus, 'speedLimits:massUpdateFailed', cancelSelection);
    eventListener.listenTo(eventbus, 'speedLimits:drawSpeedLimitsHistory', drawSpeedLimitsHistory);
    eventListener.listenTo(eventbus, 'speedLimits:hideSpeedLimitsHistory', hideSpeedLimitsHistory);
    eventListener.listenTo(eventbus, 'speedLimits:showSpeedLimitsHistory', showSpeedLimitsHistory);
  };

  var showSpeedLimitsHistory = function() {
    collection.fetchHistory(map.getView().calculateExtent(map.getSize()));
  };

  var hideSpeedLimitsHistory = function() {
    vectorLayerHistory.setVisible(false);
    isActive = false;
    vectorLayerHistory.getSource().clear();
  };

  var drawSpeedLimitsHistory = function (historySpeedLimitChains) {
    isActive = true;
    map.addLayer(vectorLayerHistory);
    //var roadLinksLayerIndex = map.getLayers().indexOf(_.find(map.getLayers(), {name: 'road'} ));
    //map.setLayerIndex(vectorLayerHistory, roadLinksLayerIndex - 1);
    var historySpeedLimits = _.flatten(historySpeedLimitChains);

    drawSpeedLimits(historySpeedLimits, vectorLayerHistory);
  };

  var selectSpeedLimitByLinkId = function(linkId) {
    var feature = _.find(vectorLayer.features, function(feature) { return feature.attributes.linkId === linkId; });
    if (feature) {
      selectControl.select(feature);
    }
  };

  var handleSpeedLimitSaved = function() {
    collection.fetch(map.getView().calculateExtent(map.getSize()));
    applicationModel.setSelectedTool('Select');
  };

  var displayConfirmMessage = function() { new Confirm(); };

  var handleSpeedLimitChanged = function(selectedSpeedLimit) {
    selectToolControl.deactivate();
    me.eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    me.eventListener.listenTo(eventbus, 'map:clicked', displayConfirmMessage);
    var selectedSpeedLimitFeatures = _.filter(vectorLayer.features, function(feature) { return selectedSpeedLimit.isSelected(feature.attributes); });
    //vectorLayer.removeFeatures(selectedSpeedLimitFeatures);
    selectToolControl.addSelectionFeatures(style.renderFeatures(selectedSpeedLimit.get()));

    //drawSpeedLimits(selectedSpeedLimit.get(), vectorLayer);
  };

  var handleSpeedLimitCancelled = function() {
    selectToolControl.activate();
    me.eventListener.stopListening(eventbus, 'map:clicked', displayConfirmMessage);
    redrawSpeedLimits(collection.getAll());
  };

    var drawIndicators = function(links) {
        var features = [];

        var markerContainer = function(link, position) {
            var style = new ol.style.Style({
                image : new ol.style.Icon({
                    src: 'images/center-marker.svg'
                }),
                text : new ol.style.Text({
                    text : link.marker,
                    fill: new ol.style.Fill({
                        color: "#ffffff"
                    })
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
    selectToolControl.deactivate();
    //me.removeLayerFeatures();
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
      var feature = _.find(layerToUse.features, function(feature) { return selectedSpeedLimit.isSelected(feature.attributes); });
     if (feature) {
        selectControl.select(feature);
      }
     highlightMultipleSpeedLimitFeatures();
      selectToolControl.onSelect = speedLimitOnSelect;

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
    //selectControl.unselectAll();
    vectorLayer.styleMap = style.browsingStyle;
    speedLimitCutter.deactivate();
  };

  var show = function(map) {
    vectorLayer.setVisible(true);
    indicatorLayer.setVisible(true);
    me.show(map);
  };

  var hideLayer = function(map) {
    reset();
    vectorLayer.setVisible(false);
    vectorLayerHistory.setVisible(false);
    indicatorLayer.setVisible(false);
    me.stop();
    me.hide();
  };

  return {
    update: update,
    vectorLayer: vectorLayer,
    show: show,
    hide: hideLayer
  };
};
