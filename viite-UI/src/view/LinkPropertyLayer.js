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
    var valintaRoadsLayerVector = new ol.source.Vector({});
    var simulationVector = new ol.source.Vector({});

    var indicatorLayer = new ol.layer.Vector({
      source: indicatorVector
    });

    var floatingMarkerLayer = new ol.layer.Vector({
      source: floatingMarkerVector
    });

    var anomalousMarkerLayer = new ol.layer.Vector({
      source: anomalousMarkerVector
    });

    var calibrationPointLayer = new ol.layer.Vector({
      source: calibrationPointVector
    });

    var greenRoadLayer = new ol.layer.Vector({
      source: greenRoadLayerVector
    });

    var valintaRoadsLayer = new ol.layer.Vector({
      source: valintaRoadsLayerVector,
      style: function(feature) {
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      }
    });

    var simulatedRoadsLayer = new ol.layer.Vector({
      source: simulationVector,
      style: function(feature) {
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      }
    });

    map.addLayer(floatingMarkerLayer);
    map.addLayer(anomalousMarkerLayer);
    map.addLayer(calibrationPointLayer);
    map.addLayer(indicatorLayer);
    map.addLayer(greenRoadLayer);
    map.addLayer(valintaRoadsLayer);
    map.addLayer(simulatedRoadsLayer);
    floatingMarkerLayer.setVisible(true);
    anomalousMarkerLayer.setVisible(true);
    calibrationPointLayer.setVisible(true);
    indicatorLayer.setVisible(true);
    greenRoadLayer.setVisible(true);
    valintaRoadsLayer.setVisible(true);
    simulatedRoadsLayer.setVisible(true);

    var isAnomalousById = function(featureId){
      var anomalousMarkers = anomalousMarkerLayer.getSource().getFeatures();
      return !_.isUndefined(_.find(anomalousMarkers, function(am){
        return am.id === featureId;
      }));
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

//This will control the double click zoom when there is no selection
    map.on('dblclick', function(event) {
      _.defer(function(){
        if(selectDoubleClick.getFeatures().getLength() < 1 && map.getView().getZoom() <= 13){
          map.getView().setZoom(map.getView().getZoom()+1);
        }
      });
    });

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
      layer: [roadLayer.layer, floatingMarkerLayer, anomalousMarkerLayer, greenRoadLayer, valintaRoadsLayer],
      //Limit this interaction to the singleClick
      condition: ol.events.condition.singleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      }
    });

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
          } else if(selection.roadLinkData.roadLinkType !== -1 && 'floating' === applicationModel.getSelectionType() &&
            !applicationModel.isReadOnly() && event.deselected.length !== 0) {
            var floatings = event.deselected;
            var nonFloatings = event.selected;
            removeFeaturesFromSelection(nonFloatings);
            addFeaturesToSelection(floatings);
          }else if ('unknown' === applicationModel.getSelectionType() && !applicationModel.isReadOnly() &&
            selection.roadLinkData.anomaly === 1 && selection.roadLinkData.roadLinkType !== -1  ){
            selectedLinkProperty.openUnknown(selection.roadLinkData.linkId, selection.roadLinkData.id, visibleFeatures);
          }
          else {
            if (isAnomalousById(selection.id) || isFloatingById(selection.id)) {
              selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, true, visibleFeatures);
            } else {
              selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, false, visibleFeatures);
            }
          }
        }
      } else if (event.selected.length === 0 && event.deselected.length !== 0) {
          selectedLinkProperty.close();
          setGeneralOpacity(1);
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
          console.log(selectSingleClick.getFeatures());
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

    var getVisibleFeatures = function(withRoads, withAnomalyMarkers, withFloatingMarkers, withGreenRoads, withValintaRoads){
      var extent = map.getView().calculateExtent(map.getSize());
      var visibleRoads = withRoads ? roadLayer.layer.getSource().getFeaturesInExtent(extent) : [];
      var visibleAnomalyMarkers =  withAnomalyMarkers ? anomalousMarkerLayer.getSource().getFeaturesInExtent(extent) : [];
      var visibleFloatingMarkers =  withFloatingMarkers ? floatingMarkerLayer.getSource().getFeaturesInExtent(extent) : [];
      var visibleGreenRoadLayer = withGreenRoads ? greenRoadLayer.getSource().getFeaturesInExtent(extent) : [];
      var visibleValintaRoadsLayer = withGreenRoads ? valintaRoadsLayer.getSource().getFeaturesInExtent(extent) : [];
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

    /*var unselectAllRoadLinks = function(options) {
     // we'll want an option to supress notification here
     var layers = this.layers || [this.layer],
     layer, feature, l, numExcept;
     for(l=0; l<layers.length; ++l) {
     layer = layers[l];
     numExcept = 0;
     //layer.selectedFeatures is null when layer is destroyed and
     //one of it's preremovelayer listener calls setLayer
     //with another layer on this control
     if(layer.selectedFeatures !== null) {
     if(applicationModel.isActiveButtons() && layer.selectedFeatures.length > numExcept)
     {
     return Confirm();
     }else {
     while (layer.selectedFeatures.length > numExcept) {
     feature = layer.selectedFeatures[numExcept];
     if (!options || options.except != feature) {
     this.unselect(feature);
     } else {
     ++numExcept;
     }
     }
     }
     }
     }
     };*/

    var clearLayers = function(){
      floatingMarkerLayer.getSource().clear();
      anomalousMarkerLayer.getSource().clear();
      calibrationPointLayer.getSource().clear();
      indicatorLayer.getSource().clear();
      greenRoadLayer.getSource().clear();
      valintaRoadsLayer.getSource().clear();
    };

    /*var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
     onSelect: selectRoadLink,
     onUnselect: unselectRoadLink,
     unselectAll: unselectAllRoadLinks
     });
     roadLayer.layer.events.register("beforefeatureselected", this, function(event){
     if(applicationModel.isActiveButtons()) {
     var feature = event.feature.attributes;
     if (applicationModel.isReadOnly() || applicationModel.getSelectionType() === 'all') {
     return true;
     } else {
     if (applicationModel.getSelectionType() === 'floating') {
     if (feature.roadLinkType !== -1) {
     me.displayConfirmMessage();
     return false;
     } else {
     return true;
     }
     }
     if (applicationModel.getSelectionType() === 'unknown') {
     if (feature.roadLinkType !== 0 && feature.anomaly !== 1 && !applicationModel.isActiveButtons()) {
     me.displayConfirmMessage();
     return false;
     } else {
     return true;
     }
     }
     }
     }
     });*/



    /*map.addControl(selectControl);
     var doubleClickSelectControl = new DoubleClickSelectControl(selectControl, map);
     this.selectControl = selectControl;
     */
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
     * -selectSingleClick
     */
    var deactivateSelectInteractions = function() {
      selectSingleClick.setActive(false);
      selectDoubleClick.setActive(false);
    };


    /**
     * This will activate the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick
     */
    var activateSelectInteractions = function() {
      selectSingleClick.setActive(true);
      selectDoubleClick.setActive(true);
    };

    /* var unselectRoadLink = function() {
     currentRenderIntent = 'default';
     selectedLinkProperty.close();
     _.map(roadLayer.layer.features,function (feature){
     if(feature.data.gapTransfering) {
     feature.data.gapTransfering = false;
     feature.attributes.gapTransfering = false;
     feature.data.anomaly = feature.data.prevAnomaly;
     feature.attributes.anomaly = feature.attributes.prevAnomaly;
     }
     });
     indicatorLayer.clearMarkers();
     unhighlightFeatures();
     roadLayer.redraw();
     };
     */

    var unselectRoadLink = function() {
      selectedLinkProperty.close();
      clearHighlights();
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
          //OL2
          //selectControl.highlight(x);
          //} else {
          //selectControl.unhighlight(x);
        }
      });
      if(featuresToHighlight.length !== 0)
        addFeaturesToSelection(featuresToHighlight);
    };

    /*var highlightFeatureByLinkId = function (linkId) {
     _.each(roadLayer.layer.features, function(feature) {
     if(feature.attributes.linkId == linkId){
     selectControl.highlight(feature);
     }
     });
     };*/
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

    /*var unhighlightFeatureByLinkId = function (linkId) {
     //OL2
     //_.each(roadLayer.layer.features, function(feature) {
     _.each (roadLayer.layer.getSource().getFeatures(), function(feature) {
     if(feature.roadLinkData.linkId == linkId){
     //OL2
     //feature.clear();
     selectSingleClick.getFeatures().remove(feature);
     }
     });
     };*/

    var draw = function() {
      cachedLinkPropertyMarker = new LinkPropertyMarker(selectedLinkProperty);
      cachedMarker = new LinkPropertyMarker(selectedLinkProperty);
      removeSelectInteractions();
      var roadLinks = roadCollection.getAll();

      if(floatingMarkerLayer.getSource() !== null)
        floatingMarkerLayer.getSource().clear();
      if(anomalousMarkerLayer.getSource() !== null)
        anomalousMarkerLayer.getSource().clear();

      if(map.getView().getZoom() > zoomlevels.minZoomForAssets) {
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

        var actualPoints =  me.drawCalibrationMarkers(calibrationPointLayer.source, roadLinks);
        _.each(actualPoints, function(actualPoint) {
          var calMarker = new CalibrationPoint(actualPoint.point);
          calibrationPointLayer.getSource().addFeature(calMarker.getMarker(true));
        });
      }
      addSelectInteractions();
    };

    /* var createMouseClickHandler = function(floatlink) {
     return function(event){
     if(floatlink.anomaly !== 1) {
     if(applicationModel.getSelectionType() !== 'floating' && floatlink.roadLinkType !== -1 || applicationModel.getSelectionType() === 'all'){
     selectControl.unselectAll();
     }
     }
     var feature = _.find(roadLayer.layer.features, function (feat) {
     return feat.attributes.linkId === floatlink.linkId;
     });
     if(event.type === 'click'){
     selectControl.select(_.assign({singleLinkSelect: false}, feature));
     } else if( event.type === 'dblclick'){
     selectControl.select(_.assign({singleLinkSelect: true}, feature));
     } else {
     selectControl.unselectAll();
     }
     };
     };*/

    /*this.refreshView = function() {
     // Generalize the zoom levels as the resolutions and zoom levels differ between map tile sources
     zoom = 11 - Math.round(Math.log(map.getResolution()) * Math.LOG2E);
     roadCollection.fetch(map.getExtent(), zoom);
     };*/

    this.refreshView = function() {
      //Generalize the zoom levels as the resolutions and zoom levels differ between map tile sources
      roadCollection.fetch(map.getExtent(), 11);
      roadLayer.layer.changed();
    };

    this.isDirty = function() {
      return selectedLinkProperty.isDirty();
    };

    var vectorLayer = new ol.layer.Vector();
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(true);

    /*var createDashedLineFeatures = function(roadLinks, dashedLineFeature) {
     return _.flatten(_.map(roadLinks, function(roadLink) {
     var points = _.map(roadLink.points, function(point) {
     return new OpenLayers.Geometry.Point(point.x, point.y);
     });
     var attributes = {
     dashedLineFeature: roadLink[dashedLineFeature],
     linkId: roadLink.linkId,
     type: 'overlay',
     linkType: roadLink.linkType,
     zIndex: 1
     };
     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
     }));
     };

     var unknownFeatureSizeLookup = {
     9: { strokeWidth: 3, pointRadius: 0 },
     10: { strokeWidth: 5, pointRadius: 10 },
     11: { strokeWidth: 7, pointRadius: 14 },
     12: { strokeWidth: 10, pointRadius: 16 },
     13: { strokeWidth: 10, pointRadius: 16 },
     14: { strokeWidth: 14, pointRadius: 22 },
     15: { strokeWidth: 14, pointRadius: 22 }
     };

     var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
     var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
     browseStyleMap.addUniqueValueRules('default', 'level', unknownFeatureSizeLookup, applicationModel.zoom);
     */

    /*
     var typeFilter = function(type) {
     return new OpenLayers.Filter.Comparison(
     { type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type });
     };
     */

    /*
     var unknownLimitStyleRule = new OpenLayers.Rule({
     filter: typeFilter('roadAddressAnomaly'),
     symbolizer: { externalGraphic: 'images/speed-limits/unknown.svg' }
     });
     */

    /*
     browseStyle.addRules([unknownLimitStyleRule]);
     var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: browseStyleMap });
     vectorLayer.setOpacity(1);
     vectorLayer.setVisibility(true);*/

    /*    var drawDashedLineFeatures = function(roadLinks) {
     var dashedRoadClasses = [7, 8, 9, 10];
     var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
     return _.contains(dashedRoadClasses, roadLink.roadClass);
     });
     roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'functionalClass'));
     };

     var drawUnderConstructionFeatures = function(roadLinks) {
     var constructionTypeValues = [1];
     var unknownType = 'unknownConstructionType';
     var dashedUnknownUnderConstructionRoadLinks = _.filter(roadLinks, function(roadLink) {
     return _.contains(constructionTypeValues, roadLink.constructionType) && roadLink.anomaly === 1;
     });
     var type = 'constructionType';
     var dashedUnderConstructionRoadLinks = _.filter(roadLinks, function(roadLink) {
     return _.contains(constructionTypeValues, roadLink.constructionType) && roadLink.roadClass === 99 && roadLink.anomaly === 0;
     });
     roadLayer.layer.addFeatures(createDarkDashedLineFeatures(dashedUnknownUnderConstructionRoadLinks, unknownType));
     roadLayer.layer.addFeatures(createDarkDashedLineFeatures(dashedUnderConstructionRoadLinks, type));
     };

     var drawDashedLineFeaturesForType = function(roadLinks) {
     var dashedLinkTypes = [2, 4, 6, 8, 12, 21];
     var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
     return _.contains(dashedLinkTypes, roadLink.linkType);
     });
     roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'linkType'));
     };
     var drawBorderLineFeatures = function(roadLinks) {
     var adminClass = 'Municipality';
     var roadClasses = [1,2,3,4,5,6,7,8,9,10,11];
     var borderLineFeatures = _.filter(roadLinks, function(roadLink) {
     return _.contains(adminClass, roadLink.administrativeClass) && _.contains(roadClasses, roadLink.roadClass) && roadLink.roadLinkType !== -1 && !(roadLink.roadLinkType === -1 && roadLink.roadClasses === 3);
     });
     var features = createBorderLineFeatures(borderLineFeatures, 'functionalClass');
     roadLayer.layer.addFeatures(features);
     };
     var createDarkDashedLineFeatures = function(roadLinks, type){
     return darkDashedLineFeatures(roadLinks, type).concat(calculateMidPointForMarker(roadLinks, type));
     };
     var darkDashedLineFeatures = function(roadLinks, darkDashedLineFeature) {
     return _.flatten(_.map(roadLinks, function(roadLink) {
     var points = _.map(roadLink.points, function(point) {
     return new OpenLayers.Geometry.Point(point.x, point.y);
     });
     var attributes = {
     dashedLineFeature: roadLink[darkDashedLineFeature],
     linkId: roadLink.linkId,
     type: 'overlay-dark',
     linkType: roadLink.linkType,
     zIndex: 1
     };
     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
     }));
     };
     var calculateMidPointForMarker = function(roadLinks, type){
     return _.map(roadLinks, function(link) {
     var points = _.map(link.points, function(point) {
     return new OpenLayers.Geometry.Point(point.x, point.y);
     });
     var road = new OpenLayers.Geometry.LineString(points);
     var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
     var attributes = {type: type, linkId: link.linkId};
     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
     });
     };
     var createBorderLineFeatures = function(roadLinks) {
     return _.flatten(_.map(roadLinks, function(roadLink) {
     var points = _.map(roadLink.points, function(point) {
     return new OpenLayers.Geometry.Point(point.x, point.y);
     });
     var attributes = {
     linkId: roadLink.linkId,
     type: 'underlay',
     linkType: roadLink.roadLinkType
     };
     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
     }));
     };*/

    var getSelectedFeatures = function() {
      return _.filter(roadLayer.layer.getSource().getFeatures(), function (feature) {
        return selectedLinkProperty.isSelectedByLinkId(feature.roadLinkData.linkId);
        /*
         return _.filter(roadLayer.layer.features, function (feature) {
         return selectedLinkProperty.isSelectedByLinkId(feature.attributes.linkId);*/
      });
    };

    var reselectRoadLink = function(targetFeature, adjacents) {
      //me.activateSelection();
      //var originalOnSelectHandler = selectControl.onSelect;
      //selectControl.onSelect = function() {};
      var visibleFeatures = getVisibleFeatures(true, true, true, true, true);
      var features = getSelectedFeatures();
      //var indicators = jQuery.extend(true, [], indicatorLayer.markers);
      var indicators = adjacents;
      indicatorLayer.getSource().clear();
      if(indicators.length !== 0){
         drawIndicators(indicators);
      }
      if (applicationModel.getSelectionType() === 'unknown' && !applicationModel.isReadOnly() &&
        targetFeature.roadLinkData.anomaly === 1 && targetFeature.roadLinkData.roadLinkType !== -1  ){
        selectedLinkProperty.openUnknown(targetFeature.roadLinkData.linkId, targetFeature.roadLinkData.id, visibleFeatures);
      }

      //if (!_.isEmpty(features)) {
      //addFeaturesToSelection(features);
      //}

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
      /*selectControl.onSelect = originalOnSelectHandler;
       if (selectedLinkProperty.isDirty()) {
       me.deactivateSelection();*/
      //}
    };

    /*
     var drawDashedLineFeaturesIfApplicable = function (roadLinks) {
     drawDashedLineFeatures(roadLinks);
     drawBorderLineFeatures(roadLinks);
     drawUnderConstructionFeatures(roadLinks);
     };
     */

    var handleLinkPropertyChanged = function(eventListener) {
      //OL2
      //redrawSelected();
      removeSelectInteractions();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      addSelectInteractions();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      setGeneralOpacity(1);
      //removeSelectInteractions();
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

      /*
       eventListener.listenTo(eventbus, 'linkProperties:multiSelected', function(link) {
       if (!_.isEmpty(selectedLinkProperty.get())){
       var feature = _.find(roadLayer.layer.features, function (feature) {
       return link.linkId !== 0 && feature.attributes.linkId === link.linkId;
       });
       if (feature) {
       _.each(selectControl.layer.selectedFeatures, function (selectedFeature) {
       if (selectedFeature.attributes.linkId !== feature.attributes.linkId) {
       selectControl.select(feature);
       }
       });
       }
       }
       clearIndicators();
       });
       */
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

      eventListener.listenTo(eventbus, 'roadLinks:fetched', draw);
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
          highlightFeatureByLinkId(roads.linkId);
        });
        highlightFeatureByLinkId(aditionalLinkId);
      });

      eventListener.listenTo(eventbus, 'adjacents:floatingAdded', function(floatings){
        drawIndicators(floatings);
      });
      eventListener.listenTo(eventbus, 'adjacents:roadTransfer', function(newRoads,changedIds){
        var roadLinks = roadCollection.getAll();
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
        indicatorLayer.getSource().clear();
        roadCollection.setTmpRoadAddresses(afterTransferLinks);
        roadCollection.setChangedIds(changedIds);
        applicationModel.setCurrentAction(applicationModel.actionCalculated);
        selectedLinkProperty.cancelAfterSiirra(applicationModel.actionCalculated, changedIds);

        clearHighlights();
        greenRoadLayer.getSource().clear();
        setGeneralOpacity(0.2);
        simulatedRoadsLayer.getSource().addFeatures(simulatedOL3Features);
      });
      /*
       eventbus.on('linkProperties:reselectRoadLink', function(){
       reselectRoadLink();
       roadLayer.redraw();
       });
       */

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
            //TODO: Work in progress
            selectedLinkProperty.openFloating(featureToSelect.roadLinkData.linkId, featureToSelect.roadLinkData.id, visibleFeatures);
            highlightFeatures();
          }
        }
      });

      eventListener.listenTo(eventbus, 'linkProperties:clearIndicators', function(){
        clearIndicators();
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


    var greenRoads = function(Ol3Features) {
      var features = [];

      var style = new ol.style.Style({
        fill: new ol.style.Fill({
          color: 'rgba(0, 255, 0, 0.75)'
        }),
        stroke: new ol.style.Stroke({
          color: 'rgba(0, 255, 0, 0.75)',
          width: 7
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
      greenRoadLayer.setZIndex(1000);
      greenRoadLayer.getSource().addFeatures(features);
    };

    var handleMapClick = function (){
      if(!applicationModel.isActiveButtons()){
        selectedLinkProperty.cancel();
        selectedLinkProperty.close();
      }

    };

    var cancelSelection = function() {
      if(!applicationModel.isActiveButtons()) {
          selectedLinkProperty.cancel();
          selectedLinkProperty.close();
          unselectRoadLink();
      }
    };

    var refreshViewAfterSaving = function() {
      unselectRoadLink();
      me.refreshView();
    };

    var redrawSelected = function(action) {
      var selectedRoadLinks = [];
      if(!applicationModel.isActiveButtons()){
        roadLayer.layer.removeFeatures(getSelectedFeatures());
      }
      if((!_.isUndefined(action) && _.isEqual(action, applicationModel.actionCalculated)) || !_.isEmpty(roadCollection.getAllTmp())){
        selectedRoadLinks = roadCollection.getAllTmp();
      } else {
        selectedRoadLinks = selectedLinkProperty.get();
      }
      //_.each(selectedRoadLinks,  function(selectedLink) { roadLayer.drawRoadLink(selectedLink); });
      //drawDashedLineFeaturesIfApplicable(selectedRoadLinks);
      //me.drawSigns(roadLayer.layer, selectedRoadLinks);
      reselectRoadLink();
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
            }
          });

          greenRoads(features);
          addFeaturesToSelection(features);
        }
      }
      if(features.length === 0)
        return undefined;
      else return _.first(features);
    };

    eventbus.on('linkProperties:highlightAnomalousByFloating', function(){
      highlightAnomalousFeaturesByFloating();
    });

    var highlightAnomalousFeaturesByFloating = function() {
      /*var featuresAnFloa = _.filter(roadLayer.layer.getSource().getFeatures(), function(anfeature){
       return anfeature.roadLinkData.anomaly === 1 || anfeature.roadLinkData.roadLinkType === -1;
       });
       var features = featuresAnFloa.concat(floatingMarkerLayer.getSource().getFeatures(), anomalousMarkerLayer.getSource().getFeatures());
       addFeaturesToSelection(features);
       */
      _.each(roadLayer.layer.getSource().getFeatures(), function(anfeature){
        if(anfeature.roadLinkData.anomaly === 1 || anfeature.roadLinkData.roadLinkType === -1)
          valintaRoadsLayer.getSource().addFeature(anfeature);
      });
      valintaRoadsLayer.setOpacity(1);
      roadLayer.setOpacity(0.2);
    };

    eventbus.on('linkProperties:unselectAllFeatures', function(){
      selectControl.unselectAll();
    });

    eventbus.on('linkProperties:deactivateInteractions', function(){
      //deactivateSelectInteractions();
    });

    eventbus.on('linkProperties:activateInteractions', function(){
      activateSelectInteractions();
    });

    eventbus.on('linkProperties:addFeaturesToInteractions', function(){
      activateSelectInteractions();
    });

    eventListener.listenTo(eventbus, 'linkProperties:unselected', function() {
      clearHighlights();
      setGeneralOpacity(1);
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
      if(greenRoadLayer.getSource().getFeatures().length !== 0){
        unselectRoadLink();
      }
      clearHighlights();
      if(simulatedRoadsLayer.getSource().getFeatures().length !== 0){
        simulatedRoadsLayer.getSource().clear();
      }
      var featureToReOpen = _.cloneDeep(_.first(selectedLinkProperty.getFeaturesToKeepFloatings()));
      var visibleFeatures = getVisibleFeatures(true,true,true);
      selectedLinkProperty.openFloating(featureToReOpen.linkId, featureToReOpen.id, visibleFeatures);
    });

    var show = function(map) {
      vectorLayer.setVisible(true);
      //eventListener.listenTo(eventbus, 'map:clicked', cancelSelection);
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
