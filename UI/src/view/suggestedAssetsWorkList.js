(function (root) {
  root.SuggestedAssetsWorkList = function(){
    WorkListView.call(this);
    var me = this;
    this.hrefDir = "#work-list/suggestedAssets";
    this.title = 'Vihjetiedot';
    var backend;
    var assetConfig = new AssetTypeConfiguration();

    this.initialize = function(mapBackend) {
      backend = mapBackend;
      me.bindEvents();
    };

    this.bindEvents = function () {
      eventbus.on('suggestedAssets:select', function(listP) {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        me.generateWorkList(listP);
      });
    };

    this.workListItemTable = function(workListItems) {

      var assetHeader = function(items) {
        return $('<h2></h2>').html(items.assetName);
      };

      var tableContentRows = function(values) {
        return _.map(values.assetIds, function(item) {
          return $('<tr></tr>').append($('<td></td>').append(assetLink(item, values.assetTypeId)));
        });
      };

      var assetLink = function(item, assetTypeId) {
        var layerName = _.find(assetConfig.pointAssetsConfig.concat(assetConfig.linearAssetsConfig).concat(assetConfig.assetTypeInfo), function(info){ return info.typeId === assetTypeId;}).layerName;
        var link = '#' + layerName  + '/' + item;
        return $('<a class="work-list-item"></a>').attr('href', link).html(link);
      };

      var tableForGroupingValues = function(values) {
        if (!values || values.length === 0) return '';
        return $('<table><tbody>').addClass('table')
          .append(tableContentRows(values))
          .append('</tbody></table>');
      };

      return $('<div></div>').append(assetHeader(workListItems))
        .append(tableForGroupingValues(workListItems));
    };

    this.generateWorkList = function(listP) {
      listP.then(function(result) {
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

        $('.page').find('#work-list-header').append($('<a class="header-link"></a>').attr('href', '#work-list/municipality/' + result.municipalityCode).html('Kuntavalinta'));

        $('#work-list .work-list').html(me.workListItemTable(result));
      });
    };
  };
})(this);
