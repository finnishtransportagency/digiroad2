package fi.liikennevirasto.digiroad2.service

import java.util.Properties

import fi.liikennevirasto.digiroad2.client.viite.{SearchViiteClient, ViiteClientException}
import fi.liikennevirasto.digiroad2.dao.{RoadAddressForLink, RoadLinkOverrideDAO}
import fi.liikennevirasto.digiroad2.lane.PieceWiseLane
import fi.liikennevirasto.digiroad2.linearasset.{PieceWiseLinearAsset, RoadLink, RoadLinkLike, SpeedLimit}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, MassLimitationAsset, Point}
import org.apache.http.conn.HttpHostConnectException
import org.slf4j.LoggerFactory

class RoadAddressService(viiteClient: SearchViiteClient ) {

  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  /**
    * Return all the current existing road numbers
    *
    * @return
    */
  def getAllRoadNumbers(): Seq[Long] = {
    viiteClient.fetchAllRoadNumbers()
  }

  /**
    * Returns all the existing road address for given road number
    *
    * @param roadNumber The road number
    * @return
    */
  def getAllByRoadNumber(roadNumber: Long): Seq[RoadAddressForLink] = {
    viiteClient.fetchAllByRoadNumber(roadNumber, Seq())
  }

  /**
    * Returns all the existing road address for the given road number and road parts
    *
    * @param roadNumber The road number
    * @param roadParts  All the road number parts
    * @return
    */
  def getAllByRoadNumberAndParts(roadNumber: Long, roadParts: Seq[Long], tracks: Seq[Track] = Seq()): Seq[RoadAddressForLink] = {
    viiteClient.fetchAllBySection(roadNumber, roadParts, tracks)
  }

  /**
    * Returns the current road address for the given road, road part and track code at road address measure
    *
    * @param road        Road number
    * @param roadPart    Road part number
    * @param track       Track code
    * @param addrMeasure Road address measure
    * @return
    */
  def getByRoadSection(road: Long, roadPart: Long, track: Track, addrMeasure: Long): Option[RoadAddressForLink] = {
    viiteClient.fetchAllBySection(road, roadPart, addrMeasure, Seq(track)).headOption
  }

  /**
    * Returns the road address at given road link id and geometry measure
    *
    * @param linkId Road link ID
    * @param mValue Road geometry measure
    */
  def getByLrmPosition(linkId: Long, mValue: Double): Option[RoadAddressForLink] = {
    viiteClient.fetchByLrmPosition(linkId, mValue).headOption
  }

  /**
    * Returns the road address at given road link id and geometry measures
    *
    * @param linkId       Road link ID
    * @param startMeasure Start measure
    * @param endMeasure   End measure
    * @return
    */
  def getAllByLrmPositions(linkId: Long, startMeasure: Double, endMeasure: Double): Seq[RoadAddressForLink] = {
    viiteClient.fetchByLrmPositions(linkId, startMeasure, endMeasure)
  }

  /**
    * Returns all the current road address on the given road link
    *
    * @param linkIds The road link ids
    * @return
    */
  //resolving_frozen_links batch keeps crashing here because of Viite API GW limits, temporary fix implemented
  //TODO Rollback this grouping fix after Viite has implemented API GW limitation work-around
  def getAllByLinkIds(linkIds: Seq[Long]): Seq[RoadAddressForLink] = {
    val linkIdsSplit = linkIds.grouped(1000).toSeq
    linkIdsSplit.flatMap(linkIdGroup => viiteClient.fetchAllByLinkIds(linkIdGroup))
  }

