define(function() {
  var unbindEvents = function() {
    eventbus.off();
    $(window).off();
  };

  var clearDom = function() {
    $('.container').html(
        '<div id="contentMap">' +
          '<div id="mapdiv"></div>' +
          '<div class="crossHair crossHairVertical"></div>' +
          '<div class="crossHair crossHairHorizontal"></div>' +
        '</div>' +
        '<nav id="map-tools"></nav>' +
        '<div id="feature-attributes"></div>'
    );
  };

  var clearAddressBar = function() {
    window.location.hash = '';
  };

  var restartApplication = function(callback, backend) {
    unbindEvents();
    clearDom();
    clearAddressBar();
    eventbus.once('map:initialized', function(map) {
      applicationModel.assetDragDelay = 0;
      callback(map);
    });
    Application.restart(backend || defaultBackend());
  };

  var defaultBackend = function() {
    return fakeBackend(AssetsTestData.generate(), {});
  };

  var fakeBackend = function(assetsData, assetData, zoomLevel) {
    return Backend.withRoadLinkData(RoadLinkTestData.generate())
      .withUserRolesData(UserRolesTestData.generate())
      .withEnumeratedPropertyValues(EnumeratedPropertyValuesTestData.generate())
      .withApplicationSetupData(ApplicationSetupTestData.generate())
      .withConfigurationData(ConfigurationTestData.generate(zoomLevel))
      .withAssetPropertyNamesData(AssetPropertyNamesTestData.generate())
      .withAssetsData(assetsData)
      .withAssetData(assetData)
      .withSpeedLimitsData(SpeedLimitsTestData.generate(2))
      .withPassThroughAssetCreation()
      .withAssetTypePropertiesData(AssetTypePropertiesTestData.generate());
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

  var getSpeedLimitFeatures = function(map) {
    return map.getLayersByName('speedLimit')[0].features;
  };

  return {
    restartApplication: restartApplication,
    defaultBackend: defaultBackend,
    fakeBackend: fakeBackend,
    clickMarker: clickMarker,
    moveMarker: moveMarker,
    getAssetMarkers: getAssetMarkers,
    getSpeedLimitFeatures: getSpeedLimitFeatures
  };
});
