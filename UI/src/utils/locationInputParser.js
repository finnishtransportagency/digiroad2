(function(root) {
  var parse = function(input, selectedLayer) {
    var coordinateRegex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
    var streetRegex = /^(\s*[A-Za-zÀ-ÿ].*)/;
    var roadRegex = /^\s*\d+\s+\d+\s+\d+\s*\d*$/;
    var idOrRoadRegex = /^\d+$/;
    var linkIdRegex = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}:\d+$/; // UUIDv4 followed with ":" and version number
    var liviIdRegex = /^[a-zA-Z]+\d+$/; // At least one letter and one digit, no space between
    var passengerIdRegex = /^MT+\s*\w/gi;
    var associationRoadIdRegex = /^YT+\s*[A-Za-zÀ-ÿ]/gi;

    var matchedCoordinates = input.match(coordinateRegex);
    var matchedStreet = input.match(streetRegex);
    var matchedRoad = input.match(roadRegex);
    var matchedIdOrRoad = input.match(idOrRoadRegex);
    var matchedLinkId = input.match(linkIdRegex);
    var matchedLiviId = input.match(liviIdRegex);
    var matchedPassengerId = input.match(passengerIdRegex);
    var matchedAssociationRoadIdRegex = input.match(associationRoadIdRegex);

    if (selectedLayer === 'massTransitStop' && matchedLiviId) {
      return {type: 'liviId', text: input};
    } else if (selectedLayer === 'massTransitStop' && matchedPassengerId) {
      return {type: 'passengerId', text: input.substring(input.indexOf(' ')+1).replace(/\s+$/g, '')};
    } else if (matchedLinkId) {
      return { type: 'linkId', text: input};
    } else if (matchedCoordinates) {
      return parseCoordinates(matchedCoordinates);
    } else if(matchedAssociationRoadIdRegex){
      return {type: 'roadAssociationName', name: input.slice(3)};
    } else if (matchedStreet) {
      var streetNameAndNumberCheck = /^(\s*[A-Za-zÀ-ÿ].*)\s(\s*\d+\s*)$/;
      var allInput = /^(\s*[A-Za-zÀ-ÿ].*)(\s)(\s*\d+\s*),(\s*[A-Za-zÀ-ÿ].*)/;
      var onlyNameCheck =/^\b[A-Za-zÀ-ÿ]+\b$/;
      if(input.match(onlyNameCheck)){
        return {type: 'street', address: input};
      }else if(input.match(streetNameAndNumberCheck)){
        return {type: 'street', address: input};
      }else if(input.match(allInput)){
        return {type: 'street', address: input};
      }else{
        return { type: 'invalid' };
      }
    } else if (matchedIdOrRoad) {
      return { type: 'assetId', text: input};
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
    return _.omitBy(output, _.isUndefined);
  };

  root.LocationInputParser = {
    parse: parse
  };
})(window);
