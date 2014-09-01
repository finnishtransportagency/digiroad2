package fi.liikennevirasto.digiroad2.asset

case class Point(x: Double, y: Double, z: Double = 0.0) {
  def distanceTo(point: Point): Double =
    Math.sqrt(Math.pow(point.x - x, 2) + Math.pow(point.y - y, 2) + Math.pow(point.z - z, 2))
}
