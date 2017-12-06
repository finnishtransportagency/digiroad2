(function (root) {
  var verificationListTable = function(layerName, workListItems, municipalityName) {
    var municipalityHeader = function(municipalityName, totalCount) {
      var countString = totalCount ? ' (yhteensä ' + totalCount + ' kpl)' : '';
      return $('<h2/>').html(municipalityName + countString);
    };
    var tableHeaderRow = function(headerName) {
      return $('<caption/>').html(headerName);
    };
    var tableContentRows = function(Ids) {
      return _.map(Ids, function(item) {
        return $('<tr/>').append($('<td/>').append(typeof item.id !== 'undefined' ? assetLink(item) : idLink(item)));
      });
    };
    var idLink = function(id) {
      var link = '#' + layerName + '/verification/'+ id;
      return $('<a class="work-list-item"/>').attr('href', link).html(link);
    };
    var assetLink = function(asset) {
      var link = '#' + layerName + '/verification/' + asset.id;
      return $('<a class="work-list-item"/>').attr('href', link).html(link);
    };
    var tableForGroupingValues = function(values, Ids, count) {
      if (!Ids || Ids.length === 0) return '';
      var countString = count ? ' (' + count + ' kpl)' : '';
      return $('<table/>').addClass('table')
        .append(tableHeaderRow(values + countString))
        .append(tableContentRows(Ids));
    };

    return $('<div/>').append(municipalityHeader(municipalityName, workListItems.totalCount))
      .append(tableForGroupingValues('Kunnan omistama', workListItems.Municipality, workListItems.municipalityCount))
      .append(tableForGroupingValues('Valtion omistama', workListItems.State, workListItems.stateCount))
      .append(tableForGroupingValues('Yksityisen omistama', workListItems.Private, workListItems.privateCount))
      .append(tableForGroupingValues('Ei tiedossa', workListItems.Unknown, 0));
  };

  var generateWorkList = function(layerName, listP) {
    var title = 'Vanhentuneiden kohteiden lista';
    $('#work-list').html('' +
      '<div style="overflow: auto;">' +
        '<div class="page">' +
          '<div class="content-box">' +
            '<header>' + title +
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
    listP.then(function(assets) {
      var unverifiedAssets = _.map(assets, _.partial(verificationListTable, layerName));
      $('#work-list .work-list').html(unverifiedAssets);
    });
  };

  var bindEvents = function() {
    eventbus.on('verificationList:select', function(layerName, listP) {
      $('.container').hide();
      $('#work-list').show();
      $('body').addClass('scrollable');

      generateWorkList(layerName, listP);
    });
  };

  root.VerificationListView = {
    initialize: function() {
      bindEvents();
    }
  };

})(this);
