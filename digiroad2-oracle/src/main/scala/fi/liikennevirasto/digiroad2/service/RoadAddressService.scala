package fi.liikennevirasto.digiroad2.service


import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{Municipality, SideCode, State}
import fi.liikennevirasto.digiroad2.client.{MassQueryParamsCoord, RoadLinkClient, VKMClient}
import fi.liikennevirasto.digiroad2.lane.PieceWiseLane
import fi.liikennevirasto.digiroad2.linearasset.{PieceWiseLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.{LogUtils, RoadAddressRange}
import fi.liikennevirasto.digiroad2._
import org.apache.http.conn.HttpHostConnectException
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
case class RoadAddressForCoordinateTransformation(linkId: String, road: Long, roadPart: Long, track: Track, startAddressM: Long, endAddressM: Long,
                                                  startMValue: Double, endMValue: Double, geom: Seq[Point] = Seq(), sideCode: Option[SideCode] = None,
                                                  municipalityCode: Option[Int] = None, createdDate: Option[String] = None)
case class RoadAddressWithPoint(firstP: Point, lastP: Point, roadAddress: RoadAddressForCoordinateTransformation)
import scala.compat.Platform.EOL
import scala.util.Try

case class RoadAddressForLink(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                              endDate: Option[DateTime] = None, linkId: String,
                              startMValue: Double, endMValue: Double, sideCode: SideCode, geom: Seq[Point],
                              expired: Boolean, createdBy: Option[String], createdDate: Option[DateTime], modifiedDate: Option[DateTime]) {
  def addressMValueToLRM(addrMValue: Long): Option[Double] = {
    if (addrMValue < startAddrMValue || addrMValue > endAddrMValue)
      None
    else
    // Linear approximation: addrM = a*mValue + b <=> mValue = (addrM - b) / a
      sideCode match {
        case TowardsDigitizing => Some((addrMValue - startAddrMValue) * lrmLength / addressLength + startMValue)
        case AgainstDigitizing => Some(endMValue - (addrMValue - startAddrMValue) * lrmLength / addressLength)
        case _ => None
      }
  }

  private val addressLength: Long = endAddrMValue - startAddrMValue
  private val lrmLength: Double = Math.abs(endMValue - startMValue)

  def addrAt(a: Double) = {
    val coefficient = (endAddrMValue - startAddrMValue) / (endMValue - startMValue)
    sideCode match {
      case SideCode.AgainstDigitizing =>
        endAddrMValue - Math.round((a - startMValue) * coefficient)
      case SideCode.TowardsDigitizing =>
        startAddrMValue + Math.round((a - startMValue) * coefficient)
      case _ => throw new IllegalArgumentException(s"Bad sidecode $sideCode on road address $id (link $linkId)")
    }
  }
}

class RoadAddressService() {
  val vkmClient = new VKMClient
  val logger = LoggerFactory.getLogger(getClass)
  val roadLinkClient: RoadLinkClient = new RoadLinkClient()
  val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  
  def getRoadAddressesByRoadAddressRange(roadAddressRange: RoadAddressRange): Seq[RoadAddressForLink] = {
    val startAndEndLinkIdsForAllSegments = vkmClient.fetchStartAndEndLinkIdForAddrRange(roadAddressRange)
    val startLinkId = startAndEndLinkIdsForAllSegments._1
    val endLinkId = startAndEndLinkIdsForAllSegments._2
    vkmClient.fetchLinkIdsBetweenTwoRoadLinks(startLinkId, endLinkId, roadAddressRange.roadNumber)
  }

  /**
    * Returns the road address at given road link id and geometry measure
    *
    * @param linkId Road link ID
    * @param mValue Road geometry measure
    */
  def getByLrmPosition(linkId: String, mValue: Double): Option[RoadAddressForLink] = {
    vkmClient.fetchRoadAddressByLrmPosition(linkId, mValue).headOption
  }

  /**
    * Returns all the current road address on the given road link
    *
    * @param linkIds The road link ids
    * @return
    */
  def getAllByLinkIds(linkIds: Seq[String]): Seq[RoadAddressForLink] = {
    if (linkIds.nonEmpty) vkmClient.fetchRoadAddressesByLinkIdsMassQuery(linkIds) else Seq.empty[RoadAddressForLink]
  }


  private def resolver(linkIds: Seq[RoadLink]) = {
    LogUtils.time(logger, s"TEST LOG Retrieve VKM road address for ${linkIds.size} linkIds") {
      val roadLinksMissingAddress= linkIds.filter(rl => rl.administrativeClass == State || rl.administrativeClass == Municipality)
      resolveByRoadLinks(roadLinksMissingAddress)
    }
  }
  private def getAddressByCoordinate(linkIds: Set[String]): Seq[RoadAddressForLink] = {
    val links = LogUtils.time(logger, s"TEST LOG Retrieve ${linkIds.size} linkId") {
      withDynTransaction {
        roadLinkService.getRoadLinksByLinkIds(linkIds,newTransaction=false)
      }
    }
    logger.info(s"Start fetching road address for total of ${linkIds.size} link ids.")
    resolver(links)
  }

  private def getAddressByCoordinate(linkIds: Seq[RoadLink]): Seq[RoadAddressForLink] = {
    logger.info(s"Start fetching road address for total of ${linkIds.size} link ids by using coordinates.")
    resolver(linkIds)
  }

  private def resolveByRoadLinks(roadLinksMissingAddress: Seq[RoadLink]): Seq[RoadAddressForLink] = {
    val massQueryParams: Seq[MassQueryParamsCoord] = roadLinksMissingAddress.flatMap { roadLinkMissingAddress =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLinkMissingAddress.geometry)
      val roadNumberOpt = Try(roadLinkMissingAddress.roadNumber.map(_.toInt).head).toOption
      val roadPartOpt = Try(roadLinkMissingAddress.roadPartNumber.map(_.toInt).head).toOption
      Seq(
        MassQueryParamsCoord(s"${roadLinkMissingAddress.linkId}_start", first, roadNumberOpt, roadPartOpt, None),
        MassQueryParamsCoord(s"${roadLinkMissingAddress.linkId}_end", last, roadNumberOpt, roadPartOpt, None)
      )
    }

    val massQueryResults: Map[String, RoadAddress] = try {
      vkmClient.coordToAddressMassQuery(massQueryParams, searchDistance = Some(3))
    } catch {
      case ex: Exception =>
        logger.error(s"Exception in VKM mass query: ${ex.getMessage}")
        Map.empty[String, RoadAddress]
    }
    val links = mapVkmInfosIntoRoadLinks(massQueryResults, roadLinksMissingAddress)
    links.map(addressWithPoint => {
      val address= addressWithPoint.roadAddress
      val sideCode = address.sideCode.getOrElse(SideCode.Unknown)
      RoadAddressForLink(id = 0, roadNumber = address.road, roadPartNumber = address.roadPart, track = address.track,
        startAddrMValue = address.startAddressM, endAddrMValue = address.endAddressM,
        linkId = address.linkId, startMValue = address.startMValue, endMValue = address.endMValue, sideCode = sideCode, geom = address.geom, expired = false,
        createdBy = None, createdDate = None, modifiedDate = None)
    })
  }
  def mapVkmInfosIntoRoadLinks(massQueryResults: Map[String, RoadAddress], roadLinksMissingAddress: Seq[RoadLink]): Seq[RoadAddressWithPoint] = {
    def calculateSideCodeUsingEndPointAddrs(addrMAtStart: Int, addrMAtEnd: Int): SideCode = {
      if (addrMAtStart < addrMAtEnd) SideCode.TowardsDigitizing
      else if (addrMAtStart > addrMAtEnd) SideCode.AgainstDigitizing
      else SideCode.Unknown
    }

    // Process each road link to build addresses using the addresses from the mass query.
    val resolvedAddresses: Seq[RoadAddressWithPoint] = roadLinksMissingAddress.flatMap { roadLinkMissingAddress =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLinkMissingAddress.geometry)
      val startPointIdentifier = s"${roadLinkMissingAddress.linkId}_start"
      val endPointIdentifier = s"${roadLinkMissingAddress.linkId}_end"

      (massQueryResults.get(startPointIdentifier), massQueryResults.get(endPointIdentifier)) match {
        case (Some(vkmAddressAtRoadLinkStart), Some(vkmAddressAtRoadLinkEnd)) =>
          val vkmAddresses = Seq(vkmAddressAtRoadLinkStart, vkmAddressAtRoadLinkEnd)
          if (vkmAddresses.size != 2) Seq.empty
          else {
            val groupedVkmAddresses = vkmAddresses.groupBy(addr => (addr.road, addr.roadPart))
            // Returned addresses must be on the same road part, in order to create road address for road link
            if (groupedVkmAddresses.keys.size > 1) None
            else {
              val orderedAddress = vkmAddresses.sortBy(_.addrM)
              // If start and end point road address Track values are not equal, use Track.Unknown
              val track = if (orderedAddress.head.track == orderedAddress.last.track) orderedAddress.head.track else Track.Unknown
              val roadAddressSideCode = calculateSideCodeUsingEndPointAddrs(vkmAddressAtRoadLinkStart.addrM, vkmAddressAtRoadLinkEnd.addrM)
              Some(RoadAddressWithPoint(first, last,
                RoadAddressForCoordinateTransformation(roadLinkMissingAddress.linkId, orderedAddress.head.road, orderedAddress.head.roadPart,
                  track, orderedAddress.head.addrM, orderedAddress.last.addrM, 0, GeometryUtils.geometryLength(roadLinkMissingAddress.geometry),
                  roadLinkMissingAddress.geometry, Some(roadAddressSideCode), municipalityCode = Some(roadLinkMissingAddress.municipalityCode))
              ))
            }
          }
        case _ => None
      }
    }

    val roadLinksAddressUnresolved = roadLinksMissingAddress.filterNot(missing => resolvedAddresses.map(_.roadAddress.linkId).contains(missing.linkId))
    logger.info(
      s"""
         |Resolved ${resolvedAddresses.size} addresses on  ${roadLinksMissingAddress.size} links.
         |State and municipality road links without road address info: ${roadLinksAddressUnresolved.size}
         |""".stripMargin)
    
    resolvedAddresses
  }
  /**
    * Returns the given road links with road address attributes
    *
    * @param roadLinks The road link sequence
    * @return
    */
  def roadLinkWithRoadAddress(roadLinks: Seq[RoadLink], logComment: String = ""): Seq[RoadLink] = {
    try {
      val linkIds = roadLinks.map(_.linkId)
      val vkmRoadAddressesForLinks = getAllByLinkIds(linkIds)
      val vkmAddressData = vkmRoadAddressesForLinks.map(a => (a.linkId, a)).toMap
      val linkIdsMissingAddress = linkIds.diff(vkmAddressData.keys.toSeq).toSet
      val neededLinks = roadLinks.filter(a=>linkIdsMissingAddress.contains(a.linkId))
      val addressByCoordinatesData = getAddressByCoordinate(neededLinks).map(a => (a.linkId, a)).toMap
      val addressData = vkmAddressData ++ addressByCoordinatesData
      logger.info(s"Fetched ${vkmAddressData.values.size} road address of ${roadLinks.size} road links. ${logComment}")
      logger.info(s"Fetched ${addressByCoordinatesData.values.size} road address of ${linkIdsMissingAddress.size} road links by coordinate. ${logComment}")
      roadLinks.map(rl =>
        if (addressData.contains(rl.linkId))
          rl.copy(attributes = rl.attributes ++ roadAddressAttributes(addressData(rl.linkId)))
        else
          rl
      )
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"VKM connection failing with message ${hhce.getMessage} and stacktrace: \n ${hhce.getStackTrace.mkString("", EOL, EOL)}")
        logger.info(s"Failed to retrieve road address information, return links without it. ${logComment}")
        roadLinks
      case rae: RoadAddressException =>
        logger.error(s"VKM error with message ${rae.getMessage} and stacktrace: \n ${rae.getStackTrace.mkString("", EOL, EOL)}")
        logger.info(s"Failed to retrieve road address information, return links without it. ${logComment}")
        roadLinks
      case e: Exception =>
        logger.error(s"Unknown error with message ${e.getMessage} and stacktrace: \n ${e.getStackTrace.mkString("", EOL, EOL)}")
        logger.info(s"Failed to retrieve road address information, return links without it. ${logComment}")
        roadLinks
    }
  }


  def massLimitationWithRoadAddress(massLimitationAsset: Seq[Seq[MassLimitationAsset]]): Seq[Seq[MassLimitationAsset]] = {
    try {
      val linkIds = massLimitationAsset.flatMap(_.map(_.linkId))
      val vkmAddressData = getAllByLinkIds(linkIds).map(a => (a.linkId, a)).toMap
      val linkIdsMissingAddress = linkIds.diff(vkmAddressData.keys.toSeq).toSet
      val addressByCoordinatesData = getAddressByCoordinate(linkIdsMissingAddress).map(a => (a.linkId, a)).toMap
      val addressData = vkmAddressData ++ addressByCoordinatesData
      massLimitationAsset.map(
        _.map(pwa =>
          if (addressData.contains(pwa.linkId))
            pwa.copy(attributes = pwa.attributes ++ roadAddressAttributes(addressData(pwa.linkId)))
          else
            pwa
        ))
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"VKM connection failing with message ${hhce.getMessage}")
        massLimitationAsset
      case rae: RoadAddressException =>
        logger.error(s"VKM error with message ${rae.getMessage}")
        massLimitationAsset
    }
  }

  /**
    * Returns the given linear assets with road address attributes
    *
    * @param pieceWiseLinearAssets The linear assets sequence
    * @return
    */
  def linearAssetWithRoadAddress(pieceWiseLinearAssets: Seq[Seq[PieceWiseLinearAsset]]): Seq[Seq[PieceWiseLinearAsset]] = {
    try {
      val linkIds = pieceWiseLinearAssets.flatMap(_.map(_.linkId))
      val vkmAddressData = groupRoadAddress(getAllByLinkIds(linkIds)).map(a => (a.linkId, a)).toMap
      val linkIdsMissingAddress = linkIds.diff(vkmAddressData.keys.toSeq).toSet
      val addressByCoordinatesData = getAddressByCoordinate(linkIdsMissingAddress).map(a => (a.linkId, a)).toMap
      val addressData = vkmAddressData ++ addressByCoordinatesData
      pieceWiseLinearAssets.map(
        _.map(pwa =>
          if (addressData.contains(pwa.linkId))
            pwa.copy(attributes = pwa.attributes ++ roadAddressAttributes(addressData(pwa.linkId)))
          else
            pwa
        ))
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"VKM connection failing with message ${hhce.getMessage}")
        pieceWiseLinearAssets
      case rae: RoadAddressException =>
        logger.error(s"VKM error with message ${rae.getMessage}")
        pieceWiseLinearAssets
    }
  }


  /**
    * Returns the given lane with road address attributes
    *
    * @param pieceWiseLanes The linear assets sequence
    * @return
    */
  def laneWithRoadAddress(pieceWiseLanes: Seq[PieceWiseLane]): Seq[PieceWiseLane] = {
    try {
      val linkIds = pieceWiseLanes.map(_.linkId)
      val vkmAddressData = getAllByLinkIds(linkIds).map(a => (a.linkId, a)).toMap
      val linkIdsMissingAddress = linkIds.diff(vkmAddressData.keys.toSeq).toSet
      val addressByCoordinatesData = getAddressByCoordinate(linkIdsMissingAddress).map(a => (a.linkId, a)).toMap
      val addressData = vkmAddressData ++ addressByCoordinatesData
      pieceWiseLanes.map( pwl =>
          if (addressData.contains(pwl.linkId))
            pwl.copy(attributes = pwl.attributes ++ roadAddressAttributes(addressData(pwl.linkId)))
          else
            pwl
        )
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"VKM connection failing with message ${hhce.getMessage}")
        pieceWiseLanes
      case rae: RoadAddressException =>
        logger.error(s"VKM error with message ${rae.getMessage}")
        pieceWiseLanes
    }
  }

  private def roadAddressAttributes(roadAddress: RoadAddressForLink) = {
    Map(
      "ROAD_NUMBER" -> roadAddress.roadNumber,
      "ROAD_PART_NUMBER" -> roadAddress.roadPartNumber,
      "TRACK" -> roadAddress.track.value,
      "SIDECODE" -> roadAddress.sideCode.value,
      "START_ADDR" -> roadAddress.startAddrMValue,
      "END_ADDR" -> roadAddress.endAddrMValue
    )
  }

  def groupRoadAddress(roadAddresses: Seq[RoadAddressForLink]): Seq[RoadAddressForLink] = {
    roadAddresses.groupBy(ra => (ra.linkId, ra.roadNumber, ra.roadPartNumber)).mapValues(ras => (ras.minBy(_.startAddrMValue), ras.maxBy(_.endAddrMValue))).map {
      case (key, (startRoadAddress, endRoadAddress)) => startRoadAddress.copy(endAddrMValue = endRoadAddress.endAddrMValue)
    }.toSeq
  }

}