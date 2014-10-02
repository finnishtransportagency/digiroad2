(function(root) {
  root.MapView = function(map, layers, zoomInstructions) {
    var isInitialized = false;
    var centerMarkerLayer;

    var showAssetZoomDialog = function() {
      zoomInstructions.show(2000);
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

    var setCursor = function(tool) {
      var cursor = {'Select': 'default', 'Add': 'crosshair', 'Cut': 'pointer'};
      $('.olMap').css('cursor', cursor[tool]);
    };

    eventbus.on('tool:changed', function(tool) {
      setCursor(tool);
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

    map.events.register('mousemove', map, function(event) {
      eventbus.trigger('map:mouseMoved', event);
    }, true);

    map.events.register('click', map, function(event) {
      eventbus.trigger('map:clicked', { x: event.xy.x, y: event.xy.y });
    });

    addCenterMarkerLayerToMap(map);

    if (applicationModel.getSelectedLayer() === 'speedLimit') {
      showSpeedLimitLayer();
    }

    setCursor(applicationModel.getSelectedTool());
  };
})(this);
