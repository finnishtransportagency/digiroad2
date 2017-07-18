package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity.Discontinuous
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.{LinkStatus, _}
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ProjectDeltaCalculatorSpec  extends FunSuite with Matchers{

  private def toProjectLink(project: RoadAddressProject)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geom, project.id, LinkStatus.NotHandled, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geom))
  }

  test("validate correct mValuesCreation") {
    val idRoad0 = 0L
    val idRoad1 =1L
    val idRoad2 = 2L
    val projectId = 1
    val baseLength = 100L
    val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("1972-03-03"), DateTime.parse("2700-01-01"), "Some additional info", List.empty[ReservedRoadPart], None)
    val projectLink0 = toProjectLink(rap)(RoadAddress(idRoad0, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
    val projectLink1 = toProjectLink(rap)(RoadAddress(idRoad1, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
    val projectLink2 = toProjectLink(rap)(RoadAddress(idRoad2, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))

    val projectLinkSeq = Seq(projectLink0, projectLink1, projectLink2)
    var linkLengths: Map[RoadPartBasis, Seq[RoadPartLengths]] = Map.empty
    projectLinkSeq.foreach(pl => {
      val index = new RoadPartBasis(pl.roadNumber, pl.roadPartNumber)
      linkLengths = linkLengths + ( index -> Seq(RoadPartLengths(pl.linkId, baseLength)))
    })

    val output = ProjectDeltaCalculator.determineMValues(projectLinkSeq, linkLengths)
    output.length should be(3)
	
    output(0).id should be(idRoad0)
    output(0).startMValue should be(0.0)
    output(0).endMValue should be(baseLength)
    output(0).startAddrMValue should be(Math.round(output(0).startMValue))
    output(0).endAddrMValue should be(Math.round(output(0).endMValue))

    output(1).id should be(idRoad1)
    output(1).startMValue should be(baseLength)
    output(1).endMValue should be(baseLength*2)
    output(1).startAddrMValue should be(Math.round(output(1).startMValue))
    output(1).endAddrMValue should be(Math.round(output(1).endMValue))

    output(2).id should be(idRoad2)
    output(2).startMValue should be(baseLength*2)
    output(2).endMValue should be(baseLength*3)
    output(2).startAddrMValue should be(Math.round(output(2).startMValue))
    output(2).endAddrMValue should be(Math.round(output(2).endMValue))
  }

  test("addressMValues N MValues calculation for new road addresses") {
    val idRoad0 = 0L
    val idRoad1 =1L
    val idRoad2 = 2L
    val idRoad3 = 3L
    val projectId = 1
    //addressMValues and MValues unknown before calculation
    val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("1972-03-03"), DateTime.parse("2700-01-01"), "Some additional info", List.empty[ReservedRoadPart], None)
    val projectLink0 = toProjectLink(rap)(RoadAddress(idRoad0, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
    val projectLink1 = toProjectLink(rap)(RoadAddress(idRoad1, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 30.0), Point(0.0, 39.8)), LinkGeomSource.NormalLinkInterface))
    val projectLink2 = toProjectLink(rap)(RoadAddress(idRoad2, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 20.2), Point(0.0, 30.0)), LinkGeomSource.NormalLinkInterface))
    val projectLink3 = toProjectLink(rap)(RoadAddress(idRoad3, 5, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 0L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 0.0, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0, 9.8), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface))

    val projectLinkSeq = Seq(projectLink0, projectLink1, projectLink2, projectLink3)
    var linkLengths: Map[RoadPartBasis, Seq[RoadPartLengths]] = Map.empty
    projectLinkSeq.foreach(pl => {
      val index = new RoadPartBasis(pl.roadNumber, pl.roadPartNumber)
      linkLengths = linkLengths + ( index -> Seq(RoadPartLengths(pl.linkId, pl.geometryLength)))
    })

    val output = ProjectDeltaCalculator.determineMValues(projectLinkSeq, linkLengths)
    output.length should be(4)

    output(0).id should be(idRoad0)
    output(0).startMValue should be(0.0)
    output(0).endMValue should be(output(0).geometryLength)
    output(0).startAddrMValue should be(Math.round(output(0).startMValue))
    output(0).endAddrMValue should be(Math.round(output(0).endMValue))

    output(1).id should be(idRoad3)
    output(1).startMValue should be(0.0)
    output(1).endMValue should be(output(1).geometryLength)
    output(1).startAddrMValue should be(Math.round(output(0).endAddrMValue))
    output(1).endAddrMValue should be(Math.round(output(1).startAddrMValue+output(1).geometryLength))

    output(2).id should be(idRoad2)
    output(2).startMValue should be(0.0)
    output(2).endMValue should be(output(2).geometryLength)
    output(2).startAddrMValue should be(Math.round(output(1).endAddrMValue))
    output(2).endAddrMValue should be(Math.round(output(2).startAddrMValue+output(2).geometryLength))

    output(3).id should be(idRoad1)
    output(3).startMValue should be(0.0)
    output(3).endMValue should be(output(3).geometryLength)
    output(3).startAddrMValue should be(Math.round(output(2).endAddrMValue))
    output(3).endAddrMValue should be(Math.round(output(3).startAddrMValue+output(3).geometryLength))
  }

}
