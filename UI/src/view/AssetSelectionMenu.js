(function (root) {
  root.AssetSelectionMenu = function(assetGroups) {
    var assetSelection = $('<div class=asset-selection></div>');

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

    assetSelection.on('click', 'input', function() {
      hide();
      window.location.hash = '#' + $(this).val();
    });

    function toggle() {
      assetSelection.toggle();
    }

    function hide() {
      assetSelection.hide();
    }

    return {
      toggle: toggle,
      hide: hide,
      element: assetSelection
    };
  };
})(this);
