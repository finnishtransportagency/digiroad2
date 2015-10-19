(function (root) {
  root.AssetSelectionMenu = function(assetGroups) {
    var assetSelection = $('<div class=asset-selection></div>');

    var assetLinks =
      _.chain(assetGroups)
        .mapValues(function(assets) {
          return _.map(assets, function (asset) {
            return $('<a href="#' + asset.layerName + '">' + asset.title + '</a><br/>');
          }).concat($('<br/>'));
        })
        .values()
        .flatten()
        .value();

    assetSelection.append(assetLinks).hide();

    assetSelection.on('click', 'a', function () {
      assetSelection.hide();
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
