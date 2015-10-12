var URLRouter = function(map, backend, models) {
  var Router = Backbone.Router.extend({
    initialize: function() {
      // Support legacy format for opening mass transit stop via ...#300289
      this.route(/^(\d+)$/, function(nationalId) {
        this.massTransitStop(nationalId);
      });

      this.route(/^([A-Za-z]+)$/, function(layer) {
        applicationModel.selectLayer(layer);
      });
    },

    routes: {
      'massTransitStop/:id': 'massTransitStop',
      'asset/:id': 'massTransitStop',
      'linkProperty/:mmlId': 'linkProperty',
      'speedLimit/:mmlId': 'speedLimit',
      'work-list/speedLimit': 'speedLimitWorkList',
      'work-list/linkProperty': 'linkPropertyWorkList',
      'work-list/massTransitStop': 'massTransitStopWorkList'
    },

    massTransitStop: function(id) {
      applicationModel.selectLayer('massTransitStop');
      backend.getMassTransitStopByNationalId(id, function(massTransitStop) {
        eventbus.once('massTransitStops:available', function() {
          models.selectedMassTransitStopModel.changeByExternalId(id);
        });
        map.setCenter(new OpenLayers.LonLat(massTransitStop.lon, massTransitStop.lat), 12);
      });
    },

    linkProperty: function(mmlId) {
      applicationModel.selectLayer('linkProperty');
      backend.getRoadLinkByMMLId(mmlId, function(response) {
        eventbus.once('linkProperties:available', function() {
          models.selectedLinkProperty.open(response.id);
        });
        map.setCenter(new OpenLayers.LonLat(response.middlePoint.x, response.middlePoint.y), 12);
      });
    },

    speedLimit: function(mmlId) {
      var roadLinkReceived = backend.getRoadLinkByMMLId(mmlId);
      var layerSelected = eventbus.oncePromise('layer:speedLimit:shown');
      applicationModel.selectLayer('speedLimit');
      $.when(layerSelected).then(function() {
        var mapMoved = $.when(roadLinkReceived).then(function(response) {
          var promise =  eventbus.oncePromise('layer:speedLimit:moved');
          map.setCenter(new OpenLayers.LonLat(response.middlePoint.x, response.middlePoint.y), 12);
          return promise;
        });
        $.when(mapMoved).then(function() {
          eventbus.trigger('speedLimit:selectByMmlId', parseInt(mmlId, 10));
        });
      });
    },

    speedLimitWorkList: function() {
      eventbus.trigger('workList:select', 'speedLimit', backend.getUnknownLimits());
    },

    linkPropertyWorkList: function() {
      eventbus.trigger('workList:select', 'linkProperty', backend.getIncompleteLinks());
    },

    massTransitStopWorkList: function() {
      eventbus.trigger('workList:select', 'massTransitStop', backend.getFloatingMassTransitStops());
    }
  });

  var router = new Router();
  // Tests seem to start Backbone.History multiple times
  if (!Backbone.History.started) {
    Backbone.history.start();
  }

  eventbus.on('asset:closed', function() {
    router.navigate('massTransitStop');
  });

  eventbus.on('asset:fetched asset:created', function(asset) {
    router.navigate('massTransitStop/' + asset.nationalId);
  });

  eventbus.on('linkProperties:unselected', function() {
    router.navigate('linkProperty');
  });

  eventbus.on('linkProperties:selected', function(linkProperty) {
    router.navigate('linkProperty/' + linkProperty.mmlId);
  });

  eventbus.on('layer:selected', function(layer) {
    router.navigate(layer);
  });
};

