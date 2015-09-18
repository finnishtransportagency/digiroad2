package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.UnknownLimit
import org.joda.time.DateTime

case class SpeedLimit(id: Long, mmlId: Long, sideCode: SideCode, trafficDirection: TrafficDirection, value: Option[Int], geometry: Seq[Point], startMeasure: Double, endMeasure: Double, modifiedBy: Option[String], modifiedDateTime: Option[DateTime], createdBy: Option[String], createdDateTime: Option[DateTime]) extends PolyLine
case class NewLimit(mmlId: Long, startMeasure: Double, endMeasure: Double)
case class SpeedLimitTimeStamps(id: Long, created: Modification, modified: Modification) extends TimeStamps

trait SpeedLimitProvider {
  def purgeUnknown(mmlIds: Set[Long]): Unit
  def getUnknown(municipalities: Option[Set[Int]]): Map[String, Map[String, Any]]
  def persistUnknown(limits: Seq[UnknownLimit]): Unit
  def create(newLimits: Seq[NewLimit], value: Int, username: String, municipalityValidation: (Int) => Unit): Seq[Long]
  def updateValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long]
  def split(id: Long, splitMeasure: Double, existingValue: Int, createdValue: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit]
  def separate(id: Long, valueTowardsDigitization: Int, valueAgainstDigitization: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit]
  def get(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]]
  def get(ids: Seq[Long]): Seq[SpeedLimit]
  def find(segmentId: Long): Option[SpeedLimit]
  def get(municipality: Int): Seq[SpeedLimit]
}