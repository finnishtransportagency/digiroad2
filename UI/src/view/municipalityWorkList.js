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
          me.generateWorkList(backend.getUnverifiedMunicipality(municipalityCode));
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
      if (fromMunicipalityTabel) {
        var innerRoot = page.find('#work-list-header');
        innerRoot.find('.header-link-reports').remove();

        innerRoot.append($('<a class="header-link"></a>').attr('href', me.hrefDir).html('Kuntavalinta').click(function () {
              me.generateWorkList(municipalityList);
            })
        );
      }
      municipalityName = municipality.name;
      me.reloadForm(municipality.id);
    };

    this.reloadForm = function(municipalityId, refresh){
      refresh = _.isUndefined(refresh) ? false : refresh;
      $('#formTable').remove();
      me.addSpinner();
      backend.getAssetTypesByMunicipality(municipalityId, refresh).then(function(assets){
        $('#work-list .work-list').html(unknownLimitsTable(assets , municipalityName, municipalityId));
        me.removeSpinner();
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

      var downloadPDF = function() {
      pdf.removeClass('pdfHide').addClass('pdfBackgroundColor pdfSize');
        var html = $('#formTable').html();

        var takeOutElements = function(beginElement, endElement, sizeElement){
          while (html.indexOf(beginElement) != -1) {
            var l = html.length;
            var i = html.indexOf(beginElement);
            var e = html.indexOf(endElement, i);
            var s = html.slice(i, e + sizeElement);
            html = html.replace(s, '');
          }
        };
        takeOutElements('<button', '</button>', 9);
        takeOutElements('<a', '</a>', 4);
        takeOutElements('<th>', '</th>', 5);
        takeOutElements('<td><input', '</td>', 5);
        takeOutElements('<th id="suggestedAssets">', '</th>', 5);
        takeOutElements('<td headers="suggestedAssets">', '</td>', 5);
        takeOutElements('<div id="test">', '</div>', 6);

        pdf.html('<div class="work-list"><div id="formTable">' + html + '</div></div>');

        var HTML_Width = $("#pdf").width();
        var HTML_Height = $("#pdf").height();
        var top_left_margin = -5;
        var PDF_Width = HTML_Width+(top_left_margin*2);
        var PDF_Height = 985;
        var canvas_image_width = HTML_Width;
        var canvas_image_height = HTML_Height;
        var totalPDFPages = Math.ceil(HTML_Height/PDF_Height)-1;

        html2canvas(document.querySelector('#pdf')).then( function (canvas){
              var imgData = canvas.toDataURL('image/jpeg');
          var doc = new jsPDF('p', 'pt', [PDF_Width, PDF_Height]);
          doc.addImage(imgData, 'JPEG', top_left_margin, top_left_margin,canvas_image_width,canvas_image_height);

          for (var i = 1; i <= totalPDFPages; i++) {
            doc.addPage(PDF_Width, PDF_Height);
            doc.addImage(imgData, 'JPEG', top_left_margin, -(PDF_Height*i)+(top_left_margin*4),canvas_image_width,canvas_image_height);
          }

          var date = new Date();
            doc.save('DR' + '_' + municipalityName.toLowerCase() + '_' + String(date.getDate()).padStart(2, '0') + String(date.getMonth() + 1).padStart(2, '0') + String(date.getFullYear()) + '.pdf');
            });
        pdf.html('').removeClass('pdfSize').addClass('pdfHide');
      };

      var printReportButton = function () {
        if (authorizationPolicy.isOperator())
          return $('<a>').addClass('btn btn-primary btn-print').text('Tulosta raportti').click(function(){
            downloadPDF();
          });
      };

      var pdf = $('<div id="pdf"/>');

      var municipalityHeader = function (municipalityName) {
        return $('<div class="municipality-header"/>').append($('<h2/>').html(municipalityName)).append(refreshButton).append(printReportButton).append(privateRoadInfoListButton);
      };

      var tableHeaderRow = function () {
        return '<thead><th></th> <th id="name">Tietolaji</th> <th id="count">Kohteiden määrä / Kohteita</th> <th id="date">Tarkistettu</th> <th id="verifier">Tarkistaja</th>' +
          '<th id="modifiedBy">Käyttäjä</th> <th id="modifiedDate">Viimeisin päivitys</th> <th id="suggestedAssets">Vihjeet</th> </tr></thead>';
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

      var suggestedAssetsButton = function(counter, typeId) {
        return counter > 0 ? '<a class="btn btn-suggested-list" href="#work-list/suggestedAssets/' + municipalityId + '/'+ typeId + '">' + counter + '</a>' : "";
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
          '<td headers="suggestedAssets">' + suggestedAssetsButton(asset.countSuggested, asset.typeId) + '</td>' +
          '</tr>';
      };
      var oldAsset = function (asset) {
        return '' +
          '<tr>' +
          '<td><input type="checkbox" class="verificationCheckbox" value=' + asset.typeId + '></td>' +
          '<td headers="name">' + asset.assetName + '<img src="images/csv-status-icons/error-icon-small.png" title="Tarkistus Vanhentumassa"' + '</td>' +
          '<td style="color:red" headers="count">' + (asset.counter ? asset.counter : '' )  + '</td>' +
          '<td style="color:red" headers="date">' + asset.verified_date + '</td>' +
          '<td style="color:red" headers="verifier">' + asset.verified_by + '</td>' +
          '</tr>';
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

      return $('<div id="formTable"/>').append(municipalityHeader(municipalityName)).append(tableForGroupingValues(workListItems.properties)).append(deleteBtn).append(saveBtn).append(pdf);
    };

    this.generateWorkList = function (listP) {
      var searchbox = $('<div class="filter-box">' +
        '<input type="text" class="location input-sm" placeholder="Kuntanimi" id="searchBox"></div>');

      var reportButton = authorizationPolicy.isOperator() ? '<a class="header-link-reports" href="#work-list/csvReports">Raportointityökalu</a>' : '';

      $('#work-list').html('' +
        '<div style="overflow: auto;">' +
        '<div class="page">' +
        '<div class="content-box">' +
        '<header id="work-list-header">' + me.title +
         '<a class="header-link" href="#' + window.applicationModel.getSelectedLayer() + '">Sulje</a>' +
        reportButton +
        '</header>' +
        '<div class="work-list">' +
        '</div>' +
        '</div>' +
        '</div>'
      );

      me.addSpinner();
      listP.then(function (limits) {
        var element = $('#work-list .work-list');
        if (limits.length === 1){
          showFormBtnVisible = false;
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
        me.removeSpinner();
      });
    };
  };
})(this);