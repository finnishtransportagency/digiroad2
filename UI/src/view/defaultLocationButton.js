(function (root) {
  root.DefaultLocationButton = function (map, container, backend) {
    var element =
      '<div class="default-location-btn-container"></div>';

    container.append(element);


    eventbus.on('roles:fetched', function(userInfo) {
      if (!(_.some(userInfo.roles, function(role) {return role === "viewer"; })))
        $('.default-location-btn-container').append('<button class="btn btn-sm btn-tertiary" id="default-location-btn">Muokkaa aloitussivua</button>');

      $('#default-location-btn').on('click', function () {
        //backend.updateUserConfigurationDefaultLocation(actualLocationInfo, function (result) {
        //new GenericConfirmPopup("Oletussijainti tallennettu.", {type: 'alert'});
        var user = backend.getUserConfiguration();
        var userThen = user.then();
        var userName = user.then(function(assets) {
          var roles = assets.configuration.roles;
          //if user is municipality user
          //if (roles.length === 0)  {

          var municipalities = backend.getUnverifiedMunicipalities();
        //}
          municipalities.then(function(municipalities) {
            new ChangeInitialViewPopup(actualLocationInfo, assets.name, municipalities);
          });

          //if ely user
          //var ely



          //new ChangeInitialViewPopup(actualLocationInfo, assets.name, municipalities);
          //});
          /*}, function () {
            alert('Tarkistus epäonnistui. Yritä hetken kuluttua uudestaan.');*/
        });
      });
    });

    var actualLocationInfo = {lon: 0, lat: 0, zoom: 5};
    eventbus.on('map:moved', function (event) {
      if (!_.isUndefined(event.center))
        actualLocationInfo = {lon: event.center[0], lat: event.center[1], zoom: zoomlevels.getViewZoom(map)};
    });


  };
})(this);