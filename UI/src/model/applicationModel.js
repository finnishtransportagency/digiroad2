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
    var appState = 'normal';

    var setApplicationkState = function(newState){
      if (appState !== newState) {
          appState = newState;
          eventbus.trigger('application:state', newState);
      }
    };

    var isFeedbackState = function() {
       return appState === applicationState.Feedback;
    };

    var setReadOnly = function(newState) {
      if (readOnly !== newState) {
        readOnly = newState;
        setSelectedTool('Select');
        eventbus.trigger('application:readOnly', newState);
      }
    };
    var roadTypeShown = true;
    var isDirty = function() {
      return _.some(models, function(model) { return model.isDirty(); });
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
      handleZoomOut: function(map) {
        var nextZoomLevel = zoomlevels.getViewZoom(map) - 1;
        var canZoomOut = this.canZoomOut(nextZoomLevel);
        if (canZoomOut) {
          var outOfEditableBoundaries = this.isOutOfEditableBoundaries(nextZoomLevel);
          if (outOfEditableBoundaries) {
            eventbus.trigger('zoomedOutOfEditableBoundaries');
          }
          return true;
        } else {
          new Confirm();
          return false;
        }
      },
      handleZoomIn: function(map) {
        var nextZoomLevel = zoomlevels.getViewZoom(map) + 1;
        var outOfEditableBoundaries = this.isOutOfEditableBoundaries(nextZoomLevel);
        if (!outOfEditableBoundaries) {
          eventbus.trigger('zoomedIntoEditableBoundaries');
        }
        return true;
      },
      canZoomOut: function(zoomLevel) {
        return !(isDirty() && (this.isOutOfEditableBoundaries(zoomLevel)));
      },
      isOutOfEditableBoundaries: function(zoomLevel) {
        return (zoomLevel < minDirtyZoomLevel);
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
      },
      setApplicationkState: setApplicationkState,
      getApplicationState: function(){
        return appState;
      }
    };
  };
})(this);

