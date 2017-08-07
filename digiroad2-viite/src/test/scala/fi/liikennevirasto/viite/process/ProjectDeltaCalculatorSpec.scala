package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode.TowardsDigitizing
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point, RoadLinkService}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity.{Continuous, Discontinuous}
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.{LinkStatus, _}
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ProjectDeltaCalculatorSpec  extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val roadAddressService = new RoadAddressService(mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }
  val projectService = new ProjectService(roadAddressService, mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }
  private def toProjectLink(project: RoadAddressProject)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, LinkStatus.NotHandled, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry))
  }

  test("MValues && AddressMValues && CalibrationPoints calculation for new road addresses") {
    val idRoad0 = 0L
    val idRoad1 = 1L
    val idRoad2 = 2L
    val idRoad3 = 3L
    val projectId = 1
    val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("1972-03-03"), DateTime.parse("2700-01-01"), "Some additional info", List.empty[ReservedRoadPart], None)
    val projectLink0 = toProjectLink(rap)(RoadAddress(idRoad0, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
    val projectLink1 = toProjectLink(rap)(RoadAddress(idRoad1, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12346L, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 39.8), Point(0.0, 30.0)), LinkGeomSource.NormalLinkInterface))
    val projectLink2 = toProjectLink(rap)(RoadAddress(idRoad2, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12347L, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 20.2), Point(0.0, 30.0)), LinkGeomSource.NormalLinkInterface))
    val projectLink3 = toProjectLink(rap)(RoadAddress(idRoad3, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12348L, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 9.8), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface))

    val projectLinkSeq = Seq(projectLink0, projectLink1, projectLink2, projectLink3)
    var linkLengths: Map[RoadPart, Seq[RoadLinkLength]] = Map.empty
    projectLinkSeq.foreach(pl => {
      val index = RoadPart(pl.roadNumber, pl.roadPartNumber)
      linkLengths = linkLengths + ( index -> Seq(RoadLinkLength(pl.linkId, pl.geometryLength)).++(linkLengths.getOrElse(index,Seq.empty)))
    })

    val output = ProjectDeltaCalculator.determineMValues(projectLinkSeq, linkLengths, Seq())
    output.length should be(4)

    output(3).id should be(idRoad0)
    output(3).startMValue should be(0.0)
    output(3).endMValue should be(output(3).geometryLength)
    output(3).startAddrMValue should be(Math.round(output(1).startMValue))
    output(3).endAddrMValue should be(Math.round(output(1).endMValue))

    output(2).id should be(idRoad3)
    output(2).startMValue should be(0.0)
    output(2).endMValue should be(output(2).geometryLength)
    output(2).startAddrMValue should be(Math.round(output(3).endAddrMValue))
    output(2).endAddrMValue should be(Math.round(output(2).startAddrMValue+output(2).geometryLength))

    output(1).id should be(idRoad2)
    output(1).startMValue should be(0.0)
    output(1).endMValue should be(output(1).geometryLength)
    output(1).startAddrMValue should be(Math.round(output(2).endAddrMValue))
    output(1).endAddrMValue should be(Math.round(output(1).startAddrMValue+output(1).geometryLength))

    output(0).id should be(idRoad1)
    output(0).startMValue should be(0.0)
    output(0).endMValue should be(output(0).geometryLength)
    output(0).startAddrMValue should be(Math.round(output(1).endAddrMValue))
    output(0).endAddrMValue should be(Math.round(output(0).startAddrMValue+output(0).geometryLength))

    val outputCP = projectService.addCalibrationMarkers(output)
    outputCP(3).id should be(idRoad0)
    outputCP(3).calibrationPoints should be(Some(CalibrationPoint(12345,0.0,0)), None)

    outputCP(2).id should be(idRoad3)
    outputCP(2).calibrationPoints should be(None, None)

    outputCP(1).id should be(idRoad2)
    outputCP(1).calibrationPoints should be(None, None)

    outputCP(0).id should be(idRoad1)
    outputCP(0).calibrationPoints should be(None,Some(CalibrationPoint(12346,0.0,40)))

  }

  test("Mvalues calculation for complex case") {
    val idRoad0 = 0L //   |
    val idRoad1 = 1L //  /
    val idRoad2 = 2L //    \
    val idRoad3 = 3L //  \
    val idRoad4 = 4L //    /
    val idRoad5 = 5L //   |
    val idRoad6 = 6L //  /
    val idRoad7 = 7L //    \
    val idRoad8 = 8L //   |
    val projectId = 1
    val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"),
      "TestUser", DateTime.parse("1972-03-03"), DateTime.parse("2700-01-01"), "Some additional info",
      List.empty[ReservedRoadPart], None)
    val projectLink0 = toProjectLink(rap)(RoadAddress(idRoad0, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad0, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
    val projectLink1 = toProjectLink(rap)(RoadAddress(idRoad1, 5, 1, RoadType.Unknown, Track.RightSide, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad1, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 9.8), Point(-2.0, 20.2)), LinkGeomSource.NormalLinkInterface))
    val projectLink2 = toProjectLink(rap)(RoadAddress(idRoad2, 5, 1, RoadType.Unknown, Track.LeftSide, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad2, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 9.8), Point(2.0, 19.2)), LinkGeomSource.NormalLinkInterface))
    val projectLink3 = toProjectLink(rap)(RoadAddress(idRoad3, 5, 1, RoadType.Unknown, Track.RightSide, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad3, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(-2.0, 20.2), Point(1.0, 30.0)), LinkGeomSource.NormalLinkInterface))
    val projectLink4 = toProjectLink(rap)(RoadAddress(idRoad4, 5, 1, RoadType.Unknown, Track.LeftSide, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad4, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(2.0, 19.2), Point(1.0, 30.0)), LinkGeomSource.NormalLinkInterface))
    val projectLink5 = toProjectLink(rap)(RoadAddress(idRoad5, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad5, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(1.0, 30.0), Point(0.0, 48.0)), LinkGeomSource.NormalLinkInterface))
    val projectLink6 = toProjectLink(rap)(RoadAddress(idRoad6, 5, 1, RoadType.Unknown, Track.RightSide, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad6, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 48.0), Point(2.0, 68.0), Point(0.0, 96.0)), LinkGeomSource.NormalLinkInterface))
    val projectLink7 = toProjectLink(rap)(RoadAddress(idRoad7, 5, 1, RoadType.Unknown, Track.LeftSide, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad7, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 48.0), Point(-2.0, 68.0), Point(0.0, 96.0)), LinkGeomSource.NormalLinkInterface))
    val projectLink8 = toProjectLink(rap)(RoadAddress(idRoad8, 5, 1, RoadType.Unknown, Track.Combined, Continuous,
      0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, idRoad8, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 96.0), Point(0.0, 148.0)), LinkGeomSource.NormalLinkInterface))

    val projectLinkSeq = Seq(projectLink0, projectLink1, projectLink2, projectLink3, projectLink4, projectLink5, projectLink6, projectLink7, projectLink8)
    var linkLengths: Map[RoadPart, Seq[RoadLinkLength]] = Map.empty
    projectLinkSeq.foreach(pl => {
      val index = RoadPart(pl.roadNumber, pl.roadPartNumber)
      linkLengths = linkLengths + ( index -> Seq(RoadLinkLength(pl.linkId, pl.geometryLength)).++(linkLengths.getOrElse(index,Seq.empty)))
    })
    val output = ProjectDeltaCalculator.determineMValues(projectLinkSeq, linkLengths, Seq()).sortBy(_.linkId)
    output.foreach(println)
    output.length should be(9)
    output.foreach(pl => pl.sideCode == TowardsDigitizing should be (true))
    val start = output.find(_.id==idRoad0).get
    start.calibrationPoints._1.nonEmpty should be (true)
    start.calibrationPoints._2.nonEmpty should be (true)

    output.filter(pl => pl.id==idRoad1 || pl.id == idRoad2).foreach{ pl =>
      pl.calibrationPoints._1.nonEmpty should be(true)
      pl.calibrationPoints._2.nonEmpty should be(false)
    }

    output.filter(pl => pl.id==idRoad3 || pl.id == idRoad4).foreach{ pl =>
      pl.calibrationPoints._1.nonEmpty should be(false)
      pl.calibrationPoints._2.nonEmpty should be(true)
    }

    output.filter(pl => pl.id > idRoad4).foreach{ pl =>
      pl.calibrationPoints._1.nonEmpty should be(true)
      pl.calibrationPoints._2.nonEmpty should be(true)
    }

  }
}
