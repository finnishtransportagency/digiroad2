package fi.liikennevirasto.digiroad2.asset

case class Point(x: Double, y: Double) {
  def distanceTo(point: Point): Double =
    Math.sqrt(Math.pow(point.x - x, 2) + Math.pow(point.y - y, 2))
}
