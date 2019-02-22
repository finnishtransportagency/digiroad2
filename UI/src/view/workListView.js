(function (root) {
  root.WorkListView = function(){
    var me = this;
    var warningIcon = '<img src="images/warningLabel.png" title="Pysäkki sijaitsee lakkautetulla tiellä"/>';
    this.initialize = function() {
      me.bindEvents();
      $(window).on('hashchange', this.showApp);
    };
    this.showApp = function() {
      $('.container').show();
      $('#work-list').hide();
      $('body').removeClass('scrollable').scrollTop(0);
    };

    this.bindEvents = function() {
      eventbus.on('workList:select', function(layerName, listP) {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        me.generateWorkList(layerName, listP);
      });
    };

    this.bindExternalEventHandlers = function() {};

    this.workListItemTable = function(layerName, workListItems, municipalityName) {

      var municipalityHeader = function(municipalityName, totalCount) {
        var countString = totalCount ? ' (yhteensä ' + totalCount + ' kpl)' : '';
        return $('<h2/>').html(municipalityName + countString);
      };
      var tableHeaderRow = function(headerName) {
        return $('<caption/>').html(headerName);
      };

      var tableContentRows = function(assetsInfo) {
        return _.map(assetsInfo, function(item) {
          var image = item.floatingReason === 8 ?   warningIcon : '';
          return $('<tr/>').append($('<td/>').append(typeof item.id !== 'undefined' ? assetLink(item) : idLink(item))).append($('<td/>').append(image));
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
      var tableForGroupingValues = function(values, assetsInfo, count) {
        if (!assetsInfo || assetsInfo.length === 0) return '';
        var countString = count ? ' (' + count + ' kpl)' : '';
        return $('<table><tbody>').addClass('table')
          .append(tableHeaderRow(values + countString))
          .append(tableContentRows(assetsInfo))
          .append('</tbody></table>');
      };

      if(layerName === 'maintenanceRoad') {
        var table = $('<div/>');
        table.append(tableForGroupingValues('Tuntematon', workListItems.Unknown));
        for(var i=1; i<=12; i++) {
          table.append(tableForGroupingValues(i, workListItems[i]));
        }
        return table;
      } else

        return $('<div/>').append(municipalityHeader(municipalityName, workListItems.totalCount))
          .append(tableForGroupingValues('Kunnan omistama', workListItems.Municipality, workListItems.municipalityCount))
          .append(tableForGroupingValues('Valtion omistama', workListItems.State, workListItems.stateCount))
          .append(tableForGroupingValues('Yksityisen omistama', workListItems.Private, workListItems.privateCount))
          .append(tableForGroupingValues('Ei tiedossa', workListItems.Unknown, 0));
    };

    this.generateWorkList = function(layerName, listP) {
      var layerInfo = {
        speedLimit: {Title: 'Tuntemattomien nopeusrajoitusten lista',  SourceLayer: 'speedLimit'},
        speedLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'speedLimit'},
        linkProperty: 'Korjattavien linkkien lista',
        massTransitStop: 'Geometrian ulkopuolelle jääneet pysäkit',
        pedestrianCrossings: 'Geometrian ulkopuolelle jääneet suojatiet',
        trafficLights: 'Geometrian ulkopuolelle jääneet liikennevalot',
        obstacles: 'Geometrian ulkopuolelle jääneet esterakennelmat',
        railwayCrossings: 'Geometrian ulkopuolelle jääneet rautatien tasoristeykset',
        directionalTrafficSigns: 'Geometrian ulkopuolelle jääneet opastustaulut',
        trafficSigns: 'Geometrian ulkopuolelle jääneet liikennemerkit',
        maintenanceRoad: 'Tarkistamattomien huoltoteiden lista',

        hazardousMaterialTransportProhibitionErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'hazardousMaterialTransportProhibition'},
        manoeuvreErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'manoeuvre'},
        heightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'heightLimit'},
        bogieWeightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'bogieWeightLimit'},
        axleWeightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'axleWeightLimit'},
        lengthLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'lengthLimit'},
        totalWeightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'totalWeightLimit'},
        trailerTruckWeightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'trailerTruckWeightLimit'},
        widthLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'widthLimit'},
        pedestrianCrossingsErrors: {Title: 'Laatuvirhelista', SourceLayer: 'pedestrianCrossings'}
      };

      var sourceLayer = (layerInfo[layerName].SourceLayer) ? layerInfo[layerName].SourceLayer : layerName;
      var title = (layerInfo[layerName].Title) ? layerInfo[layerName].Title : layerInfo[layerName];

      $('#work-list').html('' +
        '<div style="overflow: auto;">' +
        '<div class="page">' +
        '<div class="content-box">' +
        '<header>' + title +
        '<a class="header-link" href="#' + sourceLayer + '">Sulje lista</a>' +
        '</header>' +
        '<div class="work-list">' +
        '</div>' +
        '</div>' +
        '</div>'
      );
      listP.then(function(limits) {
        var unknownLimits = _.map(limits, _.partial(me.workListItemTable, layerName));
        $('#work-list .work-list').html(unknownLimits);
      });
    };
  };
})(this);
