(function(root) {
  root.ApplicationModel = function(selectedAssetModel, selectedSpeedLimit) {
    var zoomLevel;
    var selectedLayer = 'asset';
    var readOnly = true;
    var setReadOnly = function(newState) {
      if (readOnly !== newState) {
        readOnly = newState;
        eventbus.trigger('application:readOnly', newState);
      }
    };
    return {
      moveMap: function(zoom, bbox) {
        var hasZoomLevelChanged = zoomLevel !== zoom;
        zoomLevel = zoom;
        eventbus.trigger('map:moved', {selectedLayer: selectedLayer, zoom: zoom, bbox: bbox, hasZoomLevelChanged: hasZoomLevelChanged});
      },
      setSelectedTool: function(tool) {
        eventbus.trigger('tool:changed', tool);
      },
      setZoomLevel: function(level) {
        zoomLevel = level;
      },
      selectLayer: function(layer) {
        selectedLayer = layer;
        eventbus.trigger('layer:selected', layer);
        setReadOnly(true);
      },
      getSelectedLayer: function() {
        return selectedLayer;
      },
      setReadOnly: setReadOnly,
      isReadOnly: function() {
        return readOnly;
      },
      isDirty: function() {
        return selectedSpeedLimit.isDirty() || selectedAssetModel.isDirty();
      },
      assetDragDelay: 100
    };
  };
})(this);
