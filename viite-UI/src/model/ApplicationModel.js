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

    var setCurrentAction = function(action) {
      currentAction = action;
    };

    var resetCurrentAction = function(){
      currentAction = null;
    };

    var addSpinner = function () {
      jQuery('.container').append('<div class="spinner-overlay modal-overlay"><svg  class="spinner"  viewBox="0 0 500 500" version="1.1"></svg></div>');
      createMHDSpinner(".spinner",false,0.6,100,30,500);
    };

    function createMHDSpinner(className,bbcircle,duration,spinnerwidth,margintoblackcircle,width) {
      var svg = document.querySelector(className);
      var wrapper = svg.parentNode;
      //var width=svg.viewBox.animVal.width; for some reason test fails with this
      var halfwidth=(width/2),outerCurve=(halfwidth-margintoblackcircle);
      var innerCurve=(halfwidth-margintoblackcircle-spinnerwidth);
      var path, preCurvePoints = [[outerCurve, outerCurve - innerCurve], [outerCurve, outerCurve - innerCurve]], curvePoints, angle;
      while (svg.firstChild)
        svg.removeChild(svg.firstChild);
      if (bbcircle){
        var circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        circle.setAttributeNS(null, 'cx',halfwidth );
        circle.setAttributeNS(null, 'cy', halfwidth);
        circle.setAttributeNS(null, 'r',  halfwidth);
        circle.setAttributeNS(null, 'style', 'fill: black' );
        svg.appendChild(circle);
      }
      for (i = 0,shade = 0; 255 > i; i++) {
        path = document.createElementNS("http://www.w3.org/2000/svg", "path");
        angle = (i + 1)*Math.PI*2/255;
        curvePoints = [[halfwidth + outerCurve*Math.sin(angle), halfwidth - outerCurve*Math.cos(angle)], [halfwidth + innerCurve*Math.sin(angle), halfwidth - innerCurve*Math.cos(angle)]];
        path.setAttribute("d", "M " + preCurvePoints[0] + " A " + outerCurve + " " + outerCurve + ", 0, 0, 1, " + curvePoints[0] + " A " + (outerCurve - innerCurve)/2 + " " + (outerCurve - innerCurve)/2 + ", 0, 0, 1, " + curvePoints[1] + " A " + innerCurve + " " + innerCurve + ", 0, 0, 0, " + preCurvePoints[1]);
        path.setAttribute("fill", "rgb(" + [shade, shade, shade]+ ")");
        preCurvePoints = curvePoints;
        shade++;
        svg.appendChild(path);
      }
      var animateMotion = document.createElementNS("http://www.w3.org/2000/svg", "animateTransform");
      animateMotion.setAttributeNS(null, "attributeName", "transform");
      animateMotion.setAttributeNS(null, "attributeType", "XML");
      animateMotion.setAttributeNS(null, "type", "rotate");
      animateMotion.setAttributeNS(null, "dur",+duration+"s");
      animateMotion.setAttributeNS(null, "repeatCount","indefinite");
      animateMotion.setAttributeNS(null, "from", "0 0 0");
      animateMotion.setAttributeNS(null, "to", "360 0 0");
      svg.appendChild(animateMotion);
      animateMotion.beginElement();
    }

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

