(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer) {

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: function(feature) {
        eventbus.trigger('linkProperties:selected', feature.attributes);
      },
      onUnselect: function() {
        eventbus.trigger('linkProperties:unselected');
      }
    });
    map.addControl(selectControl);

    var handleMapMoved = function(state) {
      if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === 'linkProperties') {
        start();
      } else {
        stop();
      }
    };
    eventbus.on('map:moved', handleMapMoved);

    var start = function() {
      selectControl.activate();
    };

    var stop = function() {
      selectControl.deactivate();
    };

    var show = function(map) {
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        start();
      }
    };

    var hide = function() {
      stop();
    };

    return {
      show: show,
      hide: hide
    };
  };
})(this);
