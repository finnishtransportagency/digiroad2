(function(root) {
  root.LocationSearch = function(backend, applicationModel) {
    var selectedLayer;

    this.privateRoadAssociationNames = function() {
      return backend.getPrivateRoadAssociationNames().then(function(result){
        eventbus.trigger('associationNames:fetched', result);
      });
    };

    /**
     * Search by street address
     *
     * @param street
     * @returns {*}
     */
    var geocode = function(street) {
      return backend.getGeocode(street.address).then(function(result) {
        var resultLength = _.get(result, 'length');
        var vkmResultToCoordinates = function(r) {
          return { title: r.tienimiFi +" "+ r.katunumero + ", "+ r.kuntaNimi, lon: r.x, lat: r.y ,resultType:"street" };
        };
        if (resultLength > 0) {
          return _.map(result, vkmResultToCoordinates);
        } else {
          return $.Deferred().reject('Tuntematon katuosoite');
        }
      });
    };

    /**
     * Search by asset Id depending on current layer
     *
     * @param input
     * @returns {*}
     */
    var searchById = function(input) {
      if (selectedLayer === 'massTransitStop') {
        return massTransitStopSearchByNationalId(input);
      } else if (selectedLayer === 'linkProperty') {
        return roadLinkSearchById(input);
      } else if (selectedLayer === 'speedLimit') {
        return speedLimitSearchById(input);
      } else {
        return $.Deferred().reject('Haulla ei löytynyt tuloksia');
      }
    };

    /**
     * Speed limit id search
     *
     * @param input
     * @returns {*}
     */
    var speedLimitSearchById = function(input){
      return $.when(backend.getSpeedLimitsLinkIDFromSegmentID(input.text)).then(function(speedlimitdata) {
        var returnObject = [];
        if (_.get(speedlimitdata, 'success')) {
          var linkid = _.get(speedlimitdata, 'linkId');
          var y = _.get(speedlimitdata, 'latitude');
          var x= _.get(speedlimitdata, 'longitude');
          var title = input.text + " (nopeusrajoituksen ID)";
            returnObject.push({title: title, lon: x, lat: y, linkid:linkid, resultType:"SpeedLimit"});
        }

        if (_.isEmpty(returnObject))
          return $.Deferred().reject('Haulla ei löytynyt tuloksia');
        return returnObject;
        });
    };

    /**
     * Link id search
     *
     * @param input
     * @returns {*}
     */
    var roadLinkSearchById= function(input) {
      return $.when(backend.getRoadLinkToPromise(input.text)).then(function(linkdata) {
        var returnObject = [];
        if (_.get(linkdata, 'success')) {
          var x = _.get(linkdata, 'middlePoint.x');
          var y = _.get(linkdata, 'middlePoint.y');
          var title = input.text + " (linkin ID)";
          returnObject.push({title: title, lon: x, lat: y, resultType: "Link-id"});
        }

        if (_.isEmpty(returnObject))
          return $.Deferred().reject('Haulla ei löytynyt tuloksia');
        return returnObject;
      });
    };


    var extractSearchResultInfo = function (massTransitStop) {
      return { lon: _.get(massTransitStop, 'lon'),
        lat: _.get(massTransitStop, 'lat'),
        nationalId: _.get(massTransitStop, 'nationalId')
      };
    };

    /**
     * Mass transit stop national id search
     *
     * @param input
     * @returns {*}
     */
    var massTransitStopSearchByNationalId = function(input) {
      return $.when(backend.getMassTransitStopByNationalIdForSearch(input.text)).then(function(result) {
        var returnObject = [];

        if (_.get(result, 'success')) {
          var info = extractSearchResultInfo(result);
          var title = input.text + ' (valtakunnallinen ID)';
          returnObject.push({title: title, lon: info.lon, lat: info.lat, nationalId: info.nationalId,resultType:"Mtstop"});
        }

        if (_.isEmpty(returnObject))
          return $.Deferred().reject('Haulla ei löytynyt tuloksia');
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
      var constructTitle = function(road) {
        var titleParts = [_.get(road, 'tie'), _.get(road, 'osa'), _.get(road, 'etaisyys'), _.get(road, 'ajorata')];
        return _.some(titleParts, _.isUndefined) ? '' : titleParts.join(' ');
      };
      var auxRoadData = _.head(roadData);
      var lon = _.get(auxRoadData, 'x');
      var lat = _.get(auxRoadData, 'y');
      var title = constructTitle(auxRoadData);
      if (lon && lat) {
        return  [{title: title, lon: lon, lat: lat, resultType:"road"}];
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
      return $.when(backend.getMassTransitStopByLiviIdForSearch(input.text)).then(function(result) {
        var returnObject = [];

        if (_.get(result, 'success')) {
          var info = extractSearchResultInfo(result);
          var title = input.text + ' (pysäkin Livi-tunniste)';
          returnObject.push({title: title, lon: info.lon, lat: info.lat, nationalId: info.nationalId, resultType:"Mtstop"});
        }

        if (_.isEmpty(returnObject))
          return $.Deferred().reject('Haulla ei löytynyt tuloksia');
        return returnObject;
      });
    };

    /**
     * Search by mass transit stop passenger id
     *
     * @param input
     * @returns {*}
     */
    var  massTransitStopPassengerIdSearch = function(input) {
      return $.when(backend.getMassTransitStopByPassengerIdForSearch(input.text)).then(function(result) {
        var toCoordinates = function (r) {
          var title = input.text + ', ' + r.municipalityName;
          return {title: title, lon: r.lon, lat: r.lat, nationalId: r.nationalId, resultType: "Mtstop"};
        };

        if (result.length > 0 && _.head(result).success)
          return _.map(result, toCoordinates);
        return $.Deferred().reject('Haulla ei löytynyt tuloksia');
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
     * Search private road association names.
     *
     * @param associationRoadName
     */
    var getAssociationRoadNamesByName = function(associationRoad) {
      var associationRoadName = associationRoad.name.toUpperCase().replace(/\s{2,}/g,' ').trim();
      return backend.getPrivateRoadAssociationNamesBySearch(associationRoadName)
        .then(function(resultFromAPI) {
          if (resultFromAPI.length > 0)
            return _.map(resultFromAPI, function(value) {
              var title = value.name + ", " + value.municipality + ", " + value.roadName;
              return { title: title, linkId: value.linkId, resultType: "association" };
            });
          else
            return $.Deferred().reject('Hakusanalla ei löydetty sopivaa tiekuntanimeä.');
        });
    };

    /**
     * Search by coordinates
     *
     * @param coordinates
     * @returns {*|String}
     */
    var resultFromCoordinates = function(coordinates) {
      var result = _.assign({}, coordinates, { title: coordinates.lat + ',' + coordinates.lon, resultType:"coordinates" });
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
        assetId: searchById,
        liviId: massTransitStopLiviIdSearch,
        passengerId:  massTransitStopPassengerIdSearch,
        roadAssociationName: getAssociationRoadNamesByName,
        invalid: function() { return $.Deferred().reject('Syötteestä ei voitu päätellä koordinaatteja, katuosoitetta tai tieosoitetta'); }
      };

      var results = resultByInputType[input.type](input);
      return results.then(function(result) {
        return _.map(result, addDistance);
      });
    };
  };
})(this);
