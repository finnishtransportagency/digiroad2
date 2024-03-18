(function() {
  window.validitydirections = {
    outsideRoadNetwork: 0,
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
    }
  };
})();
