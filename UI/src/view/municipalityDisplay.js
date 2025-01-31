(function (root) {
  root.MunicipalityDisplay = function(map, container, backend) {
    var element =
        $('<div class="municipality-container">' +
            '<div class="municipality-wrapper">' +
            '</div>' +
            '</div>');
    container.append(element);

    var getMunicipalityInfo = _.debounce(function(lon, lat){backend.getMunicipalityFromCoordinates(lon, lat, function (vkmResult) {
      var municipalityInfo = !_.isEmpty(vkmResult) && vkmResult.properties.kuntanimi ? vkmResult.properties.kuntanimi : "Tuntematon";
        container.find('.municipality-wrapper').text(municipalityInfo);
      }, function () {
        container.find('.municipality-wrapper').text('');
      }
    );}, 250);

    eventbus.on('map:moved', function (event) {
      //Municipality name could be shown at 5 km zoom level (level 5 = 5 Km)
      if (zoomlevels.getViewZoom(map) >= 5) {
        var centerLonLat = map.getView().getCenter();
        getMunicipalityInfo(centerLonLat[0], centerLonLat[1]);
      }else
        container.find('.municipality-wrapper').text('');
    });
  };
})(this);