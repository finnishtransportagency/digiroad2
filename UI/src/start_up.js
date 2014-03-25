jQuery(document).ready(function() {
  Oskari.setLang('fi');
  Oskari.setLoaderMode('dev');
  var appSetup;
  var appConfig;

  var linearAssetLayerConfig =  {
      "wmsName": "speedlimit",
      "type": "linearassetlayer",
      "id": 236,
      "minScale": 6000,
       "wmsUrl": "/data/dummy/busstops.json",
       "url": "api/assets?assetTypeId=10",
       "maxScale": 1,
       "orgName": "LiVi",
       "inspire": "Ominaisuustiedot",
       "name": "Voimassaolevat"
  };
  
  var assetIdFromURL = function() {
      var matches = window.location.hash.match(/(\d+)(.*)/);
      if (matches) {
        return {externalId: matches[1], keepPosition: matches[2] !== ''};
      }
  };
  
  var downloadConfig = function(notifyCallback) {
    var data = assetIdFromURL();
    jQuery.ajax({
      type : 'GET',
      dataType : 'json',
      url : 'api/config' + (data && data.externalId ? '?externalAssetId=' + data.externalId : ''),
      beforeSend: function(x) {
          if (x && x.overrideMimeType) {
              x.overrideMimeType("application/j-son;charset=UTF-8");
          }
      },
      success : function(config) {
          config.mapfull.conf.layers.push(linearAssetLayerConfig);
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
  
  eventbus.on('application:readOnly', function(readOnly) {
      window.location.hash = '';
  });
  
  $(window).on('hashchange', function(evt) {
      var data = assetIdFromURL();
      if (data && data.externalId) {
          Backend.getIdFromExternalId(data.externalId, data.keepPosition);
      }
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
          var data = assetIdFromURL();
          if (data && data.externalId) {
              Backend.getIdFromExternalId(data.externalId);
          }
      });
    }
  };
  downloadAppSetup(startApplication);
  downloadConfig(startApplication);
});