(function(root) {
  root.LinearAsset = function() {
    var offsetBySideCode = function(zoom, asset) {
      if (asset.sideCode === 1) {
        return asset;
      }
      asset.points = _.map(asset.points, function(point, index, geometry) {
        var baseOffset = -3.5;
        return new GeometryUtils().offsetPoint(point, index, geometry, asset.sideCode, baseOffset);
      });
      return asset;
    };

    return {
      offsetBySideCode: offsetBySideCode
    };
  };
})(this);
