package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.GeometryDirection
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.GeometryDirection
import fi.liikennevirasto.digiroad2.{LinkChain, Point}
import fi.liikennevirasto.digiroad2.asset.{RoadLink, AssetWithProperties}

object GeometryUtils {
  /**
   * Calculates compass bearing of roadlink (in digitation direction) at asset position
   */
  def calculateBearing(asset: AssetWithProperties, roadLink: RoadLink): Int = {
    if (roadLink.lonLat.size < 2) return 0
    def direction(p1: (Double, Double), p2: (Double, Double)): Double = {
      val dLon = p2._1 - p1._1
      val dLat = p2._2 - p1._2
      (Math.toDegrees(Math.atan2(dLon, dLat)) + 360) % 360
    }
    val closest = roadLink.lonLat.minBy { ll =>
      Math.sqrt(Math.pow((asset.lon - ll._1), 2) + Math.pow((asset.lat - ll._2), 2))
    }
    val closestIdx = roadLink.lonLat.indexOf(closest)
    // value is an approximation anyway so just truncate decimal part instead of adding checks for round-up corner case
    if (closestIdx == (roadLink.lonLat.size - 1)) {
      direction(roadLink.lonLat(closestIdx - 1), closest).toInt
    } else {
      direction(closest, roadLink.lonLat(closestIdx + 1)).toInt
    }
  }

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
      case (start, end) if !liesInBetween(spanStart, (start, end)) && liesInBetween(spanEnd, (start, end)) => List((spanEnd, end))
      case (start, end) if !liesInBetween(spanEnd, (start, end)) && liesInBetween(spanStart, (start, end)) => List((start, spanStart))
      case (start, end) if !liesInBetween(spanStart, (start, end)) && !liesInBetween(spanEnd, (start, end)) => List()
      case (start, end) if liesInBetween(spanStart, (start, end)) && liesInBetween(spanEnd, (start, end)) => List((start, spanStart), (spanEnd, end))
      case x => List(x)
    }
  }

  private def splitLengths(splitMeasure: Double,
                           startMeasureOfSplitLink: Double,
                           endMeasureOfSplitLink: Double,
                           splitGeometryDirection: GeometryDirection,
                           linkLength: ((Long, Double, (Point, Point))) => Double,
                           linksBeforeSplit: LinkChain[(Long, Double, (Point, Point))],
                           linksAfterSplit: LinkChain[(Long, Double, (Point, Point))]): (Double, Double) = {
    val splitFromStart = splitMeasure - startMeasureOfSplitLink
    val splitFromEnd = endMeasureOfSplitLink - splitMeasure
    val (firstSplitLength, secondSplitLength) = splitGeometryDirection match {
      case GeometryDirection.TowardsLinkChain =>
        (splitFromStart + linksBeforeSplit.length(linkLength), splitFromEnd + linksAfterSplit.length(linkLength))
      case GeometryDirection.AgainstLinkChain =>
        (splitFromEnd + linksBeforeSplit.length(linkLength), splitFromStart + linksAfterSplit.length(linkLength))
    }
    (firstSplitLength, secondSplitLength)
  }

  private def splitResults(splitMeasure: Double,
                           startMeasureOfSplitLink: Double,
                           endMeasureOfSplitLink: Double,
                           splitGeometryDirection: GeometryDirection,
                           linksBeforeSplit: LinkChain[(Long, Double, (Point, Point))],
                           linksAfterSplit: LinkChain[(Long, Double, (Point, Point))],
                           firstSplitLength: Double,
                           secondSplitLength: Double): ((Double, Double), (Double, Double), LinkChain[(Long, Double, (Point, Point))]) = {
    (firstSplitLength > secondSplitLength, splitGeometryDirection) match {
      case (true, GeometryDirection.TowardsLinkChain) => ((startMeasureOfSplitLink, splitMeasure), (splitMeasure, endMeasureOfSplitLink), linksAfterSplit)
      case (true, GeometryDirection.AgainstLinkChain) => ((splitMeasure, endMeasureOfSplitLink), (startMeasureOfSplitLink, splitMeasure), linksAfterSplit)
      case (false, GeometryDirection.TowardsLinkChain) => ((splitMeasure, endMeasureOfSplitLink), (startMeasureOfSplitLink, splitMeasure), linksBeforeSplit)
      case (false, GeometryDirection.AgainstLinkChain) => ((startMeasureOfSplitLink, splitMeasure), (splitMeasure, endMeasureOfSplitLink), linksBeforeSplit)
    }
  }

  def createSpeedLimitSplit(splitMeasure: Double, linkToBeSplit: (Long, Double, Double), links: Seq[(Long, Double, (Point, Point))]): ((Double, Double), (Double, Double), Seq[(Long, Double, (Point, Point))]) = {
    val (splitLinkId, startMeasureOfSplitLink, endMeasureOfSplitLink) = linkToBeSplit
    def linkEndPoints(link: (Long, Double, (Point, Point))) = {
      val (_, _, linkEndPoints) = link
      linkEndPoints
    }
    def linkLength(link: (Long, Double, (Point, Point))) = {
      val (_, length, _) = link
      length
    }

    val (linksBeforeSplit, splitLink, linksAfterSplit) = LinkChain(links, linkEndPoints).splitBy { case (linkId, _, _) => linkId == splitLinkId}

    val (firstSplitLength, secondSplitLength) = splitLengths(splitMeasure, startMeasureOfSplitLink, endMeasureOfSplitLink, splitLink.geometryDirection, linkLength, linksBeforeSplit, linksAfterSplit)

    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = splitResults(splitMeasure, startMeasureOfSplitLink, endMeasureOfSplitLink, splitLink.geometryDirection, linksBeforeSplit, linksAfterSplit, firstSplitLength, secondSplitLength)
    (existingLinkMeasures, createdLinkMeasures, linksToMove.rawLinks())
  }
}