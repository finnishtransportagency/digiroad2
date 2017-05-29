define(['RoadAddressTestData',
    'RoadLinkTestData',
    'UserRolesTestData',
    'RoadAddressProjectTestData'],
  function(RoadAddressTestData,
           RoadLinkTestData,
           UserRolesTestData,
           RoadAddressProjectTestData) {

    var getRoadLayerName = function() {
      return 'roadLayer';
    };
    var getFloatingMarkerLayerName = function() {
      return 'floatingMarkerLayer';
    };
    var getAnomalousMarkerLayerName = function() {
      return 'anomalousMarkerLayer';
    };
    var getCalibrationPointLayerName = function() {
      return 'calibrationPointLayer';
    };
    var getGreenRoadLayerName = function() {
      return 'greenRoadLayer';
    };
    var getPickRoadsLayerName = function(){
      return 'pickRoadsLayer';
    };
    var getSimulatedRoadsLayerName = function() {
      return 'simulatedRoadsLayer';
    };
    var getSingleClickName = function() {
      return 'selectSingleClickInteraction';
    };
    var getDoubleClickName = function() {
      return 'selectDoubleClickInteraction';
    };

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

    //TODO
    //The restartApplication command is not properly applying the selected backend to the context of the tests done in this FloatingRoadAddressSpec.
    //It was "quick-fixed" by changing the default backend function (bellow) in the this file to retrive the data I need (thus locking the backend in one mode).
    //I require some assistance with this issue.
    var defaultBackend = function() {
      return fakeBackend(13, selectTestData('roadAddress'),354810.0, 6676460.0);
    };

    var fakeBackend = function(zoomLevel, generatedData, latitude, longitude) {
      return new Backend().withRoadLinkData(generatedData, selectTestData('roadAddressAfterSave'))
        .withUserRolesData(UserRolesTestData.roles())
        .withStartupParameters({ lon: longitude, lat: latitude, zoom: zoomLevel || 10 })
        .withFloatingAdjacents(selectTestData('floatingRoadAddress'), selectTestData('unknownRoadAddress'))
        .withGetTransferResult(selectTestData('transferFloating'))
        .withRoadAddressProjectData(RoadAddressProjectTestData.generate())
        .withRoadAddressCreation();
    };

    var clickVisibleEditModeButton = function() {
      $('.edit-mode-btn:visible').click();
    };

    var clickVisbleYesConfirmPopup = function(){
      $('.btn.yes:visible').click();
    };

    var clickValintaButton = function(){
      $('.link-properties button.continue').click();
    };

    var clickMap = function(map, longitude, latitude) {
      map.dispatchEvent({ type: 'singleclick', coordinate: [longitude, latitude] });
    };

    var clickEnabledSirraButton = function(){
      $('.link-properties button.calculate:enabled').click();
    };

    var clickEnabledSaveButton = function(){
      $('.link-properties button.save:enabled').click();
    };

    var clickProjectListButton = function(){
      $('[id=projectListButton]').click();
    };

    var getLayerByName = function(map, name){
      var layers = map.getLayers().getArray();
      return _.find(layers, function(layer){
        return layer.get('name') === name;
      });
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

    var selectTestData = function(selection){
      switch (selection){
        case 'user':
          return UserRolesTestData.roles();
        case 'roadAddress':
          return RoadAddressTestData.generateRoadAddressLinks();
        case 'roadAddressAfterSave':
          return RoadAddressTestData.generateRoadAddressDataAfterSave();
        case 'floatingRoadAddress':
          return RoadAddressTestData.generateFloatingAdjacentData();
        case 'unknownRoadAddress':
          return RoadAddressTestData.generateUnknownAdjacentData();
        case 'transferFloating':
          return RoadAddressTestData.generateTransferFloatingData();
        case 'roadLink':
          return RoadLinkTestData.generate();
        case 'normalLinkData':
          return RoadAddressProjectTestData.generateNormalLinkData();
        case 'reservedProjectLinks':
          return RoadAddressProjectTestData.generateReservedProjectLinkData();
        case 'terminatedProjectLinks':
          return RoadAddressProjectTestData.generateTerminatedProjectLinkData();
      }
    };

    var getFeatures = function(map, layerName){
      var layer = getLayerByName(map, layerName);
      return layer.getSource().getFeatures();
    };

    var getFeaturesRoadLinkData = function(map, layerName){
      var features =  getFeatures(map, layerName);
      return _.chain(features).map(function(feature){
        return feature.roadLinkData;
      }).filter(function(rlData) {
        return !_.isUndefined(rlData);
      }).value();
    };

    var getFeatureByLinkId = function(map, layerName, linkId){
      var features = getFeatures(map, layerName);
      return _.find(features, function(feature){
        return feature.roadLinkData.linkId === linkId;
      });
    };

    var getRoadLinkDataByLinkId = function (map, layerName, linkId){
      var roadLinkDatas = getFeaturesRoadLinkData(map, layerName);
      return _.find(roadLinkDatas, function (roadLinkData){
        return roadLinkData.linkId === linkId;
      });
    };

    var selectSingleFeature = function(map, feature){
      var interaction = _.find(map.getInteractions().getArray(), function(interaction) {
        return interaction.get('name') === 'selectSingleClickInteraction';
      });
      interaction.getFeatures().clear();
      interaction.getFeatures().push(feature);
      interaction.dispatchEvent({
        type: 'select',
        selected: [feature],
        deselected: []
      });
    };

    return {
      getRoadLayerName: getRoadLayerName,
      getFloatingMarkerLayerName: getFloatingMarkerLayerName,
      getAnomalousMarkerLayerName: getAnomalousMarkerLayerName,
      getCalibrationPointLayerName: getCalibrationPointLayerName,
      getGreenRoadLayerName: getGreenRoadLayerName,
      getPickRoadsLayerName: getPickRoadsLayerName,
      getSimulatedRoadsLayerName: getSimulatedRoadsLayerName,
      getSingleClickName: getSingleClickName,
      getDoubleClickName: getDoubleClickName,
      restartApplication: restartApplication,
      getPixelFromCoordinateAsync: getPixelFromCoordinateAsync,
      defaultBackend: defaultBackend,
      fakeBackend: fakeBackend,
      clickValintaButton:clickValintaButton,
      clickEnabledSirraButton: clickEnabledSirraButton,
      clickVisibleEditModeButton: clickVisibleEditModeButton,
      clickProjectListButton: clickProjectListButton,
      clickVisbleYesConfirmPopup: clickVisbleYesConfirmPopup,
      clickEnabledSaveButton: clickEnabledSaveButton,
      clickMap: clickMap,
      getLineStringFeatures: getLineStringFeatures,
      selectLayer: selectLayer,
      getLayerByName: getLayerByName,
      selectTestData: selectTestData,
      getFeatures: getFeatures,
      getFeaturesRoadLinkData: getFeaturesRoadLinkData,
      getFeatureByLinkId: getFeatureByLinkId,
      getRoadLinkDataByLinkId: getRoadLinkDataByLinkId,
      selectSingleFeature: selectSingleFeature
    };
  });
