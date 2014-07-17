(function(root) {
  var assets = {};
  var validityPeriods = {
    current: true,
    future: false,
    past: false
  };
  var selectedValidityPeriods = function(validityPeriods) {
    return _.keys(_.pick(validityPeriods, function(selected) {
      return selected;
    }));
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
    destroyGroup: function(assetIds) {
      var destroyedAssets = _.pick(assets, assetIds);
      assets = _.omit(assets, assetIds);
      eventbus.trigger('assetGroup:destroyed', destroyedAssets);
    },
    destroyAssets: function() {
      assets = {};
    },
    selectValidityPeriod: function(validityPeriod, isSelected) {
      validityPeriods[validityPeriod] = isSelected;
      eventbus.trigger('validityPeriod:changed', selectedValidityPeriods(validityPeriods));
    },
    getValidityPeriods: function() {
      return validityPeriods;
    },
    selectedValidityPeriodsContain: function(validityPeriod) {
      return validityPeriods[validityPeriod];
    }
  };
  eventbus.on('map:moved', function(map) {
    if (zoomlevels.isInAssetZoomLevel(map.zoom)) {
      if (ApplicationModel.getSelectedLayer() === 'asset') {
        Backend.getAssetsWithCallback(10, map.bbox, function(assets) {
          eventbus.trigger('assets:updated', { assets: assets, assetsRegrouped: map.hasZoomLevelChanged });
        });
      }
    } else {
      if (selectedAssetModel.isDirty()) {
        eventbus.trigger('assetModifications:confirm');
      } else  {
        if (ApplicationModel.getSelectedLayer() === 'asset') {
          eventbus.trigger('assets:outOfZoom');
        }
      }
    }
  }, this);
})(this);
