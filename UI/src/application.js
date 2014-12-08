var RoadCollection = function(backend) {
  var roadLinks = [];

  this.fetch = function(boundingBox) {
    backend.getRoadLinks(boundingBox, function(data) {
      roadLinks = data;
      eventbus.trigger('roadLinks:fetched', roadLinks);
    });
  };

  this.getAll = function() {
    return roadLinks;
  };

  this.activate = function(road) {
    eventbus.trigger('road:active', road.roadLinkId);
  };

  this.getPointsOfRoadLink = function(id) {
    var road = _.find(roadLinks, function(road) {
      return road.roadLinkId === id;
    });
    return _.cloneDeep(road.points);
  };
};

(function(application) {
  var localizedStrings;
  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

  var assetIdFromURL = function() {
    var matches = window.location.hash.match(/(\d+)(.*)/);
    if (matches) {
      return {externalId: parseInt(matches[1], 10), keepPosition: _.contains(window.location.hash, 'keepPosition=true')};
    }
  };

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay"><div class="spinner"></div></div>');
  };

  var selectAssetFromAddressBar = function() {
    var data = assetIdFromURL();
    if (data && data.externalId) {
      selectedAssetModel.changeByExternalId(data.externalId);
    }
  };

  var hashChangeHandler = function() {
    $(window).off('hashchange', hashChangeHandler);
    var oldHash = window.location.hash;

    selectAssetFromAddressBar(); // Empties the hash, so we need to set it back to original state.

    window.location.hash = oldHash;
    $(window).on('hashchange', hashChangeHandler);
  };

  var bindEvents = function() {
    eventbus.on('application:readOnly tool:changed asset:closed asset:placed', function() {
      window.location.hash = '';
    });

    $(window).on('hashchange', hashChangeHandler);

    eventbus.on('asset:saving asset:creating', function() {
      indicatorOverlay();
    });

    eventbus.on('asset:fetched asset:created', function(asset) {
      jQuery('.spinner-overlay').remove();
      var keepPosition = 'true';
      var data = assetIdFromURL();
      if (data && !data.keepPosition) {
        eventbus.trigger('coordinates:selected', { lat: asset.lat, lon: asset.lon });
        keepPosition = 'false';
      }
      window.location.hash = '#/asset/' + asset.externalId + '?keepPosition=' + keepPosition;
    });

    eventbus.on('asset:saved', function() {
      jQuery('.spinner-overlay').remove();
    });

    eventbus.on('asset:updateFailed asset:creationFailed', function() {
      jQuery('.spinner-overlay').remove();
      alert(assetUpdateFailedMessage);
    });

    eventbus.on('confirm:show', function() { new Confirm(); });

    eventbus.once('assets:all-updated', selectAssetFromAddressBar);
  };

  var createOpenLayersMap = function(startupParameters) {
    var map = new OpenLayers.Map({
      controls: [],
      units: 'm',
      maxExtent: new OpenLayers.Bounds(-548576, 6291456, 1548576, 8388608),
      resolutions: [2049, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],
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

  var setupMap = function(backend, models, withTileMaps, startupParameters) {
    var map = createOpenLayersMap(startupParameters);
    map.addControl(new OpenLayers.Control.Navigation());

    var mapOverlay = new MapOverlay($('.container'));

    if (withTileMaps) { new TileMapCollection(map); }
    var roadCollection = new RoadCollection(backend);
    var geometryUtils = new GeometryUtils();
    var linearAsset = new LinearAsset(geometryUtils);
    var roadLayer = new RoadLayer(map, roadCollection);
    var layers = {
      road: roadLayer,
      asset: new AssetLayer(map, roadCollection, mapOverlay, new AssetGrouping(applicationModel)),
      speedLimit: new SpeedLimitLayer({
        map: map,
        application: applicationModel,
        collection: models.speedLimitsCollection,
        selectedSpeedLimit: models.selectedSpeedLimit,
        roadCollection: roadCollection,
        geometryUtils: geometryUtils,
        linearAsset: linearAsset
      }),
      totalWeightLimit: new TotalWeightLimitLayer({
        map: map,
        application: applicationModel,
        collection: models.totalWeightLimitsCollection,
        selectedTotalWeightLimit: models.selectedTotalWeightLimit,
        roadCollection: roadCollection,
        geometryUtils: geometryUtils,
        linearAsset: linearAsset,
        roadLayer: roadLayer
      })
    };

    var mapPluginsContainer = $('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    new MouseCoordinatesDisplay(map, mapPluginsContainer);

    new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

    applicationModel.moveMap(map.getZoom(), map.getExtent());
  };

  var setupProjections = function() {
    proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs');
  };

  var startApplication = function(backend, models, withTileMaps, startupParameters) {
    if (localizedStrings) {
      setupProjections();
      setupMap(backend, models, withTileMaps, startupParameters);
      eventbus.trigger('application:initialized');
    }
  };

  application.start = function(customBackend, withTileMaps) {
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ?  true : withTileMaps;
    var speedLimitsCollection = new SpeedLimitsCollection(backend);
    var totalWeightLimitsCollection = new TotalWeightLimitsCollection(backend);
    var selectedSpeedLimit = new SelectedSpeedLimit(backend, speedLimitsCollection);
    var selectedTotalWeightLimit = new SelectedTotalWeightLimit(backend, totalWeightLimitsCollection);
    var models = {
      speedLimitsCollection: speedLimitsCollection,
      totalWeightLimitsCollection: totalWeightLimitsCollection,
      selectedSpeedLimit: selectedSpeedLimit,
      selectedTotalWeightLimit: selectedTotalWeightLimit
    };
    bindEvents();
    window.assetsModel = new AssetsModel(backend);
    window.selectedAssetModel = SelectedAssetModel.initialize(backend);
    window.applicationModel = new ApplicationModel(selectedAssetModel, selectedSpeedLimit, selectedTotalWeightLimit);
    ActionPanel.initialize(backend, selectedSpeedLimit, selectedTotalWeightLimit);
    AssetForm.initialize(backend);
    SpeedLimitForm.initialize(selectedSpeedLimit);
    TotalWeightLimitForm.initialize(selectedTotalWeightLimit);
    backend.getStartupParametersWithCallback(assetIdFromURL(), function(startupParameters) {
      backend.getAssetPropertyNamesWithCallback(function(assetPropertyNames) {
        localizedStrings = assetPropertyNames;
        window.localizedStrings = assetPropertyNames;
        startApplication(backend, models, tileMaps, startupParameters);
      });
    });
  };

  application.restart = function(backend, withTileMaps) {
    localizedStrings = undefined;
    this.start(backend, withTileMaps);
  };

}(window.Application = window.Application || {}));
