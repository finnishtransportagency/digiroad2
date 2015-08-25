(function(root) {
  root.LocationSearch = function(backend) {
    var geocode = function(street) {
      return backend.getGeocode(street.address).then(function(result) {
        var resultLength = _.get(result, 'results.length');
        var vkmResultToCoordinates = function(r) {
          return { title: r.address, lon: r.x, lat: r.y};
        };
        if (resultLength > 0) {
          return _.map(result.results, vkmResultToCoordinates);
        } else {
          return $.Deferred().reject('Tuntematon katuosoite');
        }
      });
    };

    var getCoordinatesFromRoadAddress = function(road) {
      var constructTitle = function(result) {
        var address = _.get(result, 'alkupiste.tieosoitteet[0]');
        var titleParts = [_.get(address, 'tie'), _.get(address, 'osa'), _.get(address, 'etaisyys'), _.get(address, 'ajorata')];
        return _.some(titleParts, _.isUndefined) ? '' : titleParts.join(' ');
      };
      return backend.getCoordinatesFromRoadAddress(road.roadNumber, road.section, road.distance, road.lane)
        .then(function(result) {
          var lon = _.get(result, 'alkupiste.tieosoitteet[0].point.x');
          var lat = _.get(result, 'alkupiste.tieosoitteet[0].point.y');
          var title = constructTitle(result);
          if (lon && lat) {
            return [{ title: title, lon: lon, lat: lat }];
          } else {
            return $.Deferred().reject('Tuntematon tieosoite');
          }
      });
    };

    var resultFromCoordinates = function(coordinates) {
      var result = _.assign({}, coordinates, { title: coordinates.lat + ',' + coordinates.lon });
      return $.Deferred().resolve([result]);
    };

    this.search = function(searchString) {
      var input = LocationInputParser.parse(searchString);
      var resultByInputType = {
        coordinate: resultFromCoordinates,
        street: geocode,
        road: getCoordinatesFromRoadAddress,
        invalid: function() { return $.Deferred().reject('Syötteestä ei voitu päätellä koordinaatteja, katuosoitetta tai tieosoitetta'); }
      };
      return resultByInputType[input.type](input);
    };
  };
})(this);
