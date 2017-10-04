(function(root) {
  root.ProjectLinkLayer = function(map, projectCollection, selectedProjectLinkProperty, roadLayer) {

    var layerName = 'roadAddressProject';
    var vectorLayer;
    var calibrationPointVector = new ol.source.Vector({});
    var directionMarkerVector = new ol.source.Vector({});
    var suravageProjectDirectionMarkerVector = new ol.source.Vector({});
    var suravageRoadVector = new ol.source.Vector({});
    var cachedMarker = null;
    var layerMinContentZoomLevels = {};
    var currentZoom = 0;
    var standardZIndex = 6;
    var floatingRoadLinkType=-1;
    var noAnomaly=0;
    var noAddressAnomaly=1;
    var geometryChangedAnomaly=2;
    var againstDigitizing = 3;
    var towardsDigitizing = 2;
    var notHandledStatus = 0;
    var unchangedStatus = 1;
    var newRoadAddressStatus = 2;
    var transferedStatus = 3;
    var numberingStatus = 4;
    var terminatedStatus = 5;
    var unknownStatus = 99;
    var isNotEditingData = true;
    Layer.call(this, layerName, roadLayer);
    var project;
    var me = this;
    var styler = new Styler();

    var vectorSource = new ol.source.Vector({
      loader: function(extent, resolution, projection) {
        var zoom = Math.log(1024/resolution) / Math.log(2);

        var nonSuravageRoads = _.filter(projectCollection.getAll(), function(projectRoad){
          return projectRoad.roadLinkSource !== 3;
        });
        var features = _.map(nonSuravageRoads, function(projectLink) {
          var points = _.map(projectLink.points, function(point) {
            return [point.x, point.y];
          });
          var feature =  new ol.Feature({ geometry: new ol.geom.LineString(points)
          });
          feature.projectLinkData = projectLink;
          feature.linkId = projectLink.linkId;
          return feature;
        });
        loadFeatures(features);
      },
      strategy: ol.loadingstrategy.bbox
    });

    var calibrationPointLayer = new ol.layer.Vector({
      source: calibrationPointVector,
      name: 'calibrationPointLayer'
    });

    var directionMarkerLayer = new ol.layer.Vector({
      source: directionMarkerVector,
      name: 'directionMarkerLayer'
    });

    var suravageRoadProjectLayer = new ol.layer.Vector({
      source: suravageRoadVector,
      name:'suravageRoadProjectLayer',
      style: function(feature) {
        return styler.generateStyleByFeature(feature.projectLinkData, map.getView().getZoom());
      }
    });

    var suravageProjectDirectionMarkerLayer =  new ol.layer.Vector({
      source: suravageProjectDirectionMarkerVector,
      name: 'suravageProjectDirectionMarkerLayer'
    });

    var styleFunction = function (feature, resolution){
      var status = feature.projectLinkData.status;
      var borderWidth;
      var lineColor;

      if(status === notHandledStatus) {
        borderWidth = 8;
        lineColor = 'rgba(247, 254, 46, 1)';
      }
      if (status === terminatedStatus) {
        borderWidth = 3;
        lineColor = 'rgba(56, 56, 54, 1)';
      }
      if(status === transferedStatus) {
        borderWidth = 3;
        lineColor = 'rgba(255, 0, 0, 1)';
      }
      if (status === unchangedStatus) {
        borderWidth = 5;
        lineColor = 'rgba(0, 0, 255, 1)';
      }
      if(status === numberingStatus) {
        borderWidth = 5;
        lineColor = 'rgba(139,69,19, 1)';
      }

      if (status === newRoadAddressStatus) {
        borderWidth = 5;
        lineColor = 'rgba(255, 85, 221, 0.7)';
      }

      if (status === notHandledStatus || status === terminatedStatus || status  === newRoadAddressStatus || status == transferedStatus || status === unchangedStatus || status == numberingStatus) {
        var strokeWidth = styler.strokeWidthByZoomLevel(currentZoom, feature.projectLinkData.roadLinkType, feature.projectLinkData.anomaly, feature.projectLinkData.roadLinkSource, false, feature.projectLinkData.constructionType);
        var borderCap = 'round';

        var line = new ol.style.Stroke({
          width: strokeWidth + borderWidth,
          color: lineColor,
          lineCap: borderCap
        });

        //Declaration of the Line Styles
        var lineStyle = new ol.style.Style({
          stroke: line
        });

        var zIndex = styler.determineZIndex(feature.projectLinkData.roadLinkType, feature.projectLinkData.anomaly, feature.projectLinkData.roadLinkSource, status);
        lineStyle.setZIndex(zIndex + 1);
        return [lineStyle];
      }
      else{
        return styler.generateStyleByFeature(feature.projectLinkData, currentZoom);
      }
    };

    vectorLayer = new ol.layer.Vector({
      source: vectorSource,
      name: layerName,
      style: styleFunction
    });

    var showChangesAndSendButton = function () {
      selectedProjectLinkProperty.clean();
      $('.wrapper').remove();
      $('#actionButtons').html('<button class="show-changes btn btn-block btn-show-changes">Avaa projektin yhteenvetotaulukko</button><button disabled id ="send-button" class="send btn btn-block btn-send">Tee tieosoitteenmuutosilmoitus</button>');
    };

    var fireDeselectionConfirmation = function (shiftPressed, selection) {
      new GenericConfirmPopup('Haluatko poistaa tien valinnan ja hylätä muutokset?', {
        successCallback: function () {
          eventbus.trigger('roadAddressProject:discardChanges');
          isNotEditingData = true;
          clearHighlights();
          showChangesAndSendButton();
          if(!_.isUndefined(selection)){
            if(selection.size > 1)
              showSingleClickChanges(shiftPressed, selection);
            else
              showDoubleClickChanges(shiftPressed, selection);
          }

        },
        closeCallback: function(){
          isNotEditingData = false;
        }
      });
    };

    var selectSingleClick = new ol.interaction.Select({
      layer: [vectorLayer, suravageRoadProjectLayer],
      condition: ol.events.condition.singleClick,
      style: function(feature, resolution) {
        if(projectLinkStatusIn(feature.projectLinkData, [notHandledStatus, newRoadAddressStatus,terminatedStatus, transferedStatus, unchangedStatus, numberingStatus]) || feature.projectLinkData.roadLinkSource === 3){
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 255, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 255, 0, 0.95)',
              width: 8
            })
          });
        } else if(feature.projectLinkData.anomaly === noAddressAnomaly && feature.projectLinkData.status === unknownStatus){
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 255, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 255, 0, 0.95)',
              width: 8
            })
          });
        } else if (feature.projectLinkData.roadClass === 99){
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 255, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 255, 0, 0.95)',
              width: 8
            })
          });
        }
      }
    });

    selectSingleClick.set('name','selectSingleClickInteractionPLL');

    selectSingleClick.on('select',function(event) {
      var shiftPressed = event.mapBrowserEvent !== undefined ?
        event.mapBrowserEvent.originalEvent.shiftKey : false;
      var selection = _.find(event.selected, function (selectionTarget) {
        return (!_.isUndefined(selectionTarget.projectLinkData) && (
          projectLinkStatusIn(selectionTarget.projectLinkData, [notHandledStatus, newRoadAddressStatus,terminatedStatus,unchangedStatus, transferedStatus, numberingStatus]) ||
          (selectionTarget.projectLinkData.anomaly==noAddressAnomaly && selectionTarget.projectLinkData.roadLinkType!=floatingRoadLinkType) ||
          selectionTarget.projectLinkData.roadClass === 99 || selectionTarget.projectLinkData.roadLinkSource === 3 )
        );
      });
      if(isNotEditingData){
        showSingleClickChanges(shiftPressed, selection);
      } else {
        var selectedFeatures = event.deselected.concat(selectDoubleClick.getFeatures().getArray());
        clearHighlights();
        addFeaturesToSelection(selectedFeatures);
        fireDeselectionConfirmation(shiftPressed, selection);
      }
    });

    var showSingleClickChanges = function(shiftPressed, selection){
      if (shiftPressed && !_.isUndefined(selectedProjectLinkProperty.get())) {
        if(!_.isUndefined(selection) && canItBeAddToSelection(selection.projectLinkData)){
          var clickedIds = projectCollection.getMultiSelectIds(selection.projectLinkData.linkId);
          var previouslySelectedIds = _.map(selectedProjectLinkProperty.get(), function(selected){
            return selected.linkId;
          });
          if(_.contains(previouslySelectedIds, selection.projectLinkData.linkId)){
            previouslySelectedIds = _.without(previouslySelectedIds, clickedIds);
          } else {
            previouslySelectedIds = _.union(previouslySelectedIds, clickedIds);
          }
          selectedProjectLinkProperty.openShift(previouslySelectedIds);
        }
        highlightFeatures();
      } else {
        selectedProjectLinkProperty.clean();
        $('.wrapper').remove();
        $('#actionButtons').html('<button class="show-changes btn btn-block btn-show-changes">Avaa projektin yhteenvetotaulukko</button><button disabled id ="send-button" class="send btn btn-block btn-send">Tee tieosoitteenmuutosilmoitus</button>');
        if (!_.isUndefined(selection))
          selectedProjectLinkProperty.open(selection.projectLinkData.linkId, true);
        else selectedProjectLinkProperty.cleanIds();
      }
    };

    var selectDoubleClick = new ol.interaction.Select({
      layer: [vectorLayer, suravageRoadProjectLayer],
      condition: function(mapBrowserEvent){
        return (ol.events.condition.doubleClick(mapBrowserEvent) && ol.events.condition.shiftKeyOnly(mapBrowserEvent)) || ol.events.condition.doubleClick(mapBrowserEvent);
      },
      style: function(feature, resolution) {
        if(projectLinkStatusIn(feature.projectLinkData, [notHandledStatus,newRoadAddressStatus, terminatedStatus, unchangedStatus, transferedStatus, numberingStatus]) ||  feature.projectLinkData.roadLinkSource === 3) {
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 255, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 255, 0, 0.95)',
              width: 8
            })
          });
        } else if(feature.projectLinkData.anomaly === noAddressAnomaly && feature.projectLinkData.status === unknownStatus) {
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 255, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 255, 0, 0.95)',
              width: 8
            })
          });
        } else if (feature.projectLinkData.roadClass === 99){
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 255, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 255, 0, 0.95)',
              width: 8
            })
          });
        }
      }
    });

    selectDoubleClick.set('name','selectDoubleClickInteractionPLL');

    selectDoubleClick.on('select',function(event) {
      var shiftPressed = event.mapBrowserEvent.originalEvent.shiftKey;
      var selection = _.find(event.selected, function (selectionTarget) {
        return (!_.isUndefined(selectionTarget.projectLinkData) && (
          projectLinkStatusIn(selectionTarget.projectLinkData,[notHandledStatus, newRoadAddressStatus, terminatedStatus, unchangedStatus, transferedStatus, numberingStatus])||
          (selectionTarget.projectLinkData.anomaly==noAddressAnomaly && selectionTarget.projectLinkData.roadLinkType!=floatingRoadLinkType) ||
          selectionTarget.projectLinkData.roadClass === 99 || selectionTarget.projectLinkData.roadLinkSource === 3)
        );
      });
      if(isNotEditingData){
        showDoubleClickChanges(shiftPressed, selection);
      } else {
        var selectedFeatures = event.deselected.concat(selectSingleClick.getFeatures().getArray());
        clearHighlights();
        addFeaturesToSelection(selectedFeatures);
        fireDeselectionConfirmation(shiftPressed, selection);
      }
    });

    var showDoubleClickChanges = function(shiftPressed, selection){
      if (shiftPressed && !_.isUndefined(selectedProjectLinkProperty.get())) {
        if(!_.isUndefined(selection) && canItBeAddToSelection(selection.projectLinkData)){
          var selectedLinkIds = _.map(selectedProjectLinkProperty.get(), function(selected){
            return selected.linkId;
          });
          if(_.contains(selectedLinkIds, selection.projectLinkData.linkId)){
            selectedLinkIds = _.without(selectedLinkIds, selection.projectLinkData.linkId);
          } else {
            selectedLinkIds = selectedLinkIds.concat(selection.projectLinkData.linkId);
          }
          selectedProjectLinkProperty.openShift(selectedLinkIds);
        }
        highlightFeatures();
      } else {
        selectedProjectLinkProperty.clean();
        if (!_.isUndefined(selection)) {
          selectedProjectLinkProperty.open(selection.projectLinkData.linkId);
        if(selection.projectLinkData.roadLinkSource==3)
          decorateSelection(selection);
        }
        else selectedProjectLinkProperty.cleanIds();
      }
    };

    var offsetBySideCode = function (suravageLink) {
      return GeometryUtils.offsetBySideCode(applicationModel.zoom.level, suravageLink);
    };

    var decorateSelection = function (suravageLink) {
      drawIndicators(_.map(_.cloneDeep(suravageLink), offsetBySideCode));
    };

    var drawIndicators = function(links) {
      var features = [];

      var markerContainer = function(link, position) {
        var anchor, offset;
        // if(assetLabel){
        //   anchor = assetLabel.getMarkerAnchor(uiState.zoomLevel);
        //   offset = assetLabel.getMarkerOffset(uiState.zoomLevel);
        // }

        var imageSettings = {src: 'images/center-marker2.svg'};
        // if(anchor)
        //   imageSettings = _.merge(imageSettings, { anchor : anchor });

        var textSettings = {
          text : link.marker,
          fill: new ol.style.Fill({
            color: '#ffffff'
          }),
          font : '12px sans-serif'
        };
        // if(offset)
        //   textSettings = _.merge(textSettings, {offsetX : offset[0], offsetY : offset[1]});

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

      var indicators = function() {
        return indicatorsForSplit();
      };
      indicators();
      selectToolControl.addNewFeature(features);
    };

    var canItBeAddToSelection = function(selectionData) {
      var currentlySelectedSample = _.first(selectedProjectLinkProperty.get());
      return selectionData.roadNumber === currentlySelectedSample.roadNumber &&
        selectionData.roadPartNumber === currentlySelectedSample.roadPartNumber &&
        selectionData.trackCode === currentlySelectedSample.trackCode;
    };

    var revertSelectedChanges = function() {
      if(projectCollection.isDirty()) {
        projectCollection.revertLinkStatus();
        projectCollection.setDirty([]);
        eventbus.trigger('roadAddress:projectLinksEdited');
      }
    };

    var clearHighlights = function(){
      if(selectDoubleClick.getFeatures().getLength() !== 0){
        selectDoubleClick.getFeatures().clear();
      }
      if(selectSingleClick.getFeatures().getLength() !== 0){
        selectSingleClick.getFeatures().clear();
      }
    };

    var clearLayers = function(){
      calibrationPointLayer.getSource().clear();
      directionMarkerLayer.getSource().clear();
      suravageProjectDirectionMarkerLayer.getSource().clear();
      suravageRoadProjectLayer.getSource().clear();
    };

    var highlightFeatures = function() {
      clearHighlights();
      var featuresToHighlight = [];
      var suravageFeaturesToHighlight = [];
      _.each(vectorLayer.getSource().getFeatures(), function(feature) {
        var canIHighlight = !_.isUndefined(feature.projectLinkData.linkId) ?
          selectedProjectLinkProperty.isSelected(feature.projectLinkData.linkId) : false;
        if(canIHighlight){
          featuresToHighlight.push(feature);
        }
      });
      if(featuresToHighlight.length !== 0) {
        addFeaturesToSelection(featuresToHighlight);
      } else {
        _.each(suravageRoadProjectLayer.getSource().getFeatures(), function(feature) {
          var canIHighlight = !_.isUndefined(feature.projectLinkData.linkId) ?
            selectedProjectLinkProperty.isSelected(feature.projectLinkData.linkId) : false;
          if(canIHighlight){
            suravageFeaturesToHighlight.push(feature);
          }
        });
        if(suravageFeaturesToHighlight.length !== 0){
          addFeaturesToSelection(suravageFeaturesToHighlight);
        }

        var suravageResult = _.filter(suravageProjectDirectionMarkerLayer.getSource().getFeatures(), function(item) {
          return _.find(suravageFeaturesToHighlight, function(sf) {
            return sf.projectLinkData.linkId === item.roadLinkData.linkId;
          });
        });

        _.each(suravageResult, function(featureMarker){
          selectSingleClick.getFeatures().push(featureMarker);
        });
      }

      var result = _.filter(directionMarkerLayer.getSource().getFeatures(), function(item) {
        return _.find(featuresToHighlight, {linkId: item.id});
      });

      _.each(result, function(featureMarker){
        selectSingleClick.getFeatures().push(featureMarker);
      });
    };

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

    eventbus.on('projectLink:clicked', function() {
      highlightFeatures();
    });

    eventbus.on('layer:selected', function(layer) {
      if (layer === 'roadAddressProject') {
        vectorLayer.setVisible(true);
        calibrationPointLayer.setVisible(true);
      } else {
        clearHighlights();
        var featuresToHighlight = [];
        vectorLayer.setVisible(false);
        calibrationPointLayer.setVisible(false);
        eventbus.trigger('roadLinks:fetched');
      }
    });

    var zoomDoubleClickListener = function(event) {
      _.defer(function(){
        if(!event.shiftKey && selectedProjectLinkProperty.get().length === 0 &&
          applicationModel.getSelectedLayer() == 'roadAddressProject' && map.getView().getZoom() <= 13){
          map.getView().setZoom(map.getView().getZoom()+1);
        }
      });
    };
    //This will control the double click zoom when there is no selection that activates
    map.on('dblclick', zoomDoubleClickListener);

    var infoContainer = document.getElementById('popup');
    var infoContent = document.getElementById('popup-content');

    var overlay = new ol.Overlay(({
      element: infoContainer
    }));

    map.addOverlay(overlay);

    //Listen pointerMove and get pixel for displaying roadAddress feature info
    eventbus.on('map:mouseMoved', function (event, pixel) {
      if (event.dragging) {
        return;
      }
      displayRoadAddressInfo(event, pixel);
    });

    var displayRoadAddressInfo = function(event, pixel) {

      var featureAtPixel = map.forEachFeatureAtPixel(pixel, function (feature, vectorLayer) {
        return feature;
      });

      //Ignore if target feature is marker
      if(isDefined(featureAtPixel) && (isDefined(featureAtPixel.roadLinkData) || isDefined(featureAtPixel.projectLinkData))) {
        var roadData;
        var coordinate = map.getEventCoordinate(event.originalEvent);

        if(isDefined(featureAtPixel.projectLinkData)) {
          roadData = featureAtPixel.projectLinkData;
        }
        else {
          roadData = featureAtPixel.roadLinkData;
        }
        //TODO roadData !== null is there for test having no info ready (race condition where hower often looses) should be somehow resolved
        if (infoContent !== null) {
          if (roadData !== null || (roadData.roadNumber !== 0 && roadData.roadPartNumber !== 0 && roadData.roadPartNumber !== 99 )) {
            infoContent.innerHTML = '<p>' +
              'Tienumero: ' + roadData.roadNumber + '<br>' +
              'Tieosanumero: ' + roadData.roadPartNumber + '<br>' +
              'Ajorata: ' + roadData.trackCode + '<br>' +
              'AET: ' + roadData.startAddressM + '<br>' +
              'LET: ' + roadData.endAddressM + '<br>' + '</p>';
          } else {
            infoContent.innerHTML = '<p>' +
              'Tuntematon tien segmentti' + '</p>'; // road with no address
          }
        }

        overlay.setPosition(coordinate);

      } else {
        overlay.setPosition(undefined);
      }
    };

    var isDefined=function(variable) {
      return !_.isUndefined(variable);
    };

    //Add defined interactions to the map.
    map.addInteraction(selectSingleClick);
    map.addInteraction(selectDoubleClick);

    var mapMovedHandler = function(mapState) {
      var projectId = _.isUndefined(projectCollection.getCurrentProject()) ? undefined : projectCollection.getCurrentProject().project.id;
      if (mapState.zoom !== currentZoom) {
        currentZoom = mapState.zoom;
      }
      if (mapState.zoom < minimumContentZoomLevel()) {
        vectorSource.clear();
        eventbus.trigger('map:clearLayers');
      } else if (mapState.selectedLayer == layerName){
        projectCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1, projectId, projectCollection.getPublishableStatus());
        handleRoadsVisibility();
      }
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
     * This will remove all the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick
     */

    var removeSelectInteractions = function() {
      map.removeInteraction(selectDoubleClick);
      map.removeInteraction(selectSingleClick);
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

    var handleRoadsVisibility = function() {
      if (_.isObject(vectorLayer)) {
        vectorLayer.setVisible(map.getView().getZoom() >= minimumContentZoomLevel());
      }
    };

    var minimumContentZoomLevel = function() {
      if (!_.isUndefined(layerMinContentZoomLevels[applicationModel.getSelectedLayer()])) {
        return layerMinContentZoomLevels[applicationModel.getSelectedLayer()];
      }
      return zoomlevels.minZoomForRoadLinks;
    };

    var loadFeatures = function (features) {
      vectorSource.addFeatures(features);
    };

    var show = function(map) {
      vectorLayer.setVisible(true);
    };

    var hideLayer = function() {
      me.stop();
      me.hide();
    };

    var clearProjectLinkLayer = function() {
      vectorLayer.getSource().clear();
    };

    var selectToolControl = new SelectToolControl(applicationModel, vectorLayer, map, {
      // style: function (feature) {
      //   return style.browsingStyle.getStyle(feature, {zoomLevel: uiState.zoomLevel});
      // },
      // onInteractionEnd: onInteractionEnd,
      // onSelect: OnSelect,
      filterGeometry: function (feature) {
        return true;
      }
    });



    var SuravageCutter = function(vectorLayer, collection, eventListener) {
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

      var remove = function() {
        selectToolControl.removeFeatures(function(feature) {
          return feature.getProperties().type === 'cutter';
        });
        scissorFeatures = [];
      };

      var self = this;

      var clickHandler = function(evt) {
        if (applicationModel.getSelectedTool() === 'Cut') {
          // if (collection.isDirty()) {
          //   me.displayConfirmMessage();
          // } else {
            self.cut(evt);
          // }
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
          if (applicationModel.getSelectedTool() === 'Cut' && !selectedProjectLinkProperty.isDirty()) {
            self.updateByPosition(event.coordinate);
          }
        });
      };

      var isWithinCutThreshold = function(suravageLink) {
        return suravageLink !== undefined && suravageLink < CUT_THRESHOLD;
      };

      var findNearestSuravageLink = function(point) {
        return _.chain(vectorSource.getFeatures())
            .filter(function(feature) {
              return feature.getGeometry() instanceof ol.geom.LineString;
            })
            // .reject(function(feature) {
            //   var properties = feature.getProperties();
            //   return _.has(properties, 'generatedId');
            //   // && _.flatten(projectCollection.getGroup(properties.id)).length > 0;
            // })
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
        var closestSuravageLink = findNearestSuravageLink(mousePoint);
        if (!closestSuravageLink) {
          return;
        }
        if (isWithinCutThreshold(closestSuravageLink.distance)) {
          moveTo(closestSuravageLink.point[0], closestSuravageLink.point[1]);
        } else {
          remove();
        }
      };

      this.cut = function(mousePoint) {
        var pointsToLineString = function(points) {
          var coordPoints = _.map(points, function(point) { return [point.x, point.y]; });
          return new ol.geom.LineString(coordPoints);
        };

        var calculateSplitProperties = function(nearestSuravage, point) {
          var lineString = pointsToLineString(nearestSuravage.points);
          // var lineString = nearestSuravage.geometry;
          // var startMeasureOffset = nearestSuravage.startMeasure;
          var splitMeasure = GeometryUtils.calculateMeasureAtPoint(lineString, point);
          var splitVertices = GeometryUtils.splitByPoint(lineString, point);
          return _.merge({ splitMeasure: splitMeasure }, splitVertices);
        };

        var nearest = findNearestSuravageLink([mousePoint.x, mousePoint.y]);
        //nearest is the selected one for this test
        // var nearest = _.first(_.filter(vectorSource.getFeatures(), function(feature){
        //   return feature.linkId == 500006335;
        // }));



        // if (!isWithinCutThreshold(nearest.distance)) {
        //   return;
        // }

        var nearestSuravage = nearest.feature.projectLinkData;
        var splitProperties = calculateSplitProperties(nearestSuravage, mousePoint);
        selectedProjectLinkProperty.splitSuravageLink(nearestSuravage, splitProperties);

        remove();
      };
    };

    var uiState = { zoomLevel: 9 };

    // var OnSelect = function(evt) {
    //   if(evt.selected.length !== 0) {
    //     var feature = evt.selected[0];
    //     var properties = feature.getProperties();
    //     verifyClickEvent(properties, evt);
    //   }else{
    //     // if (selectedSpeedLimit.exists()) {
    //     //   selectToolControl.clear();
    //     //   selectedSpeedLimit.close();
    //     // }
    //   }
    // };

    // var verifyClickEvent = function(properties, evt){
    //   var singleLinkSelect = evt.mapBrowserEvent.type === 'dblclick';
    //   selectedSpeedLimit.open(properties, singleLinkSelect);
    //   // highlightMultipleLinearAssetFeatures();
    // };

    // function onInteractionEnd(speedLimits) {
    //   if (selectedSpeedLimit.isDirty()) {
    //     displayConfirmMessage();
    //   } else {
    //     if (speedLimits.length > 0) {
    //       selectedSpeedLimit.close();
    //       showDialog(speedLimits);
    //     }
    //   }
    // }


    var projectLinkStatusIn = function(projectLink, possibleStatus){
      if(!_.isUndefined(possibleStatus) && !_.isUndefined(projectLink) )
        return _.contains(possibleStatus, projectLink.status);
      else return false;
    };

    var suravageCutter = new SuravageCutter(vectorLayer, projectCollection, me.eventListener);

    var changeTool = function(tool) {
      if (tool === 'Cut') {
        selectToolControl.deactivate();
        suravageCutter.activate();
      } else if (tool === 'Select') {
        suravageCutter.deactivate();
        selectToolControl.activate();
      }
    };

    eventbus.on('projectLink:projectLinksCreateSuccess', function () {
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1, undefined, projectCollection.getPublishableStatus());
    });

    eventbus.on('changeProjectDirection:clicked', function () {
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1, undefined, projectCollection.getPublishableStatus());
    });

    eventbus.on('projectLink:revertedChanges', function () {
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1, undefined, projectCollection.getPublishableStatus());
    });

    var redraw = function(){
      var ids = {};
      _.each(selectedProjectLinkProperty.get(), function (sel) { ids[sel.linkId] = true; });

      var editedLinks = _.map(projectCollection.getDirty(), function(editedLink) {return editedLink;});

      var separated = _.partition(projectCollection.getAll(), function(projectRoad){
        return projectRoad.roadLinkSource === 3;
      });
      var toBeTerminated = _.partition(editedLinks, function(link){
        return link.status === terminatedStatus;
      });
      var toBeUnchanged = _.partition(editedLinks, function(link){
        return link.status === unchangedStatus;
      });

      var toBeTerminatedLinkIds = _.pluck(toBeTerminated[0], 'id');
      var toBeUnchangedLinkIds = _.pluck(toBeUnchanged[0], 'id');

      var suravageProjectRoads = separated[0];
      var suravageFeatures = [];
      suravageProjectDirectionMarkerLayer.getSource().clear();

      _.map(suravageProjectRoads, function(projectLink) {
        var points = _.map(projectLink.points, function (point) {
          return [point.x, point.y];
        });
        var feature = new ol.Feature({
          geometry: new ol.geom.LineString(points)
        });
        feature.projectLinkData = projectLink;
        suravageFeatures.push(feature);
      });

      cachedMarker = new LinkPropertyMarker(selectedProjectLinkProperty);
      var suravageDirectionRoadMarker = _.filter(suravageProjectRoads, function(projectLink) {
        return projectLink.roadLinkType !== floatingRoadLinkType && projectLink.anomaly !== noAddressAnomaly && projectLink.anomaly !== geometryChangedAnomaly && (projectLink.sideCode === againstDigitizing || projectLink.sideCode === towardsDigitizing);
      });

      var suravageFeaturesToRemove = [];
      _.each(selectSingleClick.getFeatures().getArray(), function (feature) {
        if(feature.getProperties().type && feature.getProperties().type === "marker")
          suravageFeaturesToRemove.push(feature);
      });
      _.each(suravageFeaturesToRemove, function(feature){
        selectSingleClick.getFeatures().remove(feature);
      });

      _.each(suravageDirectionRoadMarker, function(directionLink) {
        var marker = cachedMarker.createMarker(directionLink);
        if(map.getView().getZoom() > zoomlevels.minZoomForDirectionalMarkers)
          suravageProjectDirectionMarkerLayer.getSource().addFeature(marker);
        selectSingleClick.getFeatures().push(marker);
      });

      suravageRoadProjectLayer.getSource().addFeatures(suravageFeatures);

      var projectLinks = separated[1];
      var features = [];
      _.map(projectLinks, function(projectLink) {
        var points = _.map(projectLink.points, function (point) {
          return [point.x, point.y];
        });
        var feature = new ol.Feature({
          geometry: new ol.geom.LineString(points)
        });
        feature.projectLinkData = projectLink;
        feature.linkId = projectLink.linkId;
        features.push(feature);
      });

      directionMarkerLayer.getSource().clear();
      cachedMarker = new LinkPropertyMarker(selectedProjectLinkProperty);
      var directionRoadMarker = _.filter(projectLinks, function(projectLink) {
        return projectLink.roadLinkType !== floatingRoadLinkType && projectLink.anomaly !== noAddressAnomaly && projectLink.anomaly !== geometryChangedAnomaly && (projectLink.sideCode === againstDigitizing || projectLink.sideCode === towardsDigitizing);
      });

      var featuresToRemove = [];
      _.each(selectSingleClick.getFeatures().getArray(), function (feature) {
        if(feature.getProperties().type && feature.getProperties().type === "marker")
          featuresToRemove.push(feature);
      });
      _.each(featuresToRemove, function(feature){
        selectSingleClick.getFeatures().remove(feature);
      });
      _.each(directionRoadMarker, function(directionLink) {
        var marker = cachedMarker.createMarker(directionLink);
        if(map.getView().getZoom() > zoomlevels.minZoomForDirectionalMarkers)
          directionMarkerLayer.getSource().addFeature(marker);
        selectSingleClick.getFeatures().push(marker);
      });

      calibrationPointLayer.getSource().clear();
      var actualPoints = me.drawCalibrationMarkers(calibrationPointLayer.source, projectLinks);
      _.each(actualPoints, function (actualPoint) {
        var calMarker = new CalibrationPoint(actualPoint.point);
        calibrationPointLayer.getSource().addFeature(calMarker.getMarker(true));
      });

      calibrationPointLayer.setZIndex(standardZIndex + 2);
      var partitioned = _.partition(features, function(feature) {
        return (!_.isUndefined(feature.projectLinkData.linkId) && _.contains(_.pluck(editedLinks, 'id'), feature.projectLinkData.linkId));
      });
      features = [];
      _.each(partitioned[0], function(feature) {
        var editedLink = (!_.isUndefined(feature.projectLinkData.linkId) && _.contains(_.pluck(editedLinks, 'id'), feature.projectLinkData.linkId));
        if(editedLink) {
          if (_.contains(toBeTerminatedLinkIds, feature.projectLinkData.linkId)) {
            feature.projectLinkData.status = terminatedStatus;
            feature.setStyle(new ol.style.Style({
              fill: new ol.style.Fill({
                color: 'rgba(56, 56, 54, 1)'
              }),
              stroke: new ol.style.Stroke({
                color: 'rgba(56, 56, 54, 1)',
                width: 8
              })
            }));
            features.push(feature);
          }
        }
      });
      if(features.length !== 0)
        addFeaturesToSelection(features);
      features = features.concat(partitioned[1]);
      vectorLayer.getSource().clear(true); // Otherwise we get multiple copies: TODO: clear only inside bbox
      vectorLayer.getSource().addFeatures(features);
      vectorLayer.changed();
    };

    eventbus.on('tool:changed', changeTool);

    eventbus.on('roadAddressProject:openProject', function(projectSelected) {
      this.project = projectSelected;
      eventbus.trigger('layer:enableButtons', false);
      eventbus.trigger('editMode:setReadOnly', false);
      eventbus.trigger('roadAddressProject:selected', projectSelected.id, layerName, applicationModel.getSelectedLayer());
    });

    eventbus.on('roadAddressProject:selected', function(projId) {
      eventbus.once('roadAddressProject:projectFetched', function(projectInfo) {
        projectCollection.fetch(map.getView().calculateExtent(map.getSize()),map.getView().getZoom(), projectInfo.id, projectInfo.publishable);
      });
      projectCollection.getProjectsWithLinksById(projId);
    });

    eventbus.on('roadAddressProject:fetched', function(newSelection) {
      applicationModel.removeSpinner();
      redraw();
      _.defer(function(){
        highlightFeatures();
      });
    });

    eventbus.on('roadAddress:projectLinksEdited',function(){
      redraw();
    });

    eventbus.on('roadAddressProject:projectLinkSaved',function(projectId, isPublishable){
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()),map.getView().getZoom(), projectId, isPublishable);
    });

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('layer:selected', function(layer, previouslySelectedLayer) {
      //TODO: there might be room for improvement on this, but I am not seeing it
      if (layer !== 'roadAddressProject') {
        deactivateSelectInteractions(true);
        removeSelectInteractions();
      }
      else {
        activateSelectInteractions(true);
        addSelectInteractions();
      }
      if (previouslySelectedLayer === 'roadAddressProject') {
        clearProjectLinkLayer();
        clearLayers();
        hideLayer();
        removeSelectInteractions();
      }
    });

    eventbus.on('roadAddressProject:deselectFeaturesSelected', function(){
      clearHighlights();
    });

    eventbus.on('roadAddressProject:clearAndDisableInteractions',function(){
      clearHighlights();
      removeSelectInteractions();
    });

    eventbus.on('roadAddressProject:enableInteractions',function(){
      addSelectInteractions();
    });

    eventbus.on('roadAddressProject:clearOnClose', function(){
      clearHighlights();
      clearLayers();
      clearProjectLinkLayer();
    });

    eventbus.on('map:clearLayers', clearLayers);

    eventbus.on('suravageProjectRoads:toggleVisibility', function(visibility) {
      suravageRoadProjectLayer.setVisible(visibility);
      suravageProjectDirectionMarkerLayer.setVisible(visibility);
    });

    eventbus.on('roadAddressProject:toggleEditingRoad',function(notEditingData){
      isNotEditingData = notEditingData;
    });

    eventbus.on('roadAddressProject:deactivateAllSelections',function(){
      deactivateSelectInteractions(true);
    });

    eventbus.on('roadAddressProject:startAllInteractions',function(){
      activateSelectInteractions(true);
    });

    vectorLayer.setVisible(true);
    suravageRoadProjectLayer.setVisible(true);
    calibrationPointLayer.setVisible(true);
    directionMarkerLayer.setVisible(true);
    suravageProjectDirectionMarkerLayer.setVisible(true);
    map.addLayer(vectorLayer);
    map.addLayer(suravageRoadProjectLayer);
    map.addLayer(calibrationPointLayer);
    map.addLayer(directionMarkerLayer);
    map.addLayer(suravageProjectDirectionMarkerLayer);
    return {
      show: show,
      hide: hideLayer,
      clearHighlights: clearHighlights
    };
  };

})(this);