(function (selectedAssetController){
    selectedAssetController.initialize = function(backend) {
        var usedKeysFromFetchedAsset = ['assetTypeId', 'bearing', 'lat', 'lon', 'roadLinkId'];
        var propertyData = [];
        var assetIsSaved = false;
        var assetHasBeenModified = false;
        var currentAsset = {};

        var reset = function() {
            assetIsSaved = false;
            assetHasBeenModified = false;
            propertyData = [];
        };

        eventbus.on('asset:unselected', function() {
            if(assetHasBeenModified && !assetIsSaved) {
                eventbus.once('confirm:ok', function() {
                    eventbus.once('asset:saved', function() { reset(); }, this);
                    backend.updateAsset(0, currentAsset);
                }, this);
                eventbus.trigger('confirm:show');
            }
        });
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
            currentAsset.properties = transformProperties(currentAsset.properties);
            propertyData = changedProperties.propertyData;
            assetHasBeenModified = true;
        });
        eventbus.on('asset:saved', function() {
            assetIsSaved = true;
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
            currentAsset = _.merge({}, _.pick(asset, usedKeysFromFetchedAsset), transformPropertyData(_.pick(asset, 'propertyData')));
        });

        return { reset: reset };
    };
})(window.SelectedAssetController = window.SelectedAssetController || {});