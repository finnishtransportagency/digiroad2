(function(geometrycalculator, undefined){
    // x1, y1 line start
    // x2, y2 line end
    // x3, y3 point for distance calculation
    geometrycalculator.getDistanceFromLine = function(x1, y1, x2, y2, x3, y3) {
        var px = x2-x1;
        var py = y2-y1;

        var something = px*px + py*py;

        var u =  ((x3 - x1) * px + (y3 - y1) * py) / something;

        if (u > 1) {
            u = 1;
        } else if (u < 0) {
            u = 0;
        }

        var x = x1 + u * px;
        var y = y1 + u * py;

        var dx = x - x3;
        var dy = y - y3;

        var dist = Math.sqrt(dx*dx + dy*dy);

        return dist;
    };
}(window.geometrycalculator = window.geometrycalculator || {}));