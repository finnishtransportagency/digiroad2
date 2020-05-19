(function() {
  window.validitydirections = {
    bothDirections: 1,
    sameDirection: 2,
    oppositeDirection: 3,
    unknown: 99,

    switchDirection: function(validityDirection) {
      var switchedDirections = {1: 1, 2: 3, 3: 2, 99: 2};
      return switchedDirections[validityDirection];
    },
    calculateRotation: function(bearing, validityDirection) {
      var bearingInPolarCoordinates = geometrycalculator.convertCompassToPolarCoordinates(bearing);
      var bearingInPolarCoordinatesRadius = geometrycalculator.deg2rad(bearingInPolarCoordinates);

      if (validityDirection === validitydirections.oppositeDirection) {
        return geometrycalculator.oppositeAngleRadius(bearingInPolarCoordinatesRadius);
      } else {
        return bearingInPolarCoordinatesRadius;
      }
    },
    filterLanesByDirection: function (lanes, validityDirection) {
      switch (validityDirection) {
        case validitydirections.sameDirection: return _.filter(lanes, function (lane) { return lane.toString().charAt(0) == 1; });
        case validitydirections.oppositeDirection: return _.filter(lanes, function (lane) {  return lane.toString().charAt(0) == 2;  });
        case validitydirections.bothDirections:
        case validitydirections.unknown: return lanes;
      }
    }
  };
})();
