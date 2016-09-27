package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, LinkType, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import fi.liikennevirasto.viite.dao.CalibrationPoint

/**
  * Created by venholat on 31.8.2016.
  */
case class RoadAddressLink(id: Long, linkId: Long, geometry: Seq[Point],
                            length: Double,  administrativeClass: AdministrativeClass,
                            functionalClass: Int,  trafficDirection: TrafficDirection,
                            linkType: LinkType,  modifiedAt: Option[String],  modifiedBy: Option[String],
                            attributes: Map[String, Any] = Map(), id: Long, roadNumber: Long, roadPartNumber: Long, trackCode: Long, elyCode: Long, discontinuity: Long,
                           startAddressM: Long, endAddressM: Long, endDate: String, startM: Double, endM: Double, sideCode: SideCode,
                           startCalibrationPoint: Option[CalibrationPoint], endCalibrationPoint: Option[CalibrationPoint]) extends PolyLine {
}
