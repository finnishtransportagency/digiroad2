(function (root) {
  var subtractVector = function (vector1, vector2) {
    return {x: vector1.x - vector2.x, y: vector1.y - vector2.y};
  };

  var scaleVector = function (vector, scalar) {
    return {x: vector.x * scalar, y: vector.y * scalar};
  };
  var sumVectors = function (vector1, vector2) {
    return {x: vector1.x + vector2.x, y: vector1.y + vector2.y};
  };
  var normalVector = function (vector) {
    return {x: vector.y, y: -vector.x};
  };
  var unitVector = function (vector) {
    var n = vectorLength(vector);
    return {x: vector.x / n, y: vector.y / n};
  };

  var vectorLength = function (vector) {
    return Math.sqrt(Math.pow(vector.x, 2) + Math.pow(vector.y, 2));
  };

  var segmentsOfLineString = function (lineString, point) {
    return _.reduce(lineString.getCoordinates(), function (acc, vertex, index, vertices) {
      if(index === 0)
        return acc;

      var previousVertex = vertices[index - 1];
      var segmentGeometry = new ol.geom.LineString([previousVertex, vertex]);
      var distanceObject = distanceToPoint(segmentGeometry, [point.x, point.y]);
      var segment = {
        distance: distanceObject.distance,
        splitPoint: {
          x: distanceObject.x0,
          y: distanceObject.y0
        },
        index: index - 1
      };
      return acc.concat([segment]);

    }, []);
  };

  root.calculateMeasureAtPoint = function (lineString, point) {
    var segments = segmentsOfLineString(lineString, point);
    var splitSegment = _.head(_.sortBy(segments, 'distance'));
    var split = _.reduce(lineString.getCoordinates(), function (acc, vertex, index) {
      var convertedVertex = { x: vertex[0] , y: vertex[1] };
      if (acc.firstSplit) {
        if (acc.previousVertex) {
          acc.splitMeasure = acc.splitMeasure + vectorLength(subtractVector(acc.previousVertex, convertedVertex));
        }
        if (index === splitSegment.index) {
          acc.splitMeasure = acc.splitMeasure + vectorLength(subtractVector(convertedVertex, splitSegment.splitPoint));
          acc.firstSplit = false;
        }
        acc.previousVertex = convertedVertex;
      }
      return acc;
    }, {
      firstSplit: true,
      previousVertex: null,
      splitMeasure: 0.0
    });
    return split.splitMeasure;
  };

  root.offsetBySideCode = function (zoom, asset) {
    asset.points = _.map(asset.points, function (point, index, geometry) {
      var baseOffset = -3.5;
      return root.offsetPoint(point, index, geometry, asset.sideCode, baseOffset);
    });
    return asset;
  };

  var distanceToSegment  = function (coordinate, segment) {
    var x0 = coordinate[0];
    var y0 = coordinate[1];
    var start = segment[0];
    var end = segment[1];
    var x1 = start[0];
    var y1 = start[1];
    var x2 = end[0];
    var y2 = end[1];
    var dx = x2 - x1;
    var dy = y2 - y1;
    var along = (dx === 0 && dy === 0) ? 0 : ((dx * (x0 - x1)) + (dy * (y0 - y1))) /
    (Math.pow(dx, 2) + Math.pow(dy, 2));
    var x, y;
    if (along <= 0) {
      x = x1;
      y = y1;
    } else if (along >= 1) {
      x = x2;
      y = y2;
    } else {
      x = x1 + along * dx;
      y = y1 + along * dy;
    }
    return{
      distance: Math.sqrt(Math.pow(x - x0, 2) + Math.pow(y - y0, 2)),
      x: x, y: y,
      along: along
    };
  };

  var distanceToPoint = function (geometry, point) {
    var result, best = {};
    var min = Number.POSITIVE_INFINITY;
    var i = 0;
    geometry.forEachSegment(function (segPoint1, segPoint2) {
      result = distanceToSegment(point, [segPoint1, segPoint2]);
      if(result.distance < min) {
        min = result.distance;
        best = {
          distance: min,
          x0: result.x, y0: result.y,
          x1: point[0], y1: point[1],
          index: i
        };
        if(min === 0) {
          return best;
        }
      }
      i++;
    });
    return best;

  };

  root.splitByPoint = function (lineString, point) {
    var segments = segmentsOfLineString(lineString, point);
    var splitSegment = _.head(_.sortBy(segments, 'distance'));
    var split = _.reduce(lineString.getCoordinates(), function (acc, vertex, index) {
      if (acc.firstSplit) {
        acc.firstSplitVertices.push({x: vertex[0], y: vertex[1]});
        if (index === splitSegment.index) {
          acc.firstSplitVertices.push({x: splitSegment.splitPoint.x, y: splitSegment.splitPoint.y});
          acc.secondSplitVertices.push({x: splitSegment.splitPoint.x, y: splitSegment.splitPoint.y});
          acc.firstSplit = false;
        }
      } else {
        acc.secondSplitVertices.push({x: vertex[0], y: vertex[1]});
      }
      return acc;
    }, {
      firstSplit: true,
      firstSplitVertices: [],
      secondSplitVertices: []
    });
    return _.pick(split, 'firstSplitVertices', 'secondSplitVertices');
  };

  root.offsetPoint = function (point, index, geometry, sideCode, baseOffset) {
    var previousPoint = index > 0 ? geometry[index - 1] : point;
    var nextPoint = geometry[index + 1] || point;

    var directionVector = scaleVector(sumVectors(subtractVector(point, previousPoint), subtractVector(nextPoint, point)), 0.5);
    var normal = normalVector(directionVector);
    var sideCodeScalar = (2 * sideCode - 5) * baseOffset;
    var offset = scaleVector(unitVector(normal), sideCodeScalar);
    return sumVectors(point, offset);
  };

  var distanceOfPoints = function (end, start) {
    return Math.sqrt(Math.pow(end[0] - start[0], 2) + Math.pow(end[1] - start[1], 2));
  };
  root.distanceOfPoints = distanceOfPoints;

  var distanceBetweenPoints = function (end, start) {
    return Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
  };
  root.distanceBetweenPoints = distanceBetweenPoints;

  var vectorialDistanceOfPoints = function (end, start) {
    return Math.sqrt(Math.pow(end[0] - start[0], 2) + Math.pow(end[1] - start[1], 2));
  };
  root.vectorialDistanceOfPoints = vectorialDistanceOfPoints;

  var radiansToDegrees = function (radians) {
    return radians * (180 / Math.PI);
  };

  var calculateAngleFromNorth = function (vector) {
    var v = unitVector(vector);
    var rad = ((Math.PI * 2) - (Math.atan2(v.y, v.x) + Math.PI)) + (3 * Math.PI / 2);
    var ret = rad > (Math.PI * 2) ? rad - (Math.PI * 2) : rad;
    return radiansToDegrees(ret);
  };

  var arePointsAdjacent = function(point1, point2){
    var epsilon = 0.01;
    return distanceBetweenPoints(point1, point2) <= epsilon;
  };

  root.calculateMidpointOfLineString = function (lineString) {
    var length = lineString.getLength();
    var vertices = lineString.getCoordinates();
    var firstVertex = _.first(vertices);
    var optionalMidpoint = _.reduce(_.tail(vertices), function (acc, vertex) {
      if (acc.midpoint) return acc;
      var distance = vectorialDistanceOfPoints(vertex, acc.previousVertex);
      var accumulatedDistance = acc.distanceTraversed + distance;
      if (accumulatedDistance < length / 2) {
        return {previousVertex: vertex, distanceTraversed: accumulatedDistance};
      } else {
        vertex = {x: vertex[0], y: vertex[1]};
        acc.previousVertex = {x: acc.previousVertex[0], y:acc.previousVertex[1] };
        return {
          midpoint: {
            x: acc.previousVertex.x + (((vertex.x - acc.previousVertex.x) / distance) * (length / 2 - acc.distanceTraversed)),
            y: acc.previousVertex.y + (((vertex.y - acc.previousVertex.y) / distance) * (length / 2 - acc.distanceTraversed)),
            angleFromNorth: calculateAngleFromNorth(subtractVector(vertex, acc.previousVertex))
          }
        };
      }
    }, {previousVertex: firstVertex, distanceTraversed: 0});
    if (optionalMidpoint.midpoint) return optionalMidpoint.midpoint;
    else return firstVertex;
  };

  root.areAdjacents = function(geometry1, geometry2){
    var epsilon = 0.01;
    var geom1FirstPoint = _.first(geometry1);
    var geom1LastPoint = _.last(geometry1);
    var geom2FirstPoint = _.first(geometry2);
    var geom2LastPoint = _.last(geometry2);
    return distanceOfPoints(geom2FirstPoint, geom1FirstPoint) < epsilon ||
      distanceOfPoints(geom2LastPoint, geom1FirstPoint) < epsilon ||
      distanceOfPoints(geom2FirstPoint,geom1LastPoint) < epsilon ||
      distanceOfPoints(geom2LastPoint,geom1LastPoint) < epsilon;
  };

  root.connectingEndPoint = function(geometry1, geometry2){
    var geom1FirstPoint = _.first(geometry1);
    var geom1LastPoint = _.last(geometry1);
    var geom2FirstPoint = _.first(geometry2);
    var geom2LastPoint = _.last(geometry2);
    var connectedEndPoint= _.find([geom1FirstPoint, geom1LastPoint], function(point){return arePointsAdjacent(point, geom2FirstPoint) || arePointsAdjacent(point, geom2LastPoint);});
    return connectedEndPoint;
  };

})(window.GeometryUtils = window.GeometryUtils || {});

