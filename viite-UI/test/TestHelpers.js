define(['RoadAddressTestData',
        'RoadLinkTestData',
        'UserRolesTestData'],
       function(RoadAddressTestData,
                RoadLinkTestData,
                UserRolesTestData) {

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
    Application.restart(backend || defaultBackend(), false);
  };

  var defaultBackend = function() {
    return fakeBackend({});
  };

  var fakeBackend = function(zoomLevel) {
    return new Backend().withRoadLinkData(RoadLinkTestData.generate())
      .withUserRolesData(UserRolesTestData.generate())
      .withStartupParameters({ lon: 374750.0, lat: 6677409.0, zoom: zoomLevel || 10 });
  };

  var clickVisibleEditModeButton = function() {
    $('.edit-mode-btn:visible').click();
  };

  var clickVisbleYesConfirmPopup = function(){
    $('.btn.yes:visible').click();
  };

  var getLayerByName = function(map, name){
      var layers = map.getLayers().getArray();
      return _.find(layers, function(layer){
          return layer.get('name') === name;
      });
  };

  var clickMap = function(map, longitude, latitude) {
    map.dispatchEvent({ type: 'singleclick', coordinate: [longitude, latitude] });
  };

  var getLineStringFeatures = function(layer) {
   return _.filter(layer.getSource().getFeatures(), function(feature) {
    return feature.getGeometry() instanceof ol.geom.LineString;
   });
  };

 var selectLayer = function(layerName) {
   applicationModel.selectLayer(layerName);
 };

 var getPixelFromCoordinateAsync = function(map, coordinate, callback) {
   var pixel = map.getPixelFromCoordinate(coordinate);
   if (pixel) {
     window.setTimeout(function() { callback(pixel); }, 0);
   } else {
     map.once('postrender', function() {
       getPixelFromCoordinateAsync(map, coordinate, callback);
     });
   }
 };

 return {
   restartApplication: restartApplication,
   getPixelFromCoordinateAsync: getPixelFromCoordinateAsync,
   defaultBackend: defaultBackend,
   fakeBackend: fakeBackend,
   clickVisibleEditModeButton: clickVisibleEditModeButton,
   clickVisbleYesConfirmPopup: clickVisbleYesConfirmPopup,
   clickMap: clickMap,
   getLineStringFeatures: getLineStringFeatures,
   selectLayer: selectLayer,
   getLayerByName: getLayerByName
 };
});
