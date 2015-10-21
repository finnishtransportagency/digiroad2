package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, SideCode}
import org.joda.time.DateTime

trait LinearAsset extends PolyLine {
  val id: Long
  val mmlId: Long
  val sideCode: SideCode
  val value: Option[Value]
}

sealed trait Value {
  def toJson: Any
}
case class NumericValue(value: Int) extends Value {
  override def toJson: Any = value
}
case class Prohibitions(prohibitions: Seq[ProhibitionValue]) extends Value {
  override def toJson: Any = prohibitions
}
case class ProhibitionValue(typeId: Int, validityPeriods: Seq[ValidityPeriod])
case class ValidityPeriod(startHour: Int, endHour: Int)

case class PieceWiseLinearAsset(id: Long, mmlId: Long, sideCode: SideCode, value: Option[Value], geometry: Seq[Point], expired: Boolean,
                                startMeasure: Double, endMeasure: Double,
                                endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                                createdBy: Option[String], createdDateTime: Option[DateTime], typeId: Int, trafficDirection: TrafficDirection) extends LinearAsset

case class PersistedLinearAsset(id: Long, mmlId: Long, sideCode: Int, value: Option[Value],
                         startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDateTime: Option[DateTime],
                         modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean, typeId: Int)

case class NewLinearAsset(mmlId: Long, startMeasure: Double, endMeasure: Double, value: Option[Int], sideCode: Int)


