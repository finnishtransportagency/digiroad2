package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
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

  def truncateGeometry(geometry: Seq[Point], startMeasure: Double, endMeasure: Double): Seq[Point] = {
    def startPointOnSegment(previousPoint: Point, point: Point, accumulatedLength: Double): Boolean = {
      (point.distanceTo(previousPoint) + accumulatedLength) >= startMeasure && accumulatedLength <= startMeasure
    }

    def endPointOnSegment(previousPoint: Point, point: Point, accumulatedLength: Double): Boolean = {
      (point.distanceTo(previousPoint) + accumulatedLength) >= endMeasure && accumulatedLength <= endMeasure
    }

    def startPoint(previousPoint: Point, point: Point, accumulatedLength: Double): Point = {
      val startPointMeasureOnLineSegment = startMeasure - accumulatedLength
      val directionVector = (point - previousPoint).normalize().scale(startPointMeasureOnLineSegment)
      previousPoint + directionVector
    }

    def endPoint(previousPoint: Point, point: Point, accumulatedLength: Double): Point = {
      val endPointMeasureOnLineSegment = endMeasure - accumulatedLength
      val directionVector = (point - previousPoint).normalize().scale(endPointMeasureOnLineSegment)
      previousPoint + directionVector
    }

    if (startMeasure > endMeasure) throw new IllegalArgumentException
    if (geometry.length == 1) throw new IllegalArgumentException
    if (geometry.isEmpty) return Nil

    val accuStart = (Seq.empty[Point], false, geometry.head, 0.0)
    geometry.tail.foldLeft(accuStart)((accu, point) => {
      val (truncatedGeometry, onSelection, previousPoint, accumulatedLength) = accu

      val (pointsToAdd, enteredSelection) = (startPointOnSegment(previousPoint, point, accumulatedLength), endPointOnSegment(previousPoint, point, accumulatedLength), onSelection) match {
        case (false, false, true) => (List(point), true)
        case (false, false, false) => (Nil, false)
        case (true, false, _) => (List(startPoint(previousPoint, point, accumulatedLength), point), true)
        case (false, true, _) => (List(endPoint(previousPoint, point, accumulatedLength)), false)
        case (true, true, _) => (List(startPoint(previousPoint, point, accumulatedLength), endPoint(previousPoint, point, accumulatedLength)), false)
      }

      (truncatedGeometry ++ pointsToAdd, enteredSelection, point, point.distanceTo(previousPoint) + accumulatedLength)
    })._1
  }
}