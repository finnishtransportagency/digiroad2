package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{SideCode, State}
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, VKMClient}
import fi.liikennevirasto.digiroad2.dao.RoadAddressTempDAO
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import org.apache.http.impl.client.HttpClientBuilder
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

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
  }

  lazy val roadAddressService: RoadAddressService = {
    new RoadAddressService(viiteClient)
  }

  lazy val vkmClient: VKMClient = {
    new VKMClient()
  }

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }

  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, eventbus, new DummySerializer)
  }

  lazy val roadLinkTempDao: RoadAddressTempDAO = {
    new RoadAddressTempDAO
  }

  lazy val username: String = "batch_process_temp_road_address"

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def calculateSideCodeUsingEndPointAddrs(addrAtStart: RoadAddress, addrAtEnd: RoadAddress): SideCode = {
    if(addrAtStart.addrM < addrAtEnd.addrM) SideCode.TowardsDigitizing
    else if (addrAtStart.addrM > addrAtEnd.addrM) SideCode.AgainstDigitizing
    else SideCode.Unknown
  }

  // Compare temp address to digitizing direction start adjacent link
  def calculateTrackUsingFirstPoint(tempAddressToCalculate: RoadAddressTEMPwithPoint, viiteAddressesWithPoints: Seq[RoadAddressTEMPwithPoint], tempAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    val adjacentOrder = viiteAddressesWithPoints.filter { address =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.firstP, address.lastP)
    }

    val adjacentReverse = viiteAddressesWithPoints.filter { address =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.firstP, address.firstP)
    }

    val frozenAdjacents = tempAddresses.filter { frozen =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.firstP, frozen.lastP) || GeometryUtils.areAdjacent(tempAddressToCalculate.firstP, frozen.firstP)
    }
    val adjacents = adjacentOrder ++ adjacentReverse

    val tempAddressTrack = (adjacents.size, frozenAdjacents.size) match {
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
        Seq(tempAddressToCalculate.copy(roadAddress = tempAddressToCalculate.roadAddress.copy(track = track)))

      case _ =>
        logger.error("Could not calculate track for temp address on linkID: " + tempAddressToCalculate.roadAddress.linkId)
        Seq()
    }
  }

  // Compare temp address to digitizing direction end adjacent link
  def calculateTrackUsingLastPoint(tempAddressToCalculate: RoadAddressTEMPwithPoint, viiteAddressesWithPoints: Seq[RoadAddressTEMPwithPoint],
                                   tempAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    val adjacentOrder = viiteAddressesWithPoints.filter { address =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.lastP, address.firstP)
    }

    val adjacentReverse = viiteAddressesWithPoints.filter { address =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.lastP, address.lastP)
    }

    val frozenAdjacents = tempAddresses.filter { frozen =>
      GeometryUtils.areAdjacent(tempAddressToCalculate.lastP, frozen.firstP) || GeometryUtils.areAdjacent(tempAddressToCalculate.lastP, frozen.lastP)
    }

    val adjacents = adjacentOrder ++ adjacentReverse

    val tempAddressTrack = (adjacents.size, frozenAdjacents.size) match {
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
        Seq(tempAddressToCalculate.copy(roadAddress = tempAddressToCalculate.roadAddress.copy(track = track)))
      case _ => Seq()
    }
  }

  /**
    * THIS METHOD HAS BEEN USED ONLY FOR LOGGING PURPOSES
    * Recalculates temp road address for links' which end points have different road parts
    * The method tries to form a road address by fetching addresses from VKM for each two adjacent points in road link geometry
    * @param frozen road link missing road address info
    * @return Formed temp road addresses with points for given road link
    */
  def recalculateAddress(frozen: RoadLink): Seq[RoadAddressTEMPwithPoint] = {
    logger.info(s"Recalculate Address on linkId ${frozen.linkId}")
    frozen.geometry.zip(frozen.geometry.tail).foldLeft(Seq.empty[RoadAddressTEMPwithPoint]) { case (result, (p1, p2)) =>
      val roadNumber = Try(frozen.roadNumber.map(_.toInt).head).toOption
      val roadPartNumber = Try(frozen.roadPartNumber.map(_.toInt).head).toOption
      val address = vkmClient.coordsToAddresses(Seq(p1, p2), roadNumber, roadPartNumber, includePedestrian = Some(true))

      if (result.isEmpty) {
        val orderedAddress = address.sortBy(_.addrM)
        Seq(RoadAddressTEMPwithPoint(p1, p2, RoadAddressTEMP(frozen.linkId, orderedAddress.head.road, orderedAddress.head.roadPart, Track.Unknown,
          orderedAddress.head.addrM, orderedAddress.last.addrM, 0, GeometryUtils.geometryLength(Seq(p1, p2)), Seq(p1, p2), municipalityCode = Some(frozen.municipalityCode))))
      } else {
        val addressHead = address.head

        if (result.exists(x => x.roadAddress.road == addressHead.road && x.roadAddress.roadPart == addressHead.roadPart)) {
          val partOfEndValue = GeometryUtils.geometryLength(Seq(p1, p2))
          val resultToChange = result.filter(x => x.roadAddress.road == addressHead.road && x.roadAddress.roadPart == addressHead.roadPart).head
          result.filterNot(x => x.roadAddress.road == addressHead.road && x.roadAddress.roadPart == addressHead.roadPart) :+
            resultToChange.copy(roadAddress = resultToChange.roadAddress.copy(endAddressM = address.last.addrM, endMValue = partOfEndValue + resultToChange.roadAddress.endMValue, geom = Seq(p1, p2) ++ resultToChange.roadAddress.geom))
        } else {
          val startValue = result.maxBy(_.roadAddress.endMValue).roadAddress.endMValue
          result :+ RoadAddressTEMPwithPoint(p1, p2, RoadAddressTEMP(frozen.linkId, addressHead.road, addressHead.roadPart, Track.Unknown, addressHead.addrM, address.last.addrM,
            startValue, startValue + GeometryUtils.geometryLength(Seq(p1, p2)), Seq(p1, p2), municipalityCode = Some(frozen.municipalityCode)))
        }
      }
    }
  }

  /**
    * Try to create temp road address for links still missing road address info, by using adjacent
    * links road addresses and KGV road number and KGV road part number fields
    * @param roadLinksMissingAddress road links still missing address with adjacent road links
    * @param mappedAddresses Viite and temp road addresses
    * @return generated temp addresses
    */
  def cleanner(roadLinksMissingAddress: Seq[RoadLinkWithPointsAndAdjacents], mappedAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    logger.info(s"Try to solve ${roadLinksMissingAddress.size}")

    roadLinksMissingAddress.flatMap { adjRoadLink =>
      val (first, last, missing, roadLinks) = (adjRoadLink.roadLinkWithPoints.firstP, adjRoadLink.roadLinkWithPoints.lastP, adjRoadLink.roadLinkWithPoints.roadLink, adjRoadLink.adjacentLinks)

      val adjacentFirstLast = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(missing.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(first, address.lastP))
      }

      val adjacentFirstFirst = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(missing.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(first, address.firstP))
      }

      val adjacentLastLast = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(missing.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(last, address.lastP))
      }

      val adjacentLastFirst = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(missing.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(last, address.firstP))
      }

      //TODO Varmaan sideCodePäättely myös tähänd
      // Tapaus "U", jossa digitointisuunta muuttunut viereisellä linkillä ei toimi, kummatkin viereiset tulis firstFirst kohtaan
      val frozenAddress =
        if ((adjacentFirstLast ++ adjacentFirstFirst).nonEmpty && (adjacentLastLast ++ adjacentLastFirst).nonEmpty) {

          val startAddressM = (adjacentFirstLast.nonEmpty, adjacentFirstFirst.nonEmpty) match {
            case (true, false) => adjacentFirstLast.head.roadAddress.endAddressM
            case _ => adjacentFirstFirst.head.roadAddress.startAddressM
          }

          val endAddressM = (adjacentLastFirst.nonEmpty, adjacentLastLast.nonEmpty) match {
            case (true, false) => adjacentLastFirst.head.roadAddress.startAddressM
            case _ => adjacentLastLast.head.roadAddress.endAddressM
          }

          val address = (adjacentFirstLast ++ adjacentFirstFirst).head.roadAddress
          Some(RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(missing.linkId, address.road, address.roadPart, Track.Unknown,
            startAddressM, endAddressM, 0, GeometryUtils.geometryLength(missing.geometry), missing.geometry, None, municipalityCode = address.municipalityCode)))
        } else
          None

      recalculateTrack(mappedAddresses, frozenAddress.toSeq, Seq())

    }
  }

  /**
    * Viite uses frozen road links from November 2022, use VKM API to determine road addresses for up-to-date road links
    *
    * @param municipality municipality where we want to generate temp road addresses for state road links
    * @return Generated temp road addresses with start and end points, and state road links still missing road address
    */
  def resolveAddressesOnOverlappingGeometry(municipality: Int): (Seq[RoadAddressTEMPwithPoint], Seq[RoadLink]) = {
    logger.info(s"Working on municipality : $municipality")

    val existingTempRoadAddress = roadLinkTempDao.getByMunicipality(municipality)
    val roadLinksInMunicipality = roadLinkService.getRoadLinksByMunicipality(municipality, newTransaction = false).filter(_.administrativeClass == State)

    // Delete old saved Temp addresses on RoadLinks that are not valid anymore
    val tempAddressLinkIdsToDelete = existingTempRoadAddress.map(_.linkId).diff(roadLinksInMunicipality.map(_.linkId))
    if (tempAddressLinkIdsToDelete.nonEmpty) {
      logger.info("Deleting temp road address info on old link ids: " + tempAddressLinkIdsToDelete.mkString(", "))
      roadLinkTempDao.deleteInfoByLinkIds(tempAddressLinkIdsToDelete.toSet)
    }

    val allViiteRoadAddresses = roadAddressService.groupRoadAddress(roadAddressService.getAllByLinkIds(roadLinksInMunicipality.map(_.linkId)))
    val stateRoadLinksMissingAddress = roadLinksInMunicipality.filterNot(roadLink => {
      val linkIdsWithTempAddress = existingTempRoadAddress.map(_.linkId)
      val linkIdsWithViiteRoadAddress = allViiteRoadAddresses.map(_.linkId)
      linkIdsWithViiteRoadAddress.contains(roadLink.linkId) || linkIdsWithTempAddress.contains(roadLink.linkId)
    })

    val groupedRoadLinksMissingAddress = stateRoadLinksMissingAddress.groupBy(_.roadNameIdentifier.getOrElse(""))
    val groupedRoadLinks = roadLinksInMunicipality.groupBy(_.roadNameIdentifier.getOrElse(""))

    val newAddress = groupedRoadLinksMissingAddress.keys.flatMap { key =>
      val relevantLinkIds = groupedRoadLinks.getOrElse(key, Seq())

      val viiteAddressesWithPoints = allViiteRoadAddresses.flatMap { address =>
        relevantLinkIds.find(x => x.linkId == address.linkId).map { roadLink =>
          val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
          val roadAddressTemp = RoadAddressTEMP(address.linkId, address.roadNumber,
            address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue, address.startMValue, address.endMValue, roadLink.geometry, Some(address.sideCode))

          RoadAddressTEMPwithPoint(first, last, roadAddressTemp)
        }
      }

      val tempAddresses = groupedRoadLinksMissingAddress(key).flatMap { roadLinkMissingAddress =>
        val (first, last) = GeometryUtils.geometryEndpoints(roadLinkMissingAddress.geometry)

        try {
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
              val tempRoadAddressSideCode = calculateSideCodeUsingEndPointAddrs(vkmAddressAtRoadLinkStart.get, vkmAddressAtRoadLinkEnd.get)
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

      recalculateTrack(viiteAddressesWithPoints, tempAddresses, Seq())
    }.toSeq

    (newAddress, stateRoadLinksMissingAddress.filterNot(frozen => newAddress.map(_.roadAddress.linkId).contains(frozen.linkId)))
  }

  def recalculateTrack(viiteAddressesWithPoints: Seq[RoadAddressTEMPwithPoint], tempAddresses: Seq[RoadAddressTEMPwithPoint],
                       result: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    val newResult = calculateTrack(viiteAddressesWithPoints, tempAddresses).filterNot(x => x.roadAddress.track == Track.Unknown)

    if (newResult.isEmpty) {
      result
    } else {
      val newResultLinkIds = newResult.map(_.roadAddress.linkId)
      recalculateTrack(newResult ++ viiteAddressesWithPoints, tempAddresses.filterNot(x => newResultLinkIds.contains(x.roadAddress.linkId)), result ++ newResult)
    }
  }

  // Try to calculate temp road address Track using adjacent road address info
  def calculateTrack(viiteAddressesWithPoints: Seq[RoadAddressTEMPwithPoint], tempAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    tempAddresses.flatMap { tempAddressToCalculate =>
      val missingFrozen = tempAddresses.filterNot(x => x.roadAddress.linkId == tempAddressToCalculate.roadAddress.linkId)

      val frozenAddrFirst = calculateTrackUsingFirstPoint(tempAddressToCalculate, viiteAddressesWithPoints, missingFrozen)

      if (frozenAddrFirst.nonEmpty) {
        frozenAddrFirst
      } else
        calculateTrackUsingLastPoint(tempAddressToCalculate, viiteAddressesWithPoints, tempAddresses)
    }
  }

  def process(): Unit = {

    //Get All Municipalities
    val municipalities: Seq[Int] = Seq(508)
//      PostGISDatabase.withDynSession {
//      Queries.getMunicipalities
//    }

    municipalities.foreach { municipality =>
      PostGISDatabase.withDynTransaction {
        val (overlappingToCreate, missing) = resolveAddressesOnOverlappingGeometry(municipality)

        val adjacentsToCreate = resolveAddressesUsingAdjacentAddresses(missing, overlappingToCreate)

        logger.info(
          s"""
             |Resolved ${overlappingToCreate.size} addresses on overlapping geometry
             |Resolved ${adjacentsToCreate.size} addreses using adjacent addresses
             |Total: ${(overlappingToCreate ++ adjacentsToCreate).size}
             |State road links still missing address: ${missing.size - adjacentsToCreate.size}
             |Municipality: $municipality
             |""".stripMargin)
        (adjacentsToCreate ++ overlappingToCreate.map(_.roadAddress)).foreach { frozen =>
          roadLinkTempDao.insertInfo(frozen, username)
        }
      }
    }

  }

  //TODO PÄÄTTELEE TEMP ROAD ADDRESS INFON LINKEILLE, JOLTA SE PUUTTUU HYÖDYNTÄEN VIEREISTEN LINKKIEN VIITE JA TEMP TIEOSOITE TIETOJA
  // Pitää nimenomaan hyödyntää myös luotuja temp tietoja, jotta voidaan ratkoa tilanteita, joissa VKM ei pystynyt auttamaan
  /**
    * @param missing  State road links still missing road address info
    * @param toCreate Temp road addresses to be created
    * @return Final result temp road addresses to be created
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

    cleanning(roadLinksMissingAddressWithAdjacents, mappedAddresses ++ toCreate, Seq())

  }

  def cleanning(missingRoadLinks: Seq[RoadLinkWithPointsAndAdjacents], tempRoadAddresses: Seq[RoadAddressTEMPwithPoint], result: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMP] = {
    val newResult = cleanner(missingRoadLinks, tempRoadAddresses)

    if (newResult.nonEmpty) {
      val roadLinksStillMissingAddress = missingRoadLinks.filterNot(roadLinkMissing =>
        newResult.map(_.roadAddress.linkId).contains(roadLinkMissing.roadLinkWithPoints.roadLink.linkId))
      cleanning(roadLinksStillMissingAddress, tempRoadAddresses ++ newResult, result ++ newResult)
    } else
      result.map(_.roadAddress)

  }
}