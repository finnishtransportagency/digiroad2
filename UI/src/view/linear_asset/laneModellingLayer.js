(function(root) {
  root.LaneModellingLayer  = function(params) {
    var map = params.map,
      application = params.application,
      collection = params.collection,
      selectedLinearAsset = params.selectedLinearAsset,
      roadLayer = params.roadLayer,
      multiElementEventCategory = params.multiElementEventCategory,
      singleElementEventCategory = params.singleElementEventCategory,
      style = params.style,
      layerName = params.layerName,
      assetLabel = params.assetLabel,
      roadAddressInfoPopup = params.roadAddressInfoPopup,
      massLimitation = params.massLimitation,
      trafficSignReadOnlyLayer = params.readOnlyLayer,
      isMultipleLinkSelectionAllowed = params.isMultipleLinkSelectionAllowed,
      authorizationPolicy = params.authorizationPolicy,
      isExperimental = params.isExperimental,
      minZoomForContent = params.minZoomForContent;

    Layer.call(this, layerName, roadLayer);
    var me = this;
    me.minZoomForContent = isExperimental && minZoomForContent ? minZoomForContent : zoomlevels.minZoomForAssets;
    var isComplementaryChecked = false;
    var extraEventListener = _.extend({running: false}, eventbus);

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
        if (application.getSelectedTool() === 'Cut' && selectableZoomLevel()) {
          self.cut(evt);
        }
      };

      this.deactivate = function() {
        eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
        eventListener.stopListening(eventbus, 'map:clicked', selectedLinearAsset.cancel);
        eventListener.stopListening(eventbus, 'map:clicked', clickHandler);
        eventListener.stopListening(eventbus, 'map:mouseMoved');
        remove();
      };

      this.activate = function() {
        eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
        eventListener.stopListening(eventbus, 'map:clicked', selectedLinearAsset.cancel);
        eventListener.listenTo(eventbus, 'map:clicked', clickHandler);
        eventListener.listenTo(eventbus, 'map:mouseMoved', function(event) {
          if (application.getSelectedTool() === 'Cut') {
            self.updateByPosition(event.coordinate);
          }
        });
      };

      var isWithinCutThreshold = function(linearAssetLink) {
        return linearAssetLink && linearAssetLink < CUT_THRESHOLD;
      };

      var findNearestLinearAssetLink = function(point) {
        var laneFeatures = _.reject(vectorSource.getFeatures(), function (feature) {
          return _.isUndefined(feature.values_.properties);
        });

        return _.chain(laneFeatures)
          .filter(function(feature) {
            return feature.getGeometry() instanceof ol.geom.LineString;
          })
          .reject(function(feature) {
            var properties = feature.getProperties();
            var laneNumber = _.head(_.find(properties.properties, function(property){
              return property.publicId == "lane_code";
            }).values).value;

            return !selectedLinearAsset.isOuterLane(laneNumber) || laneNumber.toString()[1] == "1" ||
              !_.isUndefined(properties.marker) || properties.selectedLinks.length > 1 || selectedLinearAsset.isAddByRoadAddress();
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
          var nearestLineAsset = closestLinearAssetLink.feature.getProperties();
          if (authorizationPolicy.formEditModeAccess(nearestLineAsset)) {
            if (isWithinCutThreshold(closestLinearAssetLink.distance)) {
              moveTo(closestLinearAssetLink.point[0], closestLinearAssetLink.point[1]);
            } else {
              remove();
            }
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

        if (_.isUndefined(nearest) || !isWithinCutThreshold(nearest.distance)) {
          return;
        }

        var nearestLinearAsset = nearest.feature.getProperties();
        if(authorizationPolicy.formEditModeAccess(nearestLinearAsset)) {
          var splitProperties = calculateSplitProperties(GeometryUtils.revertOffsetByLaneNumber(nearestLinearAsset), mousePoint);
          selectedLinearAsset.splitLinearAsset(_.head(_.find(nearestLinearAsset.properties, function(property){
            return property.publicId === "lane_code";
          }).values).value, splitProperties);

          remove();
        }
      };
    };

    this.uiState = { zoomLevel: 9 };

    var vectorSource = new ol.source.Vector();
    var vectorLayer = new ol.layer.Vector({
      source : vectorSource,
      style : function(feature) {
        return me.getLayerStyle(feature);
      }
    });

    this.getLayerStyle = function(feature)  {
      return style.browsingStyleProvider.getStyle(feature, {zoomLevel: me.uiState.zoomLevel});
    };

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
    var readOnlyLayer = new GroupedLinearAssetLayer(params, map);

    var linearAssetCutter = new LinearAssetCutter(me.eventListener, vectorLayer, collection);

    var selectableZoomLevel = function() {
      return me.uiState.zoomLevel >= zoomlevels.minZoomForAssets;
    };

    var onSelect = function(evt) {
      if(selectableZoomLevel()) {
        if(evt.selected.length !== 0) {
          var feature = evt.selected[0];
          var properties = feature.getProperties();
          verifyClickEvent(properties, evt);
        }else{
          if (selectedLinearAsset.exists()) {
            selectedLinearAsset.close();
            readOnlyLayer.showLayer();
            highLightReadOnlyLayer();
          }
        }
      }
    };

    var onMultipleSelect = function(evt) {
      if(selectableZoomLevel()){
        if(evt.selected.length !== 0) {
          selectedLinearAsset.addSelection(_.map(evt.selected, function(feature){ return feature.getProperties();}));
        }
        else{
          if (selectedLinearAsset.exists()) {
            selectedLinearAsset.removeSelection(_.map(evt.deselected, function(feature){ return feature.getProperties();}));
          }
        }
      }
    };

    var verifyClickEvent = function(properties, evt){
      var singleLinkSelect = evt.mapBrowserEvent.type === 'dblclick';
      selectedLinearAsset.open(properties, singleLinkSelect);
      me.highlightMultipleLinearAssetFeatures();
    };

    this.highlightMultipleLinearAssetFeatures = function() {
      readOnlyLayer.hideLayer();
      unHighLightReadOnlyLayer();
    };

    var selectToolControl = new SelectToolControl(application, vectorLayer, map, isMultipleLinkSelectionAllowed, {
      style: function(feature){ return feature.setStyle(me.getLayerStyle(feature)); },
      onInteractionEnd: onInteractionEnd,
      onSelect: onSelect,
      onMultipleSelect: onMultipleSelect,
      onClose: onCloseForm,
      enableSelect: selectableZoomLevel
    });

    this.getSelectToolControl = function() {
      return selectToolControl;
    };

    this.getVectorSource = function() {
      return vectorSource;
    };

    var showDialog = function (linearAssets) {
      linearAssets = _.filter(linearAssets, function(asset){
        return asset && !(asset.geometry instanceof ol.geom.Point) && authorizationPolicy.formEditModeAccess(asset);
      });

      if(_.isEmpty(linearAssets))
        return;

      selectedLinearAsset.openMultiple(linearAssets);

      var features = style.renderFeatures(selectedLinearAsset.get());
      if(assetLabel)
        features = features.concat(assetLabel.renderFeaturesByLinearAssets(_.map(selectedLinearAsset.get(), offsetBySideCode), me.uiState.zoomLevel));
      selectToolControl.addSelectionFeatures(features);

      LinearAssetMassUpdateDialog.show({
        count: selectedLinearAsset.count(),
        onCancel: cancelSelection,
        onSave: function (value) {
          selectedLinearAsset.saveMultiple(value);
          selectToolControl.clear();
          selectedLinearAsset.closeMultiple();
          selectToolControl.deactivateDraw();},
        validator: selectedLinearAsset.validator,
        formElements: params.formElements,
        selectedLinearAsset: selectedLinearAsset,
        assetTypeConfiguration: params
      });
    };

    function onInteractionEnd(linearAssets) {
      if (selectedLinearAsset.isDirty()) {
        me.displayConfirmMessage();
      } else {
        if (linearAssets.length > 0 && selectableZoomLevel()) {
          selectedLinearAsset.close();
          showDialog(linearAssets);
          onCloseForm();
        }
      }
    }

    function cancelSelection() {
      if(isComplementaryChecked)
        showWithComplementary();
      else
        hideComplementary();
      selectToolControl.clear();
      selectedLinearAsset.closeMultiple();
    }

    var adjustStylesByZoomLevel = function(zoom) {
      me.uiState.zoomLevel = zoom;
    };

    var changeTool = function(eventListener, tool) {
      switch(tool) {
        case 'Cut':
          selectToolControl.deactivate();
          linearAssetCutter.activate();
          break;
        case 'Select':
          linearAssetCutter.deactivate();
          selectToolControl.deactivateDraw();
          selectToolControl.activate();
          break;
        default:
      }

      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.stopListening(eventbus, 'map:clicked', selectedLinearAsset.cancel);
      if (selectedLinearAsset.isDirty() && application.getSelectedTool() !== 'Cut') {
        eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
      }else if(application.getSelectedTool() !== 'Cut'){
        eventListener.listenTo(eventbus, 'map:clicked', selectedLinearAsset.cancel);
      }
    };

    function onCloseForm()  {
      eventbus.trigger('closeForm');
    }

    var bindEvents = function(eventListener) {
      var linearAssetChanged = _.partial(handleLinearAssetChanged, eventListener);
      var linearAssetCancelled = _.partial(handleLinearAssetCancelled, eventListener);
      var linearAssetUnSelected = _.partial(handleLinearAssetUnSelected, eventListener);
      var switchTool = _.partial(changeTool, eventListener);
      eventListener.listenTo(eventbus, singleElementEvents('unselect'), linearAssetUnSelected);
      eventListener.listenTo(eventbus, singleElementEvents('selected'), linearAssetSelected);
      eventListener.listenTo(eventbus, multiElementEvent('fetched'), redrawLinearAssets);
      eventListener.listenTo(eventbus, 'tool:changed', switchTool);
      eventListener.listenTo(eventbus, singleElementEvents('saved'), handleLinearAssetSaved);
      eventListener.listenTo(eventbus, multiElementEvent('massUpdateSucceeded'), handleLinearAssetSaved);
      eventListener.listenTo(eventbus, singleElementEvents('valueChanged', 'separated'), linearAssetChanged);
      eventListener.listenTo(eventbus, singleElementEvents('cancelled', 'saved'), linearAssetCancelled);
      eventListener.listenTo(eventbus, multiElementEvent('cancelled'), linearAssetCancelled);
      eventListener.listenTo(eventbus, singleElementEvents('selectByLinkId'), selectLinearAssetByLinkId);
      eventListener.listenTo(eventbus, multiElementEvent('massUpdateFailed'), cancelSelection);
      eventListener.listenTo(eventbus, multiElementEvent('valueChanged'), linearAssetChanged);
      eventListener.listenTo(eventbus, 'toggleWithRoadAddress', refreshSelectedView);
    };

    var startListeningExtraEvents = function(){
      extraEventListener.listenTo(eventbus, layerName+'-complementaryLinks:show', showWithComplementary);
      extraEventListener.listenTo(eventbus, layerName+'-complementaryLinks:hide', hideComplementary);
    };

    var stopListeningExtraEvents = function(){
      extraEventListener.stopListening(eventbus);
    };

    var selectLinearAssetByLinkId = function(linkId) {
      var feature = _.find(vectorLayer.features, function(feature) { return feature.attributes.linkId === linkId; });
      if (feature) {
        selectToolControl.addSelectionFeatures([feature]);
      }
    };

    var handleLinearAssetUnSelected = function (eventListener) {
      selectToolControl.clear();
      if (application.getSelectedTool() !== 'Cut'){
        changeTool(eventListener, application.getSelectedTool());
      }else{
        me.eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      }
    };

    var linearAssetSelected = function(){
      me.decorateSelection();
    };

    var handleLinearAssetSaved = function() {
      me.refreshView();
      applicationModel.setSelectedTool('Select');
    };

    var handleLinearAssetChanged = function(eventListener, selectedLinearAsset, laneNumber) {
      selectToolControl.deactivate();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.stopListening(eventbus, 'map:clicked', selectedLinearAsset.cancel);
      if (selectedLinearAsset.isDirty() && application.getSelectedTool() !== 'Cut') {
        eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
      }else if(!selectedLinearAsset.isDirty() && application.getSelectedTool() !== 'Cut'){
        eventListener.listenTo(eventbus, 'map:clicked', selectedLinearAsset.cancel);
      }
      me.decorateSelection(laneNumber);
    };

    var refreshReadOnlyLayer = function () {
      if(massLimitation)
        readOnlyLayer.refreshView();
    };

    this.layerStarted = function(eventListener) {
      bindEvents(eventListener);
    };

    this.refreshView = function() {
      vectorLayer.setVisible(true);
      adjustStylesByZoomLevel(zoomlevels.getViewZoom(map));
      if (isComplementaryChecked) {
        collection.fetchAssetsWithComplementary(map.getView().calculateExtent(map.getSize()), map.getView().getCenter(), Math.round(map.getView().getZoom())).then(function() {
          eventbus.trigger('layer:linearAsset');
        });
      } else {
        collection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getCenter(), Math.round(map.getView().getZoom())).then(function() {
          eventbus.trigger('layer:linearAsset');
        });
      }
      if(trafficSignReadOnlyLayer){
        trafficSignReadOnlyLayer.refreshView();
      }
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
      readOnlyLayer.removeLayerFeatures();
    };

    var handleLinearAssetCancelled = function(eventListener) {
      selectToolControl.clear();
      if(application.getSelectedTool() !== 'Cut'){
        selectToolControl.activate();
      }
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      redrawLinearAssets(collection.getAll());
    };

    this.drawIndicators = function(links) {
      var features = [];

      var markerContainer = function(link, position) {
        var imageSettings = {src: 'images/center-marker2.svg'};

        var textSettings = {
          text : link.marker,
          fill: new ol.style.Fill({
            color: '#ffffff'
          }),
          font : '12px sans-serif'
        };

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

      indicatorsForSplit();
      selectToolControl.addNewFeature(features);
    };

    var redrawLinearAssets = function(linearAssetChains) {
      vectorSource.clear();
      indicatorLayer.getSource().clear();
      var linearAssets = _.flatten(linearAssetChains);
      me.decorateSelection(selectedLinearAsset.exists() ? selectedLinearAsset.getCurrentLaneNumber() : undefined);
      me.drawLinearAssets(linearAssets, vectorSource);
    };

    this.drawLinearAssets = function(linearAssets) {
      var allButSelected = _.filter(linearAssets, function(asset){ return !_.some(selectedLinearAsset.get(), function(selectedAsset){
        return selectedAsset.linkId === asset.linkId && selectedAsset.sideCode == asset.sideCode &&
          selectedAsset.startMeasure === asset.startMeasure && selectedAsset.endMeasure === asset.endMeasure; }) ;
      });
      vectorSource.addFeatures(style.renderFeatures(allButSelected));
      readOnlyLayer.showLayer();
      highLightReadOnlyLayer();
      if(assetLabel) {
        var splitChangedAssets = _.partition(allButSelected, function(a){ return (a.sideCode !== 1 && _.has(a, 'value'));});
        vectorSource.addFeatures(assetLabel.renderFeaturesByLinearAssets(_.map( _.cloneDeep(_.omit(splitChangedAssets[0], 'geometry')), offsetBySideCode), me.uiState.zoomLevel));
        vectorSource.addFeatures(assetLabel.renderFeaturesByLinearAssets(_.map( _.omit(splitChangedAssets[1], 'geometry'), offsetBySideCode), me.uiState.zoomLevel));
      }
    };

    var offsetBySideCode = function (linearAsset) {
      return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, linearAsset);
    };

    var offsetByLaneNumber = function (linearAsset) {
      return GeometryUtils.offsetByLaneNumber(applicationModel.zoom.level, linearAsset, false);
    };

    var removeFeature = function(feature) {
      return vectorSource.removeFeature(feature);
    };

    var geometryAndValuesEqual = function(feature, comparison) {
      var toCompare = ["linkId", "sideCode", "startMeasure", "endMeasure"];
      _.each(toCompare, function(value){
        return feature[value] === comparison[value];
      });
    };

    this.decorateSelection = function (laneNumber) {
      function removeOldAssetFeatures() {
        var features = _.reject(vectorSource.getFeatures(), function (feature) {
          return _.isUndefined(feature.values_.properties);
        });
        _.forEach(features, function (feature) {
          vectorSource.removeFeature(feature);
        });
      }

      if (selectedLinearAsset.exists()) {
        var linearAssets = selectedLinearAsset.get();
        var selectedFeatures = style.renderFeatures(linearAssets, laneNumber);

        if (assetLabel) {
            var currentFeatures = _.filter(vectorSource.getFeatures(), function (layerFeature) {
              return _.some(selectedFeatures, function (selectedFeature) {
                return geometryAndValuesEqual(selectedFeature.values_, layerFeature.values_);
              });
            });

            _.each(currentFeatures, removeFeature);

              selectedFeatures = selectedFeatures.concat(assetLabel.renderFeaturesByLinearAssets(_.map(selectedFeatures, function (feature) {
                return feature.values_;
              }), me.uiState.zoomLevel));
        }

        removeOldAssetFeatures();
        vectorSource.addFeatures(selectedFeatures);
        selectToolControl.addSelectionFeatures(selectedFeatures);

        if (selectedLinearAsset.isSplit(laneNumber)) {
          me.drawIndicators(_.map(_.cloneDeep(_.filter(selectedLinearAsset.get(), function (lane){
            return _.find(lane.properties, function (property) {
              return property.publicId == "lane_code" && _.head(property.values).value == laneNumber;
            });
          })), offsetByLaneNumber));
        }
      }
    };

    var reset = function() {
      linearAssetCutter.deactivate();
    };

    this.showLayer = function(map) {
      startListeningExtraEvents();
      vectorLayer.setVisible(true);
      indicatorLayer.setVisible(true);
      roadAddressInfoPopup.start();
      me.show(map);
    };

    var showWithComplementary = function() {
      if(trafficSignReadOnlyLayer)
        trafficSignReadOnlyLayer.showTrafficSignsComplementary();
      isComplementaryChecked = true;
      readOnlyLayer.showWithComplementary();
      me.refreshView();
    };

    var hideComplementary = function() {
      if(trafficSignReadOnlyLayer)
        trafficSignReadOnlyLayer.hideTrafficSignsComplementary();
      selectToolControl.clear();
      selectedLinearAsset.close();
      isComplementaryChecked = false;
      readOnlyLayer.hideComplementary();
      roadAddressInfoPopup.stop();
      me.refreshView();
    };

    var hideReadOnlyLayer = function(){
      if(trafficSignReadOnlyLayer){
        trafficSignReadOnlyLayer.hide();
        trafficSignReadOnlyLayer.removeLayerFeatures();
      }
    };

    var unHighLightReadOnlyLayer = function(){
      if(trafficSignReadOnlyLayer){
        trafficSignReadOnlyLayer.unHighLightLayer();
      }
    };

    var highLightReadOnlyLayer = function(){
      if(trafficSignReadOnlyLayer){
        trafficSignReadOnlyLayer.highLightLayer();
      }
    };

    this.hideLayer = function() {
      reset();
      hideReadOnlyLayer();
      vectorLayer.setVisible(false);
      indicatorLayer.setVisible(false);
      readOnlyLayer.hideLayer();
      selectedLinearAsset.close();
      stopListeningExtraEvents();
      me.stop();
      me.hide();
    };

    var refreshSelectedView = function(){
      if(applicationModel.getSelectedLayer() == layerName)
        me.refreshView();
    };

    return {
      vectorLayer: vectorLayer,
      show: me.showLayer,
      hide: me.hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);