(function(root) {
  root.PointAssetsCollection = function(backend, endPointName) {
    return {
      fetch: fetch
    };

    function fetch(boundingBox) {
      return backend.getPointAssets(boundingBox, endPointName);
    }
  };
})(this);