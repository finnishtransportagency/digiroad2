package fi.liikennevirasto.digiroad2.feature

abstract sealed class Feature
case class BusStop(id: String, lat: Double, lon: Double, latWgs84: Option[Double] = None, lonWgs84: Option[Double] = None,
                   busStopType: String, featureData: Map[String, String] = Map()) extends Feature