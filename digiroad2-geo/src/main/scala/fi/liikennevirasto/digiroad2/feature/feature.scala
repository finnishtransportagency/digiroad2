package fi.liikennevirasto.digiroad2.feature

import fi.liikennevirasto.digiroad2.geo.Point

abstract sealed class Feature
case class BusStop(id: String, lat: Double, lon: Double, featureData: Map[String, String] = Map()) extends Feature {
  BusStop
}
object BusStop {
  def apply(id: String, point: Point, features: Map[String, String]) = {
    new BusStop(id, point.lat, point.lon, features)
  }
}
