(function(backend) {
    backend.getEnumeratedPropertyValues = function (assetTypeId, success) {
        jQuery.getJSON('api/enumeratedPropertyValues/' + assetTypeId, function(enumeratedPropertyValues) {
            eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValues);
            success(enumeratedPropertyValues);
        })
        .fail(function() { console.log( "error" ); });
    };

    backend.putAssetPropertyValue = function (assetId, propertyId, data, success) {
        putAssetPropertyValue(assetId, propertyId, data, success);
    };

    backend.getAssets = function (assetTypeId, boundingBox, success) {
        jQuery.getJSON('api/assets?assetTypeId=' + assetTypeId + '&bbox=' + boundingBox, function(assets) {
            eventbus.trigger('assets:fetched', assets);
            success(assets);
        }).fail(function() {
            console.log("error");
        });
    };

    backend.getAsset = function (assetId, success) {
        $.get('api/assets/' + assetId, function(asset) {
            eventbus.trigger('asset:fetched', asset);
            success(asset);
        });
    };

    backend.getAssetTypeProperties = function (assetTypeId, success) {
        $.get('api/assetTypeProperties/' + assetTypeId, function(assetTypeProperties) {
            eventbus.trigger('assetTypeProperties:fetched', assetTypeProperties);
            success(assetTypeProperties);
        });
    };

    backend.createAsset = function(data, success) {
        jQuery.ajax({
            contentType: "application/json",
            type: "POST",
            url: "api/asset",
            data: JSON.stringify(data),
            dataType: "json",
            success: function(asset) {
                eventbus.trigger('asset:saved asset:fetched', asset);
                success(asset);
            },
            error: function () {
                console.log("error");
            }
        });
    };

    function putAssetPropertyValue(assetId, propertyId, data, success) {
        jQuery.ajax({
            contentType: "application/json",
            type: "PUT",
            url: "api/assets/" + assetId + "/properties/" + propertyId + "/values",
            data: JSON.stringify(data),
            dataType:"json",
            success: function(assetPropertyValue) {
              eventbus.trigger('assetPropertyValue:saved assetPropertyValue:fetched', assetPropertyValue);
              success(assetPropertyValue);
            },
            error: function() {
                console.log("error");
            }
        });
    }
}(window.Backend = window.Backend || {}));
