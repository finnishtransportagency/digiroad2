package fi.liikennevirasto.digiroad2.geo

abstract sealed class Geometry
case class Point(lat: Double, lon: Double) extends Geometry
