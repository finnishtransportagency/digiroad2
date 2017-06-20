(function(root) {
  root.PointAssetsCollection = function(backend, endPointName) {
    var isComplementaryActive = false;

    var filterComplementaries = function (assets) {
      if(isComplementaryActive)
        return assets;
      return _.where(assets, {linkSource: 1});
    };

    function fetch(boundingBox) {
      return backend.getPointAssetsWithComplementary(boundingBox, endPointName)
        .then(function(assets) {
          eventbus.trigger('pointAssets:fetched');
          return filterComplementaries(assets);
        });
    }

    function activeComplementary(enable) {
      isComplementaryActive = enable;
    }

    function complementaryIsActive() {
      return isComplementaryActive;
    }

    return {
      fetch: fetch,
      activeComplementary : activeComplementary,
      complementaryIsActive : complementaryIsActive
    };
  };
})(this);