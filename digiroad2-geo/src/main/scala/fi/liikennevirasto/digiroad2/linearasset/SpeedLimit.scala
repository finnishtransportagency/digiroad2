package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.DateTime

case class SpeedLimit(id: Long,
                      linkId: Long,
                      sideCode: SideCode,
                      trafficDirection: TrafficDirection,
                      value: Option[NumericValue],
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
                      municipalityCode: Option[Int],
                      linkSource: LinkGeomSource,
                      attributes: Map[String, Any] = Map()) extends LinearAsset

case class NewLimit(linkId: Long, startMeasure: Double, endMeasure: Double)
case class SpeedLimitTimeStamps(id: Long, created: Modification, modified: Modification) extends TimeStamps
case class UnknownSpeedLimit(linkId: Long, municipalityCode: Int, administrativeClass: AdministrativeClass)

