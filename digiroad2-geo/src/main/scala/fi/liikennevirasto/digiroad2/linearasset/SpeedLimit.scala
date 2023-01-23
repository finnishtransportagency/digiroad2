package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.DateTime

case class NewLimit(linkId: String, startMeasure: Double, endMeasure: Double)
case class SpeedLimitTimeStamps(id: Long, created: Modification, modified: Modification) extends TimeStamps
case class UnknownSpeedLimit(linkId: String, municipalityCode: Int, administrativeClass: AdministrativeClass)

case class SpeedLimitRow(id: Long, linkId: String, sideCode: SideCode, value: Option[Int], startMeasure: Double, endMeasure: Double,
                               modifiedBy: Option[String], modifiedDate: Option[DateTime], createdBy: Option[String], createdDate: Option[DateTime],
                               timeStamp: Long, geomModifiedDate: Option[DateTime], expired: Boolean = false, linkSource: LinkGeomSource, publicId: String)
