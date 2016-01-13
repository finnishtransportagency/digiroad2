(function(application) {
  var localizedStrings;
  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var bindEvents = function(linearAssetSpecs, pointAssetSpecs) {
    var singleElementEventNames = _.pluck(linearAssetSpecs, 'singleElementEventCategory');
    var multiElementEventNames = _.pluck(linearAssetSpecs, 'multiElementEventCategory');
    var linearAssetSavingEvents = _.map(singleElementEventNames, function(name) { return name + ':saving'; }).join(' ');
    var pointAssetSavingEvents = _.map(pointAssetSpecs, function (spec) { return spec.layerName + ':saving'; }).join(' ');
    eventbus.on('asset:saving asset:creating speedLimit:saving linkProperties:saving manoeuvres:saving ' + linearAssetSavingEvents + ' ' + pointAssetSavingEvents, function() {
      indicatorOverlay();
    });

    var fetchedEventNames = _.map(multiElementEventNames, function(name) { return name + ':fetched'; }).join(' ');
    eventbus.on('asset:fetched asset:created speedLimits:fetched linkProperties:available manoeuvres:fetched pointAssets:fetched ' + fetchedEventNames, function() {
      jQuery('.spinner-overlay').remove();
    });

    var massUpdateFailedEventNames = _.map(multiElementEventNames, function(name) { return name + ':massUpdateFailed'; }).join(' ');
    eventbus.on('asset:updateFailed asset:creationFailed linkProperties:updateFailed speedLimits:massUpdateFailed ' + massUpdateFailedEventNames, function() {
      jQuery('.spinner-overlay').remove();
      alert(assetUpdateFailedMessage);
    });

    eventbus.on('confirm:show', function() { new Confirm(); });
  };

  var createOpenLayersMap = function(startupParameters) {
    var map = new OpenLayers.Map({
      controls: [],
      units: 'm',
      maxExtent: new OpenLayers.Bounds(-548576, 6291456, 1548576, 8388608),
      resolutions: [2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25, 0.125, 0.0625],
      projection: 'EPSG:3067',
      isBaseLayer: true,
      center: new OpenLayers.LonLat(startupParameters.lon, startupParameters.lat),
      fallThrough: true,
      theme: null,
      zoomMethod: null
    });
    var base = new OpenLayers.Layer("BaseLayer", {
      layerId: 0,
      isBaseLayer: true,
      displayInLayerSwitcher: false
    });
    map.addLayer(base);
    map.render('mapdiv');
    map.zoomTo(startupParameters.zoom);
    return map;
  };

  var setupMap = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters) {
    var map = createOpenLayersMap(startupParameters);

    var NavigationControl = OpenLayers.Class(OpenLayers.Control.Navigation, {
      wheelDown: function(evt, delta) {
        if (applicationModel.canZoomOut()) {
          return OpenLayers.Control.Navigation.prototype.wheelDown.apply(this,arguments);
        } else {
          new Confirm();
        }
      }
    });

    map.addControl(new NavigationControl());

    var mapOverlay = new MapOverlay($('.container'));

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
        linearAsset.title);
    });

    _.forEach(pointAssets, function(pointAsset) {
      PointAssetForm.initialize(pointAsset.selectedPointAsset, pointAsset.layerName, pointAsset.formLabels);
    });

    var linearAssetLayers = _.reduce(linearAssets, function(acc, asset) {
      acc[asset.layerName] = new LinearAssetLayer({
        map: map,
        application: applicationModel,
        collection: asset.collection,
        selectedLinearAsset: asset.selectedLinearAsset,
        roadCollection: models.roadCollection,
        geometryUtils: new GeometryUtils(),
        roadLayer: roadLayer,
        layerName: asset.layerName,
        multiElementEventCategory: asset.multiElementEventCategory,
        singleElementEventCategory: asset.singleElementEventCategory,
        style: PiecewiseLinearAssetStyle(applicationModel),
        formElements: AssetFormElementsFactory.construct(asset)
      });
      return acc;
    }, {});

    var pointAssetLayers = _.reduce(pointAssets, function(acc, asset) {
      acc[asset.layerName] = new PointAssetLayer({
        roadLayer: roadLayer,
        roadCollection: models.roadCollection,
        collection: asset.collection,
        map: map,
        selectedAsset: asset.selectedPointAsset,
        style: PointAssetStyle(asset.layerName),
        mapOverlay: mapOverlay,
        layerName: asset.layerName,
        newAsset: asset.newAsset
      });
      return acc;
    }, {});

    var layers = _.merge({
      road: roadLayer,
      linkProperty: new LinkPropertyLayer(map, roadLayer, new GeometryUtils(), models.selectedLinkProperty, models.roadCollection, models.linkPropertiesModel, applicationModel),
      massTransitStop: new AssetLayer(map, models.roadCollection, mapOverlay, new AssetGrouping(applicationModel), roadLayer),
      speedLimit: new SpeedLimitLayer({
        map: map,
        application: applicationModel,
        collection: models.speedLimitsCollection,
        selectedSpeedLimit: models.selectedSpeedLimit,
        geometryUtils: new GeometryUtils(),
        linearAsset: LinearAsset(),
        backend: backend,
        roadLayer: roadLayer
      }),
      manoeuvre: new ManoeuvreLayer(applicationModel, map, roadLayer, new GeometryUtils(), models.selectedManoeuvreSource, models.manoeuvresCollection, models.roadCollection)
    }, linearAssetLayers, pointAssetLayers);

    var mapPluginsContainer = $('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    new CoordinatesDisplay(map, mapPluginsContainer);

    new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

    applicationModel.moveMap(map.getZoom(), map.getExtent());

    return map;
  };

  var setupProjections = function() {
    proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs');
  };

  var startApplication = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters) {
    if (localizedStrings) {
      setupProjections();
      var map = setupMap(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters);
      var selectedPedestrianCrossing = getSelectedPointAsset(pointAssets, 'pedestrianCrossings');
      var selectedTrafficLight = getSelectedPointAsset(pointAssets, 'trafficLights');
      var selectedObstacle = getSelectedPointAsset(pointAssets, 'obstacles');
      var selectedRailwayCrossing =  getSelectedPointAsset(pointAssets, 'railwayCrossings');
      var selectedDirectionalTrafficSign = getSelectedPointAsset(pointAssets, 'directionalTrafficSigns');
      new URLRouter(map, backend, _.merge({}, models,
          { selectedPedestrianCrossing: selectedPedestrianCrossing },
          { selectedTrafficLight: selectedTrafficLight },
          { selectedObstacle: selectedObstacle },
          { selectedRailwayCrossing: selectedRailwayCrossing },
          { selectedDirectionalTrafficSign: selectedDirectionalTrafficSign  }
      ));
      eventbus.trigger('application:initialized');
    }
  };

  function getSelectedPointAsset(pointAssets, layerName) {
    return _(pointAssets).find({ layerName: layerName }).selectedPointAsset;
  }

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
      var collection = new PointAssetsCollection(backend, spec.layerName);
      var selectedPointAsset = new SelectedPointAsset(backend, spec.layerName);
      return _.merge({}, spec, {
        collection: collection,
        selectedPointAsset: selectedPointAsset
      });
    });

    var selectedMassTransitStopModel = SelectedAssetModel.initialize(backend);
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
    window.assetsModel = new AssetsModel(backend);
    window.selectedAssetModel = selectedMassTransitStopModel;
    var selectedLinearAssetModels = _.pluck(linearAssets, "selectedLinearAsset");
    var selectedPointAssetModels = _.pluck(pointAssets, "selectedPointAsset");
    window.applicationModel = new ApplicationModel([
      selectedAssetModel,
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
        new LocationSearch(backend, window.applicationModel, new GeometryUtils())
      ),
      new LayerSelectBox(assetSelectionMenu),
      assetGroups
    );

    AssetForm.initialize(backend);
    SpeedLimitForm.initialize(selectedSpeedLimit);
    WorkListView.initialize(backend);
    backend.getUserRoles();
    backend.getStartupParametersWithCallback(function(startupParameters) {
      backend.getAssetPropertyNamesWithCallback(function(assetPropertyNames) {
        localizedStrings = assetPropertyNames;
        window.localizedStrings = assetPropertyNames;
        startApplication(backend, models, linearAssets, pointAssets, tileMaps, startupParameters);
      });
    });
  };

  function groupAssets(linearAssets,
                       pointAssets,
                       linkPropertiesModel,
                       selectedSpeedLimit,
                       selectedMassTransitStopModel) {
    var roadLinkBox = new RoadLinkBox(linkPropertiesModel);
    var massTransitBox = new ActionPanelBoxes.AssetBox(selectedMassTransitStopModel);
    var speedLimitBox = new ActionPanelBoxes.SpeedLimitBox(selectedSpeedLimit);
    var manoeuvreBox = new ManoeuvreBox();

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
        .concat(getLinearAsset(assetType.winterSpeedLimit)),
      [massTransitBox]
        .concat(getPointAsset(assetType.obstacles))
        .concat(getPointAsset(assetType.railwayCrossings))
        .concat(getPointAsset(assetType.directionalTrafficSigns))
        .concat(getPointAsset(assetType.pedestrianCrossings))
        .concat(getPointAsset(assetType.trafficLights))
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
        .concat(getLinearAsset(assetType.widthLimit))
    ];

    function getLinearAsset(typeId) {
      var asset = _.find(linearAssets, {typeId: typeId});
      if (asset) {
        var legendValues = [asset.editControlLabels.disabled, asset.editControlLabels.enabled];
        return [new LinearAssetBox(asset.selectedLinearAsset, asset.layerName, asset.title, asset.className, legendValues)];
      }
      return [];
    }

    function getPointAsset(typeId) {
      var asset = _.find(pointAssets, {typeId: typeId});
      if (asset) {
        return [PointAssetBox(asset.selectedPointAsset, asset.title, asset.layerName, asset.legendValues)];
      }
      return [];
    }
  }

  application.restart = function(backend, withTileMaps) {
    localizedStrings = undefined;
    this.start(backend, withTileMaps);
  };

}(window.Application = window.Application || {}));
