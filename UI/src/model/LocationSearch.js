(function(root) {
  root.LocationSearch = function(backend) {
    var geocode = function(street) {
      return backend.getGeocode(street.address).then(function(result) {
        return { lon: result.results[0].x, lat: result.results[0].y };
      });
    };

    var getCoordinatesByRoadAddress = function(road) {
      throw new Error("not implemented yet");
    };

    this.search = function(searchString) {
      var input = LocationInputParser.parse(searchString);
      var resultByInputType = {
        coordinate: function(coordinates) { return $.Deferred().resolve(coordinates); },
        street: geocode,
        road: getCoordinatesByRoadAddress,
        invalid: function() { return $.Deferred().reject(); }
      };
      return resultByInputType[input.type](input);
    };
  };
})(this);
