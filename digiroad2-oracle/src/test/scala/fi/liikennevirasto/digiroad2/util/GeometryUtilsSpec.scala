package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.{AssetWithProperties, RoadLink, Modification}
import GeometryUtils._

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
}