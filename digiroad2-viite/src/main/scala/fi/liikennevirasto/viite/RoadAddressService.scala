package fi.liikennevirasto.viite


import fi.liikennevirasto.digiroad2.RoadLinkType.{ComplementaryRoadLinkType, FloatingRoadLinkType, NormalRoadLinkType, UnknownRoadLinkType}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
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

  def buildFloatingRoadAddressLink(rl: VVHHistoryRoadLink, roadAddrSeq: Seq[RoadAddress]): Seq[RoadAddressLink] = {
    val fusedRoadAddresses = RoadAddressLinkBuilder.fuseRoadAddress(roadAddrSeq)
    fusedRoadAddresses.map(ra => {
      RoadAddressLinkBuilder.build(rl, ra)
    })
  }

  def getRoadAddressLinks(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                          everything: Boolean = false, publicRoads: Boolean = false): Seq[RoadAddressLink] = {
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
    val fetchVVHEndTime = System.currentTimeMillis()
    logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime - fetchVVHStartTime) * 0.001))

    val fetchMissingRoadAddressStartTime = System.currentTimeMillis()
    val (floatingViiteRoadLinks, addresses, floating) = Await.result(fetchRoadAddressesByBoundingBoxF, Duration.Inf)
    val missingLinkIds = linkIds -- floating.keySet -- addresses.keySet

    val missedRL = withDynTransaction {
      RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
    }.groupBy(_.linkId)
    val fetchMissingRoadAddressEndTime = System.currentTimeMillis()
    logger.info("End fetch missing and floating road address in %.3f sec".format((fetchMissingRoadAddressEndTime - fetchMissingRoadAddressStartTime) * 0.001))

    val (changedFloating, missingFloating) = floatingViiteRoadLinks.partition(ral => linkIds.contains(ral._1))

    val buildStartTime = System.currentTimeMillis()
    val viiteRoadLinks = complementedRoadLinks.map { rl =>
      val floaters = changedFloating.getOrElse(rl.linkId, Seq())
      val ra = addresses.getOrElse(rl.linkId, Seq())
      val missed = missedRL.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed, floaters)
    }.toMap
    val buildEndTime = System.currentTimeMillis()
    logger.info("End building road address in %.3f sec".format((buildEndTime - buildStartTime) * 0.001))

    val (filledTopology, changeSet) = RoadAddressFiller.fillTopology(complementedRoadLinks, viiteRoadLinks)

    eventbus.publish("roadAddress:persistMissingRoadAddress", changeSet.missingRoadAddresses)
    eventbus.publish("roadAddress:persistAdjustments", changeSet.adjustedMValues)
    eventbus.publish("roadAddress:floatRoadAddress", changeSet.toFloatingAddressIds)


    val returningTopology = filledTopology.filter(link => !complementaryLinkIds.contains(link.linkId) ||
      complementaryLinkFilter(roadNumberLimits, municipalities, everything, publicRoads)(link))

    returningTopology ++ missingFloating.flatMap(_._2)

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

  def buildRoadAddressLink(rl: RoadLink, roadAddrSeq: Seq[RoadAddress], missing: Seq[MissingRoadAddress], floaters: Seq[RoadAddressLink] = Seq.empty): Seq[RoadAddressLink] = {
    val fusedRoadAddresses = RoadAddressLinkBuilder.fuseRoadAddress(roadAddrSeq)
    val kept = fusedRoadAddresses.map(_.id).toSet
    val removed = roadAddrSeq.map(_.id).toSet.diff(kept)
    val roadAddressesToRegister = fusedRoadAddresses.filter(_.id == -1000)
    if (roadAddressesToRegister.nonEmpty)
      eventbus.publish("roadAddress:mergeRoadAddress", RoadAddressMerge(removed, roadAddressesToRegister))
    if (floaters.nonEmpty) {
      floaters.map(_.copy(anomaly = Anomaly.GeometryChanged, newGeometry = Option(rl.geometry)))
    } else {
      fusedRoadAddresses.map(ra => {
        RoadAddressLinkBuilder.build(rl, ra)
      }) ++
        missing.map(m => RoadAddressLinkBuilder.build(rl, m)).filter(_.length > 0.0)
    }
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
  def checkRoadAddressFloatingWithoutTX(ids: Set[Long], float: Boolean = false): Unit = {
    val addresses = RoadAddressDAO.queryById(ids)
    val linkIdMap = addresses.groupBy(_.linkId).mapValues(_.map(_.id))
    val roadLinks = roadLinkService.getCurrentAndComplementaryVVHRoadLinks(linkIdMap.keySet)
    addresses.foreach { address =>
      val roadLink = roadLinks.find(_.linkId == address.linkId)
      val addressGeometry = roadLink.map(rl =>
        GeometryUtils.truncateGeometry3D(rl.geometry, address.startMValue, address.endMValue))
      if(float && !(roadLink.isEmpty || addressGeometry.isEmpty || GeometryUtils.geometryLength(addressGeometry.get) == 0.0)){
        println("Floating and update geometry id %d (link id %d)".format(address.id, address.linkId))
        RoadAddressDAO.changeRoadAddressFloating(float = true, address.id, addressGeometry)
        val missing = new MissingRoadAddress(address.linkId, None, None, RoadAddressLinkBuilder.getRoadType(roadLink.get.administrativeClass, UnknownLinkType), None,None,None ,None,Anomaly.GeometryChanged)
        createSingleMissingRoadAddress(missing)
      }
      else if (roadLink.isEmpty || addressGeometry.isEmpty || GeometryUtils.geometryLength(addressGeometry.get) == 0.0) {
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

  def getFloatingAdjacent(chainLinks: Set[Long], linkId: Long, roadNumber: Long, roadPartNumber: Long, trackCode: Long): Seq[RoadAddressLink] = {
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

      val checkLinkAnomaly = withDynTransaction {
        RoadAddressDAO.getMissingRoadAddresses(Set(linkId))
      }.headOption

      val missingLinkIds = if (checkLinkAnomaly.nonEmpty){
        val missingAnomaly = checkLinkAnomaly.get.anomaly.value match {
          case 1 => linkIds -- floating.keySet -- addresses.keySet
          case 2 => linkIds -- addresses.keySet
        }
        missingAnomaly
      } else Set.empty[Long]

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
    //TODO show all returning adjacent targets as possible selecting markers for the current linkId, in the UI.
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

  /**
    * Take two sequences of road address links and order them so that the sequence covers the same road geometry
    * and logical addressing in the same order
    * @param sources Source road address links (floating)
    * @param targets Target road address links (missing addresses)
    * @return
    */
  def orderRoadAddressLinks(sources: Seq[RoadAddressLink], targets: Seq[RoadAddressLink]): (Seq[RoadAddressLink], Seq[RoadAddressLink]) = {
    /*
      Calculate sidecode changes - if next road address link starts with the current (end) point
      then side code remains the same. Otherwise it's reversed
     */
    def getSideCode(roadAddressLink: RoadAddressLink, previousSideCode: SideCode, currentPoint: Point) = {
      val geom = roadAddressLink.geometry
      if (GeometryUtils.areAdjacent(geom.head, currentPoint))
        previousSideCode
      else
        switchSideCode(previousSideCode)
    }

    def extending(link: RoadAddressLink, ext: RoadAddressLink) = {
      link.roadNumber == ext.roadNumber && link.roadPartNumber == ext.roadPartNumber &&
        link.trackCode == ext.trackCode && link.endAddressM == ext.startAddressM
    }
    def extendChainByAddress(ordered: Seq[RoadAddressLink], unordered: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
      if (ordered.isEmpty)
        return extendChainByAddress(Seq(unordered.head), unordered.tail)
      if (unordered.isEmpty)
        return ordered
      val (next, rest) = unordered.partition(u => extending(ordered.last, u))
      if (next.nonEmpty)
        extendChainByAddress(ordered ++ next, rest)
      else {
        val (previous, rest) = unordered.partition(u => extending(u, ordered.head))
        if (previous.isEmpty)
          throw new IllegalArgumentException("Non-contiguous road addressing")
        else
          extendChainByAddress(previous ++ ordered, rest)
      }
    }
    def extendChainByGeometry(ordered: Seq[RoadAddressLink], unordered: Seq[RoadAddressLink], sideCode: SideCode): Seq[RoadAddressLink] = {
      if (ordered.isEmpty)
        return extendChainByGeometry(Seq(unordered.head), unordered.tail, sideCode)
      if (unordered.isEmpty)
        return ordered
      // Current position we are building from
      val endPoint = sideCode match {
        case SideCode.TowardsDigitizing => ordered.last.geometry.last
        case SideCode.AgainstDigitizing => ordered.last.geometry.head
        case _ => throw new InvalidAddressDataException("Bad sidecode on chain")
      }
      val (next, rest) = unordered.partition(u => GeometryUtils.minimumDistance(endPoint, u.geometry) < 0.1)
      if (next.nonEmpty) {
        extendChainByGeometry(ordered ++ next, rest, getSideCode(next.head, sideCode, endPoint))
      }
      else {
        val startPoint = ordered.head.geometry.head
        val (previous, rest) = unordered.partition(u => GeometryUtils.minimumDistance(startPoint, u.geometry) < 0.1)
        if (previous.isEmpty)
          throw new IllegalArgumentException("Non-contiguous road target geometry")
        else
          extendChainByGeometry(previous ++ ordered, rest, getSideCode(previous.head, sideCode, endPoint))
      }
    }
    val orderedSources = extendChainByAddress(Seq(sources.head), sources.tail)
    val startingPoint = orderedSources.head.sideCode match {
      case SideCode.TowardsDigitizing => orderedSources.head.geometry.head
      case SideCode.AgainstDigitizing => orderedSources.head.geometry.last
      case _ => throw new InvalidAddressDataException("Bad sidecode on source")
    }
    val preSortedTargets = targets.sortBy(l => GeometryUtils.minimumDistance(startingPoint, l.geometry))
    val startingSideCode = if (GeometryUtils.areAdjacent(startingPoint, preSortedTargets.head.geometry.head))
      SideCode.TowardsDigitizing
    else
      SideCode.AgainstDigitizing
    (orderedSources, extendChainByGeometry(Seq(preSortedTargets.head), preSortedTargets.tail, startingSideCode))
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

    val (orderedSources, orderedTargets) = orderRoadAddressLinks(sources, targets)

    val allLinks = orderedSources ++ orderedTargets
    val targetsGeomLength = orderedTargets.map(_.length).sum

    val allStartCp = orderedSources.flatMap(_.startCalibrationPoint)
    val allEndCp = orderedSources.flatMap(_.endCalibrationPoint)
    if (allStartCp.size > 1 || allEndCp.size > 1)
      throw new IllegalArgumentException("Source data contains too many calibration points")

    val minStartAddressM = orderedSources.head.startAddressM
    val maxEndAddressM = orderedSources.last.endAddressM

    val adjustedSegments = adjustTopology.foldLeft(orderedSources) { (previousSources, operation) => operation(targetsGeomLength, previousSources) }

    val maxEndMValue = allLinks.flatMap(_.endCalibrationPoint) match {
      case Nil => adjustedSegments.map(_.endMValue).max
      case _ => getMValues(Option(adjustedSegments.flatMap(_.endCalibrationPoint).map(_.segmentMValue).max), Option(allLinks.map(_.endMValue).max))
    }

    val source = orderedSources.head

    val startCp = allStartCp.headOption
    val endCp = allEndCp.headOption

    if (startCp.nonEmpty && source.startCalibrationPoint.isEmpty)
      throw new IllegalArgumentException("Start calibration point not in the first link of source")

    if (endCp.nonEmpty && orderedSources.last.endCalibrationPoint.isEmpty)
      throw new IllegalArgumentException("Start calibration point not in the first link of source")

    val adjustedCreatedRoads = orderedTargets.foldLeft(orderedTargets) { (previousTargets, target) =>
      RoadAddressLinkBuilder.adjustRoadAddressTopology(orderedTargets.length, startCp, endCp, maxEndMValue, minStartAddressM, maxEndAddressM, source, target, previousTargets, user.username).filterNot(_.id == 0 && source.linkId == target.linkId) }

    // Figure out first link's side code
    val startingSideCode = if (GeometryUtils.areAdjacent(adjustedCreatedRoads.head.geometry.head, orderedSources.head.geometry.head) ||
      GeometryUtils.areAdjacent(adjustedCreatedRoads.head.geometry.last, orderedSources.head.geometry.last)) {
      orderedSources.head.sideCode
    } else {
      switchSideCode(orderedSources.head.sideCode)
    }

    // Adjust SideCode according to the starting link
    adjustSideCodes(Seq(adjustedCreatedRoads.head.copy(sideCode = startingSideCode)), adjustedCreatedRoads.tail)

  }

  private def adjustSideCodes(ready: Seq[RoadAddressLink], unprocessed: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    if (unprocessed.isEmpty)
      ready
    else {
      val last = ready.last
      val next = unprocessed.head
      val endPoint = if (ready.last.sideCode == SideCode.TowardsDigitizing) ready.last.geometry.last else  ready.last.geometry.head
      val touch = (GeometryUtils.areAdjacent(next.geometry.head, endPoint), GeometryUtils.areAdjacent(next.geometry.last, endPoint))
      val nextSideCode = touch match {
        case (true, false) => SideCode.TowardsDigitizing
        case (false, true) => SideCode.AgainstDigitizing
        //Overlapping or non-touching geometries
        case (_, _) => throw new IllegalArgumentException("Touching geometries are invalid: %s %s".format(last.geometry, next.geometry))
      }
      adjustSideCodes(ready++Seq(next.copy(sideCode=nextSideCode)), unprocessed.tail)
    }
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

  def switchSideCode(sideCode: SideCode) = {
    // Switch between against and towards 2 -> 3, 3 -> 2
    SideCode.apply(5-sideCode.value)
  }

}


case class RoadAddressMerge(merged: Set[Long], created: Seq[RoadAddress])
case class ReservedRoadPart(roadPartId: Long, roadNumber: Long, roadPartNumber: Long, length: Double, discontinuity: Discontinuity, ely: Long)


