(function (root) {
    root.DefaultLocationButton = function (map, container, backend, instructionsPopup) {
        var element =
            '<div class="default-location-btn-container">' +
            '<button class="btn btn-sm btn-tertiary" id="default-location-btn">Muuta oletussijainniksi</button>' +
            '</div>';

        container.append(element);

        var actualLonLat = {lon: 0, lat: 0};
        eventbus.on('map:moved', function (event) {
            if (!_.isUndefined(event.center))
                actualLonLat = {lon: event.center[0], lat: event.center[1]};
        });

        $('#default-location-btn').on('click', function () {
            backend.updateUserConfigurationDefaultLocation(actualLonLat, function () {
                instructionsPopup.show('Oletussijainti tallennettu', 3000);
            }, function () {
                alert('Tarkistus epäonnistui. Yritä hetken kuluttua uudestaan.');
            });
        });
    };
})(this);