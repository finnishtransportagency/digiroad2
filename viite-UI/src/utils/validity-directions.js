(function() {
  window.validitydirections = {
    bothDirections: 1,
    sameDirection: 2,
    oppositeDirection: 3,

    switchDirection: function(validityDirection) {
      var switchedDirections = { 1: 1, 2: 3, 3: 2 };
      return switchedDirections[validityDirection];
    },
    calculateRotation: function(bearing, validityDirection) {
      var bearingInPolarCoordinates = geometrycalculator.convertCompassToPolarCoordinates(bearing);
      if (validityDirection === validitydirections.oppositeDirection) {
        return geometrycalculator.oppositeAngle(bearingInPolarCoordinates);
      } else {
        return bearingInPolarCoordinates;
      }
    }
  };
})();
