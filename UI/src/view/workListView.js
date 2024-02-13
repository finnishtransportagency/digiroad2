(function (root) {
  root.WorkListView = function(){
    var me = this;
    var numberOfLimits;
    var backend;
    var warningIcon = '<img src="images/warningLabel.png" title="Pysäkki sijaitsee lakkautetulla tiellä"/>';
    this.initialize = function(mapBackend) {
        backend = mapBackend;
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

      $('#work-list').on('click', ':checkbox', function () {
        var checkedBoxes = $(".verificationCheckbox:checkbox:checked");
        $('#deleteWorkListItems').prop('disabled', _.isEmpty(checkedBoxes));
      });
    };

    this.bindExternalEventHandlers = function() {};

    this.workListItemTable = function(layerName, showDeleteCheckboxes, workListItems, municipalityName) {
      var selected = [];

      var municipalityHeader = function(municipalityName, totalCount) {
        var countString = totalCount ? ' (yhteensä ' + totalCount + ' kpl)' : '';
        return $('<h2/>').html(municipalityName + countString);
      };
      var tableHeaderRow = function(headerName) {
        return $('<caption/>').html(headerName);
      };

      var checkbox = function(itemId) {
        if(showDeleteCheckboxes) {
          return $('<td class="unknownSpeedLimitCheckboxWidth"/>').append($('<input type="checkbox" class="verificationCheckbox"/>').val(itemId));
        }
      };

      var tableContentRows = function(assetsInfo) {
        return _.map(assetsInfo, function(item) {
          var image = item.floatingReason === 8 ? warningIcon : '';
          var checkboxFunction;
          var idToShow;

          if (!_.isUndefined(item.id)) {
            checkboxFunction = checkbox(item.id);
            idToShow = assetLink(item);
          } else {
            checkboxFunction = checkbox(item);
            idToShow = idLink(item);
          }

          return $('<tr/>').append(checkboxFunction).append($('<td/>').append(idToShow)).append($('<td/>').append(image));
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

      var deleteBtn = function(){
      if(showDeleteCheckboxes && numberOfLimits === 0) {
        numberOfLimits++;
          return $('<button disabled/>').attr('id', 'deleteWorkListItems').addClass('delete btn btn-municipality').text('Poista turhat kohteet').click(function () {
            new GenericConfirmPopup("Haluatko varmasti poistaa valitut tuntemattomat nopeusrajoitukset?", {
              container: '#work-list',
              successCallback: function () {
                $(".verificationCheckbox:checkbox:checked").each(function () {
                  selected.push(parseInt(($(this).attr('value'))));
                });
                backend.deleteUnknownSpeedLimit(selected, function (){
                  new GenericConfirmPopup("Valitut tuntemattomat nopeusrajoitukset poistettu!", {container: '#work-list',type: "alert", okCallback: function() {location.reload();}});
                }, function (){
                  new GenericConfirmPopup("Valittuja tuntemattomia nopeusrajoituksia ei voitu poistaa. Yritä myöhemmin uudelleen!",{container: '#work-list',type: "alert"});
                });
                selected = [];
              },
              closeCallback: function () {}
            });
      });
      }
      };

      if(layerName === 'maintenanceRoad') {
        var table = $('<div/>');
        table.append(tableForGroupingValues('Tuntematon', workListItems.Unknown));
        for(var i=1; i<=12; i++) {
          table.append(tableForGroupingValues(i, workListItems[i]));
        }
        return table;
      } else

        return $('<div/>').append(municipalityHeader(municipalityName, workListItems.totalCount).append(deleteBtn()))
          .append(tableForGroupingValues('Kunnan omistama', workListItems.Municipality, workListItems.municipalityCount))
          .append(tableForGroupingValues('Valtion omistama', workListItems.State, workListItems.stateCount))
          .append(tableForGroupingValues('Yksityisen omistama', workListItems.Private, workListItems.privateCount))
          .append(tableForGroupingValues('Ei tiedossa', workListItems.Unknown, 0));
    };

    this.addSpinner = function () {
      $('#work-list').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
    };

    this.removeSpinner = function () {
      $('.spinner-overlay').remove();
    };

    this.generateWorkList = function(layerName, listP) {
      var errorListSummaryText = 'Laatuvirhelista on muodostettu vertaamalla liikennemerkkejä tietolajin kohteisiin. ' +
          'Tietolajin kohde nostetaan listalle, jos maksimissaan 50 metrin etäisyydellä oleva liikennemerkki ei vastaa tietolajin kohteen tietoja. ' +
          'Laatuvirhelistoja kannattaa käyttää tietolajin oikeellisuuden parantamiseen, kun kunnan alueelta on käytössä ajantasaiset liikennemerkit.';

      var layerInfo = {
        speedLimitUnknown: {Title: 'Tuntemattomien nopeusrajoitusten lista',  SourceLayer: 'speedLimit', ShowDeleteCheckboxes: true},
        speedLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'speedLimit', Summary: errorListSummaryText},
        linkProperty: 'Korjattavien linkkien lista',
        massTransitStopNationalId: 'Geometrian ulkopuolelle jääneet pysäkit',
        pedestrianCrossings: 'Geometrian ulkopuolelle jääneet suojatiet',
        trafficLights: 'Geometrian ulkopuolelle jääneet liikennevalot',
        obstacles: 'Geometrian ulkopuolelle jääneet esterakennelmat',
        railwayCrossings: 'Geometrian ulkopuolelle jääneet rautatien tasoristeykset',
        trafficSigns: 'Geometrian ulkopuolelle jääneet liikennemerkit',
        directionalTrafficSigns: 'Geometrian ulkopuolelle jääneet opastustaulut',
        maintenanceRoad: 'Tarkistamattomien huoltoteiden lista',

        hazardousMaterialTransportProhibitionErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'hazardousMaterialTransportProhibition', Summary: errorListSummaryText},
        manoeuvreErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'manoeuvre', Summary: errorListSummaryText},
        heightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'heightLimit', Summary: errorListSummaryText},
        bogieWeightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'bogieWeightLimit', Summary: errorListSummaryText},
        axleWeightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'axleWeightLimit', Summary: errorListSummaryText},
        lengthLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'lengthLimit', Summary: errorListSummaryText},
        totalWeightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'totalWeightLimit', Summary: errorListSummaryText},
        trailerTruckWeightLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'trailerTruckWeightLimit', Summary: errorListSummaryText},
        widthLimitErrors: {Title: 'Laatuvirhelista',  SourceLayer: 'widthLimit', Summary: errorListSummaryText},
        pedestrianCrossingsErrors: {Title: 'Laatuvirhelista', SourceLayer: 'pedestrianCrossings', Summary: errorListSummaryText},
        lanes: {Title: 'Kaistojen tarkistuslista', SourceLayer: 'laneModellingTool', ShowDeleteCheckboxes: true},
        autoProcessedLanes: {Title: 'Automaattisesti käsiteltyjen kaistojen tarkistuslista', SourceLayer: 'laneModellingTool', ShowDeleteCheckboxes: true},
        assetsOnExpiredLinks: {Title: 'Kohteet päättyneillä tielinkeillä', SourceLayer: 'linkProperty', ShowDeleteCheckboxes: true},
        manoeuvreSamuutusWorkList: {Title: 'Kääntymisrajoituksien samuutuksen tarkistuslista', SourceLayer: 'manoeuvre'}
      };

      var sourceLayer = (layerInfo[layerName].SourceLayer) ? layerInfo[layerName].SourceLayer : layerName;
      var title = (layerInfo[layerName].Title) ? layerInfo[layerName].Title : layerInfo[layerName];
      var showDeleteCheckboxes = (layerInfo[layerName].ShowDeleteCheckboxes) ? layerInfo[layerName].ShowDeleteCheckboxes : false;
      var workListSummary = (layerInfo[layerName].Summary) ? '<div class="work-list-summary">' + layerInfo[layerName].Summary + '</div>'  : '';

      $('#work-list').html('' +
        '<div style="overflow: auto;">' +
        '<div class="page">' +
        '<div class="content-box">' +
        '<header>' + title +
        '<a class="header-link" href="#' + sourceLayer + '">Sulje lista</a>' +
        '</header>' +
        workListSummary +
        '<div class="work-list">' +
        '</div>' +
        '</div>' +
        '</div>'
      );

      me.addSpinner();
      listP.then(function(limits) {
        numberOfLimits = 0;
        var unknownLimits = _.map(limits, _.partial(me.workListItemTable, layerName, showDeleteCheckboxes));
        $('#work-list .work-list').html(unknownLimits);
        me.removeSpinner();
      });
    };
  };
})(this);
