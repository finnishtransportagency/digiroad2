window.CoordinateSelector = function(parentElement, extent, instructionsPopup, locationSearch) {
  var tooltip = "Koordinaattien sy&ouml;tt&ouml;: pohjoinen (7 merkki&auml;), it&auml; (6 merkki&auml;). Esim. 6901839, 435323";
  var crosshairToggle = $('<div class="crosshair-wrapper"><div class="checkbox"><label><input type="checkbox" name="crosshair" value="crosshair" checked="true"/> Kohdistin</label></div></div>');
  var coordinatesDiv = $('<div class="coordinates-wrapper"/>');
  var coordinatesText = $('<input type="text" class="lonlat form-control input-sm" name="lonlat" placeholder="P, I" title="' + tooltip + '"/>');
  var moveButton = $('<button class="btn btn-sm btn-tertiary">Siirry</button>');
  var markButton = $('<button class="btn btn-sm btn-tertiary">Merkitse</button>');

  var render = function() {
    parentElement.append(coordinatesDiv.append(coordinatesText).append(moveButton).append(markButton)).append(crosshairToggle);
  };

  var bindEvents = function() {
    var moveToCoordinates = function(eventName) {
      var lonlat = $('.coordinates .lonlat').val();
      var showDialog = function(message) {
        instructionsPopup.show(message, 3000);
      };
      var result = locationSearch.search(lonlat);

      if (!result) {
        showDialog('Käytä koordinaateissa P ja I numeroarvoja.');
      } else if (!geometrycalculator.isInBounds(extent, result.lon, result.lat)) {
        showDialog('Koordinaatit eivät osu kartalle.');
      } else {
        eventbus.trigger(eventName, { lon: result.lon, lat: result.lat });
      }
    };

    coordinatesText.keypress(function(event) {
      if (event.keyCode == 13) {
        moveToCoordinates('coordinates:selected');
      }
    });
    moveButton.on('click', function() {
      moveToCoordinates('coordinates:selected');
    });
    markButton.on('click', function() {
      moveToCoordinates('coordinates:marked');
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
