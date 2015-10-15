(function(root) {
  root.AssetSelectionMenu = function(container, assets) {
    var assetSelection = $('<div class=asset-selection></div>');

    var assetLinks = _.map(assets, function(asset) {
      return $('<a href="#' + asset.layerName + '">' + asset.title + '</a>');
    })

    assetSelection.append(assetLinks);

    container.append(assetSelection.hide());

    function show() {
      assetSelection.show();
    }

    return {
      show: show
    };
  };
})(this);
