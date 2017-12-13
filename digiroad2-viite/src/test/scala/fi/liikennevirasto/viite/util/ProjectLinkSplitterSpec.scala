package fi.liikennevirasto.viite.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.{NormalLinkInterface, SuravageLinkInterface}
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.util.Track.{Combined, Unknown}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite.RoadType.{PublicRoad, UnknownOwnerRoad}
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.Discontinuity.Continuous
import fi.liikennevirasto.viite.dao.LinkStatus.{New, NotHandled}
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink, RoadAddressLinkLike}
import org.joda.time.DateTime
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class ProjectLinkSplitterSpec extends FunSuite with Matchers with BeforeAndAfter {

  val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  val mockProjectService = MockitoSugar.mock[ProjectService]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val roadAddressService = new RoadAddressService(mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }
  val projectService = new ProjectService(roadAddressService, mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }

  val projectServiceWithRoadAddressMock = new ProjectService(mockRoadAddressService, mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }

  after {
    reset(mockRoadLinkService)
  }

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def runWithRollback[T](f: => T): T = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val t = f
      dynamicSession.rollback()
      t
    }
  }

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
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, tLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 0L, 5, false, None, 85088L)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.5, 0.75), LinkStatus.UnChanged,
      LinkStatus.New, 5L, 205L, Track.Combined, Discontinuity.Continuous, 8L, LinkGeomSource.NormalLinkInterface,
      RoadType.PublicRoad, 1L, ProjectCoordinates(0, 1, 1))).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    val (splitA, splitB) = sl.partition(_.status != LinkStatus.New)
    sl should have size (2)
    tl should have size (1)
    val terminatedLink = tl.head
    terminatedLink.status should be (LinkStatus.Terminated)
    terminatedLink.endAddrMValue should be (template.endAddrMValue)
    terminatedLink.startAddrMValue should be (splitB.head.startAddrMValue)
    splitA.head.endAddrMValue should be (splitB.head.startAddrMValue)
    sl.foreach { l =>
      l.endAddrMValue == terminatedLink.startAddrMValue || l.startAddrMValue == terminatedLink.startAddrMValue should be(true)
      l.linkGeomSource should be (LinkGeomSource.SuravageLinkInterface)
      l.status == LinkStatus.New || l.status == LinkStatus.UnChanged || l.status == LinkStatus.Transfer should be (true)
      l.roadNumber should be (template.roadNumber)
      l.roadPartNumber should be (template.roadPartNumber)
      l.sideCode should be (SideCode.TowardsDigitizing)
    }
  }

  test("Incorrect digitization of link should not affect calculation") {
    val sGeom = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(16.0, -0.5), Point(20.0, -0.8))
    val tGeom = Seq(Point(5.0, 0.0), Point(15.0, 0.0), Point(16.0, -1.5), Point(20.0, -4.8)).reverse
    val sLen = GeometryUtils.geometryLength(sGeom)
    val tLen = GeometryUtils.geometryLength(tGeom)
    val suravage = ProjectLink(0L, 0L, 0L, Track.Unknown, Discontinuity.Continuous, 0L, 0L, None, None, None, 0L, 123L, 0.0, sLen,
      SideCode.Unknown, (None, None), false, sGeom, 1L, LinkStatus.NotHandled, RoadType.Unknown, LinkGeomSource.SuravageLinkInterface,
      sLen, 0L, 5, false, None, 85088L)
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, tLen,
      SideCode.TowardsDigitizing, (None, None), false, tGeom, 1L, LinkStatus.NotHandled, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface,
      tLen, 0L, 5, false, None, 85088L)
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.5, -0.75), LinkStatus.UnChanged,
      LinkStatus.New, 5L, 205L, Track.Combined, Discontinuity.Continuous, 8L, LinkGeomSource.NormalLinkInterface,
      RoadType.PublicRoad, 1L, ProjectCoordinates(0, 1, 1))).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    val (splitA, splitB) = sl.partition(_.status != LinkStatus.New)
    sl should have size (2)
    tl should have size (1)
    val terminatedLink = tl.head
    terminatedLink.status should be (LinkStatus.Terminated)
    splitB.head.endAddrMValue should be (splitA.head.startAddrMValue)
    splitA.head.geometry.head should be (template.geometry.last)
    sl.foreach { l =>
      l.startAddrMValue == terminatedLink.endAddrMValue || l.status == New should be(true)
      l.linkGeomSource should be (LinkGeomSource.SuravageLinkInterface)
      l.status == LinkStatus.New || l.status == LinkStatus.UnChanged || l.status == LinkStatus.Transfer should be (true)
      l.roadNumber should be (template.roadNumber)
      l.roadPartNumber should be (template.roadPartNumber)
      l.sideCode should be (SideCode.AgainstDigitizing)
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
    val template = ProjectLink(2L, 5L, 205L, Track.Combined, Discontinuity.Continuous, 1024L, 1040L, None, None, None, 0L, 124L, 0.0, tLen,
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
      l.sideCode should be (SideCode.AgainstDigitizing)
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
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(15.0, 0.0), LinkStatus.Transfer,
      LinkStatus.New, 5L, 205L, Track.Combined, Discontinuity.Continuous, 8L, LinkGeomSource.NormalLinkInterface,
      RoadType.PublicRoad, 1L, ProjectCoordinates(0, 1, 1))).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    sl should have size (2)
    tl should have size (1)
    val (splitA, splitB) = sl.partition(_.status != LinkStatus.New)
    val terminatedLink = tl.head
    terminatedLink.status should be(LinkStatus.Terminated)
    terminatedLink.startAddrMValue should be(template.startAddrMValue)
    terminatedLink.endAddrMValue should be (splitA.head.startAddrMValue)
    splitA.head.endAddrMValue should be (template.endAddrMValue)
    GeometryUtils.areAdjacent(terminatedLink.geometry, splitB.head.geometry) should be(true)
    GeometryUtils.areAdjacent(terminatedLink.geometry, splitA.head.geometry) should be(true)
    GeometryUtils.areAdjacent(terminatedLink.geometry, template.geometry) should be(true)
    sl.foreach { l =>
      l.roadAddressId should be(template.roadAddressId)
      GeometryUtils.areAdjacent(l.geometry, suravage.geometry) should be(true)
      l.roadNumber should be(template.roadNumber)
      l.roadPartNumber should be(template.roadPartNumber)
      l.sideCode should be(SideCode.TowardsDigitizing)
    }

  }

  test("Split by suravage border -> uniq suravage link -> (Newlink length -> 0, Unchangedlink length -> original suravage length)") {
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
    (GeometryUtils.areAdjacent(unChangedLink.geometry.head, sGeom.head) || GeometryUtils.areAdjacent(unChangedLink.geometry.last, sGeom.last)) should be (true)
    GeometryUtils.geometryLength(unChangedLink.geometry) should be (sLen)
    unChangedLink.startAddrMValue should be (template.startAddrMValue)
    unChangedLink.endAddrMValue should be (terminatedLink.startAddrMValue)
  }

  test("Split by suravage border -> uniq suravage link ->  (Newlink length -> original suravage length, Unchangedlink length -> 0)") {
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
    val (sl, tl) = ProjectLinkSplitter.split(suravage, template, SplitOptions(Point(480428.187, 7059183.911), LinkStatus.UnChanged,
      LinkStatus.New, 27L, 22L, Track.Combined, Discontinuity.Continuous, 8L, LinkGeomSource.NormalLinkInterface,
      RoadType.PublicRoad, 1L, ProjectCoordinates(0, 1, 1))).partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
    sl should have size (1)
    tl should have size (1)
    val terminatedLink = tl.head
    val newLink = sl.head
    terminatedLink.status should be (LinkStatus.Terminated)
    terminatedLink.startAddrMValue should be (template.startAddrMValue)
    terminatedLink.endAddrMValue should be (template.endAddrMValue)
    terminatedLink.endMValue should be (template.endMValue)
    newLink.startAddrMValue should be (template.startAddrMValue)
    newLink.endAddrMValue should be <= template.endAddrMValue
    GeometryUtils.areAdjacent(terminatedLink.geometry, newLink.geometry) should be (true)
    GeometryUtils.areAdjacent(newLink.geometry.head, sGeom.head) should be (true)
    GeometryUtils.areAdjacent(newLink.geometry.last, sGeom.last) should be (true)
    GeometryUtils.geometryLength(newLink.geometry) should be (GeometryUtils.geometryLength(sGeom))
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

  test("Preview the split") {
    runWithRollback {
      val projectId = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      val roadLink = RoadLink(1, Seq(Point(0, 0), Point(0, 45.3), Point(0, 87))
        , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
        InUse, NormalLinkInterface)
      val suravageAddressLink = RoadAddressLink(Sequences.nextViitePrimaryKeySeqValue, 2, Seq(Point(0, 0), Point(0, 45.3), Point(0, 123)), 123,
        AdministrativeClass.apply(1), LinkType.apply(1), RoadLinkType.UnknownRoadLinkType, ConstructionType.Planned, LinkGeomSource.SuravageLinkInterface, RoadType.PublicRoad, "testRoad",
        8, None, None, null, 1, 1, Track.Combined.value, 8, Discontinuity.Continuous.value, 0, 123, "", "", 0, 123, SideCode.Unknown, None, None, Anomaly.None, 1)

      when(mockRoadAddressService.getSuravageRoadLinkAddressesByLinkIds(any[Set[Long]])).thenReturn(Seq(suravageAddressLink))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink))
      val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      val templateGeom = toGeomString(Seq(Point(0, 0), Point(0, 45.3), Point(0, 123.5), Point(0.5, 140)))
      sqlu""" insert into LRM_Position(id,start_Measure,end_Measure,Link_id,side_code) Values($lrmPositionId,0,87,1,2) """.execute
      sqlu""" INSERT INTO PROJECT_RESERVED_ROAD_PART (ID, ROAD_NUMBER, ROAD_PART_NUMBER, PROJECT_ID, CREATED_BY) VALUES (${Sequences.nextViitePrimaryKeySeqValue},1,1,$projectId,'""')""".execute
      sqlu""" INSERT INTO PROJECT_LINK (ID, PROJECT_ID, TRACK_CODE, DISCONTINUITY_TYPE, ROAD_NUMBER, ROAD_PART_NUMBER, START_ADDR_M, END_ADDR_M, LRM_POSITION_ID, CREATED_BY, CREATED_DATE, STATUS, GEOMETRY) VALUES (${Sequences.nextViitePrimaryKeySeqValue},$projectId,0,0,1,1,0,87,$lrmPositionId,'testuser',TO_DATE('2017-10-06 14:54:41', 'YYYY-MM-DD HH24:MI:SS'), 0, $templateGeom)""".execute

      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getSuravageRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(toRoadLink(suravageAddressLink)))
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
      val splitOptions = SplitOptions(Point(0, 45.3), LinkStatus.Transfer, LinkStatus.New, 0, 0, Track.Combined, Discontinuity.Continuous, 0, LinkGeomSource.Unknown, RoadType.Unknown, projectId, ProjectCoordinates(0, 45.3, 10))
      val (splitedLinks, errorMessage, vector) = projectServiceWithRoadAddressMock.preSplitSuravageLinkInTX(suravageAddressLink.linkId,  "TestUser", splitOptions)
      errorMessage.isEmpty should be (true)
      splitedLinks.nonEmpty should be (true)
      splitedLinks.get.size should be (3)
      val (sl, tl) = splitedLinks.get.partition(_.linkGeomSource == LinkGeomSource.SuravageLinkInterface)
      val (splitA, splitB) = sl.partition(_.status != LinkStatus.New)
      splitA.size should be (1)
      splitB.size should be (1)
      tl.size should be (1)
      splitA.head.endAddrMValue should be (tl.head.startAddrMValue)
      splitB.head.startAddrMValue should be (splitA.head.endAddrMValue)
    }
  }

  private def toRoadLink(ral: RoadAddressLinkLike): RoadLink = {
    RoadLink(ral.linkId, ral.geometry, ral.length, ral.administrativeClass, 1,
      extractTrafficDirection(ral.sideCode, Track.apply(ral.trackCode.toInt)), ral.linkType, ral.modifiedAt, ral.modifiedBy, Map(
        "MUNICIPALITYCODE" -> BigInt(749), "VERTICALLEVEL" -> BigInt(1), "SURFACETYPE" -> BigInt(1),
        "ROADNUMBER" -> BigInt(ral.roadNumber), "ROADPARTNUMBER" -> BigInt(ral.roadPartNumber)),
      ral.constructionType, ral.roadLinkSource)
  }

  def toGeomString(geometry: Seq[Point]): String = {
    def toBD(d: Double): String = {
      val zeroEndRegex = """(\.0+)$""".r
      val lastZero = """(\.[0-9])0*$""".r
      val bd = BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_UP).toString
      lastZero.replaceAllIn(zeroEndRegex.replaceFirstIn(bd, ""), { m => m.group(0) } )
    }
    geometry.map(p =>
      (if (p.z != 0.0)
        Seq(p.x, p.y, p.z)
      else
        Seq(p.x, p.y)).map(toBD).mkString("[", ",","]")).mkString(",")
  }

  private def extractTrafficDirection(sideCode: SideCode, track: Track): TrafficDirection = {
    (sideCode, track) match {
      case (_, Track.Combined) => TrafficDirection.BothDirections
      case (TowardsDigitizing, Track.RightSide) => TrafficDirection.TowardsDigitizing
      case (TowardsDigitizing, Track.LeftSide) => TrafficDirection.AgainstDigitizing
      case (AgainstDigitizing, Track.RightSide) => TrafficDirection.AgainstDigitizing
      case (AgainstDigitizing, Track.LeftSide) => TrafficDirection.TowardsDigitizing
      case (_, _) => TrafficDirection.UnknownDirection
    }
  }
}
