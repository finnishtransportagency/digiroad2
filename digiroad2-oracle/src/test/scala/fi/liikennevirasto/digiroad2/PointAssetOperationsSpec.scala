package fi.liikennevirasto.digiroad2

import org.scalatest._
import fi.liikennevirasto.digiroad2.PointAssetOperations._

class PointAssetOperationsSpec extends FunSuite with Matchers {

  test ("Calculate bearing for point: horizontal") {
    val bearing = calculateBearing(Point(0,0,0), Seq(Point(-1,1,0), Point(1,1,0)))
    bearing should be (0)
  }

  test ("Calculate bearing for point: horizontal down") {
    val bearing = calculateBearing(Point(0.1,0.5,0), Seq(Point(1,1,0), Point(-1,1,0)))
    bearing should be (180)
  }

  test("Calculate bearing for point: vertical") {
    val bearing = calculateBearing(Point(0,0,0), Seq(Point(1,-1,0), Point(1,1,0)))
    bearing should be (90)
  }

  test("Calculate bearing for point: diagonal") {
    val bearing = calculateBearing(Point(0,0,0), Seq(Point(-2,-1,0), Point(0,1,0)))
    bearing should be (45)
  }

  test("Calculate bearing for point: diagonal down") {
    val bearing = calculateBearing(Point(0,0,0), Seq(Point(-2,1,0), Point(0,-1,0)))
    bearing should be (315)
  }

  test("Calculate bearing for point: corner case") {
    val bearing = calculateBearing(Point(0.5,0,0), Seq(Point(1,-1,0), Point(0.95, 0, 0), Point(1,1,0)))
    bearing should not be(90)
  }

}
