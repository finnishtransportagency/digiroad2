(function(root) {

  var RoadHistoryLayer = function(map, roadCollection, selectedLinkProperty){
    var vectorLayer;
    var selectControl;
    var layerStyleMapProvider;
    var uiState = { zoomLevel: 9 };
    var layerStyleMap = {};
    var isActive = false;

    var drawRoadLinks = function(roadLinks, zoom) {
      uiState.zoomLevel = zoom;
      vectorLayer.removeAllFeatures();
      var features = _.map(roadLinks, function(roadLink) {
        return createRoadLinkFeature(roadLink);
      });
      usingLayerSpecificStyleProvider(function() {
        vectorLayer.addFeatures(features);
      });
    };

    var drawRoadLink = function(roadLink) {
      var feature = createRoadLinkFeature(roadLink);
      usingLayerSpecificStyleProvider(function() {
        vectorLayer.addFeatures([feature]);
      });
    };

    var createRoadLinkFeature = function(roadLink){
      var points = _.map(roadLink.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), roadLink);
    };

    var setLayerSpecificStyleMapProvider = function(layer, provider) {
      layerStyleMapProvider = provider;
    };

    var activateLayerStyleMap = function() {
      vectorLayer.styleMap = layerStyleMap || new RoadStyles().roadStyles;
    };

    var redraw = function() {
      usingLayerSpecificStyleProvider(function() {
        vectorLayer.redraw();
      });
    };

    var clear = function() {
      vectorLayer.removeAllFeatures();
    };

    var usingLayerSpecificStyleProvider = function(action) {
      if (!_.isUndefined(layerStyleMapProvider)) {
          layerStyleMap = layerStyleMapProvider();
      }
      if(layerStyleMap){
        vectorLayer.styleMap = layerStyleMap;
        action();
      }
    };

    var getSelectedFeatures = function (){
      return _.filter(vectorLayer.features, function (feature) {
        return selectedLinkProperty.isSelected(feature.attributes.linkId);
      });
    };

    var removeSelectedFeatures = function() {
      vectorLayer.removeFeatures(getSelectedFeatures());
    };

    var removeFeatures = function(){
      vectorLayer.removeFeatures(vectorLayer.getFeaturesByAttribute('type', 'overlay'));
    };

    var getSelectControl = function(){
      return selectControl;
    };

    var highlightFeatures = function() {
      _.each(vectorLayer.features, function(x) {
        if (selectedLinkProperty.isSelected(x.attributes.linkId)) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
    };

    var reselectRoadLink = function() {
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var features = getSelectedFeatures();
      if (!_.isEmpty(features)) {
        selectControl.select(_.first(features));
        highlightFeatures();
      }
      selectControl.onSelect = originalOnSelectHandler;
    };

    var selectRoadLink = function(roadLink) {
      var feature = _.find(vectorLayer.features, function(feature) {
        if (roadLink.linkId) return feature.attributes.linkId === roadLink.linkId;
        else return feature.attributes.roadLinkId === roadLink.roadLinkId;
      });
      selectControl.unselectAll();
      selectControl.select(feature);
    };

    var selectRoadLinkByLinkId = function(linkId){
      var feature = _.find(vectorLayer.features, function(feature) {
        return feature.attributes.linkId === linkId;
      });
      if (feature) {
        selectControl.select(feature);
      }
    };

    var selectFeatureRoadLink = function(feature) {
      selectedLinkProperty.open(feature.attributes.linkId, feature.singleLinkSelect);
      vectorLayer.redraw();
      highlightFeatures();
    };

    var unselectFeatureRoadLink = function() {
      selectedLinkProperty.close();
      vectorLayer.redraw();
      highlightFeatures();
    };

    selectControl = new OpenLayers.Control.SelectFeature(vectorLayer, {
      onSelect: selectFeatureRoadLink,
      onUnselect: unselectFeatureRoadLink
    });

    function stylesUndefined() {
      return _.isUndefined(layerStyleMap);
    }

    var enableColorsOnRoadLayer = function() {
      if (stylesUndefined()) {
        var administrativeClassStyleLookup = {
          Private: { strokeColor: '#0011bb' },
          Municipality: { strokeColor: '#11bb00' },
          State: { strokeColor: '#ff0000' },
          Unknown: { strokeColor: '#888' }
        };
        vectorLayer.styleMap.addUniqueValueRules('default', 'administrativeClass', administrativeClassStyleLookup);
      }
    };

    var disableColorsOnRoadLayer = function() {
      if (stylesUndefined()) {
        vectorLayer.styleMap.styles.default.rules = [];
      }
    };

    var toggleRoadType = function() {
      if (applicationModel.isRoadTypeShown()) {
        enableColorsOnRoadLayer();
      } else {
        disableColorsOnRoadLayer();
      }
      usingLayerSpecificStyleProvider(function() { vectorLayer.redraw(); });
    };

    var minimumContentZoomLevel = function() {
      return zoomlevels.minZoomForRoadLinks;
    };

    var mapMovedHandler = function(mapState) {
      if (mapState.zoom < minimumContentZoomLevel()) {
        vectorLayer.removeAllFeatures();
        roadCollection.resetHistory();
      }
      handleRoadsVisibility();
    };

    var handleRoadsVisibility = function() {
      if (_.isObject(vectorLayer)) {
        vectorLayer.setVisibility(map.getZoom() >= minimumContentZoomLevel());
      }
    };

    var refreshView = function(){
      if(isActive)
        roadCollection.fetchHistory(map.getExtent());
    };

    var showLayer = function(){
      vectorLayer.setVisibility(true);
    };

    var hideLayer = function(){
      vectorLayer.setVisibility(false);
    };

    vectorLayer = new OpenLayers.Layer.Vector('historyDataLayer', {transparent: "true"}, {isBaseLayer: false});
    vectorLayer.setVisibility(true);
    map.addLayer(vectorLayer);
    var roadLinksLayerIndex = map.layers.indexOf(_.find(map.layers, {name: 'road'} ));
    map.setLayerIndex(vectorLayer, roadLinksLayerIndex - 1);
    vectorLayer.setVisibility(false);
    map.addControl(selectControl);

    eventbus.on('roadLinkHistory:show', function(){
      isActive = true;
      roadCollection.fetchHistory(map.getExtent());
      showLayer();
    });

    eventbus.on('roadLinkHistory:hide', function(){
      isActive = false;
      hideLayer();
    });

    eventbus.on('road-type:selected', toggleRoadType, this);

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('layer:selected', function(layer) {
      activateLayerStyleMap(layer);
      toggleRoadType();
    }, this);

    return {
      uiState: uiState,
      layer: vectorLayer,
      redraw: redraw,
      clear: clear,
      selectRoadLink: selectRoadLink,
      setLayerSpecificStyleMapProvider: setLayerSpecificStyleMapProvider,
      drawRoadLink: drawRoadLink,
      drawRoadLinks: drawRoadLinks,
      removeSelectedFeatures: removeSelectedFeatures,
      removeFeatures: removeFeatures,
      getSelectControl: getSelectControl,
      reselectRoadLink: reselectRoadLink,
      selectFeatureRoadLink: selectFeatureRoadLink,
      unselectFeatureRoadLink: unselectFeatureRoadLink,
      selectRoadLinkByLinkId: selectRoadLinkByLinkId,
      show: showLayer,
      hide: hideLayer,
      refreshView: refreshView
    };
  };

  root.LinkPropertyLayer = function(map, roadLayer, selectedLinkProperty, roadCollection, linkPropertiesModel, applicationModel) {
    var layerName = 'linkProperty';
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var currentRenderIntent = 'default';
    var linkPropertyLayerStyles = LinkPropertyLayerStyles(roadLayer);

    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;

    var historyLayer = new RoadHistoryLayer(map, roadCollection, selectedLinkProperty);
    var linkPropertyHistoryLayerStyles = LinkPropertyLayerStyles(historyLayer);
    historyLayer.setLayerSpecificStyleMapProvider(layerName, function(){
      var styleProvider = linkPropertyHistoryLayerStyles.getDatasetSpecificStyleMap(linkPropertiesModel.getDataset(), 'history');
      if(styleProvider)
        return styleProvider[currentRenderIntent];
      return undefined;
    });

    roadLayer.setLayerSpecificStyleMapProvider(layerName, function() {
      return linkPropertyLayerStyles.getDatasetSpecificStyleMap(linkPropertiesModel.getDataset(), currentRenderIntent);
    });

    var selectRoadLink = function(feature) {
      selectedLinkProperty.open(feature.attributes.linkId, feature.singleLinkSelect);
      currentRenderIntent = 'select';
      roadLayer.redraw();
      highlightFeatures();
    };

    var unselectRoadLink = function() {
      currentRenderIntent = 'default';
      selectedLinkProperty.close();
      roadLayer.redraw();
      highlightFeatures();
    };

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: selectRoadLink,
      onUnselect: unselectRoadLink
    });
    map.addControl(selectControl);
    var doubleClickSelectControl = new DoubleClickSelectControl(selectControl, map);
    this.selectControl = selectControl;

    var massUpdateHandler = new LinearAssetMassUpdate(map, roadLayer.layer, selectedLinkProperty, function(links) {
      selectedLinkProperty.openMultiple(links);

      LinkPropertyMassUpdateDialog.show({
        linkCount: selectedLinkProperty.count(),
        onCancel: cancelSelection,
        onSave: function(functionalClass, linkType) {
          if (functionalClass) {
            selectedLinkProperty.setFunctionalClass(functionalClass);
          }

          if (linkType) {
            selectedLinkProperty.setLinkType(linkType);
          }

          selectedLinkProperty.save();
        }
      });
    });

    this.activateSelection = function() {
      updateMassUpdateHandlerState();
      doubleClickSelectControl.activate();
    };

    this.deactivateSelection = function() {
      updateMassUpdateHandlerState();
      doubleClickSelectControl.deactivate();
    };

    var updateMassUpdateHandlerState = function() {
      if (!applicationModel.isReadOnly() &&
          applicationModel.getSelectedTool() === 'Select' &&
          applicationModel.getSelectedLayer() === layerName) {
        massUpdateHandler.activate();
      } else {
        massUpdateHandler.deactivate();
      }
    };

    var highlightFeatures = function() {
      _.each(roadLayer.layer.features, function(x) {
        if (selectedLinkProperty.isSelected(x.attributes.linkId)) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
    };

    var draw = function() {
      prepareRoadLinkDraw();
      var roadLinks = roadCollection.getAll();
      var roadLinkHistory =  roadCollection.getAllHistory();

      roadLayer.drawRoadLinks(roadLinks, map.getZoom());
      drawDashedLineFeaturesIfApplicable(roadLinks, roadLayer.layer);
      me.drawOneWaySigns(roadLayer.layer, roadLinks);

      historyLayer.drawRoadLinks(roadLinkHistory, map.getZoom());
      drawDashedLineFeaturesIfApplicable(roadLinkHistory, historyLayer.layer);
      me.drawOneWaySigns(historyLayer.layer, roadLinkHistory);

      redrawSelected();
      eventbus.trigger('linkProperties:available');
    };

    this.refreshView = function() {
      roadCollection.fetch(map.getExtent());
      historyLayer.refreshView();
    };

    this.isDirty = function() {
      return selectedLinkProperty.isDirty();
    };

    var createDashedLineFeatures = function(roadLinks, dashedLineFeature) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = {
          dashedLineFeature: roadLink[dashedLineFeature],
          linkId: roadLink.linkId,
          type: 'overlay',
          linkType: roadLink.linkType
        };
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var drawDashedLineFeatures = function(roadLinks, layer) {
      var dashedFunctionalClasses = [2, 4, 6, 8];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedFunctionalClasses, roadLink.functionalClass);
      });
      layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'functionalClass'));
    };

    var drawDashedLineFeaturesForType = function(roadLinks, layer) {
      var dashedLinkTypes = [2, 4, 6, 8, 12, 21];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedLinkTypes, roadLink.linkType);
      });
      layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'linkType'));
    };

    var getSelectedFeatures = function() {
      return _.filter(roadLayer.layer.features, function (feature) {
        return selectedLinkProperty.isSelected(feature.attributes.linkId);
      });
    };

    var reselectRoadLink = function() {
      me.activateSelection();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var features = getSelectedFeatures();
      if (!_.isEmpty(features)) {
        currentRenderIntent = 'select';
        selectControl.select(_.first(features));
        highlightFeatures();
      }
      selectControl.onSelect = originalOnSelectHandler;
      //TODO If the history layer doesn't need to be selected we don't need that
      historyLayer.reselectRoadLink();
      if (selectedLinkProperty.isDirty()) {
        me.deactivateSelection();
      }
    };

    var prepareRoadLinkDraw = function() {
      me.deactivateSelection();
    };

    var drawDashedLineFeaturesIfApplicable = function(roadLinks, layer) {
      if (linkPropertiesModel.getDataset() === 'functional-class') {
        drawDashedLineFeatures(roadLinks, layer);
      } else if (linkPropertiesModel.getDataset() === 'link-type') {
        drawDashedLineFeaturesForType(roadLinks, layer);
      }
    };

    this.layerStarted = function(eventListener) {
      var linkPropertyChangeHandler = _.partial(handleLinkPropertyChanged, eventListener);
      var linkPropertyEditConclusion = _.partial(concludeLinkPropertyEdit, eventListener);
      eventListener.listenTo(eventbus, 'linkProperties:changed', linkPropertyChangeHandler);
      eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', linkPropertyEditConclusion);
      eventListener.listenTo(eventbus, 'linkProperties:saved', refreshViewAfterSaving);
      eventListener.listenTo(eventbus, 'linkProperties:selected linkProperties:multiSelected', function(link) {
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return feature.attributes.linkId === link.linkId;
        });
        if (feature) {
          selectControl.select(feature);
        }
        historyLayer.selectRoadLinkByLinkId(link.linkId);
      });
      eventListener.listenTo(eventbus, 'roadLinks:fetched', draw);
      eventListener.listenTo(eventbus, 'roadLinks:historyFetched', draw);
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', draw);
      eventListener.listenTo(eventbus, 'application:readOnly', updateMassUpdateHandlerState);
      eventListener.listenTo(eventbus, 'linkProperties:updateFailed', cancelSelection);
    };

    var cancelSelection = function() {
      selectedLinkProperty.cancel();
      selectedLinkProperty.close();
      unselectRoadLink();
      historyLayer.unselectFeatureRoadLink();
    };

    var refreshViewAfterSaving = function() {
      unselectRoadLink();
      historyLayer.unselectFeatureRoadLink();
      me.refreshView();
    };

    var handleLinkPropertyChanged = function(eventListener) {
      redrawSelected();
      me.deactivateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      me.activateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      redrawSelected();
    };

    var redrawSelected = function() {
      roadLayer.layer.removeFeatures(getSelectedFeatures());
      historyLayer.removeSelectedFeatures();

      var selectedRoadLinks = selectedLinkProperty.get();
      _.each(selectedRoadLinks,  function(selectedLink) {
        roadLayer.drawRoadLink(selectedLink);
        historyLayer.drawRoadLink(selectedLink);
      });
      drawDashedLineFeaturesIfApplicable(selectedRoadLinks, roadLayer.layer);
      drawDashedLineFeaturesIfApplicable(selectedRoadLinks, historyLayer.layer);
      me.drawOneWaySigns(roadLayer.layer, selectedRoadLinks);
      me.drawOneWaySigns(historyLayer.layer, selectedRoadLinks);
      reselectRoadLink();
    };

    this.removeLayerFeatures = function() {
      roadLayer.layer.removeFeatures(roadLayer.layer.getFeaturesByAttribute('type', 'overlay'));
      historyLayer.removeFeatures();
    };

    var show = function(map) {
      me.show(map);
    };

    var hideLayer = function() {
      unselectRoadLink();
      historyLayer.unselectFeatureRoadLink();
      historyLayer.clear();
      me.stop();
      me.hide();
    };

    return {
      show: show,
      hide: hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
