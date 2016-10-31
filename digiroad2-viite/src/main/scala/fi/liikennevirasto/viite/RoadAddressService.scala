package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, RoadLinkService}
import fi.liikennevirasto.viite.RoadType._
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}
import fi.liikennevirasto.viite.process.RoadAddressFiller
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import slick.jdbc.{StaticQuery => Q}

class RoadAddressService(roadLinkService: RoadLinkService, eventbus: DigiroadEventBus) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  val logger = LoggerFactory.getLogger(getClass)

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

  class Contains(r: Range) {
    def unapply(i: Int): Boolean = r contains i
  }

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

  def getRoadAddressLinks(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                          everything: Boolean = false, publicRoads: Boolean = false) = {
    val roadLinks = roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads)
    val linkIds = roadLinks.map(_.linkId).toSet
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchByLinkId(linkIds).groupBy(_.linkId)
    }
    val missingLinkIds = linkIds -- addresses.keySet
    val missedRL = withDynTransaction {
      RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
    }.groupBy(_.linkId)

    val viiteRoadLinks = roadLinks.map { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      val missed = missedRL.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed)
    }.toMap

    val (filledTopology, changeSet) = RoadAddressFiller.fillTopology(roadLinks, viiteRoadLinks)

    eventbus.publish("roadAddress:persistMissingRoadAddress", changeSet.missingRoadAddresses)
    eventbus.publish("roadAddress:floatRoadAddress", changeSet.toFloatingAddressIds)

    filledTopology
  }

  /**
    * Returns missing road addresses for links that did not already exist in database
    *
    * @param roadNumberLimits
    * @param municipality
    * @return
    */
  def getMissingRoadAddresses(roadNumberLimits: Seq[(Int, Int)], municipality: Int) = {
    val roadLinks = roadLinkService.getViiteRoadLinksFromVVH(municipality, roadNumberLimits)
    val linkIds = roadLinks.map(_.linkId).toSet
    val addresses = RoadAddressDAO.fetchByLinkId(linkIds).groupBy(_.linkId)

    val missingLinkIds = linkIds -- addresses.keySet
    val missedRL = RoadAddressDAO.getMissingRoadAddresses(missingLinkIds).groupBy(_.linkId)

    val viiteRoadLinks = roadLinks.map { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      val missed = missedRL.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed)
    }.toMap

    val (_, changeSet) = RoadAddressFiller.fillTopology(roadLinks, viiteRoadLinks)

    changeSet.missingRoadAddresses
  }

  def buildRoadAddressLink(rl: RoadLink, roadAddrSeq: Seq[RoadAddress], missing: Seq[MissingRoadAddress]): Seq[RoadAddressLink] = {
    roadAddrSeq.map(ra => {
      RoadAddressLinkBuilder.build(rl, ra)
    }) ++
      missing.map(m => RoadAddressLinkBuilder.build(rl, m)).filter(_.length > 0.0)
  }

  def getRoadParts(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int]) = {
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchPartsByRoadNumbers(roadNumberLimits).groupBy(_.linkId)
    }
    val roadLinks = roadLinkService.getViiteRoadPartsFromVVH(addresses.keySet, municipalities)
    roadLinks.flatMap { rl =>
      val ra = addresses.getOrElse(rl.linkId, List())
      buildRoadAddressLink(rl, ra, Seq())
    }
  }

  def getCoarseRoadParts(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int]) = {
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchPartsByRoadNumbers(roadNumberLimits, coarse = true).groupBy(_.linkId)
    }
    val roadLinks = roadLinkService.getViiteRoadPartsFromVVH(addresses.keySet, municipalities)
    val groupedLinks = roadLinks.flatMap { rl =>
      val ra = addresses.getOrElse(rl.linkId, List())
      buildRoadAddressLink(rl, ra, Seq())
    }.groupBy(_.roadNumber)

    val retval = groupedLinks.mapValues {
      case (viiteRoadLinks) =>
        val sorted = viiteRoadLinks.sortWith({
          case (ral1, ral2) =>
            if (ral1.roadNumber != ral2.roadNumber)
              ral1.roadNumber < ral2.roadNumber
            else if (ral1.roadPartNumber != ral2.roadPartNumber)
              ral1.roadPartNumber < ral2.roadPartNumber
            else
              ral1.startAddressM < ral2.startAddressM
        })
        sorted.zip(sorted.tail).map {
          case (st1, st2) =>
            st1.copy(geometry = Seq(st1.geometry.head, st2.geometry.head))
        }
    }
    retval.flatMap(x => x._2).toSeq
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

  def createMissingRoadAddress(missingRoadLinks: Seq[MissingRoadAddress]) = {
    withDynTransaction {
      missingRoadLinks.foreach(createSingleMissingRoadAddress)
    }
  }

  def createSingleMissingRoadAddress(missingAddress: MissingRoadAddress) = {
    RoadAddressDAO.createMissingRoadAddress(missingAddress)
  }

  //TODO: Priority order for anomalies. Currently this is unused
  def getAnomalyCodeByLinkId(linkId: Long, roadPart: Long): Anomaly = {
    //roadlink dont have road address
    if (RoadAddressDAO.getLrmPositionByLinkId(linkId).length < 1) {
      return Anomaly.NoAddressGiven
    }
    //road address do not cover the whole length of the link
    //TODO: Check against link geometry length, using tolerance and GeometryUtils that we cover the whole link
    else if (RoadAddressDAO.getLrmPositionMeasures(linkId).map(x => Math.abs(x._3 - x._2)).sum > 0) {
      return Anomaly.NotFullyCovered
    }
    //road address having road parts that dont matching
    else if (RoadAddressDAO.getLrmPositionRoadParts(linkId, roadPart).nonEmpty) {
      return Anomaly.Illogical
    }
    Anomaly.None
  }

  def setRoadAddressFloating(ids: Set[Long]): Unit = {
    withDynTransaction {
      // TODO: add geometry if it is somehow available
      ids.foreach(id => RoadAddressDAO.changeRoadAddressFloating(float = true, id, None))
    }
  }

  /*
    Kalpa-API methods
  */

  def getRoadAddressesLinkByMunicipality(municipality: Int): Seq[RoadAddressLink] = {
    val roadLinks = roadLinkService.getViiteRoadLinksFromVVHByMunicipality(municipality)

    val addresses =
      withDynTransaction {
        RoadAddressDAO.fetchByLinkId(roadLinks.map(_.linkId).toSet).groupBy(_.linkId)
      }
    // In order to avoid sending roadAddressLinks that have no road address
    // we remove the road links that have no known address
    val knownRoadLinks = roadLinks.filter(rl => {
      addresses.contains(rl.linkId)
    })

    val viiteRoadLinks = knownRoadLinks.map { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, Seq())
    }.toMap

    val (filledTopology, changeSet) = RoadAddressFiller.fillTopology(roadLinks, viiteRoadLinks)

    eventbus.publish("roadAddress:persistMissingRoadAddress", changeSet.missingRoadAddresses)
    eventbus.publish("roadAddress:floatRoadAddress", changeSet.toFloatingAddressIds)

    filledTopology
  }

}

