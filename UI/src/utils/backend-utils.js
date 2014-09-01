(function (root) {
  root.Backend = function() {
    var self = this;
    this.getEnumeratedPropertyValues = function (assetTypeId) {
      $.getJSON('api/enumeratedPropertyValues/' + assetTypeId, function (enumeratedPropertyValues) {
        eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValues);
      })
        .fail(function () {
          console.log("error");
        });
    };

    this.getRoadLinks = _.throttle(function(boundingBox, callback) {
      $.getJSON('api/roadlinks?bbox=' + boundingBox, function(data) {
        callback(data);
        eventbus.trigger('roadLinks:fetched', data);
      });
    }, 1000);

    this.getAssets = function (assetTypeId, boundingBox) {
      self.getAssetsWithCallback(assetTypeId, boundingBox, function (assets) {
        eventbus.trigger('assets:fetched', assets);
      });
    };

    this.getAssetsWithCallback = _.throttle(function(assetTypeId, boundingBox, callback) {
      $.getJSON('api/assets?assetTypeId=' + assetTypeId + '&bbox=' + boundingBox, callback)
        .fail(function() { console.log("error"); });
    }, 1000);

    this.getSpeedLimits = _.throttle(function (boundingBox, callback) {
      $.getJSON('api/speedlimits?bbox=' + boundingBox, function (speedLimits) {
        callback(speedLimits);
      });
    }, 1000);

    this.getSpeedLimit = _.throttle(function(id, callback) {
      $.getJSON('api/speedlimits/' + id, function(speedLimit) {
        callback(speedLimit);
      });
    }, 1000);

    this.getAsset = function (assetId) {
      self.getAssetWithCallback(assetId, function (asset) {
        eventbus.trigger('asset:fetched', asset);
      });
    };

    this.getAssetWithCallback = function(assetId, callback) {
      $.get('api/assets/' + assetId, callback);
    };

    this.getAssetByExternalId = function (externalId, callback) {
      $.get('api/assets/' + externalId + '?externalId=true', callback);
    };

    this.getAssetTypeProperties = function (assetTypeId, callback) {
      $.get('api/assetTypeProperties/' + assetTypeId, callback);
    };

    this.getUserRoles = function () {
      $.get('api/user/roles', function (roles) {
        eventbus.trigger('roles:fetched', roles);
      });
    };

    this.getApplicationSetup = function() {
      $.getJSON('full_appsetup.json', function(setup) {
        eventbus.trigger('applicationSetup:fetched', setup);
      });
    };

    this.getConfiguration = function(selectedAsset) {
      var url = 'api/config' + (selectedAsset && selectedAsset.externalId ? '?externalAssetId=' + selectedAsset.externalId : '');
      $.getJSON(url, function(config) {
        eventbus.trigger('configuration:fetched', config);
      });
    };

    this.getAssetPropertyNames = function() {
      $.getJSON('api/assetPropertyNames/fi', function(propertyNames) {
        eventbus.trigger('assetPropertyNames:fetched', propertyNames);
      });
    };

    this.createAsset = function (data, errorCallback) {
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

    this.updateAsset = function (id, data, successCallback, errorCallback) {
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
    this.createSpeedLimit = function (startPosition, endPosition) {
      eventbus.trigger('speedLimit:created', {id: ++id, points: [startPosition, endPosition]});
    };

    this.withRoadLinkData = function (roadLinkData) {
      self.getRoadLinks = function (boundingBox, callback) {
        callback(roadLinkData);
        eventbus.trigger('roadLinks:fetched', roadLinkData);
      };
      return self;
    };

    this.withUserRolesData = function(userRolesData) {
      self.getUserRoles = function () {
        eventbus.trigger('roles:fetched', userRolesData);
      };
      return self;
    };

    this.withEnumeratedPropertyValues = function(enumeratedPropertyValuesData) {
      self.getEnumeratedPropertyValues = function () {
        eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValuesData);
      };
      return self;
    };

    this.withApplicationSetupData = function(applicationSetupData) {
      self.getApplicationSetup = function () {
        eventbus.trigger('applicationSetup:fetched', applicationSetupData);
      };
      return self;
    };

    this.withConfigurationData = function(configurationData) {
      self.getConfiguration = function () {
        eventbus.trigger('configuration:fetched', configurationData);
      };
      return self;
    };

    this.withAssetPropertyNamesData = function(assetPropertyNamesData) {
      self.getAssetPropertyNames = function () {
        eventbus.trigger('assetPropertyNames:fetched', assetPropertyNamesData);
      };
      return self;
    };

    this.withAssetsData = function(assetsData) {
      self.getAssetsWithCallback = function (assetTypeId, boundingBox, callback) {
        callback(assetsData);
      };
      return self;
    };

    this.withAssetData = function(assetData) {
      self.getAssetByExternalId = function (externalId, callback) {
        callback(assetData);
      };
      self.getAssetWithCallback = function(assetId, callback) {
        callback(assetData);
      };
      self.updateAsset = function (id, data, successCallback) {
        eventbus.trigger('asset:saving');
        successCallback(_.defaults(data, assetData));
      };
      return self;
    };

    this.withSpeedLimitsData = function(speedLimitsData) {
      self.getSpeedLimits = function(boundingBox, callback) {
        callback(speedLimitsData);
      };
      return self;
    };

    this.withSpeedLimitConstructor = function(speedLimitConstructor) {
      self.getSpeedLimit = function(id, callback) {
        callback(speedLimitConstructor(id));
      };
      return self;
    };

    this.withPassThroughAssetCreation = function() {
      self.createAsset = function(data) {
        eventbus.trigger('asset:created', data);
      };
      return self;
    };

    this.withAssetCreationTransformation = function(transformation) {
      self.createAsset = function(data) {
        eventbus.trigger('asset:created', transformation(data));
      };
      return self;
    };

    this.withAssetTypePropertiesData = function(assetTypePropertiesData) {
      self.getAssetTypeProperties = function(assetTypeId, callback) {
        callback(assetTypePropertiesData);
      };
      return self;
    };
  };
}(this));
