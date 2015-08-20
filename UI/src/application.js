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
      'linkProperties/:mmlId': 'linkProperty',
      'speedLimit/:mmlId': 'speedLimit',
      'work-list/:layer': 'workList'
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
      applicationModel.selectLayer('linkProperties');
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
      var mapMoved = $.when(roadLinkReceived).then(function(response) {
        var promise =  eventbus.oncePromise('layer:speedLimit:moved');
        map.setCenter(new OpenLayers.LonLat(response.middlePoint.x, response.middlePoint.y), 12);
        return promise;
      });
      $.when(layerSelected, mapMoved).then(function() {
        eventbus.trigger('speedLimit:selectByMmlId', parseInt(mmlId, 10));
      });
    },

    workList: function(layerName){
      eventbus.trigger('workList:select', layerName, backend.getUnknownLimits());
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
    router.navigate('linkProperties');
  });

  eventbus.on('linkProperties:selected', function(linkProperty) {
    router.navigate('linkProperties/' + linkProperty.mmlId);
  });

  eventbus.on('layer:selected', function(layer) {
    router.navigate(layer);
  });
};

(function(application) {
  var localizedStrings;
  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var bindEvents = function() {
    eventbus.on('asset:saving asset:creating speedLimit:saving', function() {
      indicatorOverlay();
    });

    eventbus.on('asset:fetched asset:created speedLimits:fetched', function(asset) {
      jQuery('.spinner-overlay').remove();
    });

    eventbus.on('asset:saved', function() {
      jQuery('.spinner-overlay').remove();
    });

    eventbus.on('asset:updateFailed asset:creationFailed linkProperties:updateFailed speedLimits:massUpdateFailed', function() {
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

  var setupMap = function(backend, models, numericalLimits, withTileMaps, startupParameters) {
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
    var geometryUtils = new GeometryUtils();
    var linearAsset = new LinearAsset(geometryUtils);
    var roadLayer = new RoadLayer(map, models.roadCollection);

    new LinkPropertyForm(models.selectedLinkProperty);
    new ManoeuvreForm(models.selectedManoeuvreSource);
    _.forEach(numericalLimits, function(numericalLimit) {
      new NumericalLimitForm(
          numericalLimit.selectedNumericalLimit,
          numericalLimit.newNumericalLimitTitle,
          numericalLimit.className,
          numericalLimit.singleElementEventCategory,
          numericalLimit.unit);
    });

    var numericalLimitLayers = _.reduce(numericalLimits, function(acc, numericalLimit) {
      acc[numericalLimit.layerName] = new NumericalLimitLayer({
        map: map,
        application: applicationModel,
        collection: numericalLimit.collection,
        selectedNumericalLimit: numericalLimit.selectedNumericalLimit,
        roadCollection: models.roadCollection,
        geometryUtils: geometryUtils,
        linearAsset: linearAsset,
        roadLayer: roadLayer,
        layerName: numericalLimit.layerName,
        multiElementEventCategory: numericalLimit.multiElementEventCategory,
        singleElementEventCategory: numericalLimit.singleElementEventCategory
      });
      return acc;
    }, {});

    var layers = _.merge({
      road: roadLayer,
      linkProperties: new LinkPropertyLayer(map, roadLayer, geometryUtils, models.selectedLinkProperty, models.roadCollection, models.linkPropertiesModel),
      massTransitStop: new AssetLayer(map, models.roadCollection, mapOverlay, new AssetGrouping(applicationModel), roadLayer),
      speedLimit: new SpeedLimitLayer({
        map: map,
        application: applicationModel,
        collection: models.speedLimitsCollection,
        selectedSpeedLimit: models.selectedSpeedLimit,
        geometryUtils: geometryUtils,
        linearAsset: linearAsset,
        backend: backend,
        roadLayer: roadLayer
      }),
      manoeuvre: new ManoeuvreLayer(applicationModel, map, roadLayer, geometryUtils, models.selectedManoeuvreSource, models.manoeuvresCollection, models.roadCollection)
    }, numericalLimitLayers);

    var mapPluginsContainer = $('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    new MouseCoordinatesDisplay(map, mapPluginsContainer);

    new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

    applicationModel.moveMap(map.getZoom(), map.getExtent());

    return map;
  };

  var setupProjections = function() {
    proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs');
  };

  var startApplication = function(backend, models, numericalLimits, withTileMaps, startupParameters) {
    if (localizedStrings) {
      setupProjections();
      var map = setupMap(backend, models, numericalLimits, withTileMaps, startupParameters);
      new URLRouter(map, backend, models);
      eventbus.trigger('application:initialized');
    }
  };

  application.start = function(customBackend, withTileMaps) {
    var numericalLimitSpecs = [
      {
        typeId: 30,
        singleElementEventCategory: 'totalWeightLimit',
        multiElementEventCategory: 'totalWeightLimits',
        layerName: 'totalWeightLimit',
        numericalLimitTitle: 'Suurin sallittu massa',
        newNumericalLimitTitle: 'Uusi suurin sallittu massa',
        className: 'total-weight-limit',
        unit: 'kg'
      },
      {
        typeId: 40,
        singleElementEventCategory: 'trailerTruckWeightLimit',
        multiElementEventCategory: 'trailerTruckWeightLimits',
        layerName: 'trailerTruckWeightLimit',
        numericalLimitTitle: 'Yhdistelmän suurin sallittu massa',
        newNumericalLimitTitle: 'Uusi yhdistelmän suurin sallittu massa',
        className: 'trailer-truck-weight-limit',
        unit: 'kg'
      },
      {
        typeId: 50,
        singleElementEventCategory: 'axleWeightLimit',
        multiElementEventCategory: 'axleWeightLimits',
        layerName: 'axleWeightLimit',
        numericalLimitTitle: 'Suurin sallittu akselimassa',
        newNumericalLimitTitle: 'Uusi suurin sallittu akselimassa',
        className: 'axle-weight-limit',
        unit: 'kg'
      },
      {
        typeId: 60,
        singleElementEventCategory: 'bogieWeightLimit',
        multiElementEventCategory: 'bogieWeightlLimits',
        layerName: 'bogieWeightLimit',
        numericalLimitTitle: 'Suurin sallittu telimassa',
        newNumericalLimitTitle: 'Uusi suurin sallittu telimassa',
        className: 'bogie-weight-limit',
        unit: 'kg'
      },
      {
        typeId: 70,
        singleElementEventCategory: 'heightLimit',
        multiElementEventCategory: 'heightLimits',
        layerName: 'heightLimit',
        numericalLimitTitle: 'Suurin sallittu korkeus',
        newNumericalLimitTitle: 'Uusi suurin sallittu korkeus',
        className: 'height-limit',
        unit: 'cm'
      },
      {
        typeId: 80,
        singleElementEventCategory: 'lengthLimit',
        multiElementEventCategory: 'lengthLimits',
        layerName: 'lengthLimit',
        numericalLimitTitle: 'Ajoneuvon tai -yhdistelmän suurin sallittu pituus',
        newNumericalLimitTitle: 'Uusi ajoneuvon tai -yhdistelmän suurin sallittu pituus',
        className: 'length-limit',
        unit: 'cm'
      },
      {
        typeId: 90,
        singleElementEventCategory: 'widthLimit',
        multiElementEventCategory: 'widthLimits',
        layerName: 'widthLimit',
        numericalLimitTitle: 'Suurin sallittu leveys',
        newNumericalLimitTitle: 'Uusi suurin sallittu leveys',
        className: 'width-limit',
        unit: 'cm'
      }
    ];
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ?  true : withTileMaps;
    var roadCollection = new RoadCollection(backend);
    var speedLimitsCollection = new SpeedLimitsCollection(backend);
    var selectedSpeedLimit = new SelectedSpeedLimit(backend, speedLimitsCollection);
    var selectedLinkProperty = new SelectedLinkProperty(backend, roadCollection);
    var linkPropertiesModel = new LinkPropertiesModel();
    var manoeuvresCollection = new ManoeuvresCollection(backend, roadCollection);
    var selectedManoeuvreSource = new SelectedManoeuvreSource(manoeuvresCollection);

    var numericalLimits = _.map(numericalLimitSpecs, function(spec) {
      var collection = new NumericalLimitsCollection(backend, spec.typeId, spec.singleElementEventCategory, spec.multiElementEventCategory);
      var selectedNumericalLimit = new SelectedNumericalLimit(backend, spec.typeId, collection, spec.singleElementEventCategory);
      return _.merge({}, spec, {
        collection: collection,
        selectedNumericalLimit: selectedNumericalLimit
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
    window.selectedLinkProperty = selectedLinkProperty;
    var selectedNumericalLimitModels = _.pluck(numericalLimits, "selectedNumericalLimit");
    window.applicationModel = new ApplicationModel([
      selectedAssetModel,
      selectedSpeedLimit,
      selectedLinkProperty,
      selectedManoeuvreSource].concat(selectedNumericalLimitModels));
    ActionPanel.initialize(backend, new InstructionsPopup($('.digiroad2')), selectedSpeedLimit, numericalLimits, linkPropertiesModel);
    AssetForm.initialize(backend);
    SpeedLimitForm.initialize(selectedSpeedLimit);
    WorkListView.initialize(backend);
    backend.getStartupParametersWithCallback(function(startupParameters) {
      backend.getAssetPropertyNamesWithCallback(function(assetPropertyNames) {
        localizedStrings = assetPropertyNames;
        window.localizedStrings = assetPropertyNames;
        startApplication(backend, models, numericalLimits, tileMaps, startupParameters);
      });
    });
  };

  application.restart = function(backend, withTileMaps) {
    localizedStrings = undefined;
    this.start(backend, withTileMaps);
  };

}(window.Application = window.Application || {}));
