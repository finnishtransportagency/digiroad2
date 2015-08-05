package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.GeometryUtils._
import fi.liikennevirasto.digiroad2.asset.{AssetWithProperties, Modification, RoadLink}
import org.scalatest._

class GeometryUtilsSpec extends FunSuite with Matchers {
  test("truncate empty geometry") {
    val truncated = truncateGeometry(Nil, 10, 15)
    truncated should be (Nil)
  }

  test("truncation fails when start measure is after end measure") {
    an [IllegalArgumentException] should be thrownBy truncateGeometry(Nil, 15, 10)
  }

  test("truncation fails on one point geometry") {
    an [IllegalArgumentException] should be thrownBy truncateGeometry(Seq(Point(0.0, 0.0)), 10, 15)
  }

  test("truncate geometry from beginning") {
    val truncatedGeometry = truncateGeometry(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 6, 10)
    truncatedGeometry should be (Seq(Point(6.0, 0.0), Point(10.0, 0.0)))
  }

  test("truncate geometry from end") {
    val truncatedGeometry = truncateGeometry(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 0, 6)
    truncatedGeometry should be (Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(6.0, 0.0)))
  }

  test("truncate geometry from beginning and end") {
    val truncatedGeometry = truncateGeometry(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 2, 6)
    truncatedGeometry should be (Seq(Point(2.0, 0.0), Point(5.0, 0.0), Point(6.0, 0.0)))
  }

  test("truncate geometry where start and end point are on the same segment") {
    val truncatedGeometry = truncateGeometry(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 2, 3)
    truncatedGeometry should be (Seq(Point(2.0, 0.0), Point(3.0, 0.0)))
  }

  test("truncate geometry where start and end point are outside geometry") {
    val truncatedGeometry = truncateGeometry(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 11.0, 15.0)
    truncatedGeometry should be(empty)
  }

  test("splitting fails if measure is not within link segment") {
    val link1 = (0.0, 100.0)
    intercept[IllegalArgumentException] {
      createSplit2(105.0, link1)
    }
  }

  test("splitting one link speed limit") {
    val link1 = (0.0, 100.0)
    val (existingLinkMeasures, createdLinkMeasures) = createSplit2(40.0, link1)

    existingLinkMeasures shouldBe(40.0, 100.0)
    createdLinkMeasures shouldBe(0.0, 40.0)
  }

  test("splitting three link speed limit where first split is longer than second should allocate first split to existing limit") {
    val link1 = (0l, 154.0, (Point(372530, 6676811), Point(372378, 6676808)))
    val link2 = (1l, 87.0, (Point(372614, 6676793), Point(372530, 6676811)))
    val link3 = (2l, 224.0, (Point(372378, 6676808), Point(372164, 6676763)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(150.0, (0, 0.0, 154.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 150.0)
    createdLinkMeasures shouldBe(150.0, 154.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where all links run against link indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(46.0, 0.0), Point(36.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where all links run towards link indexing") {
    val link1 = (0l, 20.0, (Point(0.0, 0.0), Point(20.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where speed limit terminates to links pointing outwards") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where speed limit terminates to links pointing outwards and split link runs against indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where links are unordered and links run against link indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(46.0, 0.0), Point(36.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where links are unordered and links run towards link indexing") {
    val link1 = (0l, 20.0, (Point(0.0, 0.0), Point(20.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where links are unordered and speed limit terminates to links pointing outwards") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where links are unordered and speed limit terminates to links pointing outwards and split link runs against indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
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
}