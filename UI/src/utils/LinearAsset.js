(function(root) {
  root.LinearAsset = function(geometryUtils) {
    var offsetBySideCode = function(asset) {
      if (asset.sideCode === 1) {
        return asset;
      }
      asset.links = _.map(asset.links, function(link) {
        link.points = _.map(link.points, function(point, index, geometry) {
          return geometryUtils.offsetPoint(point, index, geometry, asset.sideCode);
        });
        return link;
      });
      return asset;
    };

    return {
      offsetBySideCode: offsetBySideCode
    };
  };
})(this);