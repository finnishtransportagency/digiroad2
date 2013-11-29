package fi.liikennevirasto.digiroad2.feature

abstract sealed class Feature
case class BusStop(id: Long, lon: Double, lat: Double, busStopType: String, featureData: Map[String, String] = Map(), roadLinkId: Long) extends Feature
case class RoadLink(id: Long, lonLat: Seq[(Double, Double)]) extends Feature
