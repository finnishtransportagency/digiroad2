package fi.liikennevirasto.viite


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
import fi.liikennevirasto.viite.process.{InvalidAddressDataException, LinkRoadAddressCalculator, RoadAddressFiller}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory

import scala.collection.immutable.ListMap
import scala.collection.immutable.Stream.Empty
import scala.collection.mutable.ListBuffer
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

    val floatingViiteRoadLinks = floatingHistoryRoadLinks.filter(rl => floating.keySet.contains(rl.linkId)).map { rl =>
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
    fusedRoadAddresses.map(ra => {
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
    logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime - fetchVVHStartTime) * 0.001))
    val missingLinkIds = linkIds -- floating.keySet -- addresses.keySet

    val fetchMissingRoadAddressStartTime = System.currentTimeMillis()
    val missedRL = withDynTransaction {
      RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
    }.groupBy(_.linkId)
    val fetchMissingRoadAddressEndTime = System.currentTimeMillis()
    logger.info("End fetch missing road address in %.3f sec".format((fetchMissingRoadAddressEndTime - fetchMissingRoadAddressStartTime) * 0.001))

    val buildStartTime = System.currentTimeMillis()
    val viiteRoadLinks = complementedRoadLinks.map { rl =>
      val ra = addresses.getOrElse(rl.linkId, Seq())
      val missed = missedRL.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed)
    }.toMap
    val buildEndTime = System.currentTimeMillis()
    logger.info("End building road address in %.3f sec".format((buildEndTime - buildStartTime) * 0.001))

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
    if (roadAddressesToRegister.nonEmpty)
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
    val roadLinks = vvhRoadLinks.map(rl => rl -> combined(rl.linkId)).toMap

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

    val (addresses, missedRL) = withDynTransaction {
      (RoadAddressDAO.fetchByLinkId(Set(id), true),
        RoadAddressDAO.getMissingRoadAddresses(Set(id)))
    }
    val (roadLinks, vvhHistoryLinks) = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(Set(id))
    (addresses.size, roadLinks.size) match {
      case (0, 0) => List()
      case (_, 0) => addresses.flatMap(a => vvhHistoryLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (0, _) => missedRL.flatMap(a => roadLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (_, _) => addresses.flatMap(a => roadLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
    }
  }

  def getUniqueRoadAddressLink(id: Long) = {

    val (addresses, missedRL) = withDynTransaction {
      (RoadAddressDAO.fetchByLinkId(Set(id), true),
        RoadAddressDAO.getMissingRoadAddresses(Set(id)))
    }
    val (roadLinks, vvhHistoryLinks) = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(Set(id))
    (addresses.size, roadLinks.size) match {
      case (0, 0) => List()
      case (_, 0) => addresses.flatMap(a => vvhHistoryLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (0, _) => missedRL.flatMap(a => roadLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (_, _) => addresses.flatMap(a => roadLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
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
    val roadLinks = roadLinkService.getCurrentAndComplementaryVVHRoadLinks(linkIdMap.keySet)
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
    val roadLinks = {
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
    val (roadLinks, vvhRoadLinks) = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(linkIds)
    try{
      val surroundingLinks = linkIds.map{
        linkid =>
          val geomInChain = roadLinks.filter(_.linkId == linkid).map(_.geometry) ++ vvhRoadLinks.filter(_.linkId == linkid).map(_.geometry)
          val sourceLinkGeometryOption = geomInChain.headOption
          sourceLinkGeometryOption.map(sourceLinkGeometry => {
            val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
            val delta: Vector3d = Vector3d(0.1, 0.1, 0)
            val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
            val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
            val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, bounds2)
            val (floatingViiteRoadLinks1, addresses1, floating1) = fetchRoadAddressesByBoundingBox(bounds)
            val (floatingViiteRoadLinks2, addresses2, floating2) = fetchRoadAddressesByBoundingBox(bounds2)

            val addresses = addresses1 ++ addresses2
            val floatingRoadAddressLinks = floatingViiteRoadLinks1 ++ floatingViiteRoadLinks2
            val distinctRoadLinks = roadLinks.distinct

            val roadAddressLinks = distinctRoadLinks.map { rl =>
              val ra = addresses.filter(_._1 != linkid).getOrElse(rl.linkId, Seq()).distinct
              rl.linkId -> buildRoadAddressLink(rl, ra, Seq())
            }

            val roadAddressLinksWithFloating = roadAddressLinks ++ floatingRoadAddressLinks
            val adjacentLinks = roadAddressLinksWithFloating
              .filter(_._2.exists(ral => GeometryUtils.areAdjacent(sourceLinkGeometry, ral.geometry)
                && ral.roadLinkType != UnknownRoadLinkType && ral.roadNumber == floating.roadNumber && ral.roadPartNumber == floating.roadPartNumber && ral.trackCode == floating.trackCode))
            (linkid -> adjacentLinks.flatMap(_._2).headOption)
          }).head
      }.toMap

      surroundingLinks
    } catch {
      case e: Exception =>
        logger.warn("Exception occurred while getting surrounding links", e)
        Map()
    }
  }

  def getFloatingAdjacent(chainLinks: Set[Long], linkId: Long, roadNumber: Long, roadPartNumber: Long, trackCode: Long, filterpreviousPoint: Boolean = true): Seq[RoadAddressLink] = {
    val chainRoadLinks = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(chainLinks)
    val geomInChain = chainRoadLinks._1.filter(_.linkId == linkId).map(_.geometry) ++ chainRoadLinks._2.filter(_.linkId == linkId).map(_.geometry)
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
        && ral.roadLinkType == UnknownRoadLinkType))
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
    val transferredRoadAddresses = transferRoadAddress(sourceLinks, targetLinks, user)

    transferredRoadAddresses
  }

  def transferFloatingToGap(sourceIds: Set[Long], targetIds: Set[Long], roadAddresses: Seq[RoadAddress]) = {
    withDynTransaction {
      RoadAddressDAO.expireRoadAddresses(sourceIds)
      RoadAddressDAO.expireMissingRoadAddresses(targetIds)
      RoadAddressDAO.create(roadAddresses)
      recalculateRoadAddresses(roadAddresses.head.roadNumber.toInt, roadAddresses.head.roadPartNumber.toInt)
    }
  }

  /**
    *
    * @param roadNumber    Road's number (long)
    * @param roadStartPart Starting part (long)
    * @param roadEndPart   Ending part (long)
    * @return Optional error message, None if no error
    */
  def checkRoadAddressNumberAndSEParts(roadNumber: Long, roadStartPart: Long, roadEndPart: Long): Option[String] = {
    OracleDatabase.withDynTransaction {
      if (!RoadAddressDAO.roadPartExists(roadNumber, roadStartPart)) {
        if (!RoadAddressDAO.roadNumberExists(roadNumber)) {
          Some("Tienumeroa ei ole olemassa, tarkista tiedot")
        }
        else //roadnumber exists, but starting roadpart not
          Some("Tiellä ei ole olemassa valittua alkuosaa, tarkista tiedot")
      } else if (!RoadAddressDAO.roadPartExists(roadNumber, roadEndPart)) { // ending part check
        Some("Tiellä ei ole olemassa valittua loppuosaa, tarkista tiedot")
      } else
        None
    }
  }

  private def createNewProjectToDB(roadAddressProject: RoadAddressProject): RoadAddressProject = {
    OracleDatabase.withDynTransaction {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val project = roadAddressProject.copy(id = id)
      RoadAddressDAO.createRoadAddressProject(project)
      project
    }
  }

  private def projectFound(roadAddressProject: RoadAddressProject): Option[RoadAddressProject] = {
    OracleDatabase.withDynTransaction {
      return RoadAddressDAO.getRoadAddressProjectById(roadAddressProject.id)
    }
  }

  def checkReservability(roadNumber: Long, startPart: Long, endPart: Long): Either[String, Seq[ReservedRoadPart]] = {
    withDynTransaction {
      var listOfAddressParts: ListBuffer[ReservedRoadPart] = ListBuffer.empty
      for (part <- startPart to endPart) {
        val reserved = RoadAddressDAO.roadPartReservedByProject(roadNumber, part)
        reserved match {
          case Some(projectname) => return Left(s"TIE $roadNumber OSA $part on jo varattuna projektissa $projectname, tarkista tiedot")
          case None => {
            val (roadpartID, linkID, length, discontinuity, ely, foundAddress) = getAddressPartinfo(roadNumber, part)
            if (foundAddress) // db search failed or we couldnt get info from VVH
              listOfAddressParts += ReservedRoadPart(roadpartID, roadNumber, part, length, Discontinuity.apply(discontinuity), ely)
          }
        }
      }
      Right(listOfAddressParts)
    }
  }

  private def getAddressPartinfo(roadnumber: Long, roadpart: Long): (Long, Long, Double, String, Long, Boolean) = {
    RoadAddressDAO.getRoadPartInfo(roadnumber, roadpart) match {
      case Some((roadpartid, linkid, lenght, discontinuity)) => {
        val enrichment = false
        val roadLink = roadLinkService.getRoadLinksByLinkIdsFromVVH(Set(linkid), enrichment)
        val ely: Long = MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(roadLink.head.municipalityCode, 0)
        if (ely == 0) {
          return (0, 0, 0, "", 0, false)
        }
        return (roadpartid, linkid, lenght, Discontinuity.apply(discontinuity.toInt).description, ely, true)
      }
      case None => {
        return (0, 0, 0, "", 0, false)
      }
    }
  }

  /**
    * Adds reserved road links (from road parts) to a road address project
    *
    * @param project
    * @return
    */
  private def addLinksToProject(project: RoadAddressProject): Option[String] = {
    var croadnumber: Long = 0 //needed for error messages
    var croadpart: Long = 0
    withDynTransaction {
      try {
        for (roadaddress <- project.reservedParts) { //check validity
          if (!RoadAddressDAO.roadPartExists(roadaddress.roadNumber, roadaddress.roadPartNumber)) {
            return Some(s"TIE ${roadaddress.roadNumber} OSA: ${roadaddress.roadPartNumber} ei löytynyt tietokannasta")
          }
        }
        for (roadaddress <- project.reservedParts) {
          croadnumber = roadaddress.roadNumber
          croadpart = roadaddress.roadPartNumber
          val addresses = RoadAddressDAO.fetchByRoadPart(roadaddress.roadNumber, roadaddress.roadPartNumber, true)
          addresses.foreach(address =>
            RoadAddressDAO.createRoadAddressProjectLink(Sequences.nextViitePrimaryKeySeqValue, address, project))
        }
      } catch {
        case a: Exception =>
          if (a.getMessage.contains("ORA-20000")) {
            val reservedByProject = RoadAddressDAO.roadPartReservedByProject(croadnumber, croadpart)
            logger.info(s"Road part being reserved was already reserved to project $reservedByProject")
            return Some(s"TIE $croadnumber OSA $croadpart on jo varattuna projektissa ${
              reservedByProject.getOrElse("<Tuntematon>")
            }, tarkista tiedot " + '\n')
          } else {
            logger.error(s"Reserving of road part $croadpart failed: ${a.getMessage}", a)
            return Some(s"Tieosan $croadpart varaus ei tuntemattomasta virheestä johtuen onnistunut" + '\n')
          }
        case _ : Throwable => return Some(s"Tuntematon virhe")
      }
    }
    None
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

    val allStartCp = sources.flatMap(_.startCalibrationPoint)
    val allEndCp = sources.flatMap(_.endCalibrationPoint)
    val startCp = if (allStartCp.nonEmpty) Option(allStartCp.minBy(_.addressMValue)) else None
    val endCp = if (allEndCp.nonEmpty) Option(allEndCp.maxBy(_.addressMValue)) else None
    val minStartAddressM = sources.map(_.startAddressM).min
    val maxEndAddressM = sources.map(_.endAddressM).max

    val adjustedSegments = adjustTopology.foldLeft(sources) { (previousSources, operation) => operation(targetsGeomLength, previousSources) }

    val maxEndMValue = allLinks.flatMap(_.endCalibrationPoint) match {
      case Nil => adjustedSegments.map(_.endMValue).max
      case _ => getMValues(Option(adjustedSegments.flatMap(_.endCalibrationPoint).map(_.segmentMValue).max), Option(allLinks.map(_.endMValue).max))
    }

    val source = sources.head

    val orderedTargets = targets.size match {
      case 1 => targets
      case _ =>
        val optionalSurroundingMappedLinks = getValidSurroundingLinks(targets.map(_.linkId).toSet, source).filterNot(_._2.isEmpty)
        val startingLinkId = optionalSurroundingMappedLinks.size match {
          case 0 => targets.head.linkId
          case 1 => val secondOptionalSurroundingMappedLinks = getValidSurroundingLinks(Set(optionalSurroundingMappedLinks.head._2.get.linkId), source).filterNot(_._2.isEmpty)
            if (secondOptionalSurroundingMappedLinks.nonEmpty && optionalSurroundingMappedLinks.head._2.get.endAddressM < secondOptionalSurroundingMappedLinks.head._2.get.endAddressM) {
              val resultLinkId = optionalSurroundingMappedLinks.head._1 match {
                case x if (x == targets.head.linkId) => targets.last.linkId
                case _ => targets.head.linkId
              }
              resultLinkId
            } else if (secondOptionalSurroundingMappedLinks.nonEmpty && optionalSurroundingMappedLinks.head._2.get.endAddressM > secondOptionalSurroundingMappedLinks.head._2.get.endAddressM){
              val resultLinkId = optionalSurroundingMappedLinks.head._1 match {
                case x if (x == targets.head.linkId) => targets.head.linkId
                case _ => targets.last.linkId
              }
              resultLinkId
            } else {
              ListMap(optionalSurroundingMappedLinks.toSeq.sortBy(_._2.get.startAddressM):_*).keySet.head
            }

          case _ => ListMap(optionalSurroundingMappedLinks.toSeq.sortBy(_._2.get.startAddressM):_*).keySet.head
        }
        val firstTarget = targets.filter(_.linkId == startingLinkId).head
        val orderTargets = targets.foldLeft(Seq.empty[RoadAddressLink]) { (previousOrderedTargets, target) =>
          orderLinksRecursivelyByAdjacency(firstTarget, target, targets, previousOrderedTargets)
        }
        orderTargets
    }

    val adjustedCreatedRoads = orderedTargets.foldLeft(orderedTargets) { (previousTargets, target) =>
      RoadAddressLinkBuilder.adjustRoadAddressTopology(orderedTargets.length, startCp, endCp, maxEndMValue, minStartAddressM, maxEndAddressM, source, target, previousTargets, user.username).filterNot(_.id == 0) }

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

  def recalculateRoadAddresses(roadNumber: Long, roadPartNumber: Long) = {
    try{
      val roads = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber, true)
      if (!roads.exists(_.floating)) {
        try {
          val adjusted = LinkRoadAddressCalculator.recalculate(roads)
          assert(adjusted.size == roads.size)
          // Must not lose any
          val (changed, unchanged) = adjusted.partition(ra =>
            roads.exists(oldra => ra.id == oldra.id && (oldra.startAddrMValue != ra.startAddrMValue || oldra.endAddrMValue != ra.endAddrMValue))
          )
          logger.info(s"Road $roadNumber, part $roadPartNumber: ${changed.size} updated, ${unchanged.size} kept unchanged")
          changed.foreach(addr => RoadAddressDAO.update(addr, None))
        } catch {
          case ex: InvalidAddressDataException => logger.error(s"!!! Road $roadNumber, part $roadPartNumber contains invalid address data - part skipped !!!", ex)
        }
      } else {
        logger.info(s"Not recalculating $roadNumber / $roadPartNumber because floating segments were found")
      }
    } catch {
      case a: Exception => logger.error(a.getMessage, a)
    }
  }

  private def createFormOfReservedLinksToSavedRoadParts(project: RoadAddressProject): (Seq[RoadAddressProjectFormLine], Option[RoadAddressProjectLink]) = {
    withDynTransaction {
      val createdAddresses = RoadAddressDAO.getRoadAddressProjectLinks(project.id)
      val groupedAddresses = createdAddresses.groupBy { address =>
        (address.roadNumber, address.roadPartNumber)
      }.toSeq.sortBy(_._1._2)(Ordering[Long])
      val adddressestoform = groupedAddresses.map(addressGroup => {
        val lastAddressM = addressGroup._2.last.endAddrM
        val roadLink = roadLinkService.getRoadLinksByLinkIdsFromVVH(Set(addressGroup._2.last.linkId), false)
        val addressFormLine = RoadAddressProjectFormLine(addressGroup._2.last.linkId, project.id, addressGroup._1._1, addressGroup._1._2, lastAddressM, MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(roadLink.head.municipalityCode, -1), addressGroup._2.last.discontinuityType.description)
        //TODO:case class RoadAddressProjectFormLine(projectId: Long, roadNumber: Long, roadPartNumber: Long, RoadLength: Long, ely : Long, discontinuity: String)
        addressFormLine
      })
      val addresses = createdAddresses.headOption
      (adddressestoform, addresses)
    }
  }

  private def createNewRoadLinkProject(roadAddressProject: RoadAddressProject) = {
    val project = createNewProjectToDB(roadAddressProject)
    if (project.reservedParts.isEmpty) //check if new project has links
    {
      val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(project)
      (project, None, forminfo, "ok")
    } else { //project with links success field contains errors if any, else "ok"
    val errorMessage = addLinksToProject(project)

      val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(project)

      (project, createdlink, forminfo,  errorMessage.getOrElse("ok"))
    }
  }

  def saveRoadLinkProject(roadAddressProject: RoadAddressProject): (RoadAddressProject, Option[RoadAddressProjectLink], Seq[RoadAddressProjectFormLine], String) = {
    val projectF = projectFound(roadAddressProject)
    if (projectF.isEmpty)
      createNewRoadLinkProject(roadAddressProject)
    else {
      if (roadAddressProject.reservedParts.isEmpty) { //roadaddresses to update is empty
        withDynTransaction {
          RoadAddressDAO.updateRoadAddressProject(roadAddressProject)
        }
        val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(roadAddressProject)
        (roadAddressProject, createdlink, forminfo, "ok")
      } else {
        //list contains road addresses that we need to add
        val errorMessage = addLinksToProject(roadAddressProject)
        if (errorMessage.isEmpty) {
          //adding links succeeeded
          withDynTransaction {
            RoadAddressDAO.updateRoadAddressProject(roadAddressProject)
          }
          val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(roadAddressProject)
          (roadAddressProject, createdlink, forminfo, "ok")
        } else {
          //adding links failed
          val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(roadAddressProject)
          (roadAddressProject, createdlink, forminfo, errorMessage.get)
        }
      }
    }
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
        //case 1 => project.copy(roadNumber = formInfo.head.roadNumber, startPart = formInfo.head.roadPartNumber, endPart = formInfo.head.roadPartNumber)
        case _ => project
      }

      (fullProjectInfo, formInfo)
    }
  }
}


case class RoadAddressMerge(merged: Set[Long], created: Seq[RoadAddress])
case class ReservedRoadPart(roadPartId: Long, roadNumber: Long, roadPartNumber: Long, length: Double, discontinuity: Discontinuity, ely: Long)


