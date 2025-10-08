(function(root) {
  root.PrivateRoadsWorkList = function() {
    WorkListView.call(this);
    var me = this;
    this.hrefDir = "#work-list/privateRoads";
    this.title = "Yksityistiet";
    var backend;
    var municipalityName;
    var authorizationPolicy = new AuthorizationPolicy();
    var assetConfig = new AssetTypeConfiguration();

    this.initialize = function(mapBackend) {
      me.backend = mapBackend;
      me.bindEvents();
    };

    this.bindEvents = function() {
      eventbus.on('privateRoadsWorkList:select', function(listP){
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        me.generateWorkList(listP);
        me.addSpinner();
      });
    };

    this.downloadCSV = function(csv, filename) {
      var csvFile = new Blob(["\ufeff", csv], {type: "text/csv;charset=ANSI"});
      $('.btn-download').attr("href", window.URL.createObjectURL(csvFile)).attr("download", filename).one("click", function(){});
    };

    this.exportTableToCSV = function(municipalityName, html, filename) {
      var date = new Date();
      var csv = [];

      // When extracting the month from the date (date.getMonth()), we add 1 to the result since the method returns the month as a number from 0 to 11.
      csv.push(municipalityName + ";Tulostettu;" + String(date.getDate()).padStart(2, '0') + "/" + String(date.getMonth() + 1).padStart(2, '0') + "/" + String(date.getFullYear()));

      var rows = document.querySelectorAll("table tr");

      for (var i = 0; i < rows.length; i++) {
        var row = [], cols = rows[i].querySelectorAll("td, th");

        for (var j = 0; j < cols.length; j++)
          row.push(cols[j].innerText);

        csv.push(row.join(";"));
      }

      me.downloadCSV(csv.join("\n"), filename);
    };

    this.workListItemTable = function(result) {
      var additionalInfoIds = {
        1: 'Tieto toimitettu, rajoituksia',
        2: 'Tieto toimitettu, ei rajoituksia',
        99: 'Ei toimitettu'
      };

      var downloadCsvButton = $('<a></a>').addClass('btn btn-primary btn-download')
        .text('Lataa CSV')
        .append("<img src='images/icons/export-icon.png'/>")
        .click(function() {
          var html = $(".work-list").find(" table");
          var date = new Date();
          // When extracting the month from the date (date.getMonth()), we add 1 to the result since the method returns the month as a number from 0 to 11.
          me.exportTableToCSV(result.municipalityName, html, me.title.toLowerCase()  + "_" + result.municipalityCode + "_" + String(date.getDate()).padStart(2, '0') + "-" + String(date.getMonth() + 1).padStart(2, '0') + "-" + String(date.getFullYear())+ ".csv");
        });

      var municipalityHeader = function(municipalityName) {
        return $('<div class="municipality-header"></div>').append($('<h2></h2>').html(municipalityName)).append(downloadCsvButton);
      };

      var tableHeaderRow = function() {
        return '<thead> <th id="privateRoadName">Tiekunta</th> <th id="associationId">Käyttöoikeustunnus</th> <th id="additionalInfo">Lisätieto</th> <th id="lastModifiedDate">Muokattu viimeksi</th>' +
          ' </tr></thead>';
      };

      var tableBodyRows = function(values) {
        return _.map(values, function(privateInfo) {
          return '' +
            '<tr>' +
            '<td headers="privateRoadName">' + privateInfo.privateRoadName + '</td>' +
            '<td headers="associationId">' + (privateInfo.associationId ? privateInfo.associationId : '') + '</td>' +
            '<td headers="additionalInfo" >' + additionalInfoIds[privateInfo.additionalInfo] + '</td>' +
            '<td headers="lastModifiedDate">' + privateInfo.lastModifiedDate + '</td>' +
            '</tr>';
        });
      };

      var tableForGroupingValues = function(values) {
        return $('<table>').addClass('table')
          .append(tableHeaderRow())
          .append($('<tbody>').append(tableBodyRows(values.results))).append('</tbody>');
      };

      return $('<div id="formTable"></div>').append(municipalityHeader(result.municipalityName)).append(tableForGroupingValues(result));
    };

    this.generateWorkList = function(listP) {
      listP.then(function (result){
        me.removeSpinner();
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
