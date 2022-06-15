package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.{DateTime, LocalDate}

trait LinearAsset extends PolyLine {
  val id: Long
  val linkId: String
  val sideCode: SideCode
  val value: Option[Value]
  val vvhTimeStamp: Long
  val geomModifiedDate: Option[DateTime]
}

sealed trait Value {
  def toJson: Any
}

case class SpeedLimitValue(value: Int, isSuggested: Boolean = false) extends Value {
  override def toJson: Any = value
}

case class NumericValue(value: Int) extends Value {
  override def toJson: Any = value
}
case class TextualValue(value: String) extends Value {
  override def toJson: Any = value
}

case class Prohibitions(prohibitions: Seq[ProhibitionValue], isSuggested: Boolean = false) extends Value {
  override def toJson: Any = prohibitions

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case prohs: Prohibitions =>
        prohibitions.toSet.diff(prohs.prohibitions.toSet).isEmpty && prohs.prohibitions.toSet.diff(prohibitions.toSet).isEmpty
      case _ => super.equals(obj)
    }
  }
}
case class MassLimitationValue(massLimitation: Seq[AssetTypes]) extends Value{
  override def toJson: Any = massLimitation
}

case class DynamicAssetValue(properties: Seq[DynamicProperty])
case class DynamicValue(value: DynamicAssetValue) extends Value {
  override def toJson: Any = value

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case asset: DynamicValue =>
        value.properties.size == asset.value.properties.size && (value.properties.groupBy(_.publicId), asset.value.properties.groupBy(_.publicId)).zipped.forall {
          case (asset1, asset2) => (asset1._2, asset2._2).zipped.forall {
            case (prop1, prop2) =>
              val properties = Seq(prop1, prop2).sortBy(_.values.size)
              prop1.propertyType == prop2.propertyType && properties.last.values.forall(
                x =>  properties.head.values.exists(_.value == x.value)
              )
          }
        }
      case _ => super.equals(obj)
    }
  }
}

case class AssetTypes(typeId: Int, value: String, isSuggested: Int)
case class AssetProperties(name: String, value: String)
case class ManoeuvreProperties(name: String, value: Any)

case class ProhibitionValue(typeId: Int, validityPeriods: Set[ValidityPeriod], exceptions: Set[Int], additionalInfo: String = "")
case class ValidityPeriod(val startHour: Int, val endHour: Int, val days: ValidityPeriodDayOfWeek,
                          val startMinute: Int = 0, val endMinute: Int = 0) {
  def and(b: ValidityPeriod): Option[ValidityPeriod] = {
    if (overlaps(b)) {
      Some(ValidityPeriod(math.max(startHour, b.startHour), math.min(endHour, b.endHour), ValidityPeriodDayOfWeek.moreSpecific(days, b.days), math.min(startMinute, b.startMinute), math.min(endMinute, b.endMinute)))
    } else {
      None
    }
  }
  def duration(): Int = {
    val startHourAndMinutes: Double = (startMinute / 60.0) + startHour
    val endHourAndMinutes: Double = (endMinute / 60.0) + endHour

    if (endHourAndMinutes > startHourAndMinutes) {
      Math.ceil(endHourAndMinutes - startHourAndMinutes).toInt
    } else {
      Math.ceil(24 - startHourAndMinutes + endHourAndMinutes).toInt
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


  private def overlaps(b: ValidityPeriod): Boolean = {
    ValidityPeriodDayOfWeek.overlap(days, b.days) && hoursOverlap(b)
  }

  private def hoursOverlap(b: ValidityPeriod): Boolean = {
    val startHourAndMinutes = (startMinute / 60.0) + startHour
    val endHourAndMinutes = (endMinute / 60.0) + endHour
    val startHourAndMinutesB = (b.startMinute / 60.0) + b.startHour
    val endHourAndMinutesB = (b.endMinute / 60.0) + b.endHour

    liesInBetween(startHourAndMinutes, (startHourAndMinutesB, endHourAndMinutesB)) ||
      liesInBetween(startHourAndMinutesB, (startHourAndMinutes, endHourAndMinutes))
  }

  private def liesInBetween(hour: Double, interval: (Double, Double)): Boolean = {
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
  def toTimeDomainValue(value: ValidityPeriodDayOfWeek) : Int = value match {
    case Sunday => 1
    case Weekday => 2
    case Saturday => 7
    case _ => 99
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

case class PieceWiseLinearAsset(id: Long, linkId: String, sideCode: SideCode, value: Option[Value], geometry: Seq[Point], expired: Boolean,
                                startMeasure: Double, endMeasure: Double,
                                endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                                createdBy: Option[String], createdDateTime: Option[DateTime], typeId: Int, trafficDirection: TrafficDirection,
                                vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], linkSource: LinkGeomSource, administrativeClass: AdministrativeClass,
                                attributes: Map[String, Any] = Map(), verifiedBy: Option[String], verifiedDate: Option[DateTime], informationSource: Option[InformationSource]) extends LinearAsset

case class PersistedLinearAsset(id: Long, linkId: String, sideCode: Int, value: Option[Value],
                                startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDateTime: Option[DateTime],
                                modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean, typeId: Int,
                                vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], linkSource: LinkGeomSource, verifiedBy: Option[String], verifiedDate: Option[DateTime],
                                informationSource: Option[InformationSource])

case class NewLinearAsset(linkId: String, startMeasure: Double, endMeasure: Double, value: Value, sideCode: Int,
                          vvhTimeStamp: Long, geomModifiedDate: Option[DateTime])

case class InaccurateLinearAsset(assetId: Option[Long], municipality: String, administrativeClass: String, linkId: Option[String])

case class LightLinearAsset(geometry: Seq[Point], value: Int, expired: Boolean, typeId: Int, sideCode: Int)

