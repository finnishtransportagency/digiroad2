package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.asset.{State, TrafficDirection}
import fi.liikennevirasto.digiroad2.{FeatureClass, GeometryUtils, Point, VVHRoadlink}
import fi.liikennevirasto.viite._
import org.scalatest.{FunSuite, Matchers}

class ProjectLinkSplitterSpec extends FunSuite with Matchers {
  test("Intersection point for simple case") {
    ProjectLinkSplitter.intersectionPoint((Point(0.0, 0.0), Point(10.0, 0.0)), (Point(0.0, 1.0), Point(10.0, -1.0))) should be (Some(Point(5.0, 0.0)))
  }

  test("Intersection is not a point for parallel") {
    ProjectLinkSplitter.intersectionPoint((Point(10.0, 2.0), Point(0.0, 1.0)), (Point(0.0, 1.0), Point(10.0, 2.0))) should be (None)
  }

  test("Intersection point not found for parallel") {
    ProjectLinkSplitter.intersectionPoint((Point(0.0, 1.5), Point(10.0, 2.5)), (Point(0.0, 1.0), Point(10.0, 2.0))) should be (None)
  }

  test("Intersection point for vertical segment") {
    ProjectLinkSplitter.intersectionPoint((Point(5.0, 0.0), Point(5.0, 10.0)), (Point(0.0, 1.0), Point(10.0, -1.0))) should be (Some(Point(5.0, 0.0)))
  }

  test("Intersection point for two vertical segments") {
    ProjectLinkSplitter.intersectionPoint((Point(5.0, 0.0), Point(5.0, 10.0)), (Point(5.0, 0.0), Point(5.0, 5.0))) should be (None)
    ProjectLinkSplitter.intersectionPoint((Point(6.0, 0.0), Point(6.0, 10.0)), (Point(5.0, 0.0), Point(5.0, 5.0))) should be (None)
  }

  test("Intersection point for nearly vertical segments") {
    ProjectLinkSplitter.intersectionPoint((Point(5.0, 0.0), Point(5.0005, 10.0)), (Point(5.0, 0.0), Point(4.9995, 5.0))) should be (Some(Point(5.0, 0.0)))
  }

  test("Intersection point, second parameter is vertical") {
    ProjectLinkSplitter.intersectionPoint((Point(4.5,10.041381265149107),Point(5.526846871753764,16.20246249567169)),
      (Point(5.0,10.0),Point(5.0,16.0))).isEmpty should be (false)
  }

  test("Intersection point for equal nearly vertical segments") {
    ProjectLinkSplitter.intersectionPoint((Point(5.0, 0.0), Point(5.0005, 10.0)), (Point(5.0, 0.0), Point(5.0005, 5.0))) should be (Some(Point(5.0, 0.0)))
    ProjectLinkSplitter.intersectionPoint((Point(5.0, 0.0), Point(5.0005, 10.0)), (Point(5.0, 11.0), Point(5.0005, 15.0))) should be (None)
  }

  test("Geometry boundaries test") {
    val geom = Seq(Point(5.0, 0.0), Point(5.0, 10.0), Point(6.0, 16.0), Point(9.0, 20.0), Point(14.0, 18.0), Point(16.0, 6.0))
    val (left, right) = ProjectLinkSplitter.geometryToBoundaries(geom)
    left.size should be (geom.size)
    right.size should be (geom.size)
    left.zip(right).zip(geom).foreach{ case ((l, r), g) =>
      // Assume max 90 degree turn on test data: left and right corners are sqrt(2)*MaxSuravageTolerance away
      l.distance2DTo(g) should be >= MaxSuravageToleranceToGeometry
      l.distance2DTo(g) should be <= (MaxSuravageToleranceToGeometry * Math.sqrt(2.0))
      l.distance2DTo(g) should be (r.distance2DTo(g) +- 0.001)
    }
  }

  test("Intersection find to boundaries test") {
    val geom = Seq(Point(5.0, 0.0), Point(5.0, 10.0), Point(6.0, 16.0), Point(9.0, 20.0), Point(14.0, 18.0), Point(16.0, 6.0))
    val (left, right) = ProjectLinkSplitter.geometryToBoundaries(geom)
    val template = Seq(Point(5.0, 0.0), Point(5.0, 10.0), Point(5.0, 16.0), Point(4.0, 20.0))
    val interOpt =
      ProjectLinkSplitter.findIntersection(left, template, Some(Epsilon), Some(Epsilon))
    ProjectLinkSplitter.findIntersection(right, template, Some(Epsilon), Some(Epsilon)) should be (None)
    interOpt.isEmpty should be (false)
    interOpt.foreach { p =>
      p.x should be(5.0 +- 0.001)
      p.y should be(13.0414 +- 0.001)
    }
  }

  test("Intersection find to boundaries test on right boundary") {
    val geom = Seq(Point(5.0, 0.0), Point(5.0, 10.0), Point(6.0, 16.0), Point(9.0, 20.0), Point(14.0, 18.0), Point(16.0, 6.0))
    val (left, right) = ProjectLinkSplitter.geometryToBoundaries(geom)
    val template = Seq(Point(5.0, 0.0), Point(5.0, 10.0), Point(5.5, 13.0), Point(10.0, 15.0))
    val interOpt =
      ProjectLinkSplitter.findIntersection(right, template, Some(Epsilon), Some(Epsilon))
    ProjectLinkSplitter.findIntersection(left, template, Some(Epsilon), Some(Epsilon)) should be (None)
    interOpt.isEmpty should be (false)
    interOpt.foreach { p =>
      p.x should be(6.04745 +- 0.001)
      p.y should be(13.2433 +- 0.001)
    }
  }

  test("Matching geometry test") {
    val geom = Seq(Point(5.0, 0.0), Point(5.0, 10.0), Point(6.0, 16.0), Point(9.0, 20.0), Point(14.0, 18.0), Point(16.0, 6.0))
    val geomT = Seq(Point(5.0, 0.0), Point(5.0, 10.0), Point(5.0, 16.0), Point(4.0, 20.0))
    val suravage = VVHRoadlink(1234L, 0, geom, State, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val template = VVHRoadlink(1235L, 0, geomT, State, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val splitGeometryOpt = ProjectLinkSplitter.findMatchingGeometrySegment(suravage, template)
    splitGeometryOpt.isEmpty should be (false)
    val g = splitGeometryOpt.get
    GeometryUtils.geometryLength(g) should be < GeometryUtils.geometryLength(geomT)
    GeometryUtils.geometryLength(g) should be > (0.0)
    GeometryUtils.minimumDistance(g.last, geomT) should be < MaxDistanceForConnectedLinks
    g.length should be (3)
    g.lastOption.foreach { p =>
      p.x should be(5.0 +- 0.001)
      p.y should be(13.0414 +- 0.001)
    }
  }
}
