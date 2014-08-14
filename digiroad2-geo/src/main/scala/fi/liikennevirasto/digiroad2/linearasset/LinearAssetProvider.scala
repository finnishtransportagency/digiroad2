package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

case class Point(x: Double, y: Double)
case class SpeedLimit(id: Long, roadLinkId: Long, sideCode: Int, limit: Int, points: Seq[Point])

trait LinearAssetProvider {
  def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimit]
}