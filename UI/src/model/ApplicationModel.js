(function(root) {
  root.ApplicationModel = function(models) {
    var zoom = {
      level: undefined
    };
    var selectedLayer;
    var selectedTool = 'Select';
    var centerLonLat;
    var minDirtyZoomLevel = zoomlevels.minZoomForRoadLinks;
    var readOnly = true;
    var setReadOnly = function(newState) {
      if (readOnly !== newState) {
        readOnly = newState;
        setSelectedTool('Select');
        eventbus.trigger('application:readOnly', newState);
      }
    };
    var roadTypeShown = true;
    var isDirty = function() {
      return _.any(models, function(model) { return model.isDirty(); });
    };
    var setZoomLevel = function(level) {
      zoom.level = level;
    };
    var withRoadAddress = 'false';

    function setSelectedTool(tool) {
      if (tool !== selectedTool) {
        selectedTool = tool;
        eventbus.trigger('tool:changed', tool);
      }
    }

    eventbus.on('toggleWithRoadAddress', function(set){
      withRoadAddress = set;
    });

    return {
      moveMap: function(zoom, bbox, center) {
        var hasZoomLevelChanged = zoom.level !== zoom;
        setZoomLevel(zoom);
        centerLonLat = center;
        eventbus.trigger('map:moved', {selectedLayer: selectedLayer, zoom: zoom, bbox: bbox, center: center, hasZoomLevelChanged: hasZoomLevelChanged});
      },
      setSelectedTool: setSelectedTool,
      getSelectedTool: function() {
        return selectedTool;
      },
      zoom: zoom,
      setZoomLevel: setZoomLevel,
      setMinDirtyZoomLevel: function(level) {
        minDirtyZoomLevel = level;
      },
      selectLayer: function(layer) {
        if (layer !== selectedLayer) {
          var previouslySelectedLayer = selectedLayer;
          selectedLayer = layer;
          setSelectedTool('Select');
          eventbus.trigger('layer:selected', layer, previouslySelectedLayer);
          eventbus.trigger('readOnlyLayer:' + layer + ':shown', layer);
        } else {
          eventbus.trigger('layer:' + selectedLayer + ':shown');
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
        return isDirty();
      },
      canZoomOut: function() {
        return !(isDirty() && (zoom.level <= minDirtyZoomLevel));
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
      },
      getCurrentLocation: function() {
        return centerLonLat;
      },
      getWithRoadAddress: function() {
        return withRoadAddress;
      }
    };
  };
})(this);

