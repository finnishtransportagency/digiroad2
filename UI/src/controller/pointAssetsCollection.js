(function(root) {
  root.PointAssetsCollection = function(backend, specs, verificationCollection) {
    var isComplementaryActive = false;
    var isAllowComplementary = false;
    var me = this;

    this.filterComplementaries = function (assets) {
      if(isComplementaryActive || !isAllowComplementary )
        return assets;
      return _.filter(assets, function(asset) { return asset.linkSource != 2; });
    };

    this.fetch = function(boundingBox, center) {
      return backend.getPointAssetsWithComplementary(boundingBox, specs.layerName)
        .then(function(assets) {
          eventbus.trigger('pointAssets:fetched');
          me.allowComplementaryIsActive(specs.allowComplementaryLinks);
          verificationCollection.fetch(boundingBox, center, specs.typeId, specs.hasMunicipalityValidation);
          return me.filterComplementaries(assets);
        });
    };

     this.allowComplementaryIsActive = function(allowComplementary) {
      isAllowComplementary = allowComplementary;
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