window.CoordinateSelector = function(parentElement, extent, instructionsPopup, locationSearch) {
  var tooltip = "Koordinaattien sy&ouml;tt&ouml;: pohjoinen (7 merkki&auml;), it&auml; (6 merkki&auml;). Esim. 6901839, 435323";
  var crosshairToggle = $('<div class="crosshair-wrapper"><div class="checkbox"><label><input type="checkbox" name="crosshair" value="crosshair" checked="true"/> Kohdistin</label></div></div>');
  var coordinatesDiv = $('<div class="coordinates-wrapper"/>');
  var coordinatesText = $('<input type="text" class="location form-control input-sm" placeholder="P, I" title="' + tooltip + '"/>');
  var moveButton = $('<button class="btn btn-sm btn-tertiary">Siirry</button>');

  var render = function() {
    parentElement.append(coordinatesDiv.append(coordinatesText).append(moveButton)).append(crosshairToggle);
  };

  var bindEvents = function() {
    var moveToLocation = function() {
      var location = $('.coordinates .location').val();
      var showDialog = function(message) {
        instructionsPopup.show(message, 3000);
      };
      locationSearch.search(location).then(function(result) {
        if (geometrycalculator.isInBounds(extent, result.lon, result.lat)) {
          eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
        } else {
          showDialog('Koordinaatit eivät osu kartalle.');
        }
      }).fail(showDialog);
    };

    coordinatesText.keypress(function(event) {
      if (event.keyCode == 13) {
        moveToLocation();
      }
    });
    moveButton.on('click', function() {
      moveToLocation();
    });

    $('input', crosshairToggle).change(function() {
      $('.crosshair').toggle(this.checked);
    });
  };

  var show = function() {
    render();
    bindEvents();
  };
  show();
};
