(function(root) {
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
  var bindEvents = function() {
    eventbus.on('map:moved', function(map) {
      if (zoomlevels.isInAssetZoomLevel(map.zoom)) {
        if (applicationModel.getSelectedLayer() === 'asset') {
          Backend.getAssetsWithCallback(10, map.bbox, function(backendAssets) {
            if (map.hasZoomLevelChanged) {
              eventbus.trigger('assets:all-updated', backendAssets);
            } else {
              eventbus.trigger('assets:new-fetched', filterNonExistingAssets(backendAssets, assets));
            }
          });
        }
      } else {
        if (selectedAssetModel.isDirty()) {
          eventbus.trigger('assetModifications:confirm');
        } else  {
          if (applicationModel.getSelectedLayer() === 'asset') {
            eventbus.trigger('assets:outOfZoom');
          }
        }
      }
    }, this);
  };
  root.AssetsModel = {
    insertAsset: function(asset, assetId) {
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
      Backend.getAssets(10, boundingBox);
    },
    insertAssetsFromGroup: function(assetGroup) {
      _.each(assetGroup, function(asset) {
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
    initialize: function() {
      this.destroyAssets();
      validityPeriods = {
        current: true,
        future: false,
        past: false
      };
      bindEvents();
    }
  };
})(this);
