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
case class ProhibitionValue(typeId: Int, validityPeriods: Set[ProhibitionValidityPeriod], exceptions: Set[Int])
case class ProhibitionValidityPeriod(startHour: Int, endHour: Int, days: ValidityPeriodDayOfWeek) {
  def and(b: ProhibitionValidityPeriod): Option[ProhibitionValidityPeriod] = {
    if (overlaps(b)) {
      Some(ProhibitionValidityPeriod(math.max(startHour, b.startHour), math.min(endHour, b.endHour), ValidityPeriodDayOfWeek.moreSpecific(days, b.days)))
    } else {
      None
    }
  }
  def duration(): Int = {
    if (endHour > startHour) {
      endHour - startHour
    } else {
      24 - startHour + endHour
    }
  }
  private def overlaps(b: ProhibitionValidityPeriod): Boolean = {
    ValidityPeriodDayOfWeek.overlap(days, b.days) && hoursOverlap(b)
  }
  private def hoursOverlap(b: ProhibitionValidityPeriod): Boolean = {
    liesInBetween(startHour, (b.startHour, b.endHour)) ||
    liesInBetween(b.startHour, (startHour, endHour))
  }
  private def liesInBetween(hour: Int, interval: (Int, Int)): Boolean = {
    hour >= interval._1 && hour <= interval._2
  }
}

sealed trait ValidityPeriodDayOfWeek extends Equals { def value: Int }
object ValidityPeriodDayOfWeek {
  def apply(value: Int) = Seq(Weekday, Saturday, Sunday).find(_.value == value).getOrElse(Unknown)
  def apply(value: String) = value match {
    case "Sunday" => Sunday
    case "Weekday" => Weekday
    case "Saturday" => Saturday
    case _ => Unknown
  }
  def fromTimeDomainValue(value: Int) = value match {
    case 1 => Sunday
    case 2 => Weekday
    case 7 => Saturday
    case _ => Unknown
  }
  def moreSpecific: PartialFunction[(ValidityPeriodDayOfWeek, ValidityPeriodDayOfWeek), ValidityPeriodDayOfWeek] = {
    case (Unknown, d) => d
    case (d, Unknown) => d
    case (d, Weekday) => d
    case (Weekday, d) => d
    case (a, b) if a == b => a
    case (_, _) => Unknown
  }
  def overlap: PartialFunction[(ValidityPeriodDayOfWeek, ValidityPeriodDayOfWeek), Boolean] = {
    case (Unknown, _) => true
    case (_, Unknown) => true
    case (a, b) => a == b
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

case class NewLinearAsset(mmlId: Long, startMeasure: Double, endMeasure: Double, value: Value, sideCode: Int)
case class NewNumericValueAsset(mmlId: Long, startMeasure: Double, endMeasure: Double, value: Int, sideCode: Int)
case class NewProhibition(mmlId: Long, startMeasure: Double, endMeasure: Double, value: Seq[ProhibitionValue], sideCode: Int)


