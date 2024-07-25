package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.DateTime

case class NewLimit(linkId: String, startMeasure: Double, endMeasure: Double)
case class UnknownSpeedLimit(linkId: String, municipalityCode: Int, administrativeClass: AdministrativeClass)

case class SpeedLimitRow(id: Long, linkId: String, sideCode: SideCode, value: Option[Int], startMeasure: Double, endMeasure: Double,
                               modifiedBy: Option[String], modifiedDate: Option[DateTime], createdBy: Option[String], createdDate: Option[DateTime],
                               timeStamp: Long, geomModifiedDate: Option[DateTime], expired: Boolean = false, linkSource: LinkGeomSource, publicId: String)

case class SpeedLimitRowExperimental(id: Long, linkId: String, sideCode: SideCode, trafficDirection: TrafficDirection,
                                     value: Option[Int], geometry: Seq[Point], startMeasure: Double, endMeasure: Double, roadLinkLength: Double,
                                     modifiedBy: Option[String], modifiedDate: Option[DateTime], createdBy: Option[String],
                                     createdDate: Option[DateTime], administrativeClass: AdministrativeClass, municipalityCode: Int,
                                     constructionType: ConstructionType, linkSource: LinkGeomSource, publicId: String)
