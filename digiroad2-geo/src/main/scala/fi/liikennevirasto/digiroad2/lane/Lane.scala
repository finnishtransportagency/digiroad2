package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import org.joda.time.DateTime

trait Lane extends PolyLine{
  val id: Long
  val linkId: Long
  val sideCode: Int
  val vvhTimeStamp: Long
  val geomModifiedDate: Option[DateTime]
  val laneAttributes: LanePropertiesValues
}

case class LightLane ( geometry: Seq[Point], value: Int, expired: Boolean,  sideCode: Int )

case class PieceWiseLane ( id: Long, linkId: Long, sideCode: Int, expired: Boolean, geometry: Seq[Point],
                                startMeasure: Double, endMeasure: Double,
                                endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                                createdBy: Option[String], createdDateTime: Option[DateTime],
                                vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], administrativeClass: AdministrativeClass,
                           laneAttributes: LanePropertiesValues,  attributes: Map[String, Any] = Map() ) extends Lane

case class PersistedLane ( id: Long, linkId: Long, sideCode: Int, laneCode: Int, municipalityCode: Long,
                           startMeasure: Double, endMeasure: Double,
                           createdBy: Option[String], createdDateTime: Option[DateTime],
                           modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean,
                           vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], attributes: LanePropertiesValues )

case class NewLane ( linkId: Long, startMeasure: Double, endMeasure: Double, value: LanePropertyValue, sideCode: Int,
                          vvhTimeStamp: Long, geomModifiedDate: Option[DateTime] )

case class NewIncomeLane ( id: Long, startMeasure: Double, endMeasure: Double, municipalityCode : Long,
                           isExpired: Boolean, isDeleted: Boolean, attributes: LanePropertiesValues )

sealed trait LaneValue {
  def toJson: Any
}

case class LanePropertyValue(value: Any)
case class LaneProperty(publicId: String,  values: Seq[LanePropertyValue])
case class LanePropertiesValues(properties: Seq[LaneProperty]) extends LaneValue {
  override def toJson: Any = this
}

case class LaneRoadAddressInfo ( roadNumber: Long, initialRoadPartNumber: Long, initialDistance: Long,
                                 endRoadPartNumber: Long, endDistance: Long, track: Int )