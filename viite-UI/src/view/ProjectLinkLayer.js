(function(root) {
    root.ProjectLinkLayer = function(map, projectCollection) {
        var vectorLayer;
        var layerMinContentZoomLevels = {};
        var currentZoom = 0;
        var project;

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

      //Work in progress - add style for all roads on the layer that are not part of the project
      var styleFunction = function (feature, resolution){

        var borderWidth = 3;
        var strokeWidth = Styler.strokeWidthByZoomLevel(resolution, feature.roadLinkData.roadLinkType, feature.roadLinkData.anomaly, feature.roadLinkData.roadLinkSource, false, feature.roadLinkData.constructionType);
        var lineColor = 'rgba(247, 254, 46, 0.45)';
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

        var zIndex = Styler.determineZIndex(feature.roadLinkData.roadLinkType, feature.roadLinkData.anomaly, feature.roadLinkData.roadLinkSource);
        lineStyle.setZIndex(zIndex+2);
        return [lineStyle];
      };

        vectorLayer = new ol.layer.Vector({
            source: vectorSource,
            style: styleFunction
        });

        var loadFeatures = function (features) {
            vectorSource.addFeatures(features);
        };

        var show = function(map) {
            vectorLayer.setVisible(true);
        };

        var hideLayer = function() {
            this.stop();
            this.hide();
        };

        eventbus.on('roadAddressProject:openProject', function(projectSelected) {
          this.project = projectSelected;
          eventbus.trigger('roadAddressProject:selected', projectSelected.id);
        });

        eventbus.on('roadAddressProject:selected', function(projId) {
            console.log(projId);
          eventbus.once('roadAddressProject:projectFetched', function(id) {
            projectCollection.fetch(map.getView().calculateExtent(map.getSize()),map.getView().getZoom(), id);
            vectorSource.clear();
            eventbus.trigger('map:clearLayers');
            vectorLayer.changed();
          });
            projectCollection.getProjectsWithLinksById(projId);
        });

        vectorLayer.setVisible(true);
        map.addLayer(vectorLayer);

        return {
            show: show,
            hide: hideLayer
        };
    };

})(this);