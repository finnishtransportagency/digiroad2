package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.{ReservedRoadPart, RoadType}
import fi.liikennevirasto.viite.dao.Discontinuity.Continuous
import fi.liikennevirasto.viite.dao._
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

class TrackSectionOrderSpec extends FunSuite with Matchers {
  val projectId = 1
  val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"),
    "TestUser", DateTime.parse("1972-03-03"), DateTime.parse("2700-01-01"), "Some additional info",
    List.empty[ReservedRoadPart], None)

  private def toProjectLink(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, status, RoadType.PublicRoad,
      roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), roadAddress.id, roadAddress.ely)
  }

  test("orderProjectLinksTopologyByGeometry") {
    val idRoad0 = 0L //   >
    val idRoad1 = 1L //     <
    val idRoad2 = 2L //   >
    val idRoad3 = 3L //     <
    val projectLink0 = toProjectLink(rap, LinkStatus.New)(RoadAddress(idRoad0, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad0, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(20.0, 10.0), Point(28, 15)), LinkGeomSource.NormalLinkInterface, 8))
    val projectLink1 = toProjectLink(rap, LinkStatus.New)(RoadAddress(idRoad1, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad1, 0.0, 0.0, SideCode.AgainstDigitizing, 0, (None, None), false,
      Seq(Point(42, 14),Point(28, 15)), LinkGeomSource.NormalLinkInterface, 8))
    val projectLink2 = toProjectLink(rap, LinkStatus.New)(RoadAddress(idRoad2, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad2, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(42, 14), Point(75, 19.2)), LinkGeomSource.NormalLinkInterface, 8))
    val projectLink3 = toProjectLink(rap, LinkStatus.New)(RoadAddress(idRoad3, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad3, 0.0, 0.0, SideCode.AgainstDigitizing, 0, (None, None), false,
      Seq(Point(103.0, 15.0),Point(75, 19.2)), LinkGeomSource.NormalLinkInterface, 8))
    val list = List(projectLink0, projectLink1, projectLink2, projectLink3)
    val (ordered, _) = TrackSectionOrder.orderProjectLinksTopologyByGeometry((Point(20.0, 10.0), Point(20.0, 10.0)), list)
    // Test that the result is not dependent on the order of the links
    list.permutations.foreach(l => {
      TrackSectionOrder.orderProjectLinksTopologyByGeometry((Point(20.0, 10.0), Point(20.0, 10.0)), l)._1 should be(ordered)
    })
  }

  test("combined track with one ill-fitting link direction after discontinuity") {
    val points = Seq(Seq(Point(100,110), Point(75, 130), Point(50,159)),
      Seq(Point(50,160), Point(0, 110), Point(0,60)),
      Seq(Point(0,60), Point(-50, 80), Point(-100, 110)),
      Seq(Point(-100,110), Point(-120, 140), Point(-150,210)))
    val geom = points.map(g =>
      if (g.head.y > g.last.y)
        g.reverse
      else g
    )
    val list = geom.zip(0 to 3).map{ case (g, id) =>
      toProjectLink(rap, LinkStatus.New)(RoadAddress(id, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
        0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, id, 0.0, 0.0, SideCode.Unknown, 0, (None, None), false,
        g, LinkGeomSource.NormalLinkInterface, 5))
    }
    val (ordered, _) = TrackSectionOrder.orderProjectLinksTopologyByGeometry((Point(100,110), Point(100,110)), list)
    ordered.map(_.id) should be (Seq(0L, 1L, 2L, 3L))
  }

  test("roundabout with Towards facing starting link") {
    val points = Seq(Seq(Point(150.00, 110.00),Point(146.19, 129.13),Point(135.36, 145.36)),
      Seq(Point(135.36, 145.36),Point(119.13, 156.19),Point(100.00, 160.00)),
      Seq(Point(100.00, 160.00),Point(80.87, 156.19),Point(64.64, 145.36)),
      Seq(Point(64.64, 145.36),Point(53.81, 129.13),Point(50.00, 110.00)),
      Seq(Point(50.00, 110.00),Point(53.81, 90.87),Point(64.64, 74.64)),
      Seq(Point(64.64, 74.64),Point(80.87, 63.81),Point(100.00, 60.00)),
      Seq(Point(100.00, 60.00),Point(119.13, 63.81),Point(135.36, 74.64)),
      Seq(Point(135.36, 74.64),Point(146.19, 90.87),Point(150.00, 110.00)))
    val geom = points.map(g =>
      if (g.head.y > g.last.y)
        g.reverse
      else g
    )
    val list = geom.zip(0 to 7).map{ case (g, id) =>
      toProjectLink(rap, LinkStatus.New)(RoadAddress(id, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
        0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, id, 0.0, 0.0, SideCode.Unknown, 0, (None, None), false,
        g, LinkGeomSource.NormalLinkInterface, 5))
    }
    TrackSectionOrder.isRoundabout(list) should be (true)
    TrackSectionOrder.isRoundabout(list.init) should be (false)
    TrackSectionOrder.isRoundabout(list.tail) should be (false)
    val result = TrackSectionOrder.orderRoundAboutLinks(list)
    result should have size(8)
    result.head.sideCode should be (TowardsDigitizing)
    result.forall(_.sideCode == result.head.sideCode) should be (false)
    result.head.geometry should be (list.head.geometry)
  }

  test("invalid roundabout geometry throws exception") {
    val points = Seq(Seq(Point(150.00, 110.00),Point(146.19, 129.13),Point(135.36, 145.36)),
      Seq(Point(135.36, 145.36),Point(119.13, 156.19),Point(100.00, 160.00)),
      Seq(Point(100.00, 160.00),Point(80.87, 156.19),Point(80, 140)),
      Seq(Point(80, 140), Point(90, 130),Point(70, 100)),
      Seq(Point(70, 100),Point(60.00, 120.00),Point(50.00, 110.00)),
      Seq(Point(50.00, 110.00),Point(60.00, 83.81),Point(100.00, 60.00)),
      Seq(Point(100.00, 60.00),Point(119.13, 63.81),Point(135.36, 74.64)),
      Seq(Point(135.36, 74.64),Point(146.19, 90.87),Point(150.00, 110.00)))
    val geom = points.map(g =>
      if (g.head.y > g.last.y)
        g.reverse
      else g
    )
    val list = geom.zip(0 to 7).map{ case (g, id) =>
      toProjectLink(rap, LinkStatus.New)(RoadAddress(id, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
        0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, id, 0.0, 0.0, SideCode.Unknown, 0, (None, None), false,
        g, LinkGeomSource.NormalLinkInterface, 5))
    }
    TrackSectionOrder.isRoundabout(list) should be (true)
    intercept[InvalidGeometryException] {
      TrackSectionOrder.orderRoundAboutLinks(list)
    }
  }

  test("roundabout with Against facing starting link") {
    val points = Seq(Seq(Point(100.00, 160.00),Point(80.87, 156.19),Point(64.64, 145.36)),
      Seq(Point(64.64, 145.36),Point(53.81, 129.13),Point(50.00, 110.00)),
      Seq(Point(50.00, 110.00),Point(53.81, 90.87),Point(64.64, 74.64)),
      Seq(Point(64.64, 74.64),Point(80.87, 63.81),Point(100.00, 60.00)),
      Seq(Point(100.00, 60.00),Point(119.13, 63.81),Point(135.36, 74.64)),
      Seq(Point(135.36, 74.64),Point(146.19, 90.87),Point(150.00, 110.00)),
      Seq(Point(150.00, 110.00),Point(146.19, 129.13),Point(135.36, 145.36)),
      Seq(Point(135.36, 145.36),Point(119.13, 156.19),Point(100.00, 160.00))
    )
    val geom = points.map(g =>
      if (g.head.y > g.last.y)
        g.reverse
      else g
    )
    val list = geom.zip(0 to 7).map{ case (g, id) =>
      toProjectLink(rap, LinkStatus.New)(RoadAddress(id, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
        0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, id, 0.0, 0.0, SideCode.Unknown, 0, (None, None), false,
        g, LinkGeomSource.NormalLinkInterface, 5))
    }
    TrackSectionOrder.isRoundabout(list) should be (true)
    TrackSectionOrder.isRoundabout(list.init) should be (false)
    TrackSectionOrder.isRoundabout(list.tail) should be (false)
    val result = TrackSectionOrder.orderRoundAboutLinks(list)
    result should have size(8)
    result.head.sideCode should be (AgainstDigitizing)
    result.forall(_.sideCode == result.head.sideCode) should be (false)
    result.head.geometry should be (list.head.geometry)
  }

  test("Ramp doesn't pass as a roundabout") {
    val points = Seq(Seq(Point(150.00, 40.00),Point(100.00, 160.00),Point(80.87, 156.19),Point(64.64, 145.36)),
      Seq(Point(64.64, 145.36),Point(53.81, 129.13),Point(50.00, 110.00)),
      Seq(Point(50.00, 110.00),Point(53.81, 90.87),Point(90.0, 74.64)),
      Seq(Point(90.0, 74.64), Point(160.00, 75.0))
    )
    val geom = points.map(g =>
      if (g.head.y > g.last.y)
        g.reverse
      else g
    )
    val list = geom.zip(0 to 7).map{ case (g, id) =>
      toProjectLink(rap, LinkStatus.New)(RoadAddress(id, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
        0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, id, 0.0, 0.0, SideCode.Unknown, 0, (None, None), false,
        g, LinkGeomSource.NormalLinkInterface, 5))
    }
    list.permutations.forall(l => !TrackSectionOrder.isRoundabout(l)) should be (true)
  }
}
