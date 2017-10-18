package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, Vector3d}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.TowardsDigitizing
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.RoadType.PublicRoad
import fi.liikennevirasto.viite.dao.Discontinuity.Continuous
import fi.liikennevirasto.viite.dao._
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

class ProjectDeltaCalculatorSpec  extends FunSuite with Matchers{
  private def createRoadAddress(start: Long, distance: Long) = {
    RoadAddress(id = start, roadNumber = 5, roadPartNumber = 205, roadType = PublicRoad, track = Track.Combined,
      discontinuity = Continuous, startAddrMValue = start, endAddrMValue = start+distance, lrmPositionId = start, linkId = start,
      startMValue = 0.0, endMValue = distance.toDouble, sideCode = TowardsDigitizing, adjustedTimestamp = 0L,
      geometry = Seq(Point(0.0, start), Point(0.0, start+distance)), linkGeomSource = NormalLinkInterface, ely = 8)
  }
  private val project: RoadAddressProject = RoadAddressProject(13L, ProjectState.Incomplete, "foo", "user", DateTime.now(), "user", DateTime.now(),
    DateTime.now(), "", Seq(), None, None)
  private def createTransferProjectLink(start: Long, distance: Long) = {
    toProjectLinkWithMove(project, LinkStatus.Transfer)(createRoadAddress(start, distance))
  }

