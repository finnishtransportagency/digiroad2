package fi.liikennevirasto.digiroad2.service


import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, VKMClient}
import fi.liikennevirasto.digiroad2.dao.RoadAddressTempDAO
import fi.liikennevirasto.digiroad2.lane.PieceWiseLane
import fi.liikennevirasto.digiroad2.linearasset.{PieceWiseLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.{ClientUtils, LogUtils, Parallel, ResolvingFrozenRoadLinks, RoadAddressRange}
import fi.liikennevirasto.digiroad2.{DummyEventBus, MassLimitationAsset, Point, RoadAddressException, Track, client}
import org.apache.http.conn.HttpHostConnectException
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.parallel.ParSeq
import scala.compat.Platform.EOL

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
  val roadAddressTempDAO = new RoadAddressTempDAO
  val logger = LoggerFactory.getLogger(getClass)
  val roadLinkClient: RoadLinkClient = new RoadLinkClient()
  val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

// TODO This for adding lane by road address ?
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
    if (linkIds.nonEmpty) {
      val links = withDynTransaction { roadLinkService.getRoadLinksByLinkIds(linkIds.toSet)}
      val resolved = ResolvingFrozenRoadLinks.resolveByRoadLinks(links)._1.map(_.roadAddress)
      resolved.map(temp => {
        val sideCode = temp.sideCode.getOrElse(SideCode.Unknown)
        RoadAddressForLink(id = 0, roadNumber = temp.road, roadPartNumber = temp.roadPart, track = temp.track,
          startAddrMValue = temp.startAddressM, endAddrMValue = temp.endAddressM,
          linkId = temp.linkId, startMValue = temp.startMValue, endMValue = temp.endMValue, sideCode = sideCode, geom = temp.geom, expired = false,
          createdBy = None, createdDate = None, modifiedDate = None)
      })
    } else {
      Seq.empty[RoadAddressForLink]
    }
  }
  
  def getTempAddressesByLinkIdsAsRoadAddressForLink(linkIds: Set[String]): Seq[RoadAddressForLink] = { Seq() }
  
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
      val tempAddressData = getTempAddressesByLinkIdsAsRoadAddressForLink(linkIdsMissingAddress).map(a => (a.linkId, a)).toMap
      val addressData = vkmAddressData ++ tempAddressData
      logger.info(s"Fetched ${vkmAddressData.values.size} road address of ${roadLinks.size} road links. ${logComment}")
      logger.info(s"Fetched ${tempAddressData.values.size} temp road address of ${linkIdsMissingAddress.size} road links. ${logComment}")
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
      val tempAddressData = getTempAddressesByLinkIdsAsRoadAddressForLink(linkIdsMissingAddress).map(a => (a.linkId, a)).toMap
      val addressData = vkmAddressData ++ tempAddressData
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
      val tempAddressData = getTempAddressesByLinkIdsAsRoadAddressForLink(linkIdsMissingAddress).map(a => (a.linkId, a)).toMap
      val addressData = vkmAddressData ++ tempAddressData
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
      val tempAddressData = getTempAddressesByLinkIdsAsRoadAddressForLink(linkIdsMissingAddress).map(a => (a.linkId, a)).toMap
      val addressData = vkmAddressData ++ tempAddressData
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