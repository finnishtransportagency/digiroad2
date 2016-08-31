package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, LinkType, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink

/**
  * Created by venholat on 31.8.2016.
  */
case class RoadAddressLink(roadLink: RoadLink, roadNumber: Long, roadPartNumber: Long, trackCode: Long, discontinuity: Long,
                           startAddressM: Long, endAddressM: Long, startM: Double, endM: Double, sideCode: SideCode,
                           startCalibrationPoint: Option[CalibrationPoint], endCalibrationPoint: Option[CalibrationPoint]) {
}
