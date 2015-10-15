(function(root) {
  root.AssetSelectionMenu = function(container, assets) {
    var assetSelection = $('<div class=asset-selection></div>');

    var assetLinks = _.chain(assets)
      .pluck('title')
      .map(function(title) {
        return $('<span>' + title + '</span>');
      })
      .value();

    _.forEach(assetLinks, function(link) {
      assetSelection.append(link);
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
