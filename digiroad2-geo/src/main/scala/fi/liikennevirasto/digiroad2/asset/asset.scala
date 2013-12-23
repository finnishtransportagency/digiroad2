package fi.liikennevirasto.digiroad2.asset

case class AssetType(id: Long, assetTypeName: String, geometryType: String)
case class Asset(id: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, propertyData: Seq[Property] = List(), bearing: Option[Int] = None)
case class Property(propertyId: String, propertyName: String, propertyType: String, values: Seq[PropertyValue])
case class PropertyValue(propertyValue: Long, propertyDisplayValue: String, imageId: String = null)
case class EnumeratedPropertyValue(propertyId: String, propertyName: String, propertyType: String, values: Seq[PropertyValue])
case class RoadLink(id: Long, lonLat: Seq[(Double, Double)])

object PropertyTypes {
  val SingleChoice = "single_choice"
  val MultipleChoice = "multiple_choice"
  val Text = "text"
  val Date = "date"
}
