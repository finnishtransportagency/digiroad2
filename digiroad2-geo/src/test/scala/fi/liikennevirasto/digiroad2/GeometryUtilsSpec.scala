package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.GeometryUtils._
import org.joda.time.DateTime
import org.scalatest._

class GeometryUtilsSpec extends FunSuite with Matchers {
  test("truncate empty geometry") {
    val truncated = truncateGeometry3D(Nil, 10, 15)
    truncated should be (Nil)
  }

  test("truncation fails when start measure is after end measure") {
    an [IllegalArgumentException] should be thrownBy truncateGeometry3D(Nil, 15, 10)
  }

  test("truncation fails on one point geometry") {
    an [IllegalArgumentException] should be thrownBy truncateGeometry3D(Seq(Point(0.0, 0.0)), 10, 15)
  }

  test("truncate geometry from beginning") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 6, 10)
    truncatedGeometry should be (Seq(Point(6.0, 0.0), Point(10.0, 0.0)))
  }

  test("truncate geometry from end") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 0, 6)
    truncatedGeometry should be (Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(6.0, 0.0)))
  }

  test("truncate geometry from beginning and end") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 2, 6)
    truncatedGeometry should be (Seq(Point(2.0, 0.0), Point(5.0, 0.0), Point(6.0, 0.0)))
  }

  test("truncate geometry where start and end point are on the same segment") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 2, 3)
    truncatedGeometry should be (Seq(Point(2.0, 0.0), Point(3.0, 0.0)))
  }

  test("truncate geometry where start and end point are outside geometry") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 11.0, 15.0)
    truncatedGeometry should be(empty)
  }

  test("splitting fails if measure is not within link segment") {
    val link1 = (0.0, 100.0)
    intercept[IllegalArgumentException] {
      createSplit(105.0, link1)
    }
  }

  test("splitting one link speed limit") {
    val link1 = (0.0, 100.0)
    val (existingLinkMeasures, createdLinkMeasures) = createSplit(40.0, link1)

    existingLinkMeasures shouldBe(40.0, 100.0)
    createdLinkMeasures shouldBe(0.0, 40.0)
  }

  test("subtract contained interval from intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (4.0, 5.0))
    result shouldBe Seq((3.0, 4.0), (5.0, 6.0))
  }

  test("subtract outlying interval from intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (1.0, 2.0))
    result shouldBe Seq((3.0, 6.0))
  }

  test("subtract interval from beginning of intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (2.0, 4.0))
    result shouldBe Seq((4.0, 6.0))
  }

  test("subtract interval from end of intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (5.0, 7.0))
    result shouldBe Seq((3.0, 5.0))
  }

  test("subtract containing interval from intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (2.0, 7.0))
    result shouldBe Seq()
  }

  test("Calculate linear reference point") {
    val linkGeometry = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val point: Point = calculatePointFromLinearReference(linkGeometry, 0.5).get
    point.x should be(0.5)
    point.y should be(0.0)
  }

  test("Calculate linear reference point on three-point geometry") {
    val linkGeometry = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val point: Point = calculatePointFromLinearReference(linkGeometry, 1.5).get
    point.x should be(1.0)
    point.y should be(0.5)
  }

  test("Linear reference point on less than two-point geometry should be undefined") {
    val linkGeometry = Nil
    val point: Option[Point] = calculatePointFromLinearReference(linkGeometry, 1.5)
    point should be(None)
  }

  test("Linear reference point on negative measurement should be undefined") {
    val linkGeometry = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val point: Option[Point] = calculatePointFromLinearReference(linkGeometry, -1.5)
    point should be(None)
  }

  test("Linear reference point outside geometry should be undefined") {
    val linkGeometry = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val point: Option[Point] = calculatePointFromLinearReference(linkGeometry, 1.5)
    point should be(None)
  }

  test("Calculate length of two point geometry") {
    val geometry = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val length: Double = geometryLength(geometry)
    length should be(1.0)
  }

  test("Calculate length of three point geometry") {
    val geometry = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val length: Double = geometryLength(geometry)
    length should be(2.0)
  }

  test("Return zero length on empty geometry") {
    val length: Double = geometryLength(Nil)
    length should be(0.0)
  }

  test("Return zero length on one-point geometry") {
    val length: Double = geometryLength(List(Point(0.0, 0.0)))
    length should be(0.0)
  }

  test("Minimum distance to line segment") {
    val segment = Seq(Point(-1.0, 0.0), Point(1.0, 1.0))
    val distance = minimumDistance(Point(0.0, 0.0), segment)
    distance should be(.4472135954999579)
  }

  test("Minimum distance to line segment, close to segment start") {
    val segment = Seq(Point(-1.0, 0.0), Point(1.0, 1.0))
    val distance = minimumDistance(Point(-2.0, 0.0), segment)
    distance should be(1)
  }

  test("Minimum distance to line segment, close to segment end") {
    val segment = Seq(Point(-1.0, 0.0), Point(1.0, 1.0))
    val distance = minimumDistance(Point(1.0, 1.5), segment)
    distance should be(.5)
  }

  test("Minimum distance to line segment, multiple segments") {
    val segment = Seq(Point(-1.0, 0.0), Point(1.0, 1.0), Point(2.0, 1.0))
    val distance = minimumDistance(Point(1.5, 1.1), segment)
    distance should be >= .0999
    distance should be <= .1001
  }

  test("Get minimum distance from point to segment") {
    val distance = minimumDistance(Point(0,0,0), (Point(-1,1,0), Point(1,1,0)))
    distance should be (1.0)
  }

  test("Get minimum distance from point to segment end") {
    val distance = minimumDistance(Point(0,0,0), (Point(-1,-1,0), Point(-.5,-.5,0)))
    distance should be > .707
    distance should be < .70711
  }

  test("Get minimum distance from point to segment midpoint") {
    val distance = minimumDistance(Point(0,0,0),
      segmentByMinimumDistance(Point(0,0,0), Seq(Point(-1,1,0), Point(0,.9,0), Point(1,1,0))))
    distance should be(0.9)
  }

  test("overlap cases") {
    overlaps((0.0, 0.1), (0.1,0.2)) should be(false)
    overlaps((0.0, 0.15), (0.1,0.2)) should be(true)
    overlaps((0.11, 0.15), (0.1,0.2)) should be(true)
    overlaps((0.15, 0.11), (0.1,0.2)) should be(true)
    overlaps((0.15, 0.21), (0.2,0.1)) should be(true)
    overlaps((0.21, 0.01), (0.1,0.2)) should be(true)
    overlaps((0.21, 0.01), (0.1,0.2)) should be(true)
    overlaps((0.21, 0.22), (0.1,0.2)) should be(false)
    overlaps((0.22, 0.21), (0.1,0.2)) should be(false)
  }

  test("within tolerance") {
    val p1 = Point(0,0)
    val p2 = Point(1,1)
    val p3 = Point(1.5,1.5)
    val p4 = Point(1.01,.99)
    withinTolerance(Seq(p1, p2), Seq(p1, p2), 0.0001) should be (true)
    withinTolerance(Seq(p1, p2), Seq(p1, p3), 0.0001) should be (false)
    withinTolerance(Seq(p1, p2), Seq(p2, p1), 0.0001) should be (false)
    withinTolerance(Seq(p1, p2), Seq(p1, p3), 1.0001) should be (true)
    withinTolerance(Seq(p1, p2), Seq(p1, p4), .0001) should be (false)
    withinTolerance(Seq(p1, p2), Seq(p1, p4), .015) should be (true)
    withinTolerance(Seq(p1), Seq(p1, p4), .0001) should be (false)
    withinTolerance(Seq(), Seq(p1, p4), .0001) should be (false)
    withinTolerance(Seq(p1), Seq(p1), .0001) should be (true)
    withinTolerance(Seq(p1), Seq(), .0001) should be (false)
    withinTolerance(Seq(), Seq(), .0001) should be (true)
  }

  test("truncation calculates 3 dimensional LRM distances as lengths on map") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0, 0.0), Point(5.0, 0.0, 5.0), Point(10.0, 0.0, 2.0)), 6, 10)
    truncatedGeometry.map(_.copy(z = 0.0)) should be (Seq(Point(6.0, 0.0), Point(10.0, 0.0)))
  }

  test("truncation in 2 dimensions is not affected") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 5.0), Point(0.0, 0.0), Point(5.0, 0.0)), 6, 10)
    truncatedGeometry should be (Seq(Point(1.0, 0.0), Point(5.0, 0.0)))
  }

  test("geometry moved more over 1 meter on road end"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(5.0, 5.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (true)
  }

  test("geometry moved more over 1 meter on road start"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(5.0, 1.0), Point(1.0, 0.0), Point(1.0, 1.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (true)
  }

  test("geometry moved less than 1 meter"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(0.5, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (false)
  }

  test("geometry remains the same"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (false)
  }

  test("geometry is reversed"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(1.0, 1.0), Point(1.0, 0.0), Point(0.0, 0.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (true)
  }
}
