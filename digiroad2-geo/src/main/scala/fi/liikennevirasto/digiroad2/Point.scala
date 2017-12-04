package fi.liikennevirasto.digiroad2

case class Matrix(m: Seq[Seq[Double]]) {
  def *(that: Vector3d): Vector3d = {

    if (this.m.size == 2 && this.m.forall(_.size == 2)) {
      val v = Seq[Double](that.x, that.y)
      val x = this.m.map(_.head).zip(v).map(d => d._1 * d._2).sum
      val y = this.m.map(_.tail.head).zip(v).map(d => d._1 * d._2).sum
      Vector3d(x,y,0.0)
    } else if (m.size == 3 && m.forall(_.size == 3)) {
      val vec = Seq(that.x, that.y, that.z)
      val x = m.map(_.head).zip(vec).map(d => d._1 * d._2).sum
      val y = m.map(_.tail.head).zip(vec).map(d => d._1 * d._2).sum
      val z = m.map(_.tail.tail.head).zip(vec).map(d => d._1 * d._2).sum
      Vector3d(x,y,z)
    } else
      throw new IllegalArgumentException("Matrix operations only support 2d and 3d square matrixes")
  }
  def transpose: Matrix = {
    m.size match {
      case 2 => Matrix(Seq(Seq(m(0)(0), m(1)(0)), Seq(m(0)(1), m(1)(1))))
      case 3 => Matrix(Seq(Seq(m(0)(0), m(1)(0), m(2)(0)),
        Seq(m(0)(1), m(1)(1), m(2)(1)),
        Seq(m(0)(2), m(1)(2), m(2)(2))))
    }
  }
}

case class Vector3d(x: Double, y: Double, z: Double) {
  private val RotationLeft = Matrix(Seq(Seq(0.0, 1.0), Seq(-1.0, 0.0)))
  private val RotationRight = RotationLeft.transpose
  def rotateLeft(): Vector3d = {
    RotationLeft * this
  }
  def rotateRight(): Vector3d = {
    RotationRight * this
  }

  def dot(that: Vector3d): Double = {
    (x * that.x) + (y * that.y) + (z * that.z)
  }

  def ⋅(that: Vector3d): Double = {
    dot(that)
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

  def +(that: Vector3d): Vector3d = {
    Vector3d(x + that.x, y + that.y, z + that.z)
  }

  def ⨯(that: Vector3d): Vector3d = {
    Vector3d(this.y * that.z - this.z * that.y, this.z * that.x - this.x * that.z, this.x * that.y - this.y * that.x)
  }

  def cross(that: Vector3d): Vector3d = ⨯(that)

  def to2D(): Vector3d = {
    Vector3d(x, y, 0.0)
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

  lazy val toVector: Vector3d = {
    this - Point(0.0, 0.0)
  }
}
