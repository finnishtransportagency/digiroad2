package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{RoadLink, Asset}

object GeometryUtils {
  /**
   * Calculates compass bearing of roadlink (in digitation direction) at asset position
   */
  def calculateBearing(asset: Asset, roadLink: RoadLink): Int = {
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
}