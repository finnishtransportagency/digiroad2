package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import org.joda.time.DateTime

case class SpeedLimitLink(id: Long, roadLinkId: Long, sideCode: Int, limit: Int, points: Seq[Point], position: Int)
case class SpeedLimit(id: Long, limit: Int, endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[String], createdBy: Option[String], createdDateTime: Option[String], speedLimitLinks: Seq[SpeedLimitLink])

trait LinearAssetProvider {
  def updateSpeedLimitValue(id: Long, value: Int, username: String): Option[Long]
  def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimitLink]
  def getSpeedLimit(segmentId: Long): Option[SpeedLimit]
}