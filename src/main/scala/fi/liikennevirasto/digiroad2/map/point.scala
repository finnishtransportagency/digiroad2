package fi.liikennevirasto.digiroad2.map

sealed abstract class Point
case class BusStop(id: String, lat: Long, lon: Long) extends Point