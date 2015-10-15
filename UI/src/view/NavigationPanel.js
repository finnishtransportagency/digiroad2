(function(root) {
  root.NavigationPanel = {
    initialize: initialize
  };

  function initialize(container, instructionsPopup, locationSearch, linearAssets, linkPropertiesModel, selectedSpeedLimit) {
    container.append('<div class="navigation-panel"></div>');

    var navigationPanel = $('.navigation-panel');

    navigationPanel.append(new SearchBox(instructionsPopup, locationSearch).element);

    var roadLinkBox = new RoadLinkBox(linkPropertiesModel);
    var massTransitBox = new ActionPanelBoxes.AssetBox();
    var speedLimitBox = new ActionPanelBoxes.SpeedLimitBox(selectedSpeedLimit);
    var manoeuvreBox = new ManoeuvreBox();

    var assetElements = _.map(linearAssets, function(asset) {
      var legendValues = [asset.editControlLabels.disabled, asset.editControlLabels.enabled];
      var assetBox = new LinearAssetBox(asset.selectedLinearAsset, asset.layerName, asset.title, asset.className, legendValues);
      return {
        layerName: asset.layerName,
        element: assetBox.element
      };
    }).concat([
      {layerName: 'linkProperty', element: roadLinkBox.element},
      {layerName: 'massTransitStop', element: massTransitBox.element},
      {layerName: 'speedLimit', element: speedLimitBox.element},
      {layerName: 'manoeuvre', element: manoeuvreBox.element},
    ]);

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
  }
})(this);
