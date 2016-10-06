(function(root) {
  root.RoadMarker = function(data) {
    var cachedMarker = null;
    var cachedDirectionArrow = null;

    var defaultMarkerGraphics = {
      stroke: true,
      strokeColor: '#000000',
      fill: true
    };

    var createRoadMarker = function() {
      var markerGraphics = _.clone(defaultMarkerGraphics);
//      markerGraphics.rotation = 90;
      return new OpenLayers.Feature.Vector(
        new OpenLayers.Geometry.Point(data.x, data.y),
        null,
        _.merge(markerGraphics, {label: data.roadNumber + ' / ' + data.roadPartNumber})
      );
    };

    var getMarker = function(shouldCreate) {
      if (shouldCreate || !cachedMarker) {
        cachedMarker = createRoadMarker();
      }
      return cachedMarker;
    };

    var getRoadMarker = function() {
      return cachedMarker;
    };

    var getDirectionArrow = function(shouldCreate) {
      if (shouldCreate || !cachedDirectionArrow) {
        cachedDirectionArrow = createRoadMarker();
      }
      return cachedDirectionArrow;
    };

    var moveTo = function(lonlat) {
      getDirectionArrow().move(lonlat);
      getRoadMarker().moveTo(lonlat);
    };

    var select = function() { getRoadMarker().select(); };

    var deselect = function() { getRoadMarker().deselect(); };

    var finalizeMove = function() {
      getRoadMarker().finalizeMove();
    };

    var rePlaceInGroup = function() { getRoadMarker().rePlaceInGroup(); };

    return {
      getMarker: getMarker,
      getDirectionArrow: getDirectionArrow,
      moveTo: moveTo,
      select: select,
      deselect: deselect,
      finalizeMove: finalizeMove,
      rePlaceInGroup: rePlaceInGroup
    };
  };
}(this));
