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
          acc.splitMeasure += vectorLength(subtractVector(acc.previousVertex, convertedVertex));
        }
        if (index === splitSegment.index) {
          acc.splitMeasure += vectorLength(subtractVector(convertedVertex, splitSegment.splitPoint));
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
    if (asset.sideCode === 1) {
      return asset;
    }
    asset.points = _.map(asset.points, function (point, index, geometry) {
      var baseOffset = -3.5;
      return root.offsetPoint(point, index, geometry, asset.sideCode, baseOffset);
    });
    return asset;
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

  var radiansToDegrees = function (radians) {
    return radians * (180 / Math.PI);
  };

  var calculateAngleFromNorth = function (vector) {
    return Math.atan2(vector.x, vector.y);
  };

  root.polygonIntersect = function(polygon, geometry){
    var intersectionFrequency = 0.5; //in meters, split the distance in segments and check if each coordinate intersects the polygon
    if(geometry instanceof ol.geom.Point){
      var point = _.head(geometry.getCoordinates());
      return polygon.intersectsCoordinate([point.x, point.y]);
    }

    if(geometry instanceof ol.geom.LineString)
    {
      if(!polygon.intersectsExtent(geometry.getExtent()))
        return false;

      if(_.some(geometry.getCoordinates(), function(coordinate) {
        return polygon.intersectsCoordinate(coordinate);
      }))
        return true;

      return _.some(_.zip(geometry.getCoordinates(), _.tail(geometry.getCoordinates())), function(coordinates) {
        var startCoordinate = coordinates[0];
        var endCoordinate = coordinates[1];

        if(!endCoordinate)
          return false;

        var totalDistance = distanceOfPoints(startCoordinate, endCoordinate);
        var stride = parseInt(totalDistance / intersectionFrequency, 10);

        var xDistance = endCoordinate[0] - startCoordinate[0];
        var yDistance = endCoordinate[1] - startCoordinate[1];
        var xOffset = xDistance / stride;
        var yOffset = yDistance / stride;

        return _.some(_.range(stride-1), function(index){
          var current = index+1;
          return polygon.intersectsCoordinate([startCoordinate[0] + (current * xOffset), startCoordinate[1] + (current * yOffset)]);
        });
      });
    }
    throw new Error("Geometry not supported");
  };

  root.calculateMidpointOfLineString = function (lineString, lineFraction) {
    var length = lineString.getLength();
    var vertices = lineString.getCoordinates();
    var firstVertex = _.head(vertices);
    var optionalMidpoint = _.reduce(_.tail(vertices), function (acc, vertex) {
      if (acc.midpoint) return acc;
      var divideBy = (lineFraction || 2);
      var distance = distanceOfPoints(vertex, acc.previousVertex);
      var accumulatedDistance = acc.distanceTraversed + distance;
      if (accumulatedDistance < length / divideBy ) {
        return {previousVertex: vertex, distanceTraversed: accumulatedDistance};
      } else {
        vertex = {x: vertex[0], y: vertex[1]};
        acc.previousVertex = {x: acc.previousVertex[0], y:acc.previousVertex[1] };
        return {
          midpoint: {
            x: acc.previousVertex.x + (((vertex.x - acc.previousVertex.x) / distance) * (length / divideBy - acc.distanceTraversed)),
            y: acc.previousVertex.y + (((vertex.y - acc.previousVertex.y) / distance) * (length / divideBy - acc.distanceTraversed)),
            angleFromNorth: calculateAngleFromNorth(subtractVector(vertex, acc.previousVertex))
          }
        };
      }
    }, {previousVertex: firstVertex, distanceTraversed: 0});
    if (optionalMidpoint.midpoint) return optionalMidpoint.midpoint;
    else return firstVertex;
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




})(window.GeometryUtils = window.GeometryUtils || {});

