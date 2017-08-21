(function (root) {
  root.AssetGrouping = function(assetGroupingDistance) {
    var doGroupingByDistance = function(items, zoomLevel) {
      var result = [];
      var item;
      var findProximityStops = function(x) {
        return geometrycalculator.getSquaredDistanceBetweenPoints(x, item) < assetGroupingDistance;
      };
      while (_.isEmpty(items) === false) {
        item = _.first(items);
        var proximityStops = _.remove(items, findProximityStops);
        result.push(proximityStops);
      }
      return result;
    };

    var groupByDistance = function(assets, zoomLevel) {
      return doGroupingByDistance(_.cloneDeep(assets), zoomLevel);
    };

    var findNearestAssetWithinGroupingDistance = function(uiAssets, backendAsset) {
      var calculateDistanceToBackendAsset = function(uiAsset) {
        return geometrycalculator.getSquaredDistanceBetweenPoints(uiAsset.data.group, backendAsset);
      };

      return _.chain(uiAssets)
        .filter(function(uiAsset) {
          return calculateDistanceToBackendAsset(uiAsset) < assetGroupingDistance;
        })
        .sortBy(calculateDistanceToBackendAsset)
        .head()
        .value();
    };

    return {
      groupByDistance: groupByDistance,
      findNearestAssetWithinGroupingDistance: findNearestAssetWithinGroupingDistance
    };
  };
})(this);
