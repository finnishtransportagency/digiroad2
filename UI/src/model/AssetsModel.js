(function(root) {
  var assets = {};
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
      assets = _.omit(assets, assetIds);
    }
  }
})(this);
