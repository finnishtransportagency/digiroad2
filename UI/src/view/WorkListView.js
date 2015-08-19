(function (root) {
  var unknownLimitsTable = function(layerName, mmlIdsByAdministrativeClass, municipalityName) {
    var municipalityHeader = function(municipalityName) {
      return $('<h2/>').html(municipalityName);
    };
    var tableHeaderRow = function(administrativeClass) {
      return $('<caption/>').html(administrativeClass);
    };
    var tableContentRows = function(mmlIds) {
      return _.map(mmlIds, function(id) {
        return $('<tr/>').append($('<td/>').append(assetLink(id)));
      });
    };
    var assetLink = function(mmlId) {
      var link = '/#' + layerName + '/' + mmlId;
      return $('<a class="work-list-item"/>').attr('href', link).html(link);
    };
    var tableForAdministrativeClass = function(administrativeClass, mmlIds) {
      if (!mmlIds || mmlIds.length === 0) return '';
      return $('<table/>').addClass('table')
        .append(tableHeaderRow(administrativeClass))
        .append(tableContentRows(mmlIds));
    };
    return $('<div/>').append(municipalityHeader(municipalityName))
      .append(tableForAdministrativeClass('Kunnan omistama', mmlIdsByAdministrativeClass.Municipality))
      .append(tableForAdministrativeClass('Valtion omistama', mmlIdsByAdministrativeClass.State))
      .append(tableForAdministrativeClass('Yksityisen omistama', mmlIdsByAdministrativeClass.Private))
      .append(tableForAdministrativeClass('Ei tiedossa', mmlIdsByAdministrativeClass.Unknown));
  };

  var fetchUnknownLimits = function(backend, layerName) {
    $('#work-list').html('' +
      '<div style="overflow: auto;">' +
        '<div class="page">' +
          '<div class="content-box">' +
            '<header>Tuntemattomien nopeusrajoitusten lista</header>' +
            '<div id="unknown-limits" class="work-list">' +
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
    backend.getUnknownLimits(function(limits) {
      var unknownLimits = _.map(limits, _.partial(unknownLimitsTable, layerName));
      $('#unknown-limits').html(unknownLimits);
      $(window).on('hashchange', showApp);
    });
  };

  var bindEvents = function(backend) {
    eventbus.on('workList:select', function(layerName) {
      $('.container').hide();
      $('#work-list').show();
      $('body').addClass('scrollable');

      fetchUnknownLimits(backend, layerName);
    });
  };

  root.WorkListView = {
    initialize: function(backend) {
      bindEvents(backend);
    }
  };

})(this);
