jQuery(document).ready(function() {
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
    var downloadLocalizedStrings = function(notifyCallback) {
        jQuery.ajax({
            type : 'GET',
            dataType : 'json',
            url : 'api/assetPropertyNames/fi',
            beforeSend: function(x) {
                if (x && x.overrideMimeType) {
                    x.overrideMimeType("application/j-son;charset=UTF-8");
                }
            },
            success : function(ls) {
                localizedStrings = ls;
                window.localizedStrings = localizedStrings;
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
    if(appSetup && appConfig && localizedStrings) {
      var app = Oskari.app;
      app.setApplicationSetup(appSetup);
      app.setConfiguration(appConfig);
      app.startApplication(function(startupInfos) {
          ConfirmDialogController.initialize();
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
  downloadAppSetup(startApplication);
  downloadConfig(startApplication);
  downloadLocalizedStrings(startApplication);
});