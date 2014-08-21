package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

case class Point(x: Double, y: Double) {
  def distanceTo(point: Point): Double =
    Math.sqrt(Math.pow(point.x - x, 2) + Math.pow(point.y - y, 2))
}

case class SpeedLimitLink(id: Long, roadLinkId: Long, sideCode: Int, limit: Int, points: Seq[Point])
case class SpeedLimit(id: Long, endpoints: Set[Point])

trait LinearAssetProvider {
  def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimitLink]
  def getSpeedLimit(segmentId: Long): Option[SpeedLimit]
}