//TIETYYPPI (1= yleinen tie, 2 = lauttaväylä yleisellä tiellä, 3 = kunnan katuosuus, 4 = yleisen tien työmaa, 5 = yksityistie, 9 = omistaja selvittämättä)
sealed trait RoadType {
  def value: Int
  def displayValue: String
}
object RoadType {
  val values = Set(PublicRoad, FerryRoad, MunicipalityStreetRoad, PublicUnderConstructionRoad, PrivateRoadType, UnknownOwnerRoad)

  def apply(intValue: Int): RoadType = {
    values.find(_.value == intValue).getOrElse(UnknownOwnerRoad)
  }

  case object PublicRoad extends RoadType { def value = 1; def displayValue = "Yleinen tie" }
  case object FerryRoad extends RoadType { def value = 2; def displayValue = "Lauttaväylä yleisellä tiellä" }
  case object MunicipalityStreetRoad extends RoadType { def value = 3; def displayValue = "Kunnan katuosuus" }
  case object PublicUnderConstructionRoad extends RoadType { def value = 4; def displayValue = "Yleisen tien työmaa" }
  case object PrivateRoadType extends RoadType { def value = 5; def displayValue = "yksityistie" }
  case object UnknownOwnerRoad extends RoadType { def value = 9; def displayValue = "Omistaja selvittämättä" }
}

