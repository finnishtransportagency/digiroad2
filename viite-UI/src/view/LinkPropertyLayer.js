(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, selectedLinkProperty, roadCollection, linkPropertiesModel, applicationModel,styler) {
    var layerName = 'linkProperty';
    var cachedLinkPropertyMarker = null;
    var cachedMarker = null;
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var eventListener = _.extend({running: false}, eventbus);
    var zoom = 7;
    var currentRenderIntent = 'default';
    //TODO - Show markers on Layer
    var linkPropertyLayerStyles = Styler();
    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;
    //TODO
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


     //roadLayer.setLayerSpecificStyleMapProvider(layerName, function() {
     //  return linkPropertyLayerStyles.getDatasetSpecificStyleMap(linkPropertiesModel.getDataset(), currentRenderIntent);
     //});
    
    /**
     * We declare the type of interaction we want the map to be able to respond.
     * A selected feature is moved to a new/temporary layer out of the default roadLayer.
     * @type {ol.interaction.Select}
     */
    var selectDoubleClick = new ol.interaction.Select({
      //Multi is the one en charge of defining if we select just the feature we clicked or all the overlaping
      multi: true,
      //This will limit the interaction to the specific layer, in this case the layer where the roadAddressLinks are drawn
      layer: roadLayer.layer,
      //Limit this interaction to the doubleClick
      condition: ol.events.condition.doubleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
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
      if(event.selected.length !== 0) {
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
      }
    });

    var selectSingleClick = new ol.interaction.Select({
      //Multi is the one en charge of defining if we select just the feature we clicked or all the overlaping
      multi: true,
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

    eventbus.on('linkProperties:ol3Selected',function(ol3Features){
      selectSingleClick.getFeatures().clear();
      _.each(ol3Features, function(feature){
        selectSingleClick.getFeatures().push(feature);
      });
    });

    var selectMarkers = new ol.interaction.Select({
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

    // roadLayer.setLayerSpecificStyleMapProvider(layerName, function() {
    //   return linkPropertyLayerStyles.getDatasetSpecificStyleMap(linkPropertiesModel.getDataset(), currentRenderIntent);
    // });

    var selectRoadLink = function(feature) {
      if(typeof feature.attributes.linkId !== 'undefined') {
        selectedLinkProperty.open(feature.attributes.linkId, feature.attributes.id, feature.singleLinkSelect);
        unhighlightFeatures();
        currentRenderIntent = 'select';
        roadLayer.redraw();
        highlightFeatures();
      }
    };

    var unselectRoadLink = function() {
      currentRenderIntent = 'default';
      selectedLinkProperty.close();
      roadLayer.redraw();
      indicatorLayer.clear();
      unhighlightFeatures();
    };

    var unselectAllRoadLinks = function(options) {
        // we'll want an option to supress notification here
      //TODO
        // var layers = this.layers || [this.layer],
        //     layer, feature, l, numExcept;
        // for(l=0; l<layers.length; ++l) {
        //     layer = layers[l];
        //     numExcept = 0;
        //     //layer.selectedFeatures is null when layer is destroyed and
        //     //one of it's preremovelayer listener calls setLayer
        //     //with another layer on this control
        //     if(layer.selectedFeatures !== null) {
        //         if(applicationModel.isActiveButtons() && layer.selectedFeatures.length > numExcept)
        //         {
        //             return Confirm();
        //         }else {
        //             while (layer.selectedFeatures.length > numExcept) {
        //                 feature = layer.selectedFeatures[numExcept];
        //                 if (!options || options.except != feature) {
        //                     this.unselect(feature);
        //                 } else {
        //                     ++numExcept;
        //                 }
        //             }
        //         }
        //     }
        // }
    };

     var selectControl = new ol.interaction.Select(roadLayer.layer, {
       onSelect: selectRoadLink,
       onUnselect: unselectRoadLink,
       unselectAll: unselectAllRoadLinks
     });

     map.addControl(selectControl);
     //var doubleClickSelectControl = new DoubleClickSelectControl(selectControl, map);
     this.selectControl = selectControl;

    this.activateSelection = function() {
      // doubleClickSelectControl.activate();
    };
    this.deactivateSelection = function() {
       //doubleClickSelectControl.deactivate();
    };

    var highlightFeatures = function() {
       _.each(roadLayer.layer.features, function(x) {
         var gapTransfering = x.data.gapTransfering;
         var canIHighlight = !_.isUndefined(x.attributes.linkId) ? selectedLinkProperty.isSelectedByLinkId(x.attributes.linkId) : selectedLinkProperty.isSelectedById(x.attributes.id);
         if(gapTransfering || canIHighlight){
           selectControl.highlight(x);
         } else {
           selectControl.unhighlight(x);
         }
       });
    };

    var unhighlightFeatures = function() {
       _.each(roadLayer.layer.features, function(x) {
         selectControl.unhighlight(x);
       });
    };

    var draw = function() {
      cachedLinkPropertyMarker = new LinkPropertyMarker(selectedLinkProperty);
      cachedMarker = new LinkPropertyMarker(selectedLinkProperty);
      prepareRoadLinkDraw();
      var roadLinks = roadCollection.getAll();

      //roadLayer.drawRoadLinks(roadLinks, zoom);
      //drawDashedLineFeaturesIfApplicable(roadLinks);
      //me.drawSigns(roadLayer.layer, roadLinks);

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
          var mouseClickHandler = createMouseClickHandler(floatlink);
          var marker = cachedLinkPropertyMarker.createMarker(floatlink);
          //TODO - Click marker events
          /*marker.events.add('click',marker, mouseClickHandler);
          marker.events.add('dblclick',marker, mouseClickHandler);*/
          floatingMarkerLayer.getSource().addFeature(marker);
      });

        _.each(anomalousRoadMarkers, function(anomalouslink) {
          var mouseClickHandler = createMouseClickHandler(anomalouslink);
          var marker = cachedMarker.createMarker(anomalouslink);
          //TODO - Click marker events
          /*marker.events.add('click',marker, mouseClickHandler);
          marker.events.add('dblclick',marker, mouseClickHandler);*/
          anomalousMarkerLayer.getSource().addFeature(marker);
        });
      }

      me.drawRoadNumberMarkers(roadLayer.source, roadLinks);
      if (zoom > zoomlevels.minZoomForAssets) {
        var actualPoints =  me.drawCalibrationMarkers(roadLayer.source, roadLinks);
         _.each(actualPoints, function(actualPoint) {
          var calMarker = new CalibrationPoint(actualPoint.point);
          floatingMarkerLayer.getSource().addFeature(calMarker.getMarker(true));
        });
      }
      redrawSelected();
      eventbus.trigger('linkProperties:available');
    };

    var createMouseClickHandler = function(floatlink) {
      return function(event){
        selectControl.unselectAll();
        var feature = _.find(roadLayer.layer.features, function (feat) {
          return feat.attributes.linkId === floatlink.linkId;
        });
        if(event.type === 'click' || event.type === 'dblclick'){
          selectControl.select(_.assign({singleLinkSelect: true}, feature));
        } else {
          selectControl.unselectAll();
        }
      };
    };

    this.refreshView = function() {
      // Generalize the zoom levels as the resolutions and zoom levels differ between map tile sources
      //zoom = 11 - Math.round(Math.log(map.getResolution()) * Math.LOG2E);
      roadCollection.fetch(map.getExtent(), 11);
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
          linkType: roadLink.linkType,
          zIndex: 1
        };
        return new ol.layer.Vector(new OpenLayers.Geometry.LineString(points), attributes);
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

    // var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
    // var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
    // browseStyleMap.addUniqueValueRules('default', 'level', unknownFeatureSizeLookup, applicationModel.zoom);

    var typeFilter = function(type) {
      // return new OpenLayers.Filter.Comparison(
      //   { type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type });
    };

    // var unknownLimitStyleRule = new OpenLayers.Rule({
    //   filter: typeFilter('roadAddressAnomaly'),
    //   symbolizer: { externalGraphic: 'images/speed-limits/unknown.svg' }
    // });
    //
    // browseStyle.addRules([unknownLimitStyleRule]);
     var vectorLayer = new ol.layer.Vector();
     vectorLayer.setOpacity(1);
     vectorLayer.setVisible(true);

     var drawDashedLineFeatures = function(roadLinks) {
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
    // var createDarkDashedLineFeatures = function(roadLinks, type){
    //   return darkDashedLineFeatures(roadLinks, type).concat(calculateMidPointForMarker(roadLinks, type));
    // };
    // var darkDashedLineFeatures = function(roadLinks, darkDashedLineFeature) {
    //   return _.flatten(_.map(roadLinks, function(roadLink) {
    //     var points = _.map(roadLink.points, function(point) {
    //       return new OpenLayers.Geometry.Point(point.x, point.y);
    //     });
    //     var attributes = {
    //       dashedLineFeature: roadLink[darkDashedLineFeature],
    //       linkId: roadLink.linkId,
    //       type: 'overlay-dark',
    //       linkType: roadLink.linkType,
    //       zIndex: 1
    //     };
    //     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
    //   }));
    // };
    // var calculateMidPointForMarker = function(roadLinks, type){
    //   return _.map(roadLinks, function(link) {
    //     var points = _.map(link.points, function(point) {
    //       return new OpenLayers.Geometry.Point(point.x, point.y);
    //     });
    //     var road = new OpenLayers.Geometry.LineString(points);
    //     var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
    //     var attributes = {type: type, linkId: link.linkId};
    //     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
    //   });
    // };
     var createBorderLineFeatures = function(roadLinks) {
       return _.flatten(_.map(roadLinks, function(roadLink) {
         var points = _.map(roadLink.points, function(point) {
           return new ol.geom.Point(point.x, point.y);
         });
         var attributes = {
           linkId: roadLink.linkId,
           type: 'underlay',
           linkType: roadLink.roadLinkType
         };
         return new new ol.layer.Vector(new ol.geom.LineString(points), attributes);
       }));
     };

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
    indicatorLayer.clearMarkers();
       if(indicators.length !== 0){
         _.forEach(indicators, function(indicator){
           indicatorLayer.addMarker(createIndicatorFromBounds(indicator.bounds, indicator.div.innerText));
         });
       }
       if (!_.isEmpty(features)) {
         currentRenderIntent = 'select';
         selectControl.select(_.first(features));
         highlightFeatures();
       }
       selectControl.onSelect = originalOnSelectHandler;
       if (selectedLinkProperty.isDirty()) {
         me.deactivateSelection();
       }
     };

     var prepareRoadLinkDraw = function() {
       me.deactivateSelection();
     };

     var drawDashedLineFeaturesIfApplicable = function (roadLinks) {
       //drawDashedLineFeatures(roadLinks);
       //drawBorderLineFeatures(roadLinks);
       //drawUnderConstructionFeatures(roadLinks);
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
       eventListener.listenTo(eventbus, 'map:clicked', handleMapClick);
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
       indicatorLayer.clearMarkers();
       var indicators = me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
         var bounds = ol.extent.boundingExtent([middlePoint.x, middlePoint.y, middlePoint.x, middlePoint.y]);
         return createIndicatorFromBounds(bounds, link.marker);
       });
       _.forEach(indicators, function(indicator){
         indicatorLayer.addMarker(indicator);
       });
    };

    var createIndicatorFromBounds = function(bounds, marker) {
       var markerTemplate = _.template('<span class="marker" style="margin-left: -1em; margin-top: -1em; position: absolute;"><%= marker %></span>');
       var box = new OpenLayers.Marker.Box(bounds, "00000000");
       $(box.div).html(markerTemplate({'marker': marker}));
       $(box.div).css('overflow', 'visible');
       return box;
    };

    var handleMapClick = function (){
      // selectedLinkProperty.cancel();
      // selectedLinkProperty.close();
    };

    var cancelSelection = function() {
      // selectedLinkProperty.cancel();
      // selectedLinkProperty.close();
      // unselectRoadLink();
    };

    var refreshViewAfterSaving = function() {
      // unselectRoadLink();
      // me.refreshView();
    };

    var handleLinkPropertyChanged = function(eventListener) {
      // redrawSelected();
      // me.deactivateSelection();
      // eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      // eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      // me.activateSelection();
      // eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      // redrawSelected();
    };

    var redrawSelected = function() {
       //roadLayer.layer.removeFeatures(getSelectedFeatures());
       var selectedRoadLinks = selectedLinkProperty.get();
       _.each(selectedRoadLinks,  function(selectedLink) { roadLayer.drawRoadLink(selectedLink); });
       drawDashedLineFeaturesIfApplicable(selectedRoadLinks);
       //me.drawSigns(roadLayer.layer, selectedRoadLinks);
       //reselectRoadLink();
    };

    var redrawNextSelectedTarget= function(targets, adjacents) {
       _.find(roadLayer.layer.features, function(feature) {
         return targets !== 0 && feature.attributes.linkId == targets;
       }).data.gapTransfering = true;
       _.find(roadLayer.layer.features, function(feature) {
         return targets !== 0 && feature.attributes.linkId == targets;
       }).attributes.gapTransfering = true;
       _.find(roadLayer.layer.features, function(feature) {
         return targets !== 0 && feature.attributes.linkId == targets;
       }).data.anomaly = 0;
       _.find(roadLayer.layer.features, function(feature) {
         return targets !== 0 && feature.attributes.linkId == targets;
       }).attributes.anomaly = 0;
       var feature = _.find(roadLayer.layer.features, function(feature) {
         return targets !== 0 && feature.attributes.linkId == targets;
       });
       reselectRoadLink();
       draw();
    };

    this.removeLayerFeatures = function() {
      // roadLayer.layer.removeFeatures(roadLayer.layer.getFeaturesByAttribute('type', 'overlay'));
      // indicatorLayer.clearMarkers();
    };

    var show = function(map) {
       vectorLayer.setVisible(true);
       //me.show(map);
       eventListener.listenTo(eventbus, 'map:clicked', cancelSelection);
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
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
