(function (root) {
  root.CoordinatesDisplay = function(map, container) {
    var element =
      '<div class="mapplugin coordinates" data-position="4">' +
        '<div class="cbSpansWrapper">' +
          '<div class="cbRow">' +
            '<div class="cbCrsLabel">ETRS89-TM35FIN</div>' +
          '</div>' +
          '<div class="cbRow">' +
            '<div class="cbLabel cbLabelN" axis="lat">P:</div>' +
            '<div class="cbValue" axis="lat">lat</div>' +
          '</div>' +
          '<br clear="both">' +
          '<div class="cbRow">' +
            '<div class="cbLabel cbLabelE" axis="lon">I:</div>' +
            '<div class="cbValue" axis="lon">lon</div>' +
          '</div>' +
        '</div>' +
        '<button class="btn btn-sm btn-tertiary" id="mark-coordinates">Merkitse</button>' +
      '</div>';
    container.append(element);

    var centerLonLat = {lon: 0, lat: 0};
    eventbus.on('map:moved', function(event) {
      centerLonLat = event.center;
      if (centerLonLat) {
        container.find('.cbValue[axis="lat"]').text(Math.round(centerLonLat[1]));
        container.find('.cbValue[axis="lon"]').text(Math.round(centerLonLat[0]));
      }
    });

    $('#mark-coordinates').on('click', function() {
      eventbus.trigger('coordinates:marked', centerLonLat);
    });
  };
})(this);