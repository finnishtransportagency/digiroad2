package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import fi.liikennevirasto.digiroad2.{Point, RoadLinkType}
import fi.liikennevirasto.viite.dao.CalibrationPoint
import fi.liikennevirasto.viite.RoadType

case class RoadAddressLink(id: Long, linkId: Long, geometry: Seq[Point],
                           length: Double, administrativeClass: AdministrativeClass,
                           linkType: LinkType, roadLinkType: RoadLinkType, constructionType: ConstructionType, roadLinkSource: LinkGeomSource, roadType: RoadType, modifiedAt: Option[String],modifiedBy: Option[String],
                           attributes: Map[String, Any] = Map(), roadNumber: Long, roadPartNumber: Long, trackCode: Long, elyCode: Long, discontinuity: Long,
                           startAddressM: Long, endAddressM: Long, startDate: String, endDate: String, startMValue: Double, endMValue: Double, sideCode: SideCode,
                           startCalibrationPoint: Option[CalibrationPoint], endCalibrationPoint: Option[CalibrationPoint],
                           anomaly: Anomaly = Anomaly.None, lrmPositionId: Long) extends PolyLine {
}

sealed trait Anomaly {
  def value: Int
}

object Anomaly {
  val values = Set(None, NoAddressGiven, NotFullyCovered, Illogical)

  def apply(intValue: Int): Anomaly = {
    values.find(_.value == intValue).getOrElse(None)
  }

  case object None extends Anomaly { def value = 0 }
  case object NoAddressGiven extends Anomaly { def value = 1 }
  case object NotFullyCovered extends Anomaly { def value = 2 }
  case object Illogical extends Anomaly { def value = 3 }

}