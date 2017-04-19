package fi.liikennevirasto.viite

import java.sql.SQLException

import fi.liikennevirasto.digiroad2.RoadLinkType.{ComplementaryRoadLinkType, FloatingRoadLinkType, NormalRoadLinkType, UnknownRoadLinkType}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType._
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}
import fi.liikennevirasto.viite.process.RoadAddressFiller.LRMValueAdjustment
import fi.liikennevirasto.viite.process.{InvalidAddressDataException, RoadAddressFiller}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory

import scala.collection.immutable.ListMap
import scala.collection.immutable.Stream.Empty
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

  val MaxAllowedMValueError = 0.001
  val Epsilon = 1E-6 /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
                                See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
                             */
  val MaxDistanceDiffAllowed = 1.0 /*Temporary restriction from PO: Filler limit on modifications
                                            (LRM adjustments) is limited to 1 meter. If there is a need to fill /
                                            cut more than that then nothing is done to the road address LRM data.
                                            */
  val MinAllowedRoadAddressLength = 0.1

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

  def nonPrivatefetchRoadAddressesByBoundingBox(bounds: BoundingRectangle, fetchOnlyFloating: Boolean = false) = {
    fetchRoadAddressesByBoundingBox(bounds, true)
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

    val floatingViiteRoadLinks = floatingHistoryRoadLinks.filter(rl => floating.keySet.contains(rl.linkId)).map {rl =>
      val ra = floating.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildFloatingRoadAddressLink(rl, ra)
    }.toMap
    (floatingViiteRoadLinks, addresses, floating)
  }

  def buildFloatingRoadAddressLink(rl: RoadLink, roadAddrSeq: Seq[RoadAddress]): Seq[RoadAddressLink] = {
    val fusedRoadAddresses = RoadAddressLinkBuilder.fuseRoadAddress(roadAddrSeq)
    fusedRoadAddresses.map( ra => {
      RoadAddressLinkBuilder.build(rl, ra, true)
    })
  }

  def buildFloatingRoadAddressLink(rl: VVHHistoryRoadLink, roadAddrSeq: Seq[RoadAddress]): Seq[RoadAddressLink] = {
    val fusedRoadAddresses = RoadAddressLinkBuilder.fuseRoadAddress(roadAddrSeq)
    fusedRoadAddresses.map( ra => {
      RoadAddressLinkBuilder.build(rl, ra)
    })
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
    val linkIds = complementedRoadLinks.map(_.linkId).toSet

    val (floatingViiteRoadLinks, addresses, floating) = Await.result(fetchRoadAddressesByBoundingBoxF, Duration.Inf)
    val fetchVVHEndTime = System.currentTimeMillis()
    logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime-fetchVVHStartTime)*0.001))
    val missingLinkIds = linkIds -- floating.keySet -- addresses.keySet

    val fetchMissingRoadAddressStartTime = System.currentTimeMillis()
    val missedRL = withDynTransaction {
      RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
    }.groupBy(_.linkId)
    val fetchMissingRoadAddressEndTime = System.currentTimeMillis()
    logger.info("End fetch missing road address in %.3f sec".format((fetchMissingRoadAddressEndTime-fetchMissingRoadAddressStartTime)*0.001))

    val buildStartTime = System.currentTimeMillis()
    val viiteRoadLinks = complementedRoadLinks.map { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      val missed = missedRL.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed)
    }.toMap
    val buildEndTime = System.currentTimeMillis()
    logger.info("End building road address in %.3f sec".format((buildEndTime-buildStartTime)*0.001))

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

  def getUniqueRoadAddressLink(id: Long) = {

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

  def mergeRoadAddress(data: RoadAddressMerge): Unit = {
    withDynTransaction {
      mergeRoadAddressInTX(data)
    }
  }

  def mergeRoadAddressInTX(data: RoadAddressMerge): Unit = {
    RoadAddressDAO.lockRoadAddressTable()
    val unMergedCount = RoadAddressDAO.queryById(data.merged).size
    if (unMergedCount != data.merged.size)
      throw new InvalidAddressDataException("Data modified while updating, rolling back transaction: some source rows no longer valid")
    val mergedCount = updateMergedSegments(data.merged)
    if (mergedCount == data.merged.size)
      createMergedSegments(data.created)
    else
      throw new InvalidAddressDataException("Data modified while updating, rolling back transaction: some source rows not updated")
  }

  def createMergedSegments(mergedRoadAddress: Seq[RoadAddress]) = {
    mergedRoadAddress.grouped(500).foreach(group => RoadAddressDAO.create(group, "Automatic_merged"))
  }

  def updateMergedSegments(expiredIds: Set[Long]) = {
    expiredIds.grouped(500).map(group => RoadAddressDAO.updateMergedSegmentsById(group)).sum
  }

  /**
    * Checks that if the geometry is found and updates the geometry to match or sets it floating if not found
    *
    * @param ids
    */
  def checkRoadAddressFloating(ids: Set[Long]): Unit = {
    withDynTransaction {
      checkRoadAddressFloatingWithoutTX(ids)
    }
  }

  /**
    * For easier unit testing and use
    *
    * @param ids
    */
  def checkRoadAddressFloatingWithoutTX(ids: Set[Long]): Unit = {
    val addresses = RoadAddressDAO.queryById(ids)
    val linkIdMap = addresses.groupBy(_.linkId).mapValues(_.map(_.id))
    val roadLinks =  roadLinkService.getCurrentAndComplementaryVVHRoadLinks(linkIdMap.keySet)
    addresses.foreach { address =>
      val roadLink = roadLinks.find(_.linkId == address.linkId)
      val addressGeometry = roadLink.map(rl =>
        GeometryUtils.truncateGeometry3D(rl.geometry, address.startMValue, address.endMValue))
      if (roadLink.isEmpty || addressGeometry.isEmpty || GeometryUtils.geometryLength(addressGeometry.get) == 0.0) {
        println("Floating id %d (link id %d)".format(address.id, address.linkId))
        RoadAddressDAO.changeRoadAddressFloating(float = true, address.id, None)
      } else {
        if (!GeometryUtils.areAdjacent(addressGeometry.get, address.geom)) {
          println("Updating geometry for id %d (link id %d)".format(address.id, address.linkId))
          RoadAddressDAO.changeRoadAddressFloating(float = false, address.id, addressGeometry)
        }
      }
    }
  }
  /*
    Kalpa-API methods
  */

  def getRoadAddressesLinkByMunicipality(municipality: Int): Seq[RoadAddressLink] = {
    //TODO: Remove null checks and make sure no nulls are generated
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

  def getValidSurroundingLinks(linkIds: Set[Long], floating: RoadAddressLink): Map[Long, Option[RoadAddressLink]] = {
    val roadLinks = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(linkIds)._1
    try{
      val surroundingLinks = linkIds.map{
        linkid =>
          val geomInChain = roadLinks.filter(_.linkId == linkid).map(_.geometry)
          val sourceLinkGeometryOption = geomInChain.headOption
          sourceLinkGeometryOption.map(sourceLinkGeometry => {
            val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
            val delta: Vector3d = Vector3d(0.1, 0.1, 0)
            val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
            val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
            val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, bounds2)
            val (floatingViiteRoadLinks1, addresses1, floating1) = fetchRoadAddressesByBoundingBox(bounds, true)
            val (floatingViiteRoadLinks2, addresses2, floating2) = fetchRoadAddressesByBoundingBox(bounds2, true)

            val addresses = addresses1 ++ addresses2
            val floatingRoadAddressLinks = floatingViiteRoadLinks1 ++ floatingViiteRoadLinks2
            val distinctRoadLinks = roadLinks.distinct

            val roadAddressLinks = distinctRoadLinks.map { rl =>
              val ra = addresses.getOrElse(rl.linkId, Seq()).distinct
              rl.linkId -> buildRoadAddressLink(rl, ra, Seq())
            }

            val roadAddressLinksWithFloating = roadAddressLinks ++ floatingRoadAddressLinks
            val adjacentLinks = roadAddressLinksWithFloating
              .filter(_._2.exists(ral => GeometryUtils.areAdjacent(sourceLinkGeometry, ral.geometry)
                && ral.roadLinkType != UnknownRoadLinkType && ral.roadNumber == floating.roadNumber && ral.roadPartNumber == floating.roadPartNumber && ral.trackCode == floating.trackCode))
            (linkid -> adjacentLinks.flatMap(_._2).sortBy(_.startAddressM).headOption)
          }).head
      }.toMap

      surroundingLinks
    } catch {
      case e: Exception => Map()
    }
  }

  def getFloatingAdjacent(chainLinks: Set[Long], linkId: Long, roadNumber: Long, roadPartNumber: Long, trackCode: Long, filterpreviousPoint: Boolean = true): Seq[RoadAddressLink] = {
    val chainRoadLinks = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(chainLinks)
    val geomInChain = chainRoadLinks._1.filter(_.linkId == linkId).map(_.geometry)++chainRoadLinks._2.filter(_.linkId == linkId).map(_.geometry)
    val sourceLinkGeometryOption = geomInChain.headOption
    sourceLinkGeometryOption.map(sourceLinkGeometry => {
      val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
      val delta: Vector3d = Vector3d(0.1, 0.1, 0)
      val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
      val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
      val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, bounds2)
      val (floatingViiteRoadLinks1, addresses1, floating1) = fetchRoadAddressesByBoundingBox(bounds, true)
      val (floatingViiteRoadLinks2, addresses2, floating2) = fetchRoadAddressesByBoundingBox(bounds2, true)
      val floatingViiteRoadLinks = floatingViiteRoadLinks1 ++ floatingViiteRoadLinks2

      val floating = floating1 ++ floating2
      val addresses = addresses1 ++ addresses2
      val distinctRoadLinks = roadLinks.distinct
      val linkIds = distinctRoadLinks.map(_.linkId).toSet
      val missingLinkIds = linkIds -- floating.keySet -- addresses.keySet

      val missedRL = withDynTransaction {
        RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
      }.groupBy(_.linkId)

      val viiteMissingRoadLinks = distinctRoadLinks.map { rl =>
        val ra = addresses.getOrElse(rl.linkId, Seq()).distinct
        val missed = missedRL.getOrElse(rl.linkId, Seq()).distinct
        rl.linkId -> buildRoadAddressLink(rl, ra, missed)
      }.filter(_._2.exists(ral => GeometryUtils.areAdjacent(sourceLinkGeometry, ral.geometry)
        && ral.roadLinkType == UnknownRoadLinkType ))
        .flatMap(_._2)

      val viiteFloatingRoadLinks = floatingViiteRoadLinks
        .filterNot(_._1 == linkId)
        .filter(_._2.exists(ral => GeometryUtils.areAdjacent(sourceLinkGeometry, ral.geometry)
          && ral.roadNumber == roadNumber && ral.roadPartNumber == roadPartNumber && ral.trackCode == trackCode
          && ral.roadLinkType == FloatingRoadLinkType))
        .flatMap(_._2).toSeq

      (viiteFloatingRoadLinks.distinct ++ viiteMissingRoadLinks)
    }).getOrElse(Seq())
  }

  def getRoadAddressAfterCalculation(sources: Seq[String], targets: Seq[String], user: User): Seq[RoadAddressLink] = {
    val sourceLinks = sources.flatMap(rd => {
      getUniqueRoadAddressLink(rd.toLong)
    })
    val targetLinks = targets.flatMap(rd => {
      getUniqueRoadAddressLink(rd.toLong)
    })
    transferRoadAddress(sourceLinks, targetLinks, user)
  }

  def transferFloatingToGap(sourceIds: Set[Long], targetIds: Set[Long], roadAddresses: Seq[RoadAddress]) = {
    withDynTransaction {
      RoadAddressDAO.expireRoadAddresses(sourceIds)
      RoadAddressDAO.expireMissingRoadAddresses(targetIds)
      RoadAddressDAO.create(roadAddresses)
    }
  }

  def transferRoadAddress(sources: Seq[RoadAddressLink], targets: Seq[RoadAddressLink], user: User): Seq[RoadAddressLink] = {

    def getMValues(cp: Option[Double], fl: Option[Double]): Double = {
      (cp, fl) match {
        case (Some(calibrationPoint), Some(mVal)) => calibrationPoint
        case (None, Some(mVal)) => mVal
        case (Some(calibrationPoint), None) => calibrationPoint
        case (None, None) => 0.0
      }
    }

    val adjustTopology: Seq[(Double, Seq[RoadAddressLink]) => Seq[RoadAddressLink]] = Seq(
      RoadAddressLinkBuilder.dropSegmentsOutsideGeometry,
      RoadAddressLinkBuilder.capToGeometry,
      RoadAddressLinkBuilder.extendToGeometry,
      RoadAddressLinkBuilder.dropShort
    )

    val allLinks = sources ++ targets
    val targetsGeomLength = targets.map(_.length).sum

    val allStartCp = sources.flatMap(_.startCalibrationPoint) ++ targets.flatMap(_.startCalibrationPoint)
    val allEndCp = sources.flatMap(_.endCalibrationPoint) ++ targets.flatMap(_.endCalibrationPoint)
    val startCalibrationPoints = allStartCp.filter(_.addressMValue == allStartCp.map(_.addressMValue).min)
    val endCalibrationPoints = allEndCp.filter(_.addressMValue == allEndCp.map(_.addressMValue).max)
    val startCp: Option[CalibrationPoint] = if (!startCalibrationPoints.isEmpty) Option(startCalibrationPoints.head) else None
    val endCp: Option[CalibrationPoint] = if (!endCalibrationPoints.isEmpty) Option(endCalibrationPoints.head) else None
    val minStartAddressM = sources.map(_.startAddressM).min
    val maxEndAddressM = sources.map(_.endAddressM).max
    var minStartMValue = 0.0
    var maxEndMValue = 0.0
    val adjustedSegments = adjustTopology.foldLeft(sources) { (previousSources, operation) => operation(targetsGeomLength, previousSources) }

    if (!allLinks.flatMap(_.startCalibrationPoint).isEmpty) {
      minStartMValue = getMValues(Option(allLinks.flatMap(_.startCalibrationPoint).map(_.segmentMValue).min), Option(allLinks.map(_.startMValue).min))
    } else {
      minStartMValue = adjustedSegments.map(_.startMValue).min
    }

    if (!allLinks.flatMap(_.endCalibrationPoint).isEmpty) {
      maxEndMValue = getMValues(Option(allLinks.flatMap(_.endCalibrationPoint).map(_.segmentMValue).max), Option(allLinks.map(_.endMValue).max))
    } else {
      maxEndMValue = adjustedSegments.map(_.endMValue).max
    }

    val source = sources.head

    val orderedTargets = targets.size match {
      case 1 => targets
      case _ =>
        val optionalSurroundingMappedLinks = getValidSurroundingLinks(targets.map(_.linkId).toSet, source)
        val surroundingMappedLinks = optionalSurroundingMappedLinks.filterNot(_._2.isEmpty)
        val startingLinkId = surroundingMappedLinks.size match {
          case 0 => targets.head.linkId
          case _ => ListMap(surroundingMappedLinks.toSeq.sortBy(_._2.get.startAddressM):_*).keySet.head
        }
        val firstTarget = targets.filter(_.linkId == startingLinkId).head
        val orderTargets = targets.foldLeft(Seq.empty[RoadAddressLink]) { (previousOrderedTargets, target) =>
          orderLinksRecursivelyByAdjacency(firstTarget, target, targets, previousOrderedTargets)
        }
        orderTargets
    }

    val adjustedCreatedRoads = orderedTargets.foldLeft(orderedTargets) { (previousTargets, target) =>
      RoadAddressLinkBuilder.adjustRoadAddressTopology(maxEndMValue, minStartAddressM, maxEndAddressM, source, target, previousTargets, user.username).filterNot(_.id == 0) }

    adjustedCreatedRoads
  }

  def orderLinksRecursivelyByAdjacency(firstTarget: RoadAddressLink, target: RoadAddressLink, targets: Seq[RoadAddressLink], previousOrderedTargets: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {

    val orderedTargets = previousOrderedTargets.size match {
      case 0 => Seq(firstTarget)
      case _ =>
        val nextTarget = targets.filterNot(t =>  previousOrderedTargets.map(_.linkId).contains(t.linkId)).filter(rt => GeometryUtils.areAdjacent(previousOrderedTargets.last.geometry, target.geometry))
        (previousOrderedTargets++nextTarget)
    }
    orderedTargets
  }

  def saveRoadLinkProject(roadAddressProject: RoadAddressProject) : Map[String, Any] = {
    withDynTransaction {
      try {
        if(roadAddressProject.roadNumber != 0) {
          RoadAddressDAO.getRoadAddressProjectById(roadAddressProject.id) match {
            case None => {
              val id = Sequences.nextViitePrimaryKeySeqValue
              val project = roadAddressProject.copy(id = id)
              RoadAddressDAO.createRoadAddressProject(project)
              //create ProjectLink
              if (project.startPart <= project.endPart) {
                for (part <- project.startPart to project.endPart) {
                  val addresses = RoadAddressDAO.fetchByRoadPart(project.roadNumber, part)
                  addresses.foreach(address =>
                    RoadAddressDAO.createRoadAddressProjectLink(Sequences.nextViitePrimaryKeySeqValue, address, project))
                }
              }

              val createdAddresses = RoadAddressDAO.getRoadAddressProjectLinks(project.id)
              val groupedAddresses = createdAddresses.groupBy{address =>
                (address.roadNumber, address.roadPartNumber)}.toSeq.sortBy(_._1._2 )(Ordering[Long])
              val formInfo = groupedAddresses.map(addressGroup =>{
                val lastAddressM = addressGroup._2.last.endAddrM
                val roadLink = roadLinkService.getRoadLinksByLinkIdsFromVVH(Set(addressGroup._2.last.linkId), false)
                val addressFormLine = RoadAddressProjectFormLine(addressGroup._2.last.linkId, project.id, project.roadNumber, addressGroup._1._2, lastAddressM , MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(roadLink.head.municipalityCode, -1), addressGroup._2.last.discontinuityType.description )
                addressFormLine
              })
              Map("project" -> projectToApi(project), "projectAddresses" -> createdAddresses.headOption, "formInfo" -> formInfo)
            }
            case _ => {
              RoadAddressDAO.updateRoadAddressProject(roadAddressProject)
              Map("roadAddressProject" -> roadAddressProject)
            }
          }
        }
        else Map("project" -> projectToApi(roadAddressProject), "projectAddresses" -> None, "formInfo" -> None)
      }
      catch {
        case a: Exception => println(a.getMessage)
          Map()
      }
    }
  }

  def projectToApi(roadAddressProject: RoadAddressProject) : Map[String, Any] = {
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    Map(
      "id" -> roadAddressProject.id,
      "roadNumber" -> roadAddressProject.roadNumber,
      "dateModified" -> roadAddressProject.dateModified.toString(formatter),
      "startDate" -> roadAddressProject.startDate.toString(formatter),
      "additionalInfo" -> roadAddressProject.additionalInfo,
      "createdBy" -> roadAddressProject.createdBy,
      "endPart" -> roadAddressProject.endPart,
      "modifiedBy" -> roadAddressProject.modifiedBy,
      "name" -> roadAddressProject.name,
      "startPart" -> roadAddressProject.startPart,
      "status" -> roadAddressProject.status
    )
  }

  def getRoadAddressSingleProject(projectId: Long): Seq[RoadAddressProject] = {
    withDynTransaction {
      RoadAddressDAO.getRoadAddressProjects(projectId)
    }
  }

  def getRoadAddressAllProjects(): Seq[RoadAddressProject] = {
    withDynTransaction {
      RoadAddressDAO.getRoadAddressProjects()
    }
  }

  def getProjectsWithLinksById(projectId: Long): (RoadAddressProject, Seq[RoadAddressProjectFormLine]) = {
    withDynTransaction {
      val project:RoadAddressProject = RoadAddressDAO.getRoadAddressProjects(projectId).head
      val createdAddresses = RoadAddressDAO.getRoadAddressProjectLinks(project.id)
      val groupedAddresses = createdAddresses.groupBy { address =>
        (address.roadNumber, address.roadPartNumber)
      }.toSeq.sortBy(_._1._2)(Ordering[Long])
      val formInfo: Seq[RoadAddressProjectFormLine] = groupedAddresses.map(addressGroup => {
        val endAddressM = addressGroup._2.last.endAddrM
        val roadLink = roadLinkService.getRoadLinksByLinkIdsFromVVH(Set(addressGroup._2.head.linkId), false)
        val addressFormLine = RoadAddressProjectFormLine(addressGroup._2.head.linkId, project.id, addressGroup._2.head.roadNumber, addressGroup._2.head.roadPartNumber, endAddressM, MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(roadLink.head.municipalityCode, -1), addressGroup._2.head.discontinuityType.description)
        addressFormLine
      })

       val fullProjectInfo:RoadAddressProject = formInfo.length match {
         case 0 => project
         case 1 => project.copy(roadNumber = formInfo.head.roadNumber, startPart = formInfo.head.roadPartNumber, endPart = formInfo.head.roadPartNumber)
         case _ => project.copy(roadNumber = formInfo.head.roadNumber, startPart = formInfo.head.roadPartNumber, endPart = formInfo.last.roadPartNumber)
       }

      (fullProjectInfo, formInfo)
    }
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
  case object PrivateRoadType extends RoadType { def value = 5; def displayValue = "Yksityistie" }
  case object UnknownOwnerRoad extends RoadType { def value = 9; def displayValue = "Omistaja selvittämättä" }
}

case class RoadAddressMerge(merged: Set[Long], created: Seq[RoadAddress])

object RoadAddressLinkBuilder {
  val RoadNumber = "ROADNUMBER"
  val RoadPartNumber = "ROADPARTNUMBER"
  val ComplementarySubType = 3
  val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
  val MaxAllowedMValueError = 0.001
  val Epsilon = 1E-6
  /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
                                See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
                             */
  val MaxDistanceDiffAllowed = 1.0
  /*Temporary restriction from PO: Filler limit on modifications
                                            (LRM adjustments) is limited to 1 meter. If there is a need to fill /
                                            cut more than that then nothing is done to the road address LRM data.
                                            */
  val MinAllowedRoadAddressLength = 0.1

  lazy val municipalityMapping = OracleDatabase.withDynSession {
    MunicipalityDAO.getMunicipalityMapping
  }
  lazy val municipalityRoadMaintainerMapping = OracleDatabase.withDynSession {
    MunicipalityDAO.getMunicipalityRoadMaintainers
  }

  def getRoadType(administrativeClass: AdministrativeClass, linkType: LinkType): RoadType = {
    (administrativeClass, linkType) match {
      case (State, CableFerry) => FerryRoad
      case (State, _) => PublicRoad
      case (Municipality, _) => MunicipalityStreetRoad
      case (Private, _) => PrivateRoadType
      case (_, _) => UnknownOwnerRoad
    }
  }

  def fuseRoadAddress(roadAddresses: Seq[RoadAddress]): Seq[RoadAddress] = {
    if (roadAddresses.size == 1) {
      roadAddresses
    } else {
      val groupedRoadAddresses = roadAddresses.groupBy(record =>
        (record.roadNumber, record.roadPartNumber, record.track.value, record.startDate, record.endDate, record.linkId))

      groupedRoadAddresses.flatMap { case (_, record) =>
        RoadAddressLinkBuilder.fuseRoadAddressInGroup(record.sortBy(_.startMValue))
      }.toSeq
    }
  }

  def build(roadLink: RoadLink, roadAddress: RoadAddress, floating: Boolean = false) = {
    val roadLinkType = (floating, roadLink.linkSource) match {
      case (true, _) => FloatingRoadLinkType
      case (false, LinkGeomSource.ComplimentaryLinkInterface) => ComplementaryRoadLinkType
      case (false, _) => NormalRoadLinkType
    }
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(roadAddress.id, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, roadLinkType, roadLink.constructionType, roadLink.linkSource, getRoadType(roadLink.administrativeClass, roadLink.linkType), extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2,Anomaly.None, roadAddress.lrmPositionId)

  }

  def build(roadLink: RoadLink, missingAddress: MissingRoadAddress) = {
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, missingAddress.startMValue.getOrElse(0.0), missingAddress.endMValue.getOrElse(roadLink.length))
    val length = GeometryUtils.geometryLength(geom)
    val roadLinkRoadNumber = roadLink.attributes.get(RoadNumber).map(toIntNumber).getOrElse(0)
    val roadLinkRoadPartNumber = roadLink.attributes.get(RoadPartNumber).map(toIntNumber).getOrElse(0)
    RoadAddressLink(0, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, UnknownRoadLinkType, roadLink.constructionType, LinkGeomSource.Unknown, getRoadType(roadLink.administrativeClass, roadLink.linkType),
      extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, missingAddress.roadNumber.getOrElse(roadLinkRoadNumber),
      missingAddress.roadPartNumber.getOrElse(roadLinkRoadPartNumber), Track.Unknown.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), Discontinuity.Continuous.value,
      0, 0, "", "", 0.0, length, SideCode.Unknown, None, None, missingAddress.anomaly, 0)
  }

  def build(historyRoadLink: VVHHistoryRoadLink, roadAddress: RoadAddress): RoadAddressLink = {

    val roadLinkType = FloatingRoadLinkType

    val geom = GeometryUtils.truncateGeometry3D(historyRoadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(roadAddress.id, historyRoadLink.linkId, geom,
      length, historyRoadLink.administrativeClass, UnknownLinkType, roadLinkType, ConstructionType.UnknownConstructionType, LinkGeomSource.HistoryLinkInterface, getRoadType(historyRoadLink.administrativeClass, UnknownLinkType), extractModifiedAtVVH(historyRoadLink.attributes), Some("vvh_modified"),
      historyRoadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityRoadMaintainerMapping.getOrElse(historyRoadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2, Anomaly.None, roadAddress.lrmPositionId)
  }

  def capToGeometry(geomLength: Double, sourceSegments: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    val (overflowingSegments, passThroughSegments) = sourceSegments.partition(x => (x.endMValue - MaxAllowedMValueError > geomLength))
    val cappedSegments = overflowingSegments.map { s =>
      (s.copy(endMValue = geomLength))
    }
    (passThroughSegments ++ cappedSegments)
  }

  def extendToGeometry(geomLength: Double, sourceSegments: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    if (sourceSegments.isEmpty)
      return sourceSegments
    val sorted = sourceSegments.sortBy(_.endMValue)(Ordering[Double].reverse)
    val lastSegment = sorted.head
    val restSegments = sorted.tail
    val adjustments = (lastSegment.endMValue < geomLength - MaxAllowedMValueError) match {
      case true => (restSegments ++ Seq(lastSegment.copy(endMValue = geomLength)))
      case _ => sourceSegments
    }
    adjustments
  }

  def dropShort(geomLength: Double, sourceSegments: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    if (sourceSegments.size < 2)
      return sourceSegments
    val passThroughSegments = sourceSegments.partition(s => s.length >= MinAllowedRoadAddressLength)._1
    passThroughSegments
  }

  def dropSegmentsOutsideGeometry(geomLength: Double, sourceSegments: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    val passThroughSegments = sourceSegments.partition(x => x.startMValue + Epsilon <= geomLength)._1
    passThroughSegments
  }

  def adjustRoadAddressTopology(maxEndMValue: Double, minStartMAddress: Long, maxEndMAddress: Long, source: RoadAddressLink, currentTarget: RoadAddressLink, roadAddresses: Seq[RoadAddressLink], username: String): Seq[RoadAddressLink] = {
    val tempId = -1000
    val sorted = roadAddresses.sortBy(_.endAddressM)(Ordering[Long].reverse)
    val lastTarget = sorted.head
    val startAddressM = roadAddresses.filterNot(_.id == 0).size match {
      case 0 => minStartMAddress
      case _ => lastTarget.endAddressM
    }

    val endMAddress = startAddressM + GeometryUtils.geometryLength(currentTarget.geometry).toLong

    val newRoadAddress = Seq(RoadAddressLink(tempId, currentTarget.linkId, currentTarget.geometry, GeometryUtils.geometryLength(currentTarget.geometry), source.administrativeClass, source.linkType, NormalRoadLinkType, source.constructionType, source.roadLinkSource,
      source.roadType, source.modifiedAt, Option(username), currentTarget.attributes, source.roadNumber, source.roadPartNumber, source.trackCode, source.elyCode, source.discontinuity,
      startAddressM, endMAddress, source.startDate, source.endDate, currentTarget.startMValue, GeometryUtils.geometryLength(currentTarget.geometry), source.sideCode, None, None, source.anomaly, source.lrmPositionId))
    (roadAddresses++newRoadAddress)
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

    // Test that at the road addresses lap at least partially or are connected (one extends another)
    def addressConnected(nextSegment: RoadAddress, previousSegment: RoadAddress) = {
      (nextSegment.startAddrMValue == previousSegment.endAddrMValue ||
        previousSegment.startAddrMValue == nextSegment.endAddrMValue) ||
        (nextSegment.startAddrMValue >= previousSegment.startAddrMValue &&
          nextSegment.startAddrMValue <= previousSegment.endAddrMValue) ||
        (previousSegment.startAddrMValue >= nextSegment.startAddrMValue &&
          previousSegment.startAddrMValue <= nextSegment.endAddrMValue)
    }
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
      addressConnected(nextSegment, previousSegment) &&
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
      val combinedGeometry: Seq[Point] = GeometryUtils.truncateGeometry3D(Seq(previousSegment.geom.head, nextSegment.geom.last), startMValue, endMValue)
      val discontinuity = {
        if(nextSegment.endMValue > previousSegment.endMValue) {
          nextSegment.discontinuity
        } else
          previousSegment.discontinuity
      }

      Seq(RoadAddress(tempId, nextSegment.roadNumber, nextSegment.roadPartNumber,
        nextSegment.track, discontinuity, startAddrMValue,
        endAddrMValue, nextSegment.startDate, nextSegment.endDate, nextSegment.modifiedBy, nextSegment.lrmPositionId, nextSegment.linkId,
        startMValue, endMValue,
        nextSegment.sideCode, calibrationPoints, false, combinedGeometry))

    } else Seq(nextSegment, previousSegment)

  }


}
