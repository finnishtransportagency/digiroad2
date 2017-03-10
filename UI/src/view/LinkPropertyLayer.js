(function(root) {

    //TODO check if it's possible to reuse the Roadlayer class to that but just in the end of the user story
    var RoadHistoryLayer = function(map, roadCollection, selectedLinkProperty){
        var vectorSource = new ol.source.Vector({ strategy: ol.loadingstrategy.bbox });
        var vectorLayer;
        //var selectControl;
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
            usingLayerSpecificStyleProvider(function() {
                vectorSource.addFeatures(features);
            });
        };

        var drawRoadLink = function(roadLink){
            var feature = createRoadLinkFeature(roadLink);
            usingLayerSpecificStyleProvider(function() {
                vectorSource.addFeatures(feature);
            });
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

        var redraw = function() {
            usingLayerSpecificStyleProvider(function() {
                //TODO Check if this is need after close all tasks
            });
        };

        var clear = function() {
            vectorSource.clear();
        };

        var usingLayerSpecificStyleProvider = function(action) {
            if (!_.isUndefined(layerStyleProviders[applicationModel.getSelectedLayer()])) {
                // vectorLayer.style = layerStyleProviders[applicationModel.getSelectedLayer()]();
            }
            action();
        };

        function stylesUndefined() {
            return _.isUndefined(layerStyleProviders[applicationModel.getSelectedLayer()]);
        }

        var disableColorsOnRoadLayer = function() {
            if (stylesUndefined()) {
                //TODO Check if this is need after close all tasks
            }
        };

        var toggleRoadType = function() {
            if (applicationModel.isRoadTypeShown()) {
                //enableColorsOnRoadLayer();
            } else {
                disableColorsOnRoadLayer();
            }
            usingLayerSpecificStyleProvider(function() {
                //TODO now the redraw will always happen after a source change
                //vectorLayer.redraw();
            });
        };

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
            if(currentLayerProvider.defaultStyleProvider)
                return currentLayerProvider.defaultStyleProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel});
            return currentLayerProvider.getStyle(feature, {zoomLevel: uiState.zoomLevel});
        };

        var hideLayer = function(){
            vectorLayer.setVisible(false);
        };

        vectorLayer = new ol.layer.Vector({
            source: vectorSource,
            style: vectorLayerStyle
        });
        //TODO change this to a place that can be reused
        var findLayerIndexByName = function(map, name){
            var layers = map.getLayers().getArray();
            for (var i = 0; i < layers.length; i++) {
                if (name === layers[i].get('name')) {
                    return i;
                }
            }
            return -1;
        };

        //TODO change this to a place that can be reused
        var addLayerBehind = function(map, layer, name){
            var idx = findLayerIndexByName(map, name);
            map.getLayers().setAt(idx - 1, layer);
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

        eventbus.on('road-type:selected', toggleRoadType, this);

        eventbus.on('map:moved', mapMovedHandler, this);

        eventbus.on('layer:selected', function(layer) {
            toggleRoadType();
        }, this);

        return {
            uiState: uiState,
            layer: vectorLayer,
            redraw: redraw,
            clear: clear,
            setLayerSpecificStyleProvider: setLayerSpecificStyleProvider,
            drawRoadLink: drawRoadLink,
            drawRoadLinks: drawRoadLinks,
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
    var isComplementaryActive = false;

    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;

    var historyLayer = new RoadHistoryLayer(map, roadCollection, selectedLinkProperty);
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
        var features = _.filter(roadLayer.layer.getSource().getFeatures(), function(_feature) {
          return feature.getProperties().linkId === _feature.getProperties().linkId;
        });
        selectToolControl.addSelectionFeatures(features);
        selectedLinkProperty.open(properties.linkId, feature.singleLinkSelect);
        currentRenderIntent = 'select';
      }else{
          currentRenderIntent = 'default';
          selectedLinkProperty.close();
      }
    };

    var unselectRoadLink = function() {
      currentRenderIntent = 'default';
      selectToolControl.clear();
      selectedLinkProperty.close();
    };

    var onDragEnd = function(links) {
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
          onDragEnd: onDragEnd,
          onSelect: selectRoadLink
          //backgroundOpacity: style.vectorOpacity
      });

    //selectToolControl.activate();

    this.activateSelection = function() {
     // selectToolControl.toggleDragBox();
      selectToolControl.activate();
    };

    this.deactivateSelection = function() {
     // selectToolControl.destroyDragBoxInteraction();
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
      roadLayer.layer.getSource().addFeatures(drawDashedLineFeaturesIfApplicable(roadLinks));
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
          linkType: roadLink.linkType
        });
        return feature;
      }));
    };

    var drawDashedLineFeatures = function(roadLinks, layer) {
      //TODO problems with dash line are not because
      var dashedFunctionalClasses = [2, 4, 6, 8];
      var dashedNotAllowInLinkStatus = [1, 3];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedFunctionalClasses, roadLink.functionalClass) && !_.contains(dashedNotAllowInLinkStatus, roadLink.constructionType);
      });
      return createDashedLineFeatures(dashedRoadLinks, 'functionalClass');
    };

    var drawDashedLineFeaturesForType = function(roadLinks, layer) {
      //TODO don't understand why this is needed here
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
      eventListener.listenTo(eventbus, 'roadLinkComplementary:show', showRoadLinksWithComplementary);
      eventListener.listenTo(eventbus, 'roadLinkComplementary:hide', hideRoadLinksWithComplementary);
    };

    var cancelSelection = function() {
      selectToolControl.clear();
      selectedLinkProperty.cancel();
      selectedLinkProperty.close();
    };

    var refreshViewAfterSaving = function() {
      unselectRoadLink();
      me.refreshView();
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
      //TODO check if is really needed
      //roadLayer.layer.removeFeatures(roadLayer.layer.getFeaturesByAttribute('type', 'overlay'));
      //historyLayer.removeFeatures();
    };

    var show = function(map) {
      me.show(map);
    };

    var hideLayer = function() {
      unselectRoadLink();
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
