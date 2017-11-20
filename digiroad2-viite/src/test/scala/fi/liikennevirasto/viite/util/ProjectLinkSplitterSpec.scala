package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.{NormalLinkInterface, SuravageLinkInterface}
import fi.liikennevirasto.digiroad2.asset.SideCode.TowardsDigitizing
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.util.Track.{Combined, Unknown}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.viite.RoadType.{PublicRoad, UnknownOwnerRoad}
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.Discontinuity.Continuous
import fi.liikennevirasto.viite.dao.LinkStatus.{New, NotHandled}
import fi.liikennevirasto.viite.dao._
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
      sLen, 0L, 5, false, None, 85088L)
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, sLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 0L, 5, false, None, 85088L)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.5, 0.75), LinkStatus.UnChanged,
      LinkStatus.New, 5L, 205L, Track.Combined, Discontinuity.Continuous, 8L, LinkGeomSource.NormalLinkInterface,
      RoadType.PublicRoad, 1L, ProjectCoordinates(0, 1, 1))).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
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
      sLen, 0L, 5, false, None, 85088L)
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, sLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 0L, 5, false, None, 85088L)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.5, 0.75), LinkStatus.New,
      LinkStatus.UnChanged, 5L, 205L, Track.Combined, Discontinuity.Continuous, 8L, LinkGeomSource.NormalLinkInterface,
      RoadType.PublicRoad, 1L, ProjectCoordinates(0, 1, 1))).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
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
      sLen, 0L, 5, false, None, 85088L)
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, sLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 10L, 5,false, None, 85088L)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.0, 0.0), LinkStatus.New,
      LinkStatus.Transfer, 5L, 205L, Track.Combined, Discontinuity.Continuous, 8L, LinkGeomSource.NormalLinkInterface,
      RoadType.PublicRoad, 1L, ProjectCoordinates(0, 1, 1))).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    sl should have size (2)
    tl should have size (1)
    val terminatedLink = tl.head
    terminatedLink.status should be(LinkStatus.Terminated)
    terminatedLink.startAddrMValue should be(template.startAddrMValue)
    GeometryUtils.areAdjacent(terminatedLink.geometry,
      GeometryUtils.truncateGeometry2D(template.geometry, terminatedLink.startMValue, template.geometryLength)) should be(true)
    sl.foreach { l =>
      l.roadAddressId should be(template.roadAddressId)
      l.startAddrMValue == terminatedLink.endAddrMValue || l.startAddrMValue == terminatedLink.startAddrMValue &&
        l.status == LinkStatus.New should be(true)
      l.linkGeomSource should be(LinkGeomSource.SuravageLinkInterface)
      l.endAddrMValue == template.endAddrMValue || l.startAddrMValue == template.startAddrMValue should be(true)
      l.status == LinkStatus.New || l.status == LinkStatus.Transfer should be(true)
      GeometryUtils.areAdjacent(l.geometry, suravage.geometry) should be(true)
      l.roadNumber should be(template.roadNumber)
      l.roadPartNumber should be(template.roadPartNumber)
      l.sideCode should be(SideCode.TowardsDigitizing)
    }

  }

  test("Split shorter suravage link") {
    val sGeom = Seq(Point(480428.187, 7059183.911), Point(480441.534, 7059195.878), Point(480445.646, 7059199.566),
      Point(480451.056, 7059204.417), Point(480453.065, 7059206.218), Point(480456.611, 7059209.042),
      Point(480463.941, 7059214.747))
    val tGeom = Seq(Point(480428.187,7059183.911),Point(480453.614, 7059206.710), Point(480478.813, 7059226.322),
      Point(480503.826, 7059244.02),Point(480508.221, 7059247.010))
    val sLen = GeometryUtils.geometryLength(sGeom)
    val tLen = GeometryUtils.geometryLength(tGeom)
    val suravage = ProjectLink(0L, 0L, 0L, Track.Unknown, Discontinuity.Continuous, 0L, 0L, None, None, None, 0L, 123L, 0.0, sLen,
      SideCode.Unknown, (None, None), false, sGeom, 1L, LinkStatus.NotHandled, RoadType.Unknown, LinkGeomSource.SuravageLinkInterface,
      sLen, 0L, 9L, false, None, 85088L)
    val template = ProjectLink(2L, 27L, 22L, Track.Combined, Discontinuity.Continuous, 5076L, 5131L, None, None, None, 0L, 124L, 0.0, tLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 0L, 9L, false, None, 85088L)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(480463.941, 7059214.747), LinkStatus.UnChanged,
      LinkStatus.New, 27L, 22L, Track.Combined, Discontinuity.Continuous, 8L, LinkGeomSource.NormalLinkInterface,
      RoadType.PublicRoad, 1L, ProjectCoordinates(0, 1, 1))).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    sl should have size (1)
    tl should have size (1)
    val terminatedLink = tl.head
    val unChangedLink = sl.head
    terminatedLink.status should be (LinkStatus.Terminated)
    terminatedLink.endAddrMValue should be (template.endAddrMValue)
    terminatedLink.endMValue should be (template.endMValue)
    GeometryUtils.areAdjacent(terminatedLink.geometry, unChangedLink.geometry) should be (true)
    GeometryUtils.areAdjacent(unChangedLink.geometry.head, sGeom.head) should be (true)
    GeometryUtils.areAdjacent(unChangedLink.geometry.last, sGeom.last) should be (true)
    unChangedLink.startAddrMValue should be (template.startAddrMValue)
    unChangedLink.endAddrMValue should be (terminatedLink.startAddrMValue)
  }

  test("Geometry split") {
    val splitOptions = SplitOptions(
      Point(445447.5,7004165.0,0.0),
      LinkStatus.UnChanged, LinkStatus.New, 77, 1, Combined, Continuous, 8L, SuravageLinkInterface,
      RoadType.PublicRoad, 1, ProjectCoordinates(0, 1, 1))
    val template = ProjectLink(452342,77,14,Combined,Continuous,4286,4612,None,None,None,70452502,6138625,0.0,327.138,TowardsDigitizing,
      (None,Some(CalibrationPoint(6138625,327.138,4612))),false,List(Point(445417.266,7004142.049,0.0), Point(445420.674,7004144.679,0.0),
        Point(445436.147,7004155.708,0.0), Point(445448.743,7004164.052,0.0), Point(445461.586,7004172.012,0.0),
        Point(445551.316,7004225.769,0.0), Point(445622.099,7004268.174,0.0), Point(445692.288,7004310.224,0.0),
        Point(445696.301,7004312.628,0.0)),452278,NotHandled,PublicRoad,NormalLinkInterface,327.13776793597697,295486,8L,false, None, 85088L)
    val suravage = ProjectLink(-1000,0,0,Unknown,Continuous,0,0,None,None,Some("silari"),-1,499972933,0.0,322.45979989463626,
      TowardsDigitizing,(None,None),false,List(Point(445417.266,7004142.049,0.0), Point(445420.674,7004144.679,0.0),
        Point(445436.147,7004155.708,0.0), Point(445448.743,7004164.052,0.0), Point(445461.586,7004172.012,0.0),
        Point(445551.316,7004225.769,0.0), Point(445622.099,7004268.174,0.0), Point(445692.288,7004310.224,0.0)),452278,
      New,UnknownOwnerRoad,SuravageLinkInterface,322.45979989463626,0,8L,false, None, 85088L)
    val result = ProjectLinkSplitter.split(suravage, template, splitOptions)
    result should have size(3)
    result.foreach(pl =>
    {
      pl.connectedLinkId.isEmpty should be (false)
      pl.startAddrMValue should be < pl.endAddrMValue
      pl.sideCode should be (TowardsDigitizing)
    })
  }

  test("Geometries that are only touching should not have matching geometry segment") {
    val template = ProjectLink(452389,77,14,Combined,Continuous,4286,4612,None,None,None,70452560,6138625,0.0,327.138,
      TowardsDigitizing,(None,Some(CalibrationPoint(6138625,327.138,4612))),false,List(Point(445417.266,7004142.049,0.0),
        Point(445420.674,7004144.679,0.0), Point(445436.147,7004155.708,0.0), Point(445448.743,7004164.052,0.0),
        Point(445461.586,7004172.012,0.0), Point(445551.316,7004225.769,0.0), Point(445622.099,7004268.174,0.0),
        Point(445692.288,7004310.224,0.0), Point(445696.301,7004312.628,0.0)),452278,NotHandled,PublicRoad,
      NormalLinkInterface,327.13776793597697,295486,9L,false, None, 85088L)
    val suravage = ProjectLink(-1000,0,0,Unknown,Continuous,0,0,None,None,Some("silari"),-1,499972931,0.0,313.38119201522017,
      TowardsDigitizing,(None,None),false,List(Point(445186.594,7003930.051,0.0), Point(445278.988,7004016.523,0.0),
        Point(445295.313,7004031.801,0.0), Point(445376.923,7004108.181,0.0), Point(445391.041,7004120.899,0.0),
        Point(445405.631,7004133.071,0.0), Point(445417.266,7004142.049,0.0)),452278,New,UnknownOwnerRoad,
      SuravageLinkInterface,313.38119201522017,0,9L,false, None, 85088L)
    ProjectLinkSplitter.findMatchingGeometrySegment(suravage, template) should be (None)
  }
}
