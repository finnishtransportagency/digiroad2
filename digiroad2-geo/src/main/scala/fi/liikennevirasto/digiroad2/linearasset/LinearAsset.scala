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
case class ProhibitionValue(typeId: Int, validityPeriods: Seq[ProhibitionValidityPeriod], exceptions: Seq[Int])
case class ProhibitionValidityPeriod(startHour: Int, endHour: Int, days: ValidityPeriodDayOfWeek)

sealed trait ValidityPeriodDayOfWeek { def value: Int }
object ValidityPeriodDayOfWeek {
  def apply(value: Int) = Seq(Weekday, Saturday, Sunday).find(_.value == value).getOrElse(Unknown)
  def fromTimeDomainValue(value: Int) = value match {
    case 1 => Sunday
    case 2 => Weekday
    case 7 => Saturday
    case _ => Unknown
  }
  case object Weekday extends ValidityPeriodDayOfWeek { val value = 1 }
  case object Saturday extends ValidityPeriodDayOfWeek { val value = 2 }
  case object Sunday extends ValidityPeriodDayOfWeek { val value = 3 }
  case object Unknown extends ValidityPeriodDayOfWeek { val value = 99 }
}

case class PieceWiseLinearAsset(id: Long, mmlId: Long, sideCode: SideCode, value: Option[Value], geometry: Seq[Point], expired: Boolean,
                                startMeasure: Double, endMeasure: Double,
                                endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                                createdBy: Option[String], createdDateTime: Option[DateTime], typeId: Int, trafficDirection: TrafficDirection) extends LinearAsset

case class PersistedLinearAsset(id: Long, mmlId: Long, sideCode: Int, value: Option[Value],
                         startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDateTime: Option[DateTime],
                         modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean, typeId: Int)

case class NewLinearAsset(mmlId: Long, startMeasure: Double, endMeasure: Double, value: Option[Int], sideCode: Int)


