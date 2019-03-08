(function (root) {
  root.CreatedLinearAssetWorkList = function() {
    WorkListView.call(this);
    var me = this;
    // this.hrefDir = "#work-list/unverifiedPointAssets/layer";
    this.title = 'Unverified Linear Assets';
    var backend;
    var showFormBtnVisible = true;
    var assetsList;
    var assetTypeName;
    var authorizationPolicy = new AuthorizationPolicy();
    var assetConfig = new AssetTypeConfiguration();

    this.initialize = function(mapBackend) {
      backend = mapBackend;
      me.bindEvents();
    };

    this.bindEvents = function () {
      eventbus.on('pointWorkList:select', function(unverifiedAssets) {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        assetsList = unverifiedAssets;
        me.generateWorkList(unverifiedAssets);
      });
    };

    this.createVerificationForm = function(assetType) {
      $('#tableData').hide();
      $('.filter-box').hide();
      if (showFormBtnVisible) $('#work-list-header').append($('<a class="header-link"></a>').attr('href', me.hrefDir).html('Kuntavalinta').click(function(){
          me.generateWorkList(assetsList);
        })
      );
      assetTypeName = renameAssetLink(assetType);
      me.reloadForm(assetType);
    };

    this.assetTypesTable = function(assetTypes)  {
      var tableContentRows = function(assetTypes) {
        return _.map(assetTypes, function(assetType) {
          return $('<tr/>').append($('<td/>').append(assetLink(assetType)));
        });
      };

      var assetLink = function(assetType) {
        return $('<a class="work-list-item"/>').attr('href', me.hrefDir).html(renameAsset(assetType.typeId)).click(function(){
          me.createVerificationForm(assetType);
        });
      };

      return $('<table id="tableData"><tbody>').append(tableContentRows(assetTypes)).append('</tbody></table>');
    };

    this.assetHeader = function(assetName) {
      return $('<h2/>').html(renameAsset(assetName));
    };

    this.generatedLinearAssetsTable = function(assetContent, assetTypeId) {

      var municipalityHeader = function(municipalityName) {
        return $('<h3/>').html(municipalityName);
      };

      var tableBodyRows = function(values) {
        return $('<tbody>').append(tableContentRows(values));
      };

      var tableContentRows = function(ids) {
        return _.map(ids, function(id) {
          return $('<tr/>').append($('<td/>').append(assetLink(id)));
        });
      };

      var assetLink = function(id) {
        var link = '#' + renameAssetLink(assetTypeId) + '/' + id;
        var workListItem = $('<a class="work-list-item"/>').attr('href', link).html(link);
        return workListItem;
      };

      var tableForGroupingValues = function(assetIds) {
        return $('<table/>').addClass('table')
          .append(tableBodyRows(assetIds));
      };

      return $('<div/>').append(municipalityHeader(assetContent.municipality))
        .append(tableForGroupingValues(assetContent.asset_ids));

    };

    this.reloadForm = function(asset){
      $('#formTable').remove();
      $('#work-list .work-list').html(this.assetHeader(asset.typeId).append(_.map(asset.municipalities, function(assetContent) { return me.generatedLinearAssetsTable(assetContent, asset.typeId);})));
    };

    var renameAsset = function(assetTypeId) {
      return _.find(assetConfig.assetTypeInfo, function(config){ return config.typeId ===  assetTypeId; }).title ;
    };

    var renameAssetLink = function(assetTypeId) {
      return _.find(assetConfig.linearAssetsConfig, function(config){ return config.typeId ===  assetTypeId; }).singleElementEventCategory ;
    };

    this.generateWorkList = function(assetsList) {
      // var searchbox = $('<div class="filter-box">' +
      //   '<input type="text" class="location input-sm" placeholder="Kuntanimi" id="searchBox"></div>');

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

      // assetsList.then(function(createdLinearAssets){
      //   var element = $('#work-list .work-list');
      //   element.html($('<div class="linear-asset-list">').append(me.assetTypesTable(createdLinearAssets)));
      //
      //   // if (authorizationPolicy.workListAccess())
      //   //   searchbox.insertBefore('#tableData');
      //   //
      //   // $('#searchBox').on('keyup', function (event) {
      //   //   var currentInput = event.currentTarget.value;
      //   //
      //   //   var unknownLimits = _.partial.apply(null, [me.municipalityTable].concat([limits, currentInput]))();
      //   //   $('#tableData tbody').html(unknownLimits);
      //   // });
      //
      // });
    };

  };
})(this);