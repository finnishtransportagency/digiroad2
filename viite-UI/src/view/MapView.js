//noinspection ThisExpressionReferencesGlobalObjectJS
(function(root) {
  root.MapView = function(map, layers, instructionsPopup) {
    var isInitialized = false;
    var centerMarkerLayer = new ol.source.Vector({});
    var enableShiftModifier = false;

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
      if (mapState.zoom < minZoomForContent() && (isInitialized && mapState.hasZoomLevelChanged)) {
        showAssetZoomDialog();
      }
    };

    var drawCenterMarker = function(position) {
      //Create a new Feature with the exact point in the center of the map
      var icon = new ol.Feature({
        geometry: new ol.geom.Point(position)
      });

      //create the style of the icon of the 'Merkistse' Button
      var styleIcon = new ol.style.Style({
        image: new ol.style.Icon({
          src: 'images/center-marker.svg'
        })
      });

      //add Icon Style
      icon.setStyle(styleIcon);
      //clear the previous icon
      centerMarkerLayer.clear();
      //add icon to vector source
      centerMarkerLayer.addFeature(icon);
    };

    var vectorLayer = new ol.layer.Vector({
      source: centerMarkerLayer
    });
    vectorLayer.set('name','mapViewVectorLayer');

    var addCenterMarkerLayerToMap = function(map) {
      map.addLayer(vectorLayer);
    };

    eventbus.on('application:initialized layer:fetched', function() {
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
      var cursor = {'Select': 'default', 'Add': 'crosshair', 'Cut': 'crosshair', 'Copy': 'copy'};
      map.getViewport().style.cursor = cursor[tool];
    };

    eventbus.on('tool:changed', function(tool) {
      setCursor(tool);
    });

    eventbus.on('coordinates:selected', function(position) {
      if (geometrycalculator.isInBounds(map.getView().calculateExtent(map.getSize()), position.lon, position.lat)) {
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
      enableShiftModifier = (layer === "roadAddressProject");
    }, this);

    eventbus.on('roadAddressProject:selected', function selectLayer(id, layer, previouslySelectedLayer) {
      var layerToBeHidden = layers[previouslySelectedLayer];
      var layerToBeShown = layers[layer];

      if (layerToBeHidden) layerToBeHidden.hide(map);
      layerToBeShown.show(map);
      applicationModel.setMinDirtyZoomLevel(minZoomForContent());
    }, this);

    map.on('moveend', function() {
      applicationModel.moveMap(map.getView().getZoom(), map.getLayers().getArray()[0].getExtent(), map.getView().getCenter());
      setCursor(applicationModel.getSelectedTool());
    });

    map.on('pointermove', function(event) {
      var pixel = map.getEventPixel(event.originalEvent);
      eventbus.trigger('map:mouseMoved', event, pixel);
    }, true);
    
    map.on('singleclick', function(event) {
      eventbus.trigger('map:clicked', { x: event.coordinate.shift(), y: event.coordinate.shift() });
    });
    map.on('dblclick', function(event) {
      eventbus.trigger('map:dblclicked', { x: event.coordinate.shift(), y: event.coordinate.shift() });
    });

    addCenterMarkerLayerToMap(map);

    //initial cursor when the map user is not dragging the map
    map.getViewport().style.cursor = "initial";

    //when the map is moving (the user is dragging the map)
    //only work's when the developer options in the browser aren't open
    map.on('pointerdrag', function(evt) {
      map.getViewport().style.cursor = "move";
    });

    //when the map dragging stops the cursor value returns to the initial one
    map.on('pointerup', function(evt) {
      if(applicationModel.getSelectedTool() == 'Select')
      map.getViewport().style.cursor = "initial";
    });

    $('body').on('keydown', function(evt){
      if(evt.shiftKey && enableShiftModifier)
        map.getViewport().style.cursor = "copy";
    });

    $('body').on('keyup', function(evt){
      if(evt.which === 16) // shift key up
        map.getViewport().style.cursor = "initial";
    });

    setCursor(applicationModel.getSelectedTool());
  };
})(this);
