(function(root) {
  root.LinearAsset = function(geometryUtils) {
    var offsetBySideCode = function(zoom, asset) {
      if (asset.sideCode === 1) {
        return asset;
      }
      asset.links = _.map(asset.links, function(link) {
        link.originalPoints = _.cloneDeep(link.points);
        var baseOffset = zoom <= 12 ? -3.5 : -0.5;
        link.points = _.map(link.points, function(point, index, geometry) {
          return geometryUtils.offsetPoint(point, index, geometry, asset.sideCode, baseOffset);
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
