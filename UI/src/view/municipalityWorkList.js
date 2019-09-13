(function (root) {
  root.MunicipalityWorkList = function(){
    WorkListView.call(this);
    var me = this;
    this.hrefDir = "#work-list/municipality";
    this.title = 'Tietolajien kuntasivu';
    var backend;
    var municipalityList;
    var fromMunicipalityTabel = false;
    var municipalityName;
    var authorizationPolicy = new AuthorizationPolicy();
    var assetConfig = new AssetTypeConfiguration();

    var addSpinner = function () {
      $('#work-list').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
    };

    var removeSpinner = function(){
      $('.spinner-overlay').remove();
    };

    this.initialize = function(mapBackend){
      backend = mapBackend;
      me.bindEvents();
    };
    this.bindEvents = function () {
      eventbus.on('municipality:select', function(municipalityListAllowed, municipalityCode) {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        municipalityList = municipalityListAllowed;

        if (_.isNull(municipalityCode)) {
          me.generateWorkList(municipalityListAllowed);
        } else {
          me.generateWorkList(backend.getUnverifiedMunicipalities(municipalityCode));
        }
      });

      eventbus.on('municipality:verified', me.reloadForm);
    };

    this.municipalityTable = function (municipalities, filter) {
      var municipalityValues =
        _.isEmpty(filter) ? municipalities : _.filter(municipalities, function (municipality) {
          return municipality.name.toLowerCase().startsWith(filter.toLowerCase());});

      var tableContentRows = function (municipalities) {
        return _.map(municipalities, function (municipality) {
          return $('<tr/>').append($('<td/>').append(idLink(municipality)));
        });
      };
      var idLink = function (municipality) {
        return $('<a class="work-list-item"/>').attr('href', me.hrefDir).html(municipality.name).click(function(){
          fromMunicipalityTabel = true;
          me.createVerificationForm(municipality);
        });
      };
      return $('<table id="tableData"><tbody>').append(tableContentRows(municipalityValues)).append('</tbody></table>');
    };

    this.createVerificationForm = function(municipality) {
      $('#tableData').hide();
      $('.filter-box').hide();
      var page = $('.page');
      page.attr('class', 'page-content-box');
      if (fromMunicipalityTabel) page.find('#work-list-header').append($('<a class="header-link"></a>').attr('href', me.hrefDir).html('Kuntavalinta').click(function(){
          me.generateWorkList(municipalityList);
        })
      );
      municipalityName = municipality.name;
      me.reloadForm(municipality.id);
    };

    this.reloadForm = function(municipalityId, refresh){
      refresh = _.isUndefined(refresh) ? false : refresh;
      $('#formTable').remove();
      addSpinner();
      backend.getAssetTypesByMunicipality(municipalityId, refresh).then(function(assets){
        $('#work-list .work-list').html(unknownLimitsTable(assets , municipalityName, municipalityId));
        removeSpinner();
      });
    };

    eventbus.on('municipality:verified', function(id) {
      me.reloadForm(id);
    });

    var unknownLimitsTable = function (workListItems, municipalityName, municipalityId) {
      var selected = [];
      var refreshButton = $('<button />').addClass('btn btn-quinary btn-refresh')
        .text('Tiedot viimeksi päivitetty: ' + workListItems.refreshDate)
        .append("<img src='images/icons/refresh-icon.png'/>")
        .click(function(){
          me.reloadForm(municipalityId, true);
        });

      var privateRoadInfoListButton = $('<a>').addClass('btn btn-primary btn-private-road-list')
        .text('Yksityistiet').attr("href", "#work-list/privateRoads/" + municipalityId);

      var municipalityHeader = function (municipalityName) {
        return $('<div class="municipality-header"/>').append($('<h2/>').html(municipalityName)).append(refreshButton).append(privateRoadInfoListButton);
      };

      var tableHeaderRow = function () {
        return '<thead><th></th> <th id="name">Tietolaji</th> <th id="count">Kohteiden määrä u/ Kohteita</th> <th id="date">Tarkistettu</th> <th id="verifier">Tarkistaja</th>' +
               '<th id="modifiedBy">Käyttäjä</th> <th id="modifiedDate">Viimeisin päivitys</th></tr></thead>';
      };
      var tableBodyRows = function (values) {
        return $('<tbody>').append(tableContentRows(values));
      };
      var tableContentRows = function (values) {
        renameAssets(values);
        values = sortAssets(values);
        return _.map(values, function (asset) {
          return (asset.verified || _.isEmpty(asset.verified_by)) ? upToDateAsset(asset).concat('') : oldAsset(asset).concat('');
        });
      };


      var renameAssets = function (values) {
        _.forEach(values, function (asset) {
          asset.assetName = _.find(assetConfig.assetTypeInfo, function(config){ return config.typeId ===  asset.typeId; }).title ;
        });
      };

      var sortAssets = function (values) {
        var assetOrdering = [
          'Nopeusrajoitus',
          'Joukkoliikenteen pysäkki',
          'Kääntymisrajoitus',
          'Ajoneuvokohtaiset rajoitukset',
          'VAK-rajoitus',
          'Liikennemerkit',
          'Suurin sallittu massa',
          'Yhdistelmän suurin sallittu massa',
          'Suurin sallittu akselimassa',
          'Suurin sallittu telimassa',
          'Suurin sallittu korkeus',
          'Suurin sallittu pituus',
          'Suurin sallittu leveys',
          'Esterakennelma',
          'Päällyste',
          'Leveys',
          'Kaistojen lukumäärä',
          'Joukkoliikennekaista',
          'Rautatien tasoristeys',
          'Liikennevalo',
          'Opastustaulu',
          'Palvelupiste',
          'Kelirikko',
          'Suojatie',
          'Valaistus'
        ];

        return _.sortBy(values, function(property) {
          return _.indexOf(assetOrdering, property.assetName);
        });
      };
      var upToDateAsset = function (asset) {
        return '' +
          '<tr>' +
          '<td><input type="checkbox" class="verificationCheckbox" value=' + asset.typeId + '></td>' +
          '<td headers="name">' + asset.assetName + '</td>' +
          '<td headers="count">' + (asset.type === 'point' ? (asset.counter ? asset.counter : '' ) : (asset.counter ? 'Kyllä' : '' )) + '</td>' +
          '<td headers="date" >' + asset.verified_date + '</td>' +
          '<td headers="verifier">' + asset.verified_by + '</td>' +
          '<td headers="modifiedBy">' + asset.modified_by + '</td>' +
          '<td headers="modifiedDate">' + asset.modified_date + '</td>' +
          '</tr>';
      };
      var oldAsset = function (asset) {
        return '' +
          '<tr>' +
          '<td><input type="checkbox" class="verificationCheckbox" value=' + asset.typeId + '></td>' +
          '<td headers="name">' + asset.assetName + '<img src="images/error-icon-small.png" title="Tarkistus Vanhentumassa"' + '</td>' +
          '<td style="color:red" headers="count">' + (asset.counter ? asset.counter : '' )  + '</td>' +
          '<td style="color:red" headers="date">' + asset.verified_date + '</td>' +
          '<td style="color:red" headers="verifier">' + asset.verified_by + '</td>' +
          '</tr>'.join('');
      };

      var saveBtn = $('<button />').addClass('save btn btn-municipality').text('Merkitse tarkistetuksi').click(function () {
        $("input:checkbox[class=verificationCheckbox]:checked").each(function () {
          selected.push(parseInt(($(this).attr('value'))));
        });
        backend.verifyMunicipalityAssets(selected, municipalityId);
      });

      var deleteBtn = $('<button />').addClass('delete btn btn-municipality').text('Nollaa').click(function () {
        new GenericConfirmPopup("Haluatko varmasti nollata tietolajin tarkistuksen?", {
          container: '#work-list',
          successCallback: function () {
            $(".verificationCheckbox:checkbox:checked").each(function () {
              selected.push(parseInt(($(this).attr('value'))));
            });
            backend.removeMunicipalityVerification(selected, municipalityId);
          },
          closeCallback: function () {}
        });
      });

      var tableForGroupingValues = function (values) {
        return $('<table>').addClass('table')
          .append(tableHeaderRow())
          .append(tableBodyRows(values));
      };

      return $('<div id="formTable"/>').append(municipalityHeader(municipalityName)).append(tableForGroupingValues(workListItems.properties)).append(deleteBtn).append(saveBtn);
    };

    this.generateWorkList = function (listP) {
      var searchbox = $('<div class="filter-box">' +
        '<input type="text" class="location input-sm" placeholder="Kuntanimi" id="searchBox"></div>');

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

      listP.then(function (limits) {
        var element = $('#work-list .work-list');
        if (limits.length == 1){
          me.createVerificationForm(_.head(limits));
        }
        else {
          fromMunicipalityTabel = false;
          var unknownLimits = _.partial.apply(null, [me.municipalityTable].concat([limits, ""]))();
          element.html($('<div class="municipality-list">').append(unknownLimits));

          if (authorizationPolicy.workListAccess())
            searchbox.insertBefore('#tableData');

          $('#searchBox').on('keyup', function (event) {
            var currentInput = event.currentTarget.value;

            var unknownLimits = _.partial.apply(null, [me.municipalityTable].concat([limits, currentInput]))();
            $('#tableData tbody').html(unknownLimits);
          });
        }
      });
    };
  };
})(this);