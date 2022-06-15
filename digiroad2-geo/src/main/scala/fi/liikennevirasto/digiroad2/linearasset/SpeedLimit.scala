package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.DateTime

case class SpeedLimit(id: Long,
                      linkId: String,
                      sideCode: SideCode,
                      trafficDirection: TrafficDirection,
                      value: Option[SpeedLimitValue],
                      geometry: Seq[Point],
                      startMeasure: Double,
                      endMeasure: Double,
                      modifiedBy: Option[String],
                      modifiedDateTime: Option[DateTime],
                      createdBy: Option[String],
                      createdDateTime: Option[DateTime],
                      vvhTimeStamp: Long,
                      geomModifiedDate: Option[DateTime],
                      expired: Boolean = false,
                      linkSource: LinkGeomSource,
                      attributes: Map[String, Any] = Map()) extends LinearAsset

case class NewLimit(linkId: String, startMeasure: Double, endMeasure: Double)
case class SpeedLimitTimeStamps(id: Long, created: Modification, modified: Modification) extends TimeStamps
case class UnknownSpeedLimit(linkId: String, municipalityCode: Int, administrativeClass: AdministrativeClass)

case class PersistedSpeedLimit(id: Long, linkId: String, sideCode: SideCode, value: Option[SpeedLimitValue], startMeasure: Double, endMeasure: Double,
                               modifiedBy: Option[String], modifiedDate: Option[DateTime], createdBy: Option[String], createdDate: Option[DateTime],
                               vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], expired: Boolean = false, linkSource: LinkGeomSource)

case class SpeedLimitRow(id: Long, linkId: String, sideCode: SideCode, value: Option[Int], startMeasure: Double, endMeasure: Double,
                               modifiedBy: Option[String], modifiedDate: Option[DateTime], createdBy: Option[String], createdDate: Option[DateTime],
                               vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], expired: Boolean = false, linkSource: LinkGeomSource, publicId: String)
