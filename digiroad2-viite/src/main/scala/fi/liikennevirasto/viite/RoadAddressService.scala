package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode, State}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, RoadLinkService}
import fi.liikennevirasto.viite.dao.{CalibrationPoint, RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.model.RoadAddressLink
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import slick.jdbc.{StaticQuery => Q}


class RoadAddressService(roadLinkService: RoadLinkService, eventbus: DigiroadEventBus) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  val logger = LoggerFactory.getLogger(getClass)

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

  val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")

  /**
    * Get calibration points for road not in a project
    *
    * @param roadNumber
    * @return
    */
  def getCalibrationPoints(roadNumber: Long) = {
    // TODO: Implementation
    Seq(CalibrationPoint(1, 0.0, 0))
  }

  /**
    * Get calibration points for road including project created ones
    *
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

  def getRoadAddressLinks(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int], everything: Boolean = false) = {
    val roadLinks = roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, roadNumberLimits, municipalities, everything)
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchByLinkId(roadLinks.map(_.linkId).toSet).groupBy(_.linkId)
    }
    val viiteRoadLinks = roadLinks.flatMap { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      buildRoadAddressLink(rl, ra)
  }
    val newAnomalousRL = viiteRoadLinks.filter(rl => (rl.administrativeClass == State || rl.roadNumber > 0) && rl.id == 0)

    val missedRL  = withDynTransaction {
    RoadAddressDAO.getMissingRoadAddresses()
    }

    val missingRL = newAnomalousRL.filterNot(rl => missedRL.exists(mrl => mrl._1 == rl.linkId && mrl._2 == rl.startAddressM && mrl._3 == rl.endAddressM))
    eventbus.publish("roadAddress:persistMissingRoadAddress", missingRL)
    viiteRoadLinks
  }

  def buildRoadAddressLink(rl: RoadLink, roadAddrSeq: Seq[RoadAddress]): Seq[RoadAddressLink] = {
    roadAddrSeq.size match {
      case 0 => Seq(new RoadAddressLink(0, rl.linkId, rl.geometry,
        rl.length,  rl.administrativeClass,
        rl.functionalClass,  rl.trafficDirection,
        rl.linkType,  rl.modifiedAt,  rl.modifiedBy,
        rl.attributes, 0, 0, 0, 0,
        0, 0, 0, "", 0, 0, SideCode.Unknown,
        None, None))
      case _ => roadAddrSeq.map(ra => {
        val geom = GeometryUtils.truncateGeometry(rl.geometry, ra.startMValue, ra.endMValue)
        val length = GeometryUtils.geometryLength(geom)
        new RoadAddressLink(ra.id, rl.linkId, geom,
          length, rl.administrativeClass,
          rl.functionalClass, rl.trafficDirection,
          rl.linkType, rl.modifiedAt, rl.modifiedBy,
          rl.attributes, ra.roadNumber, ra.roadPartNumber, ra.track.value, ra.ely, ra.discontinuity.value,
          ra.startAddrMValue, ra.endAddrMValue, formatter.print(ra.endDate), ra.startMValue, ra.endMValue, toSideCode(ra.startMValue, ra.endMValue, ra.track),
          ra.calibrationPoints.find(_.mValue == 0.0), ra.calibrationPoints.find(_.mValue > 0.0))
      }).filter(_.length > 0.0)
    }
  }

  def getRoadParts(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int]) = {
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchPartsByRoadNumbers(roadNumberLimits).groupBy(_.linkId)
    }
    val roadLinks = roadLinkService.getViiteRoadPartsFromVVH(addresses.keySet, municipalities)
    roadLinks.flatMap { rl =>
      val ra = addresses.getOrElse(rl.linkId, List())
      buildRoadAddressLink(rl, ra)
    }
  }

  def getCoarseRoadParts(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int]) = {
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchPartsByRoadNumbers(roadNumberLimits, coarse=true).groupBy(_.linkId)
    }
    val roadLinks = roadLinkService.getViiteRoadPartsFromVVH(addresses.keySet, municipalities)
    val groupedLinks = roadLinks.flatMap { rl =>
      val ra = addresses.getOrElse(rl.linkId, List())
      buildRoadAddressLink(rl, ra)
    }.groupBy(_.roadNumber)

    val retval = groupedLinks.mapValues {
      case (viiteRoadLinks) =>
        val sorted = viiteRoadLinks.sortWith({
          case (ral1, ral2) =>
            if (ral1.roadNumber < ral2.roadNumber)
              true
            else if (ral1.roadNumber > ral2.roadNumber)
              false
            else if (ral1.roadPartNumber < ral2.roadPartNumber)
              true
            else if (ral1.roadPartNumber > ral2.roadPartNumber)
              false
            else if (ral1.startAddressM < ral2.startAddressM)
              true
            else
              false
        })
        sorted.zip(sorted.tail).map{
          case (st1, st2) =>
            st1.copy(geometry = Seq(st1.geometry.head, st2.geometry.head))
        }
    }
    retval.flatMap(x => x._2).toSeq
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
    val C1 = new Contains(1 to 39)
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

  def createMissingRoadAddress(missingRoadLinks: Seq[RoadAddressLink]) = {
      missingRoadLinks.map { links =>
        withDynTransaction {

          val anomalyCode = getAnomalyCodeByLinkId(links.linkId, links.roadPartNumber)
          RoadAddressDAO.createMissingRoadAddress(links.linkId, links.startAddressM, links.endAddressM, anomalyCode)

      }
    }
  }

  def getAnomalyCodeByLinkId(linkId: Long, roadPart: Long) : Int = {
    var code = 0
      //roadlink dont have road address
      if (RoadAddressDAO.getLrmPositionByLinkId(linkId).length < 1) {
        code = 1
      }
      //road address do not cover the whole length of the link
       else if (RoadAddressDAO.getLrmPositionMeasures(linkId).length > 0)
      {
        code = 2
      }
        //road adress having road parts that dont matching
      else if (RoadAddressDAO.getLrmPositionRoadParts(linkId, roadPart).length > 0){
        code = 3
      }
    code
  }

}
