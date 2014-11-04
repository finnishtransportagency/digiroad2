package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.GeometryUtils._
import fi.liikennevirasto.digiroad2.asset.{AssetWithProperties, Modification, RoadLink}
import org.scalatest._

class GeometryUtilsSpec extends FunSuite with Matchers {
  test("calculate bearing at asset position") {
    val asset = AssetWithProperties(0, 0, 0, 10.0, 10.0, 0, wgslon = 10.0, wgslat = 10.0,
        created = Modification(None, None), modified = Modification(None, None))
    val rlDegenerate = RoadLink(id = 0, lonLat = Seq(), municipalityNumber = 235)
    val rlQuadrant1 = RoadLink(id = 0, lonLat = Seq((1d, 1d), (2d, 2d)), municipalityNumber = 235)
    val rlQuadrant2 = RoadLink(id = 0, lonLat = Seq((-1d, 1d), (-2d, 2d)), municipalityNumber = 235)
    val rlQuadrant3 = RoadLink(id = 0, lonLat = Seq((-1d, -1d), (-2d, -2d)), municipalityNumber = 235)
    val rlQuadrant4 = RoadLink(id = 0, lonLat = Seq((1d, -1d), (2d, -2d)), municipalityNumber = 235)
    calculateBearing(asset, rlDegenerate) should be (0)
    calculateBearing(asset, rlQuadrant1) should be (45)
    calculateBearing(asset, rlQuadrant2) should be (315)
    calculateBearing(asset, rlQuadrant3) should be (225)
    calculateBearing(asset, rlQuadrant4) should be (135)
  }

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

  test("splitting three link speed limit where first split is longer than second should allocate first split to existing limit") {
    val link1 = (0l, 154.0, (Point(372530, 6676811), Point(372378, 6676808)))
    val link2 = (1l, 87.0, (Point(372614, 6676793), Point(372530, 6676811)))
    val link3 = (2l, 224.0, (Point(372378, 6676808), Point(372164, 6676763)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(150.0, (0, 0.0, 154.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 150.0)
    createdLinkMeasures shouldBe(150.0, 154.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where all links run against link indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(46.0, 0.0), Point(36.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where all links run towards link indexing") {
    val link1 = (0l, 20.0, (Point(0.0, 0.0), Point(20.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where speed limit terminates to links pointing outwards") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where speed limit terminates to links pointing outwards and split link runs against indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where links are unordered and links run against link indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(46.0, 0.0), Point(36.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where links are unordered and links run towards link indexing") {
    val link1 = (0l, 20.0, (Point(0.0, 0.0), Point(20.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where links are unordered and speed limit terminates to links pointing outwards") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where links are unordered and speed limit terminates to links pointing outwards and split link runs against indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }
}