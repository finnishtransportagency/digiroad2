define(function() {
  var restartApplication = function(callback, fakeBackend) {
    eventbus.once('map:initialized', callback);
    ApplicationModel.assetDragDelay = 0;
    Application.restart(fakeBackend);
  };

  var fakeBackend = function(assetsData, assetData) {
    return Backend.withRoadLinkData(RoadLinkTestData.generate())
      .withUserRolesData(UserRolesTestData.generate())
      .withEnumeratedPropertyValues(EnumeratedPropertyValuesTestData.generate())
      .withApplicationSetupData(ApplicationSetupTestData.generate())
      .withConfigurationData(ConfigurationTestData.generate())
      .withAssetPropertyNamesData(AssetPropertyNamesTestData.generate())
      .withAssetsData(assetsData)
      .withAssetData(assetData);
  };

  var clickMarker = function(id, event) {
    var asset = AssetsModel.getAsset(id);
    if (asset) {
      asset.mouseDownHandler(event);
      asset.mouseUpHandler(event);
    }
  };

  var moveMarker = function(id, originX, originY, deltaX, deltaY) {
    var asset = AssetsModel.getAsset(id);
    if (asset) {
      var targetX = originX + deltaX;
      var targetY = originY + deltaY;
      var mouseDownEvent = {clientX: originX, clientY: originY};
      var mouseUpEvent = {clientX: targetX, clientY: targetY};
      asset.mouseDownHandler(mouseDownEvent);
      eventbus.trigger('map:mouseMoved', {clientX: targetX, clientY: targetY, xy: {x: targetX, y: targetY - 40}});
      asset.mouseUpHandler(mouseUpEvent);
    }
  };

  return {
    restartApplication: restartApplication,
    fakeBackend: fakeBackend,
    clickMarker: clickMarker,
    moveMarker: moveMarker
  };
});
