(function(root) {
  root.ApplicationModel = function() {
    var zoomLevel;
    var selectedLayer = 'asset';
    var readOnly = true;
    return {
      moveMap: function(zoom, bbox) {
        var hasZoomLevelChanged = zoomLevel !== zoom;
        zoomLevel = zoom;
        eventbus.trigger('map:moved', {selectedLayer: selectedLayer, zoom: zoom, bbox: bbox, hasZoomLevelChanged: hasZoomLevelChanged});
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
      },
      setReadOnly: function(newState) {
        readOnly = newState;
        eventbus.trigger('application:readOnly', newState);
      },
      isReadOnly: function() {
        return readOnly;
      },
      assetDragDelay: 100
    };
  };
})(this);
