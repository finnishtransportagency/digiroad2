(function(root) {
  root.SearchBox = function(instructionsPopup, locationSearch) {
    var tooltip = "Hae katuosoitteella, tieosoitteella tai koordinaateilla";
    var groupDiv = $('<div class="panel-group search-box"/>');
    var coordinatesDiv = $('<div class="panel"/>');
    var coordinatesText = $('<input type="text" class="location input-sm" placeholder="Osoite tai koordinaatit" title="' + tooltip + '"/>');
    var moveButton = $('<button class="btn btn-sm btn-primary">Hae</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(coordinatesText).append(moveButton);
    var searchResults = $('<ul id="search-results"></ul>');
    var resultsSection = $('<div class="panel-section"></div>').append(searchResults).hide();
    var clearButton = $('<button class="btn btn-secondary btn-block">Tyhjenn&auml; tulokset</button>');
    var clearSection = $('<div class="panel-section"></div>').append(clearButton).hide();

    var bindEvents = function() {
      var populateSearchResults = function(results) {
        var resultItems = _.chain(results)
          .sortBy('distance')
          .map(function(result) {
            return $('<li></li>').text(result.title).on('click', function() {
if (result.title.indexOf("Link-ID")>-1){
  window.location.hash = "#linkProperty/";
  eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
  window.location.hash = "#linkProperty/" + coordinatesText.val();
} else if (result.title.indexOf("Speed Limit-ID")>-1) {
  eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
  eventbus.trigger('speedLimit:selectByLinkId', result.linkid);
} else if (result.nationalId) {
  eventbus.trigger('nationalId:selected', { lon: result.lon, lat: result.lat, nationalId: result.nationalId });
}
  else
  {
    eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
  }
            });
          }).value();

        searchResults.html(resultItems);
        resultsSection.show();
        clearSection.show();
      };
      var moveToLocation = function() {
        var showDialog = function(message) {
          instructionsPopup.show(message, 3000);
        };
        locationSearch.search(coordinatesText.val()).then(function(results) {
          populateSearchResults(results);
          if (results.length === 1) {
            var result = results[0];
            if (result.nationalId) {
              eventbus.trigger('nationalId:selected', { lon: result.lon, lat: result.lat, nationalId: result.nationalId });
            }
            else if (result.title.indexOf("Link-ID")>-1){
              window.location.hash = "#linkProperty/";
              eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
              window.location.hash = "#linkProperty/" + coordinatesText.val();
            } else if (result.title.indexOf("Speed Limit-ID")>-1) {
              eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
              eventbus.trigger('speedLimit:selectByLinkId', result.linkid);
            }
            else {
              eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
            }
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
      clearButton.on('click', function() {
        resultsSection.hide();
        clearSection.hide();
      });
    };

    bindEvents();
    this.element = groupDiv.append(coordinatesDiv.append(panelHeader).append(resultsSection).append(clearSection));
  };
})(this);
