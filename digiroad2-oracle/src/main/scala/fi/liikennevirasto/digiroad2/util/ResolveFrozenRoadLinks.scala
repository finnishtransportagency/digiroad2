package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{SideCode, State}
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, VKMClient}
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadAddressTempDAO}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

case class RoadAddressTEMP(linkId: String, road: Long, roadPart: Long, track: Track, startAddressM: Long, endAddressM: Long,
                           startMValue: Double, endMValue: Double, geom: Seq[Point] = Seq(), sideCode: Option[SideCode] = None,
                           municipalityCode: Option[Int] = None, createdDate: Option[String] = None)

case class RoadAddressTEMPwithPoint(firstP: Point, lastP: Point, roadAddress: RoadAddressTEMP)

case class RoadLinkWithPointsAndAdjacents(roadLinkWithPoints: RoadLinkWithPoints, adjacentLinks: Seq[RoadLink])

case class RoadLinkWithPoints(firstP: Point, lastP: Point, roadLink: RoadLink)

object ResolvingFrozenRoadLinks extends ResolvingFrozenRoadLinks
trait ResolvingFrozenRoadLinks {

  lazy val roadAddressService: RoadAddressService = new RoadAddressService()

  lazy val vkmClient: VKMClient = new VKMClient()

  lazy val eventbus: DigiroadEventBus = new DigiroadEventBus

  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient()

  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventbus)

  lazy val roadLinkTempDao: RoadAddressTempDAO = new RoadAddressTempDAO

  lazy val username: String = "batch_process_temp_road_address"

  //Timestamp for the instant when Viite road links were frozen
  // 20.05.2023 16:29:59
  lazy val viiteTimestamp: Long = 1684589399000L
  
  val logger: Logger = LoggerFactory.getLogger(getClass)

  def calculateSideCodeUsingEndPointAddrs(addrMAtStart: Int, addrMAtEnd: Int): SideCode = {
    if(addrMAtStart < addrMAtEnd) SideCode.TowardsDigitizing
    else if (addrMAtStart > addrMAtEnd) SideCode.AgainstDigitizing
    else SideCode.Unknown
  }

  // Compare temp address to digitizing direction start adjacent link
  def calculateTrackUsingFirstPoint(tempAddressToCalculate: RoadAddressTEMPwithPoint, resolvedAddresses: Seq[RoadAddressTEMPwithPoint], otherTempAddressesUnresolved: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    val adjacentOrder = resolvedAddresses.filter { address =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.firstP, address.lastP)
    }

    val adjacentReverse = resolvedAddresses.filter { address =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.firstP, address.firstP)
    }

    val unresolvedAdjacents = otherTempAddressesUnresolved.filter { frozen =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.firstP, frozen.lastP) || GeometryUtils.areAdjacent(tempAddressToCalculate.firstP, frozen.firstP)
    }
    val adjacents = adjacentOrder ++ adjacentReverse

    val tempAddressTrack = (adjacents.size, unresolvedAdjacents.size) match {
      case (2, 0) =>
          if (adjacents.exists(_.roadAddress.track == Track.RightSide) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
            Some(Track.Combined)
          else if (adjacents.exists(_.roadAddress.track == Track.Combined) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
            Some(Track.RightSide)
          else
            Some(Track.LeftSide)
      case (1, 0) =>
        Some(adjacents.head.roadAddress.track)
      case _ =>
        None
    }

    tempAddressTrack match {
      case Some(track) =>
        logger.info(s"""calculated track value: ${track.value} using first point on linkID: ${tempAddressToCalculate.roadAddress.linkId}""")
        Seq(tempAddressToCalculate.copy(roadAddress = tempAddressToCalculate.roadAddress.copy(track = track)))
      case _ =>
        logger.info(s"""could not calculate track using first point on linkID: ${tempAddressToCalculate.roadAddress.linkId}""")
        Seq()
    }
  }

  // Compare temp address to digitizing direction end adjacent link
  def calculateTrackUsingLastPoint(tempAddressToCalculate: RoadAddressTEMPwithPoint, resolvedAddresses: Seq[RoadAddressTEMPwithPoint],
                                   otherTempAddressesUnresolved: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    val adjacentOrder = resolvedAddresses.filter { address =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.lastP, address.firstP)
    }

    val adjacentReverse = resolvedAddresses.filter { address =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.lastP, address.lastP)
    }

    val unresolvedAdjacents = otherTempAddressesUnresolved.filter { frozen =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.lastP, frozen.firstP) || GeometryUtils.areAdjacent(tempAddressToCalculate.lastP, frozen.lastP)
    }

    val adjacents = adjacentOrder ++ adjacentReverse

    val tempAddressTrack = (adjacents.size, unresolvedAdjacents.size) match {
      case (2, 1) =>
        if (adjacents.exists(_.roadAddress.track == Track.RightSide) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
          Some(Track.Combined)
        else if (adjacents.exists(_.roadAddress.track == Track.Combined) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
          Some(Track.RightSide)
        else
          Some(Track.LeftSide)
      case (1, 1) =>
        Some(adjacents.head.roadAddress.track)
      case _ =>
        None
    }

    tempAddressTrack match {
      case Some(track) =>
        logger.info(s"""calculated track value: ${track.value} using last point on linkID: ${tempAddressToCalculate.roadAddress.linkId}""")
        Seq(tempAddressToCalculate.copy(roadAddress = tempAddressToCalculate.roadAddress.copy(track = track)))
      case _ =>
        logger.info(s"""could not calculate track using last point on linkID: ${tempAddressToCalculate.roadAddress.linkId}""")
        Seq()
    }
  }

  /**
    * Try to create temp road address for links still missing road address info, by using adjacent
    * links road addresses and KGV road number and KGV road part number fields
    * @param roadLinksMissingAddress road links still missing address with adjacent road links
    * @param mappedAddresses Viite and temp road addresses
    * @return generated temp addresses
    */
  def resolveUsingAdjacents(roadLinksMissingAddress: Seq[RoadLinkWithPointsAndAdjacents], mappedAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    logger.info(s"Try to solve ${roadLinksMissingAddress.size}")

    roadLinksMissingAddress.flatMap { roadLink =>
      val (firstPoint, lastPoint, roadLinkToResolve, adjacentRoadLinks) = (roadLink.roadLinkWithPoints.firstP, roadLink.roadLinkWithPoints.lastP, roadLink.roadLinkWithPoints.roadLink, roadLink.adjacentLinks)

      val adjacentFirstLast = mappedAddresses.filter { address =>
        Try(roadLinkToResolve.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(roadLinkToResolve.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(firstPoint, address.lastP))
      }

      val adjacentFirstFirst = mappedAddresses.filter { address =>
        Try(roadLinkToResolve.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(roadLinkToResolve.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(firstPoint, address.firstP))
      }

      val adjacentLastLast = mappedAddresses.filter { address =>
        Try(roadLinkToResolve.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(roadLinkToResolve.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(lastPoint, address.lastP))
      }

      val adjacentLastFirst = mappedAddresses.filter { address =>
        Try(roadLinkToResolve.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(roadLinkToResolve.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(lastPoint, address.firstP))
      }

      val resolvedAddresses =
        if ((adjacentFirstLast ++ adjacentFirstFirst).nonEmpty && (adjacentLastLast ++ adjacentLastFirst).nonEmpty) {

          val addrMAtFirstPoint = (adjacentFirstLast.nonEmpty, adjacentFirstFirst.nonEmpty) match {
            case (true, false) => adjacentFirstLast.head.roadAddress.endAddressM.toInt
            case _ => adjacentFirstFirst.head.roadAddress.startAddressM.toInt
          }
          val addrMAtLastPoint = (adjacentLastFirst.nonEmpty, adjacentLastLast.nonEmpty) match {
            case (true, false) => adjacentLastFirst.head.roadAddress.startAddressM.toInt
            case _ => adjacentLastLast.head.roadAddress.endAddressM.toInt
          }
          val sideCode = calculateSideCodeUsingEndPointAddrs(addrMAtFirstPoint, addrMAtLastPoint)
          val address = (adjacentFirstLast ++ adjacentFirstFirst).head.roadAddress
          Some(RoadAddressTEMPwithPoint(firstPoint, lastPoint, RoadAddressTEMP(roadLinkToResolve.linkId, address.road, address.roadPart, Track.Unknown,
            addrMAtFirstPoint, addrMAtLastPoint, 0, GeometryUtils.geometryLength(roadLinkToResolve.geometry), roadLinkToResolve.geometry, Some(sideCode), municipalityCode = address.municipalityCode)))
        } else
          None

      recalculateTracksRecursively(mappedAddresses, resolvedAddresses.toSeq, Seq())
    }
  }

  /**
    * Viite uses frozen road links from November 2022, use VKM API to determine road addresses for up-to-date road links
    *
    * @param municipality municipality where we want to generate temp road addresses for state road links
    * @return Generated temp road addresses with start and end points, and state road links still missing road address
    */
  def resolveAddressesOnOverlappingGeometry(municipality: Int): (Seq[RoadAddressTEMPwithPoint], Seq[RoadLink]) = {

    val existingTempRoadAddress = roadLinkTempDao.getByMunicipality(municipality)
    val roadLinksInMunicipality = roadLinkService.getRoadLinksByMunicipality(municipality, newTransaction = false).filter(_.administrativeClass == State)

    // Delete old saved Temp addresses on RoadLinks that are not valid anymore
    val tempAddressLinkIdsToDelete = existingTempRoadAddress.map(_.linkId).diff(roadLinksInMunicipality.map(_.linkId))
    if (tempAddressLinkIdsToDelete.nonEmpty) {
      logger.info("Deleting temp road address info on old link ids: " + tempAddressLinkIdsToDelete.mkString(", "))
      roadLinkTempDao.deleteInfoByLinkIds(tempAddressLinkIdsToDelete.toSet)
    }

    // Some state road links are missing road address in Viite
    val allViiteRoadAddresses = roadAddressService.groupRoadAddress(roadAddressService.getAllByLinkIds(roadLinksInMunicipality.map(_.linkId)))
    val stateRoadLinksMissingAddress = roadLinksInMunicipality.filter(roadLink => {
      val linkIdsWithTempAddress = existingTempRoadAddress.map(_.linkId)
      val linkIdsWithViiteRoadAddress = allViiteRoadAddresses.map(_.linkId)
      val hasAddressInfo = linkIdsWithViiteRoadAddress.contains(roadLink.linkId) || linkIdsWithTempAddress.contains(roadLink.linkId)

      !hasAddressInfo && roadLink.timeStamp > viiteTimestamp
    })

    val groupedRoadLinksMissingAddress = stateRoadLinksMissingAddress.groupBy(_.roadNameIdentifier.getOrElse(""))
    val groupedRoadLinks = roadLinksInMunicipality.groupBy(_.roadNameIdentifier.getOrElse(""))

    val resolvedAddresses = groupedRoadLinksMissingAddress.keys.flatMap { key =>
      val relevantLinkIds = groupedRoadLinks.getOrElse(key, Seq())

      val viiteAddressesWithPoints = allViiteRoadAddresses.flatMap { address =>
        relevantLinkIds.find(x => x.linkId == address.linkId).map { roadLink =>
          val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
          val roadAddressTemp = RoadAddressTEMP(address.linkId, address.roadNumber,
            address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue, address.startMValue, address.endMValue, roadLink.geometry, Some(address.sideCode))

          RoadAddressTEMPwithPoint(first, last, roadAddressTemp)
        }
      }

      val tempAddressesToCreate = groupedRoadLinksMissingAddress(key).flatMap { roadLinkMissingAddress =>
        val (first, last) = GeometryUtils.geometryEndpoints(roadLinkMissingAddress.geometry)

        try {
          // KGV road address attributes are not always up-to-date, stop
          // using them if problems arise, but for now they seem to be OK
          val roadNumber = Try(roadLinkMissingAddress.roadNumber.map(_.toInt).head).toOption
          val roadPartNumber = Try(roadLinkMissingAddress.roadPartNumber.map(_.toInt).head).toOption

          // Fetch Road Address info for road link end points from VKM
          val vkmAddressAtRoadLinkStart = try {
            Some(vkmClient.coordToAddress(first, roadNumber, roadPartNumber, includePedestrian = Some(true)))
          } catch {
            case roadAddressException: RoadAddressException =>
              logger.error(roadAddressException.getMessage)
              None
          }
          val vkmAddressAtRoadLinkEnd = try {
            Some(vkmClient.coordToAddress(last, roadNumber, roadPartNumber, includePedestrian = Some(true)))
          } catch {
            case roadAddressException: RoadAddressException =>
              logger.error(roadAddressException.getMessage)
              None
          }

          val vkmAddresses = Seq(vkmAddressAtRoadLinkStart, vkmAddressAtRoadLinkEnd).flatten
          if (vkmAddresses.isEmpty || (vkmAddresses.nonEmpty && vkmAddresses.size != 2)) {
            logger.error("VKM did not return address for both end points of linkID: " + roadLinkMissingAddress.linkId)
            Seq()
          } else {
            val groupedVkmAddresses = vkmAddresses.groupBy(addr => (addr.road, addr.roadPart))
            // Returned addresses must be on the same road part, in order to create temp road address for road link
            if (groupedVkmAddresses.keys.size > 1) {
              logger.error("Returned VKM addresses for linkID: " + roadLinkMissingAddress.linkId + " are not on the same road part")
              None
            } else {
              val orderedAddress = vkmAddresses.sortBy(_.addrM)
              // If start and end point road address Track values are not equal, Track needs to be calculated later
              val track = if (orderedAddress.head.track == orderedAddress.last.track) orderedAddress.head.track else Track.Unknown
              val tempRoadAddressSideCode = calculateSideCodeUsingEndPointAddrs(vkmAddressAtRoadLinkStart.get.addrM, vkmAddressAtRoadLinkEnd.get.addrM)
              Some(RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(roadLinkMissingAddress.linkId, orderedAddress.head.road,
                orderedAddress.head.roadPart, track, orderedAddress.head.addrM, orderedAddress.last.addrM,
                0, GeometryUtils.geometryLength(roadLinkMissingAddress.geometry), roadLinkMissingAddress.geometry, Some(tempRoadAddressSideCode), municipalityCode = Some(roadLinkMissingAddress.municipalityCode))))
            }
          }
        } catch {
          case ex: Exception =>
            logger.error(s"Exception in VKM for linkId ${roadLinkMissingAddress.linkId} exception $ex")
            None
        }
      }

      recalculateTracksRecursively(viiteAddressesWithPoints, tempAddressesToCreate, Seq())
    }.toSeq

    val roadLinksAddressUnresolved = stateRoadLinksMissingAddress.filterNot(missing => resolvedAddresses.map(_.roadAddress.linkId).contains(missing.linkId))
    (resolvedAddresses, roadLinksAddressUnresolved)
  }

  def recalculateTracksRecursively(resolvedAddresses: Seq[RoadAddressTEMPwithPoint], tempAddressesUnresolved: Seq[RoadAddressTEMPwithPoint],
                                   result: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    val newResult = calculateTrack(resolvedAddresses, tempAddressesUnresolved).filterNot(x => x.roadAddress.track == Track.Unknown)

    if (newResult.isEmpty) {
      result
    } else {
      val newResultLinkIds = newResult.map(_.roadAddress.linkId)
      recalculateTracksRecursively(newResult ++ resolvedAddresses, tempAddressesUnresolved.filterNot(x => newResultLinkIds.contains(x.roadAddress.linkId)), result ++ newResult)
    }
  }

  // Try to calculate temp road address Track using adjacent road address info
  def calculateTrack(resolvedAddresses: Seq[RoadAddressTEMPwithPoint], tempAddressesUnresolved: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    tempAddressesUnresolved.flatMap { tempAddressToCalculate =>
      val otherTempAddressesUnresolved = tempAddressesUnresolved.filterNot(x => x.roadAddress.linkId == tempAddressToCalculate.roadAddress.linkId)
      val resolvedAddressesOnSameRoadPart = resolvedAddresses.filter(resolvedAddress => resolvedAddress.roadAddress.road == tempAddressToCalculate.roadAddress.road &&
        resolvedAddress.roadAddress.roadPart == tempAddressToCalculate.roadAddress.roadPart)

      val frozenAddrFirst = calculateTrackUsingFirstPoint(tempAddressToCalculate, resolvedAddressesOnSameRoadPart, otherTempAddressesUnresolved)

      if (frozenAddrFirst.nonEmpty) {
        frozenAddrFirst
      } else
        calculateTrackUsingLastPoint(tempAddressToCalculate, resolvedAddressesOnSameRoadPart, tempAddressesUnresolved)
    }
  }

  def process(): Unit = {

    //Get All Municipalities
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach { municipality =>
      PostGISDatabase.withDynTransaction {
        logger.info(s"Resolving addresses on new road links in municipality: $municipality")
        val (overlappingToCreate, missing) = resolveAddressesOnOverlappingGeometry(municipality)
        val adjacentsToCreate = resolveAddressesUsingAdjacentAddresses(missing, overlappingToCreate)

        logger.info(
          s"""
             |Resolved ${overlappingToCreate.size} addresses on overlapping geometry
             |Resolved ${adjacentsToCreate.size} addreses using adjacent addresses on linkIds:
             | ${adjacentsToCreate.map(_.linkId).mkString(", ")}
             |Total: ${(overlappingToCreate ++ adjacentsToCreate).size}
             |New state road links without road address info: ${missing.size - adjacentsToCreate.size}
             |Municipality: $municipality
             |""".stripMargin)

        (adjacentsToCreate ++ overlappingToCreate.map(_.roadAddress)).foreach { resolvedAddress =>
          roadLinkTempDao.insertInfo(resolvedAddress, username)
        }
      }
    }

  }

  //TODO This method has not been tested properly due to insufficient test data,
  // could not find municipalities where addresses could be resolved using adjacent road address info
  // monitor this methods behaviour trough logs
  /**
    * Use possible adjacent Viite road addresses and resolved temp road addresses to resolve road addresses on links
    * where VKM could not resolve them
    * @param missing  State road links still missing road address info
    * @param toCreate Resolved temp road addresses to be created
    * @return Road addresses resolved using adjacent road link addresses
    */
  def resolveAddressesUsingAdjacentAddresses(missing: Seq[RoadLink], toCreate: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMP] = {
    val roadLinksMissingAddressWithAdjacents = missing.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val adjacentRoadLinks = roadLinkService.getAdjacent(roadLink.linkId, newTransaction = false)
      val rlWithEndPoints = RoadLinkWithPoints(first, last, roadLink)
      RoadLinkWithPointsAndAdjacents(rlWithEndPoints, adjacentRoadLinks)
    }

    val linkIds = roadLinksMissingAddressWithAdjacents.flatMap(_.adjacentLinks.map(_.linkId))

    val adjacentRoadAddresses = if (linkIds.nonEmpty) roadAddressService.getAllByLinkIds(linkIds)
    else Seq()

    val mappedAddresses = adjacentRoadAddresses.flatMap { address =>
      val matchingAdjacentRoadLink = roadLinksMissingAddressWithAdjacents.flatMap(_.adjacentLinks).find(_.linkId == address.linkId)
      matchingAdjacentRoadLink.map { roadLink =>
        val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
        RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(address.linkId, address.roadNumber,
          address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue,
          address.startMValue, address.endMValue, roadLink.geometry, Some(address.sideCode), Some(roadLink.municipalityCode)))
      }
    }

    resolveAddressesRecursively(roadLinksMissingAddressWithAdjacents, mappedAddresses ++ toCreate, Seq())
  }

  /**
    * Resolve temp addresses on new links where VKM could not provide addresses.
    * Tries to resolve addresses recursively using already resolved temp addresses and Viite addresses
    * from adjacent road links until no new addresses are resolved
    * @param missingRoadLinks Road links still missing address info
    * @param allAddresses Viite and Temp addresses to be used in resolving process
    * @param result Resulting resolved temp road addresses from past executions
    * @return Resulting resolved temp road addresses
    */
  def resolveAddressesRecursively(missingRoadLinks: Seq[RoadLinkWithPointsAndAdjacents], allAddresses: Seq[RoadAddressTEMPwithPoint], result: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMP] = {
    val resolvedAddresses = resolveUsingAdjacents(missingRoadLinks, allAddresses)

    if (resolvedAddresses.nonEmpty) {
      val roadLinksStillMissingAddress = missingRoadLinks.filterNot(roadLinkMissing =>
        resolvedAddresses.map(_.roadAddress.linkId).contains(roadLinkMissing.roadLinkWithPoints.roadLink.linkId))
      resolveAddressesRecursively(roadLinksStillMissingAddress, allAddresses ++ resolvedAddresses, result ++ resolvedAddresses)
    } else
      result.map(_.roadAddress)

  }
}