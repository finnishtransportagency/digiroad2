(function(root) {
  var parse = function(input) {
      var coordinateRegex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
      var matchedCoordinates = input.match(coordinateRegex);
      var parsedOutput;
      if (matchedCoordinates) {
          return parseCoordinates(matchedCoordinates);
      } else {
        return null;
      }
  };

  var parseCoordinates = function(coordinates) {
      return { type: 'coordinate', lat: parseInt(coordinates[1], 10), lon: parseInt(coordinates[2], 10) };
  };

  var LocationInputParser = {
    parse: parse
  };

  root.LocationInputParser = LocationInputParser;
})(window);
