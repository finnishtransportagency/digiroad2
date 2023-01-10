package fi.liikennevirasto.digiroad2.service


import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}

import java.util.Properties
import fi.liikennevirasto.digiroad2.client.viite.{SearchViiteClient, ViiteClientException}
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO
import fi.liikennevirasto.digiroad2.lane.PieceWiseLane
import fi.liikennevirasto.digiroad2.linearasset.{PieceWiseLinearAsset, RoadLink, RoadLinkLike, SpeedLimit}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.{ClientUtils, LogUtils, Track}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, MassLimitationAsset, Point}
import org.apache.http.conn.HttpHostConnectException
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

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
    ClientUtils.retry(2, logger){
      viiteClient.fetchAllBySection(roadNumber, roadParts, tracks)
    }
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
  def getByLrmPosition(linkId: String, mValue: Double): Option[RoadAddressForLink] = {
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
  def getAllByLrmPositions(linkId: String, startMeasure: Double, endMeasure: Double): Seq[RoadAddressForLink] = {
    viiteClient.fetchByLrmPositions(linkId, startMeasure, endMeasure)
  }

  /**
    * Returns all the current road address on the given road link
    *
    * @param linkIds The road link ids
    * @return
    */
  def getAllByLinkIds(linkIds: Seq[String]): Seq[RoadAddressForLink] = {
      ClientUtils.retry(5, logger) {
        LogUtils.time(logger,"TEST LOG Retrieve road address by links"){
          viiteClient.fetchAllByLinkIds(linkIds)
        }
    }
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
        logger.error(s"Viite connection failing with message ${hhce.getMessage} and stacktrace: \n ${hhce.getStackTrace.mkString("", EOL, EOL)}")
        logger.info("Failed to retrieve road address information, return links without it.")
        roadLinks
      case vce: ViiteClientException =>
        logger.error(s"Viite error with message ${vce.getMessage} and stacktrace: \n ${vce.getStackTrace.mkString("", EOL, EOL)}")
        logger.info("Failed to retrieve road address information, return links without it.")
        roadLinks
      case e: Exception =>
        logger.error(s"Unknown error with message ${e.getMessage} and stacktrace: \n ${e.getStackTrace.mkString("", EOL, EOL)}")
        logger.info("Failed to retrieve road address information, return links without it.")
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
  def laneWithRoadAddress(pieceWiseLanes: Seq[PieceWiseLane]): Seq[PieceWiseLane] = {
    try {
      val addressData = groupRoadAddress(getAllByLinkIds(pieceWiseLanes.map(_.linkId))).map(a => (a.linkId, a)).toMap
      pieceWiseLanes.map( pwl =>
          if (addressData.contains(pwl.linkId))
            pwl.copy(attributes = pwl.attributes ++ roadAddressAttributes(addressData(pwl.linkId)))
          else
            pwl
        )
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