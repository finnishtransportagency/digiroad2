(function(root) {
  root.PointerTool = function (eventListener, vectorLayer, selectControl, roadCollection, options) {

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

    var updateByPosition = function(mousePoint) {
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), mousePoint[0], mousePoint[1]);
      var projectionOnNearestLine = geometrycalculator.nearestPointOnLine(nearestLine, { x: mousePoint[0], y:mousePoint[1] });
      moveTo(projectionOnNearestLine.x, projectionOnNearestLine.y);
    };

    return {
      activate: activate,
      deactivate: deactivate
    };
  };
})(this);

