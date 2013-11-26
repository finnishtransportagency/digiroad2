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
}(window.geometrycalculator = window.geometrycalculator || {}));