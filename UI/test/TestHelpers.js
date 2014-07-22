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

  var clickMarker = function(id, map) {
    var markerBounds = _.find(map.getLayersByName('asset')[0].markers, {id: id}).bounds;
    var markerPixelPosition = map.getPixelFromLonLat(new OpenLayers.LonLat(markerBounds.top, markerBounds.left));
    var event = { clientX: markerPixelPosition.x, clientY: markerPixelPosition.y };
    var asset = AssetsModel.getAsset(id);
    if (asset) { asset.mouseClickHandler(event); }
  };

  var moveMarker = function(id, map, deltaLon, deltaLat) {
    var asset = AssetsModel.getAsset(id);
    if (asset) {
      var originBounds = _.find(map.getLayersByName('asset')[0].markers, {id: id}).bounds;
      var originLonLat = new OpenLayers.LonLat(originBounds.top, originBounds.left);
      var targetLonLat = new OpenLayers.LonLat(originLonLat.lon + deltaLon, originLonLat.lat + deltaLat);
      var originPixel = map.getPixelFromLonLat(originLonLat);
      var targetPixel = map.getPixelFromLonLat(targetLonLat);
      var mouseDownEvent = {clientX: originPixel.x, clientY: originPixel.y};
      var mouseUpEvent = {clientX: targetPixel.x, clientY: targetPixel.y};
      asset.mouseDownHandler(mouseDownEvent);
      eventbus.trigger('map:mouseMoved', {clientX: targetPixel.x, clientY: targetPixel.y, xy: {x: targetPixel.x, y: targetPixel.y - 40}});
      asset.mouseUpHandler(mouseUpEvent);
    }
  };

  var getAssetMarkers = function(map) {
    return map.getLayersByName('asset')[0].markers;
  };

  return {
    restartApplication: restartApplication,
    fakeBackend: fakeBackend,
    clickMarker: clickMarker,
    moveMarker: moveMarker,
    getAssetMarkers: getAssetMarkers
  };
});
