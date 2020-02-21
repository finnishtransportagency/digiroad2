(function (root) {
  root.DefaultLocationButton = function (map, container, backend, assetTypeConfig, defaultAssetType, startCoordinates) {
    var defaultAsset = defaultAssetType;
    var startLocationName;
    var lon = startCoordinates.lon;
    var lat = startCoordinates.lat;
    var zoom = startCoordinates.zoom;
    var defaultLon = 390000;
    var defaultLat = 6900000;
    var roles;
    var places;

    var setLocationsInfo = function (municipalityName, elyName) {
      switch (roles) {
        case "elyMaintainer":
          places = backend.getUserElyConfiguration();
          startLocationName = elyName;
          break;
        case "operator":
          places = [];
          startLocationName = municipalityName;
          break;
        default:
          places = backend.getMunicipalities();
          startLocationName = municipalityName;
      }
    };

    var vkm = function () {
      if (zoom >= 5) {
        backend.getMunicipalityFromCoordinates(lon, lat, function (vkmResult) {
          setLocationsInfo(vkmResult.kunta, vkmResult.ely_nimi);
        }, function () {
          setLocationsInfo("Tuntematon", "Tuntematon");
        });
      } else {
        setLocationsInfo("Tuntematon", "Tuntematon");
      }
    };

    var element =
      '<div class="default-location-btn-container"></div>';

    container.append(element);


    eventbus.on('roles:fetched', function(userInfo) {
      if (!(_.some(userInfo.roles, function(role) {return (role === "viewer" || role === "serviceRoadMaintainer");}))) {
        roles = _.find(userInfo.roles, function (role) {
          return role === "elyMaintainer" || role === "operator";
        });

        if (defaultLon === lon && defaultLat === lat) {
          setLocationsInfo("Suomi", "Suomi");
        } else {
          backend.getStartLocationNameByCoordinates(startCoordinates).then(function (locationName) {
            if (_.isEmpty(locationName)) {
              vkm();
            } else {
              setLocationsInfo(locationName[0].municipalityName, locationName[0].elyName);
            }
          });
        }

        $('.default-location-btn-container').append('<button class="btn btn-sm btn-tertiary" id="default-location-btn">Muokkaa aloitussivua</button>');
      }

      $('#default-location-btn').on('click', function () {
        if (places.length === 0) {
          new InitialPopupView(backend, actualLocationInfo, roles, places, assetTypeConfig, defaultAsset, startLocationName);
        } else {
          places.then(function (places) {
            new InitialPopupView(backend, actualLocationInfo, roles, places, assetTypeConfig, defaultAsset, startLocationName);
          });
        }
      });
    });

    var actualLocationInfo = {lon: 0, lat: 0, zoom: 5};
    eventbus.on('map:moved', function (event) {
      if (!_.isUndefined(event.center))
        actualLocationInfo = {lon: event.center[0], lat: event.center[1], zoom: zoomlevels.getViewZoom(map)};
    });

    eventbus.on('userInfo:setDefaultLocation', function (newAssetName, newLocationName) {
      if(newAssetName)
        defaultAsset = newAssetName;

      if(newLocationName)
        startLocationName = newLocationName;
    });


  };
})(this);