(function(backend) {
    var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

    function initialize(backend) {
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
                var groupedAssets = assetGrouping.groupByDistance(assets);
                eventbus.trigger('assets:fetched', groupedAssets);
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
                eventbus.trigger('roadLinks:fetched', roadLinks);
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

        backend.withRoadLinkData = function(roadLinkData) {
            var ret = {};
            initialize(ret);
            ret.getRoadLinks = function() { eventbus.trigger('roadLinks:fetched', roadLinkData); };
            return ret;
        };
    }

    initialize(backend);
}(window.Backend = window.Backend || {}));
