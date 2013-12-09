package fi.liikennevirasto.digiroad2.feature

abstract sealed class Feature
case class AssetType(id: Long, assetTypeName: String, geometryType: String) extends Feature
case class Asset(id: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, propertyData: Seq[Property] = List()) extends Feature
case class Property(propertyId: Long, propertyName: String, propertyType: String, values: Seq[PropertyValue]) extends Feature
case class PropertyValue(propertyValue: Long, propertyDisplayValue: String) extends Feature
case class EnumeratedPropertyValue(propertyId: Long, propertyType: String, propertyName: String, value: Long, displayName: String) extends Feature
case class BusStop(id: Long, lon: Double, lat: Double, busStopType: String, featureData: Map[String, String] = Map(), roadLinkId: Long) extends Feature
case class RoadLink(id: Long, lonLat: Seq[(Double, Double)]) extends Feature
