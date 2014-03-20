jQuery(document).ready(function() {
  Oskari.setLang('fi');
  Oskari.setLoaderMode('dev');
  var appSetup;
  var appConfig;
  
  var assetIdFromURL = function() {
      var matches = window.location.hash.match(/\#\/asset\/(\d+)/);
      if (matches) {
        return matches[1];
      }
  };
  
  var downloadConfig = function(notifyCallback) {
    var assetId = assetIdFromURL();
    jQuery.ajax({
      type : 'GET',
      dataType : 'json',
      url : 'api/config' + (assetId ? '?assetId=' + assetId : ''),
      beforeSend: function(x) {
          if (x && x.overrideMimeType) {
              x.overrideMimeType("application/j-son;charset=UTF-8");
          }
      },
      success : function(config) {
         appConfig = config;
         notifyCallback();
      }
    });
  };
  var downloadAppSetup = function(notifyCallback) {
    jQuery.ajax({
      type : 'GET',
      dataType : 'json',
      url : 'full_appsetup.json',
      beforeSend: function(x) {
          if (x && x.overrideMimeType) {
              x.overrideMimeType("application/j-son;charset=UTF-8");
          }
      },
      success : function(setup) {
         appSetup = setup;
         notifyCallback();
      }
    });
  };
  
  eventbus.on('asset:fetched asset:created', function(asset) {
      window.location.hash = '#/asset/' + asset.externalId;
  });
  
  eventbus.on('asset:unselected', function(asset) {
    window.location.hash = '';
});
  
  var startApplication = function() {
    // check that both setup and config are loaded 
    // before actually starting the application
    if(appSetup && appConfig) {
      var app = Oskari.app;
      app.setApplicationSetup(appSetup);
      app.setConfiguration(appConfig);
      app.startApplication(function(startupInfos) {
          eventbus.trigger('application:initialized');
      });
    }
  };
  downloadAppSetup(startApplication);
  downloadConfig(startApplication);
});