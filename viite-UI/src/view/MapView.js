(function(root) {
  root.MapView = function(map, layers, instructionsPopup) {
    var isInitialized = false;
    var centerMarkerLayer;

    var showAssetZoomDialog = function() {
      instructionsPopup.show('Zoomaa lähemmäksi, jos haluat nähdä kohteita', 2000);
    };

    var minZoomForContent = function() {
      if (applicationModel.getSelectedLayer()) {
        return layers[applicationModel.getSelectedLayer()].minZoomForContent || zoomlevels.minZoomForAssets;
      }
      return zoomlevels.minZoomForAssets;
    };

    var mapMovedHandler = function(mapState) {
      if (mapState.zoom < minZoomForContent()) {
        if (isInitialized && mapState.hasZoomLevelChanged) {
          showAssetZoomDialog();
        }
      }
    };

    var drawCenterMarker = function(position) {
      var icon = new ol.Feature({
        geometry: new ol.geom.Point(position.lon, position.lat)
      });
      var style = new ol.style.Style({
        image: new ol.style.Icon({
          src: './images/center-marker.svg'
        })
      });
      icon.setStyle(style);
      centerMarkerLayer.clear();
      centerMarkerLayer.addFeature(icon);
    };

    var addCenterMarkerLayerToMap = function(map) {
      centerMarkerLayer = new ol.layer.Vector();
      map.addLayer(centerMarkerLayer);
    };

    eventbus.on('application:initialized', function() {
      var zoom = map.getView().getZoom();
      applicationModel.setZoomLevel(zoom);
      if (!zoomlevels.isInAssetZoomLevel(zoom)) {
        showAssetZoomDialog();
      }
      new CrosshairToggle($('.mapplugin.coordinates'));
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

    eventbus.on('coordinates:selected', function(position) {
      if (geometrycalculator.isInBounds(map.getView().getExtent(), position.lon, position.lat)) {
        map.getView().setCenter([position.lon, position.lat]);
        map.getView().setZoom(zoomlevels.getAssetZoomLevelIfNotCloser(map.getZoom()));
      } else {
        instructionsPopup.show('Koordinaatit eivät osu kartalle.', 3000);
      }
    }, this);

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('coordinates:marked', drawCenterMarker, this);

    eventbus.on('layer:selected', function selectLayer(layer, previouslySelectedLayer) {
      var layerToBeHidden = layers[previouslySelectedLayer];
      var layerToBeShown = layers[layer];

      if (layerToBeHidden) layerToBeHidden.hide(map);
      layerToBeShown.show(map);
      applicationModel.setMinDirtyZoomLevel(minZoomForContent());
    }, this);

    map.on('moveend', function() {
      applicationModel.moveMap(map.getView().getZoom(), map.getLayers().getArray()[0].getExtent(), map.getView().getCenter());
    });

    map.on('pointermove', function(event) {
      eventbus.trigger('map:mouseMoved', event);
    }, true);

    map.on('singleclick', function(event) {
      eventbus.trigger('map:clicked', { x: event.coordinate.x, y: event.coordinate.y });
    });

    addCenterMarkerLayerToMap(map);

    setCursor(applicationModel.getSelectedTool());
  };
})(this);
