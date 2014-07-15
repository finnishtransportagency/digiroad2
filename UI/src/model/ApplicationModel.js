(function(root) {
  var zoomLevel;
  var selectedLayer = 'asset';
  root.ApplicationModel = {
    moveMap: function(zoom, bbox) {
      var hasZoomLevelChanged = zoomLevel !== zoom;
      zoomLevel = zoom;
      eventbus.trigger('map:moved', {zoom: zoom, bbox: bbox, hasZoomLevelChanged: hasZoomLevelChanged});
    },
    setZoomLevel: function(level) {
      zoomLevel = level;
    },
    selectLayer: function(layer) {
      selectedLayer = layer;
      eventbus.trigger('layer:selected', layer);
    },
    getSelectedLayer: function() {
      return selectedLayer;
    }
  };
})(this);
