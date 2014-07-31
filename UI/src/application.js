(function(application) {
  Oskari.setLang('fi');
  Oskari.setLoaderMode('dev');
  var appSetup;
  var appConfig;
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
    eventbus.on('application:readOnly tool:changed asset:closed', function() {
      window.location.hash = '';
    });

    $(window).on('hashchange', hashChangeHandler);

    eventbus.on('asset:saving asset:creating', function() {
      indicatorOverlay();
    });

    eventbus.on('asset:fetched', function(asset) {
      jQuery('.spinner-overlay').remove();
      var keepPosition = 'true';
      var data = assetIdFromURL();
      if (data && !data.keepPosition) {
        eventbus.trigger('coordinates:selected', { lat: asset.lat, lon: asset.lon });
        keepPosition = 'false';
      }
      window.location.hash = '#/asset/' + asset.externalId + '?keepPosition=' + keepPosition;
    });

    eventbus.on('asset:saved asset:created', function() {
      jQuery('.spinner-overlay').remove();
    });

    eventbus.on('asset:updateFailed asset:creationFailed', function() {
      jQuery('.spinner-overlay').remove();
      alert(assetUpdateFailedMessage);
    });

    eventbus.on('applicationSetup:fetched', function(setup) {
      appSetup = setup;
      startApplication();
    });

    eventbus.on('configuration:fetched', function(config) {
      appConfig = config;
      startApplication();
    });

    eventbus.on('assetPropertyNames:fetched', function(assetPropertyNames) {
      localizedStrings = assetPropertyNames;
      window.localizedStrings = assetPropertyNames;
      startApplication();
    });

    eventbus.on('confirm:show', function() { new Confirm(); });

    eventbus.once('assets:all-updated', selectAssetFromAddressBar);
  };

  var startApplication = function() {
    // check that both setup and config are loaded 
    // before actually starting the application
    if (appSetup && appConfig && localizedStrings) {
      var app = Oskari.app;
      app.setApplicationSetup(appSetup);
      app.setConfiguration(appConfig);
      app.startApplication(function() {
        eventbus.trigger('application:initialized');
      });
    }
  };

  application.start = function(backend) {
    bindEvents();
    if (backend) window.Backend = backend;
    window.AssetsModel.initialize();
    window.selectedAssetModel = SelectedAssetModel.initialize(Backend);
    ActionPanel.initialize(Backend);
    AssetForm.initialize(Backend);
    Backend.getApplicationSetup();
    Backend.getConfiguration(assetIdFromURL());
    Backend.getAssetPropertyNames();
  };

  application.restart = function() {
    appSetup = undefined;
    appConfig = undefined;
    localizedStrings = undefined;
    this.start();
  };

}(window.Application = window.Application || {}));
