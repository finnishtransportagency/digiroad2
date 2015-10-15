(function(root) {
  root.AssetSelectionMenu = function(container) {
    var assetSelection = $('<div class=asset-selection></div>');
    container.append(assetSelection.hide());

    function show() {
      assetSelection.show();
    }

    return {
      show: show
    };
  };
})(this);
