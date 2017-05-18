window.LinearAssetLayer = function(params) {
  var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedLinearAsset = params.selectedLinearAsset,
      roadLayer = params.roadLayer,
      multiElementEventCategory = params.multiElementEventCategory,
      singleElementEventCategory = params.singleElementEventCategory,
      style = params.style,
      layerName = params.layerName,
      assetLabel = params.assetLabel;


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
    var vectorSource = vectorLayer.getSource();

    var moveTo = function(x, y) {
      scissorFeatures = [new ol.Feature({geometry: new ol.geom.Point([x, y]), type: 'cutter' })];
      selectToolControl.removeFeatures(function(feature) {
            return feature.getProperties().type === 'cutter';
      });
      selectToolControl.addNewFeature(scissorFeatures, true);
    };

    var remove = function () {
      selectToolControl.removeFeatures(function(feature) {
          return feature && feature.getProperties().type === 'cutter';
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

    var isWithinCutThreshold = function(linearAssetLink) {
      return linearAssetLink && linearAssetLink < CUT_THRESHOLD;
    };

    var findNearestLinearAssetLink = function(point) {
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
          //TODO be sure about this distance
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
      var closestLinearAssetLink = findNearestLinearAssetLink(mousePoint);
      if (closestLinearAssetLink) {
        if (isWithinCutThreshold(closestLinearAssetLink.distance)) {
          moveTo(closestLinearAssetLink.point[0], closestLinearAssetLink.point[1]);
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

      var calculateSplitProperties = function(nearestLinearAsset, point) {
        var lineString = pointsToLineString(nearestLinearAsset.points);
        var startMeasureOffset = nearestLinearAsset.startMeasure;
        var splitMeasure = GeometryUtils.calculateMeasureAtPoint(lineString, point) + startMeasureOffset;
        var splitVertices = GeometryUtils.splitByPoint(pointsToLineString(nearestLinearAsset.points), point);
        return _.merge({ splitMeasure: splitMeasure }, splitVertices);
      };

      var nearest = findNearestLinearAssetLink([mousePoint.x, mousePoint.y]);

      if (!isWithinCutThreshold(nearest.distance)) {
        return;
      }

      var nearestLinearAsset = nearest.feature.getProperties();
      var splitProperties = calculateSplitProperties(nearestLinearAsset, mousePoint);
      selectedLinearAsset.splitLinearAsset(nearestLinearAsset.id, splitProperties);

      remove();
    };
  };

  var uiState = { zoomLevel: 9 };

  var vectorSource = new ol.source.Vector();
  var vectorLayer = new ol.layer.Vector({
    source : vectorSource,
    style : function(feature) {
      return style.browsingStyleProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel});
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

  var linearAssetCutter = new LinearAssetCutter(me.eventListener, vectorLayer, collection);

  var OnSelect = function(evt) {
    if(evt.selected.length !== 0) {
      var feature = evt.selected[0];
      var properties = feature.getProperties();
      verifyClickEvent(properties, evt);
    }else{
      if (selectedLinearAsset.exists()) {
         selectedLinearAsset.close();
      }
    }
  };

  var verifyClickEvent = function(properties, evt){
    var singleLinkSelect = evt.mapBrowserEvent.type === 'dblclick';
    selectedLinearAsset.open(properties, singleLinkSelect);
    highlightMultipleLinearAssetFeatures();
  };

  var highlightMultipleLinearAssetFeatures = function() {
    var selectedAssets = selectedLinearAsset.get();
    var features = style.renderFeatures(selectedAssets);
    if(assetLabel)
        features = features.concat(assetLabel.renderFeaturesByLinearAssets(selectedAssets, uiState.zoomLevel));
    selectToolControl.addSelectionFeatures(features);
  };

  var selectToolControl = new SelectToolControl(application, vectorLayer, map, {
    style: function(feature){ return feature.setStyle(style.browsingStyleProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel})); },
    onDragEnd: onDragEnd,
    onSelect: OnSelect
  });

  var showDialog = function (linearAssets) {
      linearAssets = _.filter(linearAssets, function(asset){
          return asset && !(asset.geometry instanceof ol.geom.Point);
      });

      selectedLinearAsset.openMultiple(linearAssets);

      var features = style.renderFeatures(selectedLinearAsset.get());
      if(assetLabel)
         features = features.concat(assetLabel.renderFeaturesByLinearAssets(selectedLinearAsset.get(), uiState.zoomLevel));
      selectToolControl.addSelectionFeatures(features);

      LinearAssetMassUpdateDialog.show({
         count: selectedLinearAsset.count(),
         onCancel: cancelSelection,
         onSave: function (value) {
           selectedLinearAsset.saveMultiple(value);
           selectToolControl.clear();
           selectedLinearAsset.closeMultiple();
         },
         validator: selectedLinearAsset.validator,
         formElements: params.formElements
      });
  };

  function onDragEnd(linearAssets) {
    if (selectedLinearAsset.isDirty()) {
        me.displayConfirmMessage();
    } else {
        if (linearAssets.length > 0) {
            selectedLinearAsset.close();
            showDialog(linearAssets);
        }
    }
  }

  function cancelSelection() {
    selectToolControl.clear();
    selectedLinearAsset.closeMultiple();
    collection.fetch(map.getView().calculateExtent(map.getSize()));
  }

  var adjustStylesByZoomLevel = function(zoom) {
    uiState.zoomLevel = zoom;
  };

  var changeTool = function(tool) {
    if (tool === 'Cut') {
      selectToolControl.deactivate();
      linearAssetCutter.activate();
    } else if (tool === 'Select') {
      linearAssetCutter.deactivate();
      selectToolControl.activate();
    }
  };

  var bindEvents = function(eventListener) {
    var linearAssetChanged = _.partial(handleLinearAssetChanged, eventListener);
    var linearAssetCancelled = _.partial(handleLinearAssetCancelled, eventListener);
    eventListener.listenTo(eventbus, singleElementEvents('unselect'), linearAssetUnSelected);
    eventListener.listenTo(eventbus, multiElementEvent('fetched'), redrawLinearAssets);
    eventListener.listenTo(eventbus, 'tool:changed', changeTool);
    eventListener.listenTo(eventbus, singleElementEvents('saved'), handleLinearAssetSaved);
    eventListener.listenTo(eventbus, multiElementEvent('massUpdateSucceeded'), handleLinearAssetSaved);
    eventListener.listenTo(eventbus, singleElementEvents('valueChanged', 'separated', 'selected'), linearAssetChanged);
    eventListener.listenTo(eventbus, singleElementEvents('cancelled', 'saved'), linearAssetCancelled);
    eventListener.listenTo(eventbus, singleElementEvents('selectByLinkId'), selectLinearAssetByLinkId);
    eventListener.listenTo(eventbus, multiElementEvent('massUpdateFailed'), cancelSelection);
  };

  var selectLinearAssetByLinkId = function(linkId) {
    var feature = _.find(vectorLayer.features, function(feature) { return feature.attributes.linkId === linkId; });
    if (feature) {
        selectToolControl.addSelectionFeatures([feature]);
    }
  };

  var linearAssetUnSelected = function () {
    selectToolControl.clear();
  };

  var handleLinearAssetSaved = function() {
    collection.fetch(map.getView().calculateExtent(map.getSize()));
    applicationModel.setSelectedTool('Select');
  };

  var handleLinearAssetChanged = function(eventListener, selectedLinearAsset) {
    //Disable interaction so the user can not click on another feature after made changes
    selectToolControl.deactivate();
    eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
    eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    var features = style.renderFeatures(selectedLinearAsset.get());
    if(assetLabel)
        features = features.concat(assetLabel.renderFeaturesByLinearAssets(selectedLinearAsset.get(), uiState.zoomLevel));
    selectToolControl.addSelectionFeatures(features);
    decorateSelection();
  };

  this.layerStarted = function(eventListener) {
    bindEvents(eventListener);
    changeTool(application.getSelectedTool());
  };

  this.refreshView = function(event) {
    vectorLayer.setVisible(true);
    adjustStylesByZoomLevel(map.getView().getZoom());
    collection.fetch(map.getView().calculateExtent(map.getSize())).then(function() {
      eventbus.trigger('layer:linearAsset:' + event);
    });
  };

  this.activateSelection = function() {
    selectToolControl.activate();
  };
  this.deactivateSelection = function() {
    selectToolControl.deactivate();
  };
  this.removeLayerFeatures = function() {
    vectorLayer.getSource().clear();
    indicatorLayer.getSource().clear();
  };

  var handleLinearAssetCancelled = function(eventListener) {
    selectToolControl.clear();
    selectToolControl.activate();
    eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
    redrawLinearAssets(collection.getAll());
  };

  var drawIndicators = function(links) {
    var features = [];

    var markerContainer = function(link, position) {
        var anchor = assetLabel.getMarkerAnchor(uiState.zoomLevel);
        var offset = assetLabel.getMarkerOffset(uiState.zoomLevel);

        var imageSettings = {src: 'images/center-marker2.svg'};
        if(anchor)
            imageSettings = _.merge(imageSettings, { anchor : anchor });

        var textSettings = {
            text : link.marker,
            fill: new ol.style.Fill({
                color: '#ffffff'
            }),
            font : '12px sans-serif'
        };
        if(offset)
          textSettings = _.merge(textSettings, {offsetX : offset[0], offsetY : offset[1]});

        var style = new ol.style.Style({
            image : new ol.style.Icon(imageSettings),
            text : new ol.style.Text(textSettings)
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
      if (selectedLinearAsset.isSplit()) {
        return indicatorsForSplit();
      }
      return indicatorsForSeparation();
    };
    indicators();
    selectToolControl.addNewFeature(features);
  };

  var redrawLinearAssets = function(linearAssetChains) {
    vectorSource.clear();
    selectToolControl.deactivate();
    indicatorLayer.getSource().clear();
    if (!selectedLinearAsset.isDirty() && application.getSelectedTool() === 'Select') {
      selectToolControl.activate();
    }
    var linearAssets = _.flatten(linearAssetChains);
      decorateSelection();

      drawLinearAssets(linearAssets);
  };

  var drawLinearAssets = function(linearAssets) {
    vectorSource.addFeatures(style.renderFeatures(linearAssets));
    if(assetLabel)
      vectorSource.addFeatures(assetLabel.renderFeaturesByLinearAssets(linearAssets, uiState.zoomLevel));
  };

  var decorateSelection = function () {
    if (selectedLinearAsset.exists()) {
      if (selectedLinearAsset.isSplitOrSeparated()) {
        var offsetBySideCode = function (linearAsset) {
          return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
        };
        drawIndicators(_.map(_.cloneDeep(selectedLinearAsset.get()), offsetBySideCode));
      }
    }
  };

  var reset = function() {
    linearAssetCutter.deactivate();
  };

  var show = function(map) {
    vectorLayer.setVisible(true);
    indicatorLayer.setVisible(true);
    me.show(map);
  };

  var hideLayer = function() {
    reset();
    vectorLayer.setVisible(false);
    indicatorLayer.setVisible(false);
    selectedLinearAsset.close();
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
