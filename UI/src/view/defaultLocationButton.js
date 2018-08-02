(function (root) {
  root.DefaultLocationButton = function (map, container, backend) {
    var element =
      '<div class="default-location-btn-container"></div>';

    container.append(element);


    eventbus.on('roles:fetched', function(userInfo) {
      if (!(_.isEmpty(userInfo.roles) || _.some(userInfo.roles, function(role) {return role === "viewer"; })))
        $('.default-location-btn-container').append('<button class="btn btn-sm btn-tertiary" id="default-location-btn">Muuta oletussijainniksi</button>');

      $('#default-location-btn').on('click', function () {
        backend.updateUserConfigurationDefaultLocation(actualLocationInfo, function () {
          new GenericConfirmPopup("Oletussijainti tallennettu.", {type: 'alert'});
        }, function () {
          alert('Tarkistus epäonnistui. Yritä hetken kuluttua uudestaan.');
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