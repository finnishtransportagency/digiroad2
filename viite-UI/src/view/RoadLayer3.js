(function(root) {
  root.RoadLayer3 = function(map, roadCollection) {
    var vectorLayer;
    var drawRoadLinks = function(roadLinks, zoom) {
      console.log("Draw road links");
    };
    var layerMinContentZoomLevels = {};
    var layerStyleMaps = {};
    var layerStyleMapProviders = {};

    var vectorSource = new ol.source.Vector({
      loader: function(extent, resolution, projection) {
        var zoom = 2048/resolution;
        var roadLinks = roadCollection.fetch(extent.join(','), zoom);
        var features = _.map(roadLinks, function(roadLink) {
          var points = _.map(roadLink.points, function(point) {
            return [point.x, point.y];
          });
          return new ol.Feature({ geometry: new ol.Geometry.LineString(points) });
        });
        console.log(features);
        loadFeatures(features);
      },
      strategy: ol.loadingstrategy.bbox
    });

    var loadFeatures = function (features) {
      vectorSource.addFeatures(features);
    };

    function stylesUndefined() {
      return _.isUndefined(layerStyleMaps[applicationModel.getSelectedLayer()]) &&
        _.isUndefined(layerStyleMapProviders[applicationModel.getSelectedLayer()]);
    }

    var changeRoadsWidthByZoomLevel = function() {
      if (stylesUndefined()) {
        var widthBase = 2 + (map.getView().getZoom() - minimumContentZoomLevel());
        var roadWidth = widthBase * widthBase;
        if (applicationModel.isRoadTypeShown()) {
          // vectorLayer.setStyle({stroke: roadWidth});
        } else {
          // vectorLayer.setStyle({stroke: roadWidth});
          // vectorLayer.styleMap.styles.default.defaultStyle.strokeWidth = 5;
          // vectorLayer.styleMap.styles.select.defaultStyle.strokeWidth = 7;
        }
      }
    };

    var minimumContentZoomLevel = function() {
      if (!_.isUndefined(layerMinContentZoomLevels[applicationModel.getSelectedLayer()])) {
        return layerMinContentZoomLevels[applicationModel.getSelectedLayer()];
      }
      return zoomlevels.minZoomForRoadLinks;
    };

    var handleRoadsVisibility = function() {
      console.log(vectorLayer);
      console.log(vectorLayer.getVisible());
      console.log(vectorLayer.getSource());
      if (_.isObject(vectorLayer)) {
        vectorLayer.setVisible(map.getView().getZoom() >= minimumContentZoomLevel());
      }
    };

    var mapMovedHandler = function(mapState) {
      console.log("map moved");
      console.log(map.getLayers());
      vectorSource.clear();
      // if (mapState.zoom >= minimumContentZoomLevel()) {
      //
      //   vectorLayer.setVisible(true);
      //   changeRoadsWidthByZoomLevel();
      // } else {
      //   vectorLayer.clear();
      //   roadCollection.reset();
      // }
      handleRoadsVisibility();
    };


    vectorLayer = new ol.layer.Vector({
      source: vectorSource
    });
    vectorLayer.setVisible(true);
    map.addLayer(vectorLayer);

    eventbus.on('map:moved', mapMovedHandler, this);

    return {
      layer: vectorLayer
    };
  };
})(this);
