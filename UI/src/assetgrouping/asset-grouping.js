(function (assetGrouping, undefined) {
  var groupByDistance = function (items, zoomLevel) {
    var result = [];
    // TODO: Take zoom-level dependent grouping distance in use when group visualization has been improved
//    var delta = Math.pow((zoomlevels.maxZoomLevel + 1) - zoomLevel, 2) * 6;
    var delta = 6;
    var item;
    var findProximityStops = function (x) {
      return geometrycalculator.getSquaredDistanceBetweenPoints(x, item) < delta * delta;
    };
    while (_.isEmpty(items) === false) {
      item = _.first(items);
      var proximityStops = _.remove(items, findProximityStops);
      result.push(proximityStops);
    }
    return result;
  };

  assetGrouping.groupByDistance = function (assets, zoomLevel) {
    return groupByDistance(_.cloneDeep(assets), zoomLevel);
  };

}(window.assetGrouping = window.assetGrouping || {}));
