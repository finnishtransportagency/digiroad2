(function(application) {
  Oskari.setLang('fi');
  Oskari.setLoaderMode('dev');
  var appSetup;
  var appConfig;
  var localizedStrings;

  var assetIdFromURL = function() {
    var matches = window.location.hash.match(/(\d+)(.*)/);
    if (matches) {
      return {externalId: matches[1], keepPosition: _.contains(window.location.hash, 'keepPosition=true')};
    }
  };

  eventbus.on('application:readOnly tool:changed validityPeriod:changed', function() {
    window.location.hash = '';
  });

  $(window).on('hashchange', function() {
    var data = assetIdFromURL();
    if (data && data.externalId) {
      Backend.getAssetByExternalId(data.externalId, function(asset) {
        eventbus.trigger('asset:fetched', asset);
        if (!data.keepPosition) {
          eventbus.trigger('coordinates:selected', { lat: asset.lat, lon: asset.lon });
        }
      });
    }
  });

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay"><div class="spinner"></div></div>');
  };

  eventbus.on('asset:saving asset:creating', function() {
    indicatorOverlay();
  });

  eventbus.on('asset:fetched asset:created', function() {
    jQuery('.spinner-overlay').remove();
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

  var startApplication = function() {
    // check that both setup and config are loaded 
    // before actually starting the application
    if (appSetup && appConfig && localizedStrings) {
      var app = Oskari.app;
      app.setApplicationSetup(appSetup);
      app.setConfiguration(appConfig);
      app.startApplication(function() {
        eventbus.on('confirm:show', function() {
          new Confirm();
        });
        eventbus.trigger('application:initialized');
        var data = assetIdFromURL();
        if (data && data.externalId) {
          Backend.getAssetByExternalId(data.externalId, function(asset) {
            eventbus.trigger('asset:fetched', asset);
          });
        }
      });
    }
  };

  application.start = function(backend) {
    if (backend) window.Backend = backend;
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
