package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

case class SpeedLimitLink(id: Long, roadLinkId: Long, sideCode: Int, limit: Int, points: Seq[Point], position: Int)
case class SpeedLimit(id: Long, limit: Int, endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[String], createdBy: Option[String], createdDateTime: Option[String], speedLimitLinks: Seq[SpeedLimitLink])
case class RoadLinkUncoveredBySpeedLimit(roadLinkId: Long, sideCode: Int, speedLimitValue: Int, startMeasure: Double, endMeasure: Double)

trait LinearAssetProvider {
  def updateSpeedLimitValue(id: Long, value: Int, username: String): Option[Long]
  def splitSpeedLimit(id: Long, roadLinkId: Long, splitMeasure: Double, limit: Int, username: String): Seq[SpeedLimit]
  def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimitLink]
  def getSpeedLimit(segmentId: Long): Option[SpeedLimit]
  def fillUncoveredRoadLinks(uncoveredRoadLinks: Map[Long, RoadLinkUncoveredBySpeedLimit]): Unit
}