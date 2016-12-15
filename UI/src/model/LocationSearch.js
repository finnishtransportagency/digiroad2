(function(root) {
  root.LocationSearch = function(backend, applicationModel) {
    var selectedLayer;

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

   var idOrRoadNumber = function(input) {
     if (selectedLayer === 'massTransitStop') {
       // TODO: Combine with road address (road number) search
       return backend.getMassTransitStopByNationalIdForSearch(input.text).then(function(result) {
         var lon = result.lon;
         var lat = result.lat;
         var title = input.text + ' (valtakunnallinen id)';
         if (lon && lat) {
           return [{ title: title, lon: lon, lat: lat, nationalId: result.nationalId }];
         } else {
           return $.Deferred().reject('Tuntematon valtakunnallinen id');
         }
       });
     }
     else if (selectedLayer === 'linkProperty') {
       return roadnumberandroadlinksearch(input);


     }
     else if (selectedLayer === 'speedLimit') {
       //SpeedLimit  & roadnumber search
     }
   };



    var roadnumberandroadlinksearch= function (input) {
      var roadnlinksearch= $.get("api/roadlinks/"+input.text);
      var roadnumbersearch=backend.getCoordinatesFromRoadAddress(input.text);
      var returnob=[];
      return $.when(
        roadnlinksearch,roadnumbersearch).then(function (linkdata,roadData){
        var constructTitle = function(result) {
          var address = _.get(result, 'alkupiste.tieosoitteet[0]');
          var titleParts = [_.get(address, 'tie'), _.get(address, 'osa'), _.get(address, 'etaisyys'), _.get(address, 'ajorata')];
          return _.some(titleParts, _.isUndefined) ? '' : titleParts.join(' ');
        };
        var lon = _.get(roadData, 'alkupiste.tieosoitteet[0].point.x');
        var lat = _.get(roadData, 'alkupiste.tieosoitteet[0].point.y');
        var titleD = constructTitle(roadData);
        if (lon && lat) {
          returnob = [{title: titleD, lon: lon, lat: lat}];
        }
        var linkfound=_.get(linkdata[0], 'Success');
        if (linkfound=="1")
        {
          var x=_.get(linkdata[0], 'middlePoint.x');
          var y=_.get(linkdata[0], 'middlePoint.y');
          var title="link-ID: " +input.text;

          if (title.indexOf("link-ID")>-1){
            if (returnob.length>0) {
              returnob.push({title: title, lon: x, lat: y, Distance: 0});
            } else {
              returnob=[{title: title, lon: x, lat: y, Distance: 0}];
            }
          }
        }
        return returnob;
      });
    };


    var  MasstransitstopLiviIdSearch = function(input) {
      //add here what to do when masstransitstop with livi-id
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
        MasstransitstopLiviId: MasstransitstopLiviIdSearch,
        invalid: function() { return $.Deferred().reject('Syötteestä ei voitu päätellä koordinaatteja, katuosoitetta tai tieosoitetta'); }
      };

      var results = resultByInputType[input.type](input);
      return results.then(function(result) {
        return _.map(result, addDistance);
      });
    };
  };
})(this);
