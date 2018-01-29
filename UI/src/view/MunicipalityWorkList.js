(function (root) {
  var backend;
  var municipalityList;
  var showFormBtnVisible = true;

  root.MunicipalityWorkList = function(){
    WorkListView.call(this);
    this.initialize = function(mapBackend){
      backend = mapBackend;
      bindExternalEventHandlers();
      bindEvents();
    };
  };
  this.bindExternalEventHandlers = function() {
    eventbus.on('roles:fetched', function(roles) {
      userRoles = roles;
    });
  };
  this.bindEvents = function () {
    eventbus.on('municipality:select', function(listP) {
      $('.container').hide();
      $('#work-list').show();
      $('body').addClass('scrollable');
      municipalityList = listP;
      generateWorkList(listP);
    });
  };
  var userRoles;

  var hrefDir = "#work-list/municipality";
  var municipalityId;
  var municipalityName;


  var municipalityTable = function (municipalities, filter) {
    var municipalityValues =
      _.isEmpty(filter) ? municipalities : _.filter(municipalities, function (municipality) {
          return municipality.name.toLowerCase().startsWith(filter.toLowerCase());});

    var tableContentRows = function (municipalities) {
      return _.map(municipalities, function (municipality) {
        return $('<tr/>').append($('<td/>').append(idLink(municipality)));
      });
    };
    var idLink = function (municipality) {
      return $('<a class="work-list-item"/>').attr('href', hrefDir).html(municipality.name).click(function(){
        createVerificationForm(municipality);
      });
    };
    return $('<table id="tableData"/>').append(tableContentRows(municipalityValues));
  };

  var createVerificationForm = function(municipality) {
    $('#tableData').hide();
    $('.filter-box').hide();
    if (showFormBtnVisible) $('#work-list-header').append($('<a class="header-link"></a>').attr('href', hrefDir).html('Kuntavalinta').click(function(){generateWorkList(municipalityList);}));
    municipalityId = municipality.id;
    municipalityName = municipality.name;
    reloadForm();
  };


  var reloadForm = function(){
    $('#formTable').remove();
    backend.getAssetTypesByMunicipality(municipalityId).then(function(assets){
      $('#work-list .work-list').append(_.map(assets, _.partial(municipalityValidationTable, _ , municipalityName, municipalityId)));
    });
  };

  eventbus.on('municipality:verified', reloadForm);

  var municipalityValidationTable = function (workListItems, municipalityName, municipalityId) {
    var selected = [];
    var municipalityHeader = function (municipalityName) {
      return $('<h2/>').html(municipalityName);
    };

    var tableHeaderRow = function () {
      return '<tr> <th></th> <th id="name">TIETOLAJI</th> <th id="date">TARKISTETTU</th> <th id="verifier">TARKISTAJA</th></tr>';
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
        "<td headers='name'>" + asset.assetName + "    <img src='images/oldAsset.png' title='Tarkistus Vanhentumassa'" + "</td>" +
        "<td style='color:red' headers='date'>" + asset.verified_date + "</td>" +
        "<td style='color:red' headers='verifier'>" + asset.verified_by + "</td></tr>";
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
      return $('<table/>').addClass('table')
        .append(tableHeaderRow())
        .append(tableContentRows(values));
    };

    return $('<table id="formTable"/>').append(municipalityHeader(municipalityName)).append(tableForGroupingValues(workListItems)).append(deleteBtn).append(saveBtn);
  };

  var searchbox = $('<div class="filter-box">' +
    '<input type="text" class="location input-sm" placeholder="Kuntanimi" id="searchBox"></div>');

  var generateWorkList = function (listP) {
    var title = 'Tietolajien kuntasivu';
    $('#work-list').html('' +
      '<div style="overflow: auto;">' +
      '<div class="page">' +
      '<div class="content-box">' +
      '<header id="work-list-header">' + title +
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
        showFormBtnVisible = false;
        createVerificationForm(_.first(limits));
      }else{
        var unknownLimits = _.partial.apply(null, [municipalityTable].concat([limits, ""]))();
        element.html($('<div class="municipality-list">').append(unknownLimits));

        if (_.contains(userRoles, 'operator') || _.contains(userRoles, 'premium'))
          searchbox.insertBefore('table');

        $('#searchBox').on('keyup', function(event){
          var currentInput = event.currentTarget.value;

          var unknownLimits = _.partial.apply(null, [municipalityTable].concat([limits, currentInput]))();
          $('#tableData tbody').html(unknownLimits);
        });
      }
    });
  };

})(this);