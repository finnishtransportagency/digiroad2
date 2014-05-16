package fi.liikennevirasto.digiroad2.asset

import org.joda.time.LocalDate
import org.joda.time.DateTime

case class AssetType(id: Long, assetTypeName: String, geometryType: String)
case class Asset(id: Long, externalId: Option[Long], assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long,
                 imageIds: Seq[String] = List(), bearing: Option[Int] = None, validityDirection: Option[Int] = None,
                 status: Option[String] = None, readOnly: Boolean = true,
                 municipalityNumber: Option[Int] = None, validityPeriod: Option[String] = None)

case class Modification(modificationTime: Option[DateTime], modifier: Option[String])
case class AssetWithProperties(id: Long, externalId: Option[Long], assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long,
                 imageIds: Seq[String] = List(), bearing: Option[Int] = None, validityDirection: Option[Int] = None,
                 status: Option[String] = None, readOnly: Boolean = true,
                 municipalityNumber: Option[Int] = None,
                 propertyData: Seq[Property] = List(), validityPeriod: Option[String] = None,
                 wgslon: Double, wgslat: Double, created: Modification, modified: Modification)

case class SimpleProperty(publicId: String, values: Seq[PropertyValue])
case class Property(id: Long, publicId: String, propertyType: String, propertyUiIndex: Int = 9999, required: Boolean = false, values: Seq[PropertyValue])
case class PropertyValue(propertyValue: String, propertyDisplayValue: Option[String] = None, imageId: String = null)
case class EnumeratedPropertyValue(propertyId: Long, publicId: String, propertyName: String, propertyType: String, required: Boolean = false, values: Seq[PropertyValue])
case class RoadLink(id: Long, lonLat: Seq[(Double, Double)], endDate: Option[LocalDate] = None, municipalityNumber: Int)
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

object AssetStatus {
  val Floating = "floating"
}

object ValidityPeriod {
  val Past = "past"
  val Current = "current"
  val Future = "future"
}
