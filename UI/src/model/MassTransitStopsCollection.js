(function(root) {
  root.MassTransitStopsCollection = function(backend) {
    var assets = {};
    var validityPeriods = {
      current: true,
      future: false,
      past: false
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
        if (mapMoveEvent.hasZoomLevelChanged) {
          eventbus.trigger('assets:all-updated massTransitStops:available', backendAssets);
        } else {
          eventbus.trigger('assets:new-fetched massTransitStops:available', filterNonExistingAssets(backendAssets, assets));
        }
      });
    };

    var refreshNormalAssets = function(mapMoveEvent) {
      backend.getAssetsWithCallback(mapMoveEvent.bbox, function(backendAssets) {
        var filteredAssets = _.where(backendAssets, {linkSource: 1});
        if (mapMoveEvent.hasZoomLevelChanged) {
          eventbus.trigger('assets:normal-updated massTransitStops:available', filteredAssets);
        } else {
          eventbus.trigger('assets:new-fetched massTransitStops:available', filterNonExistingAssets(filteredAssets, assets));
        }
      });
    };

    return {
      insertAsset: function(asset, assetId) {
        asset.data = _.merge(asset.data, {originalLon: asset.data.lon, originalLat: asset.data.lat } );
        assets[assetId] = asset;
      },
      getAsset: function(assetId) {
        return assets[assetId];
      },
      destroyAsset: function(assetId) {
        assets = _.omit(assets, assetId.toString());
      },
      getAssets: function() {
        return assets;
      },
      fetchAssets: function(boundingBox) {
        backend.getAssets(boundingBox);
      },
      fetchNormalAssets: function(boundingBox) {
        backend.getNormalAssets(boundingBox);
      },
      refreshAssets: refreshAssets,
      refreshNormalAssets: refreshNormalAssets,
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
      }
    };
  };
})(this);
