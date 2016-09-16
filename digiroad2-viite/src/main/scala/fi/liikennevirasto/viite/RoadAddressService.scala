package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.RoadLinkService
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{CalibrationPoint, RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.model.RoadAddressLink

class RoadAddressService(roadLinkService: RoadLinkService) {

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  val RoadNumber = "ROADNUMBER"
  val RoadPartNumber = "ROADPARTNUMBER"

  val HighwayClass = 1
  val MainRoadClass = 2
  val RegionalClass = 3
  val ConnectingClass = 4
  val MinorConnectingClass = 5
  val StreetClass = 6
  val RampsAndRoundAboutsClass = 7
  val PedestrianAndBicyclesClass = 8
  val WinterRoadsClass = 9
  val PathsClass = 10
  val ConstructionSiteTemporaryClass = 11
  val NoClass = 99

  class Contains(r: Range) { def unapply(i: Int): Boolean = r contains i }

  /**
    * Get calibration points for road not in a project
    * @param roadNumber
    * @return
    */
  def getCalibrationPoints(roadNumber: Long) = {
    // TODO: Implementation
    Seq(CalibrationPoint(1, 0.0, 0))
  }

  /**
    * Get calibration points for road including project created ones
    * @param roadNumber
    * @param projectId
    * @return
    */
  def getCalibrationPoints(roadNumber: Long, projectId: Long): Seq[CalibrationPoint] = {
    // TODO: Implementation
    getCalibrationPoints(roadNumber) ++ Seq(CalibrationPoint(2, 0.0, 0))
  }

  def getCalibrationPoints(linkIds: Set[Long]) = {

    linkIds.map(linkId => CalibrationPoint(linkId, 0.0, 0))
  }

  def addRoadAddresses(roadLinks: Seq[RoadLink]) = {
    val linkIds = roadLinks.map(_.linkId).toSet
    val calibrationPoints = getCalibrationPoints(linkIds)

  }

  def getRoadAddressLinks(boundingRectangle: BoundingRectangle, roadNumberLimits: (Int, Int), municipalities: Set[Int]) = {
    val roadLinks = roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, (1, 19999), municipalities)
    val addresses = withDynSession {
      RoadAddressDAO.fetchByLinkId(roadLinks.map(_.linkId).toSet).map(ra => ra.linkId -> ra).toMap
    }
    println("Found " + addresses.size + " road address links")
    val viiteRoadLinks = roadLinks.map { rl =>
      val ra = addresses.get(rl.linkId)
      buildRoadAddressLink(rl, ra)
    }
    viiteRoadLinks
  }
  def buildRoadAddressLink(rl: RoadLink, roadAddr: Option[RoadAddress]): RoadAddressLink =
    roadAddr match {
      case Some(ra) => new RoadAddressLink(rl.linkId, rl.geometry,
        rl.length,  rl.administrativeClass,
        rl.functionalClass,  rl.trafficDirection,
        rl.linkType,  rl.modifiedAt,  rl.modifiedBy,
        rl.attributes, ra.roadNumber, ra.roadPartNumber, ra.track.value, ra.ely, ra.discontinuity.value,
        ra.startAddrMValue, ra.endAddrMValue, ra.endDate, ra.startMValue, ra.endMValue, toSideCode(ra.startMValue, ra.endMValue, ra.track),
        ra.calibrationPoints.find(_.mValue == 0.0), ra.calibrationPoints.find(_.mValue > 0.0))
      case _ => new RoadAddressLink(rl.linkId, rl.geometry,
        rl.length,  rl.administrativeClass,
        rl.functionalClass,  rl.trafficDirection,
        rl.linkType,  rl.modifiedAt,  rl.modifiedBy,
        rl.attributes, 0, 0, 0, 0,
        0, 0, 0, null, 0, 0, SideCode.Unknown,
        None, None)
    }

  private def toSideCode(startMValue: Double, endMValue: Double, track: Track) = {
    track match {
      case Track.Combined => SideCode.BothDirections
      case Track.LeftSide => if (startMValue < endMValue) {
        SideCode.TowardsDigitizing
      } else {
        SideCode.AgainstDigitizing
      }
      case Track.RightSide => if (startMValue > endMValue) {
        SideCode.TowardsDigitizing
      } else {
        SideCode.AgainstDigitizing
      }
      case _ => SideCode.Unknown
    }
  }

  def roadClass(roadAddressLink: RoadAddressLink) = {
    val C1 = new Contains(1 to 49)
    val C2 = new Contains(40 to 99)
    val C3 = new Contains(100 to 999)
    val C4 = new Contains(1000 to 9999)
    val C5 = new Contains(10000 to 19999)
    val C6 = new Contains(40000 to 49999)
    val C7 = new Contains(20001 to 39999)
    val C8a = new Contains(70001 to 89999)
    val C8b = new Contains(90001 to 99999)
    val C9 = new Contains(60001 to 61999)
    val C10 = new Contains(62001 to 62999)
    val C11 = new Contains(9900 to 9999)
    try {
      val roadNumber: Int = roadAddressLink.roadNumber.toInt
      roadNumber match {
        case C1() => HighwayClass
        case C2() => MainRoadClass
        case C3() => RegionalClass
        case C4() => ConnectingClass
        case C5() => MinorConnectingClass
        case C6() => StreetClass
        case C7() => RampsAndRoundAboutsClass
        case C8a() => PedestrianAndBicyclesClass
        case C8b() => PedestrianAndBicyclesClass
        case C9() => WinterRoadsClass
        case C10() => PathsClass
        case C11() => ConstructionSiteTemporaryClass
        case _ => NoClass
      }
    } catch {
      case ex: NumberFormatException => NoClass
    }
  }
}
