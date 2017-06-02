(function(geometrycalculator, undefined) {
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
    if (t < 0) { t = 0; }
    if (t > 1) { t = 1; }
    return { x: line.start.x + length_of_side_x * t, y: line.start.y + length_of_side_y * t };
  };

  geometrycalculator.findNearestLine = function(features, x, y) {
    var calculatedistance = function(item) {
      return geometrycalculator.getDistanceFromLine(item, { x: x, y: y });
    };
    var fromFeatureVectorToLine = function(vector) {
      var temp = {};
      var min_distance = 100000000.0;
      for (var i = 0; i < vector.points.length - 1; i++) {
        var start_point = vector.points[i];
        var end_point = vector.points[i + 1];
        var point_distance = calculatedistance({ start: start_point, end: end_point });
        if (point_distance < min_distance) {
          min_distance = point_distance;
          temp = {
            id: vector.roadLinkId,
            roadLinkId: vector.roadLinkId,
            linkId: vector.linkId,
            trafficDirection: vector.trafficDirection,
            start: start_point,
            end: end_point,
            distance: point_distance,
            linkSource: vector.linkSource
          };
        }
      }
      return temp;
    };

    return _.chain(features)
      .map(fromFeatureVectorToLine)
      .min('distance')
      .omit('distance')
      .value();
  };

  geometrycalculator.getSquaredDistanceBetweenPoints = function(pointA, pointB) {
    return Math.pow(pointA.lat - pointB.lat, 2) + Math.pow(pointA.lon - pointB.lon, 2);
  };

  geometrycalculator.getLineDirectionRadAngle = function(line) {
    return Math.atan2(line.start.x - line.end.x, line.start.y - line.end.y);
  };

  geometrycalculator.getLineDirectionDegAngle = function(line) {
    var rad = geometrycalculator.getLineDirectionRadAngle(line);
    return 180 + geometrycalculator.rad2deg(rad);
  };

  geometrycalculator.rad2deg = function(angleRad) {
    return angleRad * (180 / Math.PI);
  };

  geometrycalculator.deg2rad = function(angleDeg) {
    return angleDeg * (Math.PI / 180);
  };

  geometrycalculator.convertCompassToPolarCoordinates = function(angle) {
    return (angle - 90) % 360;
  };

  geometrycalculator.oppositeAngle = function(angle) {
    return (angle + 180) % 360;
  };

  geometrycalculator.oppositeAngleRadius = function(angle) {
      return angle + Math.PI;
  };

  //bounds corresponds to [-548576, 6291456, 1548576, 8388608]
  geometrycalculator.isInBounds = function(bounds, x, y) {
      return (x > bounds[0] && x < bounds[2] && y > bounds[1] && y < bounds[3]);
  };

  geometrycalculator.getCentroid = function(points) {
    var sums = _.foldl(points, function(sum, point) {
      return { lat: point.lat + sum.lat, lon: point.lon + sum.lon };
    }, { lat: 0, lon: 0 });

    return { lon: (sums.lon / points.length), lat: (sums.lat / points.length)};
  };

}(window.geometrycalculator = window.geometrycalculator || {}));
