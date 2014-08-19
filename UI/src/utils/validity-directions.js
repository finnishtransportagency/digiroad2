(function() {
  window.validitydirections = {
    bothDirections: 1,
    sameDirection: 2,
    oppositeDirection: 3,

    switchDirection: function(validityDirection) {
      var switchedDirections = { 1: 1, 2: 3, 3: 2 };
      return switchedDirections[validityDirection];
    }
  };
})();
