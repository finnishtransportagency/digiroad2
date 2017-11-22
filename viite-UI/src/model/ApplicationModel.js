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
    var continueButton = false;
    var openProject = false;
    var projectButton = false;
    var projectFeature;
    var actionCalculating = 0;
    var actionCalculated = 1;
    var currentAction;
    var selectionType = 'all';

    var getSelectionType = function (){
      return selectionType;
    };

    var getContinueButtons = function(){
      return continueButton;
    };

    var toggleSelectionTypeAll = function () {
      selectionType = 'all';
    };

    var toggleSelectionTypeFloating = function(){
      selectionType = 'floating';
    };

    var toggleSelectionTypeUnknown = function (){
      selectionType = 'unknown';
    };

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

    var setProjectFeature = function(featureLinkID){
      projectFeature = featureLinkID;
    };

    var setProjectButton = function(newState){
      if(projectButton !== newState){
        projectButton = newState;
        eventbus.trigger('application:projectButton', newState);
      }
    };
    var setOpenProject = function(newState){
      if(openProject !== newState){
        openProject = newState;
      }
    };

    var setContinueButton = function(newState){
      if(continueButton !== newState){
        continueButton = newState;
        eventbus.trigger('application:pickActive', newState);
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

    var getCurrentAction = function() {
      return currentAction;
    };

    var getUserGeoLocation = function() {
      return {
        x: centerLonLat[0],
        y:centerLonLat[1],
        zoom:zoom.level
      };
    };

    var setCurrentAction = function(action) {
      currentAction = action;
    };

    var resetCurrentAction = function(){
      currentAction = null;
    };

    var addSpinner = function () {
      jQuery('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
    };

    var removeSpinner = function(){
      jQuery('.spinner-overlay').remove();
    };

    return {
      getCurrentAction: getCurrentAction,
      setCurrentAction: setCurrentAction,
      resetCurrentAction: resetCurrentAction,
      actionCalculating: actionCalculating,
      actionCalculated: actionCalculated,
      moveMap: function(zoom, bbox, center) {
        var hasZoomLevelChanged = zoom.level !== zoom;
        setZoomLevel(zoom);
        centerLonLat = center;
        eventbus.trigger('map:moved', {selectedLayer: selectedLayer, zoom: zoom, bbox: bbox, center: center, hasZoomLevelChanged: hasZoomLevelChanged});
      },
      getUserGeoLocation: getUserGeoLocation,
      setSelectedTool: setSelectedTool,
      getSelectedTool: function() {
        return selectedTool;
      },
      zoom: zoom,
      setZoomLevel: setZoomLevel,
      setMinDirtyZoomLevel: function(level) {
        minDirtyZoomLevel = level;
      },
      selectLayer: function(layer, toggleStart, noSave) {
        if (layer !== selectedLayer) {
          var previouslySelectedLayer = selectedLayer;
          selectedLayer = layer;
          setSelectedTool('Select');
          eventbus.trigger('layer:selected', layer, previouslySelectedLayer, toggleStart);
        } else if(layer === 'linkProperty' && toggleStart) {
          eventbus.trigger('roadLayer:toggleProjectSelectionInForm', layer, noSave);
        }
      },
      getSelectedLayer: function() {
        return selectedLayer;
      },
      setReadOnly: setReadOnly,
      setActiveButtons: setActiveButtons,
      setProjectButton: setProjectButton,
      setProjectFeature: setProjectFeature,
      setContinueButton: setContinueButton,
      getContinueButtons: getContinueButtons,
      setOpenProject : setOpenProject,
      getProjectFeature: function() {
        return projectFeature;
      },
      addSpinner: addSpinner,
      removeSpinner: removeSpinner,
      isReadOnly: function() {
        return readOnly;
      },
      isActiveButtons: function() {
        return activeButtons;
      },
      isProjectButton: function() {
        return projectButton;
      },
      isContinueButton: function() {
        return continueButton;
      },
      isProjectOpen: function() {
        return openProject;
      },
      isDirty: function() {
        return isDirty();
      },
      canZoomOut: function() {
        return !(isDirty() && (zoom.level <= minDirtyZoomLevel));
      },
      canZoomOutEditMode: function () {
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
      },
      getSelectionType: getSelectionType,
      toggleSelectionTypeAll: toggleSelectionTypeAll,
      toggleSelectionTypeFloating: toggleSelectionTypeFloating,
      toggleSelectionTypeUnknown: toggleSelectionTypeUnknown
    };
  };
})(this);

