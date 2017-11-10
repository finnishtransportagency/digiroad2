package fi.liikennevirasto.digiroad2.asset

import fi.liikennevirasto.digiroad2.{Point, Vector3d}
import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.DateTimeFormat

sealed trait LinkGeomSource{
  def value: Int
}

//LINKIN LÄHDE (1 = tielinkkien rajapinta, 2 = täydentävien linkkien rajapinta, 3 = suunnitelmalinkkien rajapinta, 4 = jäädytettyjen linkkien rajapinta, 5 = historialinkkien rajapinta)

object LinkGeomSource{
  val values = Set(NormalLinkInterface, ComplimentaryLinkInterface , SuravageLinkInterface, FrozenLinkInterface, HistoryLinkInterface)

  def apply(intValue: Int): LinkGeomSource = values.find(_.value == intValue).getOrElse(Unknown)

  case object NormalLinkInterface extends LinkGeomSource {def value = 1;}
  case object ComplimentaryLinkInterface extends LinkGeomSource {def value = 2;}
  case object SuravageLinkInterface extends LinkGeomSource {def value = 3;}
  case object FrozenLinkInterface extends LinkGeomSource {def value = 4;}
  case object HistoryLinkInterface extends LinkGeomSource {def value = 5;}
  case object Unknown extends LinkGeomSource { def value = 99 }
}

sealed trait ConstructionType {
  def value: Int
}

object ConstructionType{
  val values = Set[ConstructionType](InUse, UnderConstruction, Planned, UnknownConstructionType)

  def apply(intValue: Int): ConstructionType = {
    values.find(_.value == intValue).getOrElse(InUse)
  }

  case object InUse extends ConstructionType { def value = 0 }
  case object UnderConstruction extends ConstructionType { def value = 1 }
  case object Planned extends ConstructionType { def value = 3 }
  case object UnknownConstructionType extends ConstructionType { def value = 99 }
}

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

  def apply(stringValue: String): AdministrativeClass = {
    values.find(_.toString == stringValue).getOrElse(Unknown)
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
  def isOneWay =
    this == TrafficDirection.AgainstDigitizing ||
    this == TrafficDirection.TowardsDigitizing
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

  def switch(sideCode: SideCode): SideCode = {
    sideCode match {
      case TowardsDigitizing => AgainstDigitizing
      case AgainstDigitizing => TowardsDigitizing
      case _ => sideCode
    }
  }

  case object BothDirections extends SideCode { def value = 1 }
  case object TowardsDigitizing extends SideCode { def value = 2 }
  case object AgainstDigitizing extends SideCode { def value = 3 }
  case object Unknown extends SideCode { def value = 99 }
}

trait NationalStop { val nationalId: Long }
trait RoadLinkStop {
  val linkId: Option[Long]
  val mValue: Option[Double]
}
trait TimeStamps {
  val created: Modification
  val modified: Modification
}
trait FloatingAsset {
  val id: Long
  val floating: Boolean
}
case class AssetType(id: Long, assetTypeName: String, geometryType: String)

object Asset {
  val DateTimePropertyFormat = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
  val DateTimePropertyFormatMs = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss,SSS")
}

abstract class AbstractProperty {
  def publicId: String
  def values: Seq[PropertyValue]
}

case class Modification(modificationTime: Option[DateTime], modifier: Option[String])
case class SimpleProperty(publicId: String, values: Seq[PropertyValue]) extends AbstractProperty
case class Property(id: Long, publicId: String, propertyType: String, required: Boolean = false, values: Seq[PropertyValue]) extends AbstractProperty
case class PropertyValue(propertyValue: String, propertyDisplayValue: Option[String] = None, checked: Boolean = false)
case class EnumeratedPropertyValue(propertyId: Long, publicId: String, propertyName: String, propertyType: String, required: Boolean = false, values: Seq[PropertyValue]) extends AbstractProperty
case class Position(lon: Double, lat: Double, linkId: Long, bearing: Option[Int])

object PropertyTypes {
  val SingleChoice = "single_choice"
  val MultipleChoice = "multiple_choice"
  val Text = "text"
  val LongText = "long_text"
  val ReadOnlyText = "read_only_text"
  val ReadOnlyNumber = "read_only_number"
  val Date = "date"
  val ReadOnly = "read-only"
  val CheckBox = "checkbox"
}

object MassTransitStopValidityPeriod {
  val Past = "past"
  val Current = "current"
  val Future = "future"
}

case class BoundingRectangle(leftBottom: Point, rightTop: Point) {
  def diagonal: Vector3d = leftBottom - rightTop
  def area: Double = diagonal.x*diagonal.y
}