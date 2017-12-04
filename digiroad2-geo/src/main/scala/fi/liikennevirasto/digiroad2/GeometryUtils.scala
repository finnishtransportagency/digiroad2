package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.PolyLine

object GeometryUtils {

  // Default value of minimum distance where locations are considered to be same
  final private val DefaultEpsilon = 0.01

  def geometryEndpoints(geometry: Seq[Point]): (Point, Point) = {
    val firstPoint: Point = geometry.head
    val lastPoint: Point = geometry.last
    (firstPoint, lastPoint)
  }

  private def liesInBetween(measure: Double, interval: (Double, Double)): Boolean = {
    measure >= interval._1 && measure <= interval._2
  }

  def truncateGeometry2D(geometry: Seq[Point], startMeasure: Double, endMeasure: Double): Seq[Point] = {
    truncateGeometry3D(geometry.map(p => to2DGeometry(p)), startMeasure, endMeasure)
  }

  def truncateGeometry3D(geometry: Seq[Point], startMeasure: Double, endMeasure: Double): Seq[Point] = {
    def measureOnSegment(measure: Double, segment: (Point, Point), accumulatedLength: Double): Boolean = {
      val (firstPoint, secondPoint) = segment
      val interval = (accumulatedLength, firstPoint.distance2DTo(secondPoint) + accumulatedLength)
      liesInBetween(measure, interval)
    }

    def newPointOnSegment(measureOnSegment: Double, segment: (Point, Point)): Point = {
      val (firstPoint, secondPoint) = segment
      val directionVector = (secondPoint - firstPoint).normalize2D().scale(measureOnSegment)
      firstPoint + directionVector
    }

    if (startMeasure > endMeasure) throw new IllegalArgumentException
    if (geometry.length == 1) throw new IllegalArgumentException
    if (geometry.isEmpty) return Nil

    val accuStart = (Seq.empty[Point], false, geometry.head, 0.0)
    geometry.tail.foldLeft(accuStart)((accu, point) => {
      val (truncatedGeometry, onSelection, previousPoint, accumulatedLength) = accu

      val (pointsToAdd, enteredSelection) = (measureOnSegment(startMeasure, (previousPoint, point), accumulatedLength), measureOnSegment(endMeasure, (previousPoint, point), accumulatedLength), onSelection) match {
        case (false, false, true) => (List(point), true)
        case (false, false, false) => (Nil, false)
        case (true, false, _) => (List(newPointOnSegment(startMeasure - accumulatedLength, (previousPoint, point)), point), true)
        case (false, true, _) => (List(newPointOnSegment(endMeasure - accumulatedLength, (previousPoint, point))), false)
        case (true, true, _)  => (List(newPointOnSegment(startMeasure - accumulatedLength, (previousPoint, point)),
                                       newPointOnSegment(endMeasure - accumulatedLength, (previousPoint, point))), false)
      }

      (truncatedGeometry ++ pointsToAdd, enteredSelection, point, point.distance2DTo(previousPoint) + accumulatedLength)
    })._1
  }

  def subtractIntervalFromIntervals(intervals: Seq[(Double, Double)], interval: (Double, Double)): Seq[(Double, Double)] = {
    val (spanStart, spanEnd) = (math.min(interval._1, interval._2), math.max(interval._1, interval._2))
    intervals.flatMap {
      case (start, end) if spanEnd <= start => List((start, end))
      case (start, end) if spanStart >= end => List((start, end))
      case (start, end) if !liesInBetween(spanStart, (start, end)) && liesInBetween(spanEnd, (start, end)) => List((spanEnd, end))
      case (start, end) if !liesInBetween(spanEnd, (start, end)) && liesInBetween(spanStart, (start, end)) => List((start, spanStart))
      case (start, end) if !liesInBetween(spanStart, (start, end)) && !liesInBetween(spanEnd, (start, end)) => List()
      case (start, end) if liesInBetween(spanStart, (start, end)) && liesInBetween(spanEnd, (start, end)) => List((start, spanStart), (spanEnd, end))
      case x => List(x)
    }
  }

  def createSplit(splitMeasure: Double, segment: (Double, Double)): ((Double, Double), (Double, Double)) = {
    def splitLength(split: (Double, Double)) = split._2 - split._1

    if (!liesInBetween(splitMeasure, segment)) throw new IllegalArgumentException
    val (startMeasureOfSegment, endMeasureOfSegment) = segment
    val firstSplit = (startMeasureOfSegment, splitMeasure)
    val secondSplit = (splitMeasure, endMeasureOfSegment)

    if (splitLength(firstSplit) > splitLength(secondSplit)) (firstSplit, secondSplit)
    else (secondSplit, firstSplit)
  }

