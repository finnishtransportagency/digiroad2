package fi.liikennevirasto.digiroad2.asset

import org.joda.time.LocalDate
import org.joda.time.DateTime

sealed trait AdministrativeClass {
  def value: Int
}
object AdministrativeClass {
  val values = Set(Road, Street, PrivateRoad, UnknownRoad)

  def apply(value: Int): AdministrativeClass = {
    values.find(_.value == value).getOrElse(UnknownRoad)
  }
}
case object Road extends AdministrativeClass { def value = 1 }
case object Street extends AdministrativeClass { def value = 2}
case object PrivateRoad extends AdministrativeClass { def value = 3}
case object UnknownRoad extends AdministrativeClass { def value = 99 }

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
}
case object BothDirections extends TrafficDirection { def value = 2 }
case object AgainstDigitizing extends TrafficDirection { def value = 3 }
case object TowardsDigitizing extends TrafficDirection { def value = 4 }
case object UnknownDirection extends TrafficDirection { def value = 99 }

case class AssetType(id: Long, assetTypeName: String, geometryType: String)
case class Asset(id: Long, externalId: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long,
                 imageIds: Seq[String] = List(), bearing: Option[Int] = None, validityDirection: Option[Int] = None,
                 readOnly: Boolean = true, municipalityNumber: Int, validityPeriod: Option[String] = None,
                 floating: Boolean)

case class Modification(modificationTime: Option[DateTime], modifier: Option[String])
case class AssetWithProperties(id: Long, externalId: Long, assetTypeId: Long, lon: Double, lat: Double,
                 imageIds: Seq[String] = List(), bearing: Option[Int] = None, validityDirection: Option[Int] = None,
                 readOnly: Boolean = true,
                 municipalityNumber: Int,
                 propertyData: Seq[Property] = List(), validityPeriod: Option[String] = None,
                 wgslon: Double, wgslat: Double, created: Modification, modified: Modification, roadLinkType: AdministrativeClass = UnknownRoad,
                 floating: Boolean) {
  def getPropertyValue(propertyName: String): Option[String] = {
    propertyData.find(_.publicId.equals(propertyName))
      .flatMap(_.values.headOption.map(_.propertyValue))
  }
}

case class SimpleProperty(publicId: String, values: Seq[PropertyValue])
case class Property(id: Long, publicId: String, propertyType: String, propertyUiIndex: Int = 9999, required: Boolean = false, values: Seq[PropertyValue])
case class PropertyValue(propertyValue: String, propertyDisplayValue: Option[String] = None, imageId: String = null)
case class EnumeratedPropertyValue(propertyId: Long, publicId: String, propertyName: String, propertyType: String, required: Boolean = false, values: Seq[PropertyValue])
case class RoadLink(id: Long, lonLat: Seq[(Double, Double)], endDate: Option[LocalDate] = None, municipalityNumber: Int, roadLinkType: AdministrativeClass = UnknownRoad)
case class Position(lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int])

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
