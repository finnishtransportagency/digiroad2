package fi.liikennevirasto.digiroad2.feature

abstract sealed class Feature
// TODO: remove WGS coordinates (handled in client), change lon/lat order in BusStop, id from String -> Long
case class BusStop(id: String, lat: Double, lon: Double, latWgs84: Option[Double] = None, lonWgs84: Option[Double] = None,
                   busStopType: String, featureData: Map[String, String] = Map(), roadLinkId: Long) extends Feature
case class RoadLink(id: Long, lonLat: Seq[(Double, Double)]) extends Feature