(function(assetGrouping, undefined){

    var groupByDistance = function(items, result){
        // TODO: should be zoom level dependent
        var delta = 6;
        var findProximityStops = function (x) {
            return geometrycalculator.getDistanceBetweenPoints(x, item) < delta;
        };
        while(_.isEmpty(items) === false) {
            var item = _.first(items);
            var proximityStops = _.remove(items, findProximityStops);
            if (proximityStops.length === 1) {
                result.push(proximityStops[0]);
            } else {
                result.push(proximityStops);
            }
        }
        return result;
    };

    assetGrouping.groupByDistance = function(assets) {
        return groupByDistance(_.cloneDeep(assets), []);
    };

}(window.assetGrouping = window.assetGrouping || {}));