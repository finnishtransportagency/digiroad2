package fi.liikennevirasto.digiroad2.asset

import org.joda.time.LocalDate

case class AssetType(id: Long, assetTypeName: String, geometryType: String)
case class Asset(id: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long,
                 imageIds: Seq[String] = List(), bearing: Option[Int] = None, validityDirection: Option[Int] = None,
                 status: Option[String] = None, readOnly: Boolean = true,
                 municipalityNumber: Option[Int] = None, validityPeriod: Option[String] = None)

case class AssetWithProperties(id: Long, externalId: Option[Long], assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long,
                 imageIds: Seq[String] = List(), bearing: Option[Int] = None, validityDirection: Option[Int] = None,
                 status: Option[String] = None, readOnly: Boolean = true,
                 municipalityNumber: Option[Int] = None,
                 propertyData: Seq[Property] = List(), validityPeriod: Option[String] = None)

case class SimpleProperty(id: String, values: Seq[PropertyValue])
case class Property(propertyId: String, propertyName: String, propertyType: String, propertyUiIndex: Int = 9999, required: Boolean = false, values: Seq[PropertyValue])
case class PropertyValue(propertyValue: Long, propertyDisplayValue: String, imageId: String = null)
case class EnumeratedPropertyValue(propertyId: String, propertyName: String, propertyType: String, required: Boolean = false, values: Seq[PropertyValue])
case class RoadLink(id: Long, lonLat: Seq[(Double, Double)], endDate: Option[LocalDate] = None, municipalityNumber: Int)

object PropertyTypes {
  val SingleChoice = "single_choice"
  val MultipleChoice = "multiple_choice"
  val Text = "text"
  val LongText = "long_text"
  val ReadOnlyText = "read_only_text"
  val Date = "date"
}

object AssetStatus {
  val Floating = "floating"
}

object ValidityPeriod {
  val Past = "past"
  val Current = "current"
  val Future = "future"
}
