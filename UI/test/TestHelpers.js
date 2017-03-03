define(['AssetsTestData',
        'RoadLinkTestData',
        'UserRolesTestData',
        'EnumeratedPropertyValuesTestData',
        'AssetPropertyNamesTestData',
        'SpeedLimitsTestData',
        'SpeedLimitSplitTestData',
        'AssetTypePropertiesTestData'],
       function(AssetsTestData,
                RoadLinkTestData,
                UserRolesTestData,
                EnumeratedPropertyValuesTestData,
                AssetPropertyNamesTestData,
                SpeedLimitsTestData,
                SpeedLimitSplitTestData,
                AssetTypePropertiesTestData) {

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
    return fakeBackend(AssetsTestData.generate(), {});
  };

  var fakeBackend = function(assetsData, assetData, zoomLevel) {
    var speedLimitsTestData = SpeedLimitsTestData.generate(2);
    return new Backend().withRoadLinkData(RoadLinkTestData.generate())
      .withUserRolesData(UserRolesTestData.generate())
      .withEnumeratedPropertyValues(EnumeratedPropertyValuesTestData.generate())
      .withStartupParameters({ lon: 374750.0, lat: 6677409.0, zoom: zoomLevel || 10 })
      .withAssetPropertyNamesData(AssetPropertyNamesTestData.generate())
      .withAssetsData(assetsData)
      .withAssetData(assetData)
      .withSpeedLimitsData(speedLimitsTestData)
      .withSpeedLimitUpdate(speedLimitsTestData)
      .withPassThroughAssetCreation()
      .withAssetTypePropertiesData(AssetTypePropertiesTestData.generate());
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

  var clickMarker = function(id, map) {
      //TODO
      /*
    var markerBounds = _.find(getLayerByName(map, 'massTransitStop').markers, {id: id}).bounds;
    var markerPixelPosition = map.getPixelFromCoordinate([markerBounds.top, markerBounds.left]);
    var event = { clientX: markerPixelPosition.x, clientY: markerPixelPosition.y };
    var asset = massTransitStopsCollection.getAsset(id);
    if (asset) { asset.mouseClickHandler(event); }
    */
  };

  var moveMarker = function(id, map, deltaLon, deltaLat) {
    var asset = massTransitStopsCollection.getAsset(id);
    //TODO
      /*if (asset) {
      var originBounds = _.find(getLayerByName(map, 'massTransitStop').markers, {id: id}).bounds;
      var originLonLat = [originBounds.top, originBounds.left];
      var targetLonLat = [originLonLat.lon + deltaLon, originLonLat.lat + deltaLat];
      var originPixel = map.getPixelFromCoordinate(originLonLat);
      var targetPixel = map.getPixelFromCoordinate(targetLonLat);
      var mouseDownEvent = {clientX: originPixel.x, clientY: originPixel.y};
      var mouseUpEvent = {clientX: targetPixel.x, clientY: targetPixel.y};
      asset.mouseDownHandler(mouseDownEvent);
      eventbus.trigger('map:mouseMoved', {clientX: targetPixel.x, clientY: targetPixel.y, xy: {x: targetPixel.x, y: targetPixel.y - 40}});
      asset.mouseUpHandler(mouseUpEvent);
    }*/
  };

  var clickMap = function(map, longitude, latitude) {
    var pixel = map.getPixelFromCoordinate([longitude, latitude]);
    map.dispatchEvent('click',  {target: {}, srcElement: {}, xy: {x: pixel.x, y: pixel.y}});
  };

  var massSelect = function(map, longitude1, latitude1, longitude2, latitude2) {
    var topLeft = map.getPixelFromCoordinate([longitude1, latitude1]);
    var bottomRight = map.getPixelFromCoordinate([longitude2, latitude2]);
    var commonParameters = {target: {}, srcElement: {}, button: 1};
    var modifierKey = (navigator.platform.toLowerCase().indexOf('mac') === 0) ? { metaKey: true } : { ctrlKey: true };
    var mouseDownEvent = _.merge({}, commonParameters, modifierKey, { xy: {x: topLeft.x, y: topLeft.y} });
    var mouseMoveEvent = _.merge({}, commonParameters, modifierKey, { xy: {x: bottomRight.x, y: bottomRight.y} });
    var mouseUpEvent = _.merge({}, commonParameters, modifierKey, { xy: {x: bottomRight.x, y: bottomRight.y} });
    map.dispatchEvent('mousedown', mouseDownEvent);
    map.dispatchEvent('mousemove', mouseMoveEvent);
    map.dispatchEvent('mouseup', mouseUpEvent);
  };

  var getAssetMarkers = function(map) {
    return getLayerByName(map, 'massTransitStop').getSource().getFeatures();
  };

  var getSpeedLimitFeatures = function(map) {
    return getLayerByName(map, 'speedLimit').getSource().getFeatures();
  };

  var getSelectedSpeedLimitFeatures = function(map) {
    var interactions = _.find(map.getInteractions().getArray(), function(interaction) {
      return interaction.get('name') === 'speedLimit';
    });
    return interactions.getFeatures().getArray();
  };

 var getSpeedLimitLayer = function(map) {
   return getLayerByName(map, 'speedLimit');
 };

 var getLineStringFeatures = function(layer) {
   return _.filter(layer.getSource().getFeatures(), function(feature) {
    return feature.getGeometry() instanceof ol.geom.LineString;
   });
 };

 var getSpeedLimitVertices = function(openLayersMap, id) {
  return _.chain(getLineStringFeatures(getSpeedLimitLayer(openLayersMap)))
    .filter(function(feature) { return feature.getProperties().id === id; })
    .map(function(feature)  {
        return _.map(feature.getGeometry().getCoordinates(), function(coordinate){ return { x: coordinate[0], y: coordinate[1]}; });
    })
    .flatten()
    .value();
  };

 var selectSpeedLimit = function(map, speedLimitId, singleLinkSelect) {
   var interaction = _.find(map.getInteractions().getArray(), function(interaction) {
       return interaction.get('name') === 'speedLimit';
   });
   var feature = _.find(getSpeedLimitFeatures(map), function(feature) {
     return feature.getProperties().id === speedLimitId;
   });
   interaction.getFeatures().clear();
   interaction.getFeatures().push(feature);
   interaction.dispatchEvent({
     type: 'select',
     selected: [feature],
     deselected: []
   });
     //interaction.select(_.assign({singleLinkSelect: singleLinkSelect || false}, feature));
 };

 var clickElement = function(element) {
   var event = document.createEvent('MouseEvent');
   event.initMouseEvent('click', true, true, window, null, 0, 0, 0, 0, false, false, false, false, 0, null);
   element.dispatchEvent(event);
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
   clickMarker: clickMarker,
   moveMarker: moveMarker,
   clickMap: clickMap,
   massSelect: massSelect,
   getAssetMarkers: getAssetMarkers,
   getLineStringFeatures: getLineStringFeatures,
   getSelectedSpeedLimitFeatures: getSelectedSpeedLimitFeatures,
   getSpeedLimitFeatures: getSpeedLimitFeatures,
   getSpeedLimitVertices: getSpeedLimitVertices,
   selectSpeedLimit: selectSpeedLimit,
   clickElement: clickElement,
   selectLayer: selectLayer,
   getLayerByName: getLayerByName
 };
});
