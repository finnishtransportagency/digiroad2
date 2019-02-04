(function (root) {
  root.CreatedLinearAssetWorkList = function() {
    WorkListView.call(this);
    var me = this;
    this.hrefDir = "#work-list/createdLinearAssets";
    this.title = 'Lisätyt viivamaiset kohteet';
    var backend;
    var showFormBtnVisible = true;
    var assetsList;
    var assetTypeName;
    var authorizationPolicy = new AuthorizationPolicy();
    var assetConfig = new AssetTypeConfiguration();

    var availableAssetsTypeId = [190];

    this.initialize = function(mapBackend) {
      backend = mapBackend;
      me.bindEvents();
    };

    this.bindEvents = function () {
      eventbus.on('createdLinearAssets:select', function() {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        // assetsList = assetsCreated;
        me.generateWorkList(availableAssetsTypeId);
      });
    };

    this.createVerificationForm = function(assetType) {
      $('#tableData').hide();
      $('.filter-box').hide();
      if (showFormBtnVisible) $('#work-list-header').append($('<a class="header-link"></a>').attr('href', me.hrefDir).html('Kuntavalinta').click(function(){
          me.generateWorkList(availableAssetsTypeId);
        })
      );
      assetTypeName = renameAssetLink(assetType);
      me.reloadForm(assetType);
    };

    this.assetTypesTable = function(assetsTypeIds)  {
      var tableContentRows = function(assetTypes) {
        return _.map(assetTypes, function(assetType) {
          return $('<tr/>').append($('<td/>').append(assetLink(assetType)));
        });
      };

      var assetLink = function(assetType) {
        return $('<a class="work-list-item"/>').attr('href', me.hrefDir).html(renameAsset(assetType)).click(function(){
          me.createVerificationForm(assetType);
        });
      };

      return $('<table id="tableData"><tbody>').append(tableContentRows(assetsTypeIds)).append('</tbody></table>');
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
          return $('<tr/>').append($('<td/>').append(assetLink(id._1)));
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
                        .append(tableForGroupingValues(assetContent.created_assets));
                                    
    };

    this.reloadForm = function(assetTypeId){
      $('#formTable').remove();
      backend.getCreatedLinearAssets(assetTypeId).then( function(assets){
        $('#work-list .work-list').html($('<h2/>').html(renameAsset(assetTypeId)).append(_.map(assets, function(assets) { return me.generatedLinearAssetsTable(assets, assetTypeId);})));
      });

    };

    var renameAsset = function(assetTypeId) {
      return _.find(assetConfig.assetTypeInfo, function(config){ return config.typeId ===  assetTypeId; }).title ;
    };

    var renameAssetLink = function(assetTypeId) {
      return _.find(assetConfig.linearAssetsConfig, function(config){ return config.typeId ===  assetTypeId; }).singleElementEventCategory ;
    };

    this.generateWorkList = function(assetsList) {

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


      var element = $('#work-list .work-list');
      element.html($('<div class="linear-asset-list">').append(me.assetTypesTable(assetsList)));

      // assetsList.then(function(createdLinearAssets){
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