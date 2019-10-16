(function (root) {
  root.DefaultLocationButton = function (map, container, backend, assetTypeConfig, defaultAssetType, defaultCoordinates) {
    var vkm = function() {
      if(zoom <= 5){
        backend.getMunicipalityFromCoordinates(lon, lat, function (vkmResult) {
              var municipalityInfo = vkmResult.kunta ? vkmResult.kunta : "Suomi";
              municipality = municipalityInfo;
              ely = vkmResult.ely_nimi ? vkmResult.ely_nimi : "Suomi";
            }, function() {});
      }
    };

    var defaultAsset = defaultAssetType;
    var defaultLocation = defaultCoordinates;
    var municipality = 'Suomi';
    var ely = 'Suomi';
    var lon = defaultLocation.shift();
    var lat = defaultLocation.shift();
    var zoom = defaultLocation.shift();
    var roles;
    var places;

    var element =
      '<div class="default-location-btn-container"></div>';

    container.append(element);


    eventbus.on('roles:fetched', function(userInfo) {
      if (!(_.some(userInfo.roles, function(role) {return (role === "viewer" || role === "serviceRoadMaintainer");}))) {
          roles = userInfo.roles.find(function (role) {return role === "busStopMaintainer" || role === "operator";});
          switch(roles){
            case "busStopMaintainer":
              places = backend.getUserElyConfiguration();
              municipality = ely;
              break;
            case "operator":
              places = [];
              break;
            default:
              places = backend.getMunicipalities();
          }
          vkm();
          $('.default-location-btn-container').append('<button class="btn btn-sm btn-tertiary" id="default-location-btn">Muokkaa aloitussivua</button>');
      }

      $('#default-location-btn').on('click', function () {

          if (places.length === 0) {
              new InitialPopupView(backend, actualLocationInfo, roles, places, assetTypeConfig, defaultAsset, municipality);
      }
      else{
              places.then(function (places) {
                  new InitialPopupView(backend, actualLocationInfo, roles, places, assetTypeConfig, defaultAsset, municipality);
              });
        }
        });
      });

    var actualLocationInfo = {lon: 0, lat: 0, zoom: 5};
    eventbus.on('map:moved', function (event) {
      if (!_.isUndefined(event.center))
        actualLocationInfo = {lon: event.center[0], lat: event.center[1], zoom: zoomlevels.getViewZoom(map)};
    });


  };
})(this);