(function(application) {
  application.start = function(customBackend, withTileMaps, isExperimental) {
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ?  true : withTileMaps;
    var roadCollection = new RoadCollection(backend);
    var speedLimitsCollection = new SpeedLimitsCollection(backend);
    var selectedSpeedLimit = new SelectedSpeedLimit(backend, speedLimitsCollection);
    var selectedLinkProperty = new SelectedLinkProperty(backend, roadCollection);
    var linkPropertiesModel = new LinkPropertiesModel();
    var manoeuvresCollection = new ManoeuvresCollection(backend, roadCollection);
    var selectedManoeuvreSource = new SelectedManoeuvreSource(manoeuvresCollection);
    var instructionsPopup = new InstructionsPopup($('.digiroad2'));
    var enabledExperimentalAssets = isExperimental ? experimentalLinearAssetSpecs : [];
    var enabledLinearAssetSpecs = linearAssetSpecs.concat(enabledExperimentalAssets);
    var linearAssets = _.map(enabledLinearAssetSpecs, function(spec) {
      var collection = new LinearAssetsCollection(backend, spec.typeId, spec.singleElementEventCategory, spec.multiElementEventCategory);
      var selectedLinearAsset = SelectedLinearAssetFactory.construct(backend, collection, spec);
      return _.merge({}, spec, {
        collection: collection,
        selectedLinearAsset: selectedLinearAsset
      });
    });

    var pointAssets = _.map(pointAssetSpecs, function(spec) {
      var collection = _.isUndefined(spec.collection ) ?  new PointAssetsCollection(backend, spec.layerName, spec.allowComplementaryLinks) : new spec.collection(backend, spec.layerName, spec.allowComplementaryLinks) ;
      var selectedPointAsset = new SelectedPointAsset(backend, spec.layerName, roadCollection);
      return _.merge({}, spec, {
        collection: collection,
        selectedPointAsset: selectedPointAsset
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

    bindEvents(enabledLinearAssetSpecs, pointAssetSpecs);
    window.massTransitStopsCollection = new MassTransitStopsCollection(backend);
    window.selectedMassTransitStopModel = selectedMassTransitStopModel;
    var selectedLinearAssetModels = _.pluck(linearAssets, "selectedLinearAsset");
    var selectedPointAssetModels = _.pluck(pointAssets, "selectedPointAsset");
    window.applicationModel = new ApplicationModel([
      selectedMassTransitStopModel,
      selectedSpeedLimit,
      selectedLinkProperty,
      selectedManoeuvreSource]
        .concat(selectedLinearAssetModels)
        .concat(selectedPointAssetModels));

    EditModeDisclaimer.initialize(instructionsPopup);

    var assetGroups = groupAssets(linearAssets,
        pointAssets,
        linkPropertiesModel,
        selectedSpeedLimit,
        selectedMassTransitStopModel);

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
    WorkListView.initialize(backend);
    VerificationListView.initialize(backend);
    backend.getUserRoles();
    backend.getStartupParametersWithCallback(function(startupParameters) {
      backend.getAssetPropertyNamesWithCallback(function(assetPropertyNames) {
        localizedStrings = assetPropertyNames;
        window.localizedStrings = assetPropertyNames;
        startApplication(backend, models, linearAssets, pointAssets, tileMaps, startupParameters, roadCollection);
      });
    });
  };

  var startApplication = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection) {
    if (localizedStrings) {
      setupProjections();
      var map = setupMap(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection);
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

  var bindEvents = function(linearAssetSpecs, pointAssetSpecs, roadCollection) {
    var singleElementEventNames = _.pluck(linearAssetSpecs, 'singleElementEventCategory');
    var multiElementEventNames = _.pluck(linearAssetSpecs, 'multiElementEventCategory');
    var linearAssetSavingEvents = _.map(singleElementEventNames, function(name) { return name + ':saving'; }).join(' ');
    var pointAssetSavingEvents = _.map(pointAssetSpecs, function (spec) { return spec.layerName + ':saving'; }).join(' ');
    eventbus.on('asset:saving asset:creating speedLimit:saving linkProperties:saving manoeuvres:saving ' + linearAssetSavingEvents + ' ' + pointAssetSavingEvents, function() {
      indicatorOverlay();
    });

    var fetchedEventNames = _.map(multiElementEventNames, function(name) { return name + ':fetched'; }).join(' ');
    eventbus.on('asset:saved asset:fetched asset:created speedLimits:fetched linkProperties:available manoeuvres:fetched pointAssets:fetched ' + fetchedEventNames, function() {
      jQuery('.spinner-overlay').remove();
    });

    var massUpdateFailedEventNames = _.map(multiElementEventNames, function(name) { return name + ':massUpdateFailed'; }).join(' ');
    eventbus.on('asset:updateFailed asset:creationFailed linkProperties:updateFailed speedLimits:massUpdateFailed ' + massUpdateFailedEventNames, function() {
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
    return map;
  };

  var setupMap = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection) {
    var tileMaps = new TileMapCollection(map, "");

    var map = createOpenLayersMap(startupParameters, tileMaps.layers);

    var mapOverlay = new MapOverlay($('.container'));

    var mapPluginsContainer = $('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    new CoordinatesDisplay(map, mapPluginsContainer);
    new TrafficSignToggle(map, mapPluginsContainer);

    var roadAddressInfoPopup = new RoadAddressInfoPopup(map, mapPluginsContainer, roadCollection);

    if (withTileMaps) { new TileMapCollection(map); }
    var roadLayer = new RoadLayer(map, models.roadCollection);

    new LinkPropertyForm(models.selectedLinkProperty);
    new ManoeuvreForm(models.selectedManoeuvreSource);
    _.forEach(linearAssets, function(linearAsset) {
     LinearAssetForm.initialize(
       linearAsset.selectedLinearAsset,
       linearAsset.singleElementEventCategory,
       AssetFormElementsFactory.construct(linearAsset),
       linearAsset.newTitle,
       linearAsset.title,
       linearAsset.editConstrains || function() {return false;},
       linearAsset.layerName,
       linearAsset.isVerifiable);
    });

    _.forEach(pointAssets, function(pointAsset ) {
     PointAssetForm.initialize(pointAsset.typeId, pointAsset.selectedPointAsset, pointAsset.collection, pointAsset.layerName, pointAsset.formLabels, pointAsset.editConstrains || function() {return false;}, roadCollection, applicationModel, backend);
    });

    var trafficSignReadOnlyLayer = function(layerName){
      return new TrafficSignReadOnlyLayer({
        layerName: layerName,
        style: new PointAssetStyle('trafficSigns'),
        collection: new TrafficSignsReadOnlyCollection(backend, 'trafficSigns', true),
        assetLabel: new TrafficSignLabel(),
        assetGrouping: new AssetGrouping(9),
        map: map
      });
    };

    var linearAssetLayers = _.reduce(linearAssets, function(acc, asset) {
     acc[asset.layerName] = new LinearAssetLayer({
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
       formElements: AssetFormElementsFactory.construct(asset),
       assetLabel: asset.label,
       roadAddressInfoPopup: roadAddressInfoPopup,
       editConstrains : asset.editConstrains || function() {return false;},
       hasTrafficSignReadOnlyLayer: asset.hasTrafficSignReadOnlyLayer,
       trafficSignReadOnlyLayer: trafficSignReadOnlyLayer(asset.layerName),
       massLimitation : asset.editControlLabels.massLimitations,
       typeId : asset.typeId
     });
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
       editConstrains : asset.editConstrains || function() {return false;}
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

    }, linearAssetLayers, pointAssetLayers);

    VioniceLayer({ map: map });

    // Show environment name next to Digiroad logo
    $('#notification').append(Environment.localizedName());

    // Show information modal in integration environment (remove when not needed any more)
    if (Environment.name() === 'integration') {
      showInformationModal('Huom!<br>Tämä sivu ei ole enää käytössä.<br>Digiroad-sovellus on siirtynyt osoitteeseen <a href="https://extranet.liikennevirasto.fi/digiroad/" style="color:#FFFFFF;text-decoration: underline">https://extranet.liikennevirasto.fi/digiroad/</a>');
    }

    new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

    applicationModel.moveMap(map.getView().getZoom(), map.getLayers().getArray()[0].getExtent());

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

  function groupAssets(linearAssets,
                       pointAssets,
                       linkPropertiesModel,
                       selectedSpeedLimit,
                       selectedMassTransitStopModel) {
    var roadLinkBox = new RoadLinkBox(linkPropertiesModel);
    var massTransitBox = new ActionPanelBoxes.AssetBox(selectedMassTransitStopModel);
    var speedLimitBox = new ActionPanelBoxes.SpeedLimitBox(selectedSpeedLimit);
    var manoeuvreBox = new ManoeuvreBox();
    var winterSpeedLimits = new ActionPanelBoxes.WinterSpeedLimitBox(_.find(linearAssets, {typeId: assetType.winterSpeedLimit}));
    var serviceRoadBox = new ActionPanelBoxes.ServiceRoadBox(_.find(linearAssets, {typeId: assetType.maintenanceRoad}));

    return [
      [roadLinkBox],
      [].concat(getLinearAsset(assetType.litRoad))
          .concat(getLinearAsset(assetType.pavedRoad))
          .concat(getLinearAsset(assetType.width))
          .concat(getLinearAsset(assetType.numberOfLanes))
          .concat(getLinearAsset(assetType.massTransitLane))
          .concat(getLinearAsset(assetType.europeanRoads))
          .concat(getLinearAsset(assetType.exitNumbers))
          .concat(getLinearAsset(assetType.trSpeedLimits)),
      [speedLimitBox].concat(
      [winterSpeedLimits]),
      [massTransitBox]
          .concat(getPointAsset(assetType.obstacles))
          .concat(getPointAsset(assetType.railwayCrossings))
          .concat(getPointAsset(assetType.directionalTrafficSigns))
          .concat(getPointAsset(assetType.pedestrianCrossings))
          .concat(getPointAsset(assetType.trafficLights))
          .concat(getPointAsset(assetType.trafficSigns))
          .concat(getPointAsset(assetType.servicePoints)),
      [].concat(getLinearAsset(assetType.trafficVolume))
          .concat(getLinearAsset(assetType.congestionTendency))
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
      [].concat([serviceRoadBox])
    ];

    function getLinearAsset(typeId) {
      var asset = _.find(linearAssets, {typeId: typeId});
      if (asset) {
        var legendValues = [asset.editControlLabels.disabled, asset.editControlLabels.enabled, asset.editControlLabels.massLimitations];
        return [new LinearAssetBox(asset.selectedLinearAsset, asset.layerName, asset.title, asset.className, legendValues, asset.editControlLabels.showUnit, asset.unit, asset.allowComplementaryLinks, asset.hasTrafficSignReadOnlyLayer)];
      }
      return [];
    }

    function getPointAsset(typeId) {
      var asset = _.find(pointAssets, {typeId: typeId});
      if (asset) {
        return [PointAssetBox(asset.selectedPointAsset, asset.title, asset.layerName, asset.legendValues, asset.allowComplementaryLinks)];
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

  application.restart = function(backend, withTileMaps) {
    localizedStrings = undefined;
    this.start(backend, withTileMaps);
  };

}(window.Application = window.Application || {}));