  def calculatePointFromLinearReference(geometry: Seq[Point], measure: Double): Option[Point] = {
    case class AlgorithmState(previousPoint: Point, remainingMeasure: Double, result: Option[Point])
    if (geometry.size < 2 || measure < 0) { None }
    else {
      val state = geometry.tail.foldLeft(AlgorithmState(geometry.head, measure, None)) { (acc, point) =>
        if (acc.result.isDefined) {
          acc
        } else {
          val distance = point.distance2DTo(acc.previousPoint)
          val remainingMeasure = acc.remainingMeasure
          if (remainingMeasure <= distance + DefaultEpsilon) {
            val directionVector = (point - acc.previousPoint).normalize()
            val result = Some(acc.previousPoint + directionVector.scale(acc.remainingMeasure))
            AlgorithmState(point, acc.remainingMeasure - distance, result)
          } else {
            AlgorithmState(point, acc.remainingMeasure - distance, None)
          }
        }
      }
      state.result
    }
  }

  def geometryLength(geometry: Seq[Point]): Double = {
    case class AlgorithmState(previousPoint: Point, length: Double)
    if (geometry.size < 2) { 0.0 }
    else {
      geometry.tail.foldLeft(AlgorithmState(geometry.head, 0.0)) { (acc, point) =>
        AlgorithmState(point, acc.length + acc.previousPoint.distance2DTo(point))
      }.length
    }
  }

  private def to2DGeometry(p: Point) = {
    p.copy(z = 0.0)
  }

  def calculateLinearReferenceFromPoint(point: Point, points: Seq[Point]): Double = {
    case class Projection(distance: Double, segmentIndex: Int, segmentLength: Double, mValue: Double)
    val point2D = to2DGeometry(point)
    val points2D = points.map(to2DGeometry)
    val lineSegments: Seq[((Point, Point), Int)] = points2D.zip(points2D.tail).zipWithIndex
    val projections: Seq[Projection] = lineSegments.map { case((p1: Point, p2: Point), segmentIndex: Int) =>
      val segmentLength = (p2 - p1).length()
      val directionVector = (p2 - p1).normalize()
      val negativeMValue = (p1 - point2D).dot(directionVector)
      val clampedNegativeMValue =
        if (negativeMValue > 0) 0
        else if (negativeMValue < (-1 * segmentLength)) -1 * segmentLength
        else negativeMValue
      val projectionVectorOnLineSegment: Vector3d = directionVector.scale(clampedNegativeMValue)
      val pointToLineSegment: Vector3d = (p1 - point2D) - projectionVectorOnLineSegment
      Projection(
        distance = pointToLineSegment.length(),
        segmentIndex = segmentIndex,
        segmentLength = segmentLength,
        mValue = -1 * clampedNegativeMValue)
    }
    val targetIndex = projections.sortBy(_.distance).head.segmentIndex
    val distanceBeforeTarget = projections.take(targetIndex).map(_.segmentLength).sum
    distanceBeforeTarget + projections(targetIndex).mValue
  }

  def areAdjacent(geometry1: Seq[Point], geometry2: Seq[Point]): Boolean = {
    areAdjacent(geometry1, geometry2, DefaultEpsilon)
  }

  def areAdjacent(geometry1: Seq[Point], geometry2: Seq[Point], epsilon: Double): Boolean = {
    val geometry1EndPoints = GeometryUtils.geometryEndpoints(geometry1)
    val geometry2Endpoints = GeometryUtils.geometryEndpoints(geometry2)
    geometry2Endpoints._1.distance2DTo(geometry1EndPoints._1) < epsilon ||
      geometry2Endpoints._2.distance2DTo(geometry1EndPoints._1) < epsilon ||
      geometry2Endpoints._1.distance2DTo(geometry1EndPoints._2) < epsilon ||
      geometry2Endpoints._2.distance2DTo(geometry1EndPoints._2) < epsilon
  }

  def areAdjacent(geometry1: Seq[Point], geometry2: Point): Boolean = {
    areAdjacent(geometry1, geometry2, DefaultEpsilon)
  }

  def areAdjacent(geometry1: Seq[Point], geometry2: Point, epsilon: Double): Boolean = {
    val geometry1EndPoints = GeometryUtils.geometryEndpoints(geometry1)
    geometry2.distance2DTo(geometry1EndPoints._1) < epsilon ||
      geometry2.distance2DTo(geometry1EndPoints._2) < epsilon
  }

