(function(root) {
  root.NavigationPanel = {
    initialize: initialize
  };

  function initialize(container, searchBox, layerSelectBox, assetElements) {
    var navigationPanel = $('<div class="navigation-panel"></div>');

    navigationPanel.append(searchBox.element);
    navigationPanel.append(layerSelectBox.element);

    _.forEach(assetElements, function(asset) {
      navigationPanel.append(asset.element.hide());
    });

    var assetControls = _.chain(assetElements)
      .map(function(asset) {
        return [asset.layerName, asset.element];
      })
      .zipObject()
      .value();

    eventbus.on('layer:selected', function selectLayer(layer, previouslySelectedLayer) {
      var previousControl = assetControls[previouslySelectedLayer];
      if (previousControl) previousControl.hide();
      assetControls[layer].show();
    });

    container.append(navigationPanel);
  }
})(this);
