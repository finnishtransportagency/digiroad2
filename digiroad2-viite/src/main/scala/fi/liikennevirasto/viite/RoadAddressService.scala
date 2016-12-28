package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.RoadLinkType.{ComplementaryRoadLinkType, FloatingRoadLinkType, NormalRoadLinkType, UnknownRoadLinkType}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.LinkGeomSource.{ComplimentaryLinkInterface, HistoryLinkInterface, NormalLinkInterface, UnknownLinkInterface}
import fi.liikennevirasto.viite.RoadType._
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.RoadAddressLink
import fi.liikennevirasto.viite.process.RoadAddressFiller.LRMValueAdjustment
import fi.liikennevirasto.viite.process.{InvalidAddressDataException, RoadAddressFiller}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
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
    val fetchVVHStartTime = System.currentTimeMillis()
    val (complementedRoadLinks, complementaryLinkIds) = fetchRoadLinksWithComplementary(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads)
    val fetchVVHEndTime = System.currentTimeMillis()
    logger.info("End fetch vvh road links in "+((fetchVVHEndTime-fetchVVHStartTime)*0.001)+" sec")
    val linkIds = complementedRoadLinks.map(_.linkId).toSet

    val (floatingViiteRoadLinks, addresses, floating) = Await.result(fetchRoadAddressesByBoundingBoxF, Duration.Inf)
    val missingLinkIds = linkIds -- floating.keySet

    val fetchMissingRoadAddressStartTime = System.currentTimeMillis()
    val missedRL = withDynTransaction {
      RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
    }.groupBy(_.linkId)
    val fetchMissingRoadAddressEndTime = System.currentTimeMillis()
    logger.info("End fetch missing road address in "+((fetchMissingRoadAddressEndTime-fetchMissingRoadAddressStartTime)*0.001)+" sec")

    val buildStartTime = System.currentTimeMillis()
    val viiteRoadLinks = complementedRoadLinks.map { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      val missed = missedRL.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed)
    }.toMap
    val buildEndTime = System.currentTimeMillis()
    logger.info("End building road address in "+((buildEndTime-buildStartTime)*0.001)+" sec")

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
    val kept = fusedRoadAddresses.map(_.id).toSet
    val removed = roadAddrSeq.map(_.id).toSet.diff(kept)
    val roadAddressesToRegister = fusedRoadAddresses.filter(_.id == -1000)
    if(roadAddressesToRegister.nonEmpty)
      eventbus.publish("roadAddress:mergeRoadAddress", RoadAddressMerge(removed, roadAddressesToRegister))
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

  def mergeRoadAddress(data: RoadAddressMerge) = {
    withDynTransaction {
      updateMergedSegments(data.merged)
      createMergedSegments(data.created)
    }
  }

  def createMergedSegments(mergedRoadAddress: Seq[RoadAddress]) = {
    mergedRoadAddress.grouped(500).foreach(group => RoadAddressDAO.create(group))
  }

  def updateMergedSegments(expiredIds: Set[Long]) = {
    expiredIds.grouped(500).foreach(group => RoadAddressDAO.updateMergedSegmentsById(group))
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
        RoadAddressDAO.fetchByLinkId(roadLinksWithComplimentary.map(_.linkId).toSet, false, false).groupBy(_.linkId)
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
  val values = Set(NormalLinkInterface, ComplimentaryLinkInterface , SuravageLinkInterface, FrozenLinkInterface, HistoryLinkInterface, UnknownLinkInterface)

  def apply(intValue: Int): LinkGeomSource = values.find(_.value == intValue).getOrElse(UnknownLinkInterface)

  case object NormalLinkInterface extends LinkGeomSource {def value = 1;}
  case object ComplimentaryLinkInterface extends LinkGeomSource {def value = 2;}
  case object SuravageLinkInterface extends LinkGeomSource {def value = 3;} // Not yet implemented
  case object FrozenLinkInterface extends LinkGeomSource {def value = 4;} // Not yet implemented
  case object HistoryLinkInterface extends LinkGeomSource {def value = 5;}
  case object UnknownLinkInterface extends LinkGeomSource {def value = 99;}
}