  def geometryMoved(maxDistanceDiffAllowed: Double)(geometry1: Seq[Point], geometry2: Seq[Point]): Boolean = {
    !(geometry1.nonEmpty && geometry2.nonEmpty &&
      withinTolerance(GeometryUtils.geometryEndpoints(geometry1), GeometryUtils.geometryEndpoints(geometry2), maxDistanceDiffAllowed))
  }

  def areAdjacent(point1: Point, point2: Point): Boolean = {
    areAdjacent(point1, point2, DefaultEpsilon)
  }

  def areAdjacent(point1: Point, point2: Point, epsilon: Double): Boolean = {
    point1.distance2DTo(point2) < epsilon
  }

  def segmentByMinimumDistance(point: Point, segments: Seq[Point]): (Point, Point) = {
    val partitions = segments.init.zip(segments.tail)
    partitions.minBy { p => minimumDistance(point, p) }
  }

  def minimumDistance(point: Point, segment: Seq[Point]): Double = {
    if (segment.size < 1) { return Double.NaN }
    if (segment.size < 2) { return point.distance2DTo(segment.head) }
    val segmentPoints = segment.init.zip(segment.tail)
    segmentPoints.map{segment => minimumDistance(point, segment)}.min
  }

  def minimumDistance(point: Point, segment: (Point, Point)): Double = {
    val lengthSquared = math.pow(segment._1.distance2DTo(segment._2), 2)
    if (lengthSquared.equals(0.0)) return point.distance2DTo(segment._1)
    // Calculate projection coefficient
    val segmentVector = segment._2 - segment._1
    val t = ((point.x - segment._1.x) * (segment._2.x - segment._1.x) +
      (point.y - segment._1.y) * (segment._2.y - segment._1.y)) / lengthSquared
    if (t < 0.0) return point.distance2DTo(segment._1)
    if (t > 1.0) return point.distance2DTo(segment._2)
    val projection = segment._1 + segmentVector.scale(t)
    point.distance2DTo(projection)
  }

  /**
    * Check if segments overlap (not just barely touching)
    *
    * @param segment1
    * @param segment2
    * @return
    */
  def overlaps(segment1: (Double, Double), segment2: (Double, Double)) = {
    val (s1start, s1end) = order(segment1)
    val (s2start, s2end) = order(segment2)
    !(s1end <= s2start || s1start >= s2end) && // end of s1 is smaller than s2 or end of s1 is after start of s2 => false
      (s1start < s2end || s1end > s2start)                  // start of s1 is smaller => s1 must start before s2 ends
  }
  private def order(segment: (Double, Double)) = {
    segment._1 > segment._2 match {
      case true => segment.swap
      case _ => segment
    }
  }

  /**
    * Test if one segment is completely covered by another, either way
    *
    * @param segment1 segment 1 to test
    * @param segment2 segment 2 to test
    * @return true, if segment 1 is inside segment 2 or the other way around
    */
  def covered(segment1: (Double, Double), segment2: (Double, Double)): Boolean = {
    val o = overlap(segment1, segment2)
    val (seg1, seg2) = (order(segment1), order(segment2))

    o match {
      case Some((p1, p2)) => seg1._1 == p1 && seg1._2 == p2 || seg2._1 == p1 && seg2._2 == p2
      case None => false
    }
  }

  def overlap(segment1: (Double, Double), segment2: (Double, Double)): Option[(Double, Double)] = {
    val (seg1, seg2) = (order(segment1), order(segment2))
    overlaps(seg1, seg2) match {
      case false => None
      case true => Option(Math.max(seg1._1, seg2._1), Math.min(seg1._2, seg2._2))
    }
  }

  def overlapAmount(segment1: (Double, Double), segment2: (Double, Double)): Double = {
    val overlapping = overlap(segment1, segment2).getOrElse((0.0, 0.0))
    val seg1len = Math.abs(segment1._1 - segment1._2)
    val seg2len = Math.abs(segment2._1 - segment2._2)
    Math.abs(overlapping._1 - overlapping._2) / Math.min(seg1len, seg2len)
  }

  def isDirectionChangeProjection(projection: Projection): Boolean = {
    ((projection.oldEnd - projection.oldStart)*(projection.newEnd - projection.newStart)) < 0
  }

  def withinTolerance(geom1: Seq[Point], geom2: Seq[Point], tolerance: Double): Boolean = {
    geom1.size == geom2.size &&
      geom1.zip(geom2).forall {
        case (p1, p2) => geometryLength(Seq(p1,p2)) <= tolerance
        case _ => false
      }
  }

