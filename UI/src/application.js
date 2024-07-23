(function(application) {
  application.start = function(customBackend, withTileMaps, isExperimental, clusterDistance) {
    var assetTypeLayerName = 'massTransitStop';
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ?  true : withTileMaps;
    var roadCollection = new RoadCollection(backend);
    var verificationCollection = new AssetsVerificationCollection(backend);
    var speedLimitsCollection = new SpeedLimitsCollection(backend, verificationCollection);
    var selectedSpeedLimit = new SelectedSpeedLimit(backend, speedLimitsCollection);
    var selectedLinkProperty = new SelectedLinkProperty(backend, roadCollection);
    var linkPropertiesModel = new LinkPropertiesModel();
    var userNotificationCollection = new UserNotificationCollection(backend);
    var manoeuvresCollection = new ManoeuvresCollection(backend, roadCollection, verificationCollection);
    var selectedManoeuvreSource = new SelectedManoeuvreSource(manoeuvresCollection);
    var instructionsPopup = new InstructionsPopup($('.digiroad2'));
    var assetConfiguration = new AssetTypeConfiguration();
    var enabledExperimentalAssets = isExperimental ? assetConfiguration.experimentalAssetsConfig : [];
    var enabledLinearAssetSpecs = assetConfiguration.linearAssetsConfig.concat(enabledExperimentalAssets);
    var authorizationPolicy = new AuthorizationPolicy();
    var municipalitySituationCollection = new MunicipalitySituationCollection(backend);

    var feedbackCollection = new FeedbackModel(backend, assetConfiguration);
    new FeedbackApplicationTool(authorizationPolicy, feedbackCollection);

    var linearAssets = _.map(enabledLinearAssetSpecs, function(spec) {
      var collection = _.isUndefined(spec.collection ) ?  new LinearAssetsCollection(backend, verificationCollection, spec) : new spec.collection(backend, verificationCollection, spec);
      var selectedLinearAsset = _.isUndefined(spec.selected) ? SelectedLinearAssetFactory.construct(backend, collection, spec) : new spec.selected(backend, collection, spec.typeId, spec.singleElementEventCategory, spec.multiElementEventCategory, spec.isSeparable);
      var authorizationPolicy = _.isUndefined(spec.authorizationPolicy) ? new AuthorizationPolicy() : spec.authorizationPolicy;
      return _.merge({}, spec, {
        collection: collection,
        selectedLinearAsset: selectedLinearAsset,
        authorizationPolicy: authorizationPolicy
      });
    });

    var pointAssets = _.map(assetConfiguration.pointAssetsConfig, function(spec) {
      var rCollection = spec.roadCollection ? new spec.roadCollection(backend) :  roadCollection;
      var collection = _.isUndefined(spec.collection ) ?  new PointAssetsCollection(backend, spec, verificationCollection) : new spec.collection(backend, spec, verificationCollection) ;
      var selectedPointAsset = new SelectedPointAsset(backend, spec.layerName,  rCollection);
      var authorizationPolicy = _.isUndefined(spec.authorizationPolicy) ? new AuthorizationPolicy() : spec.authorizationPolicy;
      return _.merge({}, spec, {
        collection: collection,
        selectedPointAsset: selectedPointAsset,
        authorizationPolicy: authorizationPolicy,
        roadCollection: rCollection
      });
    });

    var selectedMassTransitStopModel = SelectedMassTransitStop.initialize(backend, roadCollection);
    var models = {
      roadCollection: roadCollection,
      speedLimitsCollection: speedLimitsCollection,
      selectedSpeedLimit: selectedSpeedLimit,
      selectedLinkProperty: selectedLinkProperty,
      selectedManoeuvreSource: selectedManoeuvreSource,
      selectedMassTransitStopModel: selectedMassTransitStopModel,
      linkPropertiesModel: linkPropertiesModel,
      manoeuvresCollection: manoeuvresCollection,
      municipalitySituationCollection: municipalitySituationCollection,
      userNotificationCollection: userNotificationCollection
    };

    bindEvents(enabledLinearAssetSpecs, assetConfiguration.pointAssetsConfig);
    window.massTransitStopsCollection = new MassTransitStopsCollection(backend, verificationCollection);
    window.selectedMassTransitStopModel = selectedMassTransitStopModel;
    var selectedLinearAssetModels = _.map(linearAssets, "selectedLinearAsset");
    var selectedPointAssetModels = _.map(pointAssets, "selectedPointAsset");
    window.applicationModel = new ApplicationModel([
      selectedMassTransitStopModel,
      selectedSpeedLimit,
      selectedLinkProperty,
      selectedManoeuvreSource]
        .concat(selectedLinearAssetModels)
        .concat(selectedPointAssetModels));

    EditModeDisclaimer.initialize(instructionsPopup);

    var linearAssetGroup = groupLinearAssets(assetConfiguration,
        linearAssets,
        linkPropertiesModel,
        selectedSpeedLimit,
        selectedMassTransitStopModel,
        isExperimental);

    var pointAssetGroup = groupPointAssets(assetConfiguration,
        pointAssets,
        linkPropertiesModel,
        selectedSpeedLimit,
        selectedMassTransitStopModel,
        isExperimental);

    var serviceRoadAsset = [new ServiceRoadBox(_.find(linearAssets, {typeId: assetConfiguration.assetTypes.maintenanceRoad}))];

    var assetSelectionMenu = AssetSelectionMenu(linearAssetGroup, pointAssetGroup, serviceRoadAsset, {
      onSelect: function(layerName) {
        window.location.hash = layerName;
      }
    });

    eventbus.on('layer:selected', function(layer) {
      assetSelectionMenu.select(layer);
    });

    NavigationPanel.initialize(
        $('#map-tools'),
        new SearchBox(
            instructionsPopup,
            new LocationSearch(backend, window.applicationModel)
        ),
        new LayerSelectBox(assetSelectionMenu),
        linearAssetGroup.concat(pointAssetGroup).concat(serviceRoadAsset)
    );

    RoadAddressInfoDataInitializer.initialize(isExperimental);
    MassTransitStopForm.initialize(backend, new FeedbackModel(backend, assetConfiguration, selectedMassTransitStopModel));
    SpeedLimitForm.initialize(selectedSpeedLimit, new FeedbackModel(backend, assetConfiguration, selectedSpeedLimit));

    new WorkListView().initialize(backend);
    new VerificationWorkList().initialize();
    new AutoGeneratedAssetsWorkList().initialize(backend);
    new MunicipalityWorkList().initialize(backend);
    new SuggestedAssetsWorkList().initialize(backend);
    new SpeedLimitWorkList().initialize();
    new InaccurateWorkList().initialize();
    new ManoeuvreSamuutusWorkList().initialize(backend);
    new LaneWorkList().initialize(backend);
    new AutoProcessedLanesWorkList().initialize(backend);
    new RoadLinkReplacementWorkList().initialize(backend);
    new AssetsOnExpiredLinksWorkList().initialize(backend);
    new PrivateRoadsWorkList().initialize(backend);
    new CsvReportsWorkList().initialize(backend);
    new UserNotificationPopup(models.userNotificationCollection).initialize();
    new MunicipalitySituationPopup(models.municipalitySituationCollection).initialize();

    backend.getStartupParametersWithCallback(function(startupParameters) {
      backend.getAssetPropertyNamesWithCallback(function(assetPropertyNames) {
        startupAssetTypeId = startupParameters.startupAsseId;
        defaultLocation = {lon: startupParameters.lon, lat: startupParameters.lat, zoom: startupParameters.zoom, startupAsseId: startupAssetTypeId};

        if(_.isUndefined(startupAssetTypeId) || startupAssetTypeId === 0){
          assetTypeLayerName = "linkProperty";
        }
        else{
          assetTypeLayerName = (getSelectedAssetByTypeId(pointAssets, startupAssetTypeId) || getSelectedAssetByTypeId(linearAssets, startupAssetTypeId) || getSelectedAssetByTypeId(assetConfiguration.assetTypeInfo, startupAssetTypeId)).layerName;
        }

        localizedStrings = assetPropertyNames;
        window.localizedStrings = assetPropertyNames;
        startApplication(backend, models, linearAssets, pointAssets, tileMaps, startupParameters, roadCollection, verificationCollection, assetConfiguration, isExperimental, clusterDistance);
        window.applicationModel.selectLayer(assetTypeLayerName);
      });
    });
  };

  var startApplication = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, assetConfiguration, isExperimental, clusterDistance) {
    if (localizedStrings) {
      setupProjections();
      var map = setupMap(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, assetConfiguration, isExperimental, clusterDistance);
      var selectedPedestrianCrossing = getSelectedPointAsset(pointAssets, 'pedestrianCrossings');
      var selectedServicePoint = getSelectedPointAsset(pointAssets, 'servicePoints');
      var selectedTrafficLight = getSelectedPointAsset(pointAssets, 'trafficLights');
      var selectedObstacle = getSelectedPointAsset(pointAssets, 'obstacles');
      var selectedRailwayCrossing =  getSelectedPointAsset(pointAssets, 'railwayCrossings');
      var selectedDirectionalTrafficSign = getSelectedPointAsset(pointAssets, 'directionalTrafficSigns');
      var selectedTrafficSign = getSelectedPointAsset(pointAssets, 'trafficSigns');
      var selectedMaintenanceRoad = getSelectedLinearAsset(linearAssets, 'maintenanceRoad');
      new URLRouter(map, backend, _.merge({}, models,
          { selectedPedestrianCrossing: selectedPedestrianCrossing },
          { selectedTrafficLight: selectedTrafficLight },
          { selectedObstacle: selectedObstacle },
          { selectedRailwayCrossing: selectedRailwayCrossing },
          { selectedDirectionalTrafficSign: selectedDirectionalTrafficSign },
          { selectedServicePoint: selectedServicePoint },
          { selectedTrafficSign: selectedTrafficSign},
          { selectedMaintenanceRoad: selectedMaintenanceRoad},
          { linearAssets: linearAssets},
          { pointAssets: pointAssets}
    ));
      eventbus.trigger('application:initialized');
    }
  };

  var localizedStrings;

  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';
  var vkmNotFoundMessage = 'Sovellus ei pysty tunnistamaan annetulle pysäkin sijainnille tieosoitetta. Pysäkin tallennus OTH:ssa epäonnistui';
  var verificationFailedMessage = 'Tarkistus epäonnistui. Yritä hetken kuluttua uudestaan.';

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var indicatorOverlayForWorklist= function() {
    jQuery('#work-list').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var bindEvents = function(linearAssetSpecs, pointAssetSpecs, roadCollection) {
    var singleElementEventNames = _.map(linearAssetSpecs, 'singleElementEventCategory');
    var multiElementEventNames = _.map(linearAssetSpecs, 'multiElementEventCategory');
    var linearAssetSavingEvents = _.map(singleElementEventNames, function(name) { return name + ':saving'; }).join(' ');
    var pointAssetSavingEvents = _.map(pointAssetSpecs, function (spec) { return spec.layerName + ':saving'; }).join(' ');
    eventbus.on('asset:saving asset:creating speedLimit:saving linkProperties:saving manoeuvres:saving ' + linearAssetSavingEvents + ' ' + pointAssetSavingEvents, function() {
      indicatorOverlay();
    });

    eventbus.on('municipality:verifying', function() {
      indicatorOverlayForWorklist();
    });

    var fetchedEventNames = _.map(multiElementEventNames, function(name) { return name + ':fetched'; }).join(' ');
    eventbus.on('asset:saved asset:fetched asset:created speedLimits:fetched linkProperties:available manoeuvres:fetched pointAssets:fetched userNotification:fetched municipality:verified dashBoardInfoAssets:fetched' + fetchedEventNames, function() {
      jQuery('.spinner-overlay').remove();
    });

    var massUpdateFailedEventNames = _.map(multiElementEventNames, function(name) { return name + ':massUpdateFailed'; }).join(' ');
    eventbus.on('asset:updateFailed asset:creationFailed linkProperties:updateFailed speedLimits:massUpdateFailed municipality:verificationFailed ' + massUpdateFailedEventNames, function() {
      jQuery('.spinner-overlay').remove();
      alert(assetUpdateFailedMessage);
    });

    eventbus.on('asset:creationNotFoundRoadAddressVKM asset:updateNotFoundRoadAddressVKM', function() {
      jQuery('.spinner-overlay').remove();
      alert(vkmNotFoundMessage);
    });

    eventbus.on('asset:verificationFailed', function() {
      jQuery('.spinner-overlay').remove();
      alert(verificationFailedMessage);
    });

    eventbus.on('confirm:show', function() { new Confirm(); });
  };

  var createOpenLayersMap = function(startupParameters, layers) {
    var map = new ol.Map({
      target: 'mapdiv',
      layers: layers,
      view: new ol.View({
        center: [startupParameters.lon, startupParameters.lat],
        projection: 'EPSG:3067',
        zoom: startupParameters.zoom,
        resolutions: [2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25, 0.125, 0.0625]
      }),
      interactions: ol.interaction.defaults({
        mouseWheelZoom: false
      }),
    });
    map.setProperties({extent : [-548576, 6291456, 1548576, 8388608]});
    map.addInteraction(new ol.interaction.DragPan({
      condition: function (mapBrowserEvent) {
        var originalEvent = mapBrowserEvent.originalEvent;
        return (!originalEvent.altKey && !originalEvent.shiftKey);
      }
    }));

    map.addInteraction(new ol.interaction.MouseWheelZoom({
      condition: function(event) {
        var deltaY = event.originalEvent.deltaY;
        if (deltaY > 0) {
          // Wheel scrolled down (Zoom out)
          return applicationModel.handleZoomOut(map);
        } else if (deltaY < 0) {
          // Wheel scrolled up (Zoom in)
          return applicationModel.handleZoomIn(map);
        } else {
          // no zoom -> no reason to block
          return true;
        }
      }
    }));
    return map;
  };

  var setupMap = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, assetConfiguration, isExperimental, clusterDistance) {
    var tileMaps = new TileMapCollection(map, "");

    var map = createOpenLayersMap(startupParameters, tileMaps.layers);

    var mapOverlay = new MapOverlay($('.container'));

    var mapPluginsContainer = $('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    var roadAddressInfoPopup = new RoadAddressInfoPopup(map, mapPluginsContainer, roadCollection);
    new CoordinatesDisplay(map, mapPluginsContainer);
    new MunicipalityDisplay(map, mapPluginsContainer, backend);
    new DefaultLocationButton(map, mapPluginsContainer, backend, assetConfiguration, getAssetTitle(), defaultLocation);
    new LoadingBarDisplay(map, mapPluginsContainer);

    function getAssetTitle() {
      if (!_.isUndefined(startupAssetTypeId) && startupAssetTypeId !== 0) {
        return _.find(assetConfiguration.assetTypeInfo, function (assetInfo) {
          return assetInfo.typeId === startupAssetTypeId;
        }).title;
      }
      else {
        return 'Tielinkki';
      }
    }


    if (withTileMaps) { new TileMapCollection(map); }
    var roadLayer = new RoadLayer(map, models.roadCollection);

    new LinkPropertyForm(models.selectedLinkProperty, new FeedbackModel(backend, assetConfiguration, models.selectedLinkProperty));
    new ManoeuvreForm(models.selectedManoeuvreSource, new FeedbackModel(backend, assetConfiguration, models.selectedManoeuvreSource));
    _.forEach(linearAssets, function(linearAsset) {
      if(linearAsset.form)
        linearAsset.form.initialize(linearAsset, new FeedbackModel(backend, assetConfiguration, linearAsset.selectedLinearAsset));
      else
        LinearAssetForm.initialize(
            linearAsset,
            AssetFormElementsFactory.construct(linearAsset),
            new FeedbackModel(backend, assetConfiguration, linearAsset.selectedLinearAsset)
        );
    });

    _.forEach(pointAssets, function(pointAsset ) {
      var parameters = {
          pointAsset: pointAsset,
          roadCollection: pointAsset.roadCollection,
          applicationModel: applicationModel,
          backend: backend,
          saveCondition: pointAsset.saveCondition || function() {return true;},
          feedbackCollection : new FeedbackModel(backend, assetConfiguration, pointAsset.selectedPointAsset),
          collection: pointAsset.collection
      };

      if(pointAsset.form) {
        new pointAsset.form().initialize(parameters);
      }else
        new PointAssetForm().initialize(parameters);
    });


    var linearAssetLayers = _.reduce(linearAssets, function(acc, asset) {
      var parameters = {
       map: map,
       application: applicationModel,
       collection: asset.collection,
       selectedLinearAsset: asset.selectedLinearAsset,
       roadCollection: models.roadCollection,
       roadLayer: roadLayer,
       layerName: asset.layerName,
       multiElementEventCategory: asset.multiElementEventCategory,
       singleElementEventCategory: asset.singleElementEventCategory,
       style: asset.style || new PiecewiseLinearAssetStyle(),
       formElements: asset.form ?  asset.form : AssetFormElementsFactory.construct(asset),
       assetLabel: asset.label,
       roadAddressInfoPopup: roadAddressInfoPopup,
       authorizationPolicy: asset.authorizationPolicy,
       readOnlyLayer: asset.readOnlyLayer ? new asset.readOnlyLayer({ layerName: asset.layerName, map: map, backend: backend }): false,
       laneReadOnlyLayer: asset.laneReadOnlyLayer,
       massLimitation: asset.editControlLabels.additionalInfo,
       typeId: asset.typeId,
       isMultipleLinkSelectionAllowed: asset.isMultipleLinkSelectionAllowed,
       minZoomForContent: asset.minZoomForContent,
       isExperimental: isExperimental
      };
      acc[asset.layerName] = asset.layer ? new asset.layer(parameters) : new LinearAssetLayer(parameters);
      return acc;

    }, {});

    var pointAssetLayers = _.reduce(pointAssets, function(acc, asset) {
      var parameters = {
        roadLayer: roadLayer,
        application: applicationModel,
        roadCollection: asset.roadCollection,
        collection: asset.collection,
        map: map,
        selectedAsset: asset.selectedPointAsset,
        style: PointAssetStyle(asset.layerName),
        mapOverlay: mapOverlay,
        layerName: asset.layerName,
        assetLabel: asset.label,
        newAsset: asset.newAsset,
        roadAddressInfoPopup: roadAddressInfoPopup,
        allowGrouping: asset.allowGrouping,
        assetGrouping: new AssetGrouping(asset.groupingDistance),
        authorizationPolicy: asset.authorizationPolicy,
        showRoadLinkInfo: asset.showRoadLinkInfo,
        readOnlyLayer: asset.readOnlyLayer ? new asset.readOnlyLayer({ layerName: asset.layerName, map: map, backend: backend }): false
      };

     acc[asset.layerName] = asset.layer ? new asset.layer(parameters) : new PointAssetLayer(parameters);
     return acc;

    }, {});

    var layers = _.merge({
      road: roadLayer,
      linkProperty: new LinkPropertyLayer(map, roadLayer, models.selectedLinkProperty, models.roadCollection, models.linkPropertiesModel, applicationModel, roadAddressInfoPopup, isExperimental),
       massTransitStop: new MassTransitStopLayer(map, models.roadCollection, mapOverlay, new AssetGrouping(36), roadLayer, roadAddressInfoPopup, isExperimental, clusterDistance),
       speedLimit: new SpeedLimitLayer({
       map: map,
       application: applicationModel,
       collection: models.speedLimitsCollection,
       selectedSpeedLimit: models.selectedSpeedLimit,
       readOnlyLayer: new TrafficSignReadOnlyLayer({ layerName: 'speedLimit', map: map, backend: backend }),
       style: SpeedLimitStyle(applicationModel),
       roadLayer: roadLayer,
       roadAddressInfoPopup: roadAddressInfoPopup,
       isExperimental: isExperimental
       }),
       manoeuvre: new ManoeuvreLayer(applicationModel, map, roadLayer, models.selectedManoeuvreSource, models.manoeuvresCollection, models.roadCollection,  new TrafficSignReadOnlyLayer({ layerName: 'manoeuvre', map: map, backend: backend }),  new LinearSuggestionLabel() )

    }, linearAssetLayers, pointAssetLayers);

    // Show environment name next to Digiroad logo
    $('#notification').append(Environment.localizedName());

    new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

    applicationModel.moveMap(zoomlevels.getViewZoom(map), map.getLayers().getArray()[0].getExtent());
    backend.getUserRoles();
    return map;
  };

  var setupProjections = function() {
    proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs');
    ol.proj.proj4.register(proj4);
  };

  function getSelectedAssetByTypeId(assets, assetTypeId) {
    return _.find(assets, function (asset) {
      return asset.typeId === assetTypeId;
    });
  }

  function getSelectedPointAsset(pointAssets, layerName) {
    return _(pointAssets).find({ layerName: layerName }).selectedPointAsset;
  }

  function getSelectedLinearAsset(linearAssets, layerName) {
    return _(linearAssets).find({ layerName: layerName }).selectedLinearAsset;
  }


  function groupLinearAssets(assetConfiguration,
                       linearAssets,
                       linkPropertiesModel,
                       selectedSpeedLimit,
                       selectedMassTransitStopModel,
                       isExperimental) {
    var assetType =  assetConfiguration.assetTypes;
    var roadLinkBox = new RoadLinkBox(linkPropertiesModel);
    var speedLimitBox = new SpeedLimitBox(selectedSpeedLimit);
    var manoeuvreBox = new ManoeuvreBox();
    var winterSpeedLimits = new WinterSpeedLimitBox(_.find(linearAssets, {typeId: assetType.winterSpeedLimit}));
    //TODO these are commented/hidden for now, Tierekisteri is in end of life cycle but we still have tierekisteri specific data
    var trSpeedLimitBox = isExperimental ? [new TRSpeedLimitBox(_.find(linearAssets, {typeId: assetType.trSpeedLimits}))] : [];
    var careClassBox = new CareClassBox(_.find(linearAssets, {typeId: assetType.careClass}));
    var carryingCapacityBox = new CarryingCapacityBox(_.find(linearAssets, {typeId: assetType.carryingCapacity}));
    var pavedRoadBox = new PavedRoadBox(_.find(linearAssets, {typeId: assetType.pavedRoad}));
    var parkingProhibitionBox = new ParkingProhibitionBox(_.find(linearAssets, {typeId: assetType.parkingProhibition}));
    var cyclingAndWalking = new CyclingAndWalkingBox(_.find(linearAssets, {typeId: assetType.cyclingAndWalking}));
    var laneModellingBox = new LaneModellingBox(_.find(linearAssets, {typeId: assetType.laneModellingTool}));
    return [
      [roadLinkBox]
          .concat(laneModellingBox)
          .concat([speedLimitBox])
          .concat([manoeuvreBox])
          .concat(getLinearAsset(assetType.prohibition))
          .concat([parkingProhibitionBox])
          .concat(getLinearAsset(assetType.hazardousMaterialTransportProhibition))
          .concat([cyclingAndWalking]),
      []
          .concat(getLinearAsset(assetType.totalWeightLimit))
          .concat(getLinearAsset(assetType.trailerTruckWeightLimit))
          .concat(getLinearAsset(assetType.axleWeightLimit))
          .concat(getLinearAsset(assetType.bogieWeightLimit))
          .concat(getLinearAsset(assetType.heightLimit))
          .concat(getLinearAsset(assetType.lengthLimit))
          .concat(getLinearAsset(assetType.widthLimit)),
      []
          .concat([pavedRoadBox])
          .concat(getLinearAsset(assetType.roadWidth))
          .concat(getLinearAsset(assetType.litRoad))
          .concat([carryingCapacityBox])
          .concat(getLinearAsset(assetType.roadDamagedByThaw))
          .concat(getLinearAsset(assetType.roadWorksAsset)),
      []
          .concat(getLinearAsset(assetType.europeanRoads))
          .concat(getLinearAsset(assetType.exitNumbers))
          .concat([careClassBox])
          .concat(getLinearAsset(assetType.numberOfLanes))
          .concat(getLinearAsset(assetType.massTransitLane))
          .concat([winterSpeedLimits])
          .concat(getLinearAsset(assetType.trafficVolume)),
      []
      //TODO these are commented/hidden for now, Tierekisteri is in end of life cycle but we still have tierekisteri specific data
         /// .concat(trSpeedLimitBox)
    ];

    function getLinearAsset(typeId) {
      var asset = _.find(linearAssets, {typeId: typeId});
      if (asset) {
        var legendValues = [asset.editControlLabels.disabled, asset.editControlLabels.enabled, asset.editControlLabels.additionalInfo];
        return [new LinearAssetBox(asset, legendValues)];
      }
      return [];
    }
  }

  function groupPointAssets(assetConfiguration,
                            pointAssets,
                            linkPropertiesModel,
                            selectedSpeedLimit,
                            selectedMassTransitStopModel,
                            isExperimental
                            ) {
    var assetType = assetConfiguration.assetTypes;
    var massTransitBox = new MassTransitStopBox(selectedMassTransitStopModel);
    var trafficSignBox = new TrafficSignBox(_.find(pointAssets, {typeId: assetType.trafficSigns}), isExperimental);
    var trafficLightBox = new TrafficLightBox(_.find(pointAssets, {typeId: assetType.trafficLights}));
    var pedestrianCrossingBox = new PedestrianCrossingBox(_.find(pointAssets, {typeId: assetType.pedestrianCrossings}));
    return [
      []
        .concat([massTransitBox])
        .concat(getPointAsset(assetType.obstacles))
        .concat(getPointAsset(assetType.railwayCrossings))
        .concat(getPointAsset(assetType.directionalTrafficSigns))
        .concat(pedestrianCrossingBox)
        .concat(trafficLightBox)
        .concat([trafficSignBox])
        .concat(getPointAsset(assetType.servicePoints)),
      []
    ];

    function getPointAsset(typeId) {
      var asset = _.find(pointAssets, {typeId: typeId});
      if (asset) {
        return [new PointAssetBox(asset)];
      }
      return [];
    }
  }

  // Shows modal with message and close button
  function showInformationModal(message) {
    $('.container').append('<div class="modal-overlay confirm-modal" style="z-index: 2000"><div class="modal-dialog"><div class="content">' + message + '</div><div class="actions"><button class="btn btn-secondary close">Sulje</button></div></div></div></div>');
    $('.confirm-modal .close').on('click', function() {
      $('.confirm-modal').remove();
    });
  }

  application.restart = function(backend, withTileMaps, isExperimental) {
    localizedStrings = undefined;
    this.start(backend, withTileMaps, isExperimental);
  };

}(window.Application = window.Application || {}));