object RoadAddressLinkBuilder {
  val RoadNumber = "ROADNUMBER"
  val RoadPartNumber = "ROADPARTNUMBER"

  val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")

  lazy val municipalityMapping = OracleDatabase.withDynSession{
    ElyDAO.getMunicipalityMapping
  }

  def getRoadType(administrativeClass: AdministrativeClass, linkType: LinkType): RoadType = {
    (administrativeClass, linkType) match {
      case (State, CableFerry) => FerryRoad
      case (State, _) => PublicRoad
      case (Municipality, _) => MunicipalityStreetRoad
      case (Private, _) => PrivateRoadType
      case (_,_) => UnknownOwnerRoad
    }
  }

  def build(roadLink: RoadLink, roadAddress: RoadAddress) = {
    val geom = GeometryUtils.truncateGeometry2D(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    new RoadAddressLink(roadAddress.id, roadLink.linkId, geom,
      length, roadLink.administrativeClass,
      roadLink.linkType, getRoadType(roadLink.administrativeClass, roadLink.linkType), extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityMapping.getOrElse(roadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(null), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      toSideCode(roadAddress.startMValue, roadAddress.endMValue, roadAddress.track),
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2)

  }

  def build(roadLink: RoadLink, missingAddress: MissingRoadAddress) = {
    val geom = GeometryUtils.truncateGeometry2D(roadLink.geometry, missingAddress.startMValue.getOrElse(0.0), missingAddress.endMValue.getOrElse(roadLink.length))
    val length = GeometryUtils.geometryLength(geom)
    val roadLinkRoadNumber = roadLink.attributes.get(RoadNumber).map(toIntNumber).getOrElse(0)
    val roadLinkRoadPartNumber = roadLink.attributes.get(RoadPartNumber).map(toIntNumber).getOrElse(0)
    new RoadAddressLink(0, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, getRoadType(roadLink.administrativeClass, roadLink.linkType),
      extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, missingAddress.roadNumber.getOrElse(roadLinkRoadNumber),
      missingAddress.roadPartNumber.getOrElse(roadLinkRoadPartNumber), Track.Unknown.value, municipalityMapping.getOrElse(roadLink.municipalityCode, -1), Discontinuity.Continuous.value,
      0, 0, "", "", 0.0, length, SideCode.Unknown, None, None, missingAddress.anomaly)
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

  private def toIntNumber(value: Any) = {
    try {
      value.asInstanceOf[String].toInt
    } catch {
      case e: Exception => 0
    }
  }

  private def extractModifiedAtVVH(attributes: Map[String, Any]): Option[String] = {
    def toLong(anyValue: Option[Any]) = {
      anyValue.map(_.asInstanceOf[BigInt].toLong)
    }
    def compareDateMillisOptions(a: Option[Long], b: Option[Long]): Option[Long] = {
      (a, b) match {
        case (Some(firstModifiedAt), Some(secondModifiedAt)) =>
          if (firstModifiedAt > secondModifiedAt)
            Some(firstModifiedAt)
          else
            Some(secondModifiedAt)
        case (Some(firstModifiedAt), None) => Some(firstModifiedAt)
        case (None, Some(secondModifiedAt)) => Some(secondModifiedAt)
        case (None, None) => None
      }
    }
    val toIso8601 = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
    val createdDate = toLong(attributes.get("CREATED_DATE"))
    val lastEditedDate = toLong(attributes.get("LAST_EDITED_DATE"))
    val geometryEditedDate = toLong(attributes.get("GEOMETRY_EDITED_DATE"))
    val latestDate = compareDateMillisOptions(lastEditedDate, geometryEditedDate)
    val latestDateString = latestDate.orElse(createdDate).map(modifiedTime => new DateTime(modifiedTime)).map(toIso8601.print(_))
    latestDateString
  }

}
