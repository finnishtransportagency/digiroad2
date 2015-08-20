package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{UnknownLimit, SideCodeAdjustment, MValueAdjustment}
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.DateTime

case class SpeedLimit(id: Long, mmlId: Long, sideCode: SideCode, trafficDirection: TrafficDirection, value: Option[Int], points: Seq[Point], startMeasure: Double, endMeasure: Double, modifiedBy: Option[String], modifiedDateTime: Option[DateTime], createdBy: Option[String], createdDateTime: Option[DateTime])
case class RoadLinkForSpeedLimit(geometry: Seq[Point], length: Double, administrativeClass: AdministrativeClass, mmlId: Long, roadIdentifier: Option[Either[Int, String]], trafficDirection: TrafficDirection, municipalityCode: Int)
case class NewLimit(mmlId: Long, startMeasure: Double, endMeasure: Double)
case class SpeedLimitTimeStamps(id: Long, created: Modification, modified: Modification) extends TimeStamps

trait LinearAssetProvider {
  def purgeUnknownSpeedLimits(mmlIds: Set[Long]): Unit
  def getUnknownSpeedLimits(municipalities: Option[Set[Int]]): Map[String, Map[String, Seq[Long]]]
  def persistUnknownSpeedLimits(limits: Seq[UnknownLimit]): Unit
  def persistSideCodeAdjustments(adjustedSideCodes: Seq[SideCodeAdjustment]): Unit
  def createSpeedLimits(newLimits: Seq[NewLimit], value: Int, username: String, municipalityValidation: (Int) => Unit): Seq[Long]
  def persistMValueAdjustments(adjustments: Seq[MValueAdjustment]): Unit
  def updateSpeedLimitValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long]
  def splitSpeedLimit(id: Long, splitMeasure: Double, existingValue: Int, createdValue: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit]
  def separateSpeedLimit(id: Long, valueTowardsDigitization: Int, valueAgainstDigitization: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit]
  def getSpeedLimits(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]]
  def getSpeedLimits(ids: Seq[Long]): Seq[SpeedLimit]
  def getSpeedLimit(segmentId: Long): Option[SpeedLimit]
  def markSpeedLimitsFloating(ids: Set[Long]): Unit
  def getSpeedLimits(municipality: Int): Seq[SpeedLimit]
}