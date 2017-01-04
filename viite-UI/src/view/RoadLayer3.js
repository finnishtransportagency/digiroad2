(function(root) {
  root.RoadLayer3 = function(map, roadCollection) {
    var vectorLayer;
    var drawRoadLinks = function(roadLinks, zoom) {
    };

    var vectorSource = new ol.source.Vector({
      loader: function(extent, resolution, projection) {
        var zoom = 2048/resolution;
        var roadLinks = roadCollection.fetch(extent[0] + ',' + extent[1] + ',' + extent[2] + ',' + extent[3], zoom);
        var features = _.map(roadLinks, function(roadLink) {
          var points = _.map(roadLink.points, function(point) {
            return [point.x, point.y];
          });
          return new ol.Feature({ geometry: new ol.Geometry.LineString(points) });
        });

      },
      strategy: ol.loadingstrategy.tile(ol.tilegrid.createXYZ({
        tileSize: 512
      }))
    });

    vectorLayer = new ol.layer.Vector({
      source: vectorSource
    });
    vectorLayer.setVisible(true);
    map.addLayer(vectorLayer);

    return {
      layer: vectorLayer
    };
  };
})(this);
