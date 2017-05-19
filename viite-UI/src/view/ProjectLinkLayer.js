(function(root) {
  root.ProjectLinkLayer = function(map, projectCollection, selectedProjectLinkProperty) {
    var layerName = 'roadAddressProject';
    var vectorLayer;
    var layerMinContentZoomLevels = {};
    var currentZoom = 0;
    var project;
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

    var styleFunction = function (feature, resolution){

      if(feature.projectLinkData.status === 0) {
        var borderWidth = 8;
        var strokeWidth = styler.strokeWidthByZoomLevel(currentZoom, feature.projectLinkData.roadLinkType, feature.projectLinkData.anomaly, feature.projectLinkData.roadLinkSource, false, feature.projectLinkData.constructionType);
        var lineColor = 'rgba(247, 254, 46, 1)';
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

        var zIndex = styler.determineZIndex(feature.projectLinkData.roadLinkType, feature.projectLinkData.anomaly, feature.projectLinkData.roadLinkSource);
        lineStyle.setZIndex(zIndex + 2);
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

    var selectSingleClick = new ol.interaction.Select({
      layer: vectorLayer,
      //Limit this interaction to the singleClick
      condition: ol.events.condition.singleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
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
    });

    selectSingleClick.on('select',function(event) {
      console.log("click");
      // TODO: 374 to take this to form
      var selection = _.find(event.selected, function (selectionTarget) {
          return !_.isUndefined(selectionTarget.projectLinkData);
      });
      selectedProjectLinkProperty.open(selection.projectLinkData.linkId);
    });

    var selectDoubleClick = new ol.interaction.Select({
      layer: vectorLayer,
      //Limit this interaction to the singleClick
      condition: ol.events.condition.doubleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
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
    });

    selectDoubleClick.on('select',function(event) {
      console.log("clickety-click");
      // TODO: 374 to take this to form
      var selection = _.find(event.selected, function (selectionTarget) {
          return !_.isUndefined(selectionTarget.projectLinkData);
      });
      selectedProjectLinkProperty.open(selection.projectLinkData.linkId);
    });

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
      // this.stop(); // No such function
      this.hide();
    };

    eventbus.on('roadAddressProject:openProject', function(projectSelected) {
      this.project = projectSelected;
      eventbus.trigger('roadAddressProject:selected', projectSelected.id, layerName, applicationModel.getSelectedLayer());
    });

    eventbus.on('roadAddressProject:selected', function(projId) {
      console.log(projId);
      eventbus.once('roadAddressProject:projectFetched', function(id) {
        projectCollection.fetch(map.getView().calculateExtent(map.getSize()),map.getView().getZoom(), id);
        // vectorSource.clear();
        // eventbus.trigger('map:clearLayers');
      });
      projectCollection.getProjectsWithLinksById(projId);
    });

    eventbus.on('roadAddressProject:fetched', function(projectLinks){
      var simulatedOL3Features = [];
      _.map(projectLinks, function(projectLink){
        var points = _.map(projectLink.points, function(point) {
          return [point.x, point.y];
        });
        var feature =  new ol.Feature({ geometry: new ol.geom.LineString(points)
        });
        feature.projectLinkData = projectLink;
        simulatedOL3Features.push(feature);
      });
      vectorLayer.getSource().addFeatures(simulatedOL3Features);
      vectorLayer.changed();
    });

    eventbus.on('map:moved', mapMovedHandler, this);

    vectorLayer.setVisible(true);
    map.addLayer(vectorLayer);

    return {
      show: show,
      hide: hideLayer
    };
  };

})(this);