case class RoadAddressMerge(merged: Set[Long], created: Seq[RoadAddress])

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

      groupedRoadAddresses.flatMap{ case (_, record) =>
        RoadAddressLinkBuilder.fuseRoadAddressInGroup(record.sortBy(_.startMValue))
      }.toSeq
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
      case _ => UnknownLinkInterface
    }

    val geom = GeometryUtils.truncateGeometry(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(roadAddress.id, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, roadLinkType, roadLink.constructionType, roadLinkSource, getRoadType(roadLink.administrativeClass, roadLink.linkType), extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
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
      length, roadLink.administrativeClass, roadLink.linkType, UnknownRoadLinkType, roadLink.constructionType, UnknownLinkInterface, getRoadType(roadLink.administrativeClass, roadLink.linkType),
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
      length, historyRoadLink.administrativeClass, UnknownLinkType, roadLinkType, ConstructionType.UnknownConstructionType, HistoryLinkInterface, getRoadType(historyRoadLink.administrativeClass, UnknownLinkType), extractModifiedAtVVH(historyRoadLink.attributes), Some("vvh_modified"),
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

  /**
    * Fusing Two RoadAddresses in One
    *
    * @param nextSegment
    * @param previousSegment
    * @return A sequence of RoadAddresses, 1 if possible to fuse, 2 if they are unfusable
    */
  private def fuseTwo(nextSegment: RoadAddress, previousSegment: RoadAddress): Seq[RoadAddress] = {
    val cpNext = nextSegment.calibrationPoints
    val cpPrevious = previousSegment.calibrationPoints
    def getMValues[T](leftMValue: T, rightMValue: T, op: (T, T) => T,
                      getValue: (Option[CalibrationPoint], Option[CalibrationPoint]) => Option[T])={
      /*  Take the value from Calibration Point if available or then use the given operation
          Starting calibration point from previous segment if available or then it's the starting calibration point for
          the next segment. If neither, use the min or max operation given as an argument.
          Similarily for ending calibration points. Cases where the calibration point truly is between segments is
          left unprocessed.
       */
      getValue(cpPrevious._1.orElse(cpNext._1), cpNext._2.orElse(cpPrevious._2)).getOrElse(op(leftMValue,rightMValue))
    }

    val tempId = -1000

    if(nextSegment.roadNumber     == previousSegment.roadNumber &&
      nextSegment.roadPartNumber  == previousSegment.roadPartNumber &&
      nextSegment.track.value     == previousSegment.track.value &&
      nextSegment.startDate       == previousSegment.startDate &&
      nextSegment.endDate         == previousSegment.endDate &&
      nextSegment.linkId          == previousSegment.linkId &&
      !(cpNext._1.isDefined && cpPrevious._2.isDefined)) { // Check that the calibration point isn't between these segments


      val startAddrMValue = getMValues[Long](nextSegment.startAddrMValue, previousSegment.startAddrMValue, Math.min, (cpp, _) => cpp.map(_.addressMValue))
      val endAddrMValue = getMValues[Long](nextSegment.endAddrMValue, previousSegment.endAddrMValue, Math.max, (_, cpn) => cpn.map(_.addressMValue))
      val startMValue = getMValues[Double](nextSegment.startMValue, previousSegment.startMValue, Math.min, (_, _) => None)
      val endMValue = getMValues[Double](nextSegment.endMValue, previousSegment.endMValue, Math.max, (_, _) => None)

      val calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = {
        val left = Seq(cpNext._1, cpPrevious._1).flatten.sortBy(_.segmentMValue).headOption
        val right = Seq(cpNext._2, cpPrevious._2).flatten.sortBy(_.segmentMValue).lastOption
        (left.map(_.copy(segmentMValue = startMValue)), right.map(_.copy(segmentMValue = endMValue)))
      }

      if(nextSegment.sideCode.value != previousSegment.sideCode.value)
        throw new InvalidAddressDataException(s"Road Address ${nextSegment.id} and Road Address ${previousSegment.id} cannot have different side codes.")

      val combinedGeometry: Seq[Point] = GeometryUtils.truncateGeometry(Seq(previousSegment.geom.head, nextSegment.geom.last), startMValue, endMValue)
      val discontinuity = {
        if(nextSegment.endMValue > previousSegment.endMValue) {
          nextSegment.discontinuity
        } else
          previousSegment.discontinuity
      }

      Seq(RoadAddress(tempId, nextSegment.roadNumber, nextSegment.roadPartNumber,
        nextSegment.track, discontinuity, startAddrMValue,
        endAddrMValue, nextSegment.startDate, nextSegment.endDate, nextSegment.linkId,
        startMValue, endMValue,
        nextSegment.sideCode, calibrationPoints, false, combinedGeometry))

    } else Seq(nextSegment, previousSegment)

  }


}
