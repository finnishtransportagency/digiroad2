(function(root) {
  root.SearchBox = function(instructionsPopup, locationSearch) {
    var tooltip = "Hae katuosoitteella, tieosoitteella tai koordinaateilla";
    var groupDiv = $('<div id="searchBox" class="panel-group search-box"/>');
    var coordinatesDiv = $('<div class="panel"/>');
    var coordinatesText = $('<input type="text" class="location input-sm" placeholder="Osoite tai koordinaatit" title="' + tooltip + '"/>');
    var moveButton = $('<button class="btn btn-sm btn-primary">Hae</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(coordinatesText).append(moveButton);
    var searchResults = $('<ul id="search-results"></ul>');
    var resultsSection = $('<div class="panel-section"></div>').append(searchResults).hide();
    var clearButton = $('<button class="btn btn-secondary btn-block">Tyhjenn&auml; tulokset</button>');
    var clearSection = $('<div class="panel-section"></div>').append(clearButton).hide();

    var setDateValue = function(date) {
      $('#dayBox').val(date[0]);
      $('#monthBox').val(date[1]);
      $('#yearBox').val(date[2]);
    };

    var bindEvents = function() {
      var populateSearchResults = function(results) {
        var resultItems = _.chain(results)
          .sortBy('distance')
          .map(function(result) {
            return $('<li></li>').text(result.title).on('click', function() {
              eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
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
      clearButton.on('click', function() {
        resultsSection.hide();
        clearSection.hide();
      });

      eventbus.on('linkProperty:fetchHistoryLinks', function(date){
        var dateBox = $('<div id="dateBox" style="margin-left: 10px" class="panel-group search-box"> ' +
          '<input id="dayBox" type="text" style="width: 40px" class="location input-sm" placeholder="DD" value="'+date[0]+'"/>' +
          '<input id="monthBox" type="text" style="width: 40px" class="location input-sm" placeholder="MM" value="'+date[1]+'"/>' +
          '<input id="yearBox" type="text" style="width: 65px" class="location input-sm" placeholder="YYYY" value="'+date[2]+'"/>');

        if($('#searchBox').has('#dateBox').length === 0) {
          $('#searchBox').append(dateBox);

          $('#dayBox, #monthBox, #yearBox').on('change', function(eventData) {
            var day = $('#dayBox').val();
            var month = $('#monthBox').val();
            var year = $('#yearBox').val();
            var newDate = [day, month,year];
            setDateValue(newDate);
            eventbus.trigger('linkProperty:fetchHistoryLinks',newDate)
          });
        }
      });
    };

    bindEvents();
    this.element = groupDiv.append(coordinatesDiv.append(panelHeader).append(resultsSection).append(clearSection));
  };
})(this);
