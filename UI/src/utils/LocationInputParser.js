(function(root) {
  var parse = function(input) {
    var coordinateRegex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
    var streetRegex = /^\s*[A-Za-z]+\s*\d*,\s*[A-Za-z]+\s*$/;
    var roadRegex = /^\s*\d*\s*\d*\s*\d*\s*\d+$/;

    var matchedCoordinates = input.match(coordinateRegex);
    if (matchedCoordinates) {
      return parseCoordinates(matchedCoordinates);
    } else if (input.match(streetRegex)) {
      return { type: 'street', address: input };
    } else if (input.match(roadRegex)) {
      return parseRoad(input);
    } else {
      return { type: 'invalid' };
    }
  };

  var parseCoordinates = function(coordinates) {
    return { type: 'coordinate', lat: _.parseInt(coordinates[1]), lon: _.parseInt(coordinates[2]) };
  };

  var parseRoad = function(input) {
    var parsed = _.map(_.words(input), _.parseInt);
    var output = { type: 'road', roadNumber: parsed[0], section: parsed[1], distance: parsed[2], lane: parsed[3] };
    return _.omit(output, _.isUndefined);
  };

  var LocationInputParser = {
    parse: parse
  };

  root.LocationInputParser = LocationInputParser;
})(window);
