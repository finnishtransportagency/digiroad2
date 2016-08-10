package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, SideCode}
import org.joda.time.DateTime

trait LinearAsset extends PolyLine {
  val id: Long
  val linkId: Long
  val sideCode: SideCode
  val value: Option[Value]
  val vvhTimeStamp: Long
  val geomModifiedDate: Option[DateTime]
}

sealed trait Value {
  def toJson: Any
}
case class NumericValue(value: Int) extends Value {
  override def toJson: Any = value
}
case class TextualValue(value: String) extends Value {
  override def toJson: Any = value
}
case class Prohibitions(prohibitions: Seq[ProhibitionValue]) extends Value {
  override def toJson: Any = prohibitions
}
case class ProhibitionValue(typeId: Int, validityPeriods: Set[ValidityPeriod], exceptions: Set[Int])

//case class ProhibitionsMinutes(prohibitions: Seq[ProhibitionValueMinutes]) extends Value {
//  override def toJson: Any = prohibitions
//}
//case class ProhibitionValueMinutes(typeId: Int, validityPeriods: Set[ValidityPeriodMinutes], exceptions: Set[Int])


trait ValidityPeriodsData{
  def startHour: Int
  def endHour: Int
  def days: ValidityPeriodDayOfWeek
  def duration(): Int
  def startMinute: Int
  def endMinute: Int
}

case class ValidityPeriod(override val startHour: Int, override val endHour: Int, override val days: ValidityPeriodDayOfWeek,
                          override val startMinute: Int = 0, override val endMinute: Int = 0) extends ValidityPeriodsData{
  def and(b: ValidityPeriod): Option[ValidityPeriod] = {
    if (overlaps(b)) {
      //TODO: do an operation that includes minutes
      Some(ValidityPeriod(math.max(startHour, b.startHour), math.min(endHour, b.endHour), ValidityPeriodDayOfWeek.moreSpecific(days, b.days)))
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

  def preciseDuration(): (Int, Int) = {
    val startTotalMinutes = startMinute + (startHour * 60)
    val endTotalMinutes = endMinute + (endHour * 60)

    if (endTotalMinutes > startTotalMinutes) {
      val duration = endTotalMinutes - startTotalMinutes
      ((duration / 60).toInt, duration % 60)
    } else {
      val duration = 1440 - startTotalMinutes + endTotalMinutes
      ((duration / 60).toInt, duration % 60)
    }
  }

  //TODO: do a check that includes minutes
  private def overlaps(b: ValidityPeriod): Boolean = {
    ValidityPeriodDayOfWeek.overlap(days, b.days) && hoursOverlap(b)
  }
  //TODO: do a check that includes minutes
  private def hoursOverlap(b: ValidityPeriod): Boolean = {
    liesInBetween(startHour, (b.startHour, b.endHour)) ||
      liesInBetween(b.startHour, (startHour, endHour))
  }
  //TODO: do a check that includes minutes
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

case class PieceWiseLinearAsset(id: Long, linkId: Long, sideCode: SideCode, value: Option[Value], geometry: Seq[Point], expired: Boolean,
                                startMeasure: Double, endMeasure: Double,
                                endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                                createdBy: Option[String], createdDateTime: Option[DateTime], typeId: Int, trafficDirection: TrafficDirection,
                                vvhTimeStamp: Long, geomModifiedDate: Option[DateTime]) extends LinearAsset

case class PersistedLinearAsset(id: Long, linkId: Long, sideCode: Int, value: Option[Value],
                                startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDateTime: Option[DateTime],
                                modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean, typeId: Int,
                                vvhTimeStamp: Long, geomModifiedDate: Option[DateTime])

case class NewLinearAsset(linkId: Long, startMeasure: Double, endMeasure: Double, value: Value, sideCode: Int,
                          vvhTimeStamp: Long, geomModifiedDate: Option[DateTime])
