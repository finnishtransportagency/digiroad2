(function(root) {
  root.LocationSearch = function(backend, applicationModel) {
    var selectedLayer;

    /**
     * Search by street address
     *
     * @param street
     * @returns {*}
     */
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

    /**
     * Combined numerical value search (asset id and road number, which is part of road address)
     *
     * @param input
     * @returns {*}
     */
    var idOrRoadNumber = function(input) {
      if (selectedLayer === 'massTransitStop') {
        return roadNumberAndNationalIdSearch(input);
      } else if (selectedLayer === 'linkProperty') {
        return roadNumberAndRoadLinkSearch(input);
      } else if (selectedLayer === 'speedLimit') {
        return roadNumberAndSpeedLimitSearch(input.text);
      } else {
        return getCoordinatesFromRoadAddress({roadNumber: input.text});
      }
    };

    /**
     * Speed limit id search, combined with road number search
     *
     * @param input
     * @returns {*}
     */
    var roadNumberAndSpeedLimitSearch = function(input){
      var roadNumberSearch = backend.getCoordinatesFromRoadAddress(input);
      var speedlimitSearch= backend.getSpeedLimitsLinkIDFromSegmentID(input);
      return $.when(
        speedlimitSearch, roadNumberSearch).then(function(speedlimitdata,roadData) {
        var returnObject = roadLocationAPIResultParser(roadData);
        var linkFound =_.get(speedlimitdata[0], 'success');
        if (linkFound === true) {
          var linkid = _.get(speedlimitdata[0], 'linkId');
          var y = _.get(speedlimitdata[0], 'latitude');
          var x= _.get(speedlimitdata[0], 'longitude');
          var title = "Speed Limit-ID: " + input;
            returnObject.push({title: title, lon: x, lat: y, linkid:linkid});
        }
        if (returnObject.length===0){
          return $.Deferred().reject('Haulla ei löytynyt tuloksia');
        }
        return returnObject;
        });
    };

    /**
     * Link id search, combined with road number search
     *
     * @param input
     * @returns {*}
     */
    var roadNumberAndRoadLinkSearch= function(input) {
      var roadLinkSearch = backend.getRoadLinkToPromise(input.text);
      var roadNumberSearch = backend.getCoordinatesFromRoadAddress(input.text);
      return $.when(roadLinkSearch, roadNumberSearch).then(function(linkdata,roadData) {
        var returnObject = roadLocationAPIResultParser(roadData);
        var linkFound =_.get(linkdata[0], 'success');
        if (linkFound === true) {
          var x = _.get(linkdata[0], 'middlePoint.x');
          var y = _.get(linkdata[0], 'middlePoint.y');
          var title = "Link-ID: " + input.text;
            if (returnObject.length > 0) {
              returnObject.push({title: title, lon: x, lat: y});
            } else {
              returnObject = [{title: title, lon: x, lat: y}];
            }
        }
        if (returnObject.length === 0){
          return $.Deferred().reject('Haulla ei löytynyt tuloksia');
        }
        return returnObject;
      });
    };

    /**
     * Mass transit stop national id search, combined with road number search
     *
     * @param input
     * @returns {*}
     */
    var roadNumberAndNationalIdSearch = function(input) {
      return $.when(backend.getMassTransitStopByNationalIdForSearch(input.text), backend.getCoordinatesFromRoadAddress(input.text)).then(function(result,roadData) {
        var returnObject = roadLocationAPIResultParser(roadData);
         if (_.get(result[0], 'success')) {
          var lon = _.get(result[0], 'lon');
          var lat = _.get(result[0], 'lat');
          var title = input.text + ' (valtakunnallinen id)';
          returnObject.push({title: title, lon: lon, lat: lat, nationalId: input.text});
         }
        if (returnObject.length === 0){
          return $.Deferred().reject('Haulla ei löytynyt tuloksia');
        }
            return returnObject;
      });
    };

    /**
     * Road address search
     *
     * @param roadData
     * @returns {*}
     */
    function roadLocationAPIResultParser(roadData){
      var constructTitle = function(result) {
        var address = _.get(result, 'alkupiste.tieosoitteet[0]');
        var titleParts = [_.get(address, 'tie'), _.get(address, 'osa'), _.get(address, 'etaisyys'), _.get(address, 'ajorata')];
        return _.some(titleParts, _.isUndefined) ? '' : titleParts.join(' ');
      };
      var lon = _.get(roadData, 'alkupiste.tieosoitteet[0].point.x');
      var lat = _.get(roadData, 'alkupiste.tieosoitteet[0].point.y');
      var title = constructTitle(roadData);
      if (lon && lat) {
        return  [{title: title, lon: lon, lat: lat}];
      } else {
        return [];
      }
    }

    /**
     * Search by mass transit stop Livi-id
     *
     * @param input
     * @returns {*}
     */
    var  massTransitStopLiviIdSearch = function(input) {
      return $.when(backend.getMassTransitStopByLiviIdForSearch(input.text), backend.getCoordinatesFromRoadAddress(input.text)).then(function(result,roadData) {
        var returnObject = roadLocationAPIResultParser(roadData);
        if (_.get(result[0], 'success')) {
          var lon = _.get(result[0], 'lon');
          var lat = _.get(result[0], 'lat');
          var title = input.text + ' (Livi-tunnus)';
          returnObject.push({title: title, lon: lon, lat: lat, nationalId: result.nationalId});
        }
          if (returnObject.length === 0){
            return $.Deferred().reject('Haulla ei löytynyt tuloksia');
          }
          return returnObject;
      });
    };
    /**
     * Get road address coordinates
     *
     * @param road
     * @returns {*}
     */
    var getCoordinatesFromRoadAddress = function(road) {
      return backend.getCoordinatesFromRoadAddress(road.roadNumber, road.section, road.distance, road.lane)
        .then(function(resultfromapi) {
          var searchResult = roadLocationAPIResultParser(resultfromapi);
          if (searchResult.length === 0) {
            return $.Deferred().reject('Tuntematon tieosoite');
          } else {
            return searchResult;
          }
        });
    };

    /**
     * Search by coordinates
     *
     * @param coordinates
     * @returns {*|String}
     */
    var resultFromCoordinates = function(coordinates) {
      var result = _.assign({}, coordinates, { title: coordinates.lat + ',' + coordinates.lon });
      return $.Deferred().resolve([result]);
    };

    /**
     * Main search method
     *
     * @param searchString
     * @returns {*}
     */
    this.search = function(searchString) {
      function addDistance(item) {
        var currentLocation = applicationModel.getCurrentLocation();

        var distance = GeometryUtils.distanceOfPoints({
          x: currentLocation.lon,
          y: currentLocation.lat
        }, {
          x: item.lon,
          y: item.lat
        });
        return _.assign(item, {
          distance: distance
        });
      }

      selectedLayer = applicationModel.getSelectedLayer();
      var input = LocationInputParser.parse(searchString, selectedLayer);
      var resultByInputType = {
        coordinate: resultFromCoordinates,
        street: geocode,
        road: getCoordinatesFromRoadAddress,
        idOrRoadNumber: idOrRoadNumber,
        liviId: massTransitStopLiviIdSearch,
        invalid: function() { return $.Deferred().reject('Syötteestä ei voitu päätellä koordinaatteja, katuosoitetta tai tieosoitetta'); }
      };

      var results = resultByInputType[input.type](input);
      return results.then(function(result) {
        return _.map(result, addDistance);
      });
    };
  };
})(this);
