(function (root) {
  root.MunicipalityVerificationForm = {
    initialize: initialize
  };

  function initialize(backend){

    var bindEvents = function () {
      eventbus.on('municipalityForm:open', function (municipalityId, listP) {
        $('.container').hide();
        $('#municipality-work-list').show();
        $('#work-list').hide();
        $('body').addClass('scrollable');
        generateWorkList(listP);
        municipalityCode = municipalityId;
      });
    };

    bindEvents();

    var municipalityCode;

    function verifyMunicipalityAssets(selectedAssets, municipalityCode) {
      backend.verifyMunicipalityAssets(selectedAssets, municipalityCode);
    }
    function removeMunicipalityVerification(selectedAssets, municipalityCode) {
      backend.removeMunicipalityVerification(selectedAssets, municipalityCode);
    }

    var municipalityValidationTable = function (workListItems, municipalityName) {
      var municipalityHeader = function (municipalityName) {
        return $('<h2/>').html(municipalityName);
      };
      var tableHeaderRow = function () {
        return '<tr>' +
          '<th></th>' +
          '<th id="name">' + 'TIETOLAJI' + '</th>' +
          '<th id="date">' + 'TARKISTETTU' + '</th>' +
          '<th id="verifier">' + 'TARKISTAJA' + '</th></tr>';
      };

      var tableContentRows = function (values) {
        var rows = "";
        _.forEach(values, function (asset) {
          rows += (asset.verified || _.isEmpty(asset.verified_by)) ? upToDateAsset(asset) : oldAsset(asset);
        });
        return rows;
      };

      var upToDateAsset = function (asset) {
        return "<tr><td><input type='checkbox' class='verificationCheckbox' value='" + asset.typeId + "'></td>" +
          "<td headers='name'>" + asset.assetName + "</td>" +
          "<td headers='date'>" + asset.verified_date + "</td>" +
          "<td headers='verifier'>" + asset.verified_by + "</td></tr>";
      };

      var oldAsset = function (asset) {
        return "<tr><td><input type='checkbox' class='verificationCheckbox' value='" + asset.typeId + "'></td>" +
          "<td headers='name'>" + asset.assetName + "    <img src='images/oldAsset.png'" + "</td>" +
          "<td style='color:red' headers='date'>" + asset.verified_date + "</td>" +
          "<td style='color:red' headers='verifier'>" + asset.verified_by + "</td></tr>";
      };

      var tableForGroupingValues = function (values) {
        return $('<table/>').addClass('table')
          .append(tableHeaderRow())
          .append(tableContentRows(values));
      };

      return $('<div/>').append(municipalityHeader(municipalityName)).append(tableForGroupingValues(workListItems));
    };

    var generateWorkList = function (listP) {

      var buttons = function () {
        return '<input type="button" class="btn btn-municipality" id="verify" value="Merkitse tarkistetuksi" />' +
          '<input type="button" class="btn btn-municipality" id="remove" value="Nollaa" />';
      };

      $('#municipality-work-list').html('' +
        '<div style="overflow: auto;">' +
          '<div class="municipality-page">' +
            '<div class="municipality-content-box">' +
              '<header>' + "Kuntatarkistus" +
                '<input id="close-form-btn" type="button" class="btn header-link-btn" value="Sulje lista"/>' +
               '<a class="header-link" href="#work-list/municipality">Kuntavalinta</a>' +
              '</header>' +
            '<div class="municipality-work-list">' +
            '</div>' +
            buttons() +
          '</div>' +
        '</div>'
      );
      var showApp = function () {
        if(window.location.href.indexOf("work-list/municipality") === -1) {
          $('.container').show();
          $('#work-list').hide();
          $('body').removeClass('scrollable').scrollTop(0);
          $(window).off('hashchange', showApp);
        }
      };

      $(window).on('hashchange', showApp);

      $("#close-form-btn").on("click", function () {
        var link = !_.isUndefined(window.applicationModel.getSelectedLayer()) ? window.applicationModel.getSelectedLayer() : '';
        window.location.href='index.html#' + link;
      });


      listP.then(function (assetTypes) {
        var assetTypesListed = _.map(assetTypes, _.partial(municipalityValidationTable));
        $('#municipality-work-list .municipality-work-list').html(assetTypesListed);

        var selected = [];

        $("#verify").on("click", function () {
          $("input:checkbox[class=verificationCheckbox]:checked").each(function () {
            selected.push(parseInt(($(this).attr('value'))));
          });
          verifyMunicipalityAssets(selected, municipalityCode);
        });

        $("#remove").on("click", function () {
          new GenericConfirmPopup("Haluatko varmasti nollata tietolajin tarkistuksen?",
            {container: '#municipality-work-list',
            successCallback: function () {
              $("input:checkbox[class=verificationCheckbox]:checked").each(function () {
                selected.push(parseInt(($(this).attr('value'))));
              });
              removeMunicipalityVerification(selected, municipalityCode);
            }
          });
        });
      });
    };
  }
})(this);
