package fi.liikennevirasto.digiroad2

object GeometryUtils {
  def geometryEndpoints(geometry: Seq[Point]): (Point, Point) = {
    val firstPoint: Point = geometry.head
    val lastPoint: Point = geometry.last
    (firstPoint, lastPoint)
  }

  private def liesInBetween(measure: Double, interval: (Double, Double)): Boolean = {
    measure >= interval._1 && measure <= interval._2
  }

  def truncateGeometry(geometry: Seq[Point], startMeasure: Double, endMeasure: Double): Seq[Point] = {
    def measureOnSegment(measure: Double, segment: (Point, Point), accumulatedLength: Double): Boolean = {
      val (firstPoint, secondPoint) = segment
      val interval = (accumulatedLength, firstPoint.distanceTo(secondPoint) + accumulatedLength)
      liesInBetween(measure, interval)
    }

    def newPointOnSegment(measureOnSegment: Double, segment: (Point, Point)): Point = {
      val (firstPoint, secondPoint) = segment
      val directionVector = (secondPoint - firstPoint).normalize().scale(measureOnSegment)
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

      (truncatedGeometry ++ pointsToAdd, enteredSelection, point, point.distanceTo(previousPoint) + accumulatedLength)
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
          val distance = point.distanceTo(acc.previousPoint)
          val remainingMeasure = acc.remainingMeasure
          if (remainingMeasure <= distance + 0.01) {
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
        AlgorithmState(point, acc.length + acc.previousPoint.distanceTo(point))
      }.length
    }
  }

  def calculateLinearReferenceFromPoint(point: Point, points: Seq[Point]): Double = {
    case class Projection(distance: Double, segmentIndex: Int, segmentLength: Double, mValue: Double)
    val lineSegments: Seq[((Point, Point), Int)] = points.zip(points.tail).zipWithIndex
    val projections: Seq[Projection] = lineSegments.map { case((p1: Point, p2: Point), segmentIndex: Int) =>
      val segmentLength = (p2 - p1).length()
      val directionVector = (p2 - p1).normalize()
      val negativeMValue = (p1 - point).dot(directionVector)
      val clampedNegativeMValue =
        if (negativeMValue > 0) 0
        else if (negativeMValue < (-1 * segmentLength)) -1 * segmentLength
        else negativeMValue
      val projectionVectorOnLineSegment: Vector3d = directionVector.scale(clampedNegativeMValue)
      val pointToLineSegment: Vector3d = (p1 - point) - projectionVectorOnLineSegment
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
    val epsilon = 0.01
    val geometry1EndPoints = GeometryUtils.geometryEndpoints(geometry1)
    val geometry2Endpoints = GeometryUtils.geometryEndpoints(geometry2)
    geometry2Endpoints._1.distanceTo(geometry1EndPoints._1) < epsilon ||
      geometry2Endpoints._2.distanceTo(geometry1EndPoints._1) < epsilon ||
      geometry2Endpoints._1.distanceTo(geometry1EndPoints._2) < epsilon ||
      geometry2Endpoints._2.distanceTo(geometry1EndPoints._2) < epsilon
  }
}