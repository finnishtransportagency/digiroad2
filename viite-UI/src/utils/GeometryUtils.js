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
    return _.reduce(lineString.getVertices(), function (acc, vertex, index, vertices) {
      if (index > 0) {
        var previousVertex = vertices[index - 1];
        var segmentGeometry = new OpenLayers.Geometry.LineString([previousVertex, vertex]);
        var distanceObject = segmentGeometry.distanceTo(point, {details: true});
        var segment = {
          distance: distanceObject.distance,
          splitPoint: {
            x: distanceObject.x0,
            y: distanceObject.y0
          },
          index: index - 1
        };
        return acc.concat([segment]);
      } else {
        return acc;
      }
    }, []);
  };

  root.calculateMeasureAtPoint = function (lineString, point) {
    var segments = segmentsOfLineString(lineString, point);
    var splitSegment = _.head(_.sortBy(segments, 'distance'));
    var split = _.reduce(lineString.getVertices(), function (acc, vertex, index) {
      if (acc.firstSplit) {
        if (acc.previousVertex) {
          acc.splitMeasure = acc.splitMeasure + vectorLength(subtractVector(acc.previousVertex, vertex));
        }
        if (index === splitSegment.index) {
          acc.splitMeasure = acc.splitMeasure + vectorLength(subtractVector(vertex, splitSegment.splitPoint));
          acc.firstSplit = false;
        }
        acc.previousVertex = vertex;
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
    var split = _.reduce(lineString.getVertices(), function (acc, vertex, index) {
      if (acc.firstSplit) {
        acc.firstSplitVertices.push({x: vertex.x, y: vertex.y});
        if (index === splitSegment.index) {
          acc.firstSplitVertices.push({x: splitSegment.splitPoint.x, y: splitSegment.splitPoint.y});
          acc.secondSplitVertices.push({x: splitSegment.splitPoint.x, y: splitSegment.splitPoint.y});
          acc.firstSplit = false;
        }
      } else {
        acc.secondSplitVertices.push({x: vertex.x, y: vertex.y});
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
    return Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
  };
  root.distanceOfPoints = distanceOfPoints;

  var radiansToDegrees = function (radians) {
    return radians * (180 / Math.PI);
  };

  var calculateAngleFromNorth = function (vector) {
    var v = unitVector(vector);
    var rad = ((Math.PI * 2) - (Math.atan2(v.y, v.x) + Math.PI)) + (3 * Math.PI / 2);
    var ret = rad > (Math.PI * 2) ? rad - (Math.PI * 2) : rad;
    return radiansToDegrees(ret);
  };

  root.calculateMidpointOfLineString = function (lineString) {
    var length = lineString.getLength();
    var vertices = lineString.getVertices();
    var firstVertex = _.first(vertices);
    var optionalMidpoint = _.reduce(_.tail(vertices), function (acc, vertex) {
      if (acc.midpoint) return acc;
      var distance = distanceOfPoints(vertex, acc.previousVertex);
      var accumulatedDistance = acc.distanceTraversed + distance;
      if (accumulatedDistance < length / 2) {
        return {previousVertex: vertex, distanceTraversed: accumulatedDistance};
      } else {
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

  root.pointsToLineString = function(points) {
    var openlayersPoints = _.map(points, function(point) { return new OpenLayers.Geometry.Point(point.x, point.y); });
    return new OpenLayers.Geometry.LineString(openlayersPoints);
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
  
})(window.GeometryUtils = window.GeometryUtils || {});

