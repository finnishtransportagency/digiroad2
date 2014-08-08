(function (backend) {
  function initialize(backend) {
    backend.getEnumeratedPropertyValues = function (assetTypeId) {
      $.getJSON('api/enumeratedPropertyValues/' + assetTypeId, function (enumeratedPropertyValues) {
        eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValues);
      })
        .fail(function () {
          console.log("error");
        });
    };

    backend.putAssetPropertyValue = function (assetId, propertyPublicId, data) {
      putAssetPropertyValue(assetId, propertyPublicId, data);
    };

    backend.getAssets = function (assetTypeId, boundingBox) {
      this.getAssetsWithCallback(assetTypeId, boundingBox, function (assets) {
        eventbus.trigger('assets:fetched', assets);
      });
    };

    backend.getAssetsWithCallback = _.throttle(function(assetTypeId, boundingBox, callback) {
      $.getJSON('api/assets?assetTypeId=' + assetTypeId + '&bbox=' + boundingBox, callback)
        .fail(function() { console.log("error"); });
    }, 1000);

    backend.getSpeedLimits = _.throttle(function (boundingBox) {
      $.getJSON('api/linearassets?bbox=' + boundingBox, function (speedLimits) {
        eventbus.trigger('speedLimits:fetched', speedLimits);
      });
    }, 1000);

    backend.getRoadLinks = _.throttle(function (boundingBox) {
      $.getJSON('api/roadlinks?bbox=' + boundingBox, function (roadLinks) {
        eventbus.trigger('roadLinks:fetched', roadLinks);
      });
    }, 1000);

    backend.getAsset = function (assetId) {
      this.getAssetWithCallback(assetId, function (asset) {
        eventbus.trigger('asset:fetched', asset);
      });
    };

    backend.getAssetWithCallback = function(assetId, callback) {
      $.get('api/assets/' + assetId, callback);
    };

    backend.getAssetByExternalId = function (externalId, callback) {
      $.get('api/assets/' + externalId + '?externalId=true', callback);
    };

    backend.getAssetTypeProperties = function (assetTypeId, callback) {
      $.get('api/assetTypeProperties/' + assetTypeId, callback);
    };

    backend.getUserRoles = function () {
      $.get('api/user/roles', function (roles) {
        eventbus.trigger('roles:fetched', roles);
      });
    };

    backend.getApplicationSetup = function() {
      $.getJSON('full_appsetup.json', function(setup) {
        eventbus.trigger('applicationSetup:fetched', setup);
      });
    };

    backend.getConfiguration = function(selectedAsset) {
      var url = 'api/config' + (selectedAsset && selectedAsset.externalId ? '?externalAssetId=' + selectedAsset.externalId : '');
      $.getJSON(url, function(config) {
        eventbus.trigger('configuration:fetched', config);
      });
    };

    backend.getAssetPropertyNames = function() {
      $.getJSON('api/assetPropertyNames/fi', function(propertyNames) {
        eventbus.trigger('assetPropertyNames:fetched', propertyNames);
      });
    };

    backend.createAsset = function (data, errorCallback) {
      eventbus.trigger('asset:creating');
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/assets",
        data: JSON.stringify(data),
        dataType: "json",
        success: function (asset) {
          eventbus.trigger('asset:created', asset);
        },
        error: errorCallback
      });
    };

    backend.updateAsset = function (id, data, successCallback, errorCallback) {
      eventbus.trigger('asset:saving');
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/assets/" + id,
        data: JSON.stringify(data),
        dataType: "json",
        success: successCallback,
        error: errorCallback
      });
    };

    // FIXME: Dummy implementation
    var id = 0;
    backend.createSpeedLimit = function (startPosition, endPosition) {
      eventbus.trigger('speedLimit:created', {id: ++id, points: [startPosition, endPosition]});
    };

    backend.withRoadLinkData = function (roadLinkData) {
      this.getRoadLinks = function () {
        eventbus.trigger('roadLinks:fetched', roadLinkData);
      };
      return this;
    };

    backend.withUserRolesData = function(userRolesData) {
      this.getUserRoles = function () {
        eventbus.trigger('roles:fetched', userRolesData);
      };
      return this;
    };

    backend.withEnumeratedPropertyValues = function(enumeratedPropertyValuesData) {
      this.getEnumeratedPropertyValues = function () {
        eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValuesData);
      };
      return this;
    };

    backend.withApplicationSetupData = function(applicationSetupData) {
      this.getApplicationSetup = function () {
        eventbus.trigger('applicationSetup:fetched', applicationSetupData);
      };
      return this;
    };

    backend.withConfigurationData = function(configurationData) {
      this.getConfiguration = function () {
        eventbus.trigger('configuration:fetched', configurationData);
      };
      return this;
    };

    backend.withAssetPropertyNamesData = function(assetPropertyNamesData) {
      this.getAssetPropertyNames = function () {
        eventbus.trigger('assetPropertyNames:fetched', assetPropertyNamesData);
      };
      return this;
    };

    backend.withAssetsData = function(assetsData) {
      this.getAssetsWithCallback = function (assetTypeId, boundingBox, callback) {
        callback(assetsData);
      };
      return this;
    };

    backend.withAssetData = function(assetData) {
      this.getAssetByExternalId = function (externalId, callback) {
        callback(assetData);
      };
      this.getAssetWithCallback = function(assetId, callback) {
        callback(assetData);
      };
      this.updateAsset = function (id, data, successCallback) {
        eventbus.trigger('asset:saving');
        successCallback(_.defaults(data, assetData));
      };
      return this;
    };

    backend.withSpeedLimitsData = function(speedLimitsData) {
      this.getSpeedLimits = function(boundingBox) {
        eventbus.trigger('speedLimits:fetched', speedLimitsData);
      };
      return this;
    };

    return backend;
  }

  initialize(backend);
}(window.Backend = window.Backend || {}));
