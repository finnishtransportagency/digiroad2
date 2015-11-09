(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      collection = params.collection,
      map = params.map,
      roadCollection = params.roadCollection;

    Layer.call(this, 'pedestrianCrossing', roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var assetLayer = new OpenLayers.Layer.Boxes('pedestrianCrossing');

    this.refreshView = function() {
      redrawLinks(map);
      collection.fetch(map.getExtent()).then(function(assets) {
        _.each(assets, function(asset) {
          var bounds = OpenLayers.Bounds.fromArray([asset.lon, asset.lat, asset.lon + 15, asset.lat + 15]);
          var box = new OpenLayers.Marker.Box(bounds, "ffffff00", 0);
          $(box.div)
            .css('overflow', 'visible !important')
            .css('background-image', 'url(./images/center-marker.svg)');
          assetLayer.addMarker(box);
        });
      });
    };

    this.activateSelection = function() {
    };
    this.deactivateSelection = function() {
    };

    function redrawLinks(map) {
      eventbus.once('roadLinks:fetched', function () {
        roadLayer.drawRoadLinks(roadCollection.getAll(), map.getZoom());
      });
      roadCollection.fetchFromVVH(map.getExtent());
    }

    function show(map) {
      redrawLinks(map);
      map.addLayer(assetLayer);
      me.show(map);
    }
    function hide() {
      map.removeLayer(assetLayer);
      roadLayer.clear();
    }
    return {
      show: show,
      hide: hide
    };
  };
})(this);