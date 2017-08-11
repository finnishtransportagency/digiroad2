(function(root) {
  root.PointAssetsCollection = function(backend, endPointName) {
    var isComplementaryActive = false;
    var me = this;

    this.filterComplementaries = function (assets) {
      if(isComplementaryActive)
        return assets;
      return _.where(assets, {linkSource: 1});
    };

    this.fetch = function(boundingBox) {
      return backend.getPointAssetsWithComplementary(boundingBox, endPointName)
        .then(function(assets) {
          eventbus.trigger('pointAssets:fetched');
          return me.filterComplementaries(assets);
        });
    };

    this.activeComplementary = function(enable) {
      isComplementaryActive = enable;
    };

    this.complementaryIsActive= function() {
      return isComplementaryActive;
    };

    this.setTrafficSigns = function(){};

  };
})(this);