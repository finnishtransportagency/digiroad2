(function(root) {
  root.PointsCursorTool = function (eventListener, vectorLayer, selectControl, roadCollection, options) {

    var settings = _.extend({
      style: function(){}
    }, options);

    var moveTo = function(coordinatex, coordinatey) {
      selectControl.removeFeatures(function(feature) {
        return feature.getProperties().type === 'pointer';
      });
      var feature = new ol.Feature({geometry: new ol.geom.Point([coordinatex, coordinatey]), type: 'pointer'});
      var style = settings.style(feature);
      feature.setStyle(style);
      selectControl.addNewFeature([feature], true);
    };

    var remove = function () {
      selectControl.removeFeatures(function(feature) {
        return feature && feature.getProperties().type === 'pointer';
      });
    };

    var deactivate = function() {
      eventListener.stopListening(eventbus, 'map:mouseMoved');
      remove();
    };

    var activate = function() {
      eventListener.listenTo(eventbus, 'map:mouseMoved', function(event) {
        updateByPosition(event.coordinate);
      });
    };

    var toggleWalkingCycling = function () {
      settings.walkingCycling = !settings.walkingCycling;
    };

    var excludeStateRoadLinks = function () {
      settings.excludeStateRoadLinks = true;
    };

    var includeStateRoadLinks = function () {
      settings.excludeStateRoadLinks = false;
    };

    var updateByPosition = function (mousePoint) {
      var roadLinks;
      if (settings.walkingCycling === true) {
        roadLinks = roadCollection.getRoadsForCarPedestrianCycling();
      } else {
        roadLinks = roadCollection.getRoadsForPointAssets();
      }

      var filteredRoadLinks;
      if( settings.excludeStateRoadLinks === true) {
        filteredRoadLinks = _.filter(roadLinks, function(link) {
          return link.administrativeClass !== "State";
        });
      } else {
        filteredRoadLinks = roadLinks;
      }

      if (filteredRoadLinks && filteredRoadLinks.length !== 0) {
        var nearestLine = geometrycalculator.findNearestLine(filteredRoadLinks, mousePoint[0], mousePoint[1]);
        var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, {
          x: mousePoint[0],
          y: mousePoint[1]
        });
        moveTo(projectionOnNearestLine.x, projectionOnNearestLine.y);
      }
    };

    return {
      activate: activate,
      deactivate: deactivate,
      toggleWalkingCycling: toggleWalkingCycling,
      excludeStateRoadLinks: excludeStateRoadLinks,
      includeStateRoadLinks: includeStateRoadLinks
    };
  };
})(this);

