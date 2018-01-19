(function(root) {
  root.MapView = function(map, layers, instructionsPopup) {
    var isInitialized = false;
    var centerMarkerSource = new ol.source.Vector({});

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
        geometry: new ol.geom.Point(position)
      });
      var style = new ol.style.Style({
        image: new ol.style.Icon({
          src: 'images/center-marker.svg'
        })
      });
      icon.setStyle(style);
      centerMarkerSource.clear();
      centerMarkerSource.addFeature(icon);
    };

    var centerMarkerLayer = new ol.layer.Vector({
       source : centerMarkerSource
    });

    var addCenterMarkerLayerToMap = function(map) {
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
      if (geometrycalculator.isInBounds(map.getProperties().extent, position.lon, position.lat)) {
        map.getView().setCenter([position.lon, position.lat]);
        map.getView().setZoom(zoomlevels.getAssetZoomLevelIfNotCloser(map.getView().getZoom()));
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

    map.on('moveend', function(event) {
      var target = document.getElementById(map.getTarget());
      target.style.cursor = '';
      applicationModel.moveMap(map.getView().getZoom(), map.getView().calculateExtent(map.getSize()), map.getView().getCenter());
    });

    map.on('pointermove', function(event) {
      var pixel = map.getEventPixel(event.originalEvent);
      var hit = map.hasFeatureAtPixel(pixel);
      var target = document.getElementById(map.getTarget());
      target.style.cursor = hit ? 'pointer' : (target.style.cursor === 'move' ? target.style.cursor : '');
      eventbus.trigger('map:mouseMoved', event);
    }, true);

    map.on('singleclick', function(event) {
      eventbus.trigger('map:clicked', { x: event.coordinate.shift(), y: event.coordinate.shift() });
    });

    map.on('pointerdrag', function(event) {
      var target = document.getElementById(map.getTarget());
      target.style.cursor = 'move';
    });

    addCenterMarkerLayerToMap(map);

    setCursor(applicationModel.getSelectedTool());
  };
})(this);
