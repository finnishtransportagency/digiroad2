package fi.liikennevirasto.digiroad2.asset

import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.DateTimeFormat

sealed trait LinkType
{
  def value: Int
}
object LinkType {
  val values = Set(Motorway, MultipleCarriageway, SingleCarriageway, Freeway, Roundabout, SlipRoad,
                   RestArea, CycleOrPedestrianPath, PedestrianZone, ServiceOrEmergencyRoad, EnclosedTrafficArea,
                   TractorRoad, MotorwayServiceAccess, CableFerry, UnknownLinkType)

  def apply(value: Int): LinkType = {
    values.find(_.value == value).getOrElse(UnknownLinkType)
  }
}
case object Motorway extends LinkType { def value = 1 }
case object MultipleCarriageway extends LinkType { def value = 2 }
case object SingleCarriageway extends LinkType { def value = 3 }
case object Freeway extends LinkType { def value = 4 }
case object Roundabout extends LinkType { def value = 5 }
case object SlipRoad extends LinkType { def value = 6 }
case object RestArea extends LinkType { def value = 7 }
case object CycleOrPedestrianPath extends LinkType { def value = 8 }
case object PedestrianZone extends LinkType { def value = 9 }
case object ServiceOrEmergencyRoad extends LinkType { def value = 10 }
case object EnclosedTrafficArea extends LinkType { def value = 11 }
case object TractorRoad extends LinkType { def value = 12 }
case object MotorwayServiceAccess extends LinkType { def value = 13 }
case object CableFerry extends LinkType { def value = 21 }
case object UnknownLinkType extends LinkType { def value = 99 }

sealed trait AdministrativeClass {
  def value: Int
}
object AdministrativeClass {
  val values = Set(State, Municipality, Private, Unknown)

  def apply(value: Int): AdministrativeClass = {
    values.find(_.value == value).getOrElse(Unknown)
  }
}
case object State extends AdministrativeClass { def value = 1 }
case object Municipality extends AdministrativeClass { def value = 2 }
case object Private extends AdministrativeClass { def value = 3 }
case object Unknown extends AdministrativeClass { def value = 99 }

object FunctionalClass {
  val Unknown: Int = 99
}

sealed trait TrafficDirection {
  def value: Int
}
object TrafficDirection {
  val values = Set(BothDirections, AgainstDigitizing, TowardsDigitizing, UnknownDirection)

  def apply(intValue: Int): TrafficDirection = {
    values.find(_.value == intValue).getOrElse(UnknownDirection)
  }

  def apply(optionalValue: Option[Int]): TrafficDirection = {
    optionalValue.map { value => values.find(_.value == value).getOrElse(UnknownDirection) }.getOrElse(UnknownDirection)
  }

  def apply(stringValue: String): TrafficDirection = {
    values.find(_.toString == stringValue).getOrElse(UnknownDirection)
  }

  case object BothDirections extends TrafficDirection { def value = 2 }
  case object AgainstDigitizing extends TrafficDirection { def value = 3 }
  case object TowardsDigitizing extends TrafficDirection { def value = 4 }
  case object UnknownDirection extends TrafficDirection { def value = 99 }
}

sealed trait SideCode {
  def value: Int
}
object SideCode {
  val values = Set(BothDirections, TowardsDigitizing, AgainstDigitizing, Unknown)

  def apply(intValue: Int): SideCode = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object BothDirections extends SideCode { def value = 1 }
  case object TowardsDigitizing extends SideCode { def value = 2 }
  case object AgainstDigitizing extends SideCode { def value = 3 }
  case object Unknown extends SideCode { def value = 99 }
}

trait NationalStop { val nationalId: Long }
trait RoadLinkStop {
  val mmlId: Option[Long]
  val mValue: Option[Double]
}
trait TimeStamps {
  val created: Modification
  val modified: Modification
}
case class AssetType(id: Long, assetTypeName: String, geometryType: String)
case class Asset(id: Long, nationalId: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long,
                 imageIds: Seq[String] = List(), bearing: Option[Int] = None, validityDirection: Option[Int] = None,
                 readOnly: Boolean = true, municipalityNumber: Int, validityPeriod: Option[String] = None,
                 floating: Boolean, stopTypes: Seq[Int] = List())
object Asset {
  val DateTimePropertyFormat = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
}

case class Modification(modificationTime: Option[DateTime], modifier: Option[String])
case class AssetWithProperties(id: Long, nationalId: Long, assetTypeId: Long, lon: Double, lat: Double,
                 stopTypes: Seq[Int] = List(), bearing: Option[Int] = None, validityDirection: Option[Int] = None,
                 readOnly: Boolean = true,
                 municipalityNumber: Int,
                 propertyData: Seq[Property] = List(), validityPeriod: Option[String] = None,
                 wgslon: Double, wgslat: Double, created: Modification, modified: Modification, roadLinkType: AdministrativeClass = Unknown,
                 floating: Boolean) extends NationalStop {
  def getPropertyValue(propertyName: String): Option[String] = {
    propertyData.find(_.publicId.equals(propertyName))
      .flatMap(_.values.headOption.map(_.propertyValue))
  }
}

case class SimpleProperty(publicId: String, values: Seq[PropertyValue])
case class Property(id: Long, publicId: String, propertyType: String, propertyUiIndex: Int = 9999, required: Boolean = false, values: Seq[PropertyValue])
case class PropertyValue(propertyValue: String, propertyDisplayValue: Option[String] = None, imageId: String = null)
case class EnumeratedPropertyValue(propertyId: Long, publicId: String, propertyName: String, propertyType: String, required: Boolean = false, values: Seq[PropertyValue])
case class Position(lon: Double, lat: Double, mmlId: Long, bearing: Option[Int])

object PropertyTypes {
  val SingleChoice = "single_choice"
  val MultipleChoice = "multiple_choice"
  val Text = "text"
  val LongText = "long_text"
  val ReadOnlyText = "read_only_text"
  val Date = "date"
  val ReadOnly = "read-only"
}

object ValidityPeriod {
  val Past = "past"
  val Current = "current"
  val Future = "future"
}

