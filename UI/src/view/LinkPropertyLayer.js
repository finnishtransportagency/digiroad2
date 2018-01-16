(function(root) {

  var RoadHistoryLayer = function(map, roadCollection){
      var vectorSource = new ol.source.Vector({ strategy: ol.loadingstrategy.bbox });
      var vectorLayer;
      var layerStyleProviders = {};
      var layerMinContentZoomLevels = {};
      var uiState = { zoomLevel: 9 };
      var isActive = false;

      var setZoomLevel = function(zoom){
          uiState.zoomLevel = zoom;
      };

      var drawRoadLinks = function(roadLinks, zoom) {
          setZoomLevel(zoom);
          vectorSource.clear();
          var features = _.map(roadLinks, function(roadLink) {
              return createRoadLinkFeature(roadLink);
          });
          vectorSource.addFeatures(features);
      };

      var drawRoadLink = function(roadLink){
          var feature = createRoadLinkFeature(roadLink);
          vectorSource.addFeatures(feature);
      };

      var createRoadLinkFeature = function(roadLink){
          var points = _.map(roadLink.points, function(point) {
              return [point.x, point.y];
          });
          return new ol.Feature(_.merge({}, roadLink, { geometry: new ol.geom.LineString(points)}));
      };

      var setLayerSpecificStyleProvider = function(layer, provider) {
          layerStyleProviders[layer] = provider;
      };

      var clear = function() {
          vectorSource.clear();
      };

      function stylesUndefined() {
          return _.isUndefined(layerStyleProviders[applicationModel.getSelectedLayer()]);
      }

      var minimumContentZoomLevel = function() {
          if (!_.isUndefined(layerMinContentZoomLevels[applicationModel.getSelectedLayer()])) {
              return layerMinContentZoomLevels[applicationModel.getSelectedLayer()];
          }
          return zoomlevels.minZoomForRoadLinks;
      };

      var mapMovedHandler = function(mapState) {
          if(isActive){
              if (mapState.zoom < minimumContentZoomLevel()) {
                  vectorSource.clear();
                  roadCollection.resetHistory();
              }
              handleRoadsVisibility();
          }
      };

      var handleRoadsVisibility = function() {
          if (_.isObject(vectorLayer)) {
              vectorLayer.setVisible(map.getView().getZoom() >= minimumContentZoomLevel());
          }
      };

      var refreshView = function(){
          if(isActive)
              roadCollection.fetchHistory(map.getView().calculateExtent(map.getSize()));
      };

      var showLayer = function(){
          vectorLayer.setVisible(true);
      };

      var vectorLayerStyle = function(feature) {
          var currentLayerProvider = layerStyleProviders[applicationModel.getSelectedLayer()]();
          if(currentLayerProvider.default)
              return currentLayerProvider.default.getStyle(feature, {zoomLevel: uiState.zoomLevel});
          return currentLayerProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel});
      };

      var hideLayer = function(){
          vectorLayer.setVisible(false);
      };

      vectorLayer = new ol.layer.Vector({
          source: vectorSource,
          style: vectorLayerStyle
      });

      var findLayerIndexByName = function(map, name){
          var layers = map.getLayers().getArray();
          for (var i = 0; i < layers.length; i++) {
              if (name === layers[i].get('name')) {
                  return i;
              }
          }
          return -1;
      };

      var addLayerBehind = function(map, layer, name){
        var idx = findLayerIndexByName(map, name);
        map.getLayers().insertAt(idx, layer);
      };

      vectorLayer.set('name', 'historyDataLayer');
      addLayerBehind(map, vectorLayer, 'road');
      vectorLayer.setVisible(false);

      eventbus.on('roadLinkHistory:show', function(){
          isActive = true;
          roadCollection.fetchHistory(map.getView().calculateExtent(map.getSize()));
          showLayer();
      });

      eventbus.on('roadLinkHistory:hide', function(){
          isActive = false;
          hideLayer();
      });

      eventbus.on('map:moved', mapMovedHandler, this);

      return {
          uiState: uiState,
          layer: vectorLayer,
          clear: clear,
          setLayerSpecificStyleProvider: setLayerSpecificStyleProvider,
          drawRoadLink: drawRoadLink,
          drawRoadLinks: drawRoadLinks,
          show: showLayer,
          hide: hideLayer,
          refreshView: refreshView
      };
  };

  root.LinkPropertyLayer = function(map, roadLayer, selectedLinkProperty, roadCollection, linkPropertiesModel, applicationModel, roadAddressInfoPopup) {
    var layerName = 'linkProperty';
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var currentRenderIntent = 'default';
    var linkPropertyLayerStyles = LinkPropertyLayerStyles(roadLayer);
    var isComplementaryActive = false;
    var extraEventListener = _.extend({running: false}, eventbus);

    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;

    var historyLayer = new RoadHistoryLayer(map, roadCollection);
    var linkPropertyHistoryLayerStyles = LinkPropertyLayerStyles(historyLayer);
    historyLayer.setLayerSpecificStyleProvider(layerName, function(){
      var styleProvider = linkPropertyHistoryLayerStyles.getDatasetSpecificStyle(linkPropertiesModel.getDataset(), 'history');
      if(styleProvider)
        return styleProvider[currentRenderIntent];
      return undefined;
    });

    roadLayer.setLayerSpecificStyleProvider(layerName, function() {
      return linkPropertyLayerStyles.getDatasetSpecificStyle(linkPropertiesModel.getDataset(), currentRenderIntent);
    });

    var selectRoadLink = function(event) {
      if(event.selected.length !== 0) {
        var feature = event.selected[0];
        var properties = feature.getProperties();
        verifyClickEvent(properties, event);
        redrawSelected();
        currentRenderIntent = 'select';
      }else{
          currentRenderIntent = 'default';
          selectedLinkProperty.close();
      }
    };

    var verifyClickEvent = function(properties, event){
       var singleLinkSelect;
       if(event)
          singleLinkSelect = event.mapBrowserEvent.type === 'dblclick';
       selectedLinkProperty.open(properties.linkId, singleLinkSelect);
    };

    var unselectRoadLink = function() {
      currentRenderIntent = 'default';
      selectToolControl.clear();
      selectedLinkProperty.close();
    };

    var onInteractionEnd = function(links) {
        selectedLinkProperty.openMultiple(links);

        highlightFeatures();

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
                selectToolControl.clear();
            }
        });
    };

    var selectToolControl = new SelectToolControl(applicationModel, roadLayer.layer, map, {
      style: function(feature){
          var provider = linkPropertyLayerStyles.getDatasetSpecificStyle(linkPropertiesModel.getDataset(), currentRenderIntent);
          return provider.getStyle(feature, {zoomLevel: roadLayer.getZoomLevel()});
      },
      onInteractionEnd: onInteractionEnd,
      onSelect: selectRoadLink
    });

    this.activateSelection = function() {
      selectToolControl.activate();
    };

    this.deactivateSelection = function() {
      selectToolControl.deactivate();
    };

    var highlightFeatures = function() {
      selectToolControl.clear();
      var features = _.filter(roadLayer.layer.getSource().getFeatures(), function(feature) { return selectedLinkProperty.isSelected(feature.getProperties().linkId); });
      if(!_.isEmpty(features))
        selectToolControl.addSelectionFeatures(features);
    };

    var draw = function() {
      prepareRoadLinkDraw();
      var roadLinks = roadCollection.getAll();
      var roadLinkHistory =  roadCollection.getAllHistory();

      roadLayer.drawRoadLinks(roadLinks, map.getView().getZoom());
      //The sortBy is used to order the addition of the features. This is used in order to the dashed lines always be in the same position
      roadLayer.layer.getSource().addFeatures(drawDashedLineFeaturesIfApplicable(_.sortBy(roadLinks, function(rl) { return rl.linkId; })));
      me.drawOneWaySigns(roadLayer.layer, roadLinks);

      historyLayer.drawRoadLinks(roadLinkHistory, map.getView().getZoom());
      historyLayer.layer.getSource().addFeatures(drawDashedLineFeaturesIfApplicable(roadLinkHistory));
      me.drawOneWaySigns(historyLayer.layer, roadLinkHistory);

      redrawSelected();
      eventbus.trigger('linkProperties:available');
    };

    this.refreshView = function () {
      if (isComplementaryActive) {
        me.refreshViewWithComplementary();
      } else {
        roadCollection.fetch(map.getView().calculateExtent(map.getSize()));
        historyLayer.refreshView();
      }
    };

    this.refreshViewWithComplementary = function() {
      roadCollection.fetchWithComplementary( map.getView().calculateExtent(map.getSize()));
      historyLayer.refreshView();
    };

    this.isDirty = function() {
      return selectedLinkProperty.isDirty();
    };

    var createDashedLineFeatures = function(roadLinks, dashedLineFeature) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return [point.x, point.y];
        });
        var feature = new ol.Feature(new ol.geom.LineString(points));
        feature.setProperties({
          dashedLineFeature: roadLink[dashedLineFeature],
          linkId: roadLink.linkId,
          type: 'overlay',
          linkType: roadLink.linkType,
          roadNumber: roadLink.roadNumber,
          roadPartNumber: roadLink.roadPartNumber,
          track: roadLink.track,
          startAddrMValue: roadLink.startAddrMValue,
          endAddrMValue: roadLink.endAddrMValue
        });
        return feature;
      }));
    };

    var drawDashedLineFeatures = function(roadLinks) {
      var dashedFunctionalClasses = [2, 4, 6, 8];
      var dashedNotAllowInLinkStatus = [1, 3];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedFunctionalClasses, roadLink.functionalClass) && !_.contains(dashedNotAllowInLinkStatus, roadLink.constructionType);
      });
      return createDashedLineFeatures(dashedRoadLinks, 'functionalClass');
    };

    var drawDashedLineFeaturesForType = function(roadLinks) {
      var dashedLinkTypes = [2, 4, 6, 8, 12, 21];
      var dashedNotAllowInLinkStatus = [1, 3];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedLinkTypes, roadLink.linkType) && !_.contains(dashedNotAllowInLinkStatus, roadLink.constructionType);
      });
      return createDashedLineFeatures(dashedRoadLinks, 'linkType');
    };

    var prepareRoadLinkDraw = function() {
      me.deactivateSelection();
    };

    var drawDashedLineFeaturesIfApplicable = function(roadLinks) {
      if (linkPropertiesModel.getDataset() === 'functional-class') {
        return drawDashedLineFeatures(roadLinks);
      }
      if (linkPropertiesModel.getDataset() === 'link-type') {
        return drawDashedLineFeaturesForType(roadLinks);
      }
      return [];
    };

    this.layerStarted = function(eventListener) {
      var linkPropertyChangeHandler = _.partial(handleLinkPropertyChanged, eventListener);
      var linkPropertyEditConclusion = _.partial(concludeLinkPropertyEdit, eventListener);
      eventListener.listenTo(eventbus, 'linkProperties:changed', linkPropertyChangeHandler);
      eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', linkPropertyEditConclusion);
      eventListener.listenTo(eventbus, 'linkProperties:saved', refreshViewAfterSaving);
      eventListener.listenTo(eventbus, 'roadLinks:fetched', draw);
      eventListener.listenTo(eventbus, 'roadLinks:historyFetched', draw);
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', draw);
      eventListener.listenTo(eventbus, 'linkProperties:updateFailed', cancelSelection);
      eventListener.listenTo(eventbus, 'linkProperties:selected linkProperties:multiSelected', function(link) {
        verifyClickEvent(link);
        redrawSelected();
        currentRenderIntent = 'select';
      });
      eventListener.listenTo(eventbus, 'toggleWithRoadAddress', function(){
        if(applicationModel.getSelectedLayer() == layerName)
          me.refreshView();
      });
    };

    var startListeningExtraEvents = function(){
      extraEventListener.listenTo(eventbus, 'roadLinkComplementary:show', showRoadLinksWithComplementary);
      extraEventListener.listenTo(eventbus, 'roadLinkComplementary:hide', hideRoadLinksWithComplementary);
    };

    var stopListeningExtraEvents = function(){
      extraEventListener.stopListening(eventbus);
    };

    var cancelSelection = function() {
      selectToolControl.clear();
      selectedLinkProperty.cancel();
      selectedLinkProperty.close();
    };

    var refreshViewAfterSaving = function() {
      me.refreshView();
      unselectRoadLink();
    };

    var showRoadLinksWithComplementary = function() {
      isComplementaryActive = true;
      me.refreshViewWithComplementary();
    };

    var hideRoadLinksWithComplementary = function() {
      selectedLinkProperty.close();
      unselectRoadLink();
      isComplementaryActive = false;
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

      if (selectedLinkProperty.isDirty())
        me.deactivateSelection();
      else
        me.activateSelection();

      var selectedRoadLinks = selectedLinkProperty.get();

      if(selectedRoadLinks.length === 0)
        return;

      var features = _.map(selectedRoadLinks,  function(selectedLink) {
          return roadLayer.createRoadLinkFeature(selectedLink);
      });

      features = features.concat(drawDashedLineFeaturesIfApplicable(selectedRoadLinks));

      selectToolControl.addSelectionFeatures(features);
    };

    this.removeLayerFeatures = function() {
    };

    var show = function(map) {
      roadAddressInfoPopup.start();
      startListeningExtraEvents();
      me.show(map);
    };

    var hideLayer = function() {
      unselectRoadLink();
      historyLayer.clear();
      roadAddressInfoPopup.stop();
      stopListeningExtraEvents();
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
