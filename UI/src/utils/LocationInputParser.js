(function(root) {
  var parse = function(input) {
    var coordinateRegex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
    var matchedCoordinates = input.match(coordinateRegex);
    if (matchedCoordinates) {
      return parseCoordinates(matchedCoordinates);
    } else {
      return parseStreet(input);
    }
  };

  var parseCoordinates = function(coordinates) {
    return { type: 'coordinate', lat: parseInt(coordinates[1], 10), lon: parseInt(coordinates[2], 10) };
  };

  var parseStreet = function(input) {
    var result = _.map(input.split(','), _.trim);
    return { type: 'street', street: result[0], municipality: result[1] };
  };

  var LocationInputParser = {
    parse: parse
  };

  root.LocationInputParser = LocationInputParser;
})(window);
