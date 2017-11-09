(function (root) {
  root.ProjectLinkLayer = function (map, projectCollection, selectedProjectLinkProperty, roadLayer) {
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
    var LinkStatus = LinkValues.LinkStatus;
    var Anomaly = LinkValues.Anomaly;
    var SideCode = LinkValues.SideCode;
    var RoadLinkType = LinkValues.RoadLinkType;
    var LinkGeomSource = LinkValues.LinkGeomSource;
    var RoadClass = LinkValues.RoadClass;

    var isNotEditingData = true;
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var styler = new Styler();
    var projectLinkStyler = new ProjectLinkStyler();

    var vectorSource = new ol.source.Vector({
      loader: function (extent, resolution, projection) {
        var zoom = Math.log(1024 / resolution) / Math.log(2);

        var nonSuravageRoads = _.filter(projectCollection.getAll(), function (projectRoad) {
          return projectRoad.roadLinkSource !== LinkGeomSource.SuravageLinkInterface.value;
        });
        var features = _.map(nonSuravageRoads, function (projectLink) {
          var points = _.map(projectLink.points, function (point) {
            return [point.x, point.y];
          });
          var feature = new ol.Feature({
            geometry: new ol.geom.LineString(points)
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
      name: 'suravageRoadProjectLayer',
      style: function (feature) {
        return projectLinkStyler.getProjectLinkStyle().getStyle( feature.projectLinkData, {zoomLevel: currentZoom});
      }
    });

    var suravageProjectDirectionMarkerLayer = new ol.layer.Vector({
      source: suravageProjectDirectionMarkerVector,
      name: 'suravageProjectDirectionMarkerLayer'
    });

    vectorLayer = new ol.layer.Vector({
      source: vectorSource,
      name: layerName,
      style: function(feature) {
        var status = feature.projectLinkData.status;
        if (status === LinkStatus.NotHandled.value || status === LinkStatus.Terminated.value || status  === LinkStatus.New.value || status == LinkStatus.Transfer.value || status === LinkStatus.Unchanged.value || status == LinkStatus.Numbering.value) {
          return projectLinkStyler.getProjectLinkStyle().getStyle( feature.projectLinkData, {zoomLevel: currentZoom});
        } else {
          return styler.generateStyleByFeature(feature.projectLinkData, currentZoom);
        }
    }
    });

    var showChangesAndSendButton = function () {
      selectedProjectLinkProperty.clean();
      $('.wrapper').remove();
      $('#actionButtons').html('<button class="show-changes btn btn-block btn-show-changes">Avaa projektin yhteenvetotaulukko</button><button disabled id ="send-button" class="send btn btn-block btn-send">Tee tieosoitteenmuutosilmoitus</button>');
    };

    var fireDeselectionConfirmation = function (shiftPressed, selection, clickType) {
      new GenericConfirmPopup('Haluatko poistaa tien valinnan ja hylätä muutokset?', {
        successCallback: function () {
          eventbus.trigger('roadAddressProject:discardChanges');
          isNotEditingData = true;
          clearHighlights();
          if (!_.isUndefined(selection)) {
            if (clickType === 'single')
              showSingleClickChanges(shiftPressed, selection);
            else
              showDoubleClickChanges(shiftPressed, selection);
          } else {
            showChangesAndSendButton();
          }
        },
        closeCallback: function () {
          isNotEditingData = false;
        }
      });
    };

    var possibleStatusForSelection = [LinkStatus.NotHandled.value, LinkStatus.New.value, LinkStatus.Terminated.value, LinkStatus.Transfer.value, LinkStatus.Unchanged.value, LinkStatus.Numbering.value];

    var selectSingleClick = new ol.interaction.Select({
      layer: [vectorLayer, suravageRoadProjectLayer],
      condition: ol.events.condition.singleClick,
      style: function (feature) {
        if(projectLinkStatusIn(feature.projectLinkData, possibleStatusForSelection) || feature.projectLinkData.roadClass === RoadClass.NoClass.value || feature.projectLinkData.roadLinkSource == LinkGeomSource.SuravageLinkInterface.value) {
         return projectLinkStyler.getSelectionLinkStyle().getStyle( feature.projectLinkData, {zoomLevel: currentZoom});
        }
      }
    });

    selectSingleClick.set('name', 'selectSingleClickInteractionPLL');

    selectSingleClick.on('select', function (event) {
      var shiftPressed = event.mapBrowserEvent !== undefined ?
        event.mapBrowserEvent.originalEvent.shiftKey : false;
      removeCutterMarkers();
      var selection = _.find(event.selected.concat(selectSingleClick.getFeatures().getArray()), function (selectionTarget) {
        return (applicationModel.getSelectedTool() != 'Cut' && !_.isUndefined(selectionTarget.projectLinkData) && (
          projectLinkStatusIn(selectionTarget.projectLinkData, possibleStatusForSelection) ||
          (selectionTarget.projectLinkData.anomaly == Anomaly.NoAddressGiven.value && selectionTarget.projectLinkData.roadLinkType != RoadLinkType.FloatingRoadLinkType.value) ||
          selectionTarget.projectLinkData.roadClass === RoadClass.NoClass.value || selectionTarget.projectLinkData.roadLinkSource === LinkGeomSource.SuravageLinkInterface.value )
        );
      });
      if (isNotEditingData) {
        showSingleClickChanges(shiftPressed, selection);
      } else {
        var selectedFeatures = event.deselected.concat(selectDoubleClick.getFeatures().getArray());
        clearHighlights();
        addFeaturesToSelection(selectedFeatures);
        fireDeselectionConfirmation(shiftPressed, selection, 'single');
      }
    });

    var showSingleClickChanges = function (shiftPressed, selection) {
      if(applicationModel.getSelectedTool() == 'Cut')
        return;
      if (shiftPressed && !_.isUndefined(selectedProjectLinkProperty.get())) {
        if (!_.isUndefined(selection) && canItBeAddToSelection(selection.projectLinkData)) {
          var clickedIds = projectCollection.getMultiSelectIds(selection.projectLinkData.linkId);
          var previouslySelectedIds = _.map(selectedProjectLinkProperty.get(), function (selected) {
            return selected.linkId;
          });
          if (_.contains(previouslySelectedIds, selection.projectLinkData.linkId)) {
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
        if (!_.isUndefined(selection) && !selectedProjectLinkProperty.isDirty()){
          if(!_.isUndefined(selection.projectLinkData.connectedLinkId)){
            selectedProjectLinkProperty.openSplit(selection.projectLinkData.linkId, true);
          } else {
            selectedProjectLinkProperty.open(selection.projectLinkData.linkId, true);
          }
        }
        else selectedProjectLinkProperty.cleanIds();
      }
    };

    var selectDoubleClick = new ol.interaction.Select({
      layer: [vectorLayer, suravageRoadProjectLayer],
      condition: ol.events.condition.doubleClick,
      style: function(feature) {
        if(projectLinkStatusIn(feature.projectLinkData, possibleStatusForSelection) || feature.projectLinkData.roadClass === RoadClass.NoClass.value || feature.projectLinkData.roadLinkSource == LinkGeomSource.SuravageLinkInterface.value) {
          return projectLinkStyler.getSelectionLinkStyle().getStyle( feature.projectLinkData, {zoomLevel: currentZoom});
        }
      }
    });

    selectDoubleClick.set('name', 'selectDoubleClickInteractionPLL');

    selectDoubleClick.on('select', function (event) {
      var shiftPressed = event.mapBrowserEvent.originalEvent.shiftKey;
      var selection = _.find(event.selected, function (selectionTarget) {
        return (applicationModel.getSelectedTool() != 'Cut' && !_.isUndefined(selectionTarget.projectLinkData) && (
          projectLinkStatusIn(selectionTarget.projectLinkData, possibleStatusForSelection) ||
          (selectionTarget.projectLinkData.anomaly == Anomaly.NoAddressGiven.value && selectionTarget.projectLinkData.roadLinkType != RoadLinkType.FloatingRoadLinkType.value) ||
          selectionTarget.projectLinkData.roadClass === RoadClass.NoClass.value || selectionTarget.projectLinkData.roadLinkSource === LinkGeomSource.SuravageLinkInterface.value)
        );
      });
      if (isNotEditingData) {
        showDoubleClickChanges(shiftPressed, selection);
      } else {
        var selectedFeatures = event.deselected.concat(selectSingleClick.getFeatures().getArray());
        clearHighlights();
        addFeaturesToSelection(selectedFeatures);
        fireDeselectionConfirmation(shiftPressed, selection, 'double');
      }
    });

    var showDoubleClickChanges = function (shiftPressed, selection) {
      if (shiftPressed && !_.isUndefined(selectedProjectLinkProperty.get())) {
        if (!_.isUndefined(selection) && canItBeAddToSelection(selection.projectLinkData)) {
          var selectedLinkIds = _.map(selectedProjectLinkProperty.get(), function (selected) {
            return selected.linkId;
          });
          if (_.contains(selectedLinkIds, selection.projectLinkData.linkId)) {
            selectedLinkIds = _.without(selectedLinkIds, selection.projectLinkData.linkId);
          } else {
            selectedLinkIds = selectedLinkIds.concat(selection.projectLinkData.linkId);
          }
          selectedProjectLinkProperty.openShift(selectedLinkIds);
        }
        highlightFeatures();
      } else {
        selectedProjectLinkProperty.clean();
        if (!_.isUndefined(selection) && !selectedProjectLinkProperty.isDirty()){
          if(!_.isUndefined(selection.projectLinkData.connectedLinkId)){
            selectedProjectLinkProperty.openSplit(selection.projectLinkData.linkId, true);
          } else {
            selectedProjectLinkProperty.open(selection.projectLinkData.linkId);
          }
        }
        else selectedProjectLinkProperty.cleanIds();
      }
    };

    var drawIndicators = function(links) {
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
          text : new ol.style.Text(textSettings),
          zIndex: 11
        });
        var marker = new ol.Feature({
          geometry : new ol.geom.Point([position.x, position.y]),
          type: 'cutter'
        });
        marker.setStyle(style);
        features.push(marker);
      };

      var indicatorsForSplit = function() {
        return mapOverLinkMiddlePoints(links, function(link, middlePoint) {
          markerContainer(link, middlePoint);
        });
      };

      var indicators = function() {
        return indicatorsForSplit();
      };
      indicators();
      addFeaturesToSelection(features);
    };

    var canItBeAddToSelection = function(selectionData) {
      var currentlySelectedSample = _.first(selectedProjectLinkProperty.get());
      return selectionData.roadNumber === currentlySelectedSample.roadNumber &&
        selectionData.roadPartNumber === currentlySelectedSample.roadPartNumber &&
        selectionData.trackCode === currentlySelectedSample.trackCode;
    };

    var clearHighlights = function(){
      if(applicationModel.getSelectedTool() == 'Cut'){
        if(selectDoubleClick.getFeatures().getLength() !== 0){
          selectDoubleClick.getFeatures().clear();
        }
        if(selectSingleClick.getFeatures().getLength() !== 0){
          selectSingleClick.getFeatures().clear();
        }
      } else {
        if(selectDoubleClick.getFeatures().getLength() !== 0){
          selectDoubleClick.getFeatures().clear();
        }
        if(selectSingleClick.getFeatures().getLength() !== 0){
          selectSingleClick.getFeatures().clear();
        }
      }
    };

    var clearLayers = function () {
      calibrationPointLayer.getSource().clear();
      directionMarkerLayer.getSource().clear();
      suravageProjectDirectionMarkerLayer.getSource().clear();
      suravageRoadProjectLayer.getSource().clear();
    };

    var highlightFeatures = function () {
      clearHighlights();
      var featuresToHighlight = [];
      var suravageFeaturesToHighlight = [];
      _.each(vectorLayer.getSource().getFeatures(), function (feature) {
        var canIHighlight = ((!_.isUndefined(feature.projectLinkData.linkId) &&
        _.isUndefined(feature.projectLinkData.connectedLinkId)) ?
          selectedProjectLinkProperty.isSelected(feature.projectLinkData.linkId) : false);
        if (canIHighlight) {
          featuresToHighlight.push(feature);
        }
      });
      if (featuresToHighlight.length !== 0) {
        addFeaturesToSelection(featuresToHighlight);
      } else {
        _.each(suravageRoadProjectLayer.getSource().getFeatures(), function(feature) {
          var canIHighlight = (!_.isUndefined(feature.projectLinkData) && !_.isUndefined(feature.projectLinkData.linkId)) ?
            selectedProjectLinkProperty.isSelected(feature.projectLinkData.linkId) : false;
          if (canIHighlight) {
            suravageFeaturesToHighlight.push(feature);
          }
        });
        if (suravageFeaturesToHighlight.length !== 0) {
          addFeaturesToSelection(suravageFeaturesToHighlight);
        }

        var suravageResult = _.filter(suravageProjectDirectionMarkerLayer.getSource().getFeatures(), function (item) {
          return _.find(suravageFeaturesToHighlight, function (sf) {
            return sf.projectLinkData.linkId === item.roadLinkData.linkId;
          });
        });

        _.each(suravageResult, function (featureMarker) {
          selectSingleClick.getFeatures().push(featureMarker);
        });
      }

      var result = _.filter(directionMarkerLayer.getSource().getFeatures(), function (item) {
        return _.find(featuresToHighlight, {linkId: item.id});
      });

      _.each(result, function (featureMarker) {
        selectSingleClick.getFeatures().push(featureMarker);
      });
    };

    /**
     * Simple method that will add various open layers 3 features to a selection.
     * @param ol3Features
     */
    var addFeaturesToSelection = function (ol3Features) {
      var olUids = _.map(selectSingleClick.getFeatures().getArray(), function (feature) {
        return feature.ol_uid;
      });
      _.each(ol3Features, function (feature) {
        if (!_.contains(olUids, feature.ol_uid)) {
          selectSingleClick.getFeatures().push(feature);
          olUids.push(feature.ol_uid); // prevent adding duplicate entries
        }
      });
    };

    eventbus.on('projectLink:clicked projectLink:split', function () {
      highlightFeatures();
    });

    eventbus.on('layer:selected', function (layer) {
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

    var zoomDoubleClickListener = function (event) {
      _.defer(function () {
        if (applicationModel.getSelectedTool() != 'Cut' && !event.shiftKey && selectedProjectLinkProperty.get().length === 0 &&
          applicationModel.getSelectedLayer() == 'roadAddressProject' && map.getView().getZoom() <= 13) {
          map.getView().setZoom(map.getView().getZoom() + 1);
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
      if (applicationModel.getSelectedTool() === 'Cut' && suravageCutter) {
        suravageCutter.updateByPosition(event.coordinate);
      } else {
        displayRoadAddressInfo(event, pixel);
      }
    });

    var displayRoadAddressInfo = function (event, pixel) {

      var featureAtPixel = map.forEachFeatureAtPixel(pixel, function (feature, vectorLayer) {
        return feature;
      });

      //Ignore if target feature is marker
      if (isDefined(featureAtPixel) && (isDefined(featureAtPixel.roadLinkData) || isDefined(featureAtPixel.projectLinkData))) {
        var roadData;
        var coordinate = map.getEventCoordinate(event.originalEvent);

        if (isDefined(featureAtPixel.projectLinkData)) {
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
              'Tuntematon tien segmentti' + '</p>';
          }
        }

        overlay.setPosition(coordinate);

      } else {
        overlay.setPosition(undefined);
      }
    };

    var isDefined = function (variable) {
      return !_.isUndefined(variable);
    };

    //Add defined interactions to the map.
    map.addInteraction(selectSingleClick);
    map.addInteraction(selectDoubleClick);

    var mapMovedHandler = function (mapState) {
      if(applicationModel.getSelectedTool() == 'Cut' && selectSingleClick.getFeatures().getArray().length > 0)
          return;
      var projectId = _.isUndefined(projectCollection.getCurrentProject()) ? undefined : projectCollection.getCurrentProject().project.id;
      if (mapState.zoom !== currentZoom) {
        currentZoom = mapState.zoom;
      }
      if (mapState.zoom < minimumContentZoomLevel()) {
        vectorSource.clear();
        eventbus.trigger('map:clearLayers');
      } else if (mapState.selectedLayer == layerName) {
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

    var removeSelectInteractions = function () {
      map.removeInteraction(selectDoubleClick);
      map.removeInteraction(selectSingleClick);
    };

    /**
     * This will deactivate the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick - only if demanded with the Both
     */

    var deactivateSelectInteractions = function (both) {
      selectDoubleClick.setActive(false);
      if (both) {
        selectSingleClick.setActive(false);
      }
    };

    /**
     * This will activate the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick - only if demanded with the Both
     */

    var activateSelectInteractions = function (both) {
      selectDoubleClick.setActive(true);
      if (both) {
        selectSingleClick.setActive(true);
      }
    };

    var handleRoadsVisibility = function () {
      if (_.isObject(vectorLayer)) {
        vectorLayer.setVisible(map.getView().getZoom() >= minimumContentZoomLevel());
      }
    };

    var minimumContentZoomLevel = function () {
      if (!_.isUndefined(layerMinContentZoomLevels[applicationModel.getSelectedLayer()])) {
        return layerMinContentZoomLevels[applicationModel.getSelectedLayer()];
      }
      return zoomlevels.minZoomForRoadLinks;
    };

    var loadFeatures = function (features) {
      vectorSource.addFeatures(features);
    };

    var show = function (map) {
      vectorLayer.setVisible(true);
    };

    var hideLayer = function () {
      me.stop();
      me.hide();
    };

    var clearProjectLinkLayer = function () {
      vectorLayer.getSource().clear();
    };

    var removeCutterMarkers = function() {
      var featuresToRemove = [];
      _.each(selectSingleClick.getFeatures().getArray(), function(feature){
        if(feature.getProperties().type == 'cutter')
          featuresToRemove.push(feature);
      });
      _.each(featuresToRemove, function(ft){
        selectSingleClick.getFeatures().remove(ft);
      });
    };

    var SuravageCutter = function(suravageLayer, collection, eventListener) {
      var scissorFeatures = [];
      var CUT_THRESHOLD = 20;
      var suravageSource = suravageLayer.getSource();
      var self = this;

      var moveTo = function(x, y) {
        scissorFeatures = [new ol.Feature({
          geometry: new ol.geom.Point([x, y]),
          type: 'cutter-crosshair'
        })];
        scissorFeatures[0].setStyle(
            new ol.style.Style({
              image: new ol.style.Icon({
                src: 'images/cursor-crosshair.svg'
              })
            })
        );
        removeFeaturesByType('cutter-crosshair');
        addFeaturesToSelection(scissorFeatures);
      };

      var removeFeaturesByType = function (match) {
        _.each(selectSingleClick.getFeatures().getArray(), function(feature){
          if(feature && feature.getProperties().type == match) {
            selectSingleClick.getFeatures().remove(feature);
          }
        });
      };

      var clickHandler = function(evt) {
        if (applicationModel.getSelectedTool() === 'Cut') {
          $('.wrapper').remove();
          removeCutterMarkers();
          self.cut(evt);
        }
      };

      this.deactivate = function() {
        eventListener.stopListening(eventbus, 'map:clicked', clickHandler);
        selectedProjectLinkProperty.setDirty(false);
      };

      this.activate = function() {
        eventListener.listenTo(eventbus, 'map:clicked map:dblclicked', clickHandler);
      };

      var isWithinCutThreshold = function(suravageLink) {
        return suravageLink !== undefined && suravageLink < CUT_THRESHOLD;
      };

      var findNearestSuravageLink = function(point) {

        var possibleSplit = _.filter(vectorSource.getFeatures().concat(suravageRoadProjectLayer.getSource().getFeatures()), function(feature){
          return !_.isUndefined(feature.projectLinkData) && (feature.projectLinkData.roadLinkSource == LinkGeomSource.SuravageLinkInterface.value);
        });
        return _.chain(possibleSplit)
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
          removeFeaturesByType('cutter-crosshair');
        }
      };

      this.cut = function(mousePoint) {
        var pointsToLineString = function(points) {
          var coordPoints = _.map(points, function(point) { return [point.x, point.y]; });
          return new ol.geom.LineString(coordPoints);
        };

        var calculateSplitProperties = function(nearestSuravage, point) {
          var lineString = pointsToLineString(nearestSuravage.points);
          var splitMeasure = GeometryUtils.calculateMeasureAtPoint(lineString, point);
          var splitVertices = GeometryUtils.splitByPoint(lineString, point);
          return _.merge({ splitMeasure: splitMeasure, point: splitVertices.secondSplitVertices[0] }, splitVertices);
        };

        var nearest = findNearestSuravageLink([mousePoint.x, mousePoint.y]);

        if (!nearest || !isWithinCutThreshold(nearest.distance)) {
          showChangesAndSendButton();
          selectSingleClick.getFeatures().clear();
          return;
        }
        var nearestSuravage = nearest.feature.projectLinkData;
        nearestSuravage.points = _.isUndefined(nearestSuravage.originalGeometry) ?
          nearestSuravage.points : nearestSuravage.originalGeometry;
        var splitProperties = calculateSplitProperties(nearestSuravage, mousePoint);
        if (!_.isUndefined(nearestSuravage.connectedLinkId)) {
          nearest.feature.geometry = pointsToLineString(nearestSuravage.originalGeometry);
        }
        selectedProjectLinkProperty.splitSuravageLink(nearestSuravage, splitProperties, mousePoint);
        projectCollection.setTmpDirty([nearest.feature.projectLinkData]);
      };
    };

    var mapOverLinkMiddlePoints = function(links, transformation) {
      return _.map(links, function(link) {
        var points = _.map(link.points, function(point) {
          return [point.x, point.y];
        });
        var lineString = new ol.geom.LineString(points);
        var middlePoint = GeometryUtils.calculateMidpointOfLineString(lineString);
        return transformation(link, middlePoint);
      });
    };

    var projectLinkStatusIn = function(projectLink, possibleStatus){
      if(!_.isUndefined(possibleStatus) && !_.isUndefined(projectLink) )
        return _.contains(possibleStatus, projectLink.status);
      else return false;
    };
    

    var suravageCutter = new SuravageCutter(suravageRoadProjectLayer, projectCollection, me.eventListener);

    var changeTool = function(tool) {
          if (tool === 'Cut') {
            suravageCutter.activate();
            selectSingleClick.setActive(false);
          } else if (tool === 'Select') {
            suravageCutter.deactivate();
            selectSingleClick.setActive(true);
          }
      };

    eventbus.on('split:projectLinks', function (split) {
      _.defer(function(){drawIndicators(_.filter(split, function(link){
        return !_.isUndefined(link.marker);
      }));});
        eventbus.trigger('projectLink:split', split);
    });

    eventbus.on('projectLink:projectLinksCreateSuccess', function () {
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1, undefined, projectCollection.getPublishableStatus());
    });

    eventbus.on('changeProjectDirection:clicked', function () {
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1, undefined, projectCollection.getPublishableStatus());
      eventbus.once('roadAddressProject:fetched', function () {
        if (selectedProjectLinkProperty.get().length > 1)
          selectedProjectLinkProperty.open(selectedProjectLinkProperty.get()[0].linkId, true);
        else
          selectedProjectLinkProperty.open(selectedProjectLinkProperty.get()[0].linkId, false);
      });
    });

    eventbus.on('projectLink:revertedChanges', function () {
      eventbus.trigger('roadAddress:projectLinksUpdated');
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1, undefined, projectCollection.getPublishableStatus());
    });

    var redraw = function () {
      var ids = {};
      _.each(selectedProjectLinkProperty.get(), function (sel) {
        ids[sel.linkId] = true;
      });

      var editedLinks = _.map(projectCollection.getDirty(), function (editedLink) {
        return editedLink;
      });

      var separated = _.partition(projectCollection.getAll(), function (projectRoad) {
        return projectRoad.roadLinkSource === LinkGeomSource.SuravageLinkInterface.value;
      });
      calibrationPointLayer.getSource().clear();

      var toBeTerminated = _.partition(editedLinks, function (link) {
        return link.status === LinkStatus.Terminated.value;
      });
      var toBeUnchanged = _.partition(editedLinks, function (link) {
        return link.status === LinkStatus.Unchanged.value;
      });

      var toBeTerminatedLinkIds = _.pluck(toBeTerminated[0], 'id');
      var suravageProjectRoads = separated[0];
      var suravageFeatures = [];
      suravageProjectDirectionMarkerLayer.getSource().clear();

      _.map(suravageProjectRoads, function (projectLink) {
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
      var suravageDirectionRoadMarker = _.filter(suravageProjectRoads, function (projectLink) {
        return projectLink.roadLinkType !== RoadLinkType.FloatingRoadLinkType.value && projectLink.anomaly !== Anomaly.NoAddressGiven.value && projectLink.anomaly !== Anomaly.GeometryChanged.value && (projectLink.sideCode === SideCode.AgainstDigitizing.value || projectLink.sideCode === SideCode.TowardsDigitizing.value);
      });

      var suravageFeaturesToRemove = [];
      _.each(selectSingleClick.getFeatures().getArray(), function (feature) {
        if (feature.getProperties().type && feature.getProperties().type === "marker")
          suravageFeaturesToRemove.push(feature);
      });
      _.each(suravageFeaturesToRemove, function (feature) {
        selectSingleClick.getFeatures().remove(feature);
      });

      _.each(suravageDirectionRoadMarker, function (directionLink) {
        var marker = cachedMarker.createMarker(directionLink);
        if (map.getView().getZoom() > zoomlevels.minZoomForDirectionalMarkers)
          suravageProjectDirectionMarkerLayer.getSource().addFeature(marker);
        selectSingleClick.getFeatures().push(marker);
      });

      var actualCalibrationPoints = me.drawCalibrationMarkers(calibrationPointLayer.source, suravageProjectRoads);
      _.each(actualCalibrationPoints, function (actualPoint) {
        var calMarker = new CalibrationPoint(actualPoint);
        calibrationPointLayer.getSource().addFeature(calMarker.getMarker(true));
      });

      suravageRoadProjectLayer.getSource().addFeatures(suravageFeatures);

      var projectLinks = separated[1];
      var features = [];
      _.map(projectLinks, function (projectLink) {
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
      var directionRoadMarker = _.filter(projectLinks, function (projectLink) {
        return projectLink.roadLinkType !== RoadLinkType.FloatingRoadLinkType.value && projectLink.anomaly !== Anomaly.NoAddressGiven.value && projectLink.anomaly !== Anomaly.GeometryChanged.value && (projectLink.sideCode === SideCode.AgainstDigitizing.value || projectLink.sideCode === SideCode.TowardsDigitizing.value);
      });

      var featuresToRemove = [];
      _.each(selectSingleClick.getFeatures().getArray(), function (feature) {
        if (feature.getProperties().type && feature.getProperties().type === "marker")
          featuresToRemove.push(feature);
      });
      _.each(featuresToRemove, function (feature) {
        selectSingleClick.getFeatures().remove(feature);
      });
      _.each(directionRoadMarker, function (directionLink) {
        var marker = cachedMarker.createMarker(directionLink);
        if (map.getView().getZoom() > zoomlevels.minZoomForDirectionalMarkers)
          directionMarkerLayer.getSource().addFeature(marker);
        selectSingleClick.getFeatures().push(marker);
      });

      var actualPoints = me.drawCalibrationMarkers(calibrationPointLayer.source, projectLinks);
      _.each(actualPoints, function (actualPoint) {
        var calMarker = new CalibrationPoint(actualPoint);
        calibrationPointLayer.getSource().addFeature(calMarker.getMarker(true));
      });

      calibrationPointLayer.setZIndex(standardZIndex + 2);
      var partitioned = _.partition(features, function (feature) {
        return (!_.isUndefined(feature.projectLinkData.linkId) && _.contains(_.pluck(editedLinks, 'id'), feature.projectLinkData.linkId));
      });
      features = [];
      _.each(partitioned[0], function (feature) {
        var editedLink = (!_.isUndefined(feature.projectLinkData.linkId) && _.contains(_.pluck(editedLinks, 'id'), feature.projectLinkData.linkId));
        if (editedLink) {
          if (_.contains(toBeTerminatedLinkIds, feature.projectLinkData.linkId)) {
            feature.projectLinkData.status = LinkStatus.Terminated.value;
            var termination = projectLinkStyler.getProjectLinkStyle().getStyle( feature.projectLinkData, {zoomLevel: currentZoom});
            feature.setStyle(termination);
            features.push(feature);
          }
        }
      });
      
      if (features.length !== 0)
        addFeaturesToSelection(features);
      features = features.concat(partitioned[1]);
      vectorLayer.getSource().clear(true);
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

    eventbus.on('roadAddressProject:selected', function (projId) {
      eventbus.once('roadAddressProject:projectFetched', function (projectInfo) {
        projectCollection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getZoom(), projectInfo.id, projectInfo.publishable);
      });
      projectCollection.getProjectsWithLinksById(projId);
    });

    eventbus.on('roadAddressProject:fetched', function (newSelection) {
      applicationModel.removeSpinner();
      redraw();
      _.defer(function () {
        highlightFeatures();
      });
    });

    eventbus.on('roadAddress:projectLinksEdited', function () {
      redraw();
    });

    eventbus.on('roadAddressProject:projectLinkSaved', function (projectId, isPublishable) {
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()), map.getView().getZoom(), projectId, isPublishable);
    });

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('layer:selected', function (layer, previouslySelectedLayer) {
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

    eventbus.on('roadAddressProject:deselectFeaturesSelected', function () {
      clearHighlights();
    });

    eventbus.on('roadAddressProject:clearAndDisableInteractions', function () {
      clearHighlights();
      removeSelectInteractions();
    });

    eventbus.on('roadAddressProject:enableInteractions', function () {
      addSelectInteractions();
    });

    eventbus.on('roadAddressProject:clearOnClose', function () {
      clearHighlights();
      clearLayers();
      clearProjectLinkLayer();
    });

    eventbus.on('map:clearLayers', clearLayers);

    eventbus.on('suravageProjectRoads:toggleVisibility', function (visibility) {
      suravageRoadProjectLayer.setVisible(visibility);
      suravageProjectDirectionMarkerLayer.setVisible(visibility);
    });

    eventbus.on('roadAddressProject:toggleEditingRoad', function (notEditingData) {
      isNotEditingData = notEditingData;
    });

    eventbus.on('roadAddressProject:deactivateAllSelections', function () {
      deactivateSelectInteractions(true);
    });

    eventbus.on('roadAddressProject:startAllInteractions', function () {
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