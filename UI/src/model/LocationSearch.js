(function(root) {
  root.LocationSearch = function(backend) {
    var geocode = function(street) {
      return backend.getGeocode(street.address).then(function(result) {
        var lon = _.get(result, 'results[0].x');
        var lat = _.get(result, 'results[0].y');
        if (lon && lat) {
          return { lon: lon, lat: lat };
        } else {
          return $.Deferred().reject('Tuntematon katuosoite');
        }
      });
    };

    var getCoordinatesFromRoadAddress = function(road) {
      return backend.getCoordinatesFromRoadAddress(road.roadNumber, road.section, road.distance, road.lane)
        .then(function(result) {
          var lon = _.get(result, 'alkupiste.tieosoitteet[0].point.x');
          var lat = _.get(result, 'alkupiste.tieosoitteet[0].point.y');
          if (lon && lat) {
            return { lon: lon, lat: lat };
          } else {
            return $.Deferred().reject('Tuntematon tieosoite');
          }
      });
    };

    this.search = function(searchString) {
      var input = LocationInputParser.parse(searchString);
      var resultByInputType = {
        coordinate: function(coordinates) { return $.Deferred().resolve(coordinates); },
        street: geocode,
        road: getCoordinatesFromRoadAddress,
        invalid: function() { return $.Deferred().reject('Syötteestä ei voitu päätellä koordinaatteja, katuosoitetta tai tieosoitetta'); }
      };
      return resultByInputType[input.type](input);
    };
  };
})(this);
