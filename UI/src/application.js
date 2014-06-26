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

  eventbus.on('application:readOnly tool:changed validityPeriod:changed', function(readOnly) {
      window.location.hash = '';
  });
  
  $(window).on('hashchange', function(evt) {
      var data = assetIdFromURL();
      if (data && data.externalId) {
          Backend.getIdFromExternalId(data.externalId, data.keepPosition);
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
    if(appSetup && appConfig && localizedStrings) {
      var app = Oskari.app;
      app.setApplicationSetup(appSetup);
      app.setConfiguration(appConfig);
      app.startApplication(function(startupInfos) {
          eventbus.on('confirm:show', function() {
              new Confirm();
          });
          eventbus.trigger('application:initialized');
          var data = assetIdFromURL();
          if (data && data.externalId) {
              Backend.getIdFromExternalId(data.externalId);
          }
      });
    }
  };

  application.start = function (backend) {
    if (backend) window.Backend = backend;
    window.selectedAssetModel = SelectedAssetModel.initialize(Backend);
    ActionPanel.initialize(Backend);
    AssetForm.initialize(Backend);
    Backend.getApplicationSetup();
    Backend.getConfiguration(assetIdFromURL());
    Backend.getAssetPropertyNames();
  };

}(window.Application = window.Application || {}));
