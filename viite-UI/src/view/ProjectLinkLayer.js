(function(root) {
  root.ProjectLinkLayer = function(map, projectCollection, selectedProjectLinkProperty, roadLayer) {
    var layerName = 'roadAddressProject';
    var vectorLayer;
    var calibrationPointVector = new ol.source.Vector({});
    var layerMinContentZoomLevels = {};
    var currentZoom = 0;
    var standardZIndex = 6;
    Layer.call(this, layerName, roadLayer);
    var project;
    var me = this;
    var styler = new Styler();

    var vectorSource = new ol.source.Vector({
      loader: function(extent, resolution, projection) {
        var zoom = Math.log(1024/resolution) / Math.log(2);
        var features = _.map(projectCollection.getAll(), function(projectLink) {
          var points = _.map(projectLink.points, function(point) {
            return [point.x, point.y];
          });
          var feature =  new ol.Feature({ geometry: new ol.geom.LineString(points)
          });
          feature.projectLinkData = projectLink;
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

    var styleFunction = function (feature, resolution){
      var status = feature.projectLinkData.status;
      var borderWidth;
      var lineColor;

      if(status === 0) {
        borderWidth = 8;
        lineColor = 'rgba(247, 254, 46, 1)';
      }
      if (status === 1) {
        borderWidth = 3;
        lineColor = 'rgba(56, 56, 54, 1)';
      }
      if (status === 0 || status === 1) {
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
      style: styleFunction
    });
    vectorLayer.set('name', layerName);

    var selectSingleClick = new ol.interaction.Select({
      layer: vectorLayer,
      condition: ol.events.condition.singleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
        if (feature.projectLinkData.status === 0){
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 255, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 255, 0, 0.95)',
              width: 8
            })
          });
        } else if(feature.projectLinkData.status === 1){
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 0, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 0, 0, 0.95)',
              width: 8
            })
          });
        }
      }
    });

    selectSingleClick.set('name','selectSingleClickInteractionPLL');

    selectSingleClick.on('select',function(event) {
      // TODO: allow selection for non-addressed road links
      var selection = _.find(event.selected, function (selectionTarget) {
        return !_.isUndefined(selectionTarget.projectLinkData) && selectionTarget.projectLinkData.status === 0;
      });
      selectedProjectLinkProperty.clean();
      $('.wrapper').remove();
      $('#actionButtons').empty();
      if (!_.isUndefined(selection))
        selectedProjectLinkProperty.open(selection.projectLinkData.linkId, true);
    });

    var selectDoubleClick = new ol.interaction.Select({
      layer: vectorLayer,
      condition: ol.events.condition.doubleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
        if(feature.projectLinkData.status === 0) {
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 255, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 255, 0, 0.95)',
              width: 8
            })
          });
        } else if(feature.projectLinkData.status === 1){
          return new ol.style.Style({
            fill: new ol.style.Fill({
              color: 'rgba(0, 0, 0, 0.75)'
            }),
            stroke: new ol.style.Stroke({
              color: 'rgba(0, 0, 0, 0.95)',
              width: 8
            })
          });
        }
      }
    });

    selectDoubleClick.set('name','selectDoubleClickInteractionPLL');

    selectDoubleClick.on('select',function(event) {
      // TODO: allow selection for non-addressed road links
      var selection = _.find(event.selected, function (selectionTarget) {
        return !_.isUndefined(selectionTarget.projectLinkData) && selectionTarget.projectLinkData.status === 0;
      });
      if (!_.isUndefined(selection))
        selectedProjectLinkProperty.open(selection.projectLinkData.linkId);
    });

    var clearHighlights = function(){
      if(selectDoubleClick.getFeatures().getLength() !== 0){
        selectDoubleClick.getFeatures().clear();
      }
      if(selectSingleClick.getFeatures().getLength() !== 0){
        selectSingleClick.getFeatures().clear();
      }
    };

    var highlightFeatures = function() {
      clearHighlights();
      var featuresToHighlight = [];
      _.each(vectorLayer.getSource().getFeatures(), function(feature) {
        var canIHighlight = !_.isUndefined(feature.projectLinkData.linkId) ?
          selectedProjectLinkProperty.isSelected(feature.projectLinkData.linkId) : false;
        if(canIHighlight){
          featuresToHighlight.push(feature);
        }
      });
      if(featuresToHighlight.length !== 0)
        addFeaturesToSelection(featuresToHighlight);
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

    var zoomDoubleClickListener = function(event) {
      _.defer(function(){
        if(selectedProjectLinkProperty.get().length === 0 && applicationModel.getSelectedLayer() == 'roadAddressProject' && map.getView().getZoom() <= 13){
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
      //autoPan: false,
    }));

    map.addOverlay(overlay);

    //Listen pointerMove and get pixel for displaying roadAddress feature info
    eventbus.on('map:mouseMoved', function (event, pixel) {
      if (event.dragging || applicationModel.getSelectedLayer() !== 'roadAddressProject') {
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
        if (roadData.roadNumber!==0 &&roadData.roadPartNumber!==0&&roadData.roadPartNumber!==99 ){
       infoContent.innerHTML = '<p>' +
          'Tienumero: ' + roadData.roadNumber + '<br>' +
          'Tieosanumero: ' + roadData.roadPartNumber + '<br>' +
          'Ajorata: ' + roadData.trackCode + '<br>' +
          'AET: ' + roadData.startAddressM + '<br>' +
          'LET: ' + roadData.endAddressM + '<br>' +'</p>';

        } else {
          infoContent.innerHTML = '<p>' +
          'Tuntematon tien segmentti' +'</p>'; // road with no address
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
      if (mapState.zoom !== currentZoom) {
        currentZoom = mapState.zoom;
      }
      if (mapState.zoom < minimumContentZoomLevel()) {
        vectorSource.clear();
        eventbus.trigger('map:clearLayers');
      } else if (mapState.selectedLayer == layerName){
        projectCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1, undefined);
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

    var redraw = function(){
      var editedLinks = projectCollection.getDirty();
      var projectLinks = projectCollection.getAll();
      var features = [];
      _.map(projectLinks, function(projectLink) {
        var points = _.map(projectLink.points, function (point) {
          return [point.x, point.y];
        });
        var feature = new ol.Feature({
          geometry: new ol.geom.LineString(points)
        });
        feature.projectLinkData = projectLink;
        features.push(feature);
      });
      var actualPoints = me.drawCalibrationMarkers(calibrationPointLayer.source, projectLinks);
      _.each(actualPoints, function (actualPoint) {
        var calMarker = new CalibrationPoint(actualPoint.point);
        calibrationPointLayer.getSource().addFeature(calMarker.getMarker(true));
      });

      calibrationPointLayer.setZIndex(standardZIndex + 2);
      var partitioned = _.partition(features, function(feature) {
        return (!_.isUndefined(feature.projectLinkData.linkId) && _.contains(editedLinks, feature.projectLinkData.linkId));
      });
      features = [];
      _.each(partitioned[0], function(feature) {
        var editedLink = (!_.isUndefined(feature.projectLinkData.linkId) && _.contains(editedLinks, feature.projectLinkData.linkId));
        if(editedLink){
          feature.projectLinkData.status = 1;
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
      });
      if(features.length !== 0)
        addFeaturesToSelection(features);
      features = features.concat(partitioned[1]);
      vectorLayer.getSource().clear(true); // Otherwise we get multiple copies: TODO: clear only inside bbox
      vectorLayer.getSource().addFeatures(features);
      vectorLayer.changed();
    };

    eventbus.on('roadAddressProject:openProject', function(projectSelected) {
      this.project = projectSelected;
      eventbus.trigger('layer:enableButtons', false);
      eventbus.trigger('editMode:setReadOnly', false);
      eventbus.trigger('roadAddressProject:selected', projectSelected.id, layerName, applicationModel.getSelectedLayer());
    });

    eventbus.on('roadAddressProject:selected', function(projId) {
      eventbus.once('roadAddressProject:projectFetched', function(id) {
        projectCollection.fetch(map.getView().calculateExtent(map.getSize()),map.getView().getZoom(), id);
      });
      projectCollection.getProjectsWithLinksById(projId);
    });

    eventbus.on('roadAddressProject:fetched', function() {
      redraw();
    });

    eventbus.on('roadAddress:projectLinksEdited',function(){
      redraw();
    });

    eventbus.on('roadAddressProject:projectLinkSaved',function(projectId, isPublishable){
      projectCollection.fetch(map.getView().calculateExtent(map.getSize()),map.getView().getZoom(), projectId, isPublishable);
    });

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('layer:selected', function(layer, previouslySelectedLayer) {
     //TODO create proper system for layer changes and needed calls
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
       hideLayer();
       removeSelectInteractions();
     }
    });

    eventbus.on('roadAddressProject:deselectFeaturesSelected', function(){
      clearHighlights();
    });

    vectorLayer.setVisible(true);
    calibrationPointLayer.setVisible(true);
    map.addLayer(vectorLayer);
    map.addLayer(calibrationPointLayer);
    return {
      show: show,
      hide: hideLayer
    };
  };

})(this);