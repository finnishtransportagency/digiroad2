(function (root) {
  var unknownLimitsTable = function(layerName, workListItems, municipalityName) {
    var municipalityHeader = function(municipalityName, totalCount) {
      var countString = totalCount ? ' (yhteensä ' + totalCount + ' kpl)' : '';
      return $('<h2/>').html(municipalityName + countString);
    };
    var tableHeaderRow = function(administrativeClass) {
      return $('<caption/>').html(administrativeClass);
    };
    var tableContentRows = function(linkIds) {
      return _.map(linkIds, function(item) {
        return $('<tr/>').append($('<td/>').append(typeof item.id !== 'undefined' ? assetLink(item) : idLink(item)));
      });
    };
    var idLink = function(id) {
      var link = '#' + layerName + '/' + id;
      return $('<a class="work-list-item"/>').attr('href', link).html(link);
    };
    var floatingValidator = function() {
      return $('<span class="work-list-item"> &nbsp; *</span>');
    };
    var assetLink = function(asset) {
      var link = '#' + layerName + '/' + asset.id;
      var workListItem = $('<a class="work-list-item"/>').attr('href', link).html(link);
      if(asset.floatingReason === 1) //floating reason equal to RoadOwnerChanged
        workListItem.append(floatingValidator);
      return workListItem;
    };
    var tableForAdministrativeClass = function(administrativeClass, linkIds, count) {
      if (!linkIds || linkIds.length === 0) return '';
      var countString = count ? ' (' + count + ' kpl)' : '';
      return $('<table/>').addClass('table')
        .append(tableHeaderRow(administrativeClass + countString))
        .append(tableContentRows(linkIds));
    };
    return $('<div/>').append(municipalityHeader(municipalityName, workListItems.totalCount))
      .append(tableForAdministrativeClass('Kunnan omistama', workListItems.Municipality, workListItems.municipalityCount))
      .append(tableForAdministrativeClass('Valtion omistama', workListItems.State, workListItems.stateCount))
      .append(tableForAdministrativeClass('Yksityisen omistama', workListItems.Private, workListItems.privateCount))
      .append(tableForAdministrativeClass('Ei tiedossa', workListItems.Unknown, 0));
  };

  var generateWorkList = function(layerName, listP) {
    var title = {
      speedLimit: 'Tuntemattomien nopeusrajoitusten lista',
      linkProperty: 'Korjattavien linkkien lista',
      massTransitStop: 'Geometrian ulkopuolelle jääneet pysäkit',
      pedestrianCrossings: 'Geometrian ulkopuolelle jääneet suojatiet',
      trafficLights: 'Geometrian ulkopuolelle jääneet liikennevalot',
      obstacles: 'Geometrian ulkopuolelle jääneet esterakennelmat',
      railwayCrossings: 'Geometrian ulkopuolelle jääneet rautatien tasoristeykset',
      directionalTrafficSigns: 'Geometrian ulkopuolelle jääneet opastustaulut',
      trafficSigns: 'Geometrian ulkopuolelle jääneet liikennevalot',
      maintenanceRoad: 'needs to be choosen'
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
