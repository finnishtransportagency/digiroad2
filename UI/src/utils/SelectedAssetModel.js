(function(selectedAssetModel){
    selectedAssetModel.initialize = function(backend) {
        var usedKeysFromFetchedAsset = ['assetTypeId', 'bearing', 'lat', 'lon', 'roadLinkId'];
        var assetHasBeenModified = false;
        var currentAsset = {};
        var changedProps = [];

        var close = function() {
            assetHasBeenModified = false;
            currentAsset = {};
            changedProps = [];
            eventbus.trigger('asset:closed');
        };

        eventbus.on('tool:changed', function() {
            if (currentAsset.id) {
                backend.getAsset(currentAsset.id, true);
            }
        });

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
                changedProps = currentAsset.payload.properties;
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

        var cancel = function() {
          if (currentAsset.id) {
              backend.getAsset(currentAsset.id, true);
          }
          changedProps = [];
          assetHasBeenModified = false;
          eventbus.trigger('asset:cancelled');
        };

        eventbus.on('application:readOnly', function() {
           if (currentAsset.id) {
               backend.getAsset(currentAsset.id, true);
           }
        });

        eventbus.on('validityPeriod:changed', function(validityPeriods) {
            if (currentAsset && (!_.contains(validityPeriods, currentAsset.validityPeriod) &&
                                 currentAsset.validityPeriod !== undefined)) {
                close();
            }
        });

        eventbus.on('asset:saved asset:created', function() {
            changedProps = [];
            assetHasBeenModified = false;
        });
        eventbus.on('asset:created', function(asset) {
           currentAsset.id = asset.id;
        });

        var open = function(asset) {
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
            currentAsset.validityPeriod = asset.validityPeriod;
        };

        eventbus.on('asset:fetched', open, this);

        var save = function() {
            if (currentAsset.id === undefined){
                backend.createAsset(currentAsset.payload);
            } else {
                currentAsset.payload.id = currentAsset.id;
                backend.updateAsset(currentAsset.id, currentAsset.payload);
            }
        };

        var setProperty = function(publicId, values) {
            changedProps = _.reject(changedProps, function(x) {
                return x.publicId === publicId;
            });
            var propertyData = {publicId: publicId, values: values};
            changedProps.push(propertyData);
            currentAsset.payload.properties = changedProps;
            assetHasBeenModified = true;
            eventbus.trigger('assetPropertyValue:changed', {propertyData: propertyData, id : currentAsset.id});
        };

        var exists = function() {
            return !_.isEmpty(currentAsset);
        };

        var change = function(asset) {
          if (exists() && currentAsset.id !== asset.id && assetHasBeenModified) {
            return false;
          }
          if (exists()) {
            close();
          }
          open(asset);
          return true;
        };

        var getId = function() {
            return currentAsset.id;
        };

        return {
            close: close,
            save: save,
            isDirty: function() { return assetHasBeenModified; },
            setProperty: setProperty,
            cancel: cancel,
            exists: exists,
            change: change,
            getId: getId
        };
    };

})(window.SelectedAssetModel = window.SelectedAssetModel || {});
