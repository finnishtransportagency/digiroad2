(function(root) {
  root.RoadLayer3 = function(map, roadCollection,styler, selectedLinkProperty) {
    var vectorLayer;
    var layerMinContentZoomLevels = {};
    var currentZoom = 0;

    var vectorSource = new ol.source.Vector({
      loader: function(extent, resolution, projection) {
        var zoom = Math.log(1024/resolution) / Math.log(2);
        eventbus.once('roadLinks:fetched', function() {
          var features = _.map(roadCollection.getAll(), function(roadLink) {
            var points = _.map(roadLink.points, function(point) {
              return [point.x, point.y];
            });
            var feature =  new ol.Feature({ geometry: new ol.geom.LineString(points)
            });
            feature.roadLinkData = roadLink;
            return feature;
          });
          loadFeatures(features);
        });
      },
      strategy: ol.loadingstrategy.bbox
    });

    function vectorLayerStyle(feature) {
      return styler.generateStyleByFeature(feature.roadLinkData, currentZoom-2);
    }

    var loadFeatures = function (features) {
      vectorSource.clear(true);
      vectorSource.addFeatures(selectedLinkProperty.filterFeaturesAfterSimulation(features));
    };

    var minimumContentZoomLevel = function() {
      if (!_.isUndefined(layerMinContentZoomLevels[applicationModel.getSelectedLayer()])) {
        return layerMinContentZoomLevels[applicationModel.getSelectedLayer()];
      }
      return zoomlevels.minZoomForRoadLinks;
    };

    var handleRoadsVisibility = function() {
      if (_.isObject(vectorLayer)) {
        vectorLayer.setVisible(map.getView().getZoom() >= minimumContentZoomLevel());
      }
    };

    var mapMovedHandler = function(mapState) {
      console.log("map moved");
      console.log("zoom = " + mapState.zoom);
      if (mapState.zoom !== currentZoom) {
        currentZoom = mapState.zoom;
      }
      if (mapState.zoom < minimumContentZoomLevel()) {
        vectorSource.clear();
        eventbus.trigger('map:clearLayers');
      } else {
        roadCollection.fetch(map.getView().calculateExtent(map.getSize()).join(','), currentZoom + 1);
        handleRoadsVisibility();
      }
    };


    vectorLayer = new ol.layer.Vector({
      source: vectorSource,
      style: vectorLayerStyle
    });
    vectorLayer.setVisible(true);
    vectorLayer.set('name', 'roadLayer');
    map.addLayer(vectorLayer);

    eventbus.on('map:moved', mapMovedHandler, this);

    return {
      layer: vectorLayer
    };
  };
})(this);
