package fi.liikennevirasto.digiroad2.feature

abstract sealed class Feature
case class AssetType(id: Long, assetTypeName: String, geometryType: String) extends Feature
case class Asset(id: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, propertyData: Seq[Property] = List(), bearing: Option[Int] = None) extends Feature
case class Property(propertyId: String, propertyName: String, propertyType: String, values: Seq[PropertyValue]) extends Feature
case class PropertyValue(propertyValue: Long, propertyDisplayValue: String, imageId: String = null) extends Feature
case class EnumeratedPropertyValue(propertyId: String, propertyName: String, propertyType: String, values: Seq[PropertyValue]) extends Feature
case class RoadLink(id: Long, lonLat: Seq[(Double, Double)]) extends Feature
