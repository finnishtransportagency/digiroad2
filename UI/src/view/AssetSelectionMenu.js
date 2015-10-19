(function (root) {
  root.AssetSelectionMenu = function(assetGroups, options) {
    var onSelection = options.onSelect;
    var assetSelection = $('<div class="asset-selection"></div>');

    var assetLinks =
      _.chain(assetGroups)
        .map(function(assets) {
          return _.map(assets, function (asset) {
            return $('<label>', {
              'for': 'nav-' + asset.layerName,
              text: ' ' + asset.title
            }).prepend($('<input>', {
              type: 'radio',
              name: 'navigation-radio',
              id: 'nav-' + asset.layerName,
              value: asset.layerName
            }));
          }).concat($('<br>'));
        })
        .values()
        .flatten()
        .value();

    assetSelection.append(assetLinks).hide();

    startListening();

    function startListening() {
      assetSelection.on('click', 'input', onClick);
    }

    function stopListening() {
      assetSelection.off('click', 'input', onClick);
    }

    function onClick() {
      hide();
      onSelection($(this).val());
      return true;
    }

    function toggle() {
      assetSelection.toggle();
    }

    function hide() {
      assetSelection.hide();
    }

    function select(layer) {
      stopListening();
      assetSelection.find('input[value="' + layer + '"]').click();
      startListening();
    }

    return {
      toggle: toggle,
      hide: hide,
      element: assetSelection,
      select: select
    };
  };
})(this);
