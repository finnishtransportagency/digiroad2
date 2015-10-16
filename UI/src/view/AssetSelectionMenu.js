(function(root) {
  root.AssetSelectionMenu = function(assets) {
    var assetSelection = $('<div class=asset-selection></div>');

    var assetLinks = _.map(assets, function(asset) {
      return $('<a href="#' + asset.layerName + '">' + asset.title + '</a><br/>');
    });

    assetSelection.append(assetLinks).hide();

    assetSelection.on('click', 'a', function() {
      assetSelection.hide();
    });


    function show() {
      assetSelection.show();
    }

    return {
      show: show,
      element: assetSelection
    };
  };
})(this);
