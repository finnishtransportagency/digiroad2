(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, selectedLinkProperty, roadCollection, linkPropertiesModel, applicationModel,styler) {
    var layerName = 'linkProperty';
    var cachedLinkPropertyMarker = null;
    var cachedMarker = null;
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var eventListener = _.extend({running: false}, eventbus);
    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;
    var indicatorVector = new ol.source.Vector({});
    var floatingMarkerVector = new ol.source.Vector({});
    var anomalousMarkerVector = new ol.source.Vector({});
    var calibrationPointVector = new ol.source.Vector({});
    var greenRoadLayerVector = new ol.source.Vector({});
    var pickRoadsLayerVector = new ol.source.Vector({});
    var simulationVector = new ol.source.Vector({});
    var activeLayer = false;

    var indicatorLayer = new ol.layer.Vector({
      source: indicatorVector
    });
    indicatorLayer.set('name','indicatorLayer');

    var floatingMarkerLayer = new ol.layer.Vector({
      source: floatingMarkerVector
    });
    floatingMarkerLayer.set('name','floatingMarkerLayer');

    var anomalousMarkerLayer = new ol.layer.Vector({
      source: anomalousMarkerVector
    });
    anomalousMarkerLayer.set('name','anomalousMarkerLayer');

    var calibrationPointLayer = new ol.layer.Vector({
      source: calibrationPointVector
    });
    calibrationPointLayer.set('name','calibrationPointLayer');

    var greenRoadLayer = new ol.layer.Vector({
      source: greenRoadLayerVector
    });
    greenRoadLayer.set('name','greenRoadLayer');

    var greenRoads = function(Ol3Features, addToGreenLayer) {
      var features = [];

      var style = new ol.style.Style({
        fill: new ol.style.Fill({
          color: 'rgba(0, 255, 0, 0.75)'
        }),
        stroke: new ol.style.Stroke({
          color: 'rgba(0, 255, 0, 0.95)',
          width: 8
        })
      });

      var greenRoadStyle = function(feature) {
        feature.setStyle(style);
        features.push(feature);
      };

      var greenStyle = function() {
        _.each(Ol3Features, function(feature) {
          greenRoadStyle(feature);
        });
      };
      greenStyle();
      greenRoadLayer.setZIndex(10000);
      if(!addToGreenLayer){
        greenRoadLayer.getSource().addFeatures(features);
        selectSingleClick.getFeatures().clear();
        addFeaturesToSelection(features);
      }
    };

    var pickRoadsLayer = new ol.layer.Vector({
      source: pickRoadsLayerVector,
      style: function(feature) {
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      }
    });
    pickRoadsLayer.set('name','pickRoadsLayer');

    var simulatedRoadsLayer = new ol.layer.Vector({
      source: simulationVector,
      style: function(feature) {
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      }
    });
    simulatedRoadsLayer.set('name','simulatedRoadsLayer');

    map.addLayer(floatingMarkerLayer);
    map.addLayer(anomalousMarkerLayer);
    map.addLayer(calibrationPointLayer);
    map.addLayer(indicatorLayer);
    map.addLayer(greenRoadLayer);
    map.addLayer(pickRoadsLayer);
    map.addLayer(simulatedRoadsLayer);
    floatingMarkerLayer.setVisible(true);
    anomalousMarkerLayer.setVisible(true);
    calibrationPointLayer.setVisible(true);
    indicatorLayer.setVisible(true);
    greenRoadLayer.setVisible(true);
    pickRoadsLayer.setVisible(true);
    simulatedRoadsLayer.setVisible(true);

    var isAnomalousById = function(featureId){
      var anomalousMarkers = anomalousMarkerLayer.getSource().getFeatures();
      var isAnomalous = _.isUndefined(_.find(anomalousMarkers, function(am){
        return am.id === featureId;
      }));
      var roadLayerFeatures = roadLayer.layer.getSource().getFeatures();
      var isAnomalousRoadLayerFeature = _.find(roadLayerFeatures,function(rlf){
        return rlf.id === featureId;
      });
      var isAnomalousRoadLayer = _.isUndefined(isAnomalousRoadLayerFeature) ? false : isAnomalousRoadLayerFeature.roadLinkData.anomaly === 1;
      return isAnomalous || isAnomalousRoadLayer;
    };

    var isFloatingById = function(featureId){
      var floatingMarkers = floatingMarkerLayer.getSource().getFeatures();
      return !_.isUndefined(_.find(floatingMarkers, function(fm){
        return fm.id === featureId;
      }));
    };

    var setGeneralOpacity = function (opacity){
      roadLayer.layer.setOpacity(opacity);
      floatingMarkerLayer.setOpacity(opacity);
      anomalousMarkerLayer.setOpacity(opacity);
    };

    /**
     * We declare the type of interaction we want the map to be able to respond.
     * A selected feature is moved to a new/temporary layer out of the default roadLayer.
     * This interaction is restricted to a double click.
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
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
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
      var visibleFeatures = getVisibleFeatures(true, true, true);
      if(selectSingleClick.getFeatures().getLength() !== 0){
        selectSingleClick.getFeatures().clear();
      }

      //Since the selected features are moved to a new/temporary layer we just need to reduce the roadlayer's opacity levels.
      if (event.selected.length !== 0) {
        if (roadLayer.layer.getOpacity() === 1) {
          setGeneralOpacity(0.2);
        }
        var selection = _.find(event.selected, function(selectionTarget){
          return !_.isUndefined(selectionTarget.roadLinkData);
        });
        if (selection.roadLinkData.roadLinkType === -1 &&
          ('all' === applicationModel.getSelectionType() || 'floating' === applicationModel.getSelectionType()) &&
          !applicationModel.isReadOnly()) {
          selectedLinkProperty.openFloating(selection.roadLinkData.linkId, selection.roadLinkData.id, visibleFeatures);
          floatingMarkerLayer.setOpacity(1);
        } else {
          selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, true, visibleFeatures);
        }
      } else if (event.selected.length === 0 && event.deselected.length !== 0){
        selectedLinkProperty.close();
        setGeneralOpacity(1);
      }
    });
    selectDoubleClick.set('name','selectDoubleClickInteractionLPL');


    var zoomDoubleClickListener = function(event) {
      if (activeLayer)
        _.defer(function(){
          if(selectDoubleClick.getFeatures().getLength() < 1 && map.getView().getZoom() <= 13){
            map.getView().setZoom(map.getView().getZoom()+1);
          }
        });
    };
    //This will control the double click zoom when there is no selection that activates
    map.on('dblclick', zoomDoubleClickListener);

    /**
     * We declare the type of interaction we want the map to be able to respond.
     * A selected feature is moved to a new/temporary layer out of the default roadLayer.
     * This interaction is restricted to a single click (there is a 250 ms enforced
     * delay between single clicks in order to diferentiate from double click).
     * @type {ol.interaction.Select}
     */
    var selectSingleClick = new ol.interaction.Select({
      //Multi is the one en charge of defining if we select just the feature we clicked or all the overlaping
      //multi: true,
      //This will limit the interaction to the specific layer, in this case the layer where the roadAddressLinks are drawn
      layer: [roadLayer.layer, floatingMarkerLayer, anomalousMarkerLayer, greenRoadLayer, pickRoadsLayer],
      //Limit this interaction to the singleClick
      condition: ol.events.condition.singleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom(), true);
      }
    });
    selectSingleClick.set('name','selectSingleClickInteractionLPL');

    //We add the defined interaction to the map.
    map.addInteraction(selectSingleClick);

    /**
     * We now declare what kind of custom actions we want when the interaction happens.
     * Note that 'select' is triggered when a feature is either selected or deselected.
     * The event holds the selected features in the events.selected and the deselected in event.deselected.
     *
     * In this particular case we are fetching every roadLinkAddress and anomaly marker in view and
     * sending them to the selectedLinkProperty.open for further processing.
     */
    selectSingleClick.on('select',function(event) {
      var visibleFeatures = getVisibleFeatures(true, true, true, true, true);
      if (selectDoubleClick.getFeatures().getLength() !== 0) {
        selectDoubleClick.getFeatures().clear();
      }
      var selection = _.find(event.selected, function (selectionTarget) {
        return !_.isUndefined(selectionTarget.roadLinkData);
      });
      //Since the selected features are moved to a new/temporary layer we just need to reduce the roadlayer's opacity levels.
      if (!_.isUndefined(selection)) {
        if (event.selected.length !== 0) {
          if (roadLayer.layer.getOpacity() === 1) {
            setGeneralOpacity(0.2);
          }
          if (selection.roadLinkData.roadLinkType === -1 &&
            ('all' === applicationModel.getSelectionType() || 'floating' === applicationModel.getSelectionType()) &&
            !applicationModel.isReadOnly()) {
            selectedLinkProperty.close();
            selectedLinkProperty.openFloating(selection.roadLinkData.linkId, selection.roadLinkData.id, visibleFeatures);
            floatingMarkerLayer.setOpacity(1);
            anomalousMarkerLayer.setOpacity(1);
          } else if(selection.roadLinkData.roadLinkType !== -1 && 'floating' === applicationModel.getSelectionType() &&
            !applicationModel.isReadOnly() && event.deselected.length !== 0) {
            var floatings = event.deselected;
            var nonFloatings = event.selected;
            removeFeaturesFromSelection(nonFloatings);
            addFeaturesToSelection(floatings);
          }else if('unknown' === applicationModel.getSelectionType() && !applicationModel.isReadOnly()) {
            if (selection.roadLinkData.anomaly === 1 && selection.roadLinkData.roadLinkType !== -1) {
              selectedLinkProperty.openUnknown(selection.roadLinkData.linkId, selection.roadLinkData.id, visibleFeatures);
            } else if(event.selected.length !== 0){
              var deselecting = event.deselected;
              var selecting = event.selected;
              removeFeaturesFromSelection(selecting);
              addFeaturesToSelection(deselecting);
            }
          }
          else {
            if (isAnomalousById(selection.id) || isFloatingById(selection.id)) {
              selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, false, visibleFeatures);
            } else {
              selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, true, visibleFeatures);
            }
          }
        }
      } else if (event.selected.length === 0 && event.deselected.length !== 0 && applicationModel.getSelectionType() !== 'unknown') {
        selectedLinkProperty.close();
        setGeneralOpacity(1);
      } else if (event.selected.length === 0 && event.deselected.length !== 0 && applicationModel.getSelectionType() === 'unknown'){
        return new ModalConfirm("Olet muokannut tietoja.Tallenna tai peru muutoksesi.");
      }

      if (!_.isUndefined(selection)) {
        if(applicationModel.getSelectionType() === 'unknown' && selection.roadLinkData.roadLinkType !== -1 && selection.roadLinkData.anomaly === 1){
          greenRoadLayer.setOpacity(1);
          var anomalousFeatures = _.uniq(_.filter(selectedLinkProperty.getFeaturesToKeep(), function (ft) {
              return ft.anomaly === 1;
            })
          );
          anomalousFeatures.forEach(function (fmf) {
            editFeatureDataForGreen(fmf.linkId);
          });
        }
      }
    });

    /**
     * Simple method that will add various open layers 3 features to a selection.
     * @param ol3Features
     */
    var addFeaturesToSelection = function (ol3Features) {
      var olUids = _.map(selectSingleClick.getFeatures().getArray(), function(feature){
        return feature.ol_uid;
      });
      _.each(ol3Features, function(feature){
        if(!_.contains(olUids,feature.ol_uid)){
          selectSingleClick.getFeatures().push(feature);
          olUids.push(feature.ol_uid); // prevent adding duplicate entries
        }
      });
    };

    /**
     * Simple method that will remove various open layers 3 features from a selection.
     * @param ol3Features
     */
    var removeFeaturesFromSelection = function (ol3Features) {
      var olUids = _.map(selectSingleClick.getFeatures().getArray(), function(feature){
        return feature.ol_uid;
      });
      _.each(ol3Features, function(feature){
        if(_.contains(olUids,feature.ol_uid)){
          selectSingleClick.getFeatures().remove(feature);
        }
      });

    };

    /**
     * Event triggred by the selectedLinkProperty.open() returning all the open layers 3 features
     * that need to be included in the selection.
     */
    eventbus.on('linkProperties:ol3Selected',function(ol3Features){
      selectSingleClick.getFeatures().clear();
      addFeaturesToSelection(ol3Features);
    });

    var getVisibleFeatures = function(withRoads, withAnomalyMarkers, withFloatingMarkers, withGreenRoads, withPickRoads){
      var extent = map.getView().calculateExtent(map.getSize());
      var visibleRoads = withRoads ? roadLayer.layer.getSource().getFeaturesInExtent(extent) : [];
      var visibleAnomalyMarkers =  withAnomalyMarkers ? anomalousMarkerLayer.getSource().getFeaturesInExtent(extent) : [];
      var visibleFloatingMarkers =  withFloatingMarkers ? floatingMarkerLayer.getSource().getFeaturesInExtent(extent) : [];
      var visibleGreenRoadLayer = withGreenRoads ? greenRoadLayer.getSource().getFeaturesInExtent(extent) : [];
      var visiblePickRoadsLayer = withGreenRoads ? pickRoadsLayer.getSource().getFeaturesInExtent(extent) : [];
      return visibleRoads.concat(visibleAnomalyMarkers).concat(visibleFloatingMarkers).concat(visibleGreenRoadLayer);
    };

    /**
     * This is remove all the features from all the selections.
     */
    var clearHighlights = function(){
      if(selectDoubleClick.getFeatures().getLength() !== 0){
        selectDoubleClick.getFeatures().clear();
      }
      if(selectSingleClick.getFeatures().getLength() !== 0){
        selectSingleClick.getFeatures().clear();
      }
    };

    var clearLayers = function(){
      floatingMarkerLayer.getSource().clear();
      anomalousMarkerLayer.getSource().clear();
      calibrationPointLayer.getSource().clear();
      indicatorLayer.getSource().clear();
      greenRoadLayer.getSource().clear();
      pickRoadsLayer.getSource().clear();
    };

    /**
     * This will remove all the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick
     */
    var removeSelectInteractions = function() {
      map.removeInteraction(selectDoubleClick);
      map.removeInteraction(selectSingleClick);
    };

    /**
     * This will add all the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick
     */
    var addSelectInteractions = function () {
      map.addInteraction(selectDoubleClick);
      map.addInteraction(selectSingleClick);
    };

    /**
     * This will deactivate the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick - only if demanded with the Both
     */
    var deactivateSelectInteractions = function(both) {
      selectDoubleClick.setActive(false);
      if(both){
        selectSingleClick.setActive(false);
      }
    };

    /**
     * This will activate the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick - only if demanded with the Both
     */
    var activateSelectInteractions = function(both) {
      selectDoubleClick.setActive(true);
      if(both){
        selectSingleClick.setActive(true);
      }
    };

    var unselectRoadLink = function() {
      indicatorLayer.getSource().clear();
      greenRoadLayer.getSource().clear();
      _.map(roadLayer.layer.getSource().getFeatures(),function (feature){
        if(feature.roadLinkData.gapTransfering) {
          feature.roadLinkData.gapTransfering = false;
          feature.roadLinkData.anomaly = feature.roadLinkData.prevAnomaly;
          var unknownRoadStyle = styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
          feature.setStyle(unknownRoadStyle);
        }
      });
    };

    var highlightFeatures = function() {
      clearHighlights();
      var featuresToHighlight = [];
      _.each(roadLayer.layer.features, function(feature) {
        var gapTransfering = x.data.gapTransfering;
        var canIHighlight = !_.isUndefined(feature.attributes.linkId) ? selectedLinkProperty.isSelectedByLinkId(feature.attributes.linkId) : selectedLinkProperty.isSelectedById(feature.attributes.id);
        if(gapTransfering || canIHighlight){
          featuresToHighlight.push(feature);
        }
      });
      if(featuresToHighlight.length !== 0)
        addFeaturesToSelection(featuresToHighlight);
    };

    var highlightFeatureByLinkId = function (linkId) {
      var highlightFeatures = _.filter(roadLayer.layer.getSource().getFeatures(), function(roadlink) {
        return roadlink.roadLinkData.linkId === linkId;
      });
      addFeaturesToSelection(highlightFeatures);
    };

    var unhighlightFeatureByLinkId = function (linkId) {
      _.each(roadLayer.layer.getSource().getFeatures(), function(feature) {
        if(feature.roadLinkData.linkId == linkId){
          feature.clear();
        }
      });
    };

    var unhighlightFeatures = function() {
      _.each(roadLayer.layer.features, function(feature) {
        feature.clear();
      });
    };

    var draw = function() {
      var marker;
      var middlefloating;
      cachedLinkPropertyMarker = new LinkPropertyMarker(selectedLinkProperty);
      cachedMarker = new LinkPropertyMarker(selectedLinkProperty);
      removeSelectInteractions();
      var roadLinks = roadCollection.getAll();
      var linkIdsToRemove = applicationModel.getCurrentAction() !== applicationModel.actionCalculated ? [] : selectedLinkProperty.linkIdsToExclude();

      if(floatingMarkerLayer.getSource() !== null)
        floatingMarkerLayer.getSource().clear();
      if(anomalousMarkerLayer.getSource() !== null)
        anomalousMarkerLayer.getSource().clear();

      if(map.getView().getZoom() >= zoomlevels.minZoomForAssets) {
        var floatingRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.roadLinkType === -1;
        });

        var anomalousRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.anomaly === 1;
        });

        var floatingGroups = _.groupBy(floatingRoadMarkers, function(value){
          return value.linkId;
        });

        var orderFloatGroup = _.sortBy(floatingGroups, 'startAddressM');
        _.each(orderFloatGroup, function(floatGroup) {
          floatGroup.sort(function(firstFloat, secondFloat){
            return firstFloat.startAddressM - secondFloat.startAddressM;
          });
          middlefloating = floatGroup[Math.floor(floatGroup.length / 2)];
          marker = cachedLinkPropertyMarker.createMarker(middlefloating);
          if(applicationModel.getCurrentAction() !== applicationModel.actionCalculated && !_.contains(linkIdsToRemove,marker.roadLinkData.linkId))
            floatingMarkerLayer.getSource().addFeature(marker);
        });

        _.each(anomalousRoadMarkers, function(anomalouslink) {
          var marker = cachedMarker.createMarker(anomalouslink);
          if(applicationModel.getCurrentAction() !== applicationModel.actionCalculated && !_.contains(linkIdsToRemove,marker.roadLinkData.linkId))
            anomalousMarkerLayer.getSource().addFeature(marker);
        });
        if(!applicationModel.isActiveButtons()) {
          var actualPoints = me.drawCalibrationMarkers(calibrationPointLayer.source, roadLinks);
          _.each(actualPoints, function (actualPoint) {
            var calMarker = new CalibrationPoint(actualPoint.point);
            calibrationPointLayer.getSource().addFeature(calMarker.getMarker(true));
          });
          calibrationPointLayer.setZIndex(22);
        }
      }
      addSelectInteractions();
      if(applicationModel.getCurrentAction() === -1){
        applicationModel.removeSpinner();
      }
    };

    this.refreshView = function() {
      //Generalize the zoom levels as the resolutions and zoom levels differ between map tile sources
      roadCollection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getZoom());
      roadLayer.layer.changed();
    };

    this.isDirty = function() {
      return selectedLinkProperty.isDirty();
    };

    var vectorLayer = new ol.layer.Vector();
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(true);
    vectorLayer.set('name','vectorLayer');

    var getSelectedFeatures = function() {
      return _.filter(roadLayer.layer.getSource().getFeatures(), function (feature) {
        return selectedLinkProperty.isSelectedByLinkId(feature.roadLinkData.linkId);
      });
    };

    var reselectRoadLink = function(targetFeature, adjacents) {
      var visibleFeatures = getVisibleFeatures(true, true, true, true, true);
      var indicators = adjacents;
      indicatorLayer.getSource().clear();
      if(indicators.length !== 0){
        drawIndicators(indicators);
      }
      if (applicationModel.getSelectionType() === 'unknown' && !applicationModel.isReadOnly() &&
        targetFeature.roadLinkData.anomaly === 1 && targetFeature.roadLinkData.roadLinkType !== -1  ){
        selectedLinkProperty.openUnknown(targetFeature.roadLinkData.linkId, targetFeature.roadLinkData.id, visibleFeatures);
      }

      if(applicationModel.getSelectionType() === 'unknown' && targetFeature.roadLinkData.roadLinkType !== -1 && targetFeature.roadLinkData.anomaly === 1){
        greenRoadLayer.setOpacity(1);
        var anomalousFeatures = _.uniq(_.filter(selectedLinkProperty.getFeaturesToKeep(), function (ft) {
            return ft.anomaly === 1;
          })
        );
        anomalousFeatures.forEach(function (fmf) {
          editFeatureDataForGreen(targetFeature.roadLinkData.linkId);
        });
      }
    };

    var handleLinkPropertyChanged = function(eventListener) {
      removeSelectInteractions();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      addSelectInteractions();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      setGeneralOpacity(1);
      if(selectDoubleClick.getFeatures().getLength() !== 0){
        selectDoubleClick.getFeatures().clear();
      }
    };

    this.layerStarted = function(eventListener) {
      indicatorLayer.setZIndex(1000);
      var linkPropertyChangeHandler = _.partial(handleLinkPropertyChanged, eventListener);
      var linkPropertyEditConclusion = _.partial(concludeLinkPropertyEdit, eventListener);
      eventListener.listenTo(eventbus, 'linkProperties:changed', linkPropertyChangeHandler);
      eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', linkPropertyEditConclusion);
      eventListener.listenTo(eventbus, 'linkProperties:saved', refreshViewAfterSaving);

      eventListener.listenTo(eventbus, 'linkProperties:selected linkProperties:multiSelected', function(link) {
        var features = [];
        _.each(roadLayer.layer.getSource().getFeatures(), function(feature){
          _.each(link, function (featureLink){
            if(featureLink.linkId !== 0 && feature.roadLinkData.linkId === featureLink.linkId){
              return features.push(feature);
            }
          });
        });
        if (features) {
          addFeaturesToSelection(features);
        }
        clearIndicators();
      });

      eventListener.listenTo(eventbus, 'linkProperties:reselect', reselectRoadLink);
      eventListener.listenTo(eventbus, 'roadLinks:drawAfterGapCanceling', function() {
        currentRenderIntent = 'default';
        _.map(roadLayer.layer.getSource().getFeatures(), function (feature){
          if(feature.roadLinkData.gapTransfering) {
            feature.roadLinkData.gapTransfering = false;
            feature.roadLinkData.anomaly = feature.roadLinkData.prevAnomaly;
          }
        });
        unhighlightFeatures();
        roadLayer.redraw();
        var current = selectedLinkProperty.get();
        _.forEach(current, function(road){
          var feature = _.find(roadLayer.layer.getSource().getFeatures(), function (feature) {
            return road.linkId !== 0 && feature.attributes.linkId === road.linkId;
          });
          if (feature) {
            _.each(selectControl.layer.selectedFeatures, function (selectedFeature) {
              if (selectedFeature.attributes.linkId !== feature.attributes.linkId) {
                selectControl.select(feature);
              }
            });
          }
        });
        indicatorLayer.getSource().clear();
      });

      eventListener.listenTo(eventbus, 'roadLinks:fetched', function(eventData){
        draw();
        _.defer(function(){
          var floatingsLinkIds = _.chain(selectedLinkProperty.getFeaturesToKeepFloatings()).map(function(feature){
            return feature.linkId;
          }).uniq().value();
          var visibleFeatures = getVisibleFeatures(true,false,true);
          var featuresToReSelect = _.filter(visibleFeatures, function(feature){
            return _.contains(floatingsLinkIds, feature.roadLinkData.linkId);
          });
          if(featuresToReSelect.length !== 0){
            addFeaturesToSelection(featuresToReSelect);
          }
        });
      });
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', draw);
      eventListener.listenTo(eventbus, 'linkProperties:updateFailed', cancelSelection);
      eventListener.listenTo(eventbus, 'adjacents:nextSelected', function(sources, adjacents, targets) {
        applicationModel.addSpinner();
        if(applicationModel.getCurrentAction()!==applicationModel.actionCalculated){
          drawIndicators(adjacents);
          selectedLinkProperty.addTargets(targets, adjacents);
        }
        redrawNextSelectedTarget(targets, adjacents);
      });
      eventListener.listenTo(eventbus, 'adjacents:added adjacents:aditionalSourceFound', function(sources,targets, aditionalLinkId){
        drawIndicators(targets);
        _.map(_.rest(selectedLinkProperty.getFeaturesToKeep()), function (roads){
          editFeatureDataForGreen(roads);
        });
      });

      eventListener.listenTo(eventbus, 'adjacents:floatingAdded', function(floatings){
        drawIndicators(floatings);
      });
      eventListener.listenTo(eventbus, 'adjacents:roadTransfer', function(newRoads,changedIds){

        var floatingsLinkIds = _.map(selectedLinkProperty.getFeaturesToKeepFloatings(), function (f){
          return f.linkId;
        });
        var roadLinks = _.reject(roadCollection.getAll(),function (rl){
          return _.contains(floatingsLinkIds, rl.linkId);
        });
        var afterTransferLinks=  _.filter(roadLinks, function(roadlink){
          return !_.contains(changedIds, roadlink.linkId.toString());
        });
        var simulatedOL3Features = [];
        _.map(newRoads, function(road){
          var points = _.map(road.points, function(point) {
            return [point.x, point.y];
          });
          var feature =  new ol.Feature({ geometry: new ol.geom.LineString(points)
          });
          feature.roadLinkData = road;
          simulatedOL3Features.push(feature);
          afterTransferLinks.push(road);
        });

        var actualPoints =  me.drawCalibrationMarkers(calibrationPointLayer.source, newRoads);
        _.each(actualPoints, function(actualPoint) {
          var calMarker = new CalibrationPoint(actualPoint.point);
          simulatedRoadsLayer.getSource().addFeature(calMarker.getMarker(true));
        });

        indicatorLayer.getSource().clear();
        roadCollection.setTmpRoadAddresses(afterTransferLinks);
        roadCollection.setChangedIds(changedIds);
        applicationModel.setCurrentAction(applicationModel.actionCalculated);
        selectedLinkProperty.cancelAfterDefloat(applicationModel.actionCalculated, changedIds);

        clearHighlights();
        greenRoadLayer.getSource().clear();
        setGeneralOpacity(0.2);

        _.each(simulatedOL3Features, function(elem) {
          roadLayer.layer.getSource().getFeatures().filter(function(item){
            if( item.roadLinkData.linkId === elem.roadLinkData.linkId)
              roadLayer.layer.getSource().removeFeature(item);
          });
        });
        simulatedRoadsLayer.getSource().addFeatures(simulatedOL3Features);
        roadLayer.layer.getSource().addFeatures(simulatedOL3Features);
      });

      eventListener.listenTo(eventbus, 'roadLink:editModeAdjacents', function() {
        if (applicationModel.isReadOnly() && !applicationModel.isActiveButtons()) {
          indicatorLayer.getSource().clear();
          var floatingsLinkIds = _.map(_.filter(selectedLinkProperty.getFeaturesToKeep(), function (feature) {
            return feature.roadLinkType == -1;
          }), function (floating) {
            return floating.linkId;
          });
          _.defer(function () {
            _.map(roadLayer.layer.features, function (feature) {
              if (_.contains(floatingsLinkIds, feature.attributes.linkId)) {
                selectControl.select(feature);
              }
            });
          });
        } else {
          var selectedFloatings = _.filter(selectedLinkProperty.get(), function (features) {
            return features.roadLinkType == -1;
          });
          _.each(selectedFloatings, function(sf){
            selectedLinkProperty.getFeaturesToKeep().push(sf);
          });
        }
      });

      eventListener.listenTo(eventbus, 'roadLinks:unSelectIndicators', function (originalFeature) {
        var visibleFeatures = getVisibleFeatures(true,true,true, true);
        clearIndicators();
        greenRoadLayerVector.clear();
        clearHighlights();
        if (applicationModel.getSelectionType() !== 'floating') {
          var features = [];
          var extractedLinkIds = _.map(originalFeature,function(of){
            return of.linkId;
          });
          _.each(roadLayer.layer.getSource().getFeatures(), function (feature) {
            if (!_.contains(extractedLinkIds, feature.roadLinkData.linkId) && feature.roadLinkData.roadLinkType === -1){
              features.push(feature);
            }
          });

          if (!_.isEmpty(features)) {
            currentRenderIntent = 'select';
            var featureToSelect = _.first(features);
            selectedLinkProperty.openFloating(featureToSelect.roadLinkData.linkId, featureToSelect.roadLinkData.id, visibleFeatures);
            highlightFeatures();
          }
        }
      });

      eventListener.listenTo(eventbus, 'linkProperties:clearIndicators', function(){
        clearIndicators();
      });

      eventListener.listenTo(eventbus, 'roadLinks:refreshView', function(){
        me.refreshView();
      });

      var clearIndicators = function () {
        indicatorLayer.getSource().clear();
      };

      eventListener.listenTo(eventListener, 'map:clearLayers', clearLayers);
    };

    var drawIndicators = function(links) {
      var features = [];

      var markerContainer = function(link, position) {
        var style = new ol.style.Style({
          image : new ol.style.Icon({
            src: 'images/center-marker2.svg'
          }),
          text : new ol.style.Text({
            text : link.marker,
            fill: new ol.style.Fill({
              color: '#ffffff'
            }),
            font : '12px sans-serif'
          })
        });
        var marker = new ol.Feature({
          geometry : new ol.geom.Point([position.x, position.y])
        });
        marker.setStyle(style);
        features.push(marker);
      };

      var indicators = function() {
        return me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
          markerContainer(link, middlePoint);
        });
      };
      indicators();
      indicatorLayer.getSource().addFeatures(features);
    };

    var cancelSelection = function() {
      if(!applicationModel.isActiveButtons()) {
        selectedLinkProperty.cancel();
        selectedLinkProperty.close();
        unselectRoadLink();
      }
    };

    var refreshViewAfterSaving = function() {
      selectedLinkProperty.setDirty(false);
      selectedLinkProperty.resetTargets();
      applicationModel.resetCurrentAction();
      applicationModel.setActiveButtons(false);
      $('#feature-attributes').empty();
      clearLayers();
      me.refreshView();
      activateSelectInteractions(true);
      applicationModel.toggleSelectionTypeAll();
      selectedLinkProperty.clearFeaturesToKeep();
      greenRoadLayer.getSource().clear();
      simulatedRoadsLayer.getSource().clear();
      eventbus.once('roadLinks:fetched', function(){
        applicationModel.removeSpinner();
      });
    };

    var redrawNextSelectedTarget= function(targets, adjacents) {
      _.find(roadLayer.layer.getSource().getFeatures(), function(feature) {
        return targets !== 0 && feature.roadLinkData.linkId === parseInt(targets);
      }).roadLinkData.gapTransfering = true;
      var targetFeature =_.find(roadLayer.layer.getSource().getFeatures(), function(feature) {
        return targets !== 0 && feature.roadLinkData.linkId === parseInt(targets);
      });
      reselectRoadLink(targetFeature, adjacents);
      draw();
      reHighlightGreen();
    };

    var reHighlightGreen = function() {
      var greenFeaturesLinkId = _.map(greenRoadLayer.getSource().getFeatures(), function(gf){
        return gf.roadLinkData.linkId;
      });

      if (greenFeaturesLinkId.length !== 0) {
        var features =[];
        _.each(roadLayer.layer.getSource().getFeatures(), function (feature) {
          if (_.contains(greenFeaturesLinkId, feature.roadLinkData.linkId)) {

            feature.roadLinkData.prevAnomaly = feature.roadLinkData.anomaly;
            feature.roadLinkData.gapTransfering = true;
            var greenRoadStyle = styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
            feature.setStyle(greenRoadStyle);
            features.push(feature);
          }
        });
        greenRoads(features,true);
        addFeaturesToSelection(features);
      }
    };

    var editFeatureDataForGreen = function (targets) {
      var features =[];
      if(targets !== 0){
        var targetFeature = _.find(greenRoadLayer.getSource().getFeatures(), function(greenfeature){
          return targets !== 0 && greenfeature.roadLinkData.linkId === parseInt(targets);
        });
        if(!targetFeature){
          _.map(roadLayer.layer.getSource().getFeatures(), function(feature){
            if(feature.roadLinkData.linkId == targets){
              feature.roadLinkData.prevAnomaly = feature.roadLinkData.anomaly;
              feature.roadLinkData.gapTransfering = true;
              var greenRoadStyle = styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
              feature.setStyle(greenRoadStyle);
              features.push(feature);
              roadCollection.addPreMovedRoadAddresses(feature.data);
              var pickAnomalousMarker = _.filter(pickRoadsLayer.getSource().getFeatures(), function(markersPick){
                return markersPick.roadLinkData.linkId === feature.roadLinkData.linkId;
              });
              _.each(pickAnomalousMarker, function(pickRoads){
                pickRoadsLayer.getSource().removeFeature(pickRoads);
              });
            }
          });
          addFeaturesToSelection(features);
          greenRoads(features);
        }
      }
      if(features.length === 0)
        return undefined;
      else return _.first(features);
    };

    eventbus.on('linkProperties:highlightSelectedProject', function(featureLinkId){
      setGeneralOpacity(0.2);
      var projectFeature = _.filter(roadLayer.layer.getSource().getFeatures(), function (ft) {
        return ft.roadLinkData.linkId === featureLinkId;
      });
      addFeaturesToSelection(projectFeature);
    });

    eventbus.on('linkProperties:deselectFeaturesSelected', function(){
      clearHighlights();
    });

    eventbus.on('linkProperties:highlightAnomalousByFloating', function(){
      highlightAnomalousFeaturesByFloating();
    });

    eventbus.on('linkProperties:highlightSelectedFloatingFeatures', function(){
      highlightSelectedFloatingFeatures();
    });

    var highlightAnomalousFeaturesByFloating = function() {
      var allFeatures = roadLayer.layer.getSource().getFeatures().concat(anomalousMarkerLayer.getSource().getFeatures()).concat(floatingMarkerLayer.getSource().getFeatures());
      _.each(allFeatures, function(feature){
        if(feature.roadLinkData.anomaly === 1 || feature.roadLinkData.roadLinkType === -1)
          pickRoadsLayer.getSource().addFeature(feature);
      });
      pickRoadsLayer.setOpacity(1);
      setGeneralOpacity(0.2);
    };

    var highlightSelectedFloatingFeatures = function() {
      var allFeatures = roadLayer.layer.getSource().getFeatures().concat(anomalousMarkerLayer.getSource().getFeatures()).concat(floatingMarkerLayer.getSource().getFeatures());
      var selectedFloatingIds = _.pluck(selectedLinkProperty.getFeaturesToKeepFloatings(), 'linkId');

      _.each(allFeatures, function(feature){
        if(feature.roadLinkData.anomaly === 1 || (_.contains(selectedFloatingIds, feature.roadLinkData.linkId) && feature.roadLinkData.roadLinkType === -1))
          pickRoadsLayer.getSource().addFeature(feature);
      });
      pickRoadsLayer.setOpacity(1);
      setGeneralOpacity(0.2);
    };

    eventbus.on('linkProperties:cleanFloatingsAfterDefloat', function(){
      cleanFloatingsAfterDefloat();
      cleanUnknownsAfterDefloat();
    });

    var cleanFloatingsAfterDefloat = function() {
      var floatingRoadMarker = [];
      /*
       * Clean from pickLayer floatings selected
       */
      var FeaturesToKeepFloatings = _.reject(_.map(selectedLinkProperty.getFeaturesToKeepFloatings(), function (featureToKeep){
        if((featureToKeep.roadLinkType === -1 && featureToKeep.anomaly === 0)){
          return featureToKeep.linkId;
        } else return undefined;
      }), function(featuresNotToKeep){
        return _.isUndefined(featuresNotToKeep);
      });

      var olUids = _.reject(_.map(pickRoadsLayer.getSource().getFeatures(), function(pickFeature){
        if(_.contains(FeaturesToKeepFloatings, pickFeature.roadLinkData.linkId))
          return pickFeature.ol_uid;
        else return undefined;
      }), function(featuresNotToKeep){
        return _.isUndefined(featuresNotToKeep);
      });
      var PickFeaturesToRemove = _.filter(pickRoadsLayer.getSource().getFeatures(), function (pickFeatureToRemove){
        return !_.contains(olUids, pickFeatureToRemove.ol_uid);
      });

      pickRoadsLayer.getSource().clear();
      pickRoadsLayer.getSource().addFeatures(PickFeaturesToRemove );

      /*
       * Clean from calibrationPoints layer selected
       */

      _.map(selectedLinkProperty.getFeaturesToKeepFloatings(), function(featureToKeep){
        if(featureToKeep.calibrationPoints.length > 0) {
          _.each(featureToKeep.calibrationPoints, function (cPoint) {
            var newPoint = new CalibrationPoint(cPoint.point).getMarker(true);
            _.each(calibrationPointLayer.getSource().getFeatures(), function (feature) {
              if (newPoint.values_.geometry.flatCoordinates[0] == feature.values_.geometry.flatCoordinates[0] &&
                newPoint.values_.geometry.flatCoordinates[1] == feature.values_.geometry.flatCoordinates[1]) {
                calibrationPointLayer.getSource().removeFeature(feature);
              }
            });
          });
        }
      });

      /*
       * Clean from roadLayer floatings selected
       */
      var olUidsRoadLayer = _.reject(_.map(roadLayer.layer.getSource().getFeatures(), function(featureRoadLayer){
        if(_.contains(FeaturesToKeepFloatings, featureRoadLayer.roadLinkData.linkId))
          return featureRoadLayer.ol_uid;
        else return undefined;
      }), function(featuresNotToKeep){
        return _.isUndefined(featuresNotToKeep);
      });
      var featuresRoadLayerToKeep = _.filter(roadLayer.layer.getSource().getFeatures(), function (featureRoadLayer){
        return !_.contains(olUidsRoadLayer, featureRoadLayer.ol_uid);
      });
      var featuresRoadLayerToRemove = _.filter(roadLayer.layer.getSource().getFeatures(), function (featureRoadLayer){
        return _.contains(olUidsRoadLayer, featureRoadLayer.ol_uid);
      });

      roadLayer.layer.getSource().clear();
      roadLayer.layer.getSource().addFeatures(featuresRoadLayerToKeep);
      floatingRoadMarker = floatingRoadMarker.concat(featuresRoadLayerToRemove);

      /*
       * Clean from floatingMarkerLayer markers from selected floatings
       */
      var olUidsFloatingLayer = _.reject(_.map(floatingMarkerLayer.getSource().getFeatures(), function(feature){
        if(_.contains(FeaturesToKeepFloatings, feature.roadLinkData.linkId))
          return feature.ol_uid;
        else return undefined;
      }), function(featuresNotToKeep){
        return _.isUndefined(featuresNotToKeep);
      });
      var featuresFloatingLayerToKeep = _.filter(floatingMarkerLayer.getSource().getFeatures(), function (featureFloatMarker){
        return !_.contains(olUidsFloatingLayer, featureFloatMarker.ol_uid);
      });

      var featuresFloatingLayerToRemove = _.filter(floatingMarkerLayer.getSource().getFeatures(), function (featureFloatMarker){
        return _.contains(olUidsFloatingLayer, featureFloatMarker.ol_uid);
      });

      floatingMarkerLayer.getSource().clear();
      floatingMarkerLayer.getSource().addFeatures(featuresFloatingLayerToKeep);
      floatingRoadMarker = floatingRoadMarker.concat(featuresFloatingLayerToRemove);
      /*
       * Add to FloatingRoadMarker to keep in the case of clicking cancel (peruuta).
       */
      selectedLinkProperty.setFloatingRoadMarker(floatingRoadMarker);
    };

    var cleanUnknownsAfterDefloat = function(){
      var unknownRoadMarkers =[];

      var unknownFeaturesToKeep = _.reject(_.map(selectedLinkProperty.getFeaturesToKeepUnknown(), function(feature){
        if(feature.anomaly === 1){
          return feature.linkId;
        } else return undefined;
      }), function(featureNotToKeep){
        return _.isUndefined(featureNotToKeep);
      });

      //Clean from anomalousMarkerLayer

      var olUidsAnomalousMarkerLayer = _.reject(_.map(anomalousMarkerLayer.getSource().getFeatures(),function(anomalousMarkerLayerFeature){
        if(_.contains(unknownFeaturesToKeep, anomalousMarkerLayerFeature.roadLinkData.linkId)){
          return anomalousMarkerLayerFeature.ol_uid;
        } else return undefined;
      }), function(featuresNotToKeep){
        return _.isUndefined(featuresNotToKeep);
      });

      var anomalousMarkerLayerFeaturesToKeep = _.filter(anomalousMarkerLayer.getSource().getFeatures(), function(pf){
        return !_.contains(olUidsAnomalousMarkerLayer, pf.ol_uid);
      });

      var anomalousMarkerLayerFeaturesToRemove = _.filter(anomalousMarkerLayer.getSource().getFeatures(), function(pf){
        return _.contains(olUidsAnomalousMarkerLayer, pf.ol_uid);
      });
      anomalousMarkerLayer.getSource().clear();
      anomalousMarkerLayer.getSource().addFeatures(anomalousMarkerLayerFeaturesToKeep);
      unknownRoadMarkers = unknownRoadMarkers.concat(anomalousMarkerLayerFeaturesToRemove);
      selectedLinkProperty.setAnomalousMarkers(unknownRoadMarkers);
    };

    eventbus.on('linkProperties:floatingRoadMarkerPreviousSelected', function(){
      addSelectedFloatings();
      addSelectedUnknowns();
    });

    var addSelectedFloatings = function() {
      var floatingRoadMarker = selectedLinkProperty.getFloatingRoadMarker();
      var floatingRoad = [];
      var floatingMarker = [];
      _.each(floatingRoadMarker, function(floating){
        if(floating.getGeometry().getType() == 'LineString'){
          floatingRoad.push(floating);
        } else {
          floatingMarker.push(floating);
        }
      });

      _.each(floatingMarker, function(marker){
        floatingMarkerLayer.getSource().addFeature(marker);
      });

      _.each(floatingRoad, function(road){
        roadLayer.layer.getSource().addFeature(road);
      });
    };

    var addSelectedUnknowns = function(){
      var anomalousRoadMarker = selectedLinkProperty.getAnomalousMarkers();
      var anomalousMarker = [];
      _.each(anomalousRoadMarker, function(anomalous){
        if(anomalous.getGeometry().getType() !== 'LineString'){
          anomalousMarker.push(anomalous);
        }
      });

      _.each(anomalousMarker, function(marker){
        anomalousMarkerLayer.getSource().addFeature(marker);
      });
    };

    eventbus.on('linkProperties:activateInteractions', function(){
      activateSelectInteractions();
    });

    eventbus.on('linkProperties:addFeaturesToInteractions', function(){
      activateSelectInteractions();
    });

    eventListener.listenTo(eventbus, 'linkProperties:unselected', function() {
      clearHighlights();
      setGeneralOpacity(1);
      if(greenRoadLayer.getSource().getFeatures().length !== 0) {
        unselectRoadLink();
      }
      if(indicatorLayer.getSource().getFeatures().length !== 0){
        indicatorLayer.getSource().clear();
      }
      if ('floating' === applicationModel.getSelectionType()) {
        setGeneralOpacity(0.2);
        floatingMarkerLayer.setOpacity(1);
      } else if ('unknown' === applicationModel.getSelectionType()) {
        setGeneralOpacity(0.2);
        anomalousMarkerLayer.setOpacity(1);
      }
    });

    eventListener.listenTo(eventbus, 'linkProperties:clearHighlights', function(){
      unselectRoadLink();
      if(pickRoadsLayer.getSource().getFeatures().length !== 0){
        pickRoadsLayer.getSource().clear();
      }
      clearHighlights();
      if(simulatedRoadsLayer.getSource().getFeatures().length !== 0){
        simulatedRoadsLayer.getSource().clear();
      }
      var featureToReOpen = _.cloneDeep(_.first(selectedLinkProperty.getFeaturesToKeepFloatings()));
      var visibleFeatures = getVisibleFeatures(true,true,true);
      selectedLinkProperty.openFloating(featureToReOpen.linkId, featureToReOpen.id, visibleFeatures);
    });

    eventListener.listenTo(eventbus, 'linkProperties:deactivateDoubleClick', function(){
      deactivateSelectInteractions();
    });

    eventListener.listenTo(eventbus, 'linkProperties:deactivateAllSelections', function(){
      deactivateSelectInteractions(true);
    });

    eventListener.listenTo(eventbus, 'linkProperties:activateDoubleClick', function(){
      activateSelectInteractions();
    });

    eventListener.listenTo(eventbus, 'linkProperties:activateAllSelections', function(){
      activateSelectInteractions(true);
    });

    eventListener.listenTo(eventbus, 'layer:selected', function(layer, previouslySelectedLayer){
      //TODO create proper system for layer changes and needed calls
      if (layer !== 'linkProperty') {
        deactivateSelectInteractions(true);
        removeSelectInteractions();
        activeLayer = false;
      } else {
        activeLayer = true;
        activateSelectInteractions(true);
        addSelectInteractions();
      }
      if (previouslySelectedLayer === 'linkProperty') {
        clearLayers();
        hideLayer();
        removeSelectInteractions();
      }
    });
    var show = function(map) {
      vectorLayer.setVisible(true);
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
      deactivateSelection: removeSelectInteractions,
      activateSelection: addSelectInteractions,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
