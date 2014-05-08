(function (selectedAssetController){
    selectedAssetController.initialize = function(backend) {
        var usedKeysFromFetchedAsset = ['assetTypeId', 'bearing', 'lat', 'lon', 'roadLinkId'];
        var assetHasBeenModified = false;
        var currentAsset = {};
        var changedProps = [];

        var reset = function() {
            assetHasBeenModified = false;
            currentAsset = {};
            changedProps = [];
        };

        eventbus.on('asset:placed', function(asset) {
            currentAsset = asset;

            // TODO: copy paste
            var transformPropertyData = function(propertyData) {
                var transformValues = function(publicId, values) {
                    var transformValue = function(value) {
                        return {
                            propertyValue: value.propertyValue,
                            propertyDisplayValue: publicId.publicId
                        };
                    };

                    return _.map(values.values, transformValue);
                };
                var transformProperty = function(property) {
                    return _.merge(
                        {},
                        _.pick(property, 'publicId'),
                        {
                            values: transformValues(_.pick(property, 'publicId'), _.pick(property, 'values'))
                        });
                };
                return {
                    properties: _.map(propertyData.propertyData, transformProperty)
                };
            };
            eventbus.once('assetTypeProperties:fetched', function(properties) {
                currentAsset.propertyData = properties;
                currentAsset.payload = _.merge({ assetTypeId: 10 }, _.pick(currentAsset, usedKeysFromFetchedAsset), transformPropertyData(_.pick(currentAsset, 'propertyData')));
                eventbus.trigger('asset:initialized', currentAsset);
            });
            backend.getAssetTypeProperties(10);
        }, this);

        eventbus.on('asset:moved', function(position) {
            currentAsset.payload.bearing = position.bearing;
            currentAsset.payload.lon = position.lon;
            currentAsset.payload.lat = position.lat;
            currentAsset.payload.roadLinkId = position.roadLinkId;
            assetHasBeenModified = true;
        });

        eventbus.on('assetPropertyValue:changed', function(changedProperty) {
            changedProps = _.reject(changedProps, function(x){
                return x[0].publicId === changedProperty.propertyData[0].publicId;
            });
            changedProps.push(changedProperty.propertyData);
            currentAsset.payload.properties = changedProps;
            assetHasBeenModified = true;
        });

        eventbus.on('asset:cancelled', function(){
           backend.getAsset(currentAsset.id);
        });

        eventbus.on('asset:saved asset:created asset:cancelled', function() {
            changedProps = [];
            assetHasBeenModified = false;
        });

        eventbus.on('asset:fetched', function(asset) {
            // TODO: copy paste
            var transformPropertyData = function(propertyData) {
                var transformValues = function(publicId, values) {
                    var transformValue = function(value) {
                        return {
                            propertyValue: value.propertyValue,
                            propertyDisplayValue: publicId.publicId
                        };
                    };
                    return _.map(values.values, transformValue);
                };
                var transformProperty = function(property) {
                    return _.merge(
                        {},
                        _.pick(property, 'publicId'),
                        {
                            values: transformValues(_.pick(property, 'publicId'), _.pick(property, 'values'))
                        });
                };
                return {
                    properties: _.map(propertyData.propertyData, transformProperty)
                };
            };
            currentAsset.id = asset.id;
            currentAsset.payload = _.merge({}, _.pick(asset, usedKeysFromFetchedAsset), transformPropertyData(_.pick(asset, 'propertyData')));
        });

        var save = function() {
            if(currentAsset.id === undefined){
                backend.createAsset(currentAsset.payload);
            } else {
                currentAsset.payload.id = currentAsset.id;
                backend.updateAsset(currentAsset.id, currentAsset.payload);
            }
        };

        return { reset: reset,
                 save: save,
                 isDirty: function() { return assetHasBeenModified; }};
    };

})(window.SelectedAssetController = window.SelectedAssetController || {});