  def withinTolerance(geom1: (Point, Point), geom2: (Point, Point), tolerance: Double): Boolean = {
    withinTolerance(Seq(geom1._1, geom1._2), Seq(geom2._1, geom2._2), tolerance)
  }

  def calculateActualBearing(validityDirection: Int, bearing: Option[Int]): Option[Int] = {
    if (validityDirection != 3) {
      bearing
    } else {
      bearing.map(_ - 180).map(x => if (x < 0) x + 360 else x)
    }
  }

  def calculateBearing(geom: Seq[Point]): Int = {
    //TODO Test bearing calculation
    val points = geometryEndpoints(geom)
    val startPoint = points._1
    val endPoint = points._2
    val rad = Math.atan2(startPoint.x - endPoint.x, startPoint.y - endPoint.y)
    (180 + (rad * (180 / Math.PI))).asInstanceOf[Int]
  }

  /**
    * Returns top-left and bottom-right corners for a minimal box that contains all given points
    * @param points point cloud
    * @return (top-left), (bottom-right) points
    */
  def boundingRectangleCorners(points: Seq[Point]): (Point, Point) = {
    val left = points.minBy(_.x).x
    val right = points.maxBy(_.x).x
    val top = points.maxBy(_.y).y
    val bottom = points.minBy(_.y).y
    (Point(left, top), Point(right, bottom))
  }

  def isLinear(polyLines: Iterable[PolyLine]): Boolean =
    !isNonLinear(polyLines)

  def isNonLinear(polyLines: Iterable[PolyLine]): Boolean = {
    if (polyLines.isEmpty)
      false
    else {
      val (p1, p2) = geometryEndpoints(polyLines.head.geometry)
      polyLines.count(p => areAdjacent(p.geometry, p1, 1.0)) > 2 ||
        polyLines.count(p => areAdjacent(p.geometry, p2, 1.0)) > 2 ||
        isNonLinear(polyLines.tail)
    }
  }

  def lastSegmentDirection(geometry: Seq[Point]): Vector3d = {
    geometry.size match {
      case 0 | 1 => throw new IllegalArgumentException("Geometry had less than 2 points")
      case 2 =>
        val (p1, p2) = (geometry.head, geometry.last)
        Vector3d(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z)
      case _ =>
        lastSegmentDirection(geometry.tail)
    }
  }

  def firstSegmentDirection(geometry: Seq[Point]): Vector3d = {
    geometry.size match {
      case 0 | 1 => throw new IllegalArgumentException("Geometry had less than 2 points")
      case _ =>
        val (p1, p2) = (geometry.head, geometry.tail.head)
        Vector3d(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z)
    }
  }

  def geometryToSegments(geometry: Seq[Point]): Seq[Seq[Point]] = {
    geometry.zip(geometry.tail).map {
      case (p1, p2) => Seq(p1, p2)
    }
  }

  def midPointGeometry(geometry: Seq[Point]): Point = {
    if (geometry.size > 1) {
      midPointGeometry(geometry.zip(geometry.tail).foldLeft(Seq.empty[Point])((b , g) => {
        val controlX = (1-0.5) * g._1.x + 0.5 * g._2.x
        val controlY = (1-0.5) * g._1.y + 0.5 * g._2.y
        b :+ Point(controlX, controlY)
      }))
    } else {
      geometry.head
    }
  }

  def calculatePointAndHeadingOnGeometry(geometry: Seq[Point], point: Point): Option[(Point, Vector3d)] = {
    calculateHeadingFromLinearReference(geometry, calculateLinearReferenceFromPoint(point, geometry)).map(v =>
      (point, v))
  }

  def calculateHeadingFromLinearReference(geometry: Seq[Point], mValue: Double): Option[Vector3d] = {
    def heading(s: Seq[Point]) = {
      s.zip(s.tail).map{case (p1, p2) => p2-p1}.fold(Vector3d(0.0,0.0,0.0)){ case (v1, v2) => v1+v2}.normalize()
    }
    val len = geometryLength(geometry)
    if (len < mValue || geometry.length < 2)
      None
    else {
      Some(heading(truncateGeometry3D(geometry, Math.max(0.0, mValue-.1), Math.min(len, mValue+.1))))
    }
  }

  case class Projection(oldStart: Double, oldEnd: Double, newStart: Double, newEnd: Double, vvhTimeStamp: Long)
}