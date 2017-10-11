package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import fi.liikennevirasto.digiroad2.{Point, RoadLinkType}
import fi.liikennevirasto.viite.dao.CalibrationPoint
import fi.liikennevirasto.viite.RoadType

trait RoadAddressLinkLike extends PolyLine {
  def id: Long
  def linkId: Long
  def length: Double
  def administrativeClass: AdministrativeClass
  def linkType: LinkType
  def roadLinkType: RoadLinkType
  def constructionType: ConstructionType
  def roadLinkSource: LinkGeomSource
  def roadType: RoadType
  def roadName: String
  def municipalityCode: BigInt
  def modifiedAt: Option[String]
  def modifiedBy: Option[String]
  def attributes: Map[String, Any]
  def roadNumber: Long
  def roadPartNumber: Long
  def trackCode: Long
  def elyCode: Long
  def discontinuity: Long
  def startAddressM: Long
  def endAddressM: Long
  def startMValue: Double
  def endMValue: Double
  def sideCode: SideCode
  def startCalibrationPoint: Option[CalibrationPoint]
  def endCalibrationPoint: Option[CalibrationPoint]
  def anomaly: Anomaly
  def lrmPositionId: Long
}

case class RoadAddressLink(id: Long, linkId: Long, geometry: Seq[Point],
                           length: Double, administrativeClass: AdministrativeClass,
                           linkType: LinkType, roadLinkType: RoadLinkType, constructionType: ConstructionType, roadLinkSource: LinkGeomSource, roadType: RoadType, roadName: String, municipalityCode: BigInt, modifiedAt: Option[String], modifiedBy: Option[String],
                           attributes: Map[String, Any] = Map(), roadNumber: Long, roadPartNumber: Long, trackCode: Long, elyCode: Long, discontinuity: Long,
                           startAddressM: Long, endAddressM: Long, startDate: String, endDate: String, startMValue: Double, endMValue: Double, sideCode: SideCode,
                           startCalibrationPoint: Option[CalibrationPoint], endCalibrationPoint: Option[CalibrationPoint],
                           anomaly: Anomaly = Anomaly.None, lrmPositionId: Long, newGeometry: Option[Seq[Point]] = None) extends RoadAddressLinkLike {
}

sealed trait Anomaly {
  def value: Int
}

object Anomaly {
  val values = Set(None, NoAddressGiven, GeometryChanged, Illogical)

  def apply(intValue: Int): Anomaly = {
    values.find(_.value == intValue).getOrElse(None)
  }

  case object None extends Anomaly { def value = 0 }
  case object NoAddressGiven extends Anomaly { def value = 1 }
  case object GeometryChanged extends Anomaly { def value = 2 }
  case object Illogical extends Anomaly { def value = 3 }

}