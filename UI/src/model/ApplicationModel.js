(function(root) {
  root.ApplicationModel = function(models) {
    var zoomLevel;
    var selectedLayer = 'massTransitStop';
    var selectedTool = 'Select';
    var readOnly = true;
    var setReadOnly = function(newState) {
      if (readOnly !== newState) {
        readOnly = newState;
        eventbus.trigger('application:readOnly', newState);
      }
    };
    var roadTypeShown = true;

    return {
      moveMap: function(zoom, bbox) {
        var hasZoomLevelChanged = zoomLevel !== zoom;
        zoomLevel = zoom;
        eventbus.trigger('map:moved', {selectedLayer: selectedLayer, zoom: zoom, bbox: bbox, hasZoomLevelChanged: hasZoomLevelChanged});
      },
      setSelectedTool: function(tool) {
        if (tool !== selectedTool) {
          selectedTool = tool;
          eventbus.trigger('tool:changed', tool);
        }
      },
      getSelectedTool: function() {
        return selectedTool;
      },
      setZoomLevel: function(level) {
        zoomLevel = level;
      },
      selectLayer: function(layer) {
        if (layer !== selectedLayer) {
          var previouslySelectedLayer = selectedLayer;
          selectedLayer = layer;
          eventbus.trigger('layer:selected', layer, previouslySelectedLayer);
          setReadOnly(true);
        }
      },
      getSelectedLayer: function() {
        return selectedLayer;
      },
      setReadOnly: setReadOnly,
      isReadOnly: function() {
        return readOnly;
      },
      isDirty: function() {
        return _.any(models, function(model) { return model.isDirty(); });
      },
      assetDragDelay: 100,
      assetGroupingDistance: 36,
      setRoadTypeShown: function(bool) {
        if (roadTypeShown !== bool) {
          roadTypeShown = bool;
          eventbus.trigger('road-type:selected', roadTypeShown);
        }
      },
      isRoadTypeShown: function() {
        return selectedLayer === 'massTransitStop' && roadTypeShown;
      }
    };
  };
})(this);

