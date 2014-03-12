(function(backend) {
    backend.getEnumeratedPropertyValues = function (assetTypeId, success) {
        jQuery.getJSON('api/enumeratedPropertyValues/' + assetTypeId, success)
            .fail(function() { console.log( "error" ); });
    };

    backend.putAssetPropertyValue = function (assetId, propertyId, data, success) {
        putAssetPropertyValue(assetId, propertyId, data, success);
    };

    backend.getAssets = function (assetTypeId, boundingBox, success) {
        jQuery.getJSON('api/assets?assetTypeId=' + assetTypeId + '&bbox=' + boundingBox, success)
            .fail(function () {
                console.log("error");
            });
    };

    backend.getAsset = function (assetId, success) {
        $.get('api/assets/' + assetId, function(asset) {
            eventbus.trigger('asset:fetched', asset);
            success();
        });
    };

    backend.getAssetTypeProperties = function (assetTypeId, success) {
        $.get('api/assetTypeProperties/' + assetTypeId, success);
    };

    backend.createAsset = function(data, success) {
        jQuery.ajax({
            contentType: "application/json",
            type: "POST",
            url: "api/asset",
            data: JSON.stringify(data),
            dataType: "json",
            success: success,
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
            success: success,
            error: function() {
                console.log("error");
            }
        });
    }
}(window.Backend = window.Backend || {}));
