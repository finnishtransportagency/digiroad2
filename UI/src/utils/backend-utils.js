(function(backend) {
    var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

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

    backend.getRoadLinks = _.throttle(function(boundingBox) {
        jQuery.getJSON('api/roadlinks?bbox=' + boundingBox, function(roadLinks) {
            var data = _.map(roadLinks.features, function(feature) {
                var id = feature.properties.roadLinkId;
                var type = feature.properties.type;
                var coordinates = _.map(feature.geometry.coordinates, function(coordinate) {
                    return {x: coordinate[0], y: coordinate[1]};
                });
                return {roadLinkId: id, type: type, points: coordinates};
            });
            eventbus.trigger('roadLinks:fetched', data);
        });
    }, 1000);

    backend.getAsset = function(assetId, keepPosition) {
        $.get('api/assets/' + assetId, function(asset) {
            eventbus.trigger('asset:fetched', asset, keepPosition);
        });
    };
    
    backend.getIdFromExternalId = function(externalId, keepPosition) {
        $.get('api/assets/' + externalId + '?externalId=true', function(asset) {
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

    var assetUpdateFailed = function () {
        alert(assetUpdateFailedMessage);
        eventbus.trigger('asset:cancelled');
    };

    backend.createAsset = function(data) {
        eventbus.trigger('asset:creating');
        jQuery.ajax({
            contentType: "application/json",
            type: "POST",
            url: "api/assets",
            data: JSON.stringify(data),
            dataType: "json",
            success: function(asset) {
                eventbus.trigger('asset:created', asset);
            },
            error: assetUpdateFailed
        });
    };

    backend.updateAsset = function(id, data) {
        eventbus.trigger('asset:saving');
        jQuery.ajax({
            contentType: "application/json",
            type: "PUT",
            url: "api/assets/" + id,
            data: JSON.stringify(data),
            dataType:"json",
            success: function(asset) {
                eventbus.trigger('asset:saved asset:fetched', asset, true);
            },
            error: assetUpdateFailed
        });
    };

    // FIXME: Dummy implementation
    var id = 0;
    backend.createLinearAsset = function(startPosition, endPosition) {
        eventbus.trigger('linearAsset:created', {id: ++id, points: [startPosition, endPosition]});
    };
}(window.Backend = window.Backend || {}));
