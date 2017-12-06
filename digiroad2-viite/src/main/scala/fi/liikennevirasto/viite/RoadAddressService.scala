package fi.liikennevirasto.viite
import java.util.Properties
import fi.liikennevirasto.digiroad2.RoadLinkType.UnknownRoadLinkType
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.process.RoadAddressFiller.{AddressChangeSet, LRMValueAdjustment}
import fi.liikennevirasto.viite.process._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class RoadAddressService(roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, frozenTimeVVHAPIServiceEnabled: Boolean = false) {

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
  val newTransaction=true
  class Contains(r: Range) {
    def unapply(i: Int): Boolean = r contains i
  }

  private def fetchRoadLinksWithComplementary(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                              everything: Boolean = false, publicRoads: Boolean = false): (Seq[RoadLink], Seq[RoadLink]) = {
    val roadLinksF = Future(roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads,frozenTimeVVHAPIServiceEnabled))
    val complementaryLinksF = Future(roadLinkService.getComplementaryRoadLinksFromVVH(boundingRectangle, municipalities))
    val (roadLinks, complementaryLinks) = Await.result(roadLinksF.zip(complementaryLinksF), Duration.Inf)
    (roadLinks, complementaryLinks)
  }

  private def fetchRoadAddressesByBoundingBox(boundingRectangle: BoundingRectangle, fetchOnlyFloating: Boolean = false,
                                              onlyNormalRoads: Boolean= false, roadNumberLimits: Seq[(Int, Int)] = Seq()) = {
    val (floatingAddresses, nonFloatingAddresses) = withDynTransaction {
      RoadAddressDAO.fetchRoadAddressesByBoundingBox(boundingRectangle, fetchOnlyFloating, onlyNormalRoads, roadNumberLimits).partition(_.floating)
    }

    val floating = floatingAddresses.groupBy(_.linkId)
    val addresses = nonFloatingAddresses.groupBy(_.linkId)

    val floatingHistoryRoadLinks = withDynTransaction {
      roadLinkService.getViiteRoadLinksHistoryFromVVH(floating.keySet)
    }

    val historyLinkAddresses = floatingHistoryRoadLinks.filter(rl => floating.keySet.contains(rl.linkId)).map { rl =>
      val ra = floating.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildFloatingRoadAddressLink(rl, ra)
    }.toMap
    RoadAddressResult(historyLinkAddresses, addresses, floating)
  }

  private def fetchMissingRoadAddressesByBoundingBox(boundingRectangle: BoundingRectangle, fetchOnlyFloating: Boolean = false) = {
    withDynTransaction {
      RoadAddressDAO.fetchMissingRoadAddressesByBoundingBox(boundingRectangle).groupBy(_.linkId)
    }
  }

  def buildFloatingRoadAddressLink(rl: VVHHistoryRoadLink, roadAddrSeq: Seq[RoadAddress]): Seq[RoadAddressLink] = {
    val fusedRoadAddresses = RoadAddressLinkBuilder.fuseRoadAddress(roadAddrSeq)
    fusedRoadAddresses.map(ra => {
      RoadAddressLinkBuilder.build(rl, ra)
    })
  }

  def getSuravageRoadLinkAddresses(boundingRectangle: BoundingRectangle, municipalities: Set[Int],
                                   boundingBoxResult: BoundingBoxResult) :Seq[RoadAddressLink]= {
    Await.result(boundingBoxResult.suravageF, Duration.Inf).map( suravage=>RoadAddressLinkBuilder.buildSuravageRoadAddressLink(suravage))
  }

  def getSuravageRoadLinkAddressesByLinkIds(linkIdsToGet: Set[Long]): Seq[RoadAddressLink] = {
    val suravageLinks = roadLinkService.fetchSuravageLinksByLinkIdsFromVVH(linkIdsToGet)
    suravageLinks.map( suravage=>RoadAddressLinkBuilder.buildSuravageRoadAddressLink(suravage))
  }

  def fetchBoundingBoxF(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                        everything: Boolean = false, publicRoads: Boolean = false): BoundingBoxResult = {
    BoundingBoxResult(
      roadLinkService.getChangeInfoFromVVHF(boundingRectangle, municipalities),
      Future(fetchRoadAddressesByBoundingBox(boundingRectangle)),
      Future(roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads,frozenTimeVVHAPIServiceEnabled)),
      Future(roadLinkService.getComplementaryRoadLinksFromVVH(boundingRectangle, municipalities)),
      roadLinkService.getSuravageLinksFromVVHF(boundingRectangle, municipalities)
    )
  }

  def getRoadAddressLinksWithSuravage(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                      everything: Boolean = false, publicRoads: Boolean = false): Seq[RoadAddressLink] ={
    val combinedFuture = fetchBoundingBoxF(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads)
    val roadAddressLinks = getRoadAddressLinks(combinedFuture, boundingRectangle, Seq(), municipalities, everything)
    val suravageAddresses = getSuravageRoadLinkAddresses(boundingRectangle, municipalities, combinedFuture)
    suravageAddresses ++ roadAddressLinks
  }

  private def withTiming[T](f: => T, s: String): T = {
    val startTime = System.currentTimeMillis()
    val t = f
    logger.info(s.format((System.currentTimeMillis() - startTime) * 0.001))
    t
  }

  def getRoadAddressLinks(boundingBoxResult: BoundingBoxResult,
                          boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                          everything: Boolean = false, publicRoads: Boolean = false): Seq[RoadAddressLink] = {
    def complementaryLinkFilter(roadAddressLink: RoadAddressLink) = {
      everything || publicRoads || roadNumberLimits.exists {
        case (start, stop) => roadAddressLink.roadNumber >= start && roadAddressLink.roadNumber <= stop
      }
    }
    //TODO use complementedIds instead of only roadLinkIds below. There is no complementary ids for changeInfo dealing (for now)
    val combinedFuture=
      for {
        changedRoadLinksF <- boundingBoxResult.changeInfoF
        roadLinkFuture <- boundingBoxResult.roadLinkF
        complementaryFuture <- boundingBoxResult.complementaryF
        fetchRoadAddressesByBoundingBoxF <- boundingBoxResult.roadAddressResultF
      }  yield( changedRoadLinksF, (roadLinkFuture, complementaryFuture),fetchRoadAddressesByBoundingBoxF)
    val (changedRoadLinks,((roadLinks, complementaryLinks)),roadAddressResults) =
      withTiming(
        Await.result(combinedFuture, Duration.Inf), "End fetch vvh road links in %.3f sec"
      )
    val (historyLinkAddresses, addresses, floating) = (roadAddressResults.historyLinkAddresses, roadAddressResults.current, roadAddressResults.floating)
    val (roadsWithEndDate, roadsWithoutEndDate) = addresses.values.flatten.partition(a => a.endDate.isDefined)
    val filteredRoadsWithEndDate = roadsWithEndDate.filterNot(r => roadsWithoutEndDate.exists(_.linkId == r.linkId))
    val complementaryLinkIds = complementaryLinks.map(_.linkId)
    val roadsWithEndDateLinkIds = filteredRoadsWithEndDate.map(_.linkId).toSet
    val normalRoadLinkIds = roadLinks.filterNot(rl => roadsWithEndDateLinkIds.contains(rl.linkId)).map(_.linkId)
    val allRoadLinks = roadLinks.filterNot(rl => roadsWithEndDateLinkIds.contains(rl.linkId))++complementaryLinks
    val linkIds = (complementaryLinkIds ++ normalRoadLinkIds).toSet

    //TODO: In the future when we are dealing with VVHChangeInfo we need to better evaluate when do we switch from bounding box queries to
    //pure linkId based queries, maybe something related to the zoomLevel we are in map level.
    val filteredChangedRoadLinks = changedRoadLinks.filter(crl => crl.oldId.exists(id =>
        addresses.keySet.contains(id) || normalRoadLinkIds.contains(id)))
    val allRoadAddressesAfterChangeTable = applyChanges(allRoadLinks, if (!frozenTimeVVHAPIServiceEnabled) filteredChangedRoadLinks else Seq(), addresses)
    val missingLinkIds = linkIds -- floating.keySet -- allRoadAddressesAfterChangeTable.keySet
    val missedRL = withTiming(
      withDynTransaction {
        if (everything || !frozenTimeVVHAPIServiceEnabled) {
          RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
        } else {
          List[MissingRoadAddress]()
        }
      }.groupBy(_.linkId), "End finding missing road addresses in %.3f sec")
    val (changedFloating, missingFloating) = historyLinkAddresses.partition(ral => linkIds.contains(ral._1))
    val roadAddressLinkMap = createRoadAddressLinkMap(allRoadLinks, changedFloating, allRoadAddressesAfterChangeTable, missedRL)

    val (filledTopology, changeSet) = RoadAddressFiller.fillTopology(allRoadLinks, roadAddressLinkMap)

    publishChangeSet(changeSet)
    val returningTopology = filledTopology.filter(link => !complementaryLinkIds.contains(link.linkId) ||
      complementaryLinkFilter(link))

    returningTopology ++ missingFloating.flatMap(_._2)

  }

  private def publishChangeSet(changeSet: AddressChangeSet): Unit = {
    //Temporary filter for missing road addresses QA
    if(!frozenTimeVVHAPIServiceEnabled){
      eventbus.publish("roadAddress:persistMissingRoadAddress", changeSet.missingRoadAddresses)
    }
    eventbus.publish("roadAddress:persistAdjustments", changeSet.adjustedMValues)
    eventbus.publish("roadAddress:floatRoadAddress", changeSet.toFloatingAddressIds)
  }

  private def createRoadAddressLinkMap(roadLinks: Seq[RoadLink], toFloating: Map[Long, Seq[RoadAddressLink]],
                                       addresses: Map[Long, Seq[RoadAddress]],
                                       missedRL: Map[Long, List[MissingRoadAddress]]): Map[Long, Seq[RoadAddressLink]] = {
    roadLinks.map { rl =>
      val floaters = toFloating.getOrElse(rl.linkId, Seq())
      val ra = addresses.getOrElse(rl.linkId, Seq())
      val missed = missedRL.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed, floaters)
    }.toMap

  }

  def getRoadAddressLinksByLinkId(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int]): Seq[RoadAddressLink] = {

    val fetchAddrStartTime = System.currentTimeMillis()
    val fetchRoadAddressesByBoundingBoxF = Future(fetchRoadAddressesByBoundingBox(boundingRectangle, false, true, roadNumberLimits))
    val fetchMissingRoadAddressesByBoundingBoxF = Future(fetchMissingRoadAddressesByBoundingBox(boundingRectangle))

    val fetchResult = Await.result(fetchRoadAddressesByBoundingBoxF, Duration.Inf)
    val (historyLinkAddresses, addresses) = (fetchResult.historyLinkAddresses, fetchResult.current)
    val missingViiteRoadAddress = if(!frozenTimeVVHAPIServiceEnabled) Await.result(fetchMissingRoadAddressesByBoundingBoxF, Duration.Inf) else Map[Long, Seq[MissingRoadAddress]]()
    logger.info("End fetch addresses in %.3f sec".format((System.currentTimeMillis() - fetchAddrStartTime) * 0.001))

    val roadsWithEndDate = addresses.values.flatten.filter(a => a.endDate.isDefined).map(_.linkId).toSeq

    val addressLinkIds = addresses.filterNot(rl => roadsWithEndDate.contains(rl._1)).keySet ++ missingViiteRoadAddress.keySet
    val fetchVVHStartTime = System.currentTimeMillis()
    val changedRoadLinksF = if (!frozenTimeVVHAPIServiceEnabled) roadLinkService.getChangeInfoFromVVHF(addressLinkIds) else Future(Seq())

    val roadLinks = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(addressLinkIds,newTransaction,frozenTimeVVHAPIServiceEnabled)

    val fetchVVHEndTime = System.currentTimeMillis()
    logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime - fetchVVHStartTime) * 0.001))

    val linkIds = roadLinks.map(_.linkId).toSet

    val filteredChangedRoadLinks = Await.result(changedRoadLinksF, Duration.Inf).filter(crl => crl.oldId.exists(id =>
      linkIds.contains(id) && addresses.getOrElse(id, Seq()).exists(ra => crl.affects(ra.linkId, ra.adjustedTimestamp))))
    logger.info("End change info in %.3f sec".format((System.currentTimeMillis() - fetchVVHEndTime) * 0.001))

    val complementedWithChangeAddresses = applyChanges(roadLinks, if (!frozenTimeVVHAPIServiceEnabled) filteredChangedRoadLinks else Seq(), addresses)

    val (changedFloating, missingFloating) = historyLinkAddresses.partition(ral => linkIds.contains(ral._1))

    val buildStartTime = System.currentTimeMillis()
    val viiteRoadLinks = roadLinks.map { rl =>
      val floaters = changedFloating.getOrElse(rl.linkId, Seq())
      val ra = complementedWithChangeAddresses.getOrElse(rl.linkId, Seq())
      val missed = missingViiteRoadAddress.getOrElse(rl.linkId, Seq())
      rl.linkId -> buildRoadAddressLink(rl, ra, missed, floaters)
    }.toMap
    val buildEndTime = System.currentTimeMillis()
    logger.info("End building road address in %.3f sec".format((buildEndTime - buildStartTime) * 0.001))

    val (filledTopology, changeSet) = RoadAddressFiller.fillTopology(roadLinks, viiteRoadLinks)

    publishChangeSet(changeSet)

    val returningTopology = filledTopology.filterNot(p => p.anomaly == Anomaly.NoAddressGiven)

    returningTopology ++ missingFloating.flatMap(_._2)

  }

  def applyChanges(roadLinks: Seq[RoadLink], changedRoadLinks: Seq[ChangeInfo], addresses: Map[Long, Seq[RoadAddress]]): Map[Long, Seq[RoadAddress]] = {
    if (changedRoadLinks.isEmpty)
      addresses
    else
      withDynTransaction {
        val newRoadAddresses = RoadAddressChangeInfoMapper.resolveChangesToMap(addresses, roadLinks, changedRoadLinks)
        val roadLinkMap = roadLinks.map(rl => rl.linkId -> rl).toMap

        val (addressesToCreate, unchanged) = newRoadAddresses.values.flatten.toSeq.partition(_.id == NewRoadAddress)
        val savedRoadAddresses = addressesToCreate.filter(r => roadLinkMap.contains(r.linkId)).map(r =>
          r.copy(geometry = GeometryUtils.truncateGeometry3D(roadLinkMap(r.linkId).geometry,
            r.startMValue, r.endMValue), linkGeomSource = roadLinkMap(r.linkId).linkSource))

        val ids = RoadAddressDAO.create(savedRoadAddresses).toSet ++ unchanged.map(_.id).toSet

        val removedIds = addresses.values.flatten.map(_.id).toSet -- ids
        removedIds.grouped(500).foreach(s => {RoadAddressDAO.expireById(s)
          logger.debug("Expired: "+s.mkString(","))
        })

        val changedRoadParts = addressesToCreate.map(a => a.roadNumber -> a.roadPartNumber).groupBy(_._1).mapValues(seq => seq.map(_._2).toSet)

        val adjustedRoadParts = changedRoadParts.flatMap(r => r._2.map(p => r._1 -> p)).toSeq.filter { x =>
          recalculateRoadAddresses(x._1, x._2)}
        // re-fetch after recalculation
        val adjustedAddresses = adjustedRoadParts.flatMap { case (road, part) => RoadAddressDAO.fetchByRoadPart(road, part) }

        (adjustedAddresses ++ RoadAddressDAO.fetchByIdMassQuery(ids -- adjustedAddresses.map(_.id).toSet, true, true)).groupBy(_.linkId)
      }
  }

  /**
    * Returns missing road addresses for links that did not already exist in database
    *
    * @param roadNumberLimits
    * @param municipality
    * @return
    */
  def getMissingRoadAddresses(roadNumberLimits: Seq[(Int, Int)], municipality: Int) = {
    val roadLinks = roadLinkService.getViiteCurrentAndComplementaryRoadLinksFromVVH(municipality, roadNumberLimits,frozenTimeVVHAPIServiceEnabled)
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
    val roadAddressesToRegister = fusedRoadAddresses.filter(_.id == fi.liikennevirasto.viite.NewRoadAddress)
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
        geometry = Seq(min.geometry.head, max.geometry.last))
    }
  }

  def getRoadParts(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int]) = {
    val addresses = withDynTransaction {
      RoadAddressDAO.fetchPartsByRoadNumbers(boundingRectangle, roadNumberLimits).groupBy(_.linkId)
    }

    val vvhRoadLinks = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(addresses.keySet,newTransaction,frozenTimeVVHAPIServiceEnabled)
    val combined = addresses.mapValues(combineGeom)
    val roadLinks = vvhRoadLinks.map(rl => rl -> combined(rl.linkId)).toMap

    roadLinks.flatMap { case (rl, ra) =>
      buildRoadAddressLink(rl, Seq(ra), Seq())
    }.toSeq
  }

  // Boolean  (frozenTimeVVHAPIServiceEnabled)  should be added if we start using this method  for disabling changeAPI and switch to frozentime VVH API)
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
    val anomaly = missedRL.headOption.map(_.anomaly).getOrElse(Anomaly.None)
    val (roadLinks, vvhHistoryLinks) = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(Set(id),frozenTimeVVHAPIServiceEnabled)
    (anomaly, addresses.size, roadLinks.size) match {
      case (_, 0, 0) => List() // No road link currently exists and no addresses on this link id => ignore
      case (Anomaly.GeometryChanged, _, _) => addresses.flatMap(a => vvhHistoryLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (_, _, 0) => addresses.flatMap(a => vvhHistoryLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (Anomaly.NoAddressGiven, 0, _) => missedRL.flatMap(a => roadLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
      case (_, _, _) => addresses.flatMap(a => roadLinks.map(rl => RoadAddressLinkBuilder.build(rl, a)))
    }
  }

  def getTargetRoadLink(linkId: Long): RoadAddressLink = {
    val (roadLinks, _) = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(Set(linkId),frozenTimeVVHAPIServiceEnabled)
    if (roadLinks.isEmpty) {
      throw new InvalidAddressDataException(s"Can't find road link for target link id $linkId")
    } else{
      RoadAddressLinkBuilder.build(roadLinks.head, MissingRoadAddress(linkId = linkId, None, None, RoadType.Unknown, None, None, None, None, anomaly = Anomaly.NoAddressGiven,Seq.empty[Point]))
    }
  }

  def getUniqueRoadAddressLink(id: Long) = getRoadAddressLink(id)

  def roadClass(roadNumber: Long) = {
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
      val roadNum: Int = roadNumber.toInt
      roadNum match {
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
    val unMergedCount = RoadAddressDAO.queryById(data.merged).size
    if (unMergedCount != data.merged.size)
      throw new InvalidAddressDataException("Data modified while updating, rolling back transaction: some source rows no longer valid")
    val mergedCount = expireRoadAddresses(data.merged)
    if (mergedCount == data.merged.size)
      createMergedSegments(data.created)
    else
      throw new InvalidAddressDataException("Data modified while updating, rolling back transaction: some source rows not updated")
  }

  def createMergedSegments(mergedRoadAddress: Seq[RoadAddress]) = {
    mergedRoadAddress.grouped(500).foreach(group => RoadAddressDAO.create(group, Some("Automatic_merged")))
  }

  def expireRoadAddresses(expiredIds: Set[Long]) = {
    expiredIds.grouped(500).map(group => RoadAddressDAO.expireById(group)).sum
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
    def nonEmptyTargetLinkGeometry(roadLinkOpt: Option[RoadLinkLike], geometryOpt: Option[Seq[Point]]) = {
      !(roadLinkOpt.isEmpty || geometryOpt.isEmpty || GeometryUtils.geometryLength(geometryOpt.get) == 0.0)
    }
    val addresses = RoadAddressDAO.queryById(ids)
    val linkIdMap = addresses.groupBy(_.linkId).mapValues(_.map(_.id))
    val roadLinks = roadLinkService.getCurrentAndComplementaryVVHRoadLinks(linkIdMap.keySet,frozenTimeVVHAPIServiceEnabled)
    addresses.foreach { address =>
      val roadLink = roadLinks.find(_.linkId == address.linkId)
      val addressGeometry = roadLink.map(rl =>
        GeometryUtils.truncateGeometry3D(rl.geometry, address.startMValue, address.endMValue))
      if(float && nonEmptyTargetLinkGeometry(roadLink, addressGeometry)){
        println("Floating and update geometry id %d (link id %d)".format(address.id, address.linkId))
        RoadAddressDAO.changeRoadAddressFloating(float = true, address.id, addressGeometry)
        val missing = new MissingRoadAddress(address.linkId, Some(address.startAddrMValue), Some(address.endAddrMValue), RoadAddressLinkBuilder.getRoadType(roadLink.get.administrativeClass, UnknownLinkType), None,None,Some(address.startMValue) ,Some(address.endMValue),Anomaly.GeometryChanged,Seq.empty[Point])
        RoadAddressDAO.createMissingRoadAddress(missing.linkId, missing.startAddrMValue.getOrElse(0), missing.endAddrMValue.getOrElse(0), missing.anomaly.value, missing.startMValue.get, missing.endMValue.get)
      } else if (!nonEmptyTargetLinkGeometry(roadLink, addressGeometry)) {
        println("Floating id %d (link id %d)".format(address.id, address.linkId))
        RoadAddressDAO.changeRoadAddressFloating(float = true, address.id, None)
      } else {
        if (!GeometryUtils.areAdjacent(addressGeometry.get, address.geometry)) {
          println("Updating geometry for id %d (link id %d)".format(address.id, address.linkId))
          RoadAddressDAO.changeRoadAddressFloating(float = false, address.id, addressGeometry)
        }
      }
    }
  }

  /*
    Kalpa-API methods
  */

  def getRoadAddressesLinkByMunicipality(municipality: Int, roadLinkDataTempAPI:Boolean=false): Seq[RoadAddressLink] = {
    //TODO: Remove null checks and make sure no nulls are generated
    val roadLinks = {
      val tempRoadLinks = roadLinkService.getViiteRoadLinksFromVVHByMunicipality(municipality,frozenTimeVVHAPIServiceEnabled)
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
        RoadAddressDAO.fetchByLinkId(roadLinksWithComplimentary.map(_.linkId).toSet, includeFloating = false, includeHistory = false).groupBy(_.linkId)
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

    publishChangeSet(changeSet)

    filledTopology
  }

  def saveAdjustments(addresses: Seq[LRMValueAdjustment]): Unit = {
    withDynTransaction {
      addresses.foreach(RoadAddressDAO.updateLRM)
    }
  }

  private def getAdjacentAddresses(chainLinks: Set[Long], linkId: Long, roadNumber: Long, roadPartNumber: Long, track: Track) = {
    withDynSession {
      val ra = RoadAddressDAO.fetchByLinkId(chainLinks, includeFloating = true).sortBy(_.startAddrMValue)
      assert(ra.forall(r => r.roadNumber == roadNumber && r.roadPartNumber == roadPartNumber && r.track == track),
        s"Mixed floating addresses selected ($roadNumber/$roadPartNumber/$track): " + ra.map(r =>
          s"${r.linkId} = ${r.roadNumber}/${r.roadPartNumber}/${r.track.value}").mkString(", "))
      val startValues = ra.map(_.startAddrMValue)
      val endValues = ra.map(_.endAddrMValue)
      val orphanStarts = startValues.filterNot(st => endValues.contains(st))
      val orphanEnds = endValues.filterNot(st => startValues.contains(st))
      (orphanStarts.flatMap(st => RoadAddressDAO.fetchByAddressEnd(roadNumber, roadPartNumber, track, st))
        ++ orphanEnds.flatMap(end => RoadAddressDAO.fetchByAddressStart(roadNumber, roadPartNumber, track, end)))
        .distinct
    }
  }

  def getFloatingAdjacent(chainLinks: Set[Long], linkId: Long, roadNumber: Long, roadPartNumber: Long, trackCode: Int): Seq[RoadAddressLink] = {
    val adjacentAddresses = getAdjacentAddresses(chainLinks, linkId, roadNumber, roadPartNumber, Track.apply(trackCode))
    val adjacentLinkIds = adjacentAddresses.map(_.linkId).toSet
    val roadLinks = roadLinkService.getViiteCurrentAndHistoryRoadLinksFromVVH(adjacentLinkIds,frozenTimeVVHAPIServiceEnabled)
    val adjacentAddressLinks = roadLinks._1.map(rl => rl.linkId -> rl).toMap
    val historyLinks = roadLinks._2.groupBy(rl => rl.linkId)

    val anomaly2List = withDynSession{ RoadAddressDAO.getMissingRoadAddresses(adjacentLinkIds).filter(_.anomaly == Anomaly.GeometryChanged) }

    val floatingAdjacents = adjacentAddresses.filter(_.floating).map(ra =>
      if (anomaly2List.exists(_.linkId == ra.linkId)) {
        val rl = adjacentAddressLinks(ra.linkId)
        RoadAddressLinkBuilder.build(rl, ra, true, Some(rl.geometry))
      } else {
        RoadAddressLinkBuilder.build(historyLinks(ra.linkId).head, ra)
      }
    )
    floatingAdjacents
  }

  def getAdjacent(chainLinks: Set[Long], linkId: Long): Seq[RoadAddressLink] = {
    val chainRoadLinks = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(chainLinks,newTransaction,frozenTimeVVHAPIServiceEnabled)
    val pointCloud = chainRoadLinks.map(_.geometry).map(GeometryUtils.geometryEndpoints).flatMap(x => Seq(x._1, x._2))
    val boundingPoints = GeometryUtils.boundingRectangleCorners(pointCloud)
    val boundingRectangle = BoundingRectangle(boundingPoints._1 + Vector3d(-.1, .1, 0.0), boundingPoints._2 + Vector3d(.1, -.1, 0.0))
    val connectedLinks =  roadLinkService.getRoadLinksAndChangesFromVVHWithFrozenTimeAPI(boundingRectangle,Set(),frozenTimeVVHAPIServiceEnabled)._1
      .filterNot(rl => chainLinks.contains(rl.linkId))
      .filter{rl =>
        val endPoints = GeometryUtils.geometryEndpoints(rl.geometry)
        pointCloud.exists(p => GeometryUtils.areAdjacent(p, endPoints._1) || GeometryUtils.areAdjacent(p, endPoints._2))
      }.map(rl => rl.linkId -> rl).toMap
    val missingLinks = withDynSession {
      RoadAddressDAO.getMissingRoadAddresses(connectedLinks.keySet)
    }
    missingLinks.map(ml => RoadAddressLinkBuilder.build(connectedLinks(ml.linkId), ml))
  }

  def getRoadAddressLinksAfterCalculation(sources: Seq[String], targets: Seq[String], user: User): Seq[RoadAddressLink] = {
    val transferredRoadAddresses = getRoadAddressesAfterCalculation(sources, targets, user)
    val target = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(targets.map(rd => rd.toLong).toSet,newTransaction,frozenTimeVVHAPIServiceEnabled)
    transferredRoadAddresses.map(ra => RoadAddressLinkBuilder.build(target.find(_.linkId == ra.linkId).get, ra))
  }

  def getRoadAddressesAfterCalculation(sources: Seq[String], targets: Seq[String], user: User): Seq[RoadAddress] = {
    def adjustGeometry(ra: RoadAddress, link: RoadAddressLinkLike): RoadAddress = {
      val geom = GeometryUtils.truncateGeometry3D(link.geometry, ra.startMValue, ra.endMValue)
      ra.copy(geometry = geom, linkGeomSource = link.roadLinkSource)
    }
    val sourceRoadAddressLinks = sources.flatMap(rd => {
      getRoadAddressLink(rd.toLong)
    })
    val targetIds = targets.map(rd => rd.toLong).toSet
    val targetRoadAddressLinks = targetIds.toSeq.map(getTargetRoadLink)
    val targetLinkMap: Map[Long, RoadAddressLinkLike] = targetRoadAddressLinks.map(l => l.linkId -> l).toMap
    transferRoadAddress(sourceRoadAddressLinks, targetRoadAddressLinks, user).map(ra => adjustGeometry(ra, targetLinkMap(ra.linkId)))
  }

  def transferFloatingToGap(sourceIds: Set[Long], targetIds: Set[Long], roadAddresses: Seq[RoadAddress], username: String): Unit = {
    withDynTransaction {
      val currentRoadAddresses = RoadAddressDAO.fetchByLinkId(sourceIds, includeFloating = true, includeHistory = true)
      RoadAddressDAO.expireById(currentRoadAddresses.map(_.id).toSet)
      RoadAddressDAO.create(roadAddresses, Some(username))
      recalculateRoadAddresses(roadAddresses.head.roadNumber.toInt, roadAddresses.head.roadPartNumber.toInt)
    }
  }

  def transferRoadAddress(sources: Seq[RoadAddressLink], targets: Seq[RoadAddressLink], user: User): Seq[RoadAddress] = {
    val mapping = DefloatMapper.createAddressMap(sources, targets)
    if (mapping.exists(DefloatMapper.invalidMapping)) {
      throw new InvalidAddressDataException("Mapping failed to map following items: " +
        mapping.filter(DefloatMapper.invalidMapping).map(
          r => s"${r.sourceLinkId}: ${r.sourceStartM}-${r.sourceEndM} -> ${r.targetLinkId}: ${r.targetStartM}-${r.targetEndM}").mkString(", ")
      )
    }
    val sourceRoadAddresses = withDynSession {
      RoadAddressDAO.fetchByLinkId(sources.map(_.linkId).toSet, includeFloating = true,
        includeHistory = false)
    }

    DefloatMapper.preTransferChecks(sourceRoadAddresses)
    val targetRoadAddresses = RoadAddressLinkBuilder.fuseRoadAddress(sourceRoadAddresses.flatMap(DefloatMapper.mapRoadAddresses(mapping)))
    DefloatMapper.postTransferChecks(targetRoadAddresses, sourceRoadAddresses)

    targetRoadAddresses
  }

  def recalculateRoadAddresses(roadNumber: Long, roadPartNumber: Long): Boolean = {
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
          changed.nonEmpty
        } catch {
          case ex: InvalidAddressDataException => logger.error(s"!!! Road $roadNumber, part $roadPartNumber contains invalid address data - part skipped !!!", ex)
        }
      } else {
        logger.info(s"Not recalculating $roadNumber / $roadPartNumber because floating segments were found")
      }
    } catch {
      case a: Exception => logger.error(a.getMessage, a)
    }
    false
  }
  def prettyPrint(changes: Seq[ChangeInfo]) = {
    def setPrecision(d: Double) = {
      BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
    }
    def concatenate(c: ChangeInfo, s: String): String = {
      val newS = s"""old id: ${c.oldId.getOrElse("MISS!")} new id: ${c.newId.getOrElse("MISS!")} old length: ${setPrecision(c.oldStartMeasure.getOrElse(0.0))}-${setPrecision(c.oldEndMeasure.getOrElse(0.0))} new length: ${setPrecision(c.newStartMeasure.getOrElse(0.0))}-${setPrecision(c.newEndMeasure.getOrElse(0.0))} mml id: ${c.mmlId} vvhTimeStamp ${c.vvhTimeStamp}
     """
      (s+ "\n" +newS)
    }

    val groupedChanges = SortedMap(changes.groupBy(_.changeType).toSeq:_*)
    groupedChanges.foreach{ group =>
      println(s"""changeType: ${group._1}""" + "\n" + group._2.foldLeft("")((stream, nextChange) => concatenate(nextChange, stream))+ "\n")
    }
  }
}


case class RoadAddressMerge(merged: Set[Long], created: Seq[RoadAddress])
case class ReservedRoadPart(id: Long, roadNumber: Long, roadPartNumber: Long, roadLength: Double, addressLength: Long,
                            discontinuity: Discontinuity, ely: Long = -1, startDate: Option[DateTime], endDate: Option[DateTime],
                            startingLinkId: Option[Long] = None, isDirty: Boolean = false) {
  def holds(baseRoadAddress: BaseRoadAddress): Boolean = {
    roadNumber == baseRoadAddress.roadNumber && roadPartNumber == baseRoadAddress.roadPartNumber
  }
}
case class RoadAddressResult(historyLinkAddresses: Map[Long, Seq[RoadAddressLink]], current: Map[Long, Seq[RoadAddress]],
                             floating: Map[Long, Seq[RoadAddress]])

case class BoundingBoxResult(changeInfoF: Future[Seq[ChangeInfo]], roadAddressResultF: Future[RoadAddressResult],
                             roadLinkF: Future[Seq[RoadLink]], complementaryF: Future[Seq[RoadLink]], suravageF: Future[Seq[VVHRoadlink]])
