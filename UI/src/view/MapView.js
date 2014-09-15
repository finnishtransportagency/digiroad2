(function(root) {
  root.MapView = function(map, layers) {
    var isInitialized = false;
    var centerMarkerLayer;

    var showAssetZoomDialog = function() {
      var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
      dialog.show('Zoomaa l&auml;hemm&auml;ksi, jos haluat n&auml;hd&auml; kohteita');
      dialog.fadeout(2000);
    };

    var mapMovedHandler = function(mapState) {
      if (!zoomlevels.isInAssetZoomLevel(mapState.zoom)) {
        if (isInitialized && mapState.hasZoomLevelChanged) {
          showAssetZoomDialog();
        }
      }
    };

    var drawCenterMarker = function(position) {
      var size = new OpenLayers.Size(16, 16);
      var offset = new OpenLayers.Pixel(-(size.w / 2), -size.h / 2);
      var icon = new OpenLayers.Icon('./images/center-marker.svg', size, offset);

      centerMarkerLayer.clearMarkers();
      var marker = new OpenLayers.Marker(new OpenLayers.LonLat(position.lon, position.lat), icon);
      centerMarkerLayer.addMarker(marker);
    };

    var addCenterMarkerLayerToMap = function(map) {
      centerMarkerLayer = new OpenLayers.Layer.Markers('centerMarker');
      map.addLayer(centerMarkerLayer);
    };

    eventbus.on('application:initialized', function() {
      var zoom = map.getZoom();
      applicationModel.setZoomLevel(zoom);
      if (!zoomlevels.isInAssetZoomLevel(zoom)) {
        showAssetZoomDialog();
      }
      new CoordinateSelector($('.mapplugin.coordinates'), map.getMaxExtent());
      isInitialized = true;
      eventbus.trigger('map:initialized', map);
    }, this);

    eventbus.on('tool:changed', function(action) {
      var cursor = {'Select': 'default', 'Add': 'crosshair'};
      $('.olMap').css('cursor', cursor[action]);
    });

    eventbus.on('coordinates:selected coordinates:marked', function(position) {
      map.setCenter(new OpenLayers.LonLat(position.lon, position.lat), zoomlevels.getAssetZoomLevelIfNotCloser(map.getZoom()));
    }, this);

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('coordinates:marked', function(position) {
      drawCenterMarker(position);
    }, this);

    eventbus.on('layer:selected', function(layer) {
      var assetLayer = layers.asset;
      if (layer === 'speedLimit') {
        showSpeedLimitLayer();
        assetLayer.hide();
      } else {
        assetLayer.show();
        hideSpeedLimitLayer();
      }
    }, this);

    var showSpeedLimitLayer = function() {
      var speedLimitLayer = layers.speedLimit;
      map.addLayer(speedLimitLayer.vectorLayer);
      speedLimitLayer.vectorLayer.setVisibility(true);
      speedLimitLayer.update(map.getZoom(), map.getExtent());
    };

    var hideSpeedLimitLayer = function() {
      layers.speedLimit.reset();
      map.removeLayer(layers.speedLimit.vectorLayer);
    };

    map.events.register('moveend', this, function() {
      applicationModel.moveMap(map.getZoom(), map.getExtent());
    });

    addCenterMarkerLayerToMap(map);

    if (applicationModel.getSelectedLayer() === 'speedLimit') {
      showSpeedLimitLayer();
    }
  };
})(this);
