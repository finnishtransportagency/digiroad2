(function (selectedAssetController){
    selectedAssetController.initialize = function(backend) {
        var usedKeysFromFetchedAsset = ['assetTypeId', 'bearing', 'lat', 'lon', 'roadLinkId'];
        var assetHasBeenModified = false;
        var currentAsset = {};

        var reset = function() {
            assetHasBeenModified = false;
            currentAsset = {};
        };

        eventbus.on('asset:moved', function() {
            assetHasBeenModified = true;
        });
        eventbus.on('assetPropertyValue:changed', function(changedProperties) {
            var transformProperties = function(properties) {
              return _.map(properties, function(property) {
                  var changedProperty = _.find(changedProperties.propertyData, function(p) { return p.publicId === property.publicId; });
                  if(changedProperty) {
                      return _.merge({}, property, _.pick(changedProperty, 'values'));
                  } else {
                      return property;
                  }
              });
            };
            currentAsset.payload.properties = transformProperties(currentAsset.payload.properties);
            assetHasBeenModified = true;
        });

        eventbus.on('asset:save', function(){
            backend.updateAsset(currentAsset.id, currentAsset.payload);
        });

        eventbus.on('asset:cancelled', function(){
           backend.getAsset(currentAsset.id);
        });

        eventbus.on('assetTypeProperties:fetched', function(properties) {
            //currentAsset.payload = properties;
            eventbus.trigger('asset:fetched', properties, this);
        }, this);

        eventbus.on('asset:saved asset:created asset:cancelled', function() {
            assetHasBeenModified = false;
        });
        eventbus.on('asset:fetched', function(asset) {
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

        return { reset: reset,
                 isDirty: function() { return assetHasBeenModified; }};
    };

})(window.SelectedAssetController = window.SelectedAssetController || {});