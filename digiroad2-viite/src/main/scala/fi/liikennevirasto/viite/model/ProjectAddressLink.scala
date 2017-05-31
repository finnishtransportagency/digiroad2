package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.{Point, RoadLinkType}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{CalibrationPoint, LinkStatus}

case class ProjectAddressLink (id: Long, linkId: Long, geometry: Seq[Point],
                               length: Double, administrativeClass: AdministrativeClass,
                               linkType: LinkType, roadLinkType: RoadLinkType, constructionType: ConstructionType,
                               roadLinkSource: LinkGeomSource, roadType: RoadType, modifiedAt: Option[String],modifiedBy: Option[String],
                               attributes: Map[String, Any] = Map(), roadNumber: Long, roadPartNumber: Long, trackCode: Long, elyCode: Long, discontinuity: Long,
                               startAddressM: Long, endAddressM: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                               startCalibrationPoint: Option[CalibrationPoint], endCalibrationPoint: Option[CalibrationPoint],
                               anomaly: Anomaly = Anomaly.None, lrmPositionId: Long, status: LinkStatus) extends RoadAddressLinkLike
