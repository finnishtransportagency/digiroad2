(function(root) {
  var parse = function(input) {
    var coordinateRegex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
    var streetRegex = /^\s*([A-Za-z]+)\s*(\d+),\s*([A-Za-z]+)\s*$/;
    var roadRegex = /^\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*$/;

    var matchedCoordinates = input.match(coordinateRegex);
    if (matchedCoordinates) {
      return parseCoordinates(matchedCoordinates);
    } else if (input.match(streetRegex)) {
      return parseStreet(input);
    } else if (input.match(roadRegex)) {
      return parseRoad(input);
    } else {
      return { type: 'invalid' };
    }
  };

  var parseCoordinates = function(coordinates) {
    return { type: 'coordinate', lat: _.parseInt(coordinates[1]), lon: _.parseInt(coordinates[2]) };
  };

  var parseStreet = function(input) {
    var result = _.map(input.split(','), _.trim);
    return { type: 'street', street: result[0], municipality: result[1] };
  };

  var parseRoad = function(input) {
    var result = _.map(input.split(','), _.parseInt);
    return { type: 'road', roadNumber: result[0], section: result[1], distance: result[2] };
  };

  var LocationInputParser = {
    parse: parse
  };

  root.LocationInputParser = LocationInputParser;
})(window);
