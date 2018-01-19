(function(root) {
  var parse = function(input, selectedLayer) {
    var coordinateRegex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
    var streetRegex = /^\s*[^0-9,]+\s*\d*(,\s*[^0-9,]+\s*$)?/;
    var roadRegex = /^\s*\d*\s*\d*\s*\d*\s*\d+$/;
    var idOrRoadRegex = /^\d+$/;
    var liviIdRegex =/^[a-zA-Z]+\d+$/; // At least one letter and one digit, no space between

    var matchedCoordinates = input.match(coordinateRegex);
    var matchedStreet = input.match(streetRegex);
    var matchedRoad = input.match(roadRegex);
    var matchedIdOrRoad = input.match(idOrRoadRegex);
    var matchedLiviId = input.match(liviIdRegex);

    if (selectedLayer === 'massTransitStop' && matchedLiviId) {
      return {type: 'liviId', text: input};
    } else if (matchedCoordinates) {
      return parseCoordinates(matchedCoordinates);
    } else if (matchedStreet) {
      return {type: 'street', address: input};
    } else if (matchedIdOrRoad) {
      return { type: 'idOrRoadNumber', text:input};
    } else if (matchedRoad) {
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

  root.LocationInputParser = {
    parse: parse
  };
})(window);
