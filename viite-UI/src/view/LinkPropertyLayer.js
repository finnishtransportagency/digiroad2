(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, selectedLinkProperty, roadCollection, linkPropertiesModel, applicationModel,styler) {
    var layerName = 'linkProperty';
    var cachedLinkPropertyMarker = null;
    var cachedMarker = null;
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var eventListener = _.extend({running: false}, eventbus);
    var zoom = 7;
    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;
    var indicatorVector = new ol.source.Vector({});
    var floatingMarkerVector = new ol.source.Vector({});
    var anomalousMarkerVector = new ol.source.Vector({});

    var indicatorLayer = new ol.layer.Vector({
      source: indicatorVector
    });

    var floatingMarkerLayer = new ol.layer.Vector({
      source: floatingMarkerVector
    });

    var anomalousMarkerLayer = new ol.layer.Vector({
      source: anomalousMarkerVector
    });

    map.addLayer(floatingMarkerLayer);
    map.addLayer(anomalousMarkerLayer);
    map.addLayer(indicatorLayer);
    floatingMarkerLayer.setVisible(true);
    anomalousMarkerLayer.setVisible(true);
    indicatorLayer.setVisible(true);


    /**
     * We declare the type of interaction we want the map to be able to respond.
     * A selected feature is moved to a new/temporary layer out of the default roadLayer.
     * @type {ol.interaction.Select}
     */
    var selectDoubleClick = new ol.interaction.Select({
      //Multi is the one en charge of defining if we select just the feature we clicked or all the overlaping
      //multi: true,
      //This will limit the interaction to the specific layer, in this case the layer where the roadAddressLinks are drawn
      layer: roadLayer.layer,
      //Limit this interaction to the doubleClick
      condition: ol.events.condition.doubleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature) {
        var featureStyle = styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
        var opacityIndex = featureStyle[0].stroke_.color_.lastIndexOf(", ");
        featureStyle[0].stroke_.color_ = featureStyle[0].stroke_.color_.substring(0,opacityIndex) + ", 1)";
        return featureStyle;
      }
    });

    //We add the defined interaction to the map.
    map.addInteraction(selectDoubleClick);

    /**
     * We now declare what kind of custom actions we want when the interaction happens.
     * Note that 'select' is triggered when a feature is either selected or deselected.
     * The event holds the selected features in the events.selected and the deselected in event.deselected.
     */
    selectDoubleClick.on('select',function(event) {
      //Since the selected features are moved to a new/temporary layer we just need to reduce the roadlayer's opacity levels.
      if (event.selected.length !== 0) {
        if (roadLayer.layer.getOpacity() === 1) {
          roadLayer.layer.setOpacity(0.2);
        }
        selectedLinkProperty.close();
        var selection = _.find(event.selected, function(selectionTarget){
          return !_.isUndefined(selectionTarget.roadLinkData);
        });
        selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, true);
      } else if (event.selected.length === 0 && event.deselected.length !== 0){
        selectedLinkProperty.close();
        roadLayer.layer.setOpacity(1);
        map.getView().setZoom(map.getView().getZoom()+1);
      }
    });

    var selectSingleClick = new ol.interaction.Select({
      //Multi is the one en charge of defining if we select just the feature we clicked or all the overlaping
      //multi: true,
      //This will limit the interaction to the specific layer, in this case the layer where the roadAddressLinks are drawn
      layer: roadLayer.layer,
      //Limit this interaction to the singleClick
      condition: ol.events.condition.singleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
        var featureStyle = styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
        var opacityIndex = featureStyle[0].stroke_.color_.lastIndexOf(", ");
        featureStyle[0].stroke_.color_ = featureStyle[0].stroke_.color_.substring(0,opacityIndex) + ", 1)";
        return featureStyle;
      }
    });

    map.addInteraction(selectSingleClick);

    selectSingleClick.on('select',function(event) {
      var source = roadLayer.layer.getSource();
      var extent = map.getView().calculateExtent(map.getSize());
      var visibleFeatures = source.getFeaturesInExtent(extent);
      //Since the selected features are moved to a new/temporary layer we just need to reduce the roadlayer's opacity levels.
      if(event.selected.length !== 0) {
        if (roadLayer.layer.getOpacity() === 1) {
          roadLayer.layer.setOpacity(0.2);
        }
        selectedLinkProperty.close();

        var selection = _.find(event.selected, function(selectionTarget){
          return !_.isUndefined(selectionTarget.roadLinkData);
        });
        selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, false, visibleFeatures);
      } else if (event.selected.length === 0 && event.deselected.length !== 0){
        selectedLinkProperty.close();
        roadLayer.layer.setOpacity(1);
      }
    });

    var addFeaturesToSelection = function (ol3Features) {
      _.each(ol3Features, function(feature){
        selectSingleClick.getFeatures().push(feature);
      });
    };

    eventbus.on('linkProperties:ol3Selected',function(ol3Features){
      selectSingleClick.getFeatures().clear();
      addFeaturesToSelection(ol3Features);
    });

    var selectMarkers = new ol.interaction.Select({
      toggleCondition: ol.events.condition.never,
      condition: ol.events.condition.click,
      layers: [floatingMarkerLayer, anomalousMarkerLayer]
    });

    map.addInteraction(selectMarkers);

    selectMarkers.on('select',function(event) {
      if(event.selected.length !== 0) {
        if (floatingMarkerLayer.getOpacity() === 1 && anomalousMarkerLayer.getOpacity() === 1) {
          floatingMarkerLayer.setOpacity(0.2);
          anomalousMarkerLayer.setOpacity(0.2);
        }
        selectedLinkProperty.close();
        var selection = _.find(event.selected, function(selectionTarget){
          return !_.isUndefined(selectionTarget.roadLinkData);
        });
        selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, true);
      } else if (event.selected.length === 0 && event.deselected.length !== 0){
        selectedLinkProperty.close();
        floatingMarkerLayer.setOpacity(1);
        anomalousMarkerLayer.setOpacity(1);
      }
    });

    var clearHighlights = function(){
      if(selectDoubleClick.getFeatures().getLength() !== 0){
        selectDoubleClick.getFeatures().clear();
      }
      if(selectSingleClick.getFeatures().getLength() !== 0){
        selectSingleClick.getFeatures().clear();
      }
    };

    var deactivateSelection = function() {
      map.removeInteraction(selectDoubleClick);
      map.removeInteraction(selectSingleClick);
      map.removeInteraction(selectMarkers);
    };

    var activateSelection = function () {
      map.addInteraction(selectDoubleClick);
      map.addInteraction(selectSingleClick);
      map.addInteraction(selectMarkers);
    };

    var unselectRoadLink = function() {
      selectedLinkProperty.close();
      clearHighlights();
      indicatorLayer.getSource().clear();
    };

    var highlightFeatures = function() {
      clearHighlights();
      var featuresToHighlight = [];
      _.each(roadLayer.layer.features, function(x) {
        var gapTransfering = x.data.gapTransfering;
        var canIHighlight = !_.isUndefined(x.attributes.linkId) ? selectedLinkProperty.isSelectedByLinkId(x.attributes.linkId) : selectedLinkProperty.isSelectedById(x.attributes.id);
        if(gapTransfering || canIHighlight){
          featuresToHighlight.push(x);
        }
      });
      if(featuresToHighlight.length !== 0)
        addFeaturesToSelection(featuresToHighlight);
    };

    var draw = function() {
      cachedLinkPropertyMarker = new LinkPropertyMarker(selectedLinkProperty);
      cachedMarker = new LinkPropertyMarker(selectedLinkProperty);
      deactivateSelection();
      var roadLinks = roadCollection.getAll();

      if(floatingMarkerLayer.getSource() !== null)
        floatingMarkerLayer.getSource().clear();
      if(anomalousMarkerLayer.getSource() !== null)
        anomalousMarkerLayer.getSource().clear();

      if(zoom > zoomlevels.minZoomForAssets) {
        var floatingRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.roadLinkType === -1;
        });

        var anomalousRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.anomaly === 1;
        });

        _.each(floatingRoadMarkers, function(floatlink) {
          var marker = cachedLinkPropertyMarker.createMarker(floatlink);
          floatingMarkerLayer.getSource().addFeature(marker);
        });

        _.each(anomalousRoadMarkers, function(anomalouslink) {
          var marker = cachedMarker.createMarker(anomalouslink);
          anomalousMarkerLayer.getSource().addFeature(marker);
        });
      }

      if (zoom > zoomlevels.minZoomForAssets) {
        var actualPoints =  me.drawCalibrationMarkers(roadLayer.source, roadLinks);
        _.each(actualPoints, function(actualPoint) {
          var calMarker = new CalibrationPoint(actualPoint.point);
          floatingMarkerLayer.getSource().addFeature(calMarker.getMarker(true));
        });
      }
      redrawSelected();
      activateSelection();
      eventbus.trigger('linkProperties:available');
    };

    this.refreshView = function() {
      roadCollection.fetch(map.getExtent(), 11);
      roadLayer.layer.changed();
    };

    this.isDirty = function() {
      return selectedLinkProperty.isDirty();
    };

    var vectorLayer = new ol.layer.Vector();
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(true);

    var getSelectedFeatures = function() {
      return _.filter(roadLayer.layer.features, function (feature) {
        return selectedLinkProperty.isSelectedByLinkId(feature.attributes.linkId);
      });
    };

    var reselectRoadLink = function() {
      me.activateSelection();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var features = getSelectedFeatures();
      var indicators = jQuery.extend(true, [], indicatorLayer.markers);
      //indicatorLayer.clearMarkers();
      indicatorLayer.clear();
      if(indicators.length !== 0){
        _.forEach(indicators, function(indicator){
          indicatorLayer.addMarker(createIndicatorFromBounds(indicator.bounds, indicator.div.innerText));
        });
      }
      if (!_.isEmpty(features)) {
        selectControl.select(_.first(features));
        highlightFeatures();
      }
      selectControl.onSelect = originalOnSelectHandler;
      if (selectedLinkProperty.isDirty()) {
        me.deactivateSelection();
      }
    };

    var handleLinkPropertyChanged = function(eventListener) {
      deactivateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      activateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      roadLayer.layer.setOpacity(1);
    };

    this.refreshView = function() {
      // Generalize the zoom levels as the resolutions and zoom levels differ between map tile sources
      roadCollection.fetch(map.getExtent(), 11);
      roadLayer.layer.changed();
    };

    var refreshViewAfterSaving = function() {
      unselectRoadLink();
      me.refreshView();
    };

    this.layerStarted = function(eventListener) {
      indicatorLayer.setZIndex(1000);
      var linkPropertyChangeHandler = _.partial(handleLinkPropertyChanged, eventListener);
      var linkPropertyEditConclusion = _.partial(concludeLinkPropertyEdit, eventListener);
      eventListener.listenTo(eventbus, 'linkProperties:changed', linkPropertyChangeHandler);
      eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', linkPropertyEditConclusion);
      eventListener.listenTo(eventbus, 'linkProperties:saved', refreshViewAfterSaving);
      eventListener.listenTo(eventbus, 'linkProperties:selected linkProperties:multiSelected', function(link) {
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return link.linkId !== 0 && feature.attributes.linkId === link.linkId;
        });
        if (feature) {
          _.each(selectControl.layer.selectedFeatures, function (selectedFeature){
            if(selectedFeature.attributes.linkId !== feature.attributes.linkId) {
              selectControl.select(feature);
            }
          });
        }
      });
      eventListener.listenTo(eventbus, 'linkProperties:reselect', reselectRoadLink);
      eventListener.listenTo(eventbus, 'roadLinks:fetched', draw);
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', draw);
      eventListener.listenTo(eventbus, 'linkProperties:updateFailed', cancelSelection);
      eventListener.listenTo(eventbus, 'adjacents:nextSelected', function(sources, adjacents, targets) {
        redrawNextSelectedTarget(targets, adjacents);
        drawIndicators(adjacents);
        selectedLinkProperty.addTargets(targets, adjacents);
      });
      eventListener.listenTo(eventbus, 'adjacents:added adjacents:aditionalSourceFound', function(sources,targets){
        drawIndicators(targets);
      });
    };

    var drawIndicators= function(links){
       indicatorLayer.getSource().clear();
       var indicators = me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
         var bounds = ol.extent.boundingExtent([middlePoint.x, middlePoint.y, middlePoint.x, middlePoint.y]);
         return createIndicatorFromBounds(bounds, link.marker);
       });
       _.forEach(indicators, function(indicator){
         indicatorLayer.getSource().addFeature(indicator);
       });
    };

    var createIndicatorFromBounds = function(bounds, marker) {
      var markerTemplate = _.template('<span class="marker" style="margin-left: -1em; margin-top: -1em; position: absolute;"><%= marker %></span>');
      var box = new OpenLayers.Marker.Box(bounds, "00000000");
      $(box.div).html(markerTemplate({'marker': marker}));
      $(box.div).css('overflow', 'visible');
      return box;
    };

    var redrawSelected = function() {
      var selectedRoadLinks = selectedLinkProperty.get();
      _.each(selectedRoadLinks,  function(selectedLink) {
        roadLayer.drawRoadLink(selectedLink);
      });
    };

    var redrawNextSelectedTarget= function(targets, adjacents) {
      _.find(roadLayer.layer.features, function(feature) {
        return targets !== 0 && feature.attributes.linkId === targets;
      }).data.gapTransfering = true;
      _.find(roadLayer.layer.features, function(feature) {
        return targets !== 0 && feature.attributes.linkId === targets;
      }).attributes.gapTransfering = true;
      _.find(roadLayer.layer.features, function(feature) {
        return targets !== 0 && feature.attributes.linkId === targets;
      }).data.anomaly = 0;
      _.find(roadLayer.layer.features, function(feature) {
        return targets !== 0 && feature.attributes.linkId === targets;
      }).attributes.anomaly = 0;
      reselectRoadLink();
      draw();
    };


    var show = function(map) {
      vectorLayer.setVisible(true);
      eventListener.listenTo(eventbus, 'map:clicked', cancelSelection);
    };

    var cancelSelection = function() {
      selectedLinkProperty.cancel();
      selectedLinkProperty.close();
      unselectRoadLink();
    };

    var hideLayer = function() {
      unselectRoadLink();
      me.stop();
      me.hide();
    };

    me.layerStarted(eventListener);

    return {
      show: show,
      hide: hideLayer,
      deactivateSelection: deactivateSelection,
      activateSelection: activateSelection,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
