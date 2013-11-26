(function(geometrycalculator, undefined){
    geometrycalculator.getDistanceFromLine = function(line, point) {
        var nearest_point = geometrycalculator.nearestPointOnLine(line, point);
        var dx = nearest_point.x - point.x;
        var dy = nearest_point.y - point.y;
        return Math.sqrt(dx * dx + dy * dy);
    };

    geometrycalculator.nearestPointOnLine = function(line, point) {
        var length_of_side_x = line.end.x - line.start.x;
        var length_of_side_y = line.end.y - line.start.y;
        var sum_of_squared_sides = length_of_side_x * length_of_side_x + length_of_side_y * length_of_side_y;

        var apx = point.x - line.start.x;
        var apy = point.y - line.start.y;
        var t = (apx * length_of_side_x + apy * length_of_side_y) / sum_of_squared_sides;
        t = Math.max(0, t);
        t = Math.min(1, t);
        return { x: line.start.x + length_of_side_x * t, y: line.start.y + length_of_side_y * t };
    };

    geometrycalculator.findNearestLine = function(features, x, y) {
        var calculatedistance = function(item){
            return _.merge(item, { distance: geometrycalculator.getDistanceFromLine(item, { x: x, y: y }) });
        };
        return _.chain(features)
                .map(fromFeatureVectorToLine)
                .flatten()
                .map(calculatedistance)
                .min('distance')
                .omit('distance')
                .value();
    };

    var fromFeatureVectorToLine = function(vector){
        var temp = [];
        for(var i = 0; i < vector.geometry.components.length - 1; i++) {
            var start_point = vector.geometry.components[i];
            var end_point = vector.geometry.components[i + 1];
            temp.unshift({ id: vector.id, start: start_point, end: end_point });
        }
        return temp;
    };
}(window.geometrycalculator = window.geometrycalculator || {}));