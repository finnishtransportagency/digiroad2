(function (root) {
  root.AssetSelectionMenu = function(linearAssetGroups, pointAssetGroups, maintenanceAssetGroup, options) {
    var onSelection = options.onSelect;
    var assetSelection = $('<div class="asset-selection">' +
      '<div class="column-1"></div>' +
      '<div class="column-2"></div>'+
      '</div>');

    var linearAssets = $('<div class="linear-assets-column"></div>');
    var pointAssets = $('<div class="point-assets-column"></div>');
    var maintenanceAsset = $('<div class="maintenance-asset-column"></div>');

    var linearGroup = $('<span><h2>Viivamaiset kohteet</h2></span>');
    var pointGroup = $('<span><h2>Pistemäiset kohteet</h2></span>');
    var maintenanceGroup = $('<span><h3>Vain ELYn ylläpidossa</h3></span>');

    var linearAssetLinks =
      _.chain(linearAssetGroups)
        .map(buildAssetsOptions)
        .values()
        .flatten()
        .value();

    var pointAssetLinks =
      _.chain(pointAssetGroups)
        .map(buildAssetsOptions)
        .values()
        .flatten()
        .value();

    var maintenanceAssetLink = buildAssetsOptions(maintenanceAssetGroup);

    function buildAssetsOptions(assets) {
        var assetGroupDiv = $('<div class="asset-group"></div>');
        return assetGroupDiv.append(_.map(assets, function (asset) {
          var radioDiv= $($('<div class="radio">').append($('<label>', {
            'for': 'nav-' + asset.layerName,
            text: ' ' + asset.title
          }).prepend($('<input>', {
            type: 'radio',
            name: 'navigation-radio',
            id: 'nav-' + asset.layerName,
            value: asset.layerName
          }))));
          return radioDiv;
        }));
      }

    linearAssets.append(linearGroup);
    linearAssets.append(linearAssetLinks);

    pointAssets.append(pointGroup);
    pointAssets.append(pointAssetLinks);

    maintenanceAsset.append(maintenanceGroup);
    maintenanceAsset.append(maintenanceAssetLink);

    assetSelection.find('.column-1').append(linearAssets);
    assetSelection.find('.column-2').append(pointAssets);
    assetSelection.find('.column-2').append(maintenanceAsset);

    assetSelection.hide();

    startListening();

    function startListening() {
      assetSelection.on('click', 'input', onClick);
    }

    function ignoringEvents(f) {
      assetSelection.off('click', 'input', onClick);
      f();
      startListening();
    }

    function onClick() {
      hide();
      onSelection($(this).val());
    }

    function toggle() {
      assetSelection.toggle();
    }

    function hide() {
      assetSelection.hide();
    }

    function select(layer) {
      ignoringEvents(function() {
        assetSelection.find('input[value="' + layer + '"]').click();
      });
    }

    return {
      toggle: toggle,
      hide: hide,
      element: assetSelection,
      select: select
    };
  };
})(this);
