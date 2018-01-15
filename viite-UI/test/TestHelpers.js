define(['RoadAddressTestData', 'RoadLinkTestData', 'UserRolesTestData', 'RoadAddressProjectTestData', 'SplittingTestData'],

  function(RoadAddressTestData,
           RoadLinkTestData,
           UserRolesTestData,
           RoadAddressProjectTestData,
           SplittingTestData) {

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
    var getRoadAddressProjectLayerName = function() {
      return 'roadAddressProject';
    };
    var getSingleClickNameLinkPropertyLayer = function() {
      return 'selectSingleClickInteractionLPL';
    };
    var getDoubleClickNameLinkPropertyLayer = function() {
      return 'selectDoubleClickInteractionLPL';
    };
    var getSingleClickNameProjectLinkLayer = function() {
      return 'selectSingleClickInteractionPLL';
    };
    var getDoubleClickNameProjectLinkLayer = function() {
      return 'selectDoubleClickInteractionPLL';
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
      eventbus.on('map:initialized', function(map) {
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
      return fakeBackend(13, selectTestData('roadAddress'),354810.0, 6676460.0, 'Project Two');
    };

    var fakeBackend = function(zoomLevel, generatedData, latitude, longitude, projectDefinition) {
      var data = getSimulatedData(projectDefinition);
      return new Backend().withRoadLinkData(generatedData, selectTestData('roadAddressAfterSave'))
        .withUserRolesData(UserRolesTestData.roles())
        .withStartupParameters({ lon: longitude, lat: latitude, zoom: zoomLevel || 10, deploy_date: "" })
        .withFloatingAdjacents(data.floatingAdjacents)
        .withGetTargetAdjacent(data.targetAdjacent)
        .withGetTransferResult(data.transferResult)
        .withRoadAddressProjectData(data.projectData)
        .withRoadPartReserved(data.partReserved)
        .withProjectLinks(data.projectLinks)
        .withGetProjectsWithLinksById(data.projectsWithLinks)
        .withRoadAddressProjects(data.projects)
        .withGetRoadLinkByLinkId(data.roadLinkById)
        .withCreateRoadAddressProject(data.createRoadAddressProject)
        .withPreSplitData(data.splitData)
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

    var clickEnabledSiirraButton = function(){
      $('.link-properties button.calculate:enabled').click();
    };

    var clickEnabledSaveButton = function(){
      $('.link-properties button.save:enabled').click();
    };

    var clickProjectListButton = function(){
      $('[id=projectListButton]').click();
    };

    var clickNextButton = function(){
      $('#generalNext').click();
    };

    var clickReserveButton = function(){
      $('.btn-reserve').click();
    };

    var clickOpenProjectButton = function(){
      $('[id*="open-project"]').click();
    };

    var clickNewProjectButton = function(){
      $('button.new').click();
    };

    var clickCancelButton = function () {
      $('.split-form button.cancelLink').click();
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

    var getSimulatedData = function (projectDefinition) {
      switch (projectDefinition) {
        case 'Project Two':
          return {
            floatingAdjacents: selectTestData('floatingRoadAddress'),
            targetAdjacent: selectTestData('unknownRoadAddress'),
            transferResult: selectTestData('transferFloating'),
            projectData: RoadAddressProjectTestData.generate(),
            partReserved: RoadAddressProjectTestData.generateRoadPartChecker(),
            projectLinks: RoadAddressProjectTestData.generateProjectLinks(),
            projectsWithLinks: RoadAddressProjectTestData.generateProjectLinksByProjectId(),
            projects: RoadAddressProjectTestData.generateProject(),
            roadLinkById: RoadAddressProjectTestData.generateRoadLinkByLinkId(),
            createRoadAddressProject: RoadAddressProjectTestData.generateCreateRoadAddressProject(),
            splitData: {}
          };

        case 'projectThree': //Suravage project
          return {
            floatingAdjacents: {},
            targetAdjacent: {},
            transferResult: {},
            projectData: {},
            roadLinkById: {},
            projectsWithLinks: SplittingTestData.generateProjectData(),
            partReserved: SplittingTestData.generateReservedPart(),
            projectLinks: SplittingTestData.generateProjectRoadLinks(),
            projects: SplittingTestData.generateProjectData(),
            createRoadAddressProject: SplittingTestData.generateProject(),
            splitData: SplittingTestData.generatePreSplitData()
          };
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
          return RoadAddressProjectTestData.generateProjectLinkData();
        case 'terminatedProjectLinks':
          return RoadAddressProjectTestData.generateTerminatedProjectLinkData();
        case 'suravageProjectLinks':
          return SplittingTestData.generateProjectRoadLinks();
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
        return (layerName == "roadAddressProject" ? feature.projectLinkData.linkId === linkId : feature.roadLinkData.linkId === linkId);
      });
    };

    var getRoadLinkDataByLinkId = function (map, layerName, linkId){
      var roadLinkDatas = getFeaturesRoadLinkData(map, layerName);
      return _.find(roadLinkDatas, function (roadLinkData){
        return roadLinkData.linkId === linkId;
      });
    };

    var selectSingleFeatureByInteraction = function(map, feature, interactionName){
      var interaction = _.find(map.getInteractions().getArray(), function(interaction) {
        return interaction.get('name') === interactionName;
      });
      interaction.getFeatures().clear();
      interaction.getFeatures().push(feature);
      interaction.dispatchEvent({
        type: 'select',
        selected: [feature],
        deselected: []
      });
    };

    var selectTool = function(tool) {
      applicationModel.setSelectedTool(tool);
    };

    return {
      getRoadLayerName: getRoadLayerName,
      getFloatingMarkerLayerName: getFloatingMarkerLayerName,
      getAnomalousMarkerLayerName: getAnomalousMarkerLayerName,
      getCalibrationPointLayerName: getCalibrationPointLayerName,
      getGreenRoadLayerName: getGreenRoadLayerName,
      getPickRoadsLayerName: getPickRoadsLayerName,
      getRoadAddressProjectLayerName: getRoadAddressProjectLayerName,
      getSimulatedRoadsLayerName: getSimulatedRoadsLayerName,
      getSingleClickNameLinkPropertyLayer: getSingleClickNameLinkPropertyLayer,
      getDoubleClickNameLinkPropertyLayer: getDoubleClickNameLinkPropertyLayer,
      getSingleClickNameProjectLinkLayer: getSingleClickNameProjectLinkLayer,
      getDoubleClickNameProjectLinkLayer: getDoubleClickNameProjectLinkLayer,
      getDoubleClickName: getDoubleClickName,
      restartApplication: restartApplication,
      getPixelFromCoordinateAsync: getPixelFromCoordinateAsync,
      defaultBackend: defaultBackend,
      fakeBackend: fakeBackend,
      clickValintaButton:clickValintaButton,
      clickEnabledSiirraButton: clickEnabledSiirraButton,
      clickVisibleEditModeButton: clickVisibleEditModeButton,
      clickProjectListButton: clickProjectListButton,
      clickNextButton: clickNextButton,
      clickReserveButton: clickReserveButton,
      clickOpenProjectButton: clickOpenProjectButton,
      clickNewProjectButton: clickNewProjectButton,
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
      selectSingleFeatureByInteraction: selectSingleFeatureByInteraction,
      selectTool: selectTool,
      clickCancelButton: clickCancelButton
    };
  });
