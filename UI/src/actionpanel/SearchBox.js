(function(root) {
  root.SearchBox = function(instructionsPopup, locationSearch) {
    var tooltip = "Hae katuosoitteella, tieosoitteella tai koordinaateilla";
    var groupDiv = $('<div class="panel-group"/>');
    var coordinatesDiv = $('<div class="panel search-box"/>');
    var coordinatesText = $('<input type="text" class="location input-sm" placeholder="Osoite tai koordinaatit" title="' + tooltip + '"/>');
    var moveButton = $('<button class="btn btn-sm btn-primary">Hae</button>');

    var bindEvents = function() {
      var moveToLocation = function() {
        var location = coordinatesText.val();
        var showDialog = function(message) {
          instructionsPopup.show(message, 3000);
        };
        locationSearch.search(location).then(function(results) {
          if (results.length === 1) {
            var result = results[0];
            eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
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
    };

    bindEvents();
    this.element = groupDiv.append(coordinatesDiv.append(coordinatesText).append(moveButton));
  };
})(this);