  /**
    * Returns the given road links with road address attributes
    *
    * @param roadLinks The road link sequence
    * @return
    */
  def roadLinkWithRoadAddress(roadLinks: Seq[RoadLink]): Seq[RoadLink] = {
    try {
      val roadAddressLinks = getAllByLinkIds(roadLinks.map(_.linkId))
      val addressData = groupRoadAddress(roadAddressLinks).map(a => (a.linkId, a)).toMap
      logger.info(s"Fetched ${roadAddressLinks.size} road address of ${roadLinks.size} road links.")
      roadLinks.map(rl =>
        if (addressData.contains(rl.linkId))
          rl.copy(attributes = rl.attributes ++ roadAddressAttributes(addressData(rl.linkId)))
        else
          rl
      )
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"Viite connection failing with message ${hhce.getMessage}")
        roadLinks
      case vce: ViiteClientException =>
        logger.error(s"Viite error with message ${vce.getMessage}")
        roadLinks
    }
  }


  def massLimitationWithRoadAddress(massLimitationAsset: Seq[Seq[MassLimitationAsset]]): Seq[Seq[MassLimitationAsset]] = {
    try {
      val addressData = groupRoadAddress(getAllByLinkIds(massLimitationAsset.flatMap(pwa => pwa.map(_.linkId)))).map(a => (a.linkId, a)).toMap
      massLimitationAsset.map(
        _.map(pwa =>
          if (addressData.contains(pwa.linkId))
            pwa.copy(attributes = pwa.attributes ++ roadAddressAttributes(addressData(pwa.linkId)))
          else
            pwa
        ))
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"Viite connection failing with message ${hhce.getMessage}")
        massLimitationAsset
      case vce: ViiteClientException =>
        logger.error(s"Viite error with message ${vce.getMessage}")
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
      val addressData = groupRoadAddress(getAllByLinkIds(pieceWiseLinearAssets.flatMap(pwa => pwa.map(_.linkId)))).map(a => (a.linkId, a)).toMap
      pieceWiseLinearAssets.map(
        _.map(pwa =>
          if (addressData.contains(pwa.linkId))
            pwa.copy(attributes = pwa.attributes ++ roadAddressAttributes(addressData(pwa.linkId)))
          else
            pwa
        ))
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"Viite connection failing with message ${hhce.getMessage}")
        pieceWiseLinearAssets
      case vce: ViiteClientException =>
        logger.error(s"Viite error with message ${vce.getMessage}")
        pieceWiseLinearAssets
    }
  }


  /**
    * Returns the given lane with road address attributes
    *
    * @param pieceWiseLanes The linear assets sequence
    * @return
    */
  def laneWithRoadAddress(pieceWiseLanes: Seq[Seq[PieceWiseLane]]): Seq[Seq[PieceWiseLane]] = {
    try {
      val addressData = groupRoadAddress(getAllByLinkIds(pieceWiseLanes.flatMap(pwl => pwl.map(_.linkId)))).map(a => (a.linkId, a)).toMap
      pieceWiseLanes.map(
        _.map(pwl =>
          if (addressData.contains(pwl.linkId))
            pwl.copy(attributes = pwl.attributes ++ roadAddressAttributes(addressData(pwl.linkId)))
          else
            pwl
        ))
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"Viite connection failing with message ${hhce.getMessage}")
        pieceWiseLanes
      case vce: ViiteClientException =>
        logger.error(s"Viite error with message ${vce.getMessage}")
        pieceWiseLanes
    }
  }

  /**
    * Returns the given speed limits with road address attributes
    *
    * @param speedLimits
    * @return
    */
  def speedLimitWithRoadAddress(speedLimits: Seq[Seq[SpeedLimit]]): Seq[Seq[SpeedLimit]] = {
    try {
      val addressData = groupRoadAddress(getAllByLinkIds(speedLimits.flatMap(pwa => pwa.map(_.linkId)))).map(a => (a.linkId, a)).toMap
      speedLimits.map(
        _.map(pwa =>
          if (addressData.contains(pwa.linkId))
            pwa.copy(attributes = pwa.attributes ++ roadAddressAttributes(addressData(pwa.linkId)))
          else
            pwa
        ))
    } catch {
      case hhce: HttpHostConnectException =>
        logger.error(s"Viite connection failing with message ${hhce.getMessage}")
        speedLimits
      case vce: ViiteClientException =>
        logger.error(s"Viite error with message ${vce.getMessage}")
        speedLimits
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