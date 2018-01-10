(function(root) {
  root.MassTransitStopsCollection = function(backend) {
    var assets = {};
    var isComplementaryActive = false;
    var validityPeriods = {
      current: true,
      future: false,
      past: false
    };

    var filterComplementaries = function(assets){
      if(isComplementaryActive)
        return assets;
      return _.filter(assets, function(asset) { return asset.linkSource != 2; });
    };

    var filterNonExistingAssets = function(assets, existingAssets) {
      return _.reject(assets, function(asset) {
        return _.has(existingAssets, asset.id.toString());
      });
    };

    var selectedValidityPeriods = function(validityPeriods) {
      return _.keys(_.pick(validityPeriods, function(selected) {
        return selected;
      }));
    };

    var refreshAssets = function(mapMoveEvent) {
      backend.getAssetsWithCallback(mapMoveEvent.bbox, function(backendAssets) {
        backendAssets = filterComplementaries(backendAssets);
        if (mapMoveEvent.hasZoomLevelChanged) {
          eventbus.trigger('assets:all-updated massTransitStops:available', backendAssets);
        } else {
          eventbus.trigger('assets:new-fetched massTransitStops:available', filterNonExistingAssets(backendAssets, assets));
        }
      });
    };

    var getAllTerminalNearestStops = function(properties) {
        return _.map(
            _.flatten(
              _.map(
                _.filter(properties, function(property){
                  return property.publicId == 'liitetyt_pysakit';
                }),
                function(property) {
                  return property.values;
                }
              )
            ),
            function(value) {
              return { id: parseInt(value.propertyValue), isChild: value.checked };
            }
        );
    };

    return {
      getAllTerminalNearestStops: getAllTerminalNearestStops,
      insertAsset: function(asset, assetId) {
        asset.data = _.merge(asset.data, {originalLon: asset.data.lon, originalLat: asset.data.lat } );
        assets[assetId] = asset;
      },
      getAsset: function(assetId) {
        return assets[assetId];
      },
      destroyAsset: function(assetId) {
        assets = _.omit(assets, ""+assetId);
      },
      getAssets: function() {
        if(isComplementaryActive)
            return assets;
        return _.filter(assets, function(asset){ return asset.data.linkSource == 1;});
      },
      getComplementaryAssets: function(){
        return _.reject(assets, function(asset){
          if(!isComplementaryActive)
            return asset.data.linkSource == 1;
          return true;
        });
      },
      fetchAssets: function(boundingBox) {
        backend.getAssets(boundingBox, function(assets){
          return filterComplementaries(assets);
        });
      },
      refreshAssets: refreshAssets,
      insertAssetsFromGroup: function(assetGroup) {
        _.each(assetGroup, function(asset) {
          asset.data = _.merge(asset.data, {originalLon: asset.data.lon, originalLat: asset.data.lat } );
          assets[asset.data.id.toString()] = asset;
        });
      },
      destroyGroup: function(assetIds) {
        var destroyedAssets = _.pick(assets, assetIds);
        assets = _.omit(assets, assetIds);
        eventbus.trigger('assetGroup:destroyed', destroyedAssets);
      },
      destroyAssets: function() {
        assets = {};
      },
      selectValidityPeriod: function(validityPeriod, isSelected) {
        if (validityPeriods[validityPeriod] !== isSelected) {
          validityPeriods[validityPeriod] = isSelected;
          eventbus.trigger('validityPeriod:changed', selectedValidityPeriods(validityPeriods));
        }
      },
      getValidityPeriods: function() {
        return validityPeriods;
      },
      selectedValidityPeriodsContain: function(validityPeriod) {
        return validityPeriods[validityPeriod];
      },
      activeComplementary: function(enable){
        isComplementaryActive = enable;
      },
      isComplementaryActive: function(){
        return isComplementaryActive;
      }
    };
  };
})(this);
