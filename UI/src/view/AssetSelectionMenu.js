(function(root) {
  root.AssetSelectionMenu = function(container, assets) {
    var assetSelection = $('<div class=asset-selection></div>');

    var assetLinks = _.map(assets, function(asset) {
      return $('<a href="#' + asset.layerName + '">' + asset.title + '</a>');
    });

    assetSelection.append(assetLinks);

    assetSelection.on('click', 'a', function() {
      assetSelection.hide();
    });

    container.append(assetSelection.hide());

    function show() {
      assetSelection.show();
    }

    return {
      show: show
    };
  };
})(this);
