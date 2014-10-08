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
    def startPointOnSegment(previousPoint: Point, point: Point, accumulatedLength: Double) = {
      (point.distanceTo(previousPoint) + accumulatedLength) > startMeasure
    }

    def endPointOnSegment(previousPoint: Point, point: Point, accumulatedLength: Double) = {
      (point.distanceTo(previousPoint) + accumulatedLength) > endMeasure
    }

    if (startMeasure > endMeasure) throw new IllegalArgumentException
    if (geometry.length == 1) throw new IllegalArgumentException
    if (geometry.isEmpty) return Nil

    val accuStart = (Seq.empty[Point], false, false, geometry.head, 0.0)
    geometry.foldLeft(accuStart)((accu, point) => {
      val (truncatedGeometry, firstPointFound, lastPointFound, previousPoint, accumulatedLength) = accu
      val (pointsToAdd, foundFirstPoint, foundLastPoint) =
        if (!firstPointFound) {
          if (startPointOnSegment(previousPoint, point, accumulatedLength)) {
            val startPointMeasureOnLineSegment = startMeasure - accumulatedLength
            val directionVector = (point - previousPoint).normalize().scale(startPointMeasureOnLineSegment)
            (List(previousPoint + directionVector, point), true, lastPointFound)
          } else (Nil, firstPointFound, lastPointFound)
        }
        else {
          if(!lastPointFound) {
            if (endPointOnSegment(previousPoint, point, accumulatedLength)) {
              val endPointMeasureOnLineSegment = endMeasure - accumulatedLength
              val directionVector = (point - previousPoint).normalize().scale(endPointMeasureOnLineSegment)
              (List(previousPoint + directionVector), firstPointFound, true)
            } else (List(point), firstPointFound, lastPointFound)
          } else (Nil, firstPointFound, lastPointFound)
        }
      (truncatedGeometry ++ pointsToAdd, foundFirstPoint, foundLastPoint, point, point.distanceTo(previousPoint) + accumulatedLength)
    })._1
  }
}