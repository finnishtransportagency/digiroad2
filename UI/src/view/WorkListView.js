(function (root) {
  var unknownLimitsTable = function(layerName, mmlIdsByAdministrativeClass, municipalityName) {
    var municipalityHeader = function(municipalityName, totalCount) {
      var countString = totalCount ? ' (yhteensä ' + totalCount + ' kpl)' : '';
      return $('<h2/>').html(municipalityName + countString);
    };
    var tableHeaderRow = function(administrativeClass) {
      return $('<caption/>').html(administrativeClass);
    };
    var tableContentRows = function(mmlIds) {
      return _.map(mmlIds, function(id) {
        return $('<tr/>').append($('<td/>').append(assetLink(id)));
      });
    };
    var assetLink = function(id) {
      var link = '#' + layerName + '/' + id;
      return $('<a class="work-list-item"/>').attr('href', link).html(link);
    };
    var tableForAdministrativeClass = function(administrativeClass, mmlIds, count) {
      if (!mmlIds || mmlIds.length === 0) return '';
      var countString = count ? ' (' + count + ' kpl)' : '';
      return $('<table/>').addClass('table')
        .append(tableHeaderRow(administrativeClass + countString))
        .append(tableContentRows(mmlIds));
    };
    return $('<div/>').append(municipalityHeader(municipalityName, mmlIdsByAdministrativeClass.totalCount))
      .append(tableForAdministrativeClass('Kunnan omistama', mmlIdsByAdministrativeClass.Municipality, mmlIdsByAdministrativeClass.municipalityCount))
      .append(tableForAdministrativeClass('Valtion omistama', mmlIdsByAdministrativeClass.State, mmlIdsByAdministrativeClass.stateCount))
      .append(tableForAdministrativeClass('Yksityisen omistama', mmlIdsByAdministrativeClass.Private, mmlIdsByAdministrativeClass.privateCount))
      .append(tableForAdministrativeClass('Ei tiedossa', mmlIdsByAdministrativeClass.Unknown, 0));
  };

  var generateWorkList = function(layerName, listP) {
    var title = {
      speedLimit: 'Tuntemattomien nopeusrajoitusten lista',
      linkProperty: 'Korjattavien linkkien lista',
      massTransitStop: 'Geometrian ulkopuolelle jääneet pysäkit'
    };
    $('#work-list').html('' +
      '<div style="overflow: auto;">' +
        '<div class="page">' +
          '<div class="content-box">' +
            '<header>' + title[layerName] +
              '<a class="header-link" href="#' + layerName + '">Sulje lista</a>' +
            '</header>' +
            '<div class="work-list">' +
          '</div>' +
        '</div>' +
      '</div>'
    );
    var showApp = function() {
      $('.container').show();
      $('#work-list').hide();
      $('body').removeClass('scrollable').scrollTop(0);
      $(window).off('hashchange', showApp);
    };
    $(window).on('hashchange', showApp);
    listP.then(function(limits) {
      var unknownLimits = _.map(limits, _.partial(unknownLimitsTable, layerName));
      $('#work-list .work-list').html(unknownLimits);
    });
  };

  var bindEvents = function() {
    eventbus.on('workList:select', function(layerName, listP) {
      $('.container').hide();
      $('#work-list').show();
      $('body').addClass('scrollable');

      generateWorkList(layerName, listP);
    });
  };

  root.WorkListView = {
    initialize: function() {
      bindEvents();
    }
  };

})(this);
