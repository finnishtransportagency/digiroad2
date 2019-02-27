(function(root) {
  root.SearchBox = function(instructionsPopup, locationSearch) {
    var tooltip ="Hae katuosoitteella, tieosoitteella, koordinaateilla tai kohteen ID:llä (esim. link_ID tai pysäkin valtakunnallinen_ID)";
    var groupDiv = $('<div class="panel-group search-box"/>');
    var coordinatesDiv = $('<div class="panel"/>');
    var coordinatesText = $('<input type="text" class="location input-sm" placeholder="Osoite tai koordinaatit" title="' + tooltip + '"/>');
    var moveButton = $('<button class="btn btn-sm btn-primary">Hae</button>');
    var panelHeader = $('<div class="panel-header"></div>').append(coordinatesText).append(moveButton);
    var searchResults = $('<ul id="search-results"></ul>');
    var resultsSection = $('<div class="panel-section"></div>').append(searchResults).hide();
    var clearButton = $('<button class="btn btn-secondary btn-block">Tyhjenn&auml; tulokset</button>');
    var clearSection = $('<div class="panel-section"></div>').append(clearButton).hide();

    var associationNamesRegex = /^YT +/g;

    var bindEvents = function() {
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

      locationSearch.privateRoadAssociationNames();

      eventbus.on('associationNames:fetched', function(result) {
        enableAutoComplete(coordinatesText,result);
      });

      eventbus.on('associationNames:reload', function() {
        locationSearch.privateRoadAssociationNames();
      });
    };

    var moveToLocation = function() {
      var showDialog = function(message) {
        instructionsPopup.show(message, 3000);
      };
      locationSearch.search(coordinatesText.val()).then(function(results) {
        populateSearchResults(results);
        if (results.length === 1) {
          var result = results[0];
          if (result.resultType.indexOf("Mtstop")>-1) {
            window.location.hash = "#massTransitStop/";
            window.location.hash="#massTransitStop/"+result.nationalId;
          }
          else if (result.resultType.indexOf("Link-id")>-1) {
            window.location.hash = "#linkProperty/";
            eventbus.trigger('coordinates:selected', {lon: result.lon, lat: result.lat});
            window.location.hash = "#linkProperty/" + coordinatesText.val();
          } else if (result.resultType.indexOf("SpeedLimit")>-1) {
            eventbus.once('layer:speedLimit:moved', function() {
              eventbus.trigger('speedLimit:selectByLinkId', result.linkid);
            });
            eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
          }
          else {
            eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
          }
        }
      }).fail(showDialog);
    };

    var populateSearchResults = function(results) {
      var resultItems = _.chain(results)
        .sortBy('distance')
        .map(function(result) {

          var shownText = function(value){
            if(value.resultType.indexOf("association")>-1)
              return value.title + ", " + value.municipality + ", " + value.roadName;
            else
              return value.title;
          };

          return $('<li></li>').text(shownText(result)).on('click', function() {
            if (result.resultType.indexOf("Link-id")>-1){
              window.location.hash = "#linkProperty/";
              eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
              window.location.hash = "#linkProperty/" + coordinatesText.val();
            } else if (result.resultType.indexOf("SpeedLimit")>-1) {
              eventbus.once('layer:speedLimit:moved', function() {
                eventbus.trigger('speedLimit:selectByLinkId', result.linkid);
              });
              eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
            } else if (result.resultType.indexOf("Mtstop")>-1) {
              window.location.hash = "#massTransitStop/";
              window.location.hash="#massTransitStop/"+result.nationalId;
            } else if (result.resultType.indexOf("association")>-1) {
              window.location.hash = "#linkProperty/";
              window.location.hash = "#linkProperty/" + result.linkId;
            } else {
              eventbus.trigger('coordinates:selected', { lon: result.lon, lat: result.lat });
            }
          });
        }).value();

      searchResults.html(resultItems);
      resultsSection.show();
      clearSection.show();
    };

    var splitBySpaces = function(value) {
      return value.split(/ \s*/);
    };

    var pushSearchValues = function(value, event, ui) {
      var terms = splitBySpaces(value.val().substring(0, value.selectionStart));
      terms.splice(1);
      terms.push(ui.item.value);
      value.val($.trim(terms.join(" ")));
    };

    var loadAutocompleteValues = function(request, response, values, regex) {
      if(request.term.match(regex)){
        var slicedInput = request.term.slice(3);
        if(slicedInput.length !== 0) {
          var filteredResult = new RegExp($.ui.autocomplete.escapeRegex(slicedInput), "i");
          response($.grep(values, function(value){
            return filteredResult.test(value);
          }) );
        }
      }
    };

    var enableAutoComplete = function(element, values) {
      element.autocomplete({
        source: function(request, response) {
          return loadAutocompleteValues(request, response, values, associationNamesRegex);
        },
        focus: function(event, ui) {
          pushSearchValues(coordinatesText, event, ui);
          return false;
        },
        select: function(event, ui) {
          pushSearchValues(coordinatesText, event, ui);
          return false;
        },
        appendTo: ".search-box > .panel > .panel-header"
      });
    };

    bindEvents();
    this.element = groupDiv.append(coordinatesDiv.append(panelHeader).append(resultsSection).append(clearSection));

  };
})(this);
