package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{FeatureClass, GeometryUtils, Point, VVHRoadlink}
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.{Discontinuity, LinkStatus, ProjectLink}
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

  test("Matching geometry test for differing digitization directions") {
    val geom = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(16.0, 0.5), Point(20.0, 0.8))
    val geomT = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(17.0, -0.5), Point(20.0, -2.0)).reverse
    val suravage = VVHRoadlink(1234L, 0, geom, State, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val template = VVHRoadlink(1235L, 0, geomT, State, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val splitGeometryOpt = ProjectLinkSplitter.findMatchingGeometrySegment(suravage, template)
    splitGeometryOpt.isEmpty should be (false)
    val g = splitGeometryOpt.get
    GeometryUtils.geometryLength(g) should be < GeometryUtils.geometryLength(geomT)
    GeometryUtils.geometryLength(g) should be > (0.0)
    GeometryUtils.minimumDistance(g.last, geomT) should be < MaxDistanceForConnectedLinks
    g.length should be (3)
    g.headOption.foreach { p =>
      p.x should be(15.75 +- 0.1)
      p.y should be(-.19 +- 0.1)
    }
  }

  test("Split aligning project links") {
    val sGeom = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(16.0, 0.5), Point(20.0, 0.8))
    val tGeom = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(16.0, 1.5), Point(20.0, 4.8))
    val sLen = GeometryUtils.geometryLength(sGeom)
    val tLen = GeometryUtils.geometryLength(tGeom)
    val suravage = ProjectLink(0L, 0L, 0L, Track.Unknown, Discontinuity.Continuous, 0L, 0L, None, None, None, 0L, 123L, 0.0, sLen,
      SideCode.Unknown, (None, None), false, sGeom, 1L, LinkStatus.NotHandled, RoadType.Unknown, LinkGeomSource.SuravageLinkInterface,
      sLen, 0L, None)
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, sLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 0L, None)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.5, 0.75), LinkStatus.UnChanged,
      LinkStatus.New, 5L, 205L, Track.Combined.value, Discontinuity.Continuous.value, 8L, LinkGeomSource.NormalLinkInterface.value,
      RoadType.PublicRoad.value)).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    sl should have size (2)
    tl should have size (1)
    val terminatedLink = tl.head
    terminatedLink.status should be (LinkStatus.Terminated)
    terminatedLink.endAddrMValue should be (template.endAddrMValue)
    GeometryUtils.areAdjacent(terminatedLink.geometry,
      GeometryUtils.truncateGeometry2D(template.geometry, terminatedLink.startMValue, template.geometryLength)) should be (true)
    sl.foreach { l =>
      l.endAddrMValue == terminatedLink.startAddrMValue || l.startAddrMValue == terminatedLink.startAddrMValue should be(true)
      l.linkGeomSource should be (LinkGeomSource.SuravageLinkInterface)
      (l.endAddrMValue == template.endAddrMValue &&
        l.status == LinkStatus.New) || l.endAddrMValue == terminatedLink.startAddrMValue  should be(true)
      l.status == LinkStatus.New || l.status == LinkStatus.UnChanged should be (true)
      GeometryUtils.areAdjacent(l.geometry, suravage.geometry) should be (true)
      l.roadNumber should be (template.roadNumber)
      l.roadPartNumber should be (template.roadPartNumber)
      l.sideCode should be (SideCode.TowardsDigitizing)
    }

  }

  test("Split opposing project links") {
    val sGeom = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(16.0, 0.5), Point(20.0, -0.8)).reverse
    val tGeom = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(16.0, 1.5), Point(20.0, 4.8))
    val sLen = GeometryUtils.geometryLength(sGeom)
    val tLen = GeometryUtils.geometryLength(tGeom)
    val suravage = ProjectLink(0L, 0L, 0L, Track.Unknown, Discontinuity.Continuous, 0L, 0L, None, None, None, 0L, 123L, 0.0, sLen,
      SideCode.Unknown, (None, None), false, sGeom, 1L, LinkStatus.NotHandled, RoadType.Unknown, LinkGeomSource.SuravageLinkInterface,
      sLen, 0L, None)
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, sLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 0L, None)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.5, 0.75), LinkStatus.New,
      LinkStatus.UnChanged, 5L, 205L, Track.Combined.value, Discontinuity.Continuous.value, 8L, LinkGeomSource.NormalLinkInterface.value,
      RoadType.PublicRoad.value)).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    sl should have size (2)
    tl should have size (1)
    val terminatedLink = tl.head
    terminatedLink.status should be (LinkStatus.Terminated)
    terminatedLink.endAddrMValue should be (template.endAddrMValue)
    GeometryUtils.areAdjacent(terminatedLink.geometry,
      GeometryUtils.truncateGeometry2D(template.geometry, terminatedLink.startMValue, template.geometryLength)) should be (true)
    sl.foreach { l =>
      l.sideCode should be (SideCode.AgainstDigitizing)
      l.endAddrMValue == template.endAddrMValue || l.startMValue > 0.0 should be (true)
      l.startAddrMValue == template.startAddrMValue || l.startMValue == 0.0 should be (true)
    }

  }

  test("Split aligning project/suravage links joining at the end") {
    val sGeom = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(16.0, 0.5), Point(20.0, 3.8))
    val tGeom = Seq(Point(5.0, 3.0), Point(15.0, 0.0), Point(16.0, 0.5), Point(20.0, 3.8))
    val sLen = GeometryUtils.geometryLength(sGeom)
    val tLen = GeometryUtils.geometryLength(tGeom)
    val suravage = ProjectLink(0L, 0L, 0L, Track.Unknown, Discontinuity.Continuous, 0L, 0L, None, None, None, 0L, 123L, 0.0, sLen,
      SideCode.Unknown, (None, None), false, sGeom, 1L, LinkStatus.NotHandled, RoadType.Unknown, LinkGeomSource.SuravageLinkInterface,
      sLen, 0L, None)
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, sLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 10L, None)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.0, 0.0), LinkStatus.New,
      LinkStatus.Transfer, 5L, 205L, Track.Combined.value, Discontinuity.Continuous.value, 8L, LinkGeomSource.NormalLinkInterface.value,
      RoadType.PublicRoad.value)).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    sl should have size (2)
    tl should have size (1)
    val terminatedLink = tl.head
    terminatedLink.status should be (LinkStatus.Terminated)
    terminatedLink.startAddrMValue should be (template.startAddrMValue)
    GeometryUtils.areAdjacent(terminatedLink.geometry,
      GeometryUtils.truncateGeometry2D(template.geometry, terminatedLink.startMValue, template.geometryLength)) should be (true)
    sl.foreach { l =>
      l.roadAddressId should be (template.roadAddressId)
      l.startAddrMValue == terminatedLink.endAddrMValue || l.startAddrMValue == terminatedLink.startAddrMValue &&
        l.status == LinkStatus.New should be(true)
      l.linkGeomSource should be (LinkGeomSource.SuravageLinkInterface)
      l.endAddrMValue == template.endAddrMValue || l.startAddrMValue == template.startAddrMValue should be(true)
      l.status == LinkStatus.New || l.status == LinkStatus.Transfer should be (true)
      GeometryUtils.areAdjacent(l.geometry, suravage.geometry) should be (true)
      l.roadNumber should be (template.roadNumber)
      l.roadPartNumber should be (template.roadPartNumber)
      l.sideCode should be (SideCode.TowardsDigitizing)
    }

  }
}
