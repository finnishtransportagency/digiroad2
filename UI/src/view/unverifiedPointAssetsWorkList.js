(function (root) {
  root.UnverifiedPointAssetsWorkList = function() {
    WorkListView.call(this);
    var me = this;
    this.title = 'Tarkistamattomien opastustaulujen lista';
    var assetsList;

    this.initialize = function() {
      me.bindEvents();
    };

    this.bindEvents = function () {
      eventbus.on('unverifiedPointWorkList:select', function(layerName, unverifiedAssets) {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        assetsList = unverifiedAssets;
        me.generateWorkList(layerName, unverifiedAssets);
      });
    };

    this.workListItemTable = function(layerName, workListItems) {

      var municipalityHeader = function(municipalityName) {
        return $('<h2/>').html(municipalityName);
      };
      var tableHeaderRow = function(headerName) {
        return $('<caption/>').html(headerName);
      };
      var tableContentRows = function(assetIds) {
        return _.map(assetIds, function(item) {
          return $('<tr/>').append($('<td/>').append(idLink(item)));
        });
      };
      var idLink = function(item) {
        var href =  '#' + layerName + '/' + item;
        var link =  '#' + layerName + '/' + item;
        return $('<a class="work-list-item"/>').attr('href', href).html(link);
      };


      var tableForGroupingValues = function(assetIds) {
        if (!assetIds || assetIds.length === 0) return '';
        return $('<table><tbody>').addClass('table')
          .append(tableContentRows(assetIds))
          .append('</tbody></table>');
      };

      return $('<div/>').append(municipalityHeader(workListItems.municipality))
        .append(tableForGroupingValues(workListItems.assets));
    };

    this.generateWorkList = function(layerName, assetsList) {
      $('#work-list').html('' +
        '<div style="overflow: auto;">' +
        '<div class="page">' +
        '<div class="content-box">' +
        '<header id="work-list-header">' + me.title +
        '<a class="header-link" href="#' + window.applicationModel.getSelectedLayer() + '">Sulje</a>' +
        '</header>' +
        '<div class="work-list">' +
        '</div>' +
        '</div>' +
        '</div>'
      );

      assetsList.then(function(unverifiedAssets){
        var unknownLimits = _.map(unverifiedAssets, _.partial(me.workListItemTable, layerName));
        $('#work-list .work-list').html(unknownLimits);
      });
    };

  };
})(this);