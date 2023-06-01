package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{SideCode, State}
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, VKMClient}
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadAddressTEMP, RoadLinkTempDAO}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2._
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

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

  lazy val roadLinkTempDao: RoadLinkTempDAO = {
    new RoadLinkTempDAO
  }

  lazy val username: String = "batch_process_temp_road_address"

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def getSideCode(adjacentOrder: Option[RoadAddressTEMPwithPoint], adjacentReverse: Option[RoadAddressTEMPwithPoint]): Option[SideCode] = {
    if (adjacentOrder.nonEmpty && adjacentOrder.head.roadAddress.sideCode.nonEmpty) {
      adjacentOrder.head.roadAddress.sideCode
    } else if (adjacentReverse.nonEmpty && adjacentReverse.head.roadAddress.sideCode.nonEmpty) {
      Some(SideCode.switch(adjacentReverse.head.roadAddress.sideCode.head))
    } else
      None
  }

  def getTrackAndSideCodeFirst(frozenAddress: RoadAddressTEMPwithPoint, mappedAddresses: Seq[RoadAddressTEMPwithPoint], frozenAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    val adjacentOrder = mappedAddresses.filter { address =>
      GeometryUtils.areAdjacent(frozenAddress.firstP, address.lastP)
    }

    val adjacentReverse = mappedAddresses.filter { address =>
      GeometryUtils.areAdjacent(frozenAddress.firstP, address.firstP)
    }

    val frozenAdjacents = frozenAddresses.filter { frozen =>
      GeometryUtils.areAdjacent(frozenAddress.firstP, frozen.lastP) || GeometryUtils.areAdjacent(frozenAddress.firstP, frozen.firstP)
    }
    val adjacents = adjacentOrder ++ adjacentReverse

    val trackAndSideInfo = (adjacents.size, frozenAdjacents.size) match {
      case (2, 0) =>
        val track: Option[Track] =
          if (adjacents.exists(_.roadAddress.track == Track.RightSide) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
            Some(Track.Combined)
          else if (adjacents.exists(_.roadAddress.track == Track.Combined) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
            Some(Track.RightSide)
          else
            Some(Track.LeftSide)

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))

      case (1, 0) =>
        val track = Some(adjacents.head.roadAddress.track)

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))

      case _ =>
        (None, None)
    }

    trackAndSideInfo match {
      case (Some(track), Some(sideCode)) =>
        Seq(frozenAddress.copy(roadAddress = frozenAddress.roadAddress.copy(track = track, sideCode = Some(sideCode))))

      case (_, Some(sideCode)) =>
        Seq(frozenAddress.copy(roadAddress = frozenAddress.roadAddress.copy(sideCode = Some(sideCode))))

      case _ => Seq()
    }
  }

  def getTrackAndSideCodeLast(frozenAddress: RoadAddressTEMPwithPoint, mappedAddresses: Seq[RoadAddressTEMPwithPoint],
                              frozenAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    val adjacentOrder = mappedAddresses.filter { address =>
      GeometryUtils.areAdjacent(frozenAddress.lastP, address.firstP)
    }

    val adjacentReverse = mappedAddresses.filter { address =>
      GeometryUtils.areAdjacent(frozenAddress.lastP, address.lastP)
    }

    val frozenAdjacents = frozenAddresses.filter { frozen =>
      GeometryUtils.areAdjacent(frozenAddress.lastP, frozen.firstP) || GeometryUtils.areAdjacent(frozenAddress.lastP, frozen.lastP)
    }

    val adjacents = adjacentOrder ++ adjacentReverse

    val trackAndSideInfo = (adjacents.size, frozenAdjacents.size) match {
      case (2, 1) =>
        val track = if (adjacents.exists(_.roadAddress.track == Track.RightSide) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
          Some(Track.Combined)
        else if (adjacents.exists(_.roadAddress.track == Track.Combined) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
          Some(Track.RightSide)
        else
          Some(Track.LeftSide)

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))

      case (1, 1) =>
        val track = Some(adjacents.head.roadAddress.track)

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))
      case _ =>
        (None, None)
    }

    trackAndSideInfo match {
      case (Some(track), Some(sideCode)) =>
        Seq(frozenAddress.copy(roadAddress = frozenAddress.roadAddress.copy(track = track, sideCode = Some(sideCode))))

      case (_, Some(sideCode)) =>
        Seq(frozenAddress.copy(roadAddress = frozenAddress.roadAddress.copy(sideCode = Some(sideCode))))

      case _ => Seq()
    }
  }

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

  def cleanner(roadLinksMissingAddress: Seq[RoadLinkWithPointsAndAdjacents], mappedAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    logger.info(s"Try to solve ${roadLinksMissingAddress.size}")

    roadLinksMissingAddress.flatMap { adjRoadLink =>
      val ((first, last, missing), roadLinks) = ((adjRoadLink.roadLinkWithPoints.firstP, adjRoadLink.roadLinkWithPoints.lastP, adjRoadLink.roadLinkWithPoints.roadLink), adjRoadLink.adjacentLinks)

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

      recalculateTrackAndSideCode(mappedAddresses, frozenAddress.toSeq, Seq())

    }
  }

  /**
    * Viite uses frozen road links from November 2022, use VKM API to determine road addresses for up-to-date road links
    *
    * @param municipality municipality where we want to generate temp road addresses for state road links
    * @return Generated temp road addresses with start and end points, and state road links still missing road address
    */
  def processing(municipality: Int): (Seq[RoadAddressTEMPwithPoint], Seq[RoadLink]) = {
    logger.info(s"Working on municipality : $municipality")

    val existingTempRoadAddress = roadLinkTempDao.getByMunicipality(municipality)

    val roadLinksInMunicipality = roadLinkService.getRoadLinksByMunicipality(municipality, newTransaction = false).filter(_.administrativeClass == State)

    //    val allViiteRoadAddresses = integrationViiteClient.fetchAllByMunicipalityAndDate(municipality)
    //    val stateRoadLinksMissingAddress = roadLinks.filterNot(road => allViiteRoadAddresses.map(_.linkId).contains(road.linkId))

    val allViiteRoadAddresses = roadAddressService.groupRoadAddress(roadAddressService.getAllByLinkIds(roadLinksInMunicipality.map(_.linkId)))
    val stateRoadLinksMissingAddress = roadLinksInMunicipality.filterNot(roadLink => {
      val linkIdsWithTempAddress = existingTempRoadAddress.map(_.linkId)
      val linkIdsWithViiteRoadAddress = allViiteRoadAddresses.map(_.linkId)
      linkIdsWithViiteRoadAddress.contains(roadLink.linkId) || linkIdsWithTempAddress.contains(roadLink.linkId)
    })


    val frozenRoadLinks = stateRoadLinksMissingAddress.filter { road =>
      val matchedTempAddress = existingTempRoadAddress.find(_.linkId == road.linkId)
      val roadLinkEditedDate = road.attributes.getOrElse("LAST_EDITED_DATE", road.attributes.getOrElse("CREATED_DATE", BigInt(0))).asInstanceOf[BigInt].longValue()

      val formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")

      matchedTempAddress.isEmpty || (roadLinkEditedDate > DateTime.parse(matchedTempAddress.get.createdDate.head, formatter).getMillis)
    }

    val tempAddressLinkIdsToDelete = existingTempRoadAddress.map(_.linkId).diff(stateRoadLinksMissingAddress.map(_.linkId)) ++ frozenRoadLinks.map(_.linkId)
    if (tempAddressLinkIdsToDelete.nonEmpty)
      roadLinkTempDao.deleteInfoByLinkIds(tempAddressLinkIdsToDelete.toSet)

    val groupedFrozenRoadLinks = frozenRoadLinks.groupBy(_.roadNameIdentifier.getOrElse(""))
    val groupedRoadLinks = roadLinksInMunicipality.groupBy(_.roadNameIdentifier.getOrElse(""))

    val newAddress = groupedFrozenRoadLinks.keys.flatMap { key =>
      val relevantLinkIds = groupedRoadLinks.getOrElse(key, Seq())

      val mappedAddresses = allViiteRoadAddresses.flatMap { address =>
        relevantLinkIds.find(x => x.linkId == address.linkId).map { roadLink =>
          val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
          val roadAddressTemp = RoadAddressTEMP(address.linkId, address.roadNumber,
            address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue, address.startMValue, address.endMValue, roadLink.geometry, Some(address.sideCode))

          RoadAddressTEMPwithPoint(first, last, roadAddressTemp)
        }
      }

      val frozenAddresses = groupedFrozenRoadLinks(key).flatMap { frozen =>
        val (first, last) = GeometryUtils.geometryEndpoints(frozen.geometry)

        try {
          val roadNumber = Try(frozen.roadNumber.map(_.toInt).head).toOption
          val roadPartNumber = Try(frozen.roadPartNumber.map(_.toInt).head).toOption

          val address = vkmClient.coordsToAddresses(Seq(first, last), roadNumber, roadPartNumber, includePedestrian = Some(true))
          if (address.isEmpty || (address.nonEmpty && address.size != 2)) {
            logger.info("VKM did not return address for both end points")
            Seq()
          } else {
            val grouped = address.groupBy(addr => (addr.road, addr.roadPart))
            if (grouped.keys.size > 1) {
              recalculateAddress(frozen).foreach { recalc =>
                logger.info(s" more than one road -> linkId: ${recalc.roadAddress.linkId} road ${recalc.roadAddress.roadPart} " +
                  s"roadPart ${recalc.roadAddress.roadPart} track ${recalc.roadAddress.track}  etays ${recalc.roadAddress.startAddressM} " +
                  s"let ${recalc.roadAddress.endAddressM} start ${recalc.roadAddress.startMValue}  end let ${recalc.roadAddress.endMValue} ")
              }
              None
            } else {
              val orderedAddress = address.sortBy(_.addrM)
              val track = if (orderedAddress.head.track == orderedAddress.last.track) orderedAddress.head.track else Track.Unknown
              Some(RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(frozen.linkId, orderedAddress.head.road,
                orderedAddress.head.roadPart, track, orderedAddress.head.addrM, orderedAddress.last.addrM,
                0, GeometryUtils.geometryLength(frozen.geometry), frozen.geometry, municipalityCode = Some(frozen.municipalityCode))))
            }
          }
        } catch {
          case ex: Exception =>
            logger.error(s"Exception in VKM for linkId ${frozen.linkId} exception $ex")
            None
        }
      }

      recalculateTrackAndSideCode(mappedAddresses, frozenAddresses, Seq())

    }.toSeq


    (newAddress, frozenRoadLinks.filterNot(frozen => newAddress.map(_.roadAddress.linkId).contains(frozen.linkId)))
  }

  def recalculateTrackAndSideCode(mappedAddresses: Seq[RoadAddressTEMPwithPoint], frozenAddresses: Seq[RoadAddressTEMPwithPoint], result: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    val newResult = calculateTrackAndSideCode(mappedAddresses, frozenAddresses).filterNot(x => x.roadAddress.track == Track.Unknown || x.roadAddress.sideCode.isEmpty)

    if (newResult.isEmpty) {
      result
    } else {
      val newResultLinkIds = newResult.map(_.roadAddress.linkId)
      recalculateTrackAndSideCode(newResult ++ mappedAddresses, frozenAddresses.filterNot(x => newResultLinkIds.contains(x.roadAddress.linkId)), result ++ newResult)
    }
  }

  def calculateTrackAndSideCode(mappedAddresses: Seq[RoadAddressTEMPwithPoint], frozenAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    frozenAddresses.flatMap { frozenAddress =>
      val missingFrozen = frozenAddresses.filterNot(x => x.roadAddress.linkId == frozenAddress.roadAddress.linkId)

      val frozenAddrFirst = getTrackAndSideCodeFirst(frozenAddress, mappedAddresses, missingFrozen)

      if (frozenAddrFirst.nonEmpty) {
        frozenAddrFirst
      } else
        getTrackAndSideCodeLast(frozenAddress, mappedAddresses, frozenAddresses)
    }
  }

  def process() {

    //Get All Municipalities
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach { municipality =>
      PostGISDatabase.withDynTransaction {
        val (toCreate, missing) = processing(municipality)

        val cleanningResult = cleaning(missing, toCreate)

        (cleanningResult ++ toCreate.map(_.roadAddress)).foreach { frozen =>
          roadLinkTempDao.insertInfo(frozen, username)
        }
      }
    }

  }

  /** *
    *
    * @param missing  State road links still missing road address info
    * @param toCreate Temp road addresses to be created
    * @return Final result temp road addresses to be created
    */
  def cleaning(missing: Seq[RoadLink], toCreate: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMP] = {


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