(function(root) {
  root.ApplicationModel = function(models) {
    var zoom = {
      level: undefined
    };
    var selectedLayer;
    var selectedTool = 'Select';
    var centerLonLat;
    var minDirtyZoomLevel = zoomlevels.minZoomForRoadLinks;
    var minEditModeZoomLevel = zoomlevels.minZoomForEditMode;
    var readOnly = true;
    var activeButtons = false;
    var setReadOnly = function(newState) {
      if (readOnly !== newState) {
        readOnly = newState;
        setActiveButtons(false);
        setSelectedTool('Select');
        eventbus.trigger('application:readOnly', newState);
      }
    };
    var setActiveButtons = function(newState){
      if(activeButtons !== newState){
        activeButtons = newState;
        eventbus.trigger('application:activeButtons', newState);
      }
    };
    var roadTypeShown = true;
    var isDirty = function() {
      return _.any(models, function(model) { return model.isDirty(); });
    };
    var setZoomLevel = function(level) {
      zoom.level = level;
    };

    function setSelectedTool(tool) {
      if (tool !== selectedTool) {
        selectedTool = tool;
        eventbus.trigger('tool:changed', tool);
      }
    }

    return {
      moveMap: function(zoom, bbox) {
        var hasZoomLevelChanged = zoom.level !== zoom;
        setZoomLevel(zoom);
        centerLonLat = bbox.getCenterLonLat();
        eventbus.trigger('map:moved', {selectedLayer: selectedLayer, zoom: zoom, bbox: bbox, hasZoomLevelChanged: hasZoomLevelChanged});
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
        } else {
          eventbus.trigger('layer:' + selectedLayer + ':shown');
        }
      },
      getSelectedLayer: function() {
        return selectedLayer;
      },
      setReadOnly: setReadOnly,
      setActiveButtons: setActiveButtons,
      isReadOnly: function() {
        return readOnly;
      },
      isActiveButtons: function() {
          return activeButtons;
      },
      isDirty: function() {
        return isDirty();
      },
      canZoomOut: function() {
        return !(isDirty() && (zoom.level <= minDirtyZoomLevel));
      },
        canZoomOutEditMode: function () {
            console.log('zoom: ' + zoom.level);
            console.log('activeButtons: ' + activeButtons);
          return (zoom.level > minEditModeZoomLevel && !readOnly && activeButtons) ||  (!readOnly && !activeButtons) || (readOnly) ;
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
      }
    };
  };
})(this);

