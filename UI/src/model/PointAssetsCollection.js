(function(root) {
  root.PointAssetsCollection = function(backend) {
    return {
      fetch: fetch
    };

    function fetch(boundingBox) {
      return backend.getPointAssets(boundingBox);
    }
  };
})(this);