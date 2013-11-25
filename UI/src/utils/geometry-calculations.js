(function(geometrycalculator, undefined){
    geometrycalculator.getDistanceFromLine = function(line, point) {
        var px = line.end.x - line.start.x;
        var py = line.end.y - line.start.y;

        var squaredDistance = px * px + py * py;

        var u = ((point.x - line.start.x) * px + (point.y - line.start.y) * py) / squaredDistance;
        u = Math.min(1, u);
        u = Math.max(0, u);

        var x = line.start.x + u * px;
        var y = line.start.y + u * py;

        var dx = x - point.x;
        var dy = y - point.y;
        return Math.sqrt(dx * dx + dy * dy);
    };
}(window.geometrycalculator = window.geometrycalculator || {}));