(function(root) {
  var assets = {};
  var validityPeriods = {
    current: true,
    future: false,
    past: false
  };
  root.AssetsModel = {
    insertAsset: function(asset, assetId) {
      assets[assetId] = asset;
    },
    getAsset: function(assetId) {
      return assets[assetId];
    },
    getAssets: function() {
      return assets;
    },
    destroyGroup: function(assetIds) {
      var destroyedAssets = _.pick(assets, assetIds);
      assets = _.omit(assets, assetIds);
      eventbus.trigger('assetGroup:destroyed', destroyedAssets);
    },
    selectValidityPeriod: function(validityPeriod, isSelected) {
      validityPeriods[validityPeriod] = isSelected;
    },
    getValidityPeriods: function() {
      return validityPeriods;
    }
  };

})(this);
