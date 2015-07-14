package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.SpeedLimitFiller.MValueAdjustment
import fi.liikennevirasto.digiroad2.asset.{TimeStamps, Modification, BoundingRectangle, AdministrativeClass}
import org.joda.time.DateTime

case class SpeedLimitLink(id: Long, mmlId: Long, sideCode: Int, value: Option[Int], points: Seq[Point], startMeasure: Double, endMeasure: Double, position: Int, towardsLinkChain: Boolean)
case class SpeedLimit(id: Long, value: Option[Int], endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[String], createdBy: Option[String], createdDateTime: Option[String], speedLimitLinks: Seq[SpeedLimitLink])
case class RoadLinkForSpeedLimit(geometry: Seq[Point], length: Double, administrativeClass: AdministrativeClass, mmlId: Long, roadNumber: Option[Int])
case class SpeedLimitDTO(assetId: Long, mmlId: Long, sideCode: Int, value: Option[Int], geometry: Seq[Point], startMeasure: Double, endMeasure: Double)
case class NewLimit(mmlId: Long, startMeasure: Double, endMeasure: Double)
case class SpeedLimitTimeStamps(id: Long, created: Modification, modified: Modification) extends TimeStamps

trait LinearAssetProvider {
  def getSpeedLimitTimeStamps(ids: Set[Long]): Seq[SpeedLimitTimeStamps]
  def createSpeedLimits(newLimits: Seq[NewLimit], value: Int, username: String, municipalityValidation: (Int) => Unit): Seq[Long]
  def persistMValueAdjustments(adjustments: Seq[MValueAdjustment]): Unit
  def updateSpeedLimitValue(id: Long, value: Int, username: String, municipalityValidation: Int => Unit): Option[Long]
  def updateSpeedLimitValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long]
  def splitSpeedLimit(id: Long, mmlId: Long, splitMeasure: Double, value: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit]
  def getSpeedLimits(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[SpeedLimitLink]
  def getSpeedLimit(segmentId: Long): Option[SpeedLimit]
  def markSpeedLimitsFloating(ids: Set[Long]): Unit
  def getSpeedLimits(municipality: Int): Seq[SpeedLimitLink]
}