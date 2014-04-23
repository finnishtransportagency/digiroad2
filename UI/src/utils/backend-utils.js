(function(backend) {
    backend.getEnumeratedPropertyValues = function (assetTypeId) {
        jQuery.getJSON('api/enumeratedPropertyValues/' + assetTypeId, function(enumeratedPropertyValues) {
            eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValues);
        })
        .fail(function() { console.log( "error" ); });
    };

    backend.putAssetPropertyValue = function (assetId, propertyPublicId, data) {
        putAssetPropertyValue(assetId, propertyPublicId, data);
    };

    backend.getAssets =  _.throttle(function(assetTypeId, boundingBox) {
        jQuery.getJSON('api/assets?assetTypeId=' + assetTypeId + '&bbox=' + boundingBox, function(assets) {
            eventbus.trigger('assets:fetched', assets);
        }).fail(function() {
            console.log("error");
        });
    }, 1000);

    backend.getLinearAssets = _.throttle(function(boundingBox) {
        jQuery.getJSON('api/linearassets?bbox=' + boundingBox, function(linearAssets) {
            eventbus.trigger('linearAssets:fetched', linearAssets);
        });
    }, 1000);

    backend.getAsset = function (assetId) {
        $.get('api/assets/' + assetId, function(asset) {
            eventbus.trigger('asset:fetched', asset);
        });
    };
    
    backend.getIdFromExternalId = function(externalId, keepPosition) {
        $.get('api/assets/' + externalId + '?externalId=true', function(asset) {
            eventbus.trigger('asset:unselected');
            eventbus.trigger('asset:fetched', asset, keepPosition);
        });
    };

    backend.getAssetTypeProperties = function (assetTypeId) {
        $.get('api/assetTypeProperties/' + assetTypeId, function(assetTypeProperties) {
            eventbus.trigger('assetTypeProperties:fetched', assetTypeProperties);
        });
    };

    backend.getUserRoles = function () {
        $.get('api/user/roles', function(roles) {
            eventbus.trigger('roles:fetched', roles);
        });
    };

    backend.createAsset = function(data) {
        jQuery.ajax({
            contentType: "application/json",
            type: "POST",
            url: "api/asset",
            data: JSON.stringify(data),
            dataType: "json",
            success: function(asset) {
                eventbus.trigger('asset:created', asset);
            },
            error: function () {
                console.log("error");
            }
        });
    };
    
    backend.updateAssetPosition = function(id, data) {
        jQuery.ajax({
          contentType: "application/json",
          type: "PUT",
          url: "api/assets/" + id + "?positionOnly=true",
          data: JSON.stringify(data),
          dataType:"json",
          success: function(asset) {
              eventbus.trigger('asset:saved asset:fetched', asset, true);
          },
          error: function() {
              console.log("error");
          }
        });
    };

    backend.updateAssetProperties = function(id, data) {
        jQuery.ajax({
            contentType: "application/json",
            type: "PUT",
            url: "api/assets/" + id + "?propertiesOnly=true",
            data: JSON.stringify(data),
            dataType:"json",
            success: function(asset) {
                eventbus.trigger('asset:saved asset:fetched', asset, true);
            },
            error: function() {
                console.log("error");
            }
        });
    };

    backend.updateAsset = function(id, data) {
        jQuery.ajax({
            contentType: "application/json",
            type: "PUT",
            url: "api/assets/" + id,
            data: JSON.stringify(data),
            dataType:"json",
            success: function(asset) {
                eventbus.trigger('asset:saved asset:fetched', asset, true);
            },
            error: function() {
                console.log("error");
            }
        });
    };

    function putAssetPropertyValue(assetId, propertyPublicId, data) {
        jQuery.ajax({
            contentType: "application/json",
            type: "PUT",
            url: "api/assets/" + assetId + "/properties/" + propertyPublicId + "/values",
            data: JSON.stringify(data),
            dataType:"json",
            success: function(assetPropertyValue) {
              eventbus.trigger('assetPropertyValue:saved assetPropertyValue:fetched', assetPropertyValue);
            },
            error: function() {
                console.log("error");
            }
        });
    }

    // FIXME: Dummy implementation
    var id = 0;
    backend.createLinearAsset = function(startPosition, endPosition) {
        eventbus.trigger('linearAsset:created', {id: ++id, points: [startPosition, endPosition]});
    };
}(window.Backend = window.Backend || {}));
