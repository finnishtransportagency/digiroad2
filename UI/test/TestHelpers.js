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
      .withAssetEnumeratedPropertyValues([], 300)
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

  var moveMassTransitStop = function(map, id, coordinate, doneCallback){
      getPixelFromCoordinateAsync(map, coordinate, function(pixel){
          eventbus.once('asset:fetched', function(asset){
              eventbus.once('asset:moved', function(position){
                  doneCallback(asset, position);
              });
              var interaction = _.find(map.getInteractions().getArray(), function(interaction) {
                  return interaction.get('name') === 'translate_massTransitStop';
              });
              var features = new ol.Collection(getSelectedAssetMarkers(map));
              interaction.dispatchEvent({type: 'translating', features: features, pixel: pixel, coordinate: coordinate });
              interaction.dispatchEvent({type: 'translateend', features: features, pixel: pixel, coordinate: coordinate });
          });
          selectMassTransitStop(map, id);
      });
  };

  var clickMap = function(map, longitude, latitude) {
    map.dispatchEvent({ type: 'singleclick', coordinate: [longitude, latitude] });
  };

  var selectMassTransitStop = function(map, massTranstiStopId) {
     var interaction = _.find(map.getInteractions().getArray(), function(interaction) {
         return interaction.get('name') === 'massTransitStop';
     });
     var feature = _.find(getMassTransitStopFeatures(map), function(feature) {
         return feature.getProperties().data.id === massTranstiStopId;
     });
     interaction.getFeatures().clear();
     interaction.getFeatures().push(feature);
     interaction.dispatchEvent({
         type: 'select',
         selected: [feature],
         deselected: []
     });
  };

   var getAssetMarker = function(map, id){
       return _.find(getAssetMarkers(map), function(properties){
           return properties.data.id == id;
       });
   };

  var getAssetMarkers = function(map) {
    return _.map(getLayerByName(map, 'massTransitStop').getSource().getFeatures(), function(feature){
        return feature.getProperties();
    });
  };

  var getSelectedAssetMarkers = function(map) {
      var interactions = _.find(map.getInteractions().getArray(), function(interaction) {
          return interaction.get('name') === 'massTransitStop';
      });
      return interactions.getFeatures().getArray();
  };

   var getMassTransitStopFeatures = function(map) {
       return getLayerByName(map, 'massTransitStop').getSource().getFeatures();
   };

  var getSpeedLimitFeatures = function(map) {
    return getLayerByName(map, 'speedLimit').getSource().getFeatures();
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

   var mapBrowserEvent =  singleLinkSelect ? { type: 'dblclick' } : { type: 'click' };

   interaction.getFeatures().clear();
   interaction.getFeatures().push(feature);
   interaction.dispatchEvent({
     type: 'select',
     selected: [feature],
     deselected: [],
     mapBrowserEvent : mapBrowserEvent
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

 var getSelectedSpeedLimitFeatures = function(map) {
   var interactions = _.find(map.getInteractions().getArray(), function(interaction) {
       return interaction.get('name') === 'speedLimit';
   });
   return interactions.getFeatures().getArray();
 };

 return {
   restartApplication: restartApplication,
   getPixelFromCoordinateAsync: getPixelFromCoordinateAsync,
   defaultBackend: defaultBackend,
   fakeBackend: fakeBackend,
   clickVisibleEditModeButton: clickVisibleEditModeButton,
   clickVisbleYesConfirmPopup: clickVisbleYesConfirmPopup,
   clickMap: clickMap,
   moveMassTransitStop: moveMassTransitStop,
   getAssetMarkers: getAssetMarkers,
   getAssetMarker: getAssetMarker,
   getSelectedAssetMarkers: getSelectedAssetMarkers,
   getLineStringFeatures: getLineStringFeatures,
   getSelectedSpeedLimitFeatures: getSelectedSpeedLimitFeatures,
   getSpeedLimitFeatures: getSpeedLimitFeatures,
   getSpeedLimitVertices: getSpeedLimitVertices,
   selectSpeedLimit: selectSpeedLimit,
   selectLayer: selectLayer,
   getLayerByName: getLayerByName,
   selectMassTransitStop: selectMassTransitStop,
   getMassTransitStopFeatures: getMassTransitStopFeatures
 };
});
