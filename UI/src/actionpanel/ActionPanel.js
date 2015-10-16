(function(root) {
  root.ActionPanel = {
    initialize: function(backend, instructionsPopup, selectedSpeedLimit, linearAssets, linkPropertiesModel, locationSearch) {
      var panelControl = ['<div class="action-panels"></div>'].join('');

      $('#map-tools').append(panelControl);

      var searchBox = new SearchBox(instructionsPopup, locationSearch);
      $('.action-panels').append(searchBox.element);

      var roadLinkBox = new RoadLinkBox(linkPropertiesModel);
      $('.action-panels').append(roadLinkBox.element);

      var assetBox = new ActionPanelBoxes.AssetBox();
      $('.action-panels').append(assetBox.element);

      var speedLimitBox = new ActionPanelBoxes.SpeedLimitBox(selectedSpeedLimit);
      $('.action-panels').append(speedLimitBox.element);

      var manoeuvreBox = new ManoeuvreBox();
      $('.action-panels').append(manoeuvreBox.element);

      _.forEach(linearAssets, function(linearAsset) {
        var legendValues = [linearAsset.editControlLabels.disabled, linearAsset.editControlLabels.enabled];
        var linearAssetBox = new LinearAssetBox(linearAsset.selectedLinearAsset, linearAsset.layerName, linearAsset.title, linearAsset.className, legendValues);
        $('.action-panels').append(linearAssetBox.element);
      });

      backend.getUserRoles();
    }
  };
}(this));
