(function (root) {

  var unknownLimitsTable = function(mmlIdsByAdministrativeClass, municipalityName) {
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
      var link = '/#speedLimit/' + mmlId;
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

  var fetchUnknownLimits = function() {
    var backend = new Backend();
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
    backend.getUnknownLimits(function(limits) {
      var unknownLimits = _.map(limits, unknownLimitsTable);
      $('#unknown-limits').html(unknownLimits);
      $('a.work-list-item').on('click', function() {
        $('.container').show();
        $('#work-list').hide();
        $('body').removeClass('work-list');
      });
    });
  };

  var initialize = function() {
    eventbus.on('workList:select', function() {
      $('.container').hide();
      $('#work-list').show();
      $('body').addClass('work-list');
      fetchUnknownLimits();
    });
  };

  root.WorkListView = {
    initialize: initialize
  };

})(this);
