(function(root) {
  root.PointAssetsCollection = function(backend) {
    return {
      fetch: fetch,
      save: save
    };

    function fetch(boundingBox) {
      return backend.getPointAssets(boundingBox);
    }

    function save(current) {
      return backend.deletePointAsset(current);
    }
  };
})(this);