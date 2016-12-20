package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.RoadLinkType.{ComplementaryRoadLinkType, FloatingRoadLinkType, NormalRoadLinkType, UnknownRoadLinkType}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.LinkGeomSource.{ComplimentaryLinkInterface, HistoryLinkInterface, NormalLinkInterface}
import fi.liikennevirasto.viite.RoadType._
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.RoadAddressLink
import fi.liikennevirasto.viite.process.RoadAddressFiller.LRMValueAdjustment
import fi.liikennevirasto.viite.process.{InvalidAddressDataException, RoadAddressFiller}
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import slick.jdbc.{StaticQuery => Q}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class RoadAddressService(roadLinkService: RoadLinkService, eventbus: DigiroadEventBus) {


  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

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

  private def fetchRoadLinksWithComplementary(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                              everything: Boolean = false, publicRoads: Boolean = false): (Seq[RoadLink], Set[Long]) = {
    val roadLinksF = Future(roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads))
    val complementaryLinksF = Future(roadLinkService.getComplementaryRoadLinksFromVVH(boundingRectangle, municipalities))
    val (roadLinks, complementaryLinks) = Await.result(roadLinksF.zip(complementaryLinksF), Duration.Inf)
    (roadLinks ++ complementaryLinks, complementaryLinks.map(_.linkId).toSet)
  }

  private def fetchRoadAddressesByBoundingBox(boundingRectangle: BoundingRectangle, fetchOnlyFloating: Boolean = false) = {
    val (floatingAddresses, nonFloatingAddresses) = withDynTransaction {
      RoadAddressDAO.fetchByBoundingBox(boundingRectangle, fetchOnlyFloating)._1.partition(_.floating)
    }

    val floating = floatingAddresses.groupBy(_.linkId)
    val addresses = nonFloatingAddresses.groupBy(_.linkId)

    val floatingHistoryRoadLinks = withDynTransaction {
      roadLinkService.getViiteRoadLinksHistoryFromVVH(floating.keySet)
    }

    val floatingViiteRoadLinks = floatingHistoryRoadLinks.map {rl =>
      val ra = floating.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildFloatingRoadAddressLink(rl, ra)
    }.toMap
    (floatingViiteRoadLinks, addresses, floating)
  }

  def getRoadAddressLinks(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                          everything: Boolean = false, publicRoads: Boolean = false) = {
    def complementaryLinkFilter(roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                everything: Boolean = false, publicRoads: Boolean = false)(roadAddressLink: RoadAddressLink) = {
      everything || publicRoads || roadNumberLimits.exists {
        case (start, stop) => roadAddressLink.roadNumber >= start && roadAddressLink.roadNumber <= stop
      }
    }
    val fetchRoadAddressesByBoundingBoxF = Future(fetchRoadAddressesByBoundingBox(boundingRectangle))
    val (complementedRoadLinks, complementaryLinkIds) = fetchRoadLinksWithComplementary(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads)
    val linkIds = complementedRoadLinks.map(_.linkId).toSet

    val (floatingViiteRoadLinks, addresses, floating) = Await.result(fetchRoadAddressesByBoundingBoxF, Duration.Inf)
    val missingLinkIds = linkIds -- floating.keySet

    val missedRL = withDynTransaction {
      RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
    }.groupBy(_.linkId)

    val viiteRoadLinks = complementedRoadLinks.map { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      val missed = missedRL.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed)
    }.toMap

    val (filledTopology, changeSet) = RoadAddressFiller.fillTopology(complementedRoadLinks, viiteRoadLinks)

    eventbus.publish("roadAddress:persistMissingRoadAddress", changeSet.missingRoadAddresses)
    eventbus.publish("roadAddress:persistAdjustments", changeSet.adjustedMValues)
    eventbus.publish("roadAddress:floatRoadAddress", changeSet.toFloatingAddressIds)


    val returningTopology = filledTopology.filter(link => !complementaryLinkIds.contains(link.linkId) ||
      complementaryLinkFilter(roadNumberLimits, municipalities, everything, publicRoads)(link))

    returningTopology ++ floatingViiteRoadLinks.flatMap(_._2)

  }

  /**
    * Returns missing road addresses for links that did not already exist in database
    *
    * @param roadNumberLimits
    * @param municipality
    * @return
    */
  def getMissingRoadAddresses(roadNumberLimits: Seq[(Int, Int)], municipality: Int) = {
    val roadLinks = roadLinkService.getViiteCurrentAndComplementaryRoadLinksFromVVH(municipality, roadNumberLimits)
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
    val fusedRoadAddresses = RoadAddressLinkBuilder.fuseRoadAddress(roadAddrSeq)
    val roadAddressesToRegister = fusedRoadAddresses.filter(_.id == -1000)
    if(roadAddressesToRegister.size > 0)
      eventbus.publish("roadAddress:mergeRoadAddress", roadAddressesToRegister)
    fusedRoadAddresses.map(ra => {
      RoadAddressLinkBuilder.build(rl, ra)
    }) ++
      missing.map(m => RoadAddressLinkBuilder.build(rl, m)).filter(_.length > 0.0)
  }

  def buildFloatingRoadAddressLink(rl: VVHHistoryRoadLink, roadAddrSeq: Seq[RoadAddress]): Seq[RoadAddressLink] = {
    roadAddrSeq.map( ra => {
      RoadAddressLinkBuilder.build(rl, ra)
    })
  }

  private def combineGeom(roadAddresses: Seq[RoadAddress]) = {
    if (roadAddresses.length == 1) {
      roadAddresses.head
    } else {
      val max = roadAddresses.maxBy(ra => ra.endMValue)
      val min = roadAddresses.minBy(ra => ra.startMValue)
      min.copy(startAddrMValue = Math.min(min.startAddrMValue, max.startAddrMValue),
        endAddrMValue = Math.max(min.endAddrMValue, max.endAddrMValue),
        startMValue = min.startMValue, endMValue = max.endMValue,
        geom = Seq(min.geom.head, max.geom.last))
    }
  }

  def getRoadParts(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int]) = {
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchPartsByRoadNumbers(boundingRectangle, roadNumberLimits).groupBy(_.linkId)
    }

    val vvhRoadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(addresses.keySet)
    val combined = addresses.mapValues(combineGeom)
    val roadLinks = vvhRoadLinks.map( rl => rl -> combined(rl.linkId)).toMap

    roadLinks.flatMap { case (rl, ra) =>
      buildRoadAddressLink(rl, Seq(ra), Seq())
    }.toSeq
  }

  def getCoarseRoadParts(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int]) = {
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchPartsByRoadNumbers(boundingRectangle, roadNumberLimits, coarse = true).groupBy(_.linkId)
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

  def getRoadAddressLink(id: Long) = {

    val (addresses,missedRL) = withDynTransaction {
      (RoadAddressDAO.fetchByLinkId(Set(id), true),
        RoadAddressDAO.getMissingRoadAddresses(Set(id)))
    }
    val (roadLinks, vvhHistoryLinks) = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(Set(id))
    (addresses.size, roadLinks.size) match {
      case (0,0) => List()
      case (_,0) => addresses.flatMap(a => vvhHistoryLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (0,_) => missedRL.flatMap( a => roadLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (_,_) => addresses.flatMap( a => roadLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
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

  def createMissingRoadAddress(missingRoadLinks: Seq[MissingRoadAddress]) = {
    withDynTransaction {
      missingRoadLinks.foreach(createSingleMissingRoadAddress)
    }
  }

  def createSingleMissingRoadAddress(missingAddress: MissingRoadAddress) = {
    RoadAddressDAO.createMissingRoadAddress(missingAddress)
  }

  def mergeRoadAddress(toMergeRoadAddress: Seq[RoadAddress]) = {
    withDynTransaction {
      toMergeRoadAddress.foreach(updateMergedSegments)
      createMergedSegments(toMergeRoadAddress)
    }
  }

  def createMergedSegments(mergedRoadAddress: Seq[RoadAddress]) = {
    RoadAddressDAO.create(mergedRoadAddress)
  }

  def updateMergedSegments(toMergeRoadAddresses: RoadAddress) = {
    RoadAddressDAO.updateMergedSegmentsByLinkId(toMergeRoadAddresses.linkId)
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
    val roadLinks =
      {
        val tempRoadLinks = roadLinkService.getViiteRoadLinksFromVVHByMunicipality(municipality)
        if (tempRoadLinks == null)
          Seq.empty[RoadLink]
        else tempRoadLinks
      }
    val complimentaryLinks = {
      val tempComplimentary = roadLinkService.getComplementaryRoadLinksFromVVH(municipality)
      if (tempComplimentary == null)
        Seq.empty[RoadLink]
      else tempComplimentary
    }
    val roadLinksWithComplimentary = roadLinks ++ complimentaryLinks

    val addresses =
      withDynTransaction {
        RoadAddressDAO.fetchByLinkId(roadLinksWithComplimentary.map(_.linkId).toSet).groupBy(_.linkId)
      }
    // In order to avoid sending roadAddressLinks that have no road address
    // we remove the road links that have no known address
    val knownRoadLinks = roadLinksWithComplimentary.filter(rl => {
      addresses.contains(rl.linkId)
    })

    val viiteRoadLinks = knownRoadLinks.map { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, Seq())
    }.toMap

    val (filledTopology, changeSet) = RoadAddressFiller.fillTopology(roadLinksWithComplimentary, viiteRoadLinks)

    eventbus.publish("roadAddress:persistMissingRoadAddress", changeSet.missingRoadAddresses)
    eventbus.publish("roadAddress:persistAdjustments", changeSet.adjustedMValues)
    eventbus.publish("roadAddress:floatRoadAddress", changeSet.toFloatingAddressIds)

    filledTopology
  }

  def saveAdjustments(addresses: Seq[LRMValueAdjustment]): Unit = {
    withDynTransaction {
      addresses.foreach(RoadAddressDAO.updateLRM)
    }
  }
}

//TIETYYPPI (1= yleinen tie, 2 = lauttaväylä yleisellä tiellä, 3 = kunnan katuosuus, 4 = yleisen tien työmaa, 5 = yksityistie, 9 = omistaja selvittämättä)
sealed trait RoadType {
  def value: Int
  def displayValue: String
}
//LINKIN LÄHDE (1 = tielinkkien rajapinta, 2 = täydentävien linkkien rajapinta, 3 = suunnitelmalinkkien rajapinta, 4 = jäädytettyjen linkkien rajapinta, 5 = historialinkkien rajapinta)
sealed trait LinkGeomSource{
  def value: Int
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
  case object PrivateRoadType extends RoadType { def value = 5; def displayValue = "Yksityistie" }
  case object UnknownOwnerRoad extends RoadType { def value = 9; def displayValue = "Omistaja selvittämättä" }
}
object LinkGeomSource{
  val values = Set(NormalLinkInterface, ComplimentaryLinkInterface , SuravageLinkInterface, FrozenLinkInterface, HistoryLinkInterface)

  def apply(intValue: Int): LinkGeomSource = values.find(_.value == intValue).orNull

  case object NormalLinkInterface extends LinkGeomSource {def value = 1;}
  case object ComplimentaryLinkInterface extends LinkGeomSource {def value = 2;}
  case object SuravageLinkInterface extends LinkGeomSource {def value = 3;} // Not yet implemented
  case object FrozenLinkInterface extends LinkGeomSource {def value = 4;} // Not yet implemented
  case object HistoryLinkInterface extends LinkGeomSource {def value = 5;}
}

object RoadAddressLinkBuilder {
  val RoadNumber = "ROADNUMBER"
  val RoadPartNumber = "ROADPARTNUMBER"
  val ComplementarySubType = 3

  val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")

  lazy val municipalityMapping = OracleDatabase.withDynSession{
    MunicipalityDAO.getMunicipalityMapping
  }
  lazy val municipalityRoadMaintainerMapping = OracleDatabase.withDynSession{
    MunicipalityDAO.getMunicipalityRoadMaintainers
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

  def fuseRoadAddress(roadAddresses: Seq[RoadAddress]): Seq[RoadAddress] = {
    if (roadAddresses.size == 1) {
      roadAddresses
    } else {
      val groupedRoadAddresses = roadAddresses.groupBy(record =>
        (record.roadNumber, record.roadPartNumber, record.track.value, record.startDate, record.endDate, record.linkId))

      groupedRoadAddresses.map(record =>
        RoadAddressLinkBuilder.fuseRoadAddressNonRecursive(record._2.sortBy(_.startMValue))
      ).flatten.toSeq
    }
  }

  def build(roadLink: RoadLink, roadAddress: RoadAddress) = {

    val roadLinkType = roadLink.attributes.contains("SUBTYPE") && roadLink.attributes("SUBTYPE") == ComplementarySubType match {
      case true => ComplementaryRoadLinkType
      case false => NormalRoadLinkType
    }

    val roadLinkSource = roadLinkType  match {
      case NormalRoadLinkType => NormalLinkInterface
      case ComplementaryRoadLinkType => ComplimentaryLinkInterface
      case _ => null
    }

    val geom = GeometryUtils.truncateGeometry(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(roadAddress.id, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, roadLinkType, roadLinkSource, getRoadType(roadLink.administrativeClass, roadLink.linkType), extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2)

  }

  def build(roadLink: RoadLink, missingAddress: MissingRoadAddress) = {
    val geom = GeometryUtils.truncateGeometry2D(roadLink.geometry, missingAddress.startMValue.getOrElse(0.0), missingAddress.endMValue.getOrElse(roadLink.length))
    val length = GeometryUtils.geometryLength(geom)
    val roadLinkRoadNumber = roadLink.attributes.get(RoadNumber).map(toIntNumber).getOrElse(0)
    val roadLinkRoadPartNumber = roadLink.attributes.get(RoadPartNumber).map(toIntNumber).getOrElse(0)
    RoadAddressLink(0, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, UnknownRoadLinkType, null, getRoadType(roadLink.administrativeClass, roadLink.linkType),
      extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, missingAddress.roadNumber.getOrElse(roadLinkRoadNumber),
      missingAddress.roadPartNumber.getOrElse(roadLinkRoadPartNumber), Track.Unknown.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), Discontinuity.Continuous.value,
      0, 0, "", "", 0.0, length, SideCode.Unknown, None, None, missingAddress.anomaly)
  }

  def build(historyRoadLink: VVHHistoryRoadLink, roadAddress: RoadAddress): RoadAddressLink = {

    val roadLinkType = FloatingRoadLinkType

    val geom = GeometryUtils.truncateGeometry2D(historyRoadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(roadAddress.id, historyRoadLink.linkId, geom,
      length, historyRoadLink.administrativeClass, UnknownLinkType, roadLinkType, HistoryLinkInterface, getRoadType(historyRoadLink.administrativeClass, UnknownLinkType), extractModifiedAtVVH(historyRoadLink.attributes), Some("vvh_modified"),
      historyRoadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityRoadMaintainerMapping.getOrElse(historyRoadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2)
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
    val endDate = toLong(attributes.get("END_DATE"))
    val latestDate = compareDateMillisOptions(lastEditedDate, geometryEditedDate)
    val withHistoryLatestDate = compareDateMillisOptions(latestDate, endDate)
    val timezone = DateTimeZone.forOffsetHours(0)
    val latestDateString = withHistoryLatestDate.orElse(createdDate).map(modifiedTime => new DateTime(modifiedTime, timezone)).map(toIso8601.print(_))
    latestDateString
  }

  /**
    * Fuse recursively
    *
    * @param unprocessed road addresses ordered by the startMValue
    * @param ready recursive value
    * @return road addresses fused in reverse order
    */
  private def fuseRoadAddressInGroup(unprocessed: Seq[RoadAddress], ready: Seq[RoadAddress] = Nil): Seq[RoadAddress] = {
    if (ready.isEmpty)
      fuseRoadAddressInGroup(unprocessed.tail, Seq(unprocessed.head))
    else if (unprocessed.isEmpty)
      ready
    else
      {
        fuseRoadAddressInGroup(unprocessed.tail, fuseTwo(unprocessed.head, ready.head) ++ ready.tail)
      }
  }

  private def fuseRoadAddressNonRecursive(roadAddress: Seq[RoadAddress]): Seq[RoadAddress] = {
    if(roadAddress.size == 1)
      return roadAddress

    var currentRoadAddress = roadAddress.head
    roadAddress.tail.map{
      rd =>
        val fused = fuseTwo(rd, currentRoadAddress)
        if(rd == roadAddress.last) {
          fused
        } else {
          currentRoadAddress = fused.head
          if(fused.size == 1){
            Nil
          } else{
            fused.tail
          }
        }
    }.flatten
  }

  /**
    * Fusing Two RoadAddresses in One
    *
    * @param nextSegment
    * @param previouusSegment
    * @return A sequence of RoadAddresses, 1 if possible to fuse, 2 if they are unfusable
    */
  private def fuseTwo(nextSegment: RoadAddress, previouusSegment: RoadAddress): Seq[RoadAddress] = {
    val cpNext = nextSegment.calibrationPoints
    val cpPrevious = previouusSegment.calibrationPoints
    val calibrationPointss = Seq(cpNext._1, cpPrevious._1).flatten
    def getMValues[T](leftMValue: T, rightMValue: T, minMax: (T, T) => T, getValue: Seq[CalibrationPoint] => T)={
      if(calibrationPointss.isEmpty)
        minMax(leftMValue,rightMValue)
      else
        getValue(calibrationPointss)
    }

    val tempId = -1000

    if(nextSegment.roadNumber     == previouusSegment.roadNumber &&
      nextSegment.roadPartNumber  == previouusSegment.roadPartNumber &&
      nextSegment.track.value     == previouusSegment.track.value &&
      nextSegment.startDate       == previouusSegment.startDate &&
      nextSegment.endDate         == previouusSegment.endDate &&
      nextSegment.linkId          == previouusSegment.linkId &&
      !(cpNext._1.isDefined && cpPrevious._2.isDefined)) {


      val startAddrMValue = getMValues[Long](nextSegment.startAddrMValue, previouusSegment.startAddrMValue, Math.min, cp => cp.minBy(_.addressMValue).addressMValue)
      val endAddrMValue = getMValues[Long](nextSegment.endAddrMValue, previouusSegment.endAddrMValue, Math.max, cp => cp.maxBy(_.addressMValue).addressMValue)
      val startMValue = getMValues[Double](nextSegment.startMValue, previouusSegment.startMValue, Math.min, cp => cp.minBy(_.segmentMValue).segmentMValue)
      val endMValue = getMValues[Double](nextSegment.endMValue, previouusSegment.endMValue, Math.max, cp => cp.maxBy(_.segmentMValue).segmentMValue)

      val calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = {
        val calibrations = Seq(cpNext._1, cpPrevious._1).flatten
        val left = calibrations.isEmpty match{
          case true => None
          case false => Some(calibrations.minBy(_.segmentMValue))
        }
        val right = calibrations.isEmpty match{
          case true => None
          case false => Some(calibrations.maxBy(_.segmentMValue))
        }
        (left, right)
      }

      if(nextSegment.sideCode.value != previouusSegment.sideCode.value)
        throw new InvalidAddressDataException(s"Road Address ${nextSegment.id} and Road Address ${previouusSegment.id} cannot have different side codes.")

      val combinedGeometry: Seq[Point] = GeometryUtils.truncateGeometry((nextSegment.geom ++ previouusSegment.geom), startMValue, endMValue)
      val discontinuity = {
        if(nextSegment.endMValue > previouusSegment.endMValue) {
          nextSegment.discontinuity
        } else
          previouusSegment.discontinuity
      }

      Seq(RoadAddress(-1000, nextSegment.roadNumber, nextSegment.roadPartNumber,
        nextSegment.track, discontinuity, startAddrMValue,
        endAddrMValue, nextSegment.startDate, nextSegment.endDate, nextSegment.linkId,
        startMValue, endMValue,
        nextSegment.sideCode, calibrationPoints, false, combinedGeometry))

    } else Seq(nextSegment, previouusSegment)

  }


}
