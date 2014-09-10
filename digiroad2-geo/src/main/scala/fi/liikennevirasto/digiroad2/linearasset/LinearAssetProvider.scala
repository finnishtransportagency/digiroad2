package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

case class SpeedLimitLink(id: Long, roadLinkId: Long, sideCode: Int, limit: Int, points: Seq[Point])
case class SpeedLimit(id: Long, limit: Int, endpoints: Set[Point], modifiedBy: Option[String])

trait LinearAssetProvider {
  def updateSpeedLimitValue(id: Long, value: Int, username: String): Option[Long]
  def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimitLink]
  def getSpeedLimit(segmentId: Long): Option[SpeedLimit]
}