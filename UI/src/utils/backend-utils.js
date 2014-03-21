(function(backend) {
    backend.getEnumeratedPropertyValues = function (assetTypeId) {
        jQuery.getJSON('api/enumeratedPropertyValues/' + assetTypeId, function(enumeratedPropertyValues) {
            eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValues);
        })
        .fail(function() { console.log( "error" ); });
    };

    backend.putAssetPropertyValue = function (assetId, propertyId, data) {
        putAssetPropertyValue(assetId, propertyId, data);
    };

    backend.getAssets =  _.throttle(function(assetTypeId, boundingBox) {
        jQuery.getJSON('api/assets?assetTypeId=' + assetTypeId + '&bbox=' + boundingBox, function(assets) {
            eventbus.trigger('assets:fetched', assets);
        }).fail(function() {
            console.log("error");
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
            eventbus.trigger('asset:selected', {id: asset.id, externalId: asset.externalId}, keepPosition);
        });
    };

    backend.getAssetTypeProperties = function (assetTypeId) {
        $.get('api/assetTypeProperties/' + assetTypeId, function(assetTypeProperties) {
            eventbus.trigger('assetTypeProperties:fetched', assetTypeProperties);
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
    
    backend.updateAsset = function(id, data) {
        jQuery.ajax({
          contentType: "application/json",
          type: "PUT",
          url: "api/assets/" + id,
          data: JSON.stringify(data),
          dataType:"json",
          success: function(asset) {
              eventbus.trigger('asset:saved asset:fetched', asset);
          },
          error: function() {
              console.log("error");
          }
        });
    };

    function putAssetPropertyValue(assetId, propertyId, data) {
        jQuery.ajax({
            contentType: "application/json",
            type: "PUT",
            url: "api/assets/" + assetId + "/properties/" + propertyId + "/values",
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
}(window.Backend = window.Backend || {}));
