(function(root) {
  root.RoadMarker = function(data) {
    var cachedMarker = null;
    var cachedDirectionArrow = null;
    var cachedRoadMarker = null;

    var defaultDirectionArrowGraphics = {
      externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg',
      graphicWidth: 30,
      graphicHeight: 16,
      graphicXOffset: -15,
      graphicYOffset: -8
    };

    var floatingDirectionArrowGraphics = {
      externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning.svg',
      graphicWidth: 34,
      graphicHeight: 20,
      graphicXOffset: -17,
      graphicYOffset: -10
    };

    var createDirectionArrow = function() {
      var directionArrowGraphics = _.clone(data.floating ? floatingDirectionArrowGraphics : defaultDirectionArrowGraphics);
      directionArrowGraphics.rotation = validitydirections.calculateRotation(data.bearing, data.validityDirection);
      return new OpenLayers.Feature.Vector(
        new OpenLayers.Geometry.Point(data.group ? data.group.lon : data.lon, data.group ? data.group.lat : data.lat),
        null,
        directionArrowGraphics
      );
    };

    var getMarker = function(shouldCreate) {
      if (shouldCreate || !cachedMarker) {
        cachedRoadMarker = new CalibrationPoint(data);
        cachedMarker = cachedRoadMarker.createMarker();
      }
      return cachedMarker;
    };

    var createNewMarker = function() {
      cachedRoadMarker = new CalibrationPoint(data);
      cachedMarker = cachedRoadMarker.createNewMarker();
      return cachedMarker;
    };

    var getCalibrationPointMarker = function() {
      return cachedRoadMarker;
    };

    var getDirectionArrow = function(shouldCreate) {
      if (shouldCreate || !cachedDirectionArrow) {
        cachedDirectionArrow = createDirectionArrow();
      }
      return cachedDirectionArrow;
    };

    var moveTo = function(lonlat) {
      getDirectionArrow().move(lonlat);
      getCalibrationPointMarker().moveTo(lonlat);
    };

    var select = function() { getCalibrationPointMarker().select(); };

    var deselect = function() { getCalibrationPointMarker().deselect(); };

    var finalizeMove = function() {
      getCalibrationPointMarker().finalizeMove();
    };

    var rePlaceInGroup = function() { getCalibrationPointMarker().rePlaceInGroup(); };

    return {
      getMarker: getMarker,
      createNewMarker: createNewMarker,
      getDirectionArrow: getDirectionArrow,
      moveTo: moveTo,
      select: select,
      deselect: deselect,
      finalizeMove: finalizeMove,
      rePlaceInGroup: rePlaceInGroup
    };
  };
}(this));
