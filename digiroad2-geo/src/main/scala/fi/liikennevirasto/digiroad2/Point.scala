package fi.liikennevirasto.digiroad2

case class Vector3d(x: Double, y: Double, z: Double) {
  def dot(that: Vector3d): Double = {
    (x * that.x) + (y * that.y) + (z * that.z)
  }

  def normalize(): Vector3d = {
    if (length() != 0) {
      scale(1 / length())
    } else {
      scale(0.0)
    }
  }

  def normalize2D(): Vector3d = {
    if (this.copy(z=0.0).length() != 0) {
      scale(1 / this.copy(z=0.0).length())
    } else {
      scale(0.0)
    }
  }

  def scale(scalar: Double): Vector3d = {
    Vector3d(x * scalar, y * scalar, z * scalar)
  }

  def length(): Double = {
    Math.sqrt((x * x) + (y * y) + (z * z))
  }

  def -(that: Vector3d): Vector3d = {
    Vector3d(x - that.x, y - that.y, z - that.z)
  }
}

case class Point(x: Double, y: Double, z: Double = 0.0) {
  def distance2DTo(point: Point): Double =
    Math.sqrt(Math.pow(point.x - x, 2) + Math.pow(point.y - y, 2))

  def distance3DTo(point: Point): Double =
    Math.sqrt(Math.pow(point.x - x, 2) + Math.pow(point.y - y, 2) + Math.pow(point.z - z, 2))

  def -(that: Point): Vector3d = {
    Vector3d(x - that.x, y - that.y, z - that.z)
  }

  def -(that: Vector3d): Point = {
    Point(x - that.x, y - that.y, z - that.z)
  }

  def +(that: Vector3d): Point = {
    Point(x + that.x, y + that.y, z + that.z)
  }

  override def equals(o: Any) = o match {
    case that: Point => GeometryUtils.areAdjacent(that, this)
    case _ => false
  }

}
