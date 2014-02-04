(function(backend) {
    backend.putAssetPropertyValue = function (assetId, propertyId, data, success) {
        putAssetPropertyValue(assetId, propertyId, data, success);
    };

    backend.getAsset = function (assetId, success) {
        $.get('/api/assets/' + assetId, success);
    };

    function putAssetPropertyValue(assetId, propertyId, data, success) {
        jQuery.ajax({
            contentType: "application/json",
            type: "PUT",
            url: "/api/assets/" + assetId + "/properties/" + propertyId + "/values",
            data: JSON.stringify(data),
            dataType:"json",
            success: success,
            error: function() {
                console.log("error");
            }
        });
    }
}(window.Backend = window.Backend || {}));
