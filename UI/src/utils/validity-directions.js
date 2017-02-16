(function() {
  window.validitydirections = {
    bothDirections: 1,
    sameDirection: 2,
    oppositeDirection: 3,

    switchDirection: function(validityDirection) {
      var switchedDirections = { 1: 1, 2: 3, 3: 2 };
      return switchedDirections[validityDirection];
    },
    //TODO : validate the use of this function in order to work with radius
    calculateRotation: function(bearing, validityDirection) {
      var bearingInPolarCoordinates = geometrycalculator.convertCompassToPolarCoordinates(bearing);
      bearingInPolarCoordinates = bearingInPolarCoordinates * (Math.PI / 180);
      if (validityDirection === validitydirections.oppositeDirection) {
        //return geometrycalculator.oppositeAngle(bearingInPolarCoordinates);
          return bearingInPolarCoordinates + Math.PI;
      } else {
        return bearingInPolarCoordinates;
      }
    }
  };
})();
