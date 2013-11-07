package fi.liikennevirasto.digiroad2.geo

trait Geometry
sealed abstract class Point extends Geometry
case class BusStop(id: String, lat: Long, lon: Long) extends Point