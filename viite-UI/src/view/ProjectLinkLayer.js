(function(root) {
    root.ProjectLinkLayer = function(map, projectCollection) {
        var vectorLayer;
        var layerMinContentZoomLevels = {};
        var currentZoom = 0;

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

        vectorLayer = new ol.layer.Vector({
            source: vectorSource
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

        eventbus.on('roadAddressProject:selected', function(projId) {
            projectCollection.fetch(map.getView().calculateExtent(map.getSize()),map.getView().getZoom(), projId);
            vectorSource.clear();
            eventbus.trigger('map:clearLayers');
            vectorLayer.changed();
        });

        vectorLayer.setVisible(true);
        map.addLayer(vectorLayer);

        return {
            show: show,
            hide: hideLayer
        };
    };

})(this);