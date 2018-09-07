(function (root) {
  root.InaccurateWorkList = function(){
    WorkListView.call(this);
    var me = this;
    this.initialize = function(){
      me.bindEvents();
    };

    this.workListItemTable = function(layerName, workListItems, municipalityName) {

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
      var tableForGroupingValues = function(values, Ids) {
        if (!Ids || Ids.length === 0) return '';
        return $('<table/>').addClass('table')
          .append(tableHeaderRow(values))
          .append(tableContentRows(Ids));
      };

      return $('<div/>').append(municipalityHeader(municipalityName, workListItems.totalCount))
          .append(tableForGroupingValues('Kunnan omistama', workListItems.Municipality))
          .append(tableForGroupingValues('Valtion omistama', workListItems.State))
          .append(tableForGroupingValues('Yksityisen omistama', workListItems.Private))
          .append(tableForGroupingValues('Ei tiedossa', workListItems.Unknown));
    };
  };
})(this);
