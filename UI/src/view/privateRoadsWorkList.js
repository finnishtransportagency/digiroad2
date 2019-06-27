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
        addSpinner();
        me.generateWorkList(listP);
      });
    };

    var addSpinner = function () {
      $('#work-list').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
    };

    var removeSpinner = function(){
      $('.spinner-overlay').remove();
    };

    this.workListItemTable = function(result) {
      var additionalInfoIds = {
        1: 'Tieto toimitettu, rajoituksia',
        2: 'Tieto toimitettu, ei rajoituksia',
        99: 'Ei toimitettu'
      };

      var downloadCsvButton = $('<button />').addClass('btn btn-primary btn-download')
        .text('Lataa CSV')
        .append("<img src='images/icons/export-icon.png'/>")
        .click();

      var municipilatyHeader = function(municipalityName) {
        return $('<div class="municipality-header"/>').append($('<h2/>').html(municipalityName)).append(downloadCsvButton);
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

      return $('<div id="formTable"/>').append(municipilatyHeader(result.municipalityName)).append(tableForGroupingValues(result));
    };

    this.generateWorkList = function(listP) {
      listP.then(function (result){
        removeSpinner();
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