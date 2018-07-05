(function(application) {
  application.start = function(customBackend, withTileMaps, isExperimental) {
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ?  true : withTileMaps;
    var roadCollection = new RoadCollection(backend);
    var verificationCollection = new AssetsVerificationCollection(backend);
    var speedLimitsCollection = new SpeedLimitsCollection(backend, verificationCollection);
    var feedbackCollection = new FeedbackModel(backend);
    var selectedSpeedLimit = new SelectedSpeedLimit(backend, speedLimitsCollection);
    var selectedLinkProperty = new SelectedLinkProperty(backend, roadCollection);
    var linkPropertiesModel = new LinkPropertiesModel();
    var manoeuvresCollection = new ManoeuvresCollection(backend, roadCollection, verificationCollection);
    var selectedManoeuvreSource = new SelectedManoeuvreSource(manoeuvresCollection);
    var instructionsPopup = new InstructionsPopup($('.digiroad2'));
    var assetConfiguration = new AssetTypeConfiguration();
    var enabledExperimentalAssets = isExperimental ? assetConfiguration.experimentalAssetsConfig : [];
    var enabledLinearAssetSpecs = assetConfiguration.linearAssetsConfig.concat(enabledExperimentalAssets);
    var authorizationPolicy = new AuthorizationPolicy();
    new FeedbackTool(authorizationPolicy, feedbackCollection);

    var linearAssets = _.map(enabledLinearAssetSpecs, function(spec) {
      var collection = _.isUndefined(spec.collection ) ?  new LinearAssetsCollection(backend, verificationCollection, spec) : new spec.collection(backend, verificationCollection, spec) ;
      var selectedLinearAsset = SelectedLinearAssetFactory.construct(backend, collection, spec);
      var authorizationPolicy = _.isUndefined(spec.authorizationPolicy) ? new AuthorizationPolicy() : spec.authorizationPolicy;
      return _.merge({}, spec, {
        collection: collection,
        selectedLinearAsset: selectedLinearAsset,
        authorizationPolicy: authorizationPolicy
      });
    });

    var pointAssets = _.map(assetConfiguration.pointAssetsConfig, function(spec) {
      var collection = _.isUndefined(spec.collection ) ?  new PointAssetsCollection(backend, spec, verificationCollection) : new spec.collection(backend, spec, verificationCollection) ;
      var selectedPointAsset = new SelectedPointAsset(backend, spec.layerName, roadCollection);
      var authorizationPolicy = _.isUndefined(spec.authorizationPolicy) ? new AuthorizationPolicy() : spec.authorizationPolicy;
      return _.merge({}, spec, {
        collection: collection,
        selectedPointAsset: selectedPointAsset,
        authorizationPolicy: authorizationPolicy
      });
    });

    var groupedPointAssets = _.map(assetConfiguration.groupedPointAssetSpecs, function(spec) {
      var collection = _.isUndefined(spec.collection) ?  new GroupedPointAssetsCollection(backend, spec) : new spec.collection(backend, spec) ;
      var selectedPointAsset = new SelectedPointAsset(backend, spec.layerName, roadCollection);
      var authorizationPolicy = _.isUndefined(spec.authorizationPolicy) ? new AuthorizationPolicy() : spec.authorizationPolicy;
      return _.merge({}, spec, {
        collection: collection,
        selectedPointAsset: selectedPointAsset,
        authorizationPolicy: authorizationPolicy
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
      manoeuvresCollection: manoeuvresCollection
    };

    bindEvents(enabledLinearAssetSpecs, assetConfiguration.pointAssetsConfig);
    window.massTransitStopsCollection = new MassTransitStopsCollection(backend, verificationCollection);
    window.selectedMassTransitStopModel = selectedMassTransitStopModel;
    var selectedLinearAssetModels = _.map(linearAssets, "selectedLinearAsset");
    var selectedPointAssetModels = _.map(pointAssets, "selectedPointAsset");
    var selectedGroupedPointAssetModels = _.map(groupedPointAssets, "selectedPointAsset");
    window.applicationModel = new ApplicationModel([
      selectedMassTransitStopModel,
      selectedSpeedLimit,
      selectedLinkProperty,
      selectedManoeuvreSource]
        .concat(selectedLinearAssetModels)
        .concat(selectedPointAssetModels)
        .concat(selectedGroupedPointAssetModels));

    EditModeDisclaimer.initialize(instructionsPopup);

    var assetGroups = groupAssets(assetConfiguration,
        linearAssets,
        pointAssets,
        linkPropertiesModel,
        selectedSpeedLimit,
        selectedMassTransitStopModel,
        groupedPointAssets,
        isExperimental);

    var assetSelectionMenu = AssetSelectionMenu(assetGroups, {
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
        assetGroups
    );

    RoadAddressInfoDataInitializer.initialize(isExperimental);
    MassTransitStopForm.initialize(backend);
    SpeedLimitForm.initialize(selectedSpeedLimit);

    new WorkListView().initialize(backend);
    new VerificationWorkList().initialize();
    new MunicipalityWorkList().initialize(backend);
    new SpeedLimitWorkList().initialize();

    backend.getStartupParametersWithCallback(function(startupParameters) {
      backend.getAssetPropertyNamesWithCallback(function(assetPropertyNames) {
        localizedStrings = assetPropertyNames;
        window.localizedStrings = assetPropertyNames;
        startApplication(backend, models, linearAssets, pointAssets, tileMaps, startupParameters, roadCollection, verificationCollection, groupedPointAssets);
      });
    });
  };

  var startApplication = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, groupedPointAssets) {
    if (localizedStrings) {
      setupProjections();
      var map = setupMap(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, groupedPointAssets);
      var selectedPedestrianCrossing = getSelectedPointAsset(pointAssets, 'pedestrianCrossings');
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
          { selectedTrafficSign: selectedTrafficSign},
          { selectedMaintenanceRoad: selectedMaintenanceRoad},
          { linearAssets: linearAssets}
    ));
      eventbus.trigger('application:initialized');
    }
  };

  var localizedStrings;

  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';
  var tierekisteriFailedMessage = 'Tietojen tallentaminen/muokkaminen Tierekisterissa epäonnistui. Tehtyjä muutoksia ei tallennettu OTH:ssa';
  var tierekisteriFailedMessageDelete = 'Tietojen poisto Tierekisterissä epäonnistui. Pysäkkiä ei poistettu OTH:ssa';
  var vkmNotFoundMessage = 'Sovellus ei pysty tunnistamaan annetulle pysäkin sijainnille tieosoitetta. Pysäkin tallennus Tierekisterissä ja OTH:ssa epäonnistui';
  var notFoundInTierekisteriMessage = 'Huom! Tämän pysäkin tallennus ei onnistu, koska vastaavaa pysäkkiä ei löydy Tierekisteristä tai Tierekisteriin ei ole yhteyttä tällä hetkellä.';
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
    eventbus.on('asset:saved asset:fetched asset:created speedLimits:fetched linkProperties:available manoeuvres:fetched pointAssets:fetched municipality:verified ' + fetchedEventNames, function() {
      jQuery('.spinner-overlay').remove();
    });

    var massUpdateFailedEventNames = _.map(multiElementEventNames, function(name) { return name + ':massUpdateFailed'; }).join(' ');
    eventbus.on('asset:updateFailed asset:creationFailed linkProperties:updateFailed speedLimits:massUpdateFailed municipality:verificationFailed ' + massUpdateFailedEventNames, function() {
      jQuery('.spinner-overlay').remove();
      alert(assetUpdateFailedMessage);
    });

    eventbus.on('asset:notFoundInTierekisteri', function() {
      jQuery('.spinner-overlay').remove();
      alert(notFoundInTierekisteriMessage);
    });

    eventbus.on('asset:creationTierekisteriFailed asset:updateTierekisteriFailed', function() {
      jQuery('.spinner-overlay').remove();
      alert(tierekisteriFailedMessage);
    });

    eventbus.on('asset:deleteTierekisteriFailed', function() {
      jQuery('.spinner-overlay').remove();
      alert(tierekisteriFailedMessageDelete);
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
      })
    });
    map.setProperties({extent : [-548576, 6291456, 1548576, 8388608]});
    map.addInteraction(new ol.interaction.DragPan({
      condition: function (mapBrowserEvent) {
        var originalEvent = mapBrowserEvent.originalEvent;
        return (!originalEvent.altKey && !originalEvent.shiftKey);
      }
    }));
    return map;
  };

  var setupMap = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, groupedPointAssets) {
    var tileMaps = new TileMapCollection(map, "");

    var map = createOpenLayersMap(startupParameters, tileMaps.layers);

    var mapOverlay = new MapOverlay($('.container'));

    var mapPluginsContainer = $('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    new CoordinatesDisplay(map, mapPluginsContainer);
    new TrafficSignToggle(map, mapPluginsContainer);
    new MunicipalityDisplay(map, mapPluginsContainer, backend);
    new DefaultLocationButton(map, mapPluginsContainer, backend, new InstructionsPopup($('.digiroad2')));
    var roadAddressInfoPopup = new RoadAddressInfoPopup(map, mapPluginsContainer, roadCollection);

    if (withTileMaps) { new TileMapCollection(map); }
    var roadLayer = new RoadLayer(map, models.roadCollection);

    new LinkPropertyForm(models.selectedLinkProperty);
    new ManoeuvreForm(models.selectedManoeuvreSource);
    _.forEach(linearAssets, function(linearAsset) {
      if(linearAsset.form)
        linearAsset.form.initialize(linearAsset);
      else
        LinearAssetForm.initialize(
            linearAsset,
            AssetFormElementsFactory.construct(linearAsset)
        );
    });

    _.forEach(pointAssets, function(pointAsset ) {
    new PointAssetForm(
       pointAsset,
       roadCollection,
       applicationModel,
       backend,
       pointAsset.saveCondition || function() {return true;});
    });

    _.forEach(groupedPointAssets, function(pointAsset) {
      GroupedPointAssetForm.initialize(
        pointAsset,
        roadCollection
       );
    });

    var trafficSignReadOnlyLayer = function(layerName){
      return new TrafficSignReadOnlyLayer({
        layerName: layerName,
        style: new PointAssetStyle('trafficSigns'),
        collection: new TrafficSignsReadOnlyCollection(backend, 'trafficSigns', true),
        assetLabel: new TrafficSignLabel(9),
        assetGrouping: new AssetGrouping(9),
        map: map
      });
    };

    var linearAssetLayers = _.reduce(linearAssets, function(acc, asset) {
      var parameters ={
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
       hasTrafficSignReadOnlyLayer: asset.hasTrafficSignReadOnlyLayer,
       trafficSignReadOnlyLayer: trafficSignReadOnlyLayer(asset.layerName),
       massLimitation: asset.editControlLabels.massLimitations,
       typeId: asset.typeId,
       isMultipleLinkSelectionAllowed: asset.isMultipleLinkSelectionAllowed

      };
      acc[asset.layerName] = asset.layer ? asset.layer.call(this, parameters) : new LinearAssetLayer(parameters);
      return acc;

    }, {});

    var pointAssetLayers = _.reduce(pointAssets, function(acc, asset) {
     acc[asset.layerName] = new PointAssetLayer({
       roadLayer: roadLayer,
       application: applicationModel,
       roadCollection: models.roadCollection,
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
       hasTrafficSignReadOnlyLayer: asset.hasTrafficSignReadOnlyLayer,
       trafficSignReadOnlyLayer: trafficSignReadOnlyLayer(asset.layerName),
       authorizationPolicy: asset.authorizationPolicy
     });
     return acc;
    }, {});

    var groupedPointAssetLayers = _.reduce(groupedPointAssets, function(acc, asset) {
      acc[asset.layerName] = new GroupedPointAssetLayer({
        roadLayer: roadLayer,
        application: applicationModel,
        roadCollection: models.roadCollection,
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
        hasTrafficSignReadOnlyLayer: asset.hasTrafficSignReadOnlyLayer,
        authorizationPolicy: asset.authorizationPolicy,
        assetTypeIds: asset.typeIds
      });
      return acc;
    }, {});

    var layers = _.merge({
      road: roadLayer,
      linkProperty: new LinkPropertyLayer(map, roadLayer, models.selectedLinkProperty, models.roadCollection, models.linkPropertiesModel, applicationModel, roadAddressInfoPopup),
       massTransitStop: new MassTransitStopLayer(map, models.roadCollection, mapOverlay, new AssetGrouping(36), roadLayer, roadAddressInfoPopup),
       speedLimit: new SpeedLimitLayer({
       map: map,
       application: applicationModel,
       collection: models.speedLimitsCollection,
       selectedSpeedLimit: models.selectedSpeedLimit,
       trafficSignReadOnlyLayer: trafficSignReadOnlyLayer('speedLimit'),
       style: SpeedLimitStyle(applicationModel),
       roadLayer: roadLayer,
       roadAddressInfoPopup: roadAddressInfoPopup
       }),
       manoeuvre: new ManoeuvreLayer(applicationModel, map, roadLayer, models.selectedManoeuvreSource, models.manoeuvresCollection, models.roadCollection)

    }, linearAssetLayers, pointAssetLayers, groupedPointAssetLayers);

    VioniceLayer({ map: map });

    // Show environment name next to Digiroad logo
    $('#notification').append(Environment.localizedName());

    // Show information modal in integration environment (remove when not needed any more)
    if (Environment.name() === 'integration') {
      showInformationModal('Huom!<br>Tämä sivu ei ole enää käytössä.<br>Digiroad-sovellus on siirtynyt osoitteeseen <a href="https://extranet.liikennevirasto.fi/digiroad/" style="color:#FFFFFF;text-decoration: underline">https://extranet.liikennevirasto.fi/digiroad/</a>');
    }

    new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

    applicationModel.moveMap(zoomlevels.getViewZoom(map), map.getLayers().getArray()[0].getExtent());
    backend.getUserRoles();
    return map;
  };

  var setupProjections = function() {
    proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs');
  };

  function getSelectedPointAsset(pointAssets, layerName) {
    return _(pointAssets).find({ layerName: layerName }).selectedPointAsset;
  }

  function getSelectedLinearAsset(linearAssets, layerName) {
    return _(linearAssets).find({ layerName: layerName }).selectedLinearAsset;
  }

  function groupAssets(assetConfiguration,
                       linearAssets,
                       pointAssets,
                       linkPropertiesModel,
                       selectedSpeedLimit,
                       selectedMassTransitStopModel,
                       groupedPointAssets,
                       isExperimental) {
    var assetType =  assetConfiguration.assetTypes;
    var assetGroups = assetConfiguration.assetGroups;
    var roadLinkBox = new RoadLinkBox(linkPropertiesModel);
    var massTransitBox = new MassTransitStopBox(selectedMassTransitStopModel);
    var speedLimitBox = new SpeedLimitBox(selectedSpeedLimit);
    var manoeuvreBox = new ManoeuvreBox();
    var winterSpeedLimits = new WinterSpeedLimitBox(_.find(linearAssets, {typeId: assetType.winterSpeedLimit}));
    var serviceRoadBox = new ServiceRoadBox(_.find(linearAssets, {typeId: assetType.maintenanceRoad}));
    var trSpeedLimitBox = isExperimental ? [new TRSpeedLimitBox(_.find(linearAssets, {typeId: assetType.trSpeedLimits}))] : [];
    var trafficSignBox = new TrafficSignBox(_.find(pointAssets, {typeId: assetType.trafficSigns}));
    var heightBox = new HeightLimitationBox(_.find(pointAssets, {typeId: assetType.trHeightLimits}));
    var widthBox = new WidthLimitationBox(_.find(pointAssets, {typeId: assetType.trWidthLimits}));
    return [
      [roadLinkBox],
      [].concat(getLinearAsset(assetType.litRoad))
          .concat(getLinearAsset(assetType.pavedRoad))
          .concat(getLinearAsset(assetType.width))
          .concat(getLinearAsset(assetType.numberOfLanes))
          .concat(getLinearAsset(assetType.massTransitLane))
          .concat(getLinearAsset(assetType.europeanRoads))
          .concat(getLinearAsset(assetType.exitNumbers)),
      [speedLimitBox]
        .concat([winterSpeedLimits]),
      [massTransitBox]
          .concat(getPointAsset(assetType.obstacles))
          .concat(getPointAsset(assetType.railwayCrossings))
          .concat(getPointAsset(assetType.directionalTrafficSigns))
          .concat(getPointAsset(assetType.pedestrianCrossings))
          .concat(getPointAsset(assetType.trafficLights))
          .concat([trafficSignBox])
          .concat(getPointAsset(assetType.servicePoints)),
      [].concat(getLinearAsset(assetType.trafficVolume))
          .concat(getLinearAsset(assetType.damagedByThaw)),
      [manoeuvreBox]
        .concat(getLinearAsset(assetType.prohibition))
        .concat(getLinearAsset(assetType.hazardousMaterialTransportProhibition))
        .concat(getLinearAsset(assetType.totalWeightLimit))
        .concat(getLinearAsset(assetType.trailerTruckWeightLimit))
        .concat(getLinearAsset(assetType.axleWeightLimit))
        .concat(getLinearAsset(assetType.bogieWeightLimit))
        .concat(getLinearAsset(assetType.heightLimit))
        .concat(getLinearAsset(assetType.lengthLimit))
        .concat(getLinearAsset(assetType.widthLimit)),
      [].concat([serviceRoadBox]),
      [].concat([heightBox])
        .concat([widthBox])
        .concat(getGroupedPointAsset(assetGroups.trWeightGroup)),
      [].concat(trSpeedLimitBox)

    ];

    function getLinearAsset(typeId) {
      var asset = _.find(linearAssets, {typeId: typeId});
      if (asset) {
        var legendValues = [asset.editControlLabels.disabled, asset.editControlLabels.enabled, asset.editControlLabels.massLimitations];
        return [new LinearAssetBox(asset, legendValues)];
      }
      return [];
    }

    function getPointAsset(typeId) {
      var asset = _.find(pointAssets, {typeId: typeId});
      if (asset) {
        return [new PointAssetBox(asset)];
      }
      return [];
    }

  function getGroupedPointAsset(typeIds) {
    var asset = _.find(groupedPointAssets, {typeIds: typeIds.sort()});
    if (asset) {
      return [new WeightLimitationBox(asset)];
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
