(function (root) {
    root.DefaultLocationButton = function (map, container, instructionsPopup) {
        var element =
            '<div class="default-location-btn-container">' +
            '<button class="btn btn-sm btn-tertiary" id="default-location-btn">Muuta oletussijainniksi</button>' +
            '</div>';

        container.append(element);

        var actualLonLat = {lon: 0, lat: 0};
        eventbus.on('map:moved', function(event) {
            actualLonLat = event.center;
        });

        $('#default-location-btn').on('click', function () {
            instructionsPopup.show('Oletussijainti tallennettu', 3000);
        });
  };
})(this);