(function (root) {
  root.MouseCoordinatesDisplay = function(map, container) {
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
      '</div>';
    container.append(element);

    eventbus.on('map:mouseMoved', function(event) {
      var lonlat = map.getLonLatFromPixel(event.xy);
      container.find('.cbValue[axis="lat"]').text(Math.round(lonlat.lat));
      container.find('.cbValue[axis="lon"]').text(Math.round(lonlat.lon));
    });
  };
})(this);