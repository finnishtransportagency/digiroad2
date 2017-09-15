package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
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
      geometry = Seq(Point(0.0, start), Point(0.0, start+distance)), linkGeomSource = NormalLinkInterface)
  }
  private val project: RoadAddressProject = RoadAddressProject(13L, ProjectState.Incomplete, "foo", "user", DateTime.now(), "user", DateTime.now(),
    DateTime.now(), "", Seq(), None, None)
  private def createTransferProjectLink(start: Long, distance: Long) = {
    toProjectLink(project, LinkStatus.Transfer)(createRoadAddress(start, distance))
  }

  private def toProjectLink(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue + project.id, roadAddress.endAddrMValue + project.id, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, status, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry))
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
}
