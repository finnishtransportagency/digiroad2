package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{SideCodeAdjustment, MValueAdjustment}
import fi.liikennevirasto.digiroad2.asset.{TimeStamps, Modification, BoundingRectangle, AdministrativeClass}
import org.joda.time.DateTime

case class SpeedLimit(id: Long, mmlId: Long, sideCode: Int, value: Option[Int], points: Seq[Point], startMeasure: Double, endMeasure: Double, modifiedBy: Option[String], modifiedDateTime: Option[DateTime], createdBy: Option[String], createdDateTime: Option[DateTime])
case class RoadLinkForSpeedLimit(geometry: Seq[Point], length: Double, administrativeClass: AdministrativeClass, mmlId: Long, roadIdentifier: Option[Either[Int, String]])
case class NewLimit(mmlId: Long, startMeasure: Double, endMeasure: Double)
case class SpeedLimitTimeStamps(id: Long, created: Modification, modified: Modification) extends TimeStamps

trait LinearAssetProvider {
  def persistSideCodeAdjustments(adjustedSideCodes: Seq[SideCodeAdjustment]): Unit
  def createSpeedLimits(newLimits: Seq[NewLimit], value: Int, username: String, municipalityValidation: (Int) => Unit): Seq[Long]
  def persistMValueAdjustments(adjustments: Seq[MValueAdjustment]): Unit
  def updateSpeedLimitValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long]
  def splitSpeedLimit(id: Long, mmlId: Long, splitMeasure: Double, value: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit]
  def separateSpeedLimit(id: Long, valueTowardsDigitization: Int, valueAgainstDigitization: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit]
  def getSpeedLimits(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]]
  def getSpeedLimits(ids: Seq[Long]): Seq[SpeedLimit]
  def getSpeedLimit(segmentId: Long): Option[SpeedLimit]
  def markSpeedLimitsFloating(ids: Set[Long]): Unit
  def getSpeedLimits(municipality: Int): Seq[SpeedLimit]
}