(function(application) {
  var linearAssetSpecs = [
    {
      typeId: 30,
      singleElementEventCategory: 'totalWeightLimit',
      multiElementEventCategory: 'totalWeightLimits',
      layerName: 'totalWeightLimit',
      title: 'Suurin sallittu massa',
      newTitle: 'Uusi suurin sallittu massa',
      className: 'total-weight-limit',
      unit: 'kg',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: 40,
      singleElementEventCategory: 'trailerTruckWeightLimit',
      multiElementEventCategory: 'trailerTruckWeightLimits',
      layerName: 'trailerTruckWeightLimit',
      title: 'Yhdistelmän suurin sallittu massa',
      newTitle: 'Uusi yhdistelmän suurin sallittu massa',
      className: 'trailer-truck-weight-limit',
      unit: 'kg',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: 50,
      singleElementEventCategory: 'axleWeightLimit',
      multiElementEventCategory: 'axleWeightLimits',
      layerName: 'axleWeightLimit',
      title: 'Suurin sallittu akselimassa',
      newTitle: 'Uusi suurin sallittu akselimassa',
      className: 'axle-weight-limit',
      unit: 'kg',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: 60,
      singleElementEventCategory: 'bogieWeightLimit',
      multiElementEventCategory: 'bogieWeightlLimits',
      layerName: 'bogieWeightLimit',
      title: 'Suurin sallittu telimassa',
      newTitle: 'Uusi suurin sallittu telimassa',
      className: 'bogie-weight-limit',
      unit: 'kg',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: 70,
      singleElementEventCategory: 'heightLimit',
      multiElementEventCategory: 'heightLimits',
      layerName: 'heightLimit',
      title: 'Suurin sallittu korkeus',
      newTitle: 'Uusi suurin sallittu korkeus',
      className: 'height-limit',
      unit: 'cm',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: 80,
      singleElementEventCategory: 'lengthLimit',
      multiElementEventCategory: 'lengthLimits',
      layerName: 'lengthLimit',
      title: 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus',
      newTitle: 'Uusi pituusrajoitus',
      className: 'length-limit',
      unit: 'cm',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: 90,
      singleElementEventCategory: 'widthLimit',
      multiElementEventCategory: 'widthLimits',
      layerName: 'widthLimit',
      title: 'Suurin sallittu leveys',
      newTitle: 'Uusi suurin sallittu leveys',
      className: 'width-limit',
      unit: 'cm',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: 100,
      defaultValue: 1,
      singleElementEventCategory: 'litRoad',
      multiElementEventCategory: 'litRoads',
      layerName: 'litRoad',
      title: 'Valaistu tie',
      newTitle: 'Uusi valaistu tie',
      className: 'lit-road',
      isSeparable: false,
      editControlLabels: { title: 'Valaistus',
        enabled: 'Valaistus',
        disabled: 'Ei valaistusta' }
    },
    {
      typeId: 110,
      defaultValue: 1,
      singleElementEventCategory: 'pavedRoad',
      multiElementEventCategory: 'pavedRoads',
      layerName: 'pavedRoad',
      title: 'Päällystetty tie',
      newTitle: 'Uusi päällystetty tie',
      className: 'paved-road',
      isSeparable: false,
      editControlLabels: { title: 'Päällyste',
        enabled: 'Päällyste',
        disabled: 'Ei päällystettä' }
    },
    {
      typeId: 120,
      singleElementEventCategory: 'roadWidth',
      multiElementEventCategory: 'roadWidth',
      layerName: 'roadWidth',
      title: 'Tien leveys',
      newTitle: 'Uusi tien leveys',
      className: 'road-width',
      unit: 'cm',
      isSeparable: false,
      editControlLabels: {
        title: 'Leveys',
        enabled: 'Leveys tiedossa',
        disabled: 'Leveys ei tiedossa'
      }
    },
    {
      typeId: 130,
      defaultValue: 1,
      singleElementEventCategory: 'roadDamagedByThaw',
      multiElementEventCategory: 'roadsDamagedByThaw',
      layerName: 'roadDamagedByThaw',
      title: 'Kelirikko',
      newTitle: 'Uusi kelirikko',
      className: 'road-damaged-by-thaw',
      isSeparable: false,
      editControlLabels: {
        title: 'Kelirikko',
        enabled: 'Kelirikko',
        disabled: 'Ei kelirikkoa'
      }
    },
    {
      typeId: 150,
      defaultValue: 1,
      singleElementEventCategory: 'congestionTendency',
      multiElementEventCategory: 'congestionTendencies',
      layerName: 'congestionTendency',
      title: 'Ruuhkautumisherkkyys',
      newTitle: 'Uusi ruuhkautumisherkkä tie',
      className: 'congestion-tendency',
      isSeparable: false,
      editControlLabels: {
        title: 'Herkkyys',
        enabled: 'Ruuhkautumisherkkä',
        disabled: 'Ei ruuhkautumisherkkä'
      }
    },
    {
      typeId: 170,
      singleElementEventCategory: 'trafficVolume',
      multiElementEventCategory: 'trafficVolumes',
      layerName: 'trafficVolume',
      title: 'Liikennemäärä',
      newTitle: 'Uusi liikennemäärä',
      className: 'traffic-volume',
      unit: 'ajoneuvoa/vuorokausi',
      isSeparable: false,
      editControlLabels: {
        title: '',
        enabled: 'Liikennemäärä',
        disabled: 'Ei tiedossa'
      }
    },
    {
      typeId: 140,
      singleElementEventCategory: 'laneCount',
      multiElementEventCategory: 'laneCounts',
      layerName: 'numberOfLanes',
      title: 'Kaistojen lukumäärä',
      newTitle: 'Uusi kaistojen lukumäärä',
      className: 'lane-count',
      unit: 'kpl / suunta',
      isSeparable: true,
      editControlLabels: {
        title: 'Lukumäärä',
        enabled: 'Kaistojen lukumäärä / suunta',
        disabled: 'Linkin mukainen tyypillinen kaistamäärä'
      }
    },
    {
      typeId: 160,
      defaultValue: 1,
      singleElementEventCategory: 'massTransitLane',
      multiElementEventCategory: 'massTransitLanes',
      layerName: 'massTransitLanes',
      title: 'Joukkoliikennekaista',
      newTitle: 'Uusi joukkoliikennekaista',
      className: 'mass-transit-lane',
      isSeparable: true,
      editControlLabels: {
        title: 'Kaista',
        enabled: 'Joukkoliikennekaista',
        disabled: 'Ei joukkoliikennekaistaa'
      }
    }
  ];
  var localizedStrings;
  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var bindEvents = function() {
    var singleElementEventNames = _.pluck(linearAssetSpecs, 'singleElementEventCategory');
    var multiElementEventNames = _.pluck(linearAssetSpecs, 'multiElementEventCategory');
    var savingEventNames = _.map(singleElementEventNames, function(name) { return name + ':saving'; }).join(' ');
    eventbus.on('asset:saving asset:creating speedLimit:saving linkProperties:saving ' + savingEventNames, function() {
      indicatorOverlay();
    });
    var fetchedEventNames = _.map(multiElementEventNames, function(name) { return name + ':fetched'; }).join(' ');
    eventbus.on('asset:fetched asset:created speedLimits:fetched linkProperties:available ' + fetchedEventNames, function() {
      jQuery('.spinner-overlay').remove();
    });

    eventbus.on('asset:saved', function() {
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

  var setupMap = function(backend, models, linearAssets, withTileMaps, startupParameters) {
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
          PiecewiseLinearAssetFormElements(linearAsset.unit, linearAsset.editControlLabels, linearAsset.className, linearAsset.defaultValue),
          linearAsset.newTitle,
          linearAsset.title);
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
        formElements: PiecewiseLinearAssetFormElements(asset.unit, asset.editControlLabels, asset.className, asset.defaultValue)
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
    }, linearAssetLayers);

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

  var startApplication = function(backend, models, linearAssets, withTileMaps, startupParameters) {
    if (localizedStrings) {
      setupProjections();
      var map = setupMap(backend, models, linearAssets, withTileMaps, startupParameters);
      new URLRouter(map, backend, models);
      eventbus.trigger('application:initialized');
    }
  };

  application.start = function(customBackend, withTileMaps) {
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ?  true : withTileMaps;
    var roadCollection = new RoadCollection(backend);
    var speedLimitsCollection = new SpeedLimitsCollection(backend);
    var selectedSpeedLimit = new SelectedSpeedLimit(backend, speedLimitsCollection);
    var selectedLinkProperty = new SelectedLinkProperty(backend, roadCollection);
    var linkPropertiesModel = new LinkPropertiesModel();
    var manoeuvresCollection = new ManoeuvresCollection(backend, roadCollection);
    var selectedManoeuvreSource = new SelectedManoeuvreSource(manoeuvresCollection);
    var linearAssets = _.map(linearAssetSpecs, function(spec) {
      var collection = new LinearAssetsCollection(backend, spec.typeId, spec.singleElementEventCategory, spec.multiElementEventCategory);
      var selectedLinearAsset = new SelectedLinearAsset(backend, collection, spec.typeId,
                                                         spec.singleElementEventCategory, spec.multiElementEventCategory, spec.isSeparable);
      return _.merge({}, spec, {
        collection: collection,
        selectedLinearAsset: selectedLinearAsset
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

    bindEvents();
    window.assetsModel = new AssetsModel(backend);
    window.selectedAssetModel = selectedMassTransitStopModel;
    var selectedLinearAssetModels = _.pluck(linearAssets, "selectedLinearAsset");
    window.applicationModel = new ApplicationModel([
      selectedAssetModel,
      selectedSpeedLimit,
      selectedLinkProperty,
      selectedManoeuvreSource].concat(selectedLinearAssetModels));
    ActionPanel.initialize(backend,
                           new InstructionsPopup($('.digiroad2')),
                           selectedSpeedLimit,
                           linearAssets,
                           linkPropertiesModel,
                           new LocationSearch(backend, window.applicationModel, new GeometryUtils()));
    AssetForm.initialize(backend);
    SpeedLimitForm.initialize(selectedSpeedLimit);
    WorkListView.initialize(backend);
    backend.getStartupParametersWithCallback(function(startupParameters) {
      backend.getAssetPropertyNamesWithCallback(function(assetPropertyNames) {
        localizedStrings = assetPropertyNames;
        window.localizedStrings = assetPropertyNames;
        startApplication(backend, models, linearAssets, tileMaps, startupParameters);
      });
    });
  };

  application.restart = function(backend, withTileMaps) {
    localizedStrings = undefined;
    this.start(backend, withTileMaps);
  };

}(window.Application = window.Application || {}));
