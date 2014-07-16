(function (backend) {
  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

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

    backend.getLinearAssets = _.throttle(function (boundingBox) {
      $.getJSON('api/linearassets?bbox=' + boundingBox, function (linearAssets) {
        eventbus.trigger('linearAssets:fetched', linearAssets);
      });
    }, 1000);

    backend.getRoadLinks = _.throttle(function (boundingBox) {
      $.getJSON('api/roadlinks?bbox=' + boundingBox, function (roadLinks) {
        eventbus.trigger('roadLinks:fetched', roadLinks);
      });
    }, 1000);

    backend.getAsset = function (assetId, keepPosition) {
      $.get('api/assets/' + assetId, function (asset) {
        eventbus.trigger('asset:fetched', asset, keepPosition);
      });
    };

    backend.getIdFromExternalId = function (externalId, keepPosition) {
      $.get('api/assets/' + externalId + '?externalId=true', function (asset) {
        eventbus.trigger('asset:fetched', asset, keepPosition);
      });
    };

    backend.getAssetTypeProperties = function (assetTypeId) {
      $.get('api/assetTypeProperties/' + assetTypeId, function (assetTypeProperties) {
        eventbus.trigger('assetTypeProperties:fetched', assetTypeProperties);
      });
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

    var assetUpdateFailed = function () {
      alert(assetUpdateFailedMessage);
      eventbus.trigger('asset:cancelled');
    };

    backend.createAsset = function (data) {
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
        error: assetUpdateFailed
      });
    };

    backend.updateAsset = function (id, data) {
      eventbus.trigger('asset:saving');
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/assets/" + id,
        data: JSON.stringify(data),
        dataType: "json",
        success: function (asset) {
          eventbus.trigger('asset:saved asset:fetched', asset, true);
        },
        error: assetUpdateFailed
      });
    };

    // FIXME: Dummy implementation
    var id = 0;
    backend.createLinearAsset = function (startPosition, endPosition) {
      eventbus.trigger('linearAsset:created', {id: ++id, points: [startPosition, endPosition]});
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

    return backend;
  }

  initialize(backend);
}(window.Backend = window.Backend || {}));