  private def toProjectLinkWithMove(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue + project.id, roadAddress.endAddrMValue + project.id, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, status,
      roadAddress.roadType, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), roadAddress.id, roadAddress.ely)
  }

  private def toProjectLink(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, status,
      roadAddress.roadType, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), roadAddress.id, roadAddress.ely)
  }

  test("Multiple transfers on single road part") {
    val transferSource1 = (0 to 10).map(x => createRoadAddress(x*10, 10))
    val transferSource2 = (12 to 15).map(x => createRoadAddress(x*10, 10))
    val transferTarget1 = (0 to 10).map(x => createTransferProjectLink(x*10, 10))
    val transferTarget2 = (12 to 15).map(x => createTransferProjectLink(x*10, 10))
    val mapping =
      ProjectDeltaCalculator.partition(transferSource1 ++ transferSource2, transferTarget1 ++ transferTarget2)
    mapping.foreach{ elem =>
      elem._1.startMAddr should be (elem._2.startMAddr - project.id)
      elem._1.endMAddr should be (elem._2.endMAddr - project.id)
      elem._1.track should be (elem._2.track)
    }
  }

  test("Unchanged + transfer + transfer") {
    val addresses = (0 to 10).map(i => createRoadAddress(i*10, 10L))
    val addresses2 = (11 to 21).map(i => createRoadAddress(i*10, 10L)).map(a => a.copy(roadPartNumber=206, startAddrMValue = a.startAddrMValue-110L,
      endAddrMValue = a.endAddrMValue - 110L))
    val (transferLinks1, transferLinks2) = addresses2.map(toProjectLink(project, LinkStatus.Transfer)).partition(_.startAddrMValue == 0L)
    val projectLinks = addresses.map(toProjectLink(project, LinkStatus.UnChanged)) ++
      transferLinks1.map(l => l.copy(roadPartNumber = 205, startAddrMValue = 110L, endAddrMValue=120L)) ++
      transferLinks2.map(l => l.copy(startAddrMValue = l.startAddrMValue - 10L, endAddrMValue = l.endAddrMValue - 10L))

    val partitions = ProjectDeltaCalculator.partition(addresses++addresses2, projectLinks)
    partitions should have size(3)
    val start205 = partitions.find(p => p._1.roadPartNumberStart == 205 && p._2.roadPartNumberStart == 205)
    val to205 = partitions.find(p => p._1.roadPartNumberStart == 206 && p._2.roadPartNumberStart == 205)
    val remain205 = partitions.find(p => p._1.roadPartNumberStart == 206 && p._2.roadPartNumberStart == 206)

    start205.size should be (1)
    to205.size should be (1)
    remain205.size should be (1)

    start205.map(x => (x._1.startMAddr, x._2.startMAddr, x._1.endMAddr, x._2.endMAddr)) should be (Some((0L, 0L, 110L, 110L)))
  }

  test("Numbering operation") {
    val addresses = (0 to 10).map(i => createRoadAddress(i*10, 10L))
    val numberingLinks = addresses.map(_.copy(roadNumber = 12345, roadPartNumber = 1)).map(toProjectLink(project, LinkStatus.Numbering))

    val partitions = ProjectDeltaCalculator.partition(addresses, numberingLinks)
    partitions should have size(1)
    val correctRoadNumber = partitions.find(p => p._1.roadNumber == 5 && p._2.roadNumber == 12345)
    val correctRoadPartNumber = partitions.find(p => p._1.roadPartNumberStart == 205 && p._1.roadPartNumberEnd == 205 && p._2.roadPartNumberStart == 1 && p._2.roadPartNumberEnd == 1)
    correctRoadNumber.size should be (1)
    correctRoadPartNumber.size should be (1)

    correctRoadNumber.get._1.track should be (correctRoadNumber.get._2.track)
    correctRoadNumber.get._1.discontinuity should be (correctRoadNumber.get._2.discontinuity)
    correctRoadNumber.map(x => (x._1.startMAddr, x._2.startMAddr, x._1.endMAddr, x._2.endMAddr)) should be (Some((0L, 0L, 110L, 110L)))
  }

  test("2 track termination + transfer") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L)).map(_.copy(track = Track.RightSide))
    val addresses2 = (0 to 11).map(i => createRoadAddress(i*10, 10L)).map(l => l.copy(track = Track.LeftSide, id=l.id+1))
    val terminations = Seq(addresses.head, addresses2.head)
    val transfers = addresses.tail ++ addresses2.tail
    val projectLinks =
      terminations.map(toProjectLink(project, LinkStatus.Terminated)) ++
        transfers.map(t => {
          val d = if (t.track == Track.RightSide) 12 else 10
          toProjectLink(project, LinkStatus.Transfer)(t.copy(startAddrMValue = t.startAddrMValue - d,
            endAddrMValue = t.endAddrMValue - d))
        })

    val transLinks = projectLinks.filter(_.status != LinkStatus.Terminated)
    val termPart = ProjectDeltaCalculator.partition(terminations)
    termPart should have size(2)
    termPart.foreach(x => {
      x.startMAddr should be (0L)
      x.endMAddr should be (11L)
    })

    val transferParts = ProjectDeltaCalculator.partition(transfers, transLinks)
    transferParts should have size(2)
    transferParts.foreach(x => {
      val (fr, to) = x
      fr.startMAddr should be (11L)
      to.startMAddr should be (0L)
      fr.endMAddr should be (120L)
      to.endMAddr should be (109L)
    })
  }

  test("unchanged + 2 track termination") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L)).map(_.copy(track = Track.RightSide))
    val addresses2 = (0 to 11).map(i => createRoadAddress(i*10, 10L)).map(l => l.copy(track = Track.LeftSide, id=l.id+1))
    val terminations = Seq(addresses.last, addresses2.last)
    val unchanged = addresses.init ++ addresses2.init
    val projectLinks =
      terminations.map(toProjectLink(project, LinkStatus.Terminated)) ++
        unchanged.map(toProjectLink(project, LinkStatus.UnChanged))

    val uncLinks = projectLinks.filter(_.status != LinkStatus.Terminated)
    val termPart = ProjectDeltaCalculator.partition(terminations)
    termPart should have size(2)
    termPart.foreach(x => {
      x.startMAddr should be (109L)
      x.endMAddr should be (120L)
    })

    val transferParts = ProjectDeltaCalculator.partition(unchanged, uncLinks)
    transferParts should have size(2)
    transferParts.foreach(x => {
      val (fr, to) = x
      fr.startMAddr should be (to.startMAddr)
      fr.endMAddr should be (to.endMAddr)
    })
  }

  test("unchanged + 2 track termination + transfer") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L)).map(_.copy(track = Track.RightSide))
    val addresses2 = (0 to 11).map(i => createRoadAddress(i*10, 10L)).map(l => l.copy(track = Track.LeftSide, id=l.id+1))
    val terminations = Seq(addresses(4), addresses(5), addresses2(5), addresses2(6))
    val unchanged = addresses.take(3) ++ addresses2.take(4)
    val transfers = addresses.drop(5) ++ addresses2.drop(6)

    val newLinks = Seq(ProjectLink(981, 5, 205, Track.RightSide,
      Discontinuity.MinorDiscontinuity, 36, 49, None, None,
      modifiedBy=Option(project.createdBy), 0L, 981, 0.0, 12.1,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 36.0), Point(0.0, 48.1)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 12.1, -1L, 8), ProjectLink(982, 5, 205, Track.LeftSide,
      Discontinuity.MinorDiscontinuity, 40, 53, None, None,
      modifiedBy=Option(project.createdBy), 0L, 982, 0.0, 12.2,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 36.0), Point(0.0, 48.2)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 12.2, -1L, 8), ProjectLink(983, 5, 205, Track.RightSide,
      Discontinuity.MinorDiscontinuity, 109, 124, None, None,
      modifiedBy=Option(project.createdBy), 0L, 983, 0.0, 15.2,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 120.0), Point(0.0, 135.2)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 15.2, -1L, 8), ProjectLink(984, 5, 205, Track.LeftSide,
      Discontinuity.MinorDiscontinuity, 113, 127, None, None,
      modifiedBy=Option(project.createdBy), 0L, 984, 0.0, 14.2,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 120.0), Point(0.0, 135.2)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 14.2, -1L, 8)
    )
    val projectLinks =
      terminations.map(toProjectLink(project, LinkStatus.Terminated)) ++
        unchanged.map(toProjectLink(project, LinkStatus.UnChanged)) ++
        transfers.map(toProjectLink(project, LinkStatus.Transfer)).map(l => {
          val d = if (l.track == Track.RightSide) 11L else 7L
          l.copy(startAddrMValue = l.startAddrMValue - d, endAddrMValue = l.endAddrMValue - d)
        }) ++
        newLinks

    val (uncLinks, trfLinks) = projectLinks.filter(l =>
      l.status != LinkStatus.Terminated && l.status != LinkStatus.New).partition(_.status == LinkStatus.UnChanged)
    val termPart = ProjectDeltaCalculator.partition(terminations)
    termPart should have size(2)
    termPart.foreach(x => {
      x.startMAddr should be (49L)
      x.endMAddr should be (71L)
    })

    val uncParts = ProjectDeltaCalculator.partition(unchanged, uncLinks)
    uncParts should have size(2)
    uncParts.foreach(x => {
      val (fr, to) = x
      fr.startMAddr should be (0L)
      fr.endMAddr should be (38L)
    })

    val transferParts = ProjectDeltaCalculator.partition(transfers, trfLinks)
    transferParts should have size(2)
    transferParts.foreach(x => {
      val (fr, to) = x
      fr.startMAddr should be (60L)
      fr.endMAddr should be (120L)
      to.startMAddr should be (51L)
      to.endMAddr should be (111L)
    })
    val newParts = ProjectDeltaCalculator.partition(newLinks)
    newParts should have size(4)
    newParts.filter(_.startMAddr < 100).foreach(to => {
      to.startMAddr should be (38)
      to.endMAddr should be (51)
    })
    newParts.filter(_.startMAddr >= 100).foreach(to => {
      to.startMAddr should be (111)
      to.endMAddr should be (125)
    })
  }

  test("unchanged with road type change in between + new") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L)).map( ra =>
      if (ra.id > 50)
        ra
      else
        ra.copy(roadType = RoadType.MunicipalityStreetRoad)
    )
    val unchanged = addresses.map(toProjectLink(project, LinkStatus.UnChanged))

    val newLinks = Seq(ProjectLink(981, 5, 205, Track.Combined,
      Discontinuity.MinorDiscontinuity, 120, 130, None, None,
      modifiedBy=Option(project.createdBy), 0L, 981, 0.0, 12.1,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 36.0), Point(0.0, 48.1)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 12.1, -1L, 8))

    val uncParts = ProjectDeltaCalculator.partition(addresses, unchanged)
    uncParts should have size(2)
    uncParts.foreach(x => {
      val (fr, to) = x
      (fr.startMAddr == 60 || fr.endMAddr == 60) should be (true)
      (to.startMAddr == 60 || to.endMAddr == 60) should be (true)
      if (fr.startMAddr == 0L)
        fr.roadType should be (RoadType.MunicipalityStreetRoad)
      else
        fr.roadType should be (RoadType.PublicRoad)
      if (to.startMAddr == 0L)
        to.roadType should be (RoadType.MunicipalityStreetRoad)
      else
        to.roadType should be (RoadType.PublicRoad)
    })
    val newParts = ProjectDeltaCalculator.partition(newLinks)
    newParts should have size(1)
    newParts.foreach(to => {
      to.startMAddr should be (120)
      to.endMAddr should be (130)
    })
  }

  test("update ely, road type and discontinuity") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L))
    val links = addresses.map(toProjectLink(project, LinkStatus.UnChanged))

    val partitioned = ProjectDeltaCalculator.partition(links.map(_.copy(ely = 5)))
    partitioned
  }
}
