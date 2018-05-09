package fi.liikennevirasto.digiroad2.service

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.TractorRoad
import fi.liikennevirasto.digiroad2.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.digiroad2.linearasset.{PieceWiseLinearAsset, RoadLink, RoadLinkLike, SpeedLimit}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, MassLimitationAsset, Point}
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import sun.reflect.generics.reflectiveObjects.NotImplementedException

//TODO add parameters after merge of DROTH-1276
class RoadAddressesService {

  val logger = LoggerFactory.getLogger(getClass)

  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(dr2properties.getProperty("digiroad2.viiteRestApiEndPoint"), HttpClientBuilder.create().build())
  }

  /**
    * Return all the current existing road numbers
    * @return
    */
  def getAllRoadNumbers(): Seq[Long] ={
    viiteClient.fetchAllRoadNumbers()
  }

  /**
    * Returns all the existing road address for given road number
    * @param roadNumber The road number
    * @return
    */
  def getAllByRoadNumber(roadNumber: Long): Seq[RoadAddress] = {
    viiteClient.fetchAllByRoadNumber(roadNumber, Seq())
  }

  /**
    * Returns all the existing road address for the given road number and road parts
    * @param roadNumber The road number
    * @param roadParts All the road number parts
    * @return
    */
  def getAllByRoadNumberAndParts(roadNumber: Long, roadParts: Seq[Long]): Seq[RoadAddress] = {
    viiteClient.fetchAllBySection(roadNumber, roadParts, Seq())
  }

  /**
    * Returns the current road address for the given road, road part and track code at road address measure
    * @param road Road number
    * @param roadPart Road part number
    * @param track Track code
    * @param addrMeasure Road address measure
    * @return
    */
  def getByRoadSection(road: Long, roadPart: Long, track: Track, addrMeasure: Long) : Option[RoadAddress] = {
    viiteClient.fetchAllBySection(road, roadPart, addrMeasure, Seq(track)).headOption
  }

  /**
    * Returns the road address at given road link id and geometry measure
    * @param linkId Road link ID
    * @param mValue Road geometry measure
    */
  def getByLrmPosition(linkId: Long, mValue: Double): Option[RoadAddress] = {
    viiteClient.fetchByLrmPosition(linkId, mValue).headOption
  }

  /**
    * Returns the road address at given road link id and geometry measures
    * @param linkId Road link ID
    * @param startMeasure Start measure
    * @param endMeasure End measure
    * @return
    */
  def getAllByLrmPositions(linkId: Long, startMeasure: Double, endMeasure: Double): Seq[RoadAddress] = {
    viiteClient.fetchByLrmPositions(linkId, startMeasure, endMeasure)
  }

  /**
    * Returns all the current road address on the given road link
    * @param linkIds The road link ids
    * @return
    */
  def getAllByLinkIds(linkIds: Seq[Long]): Seq[RoadAddress] = {
    viiteClient.fetchAllByLinkIds(linkIds)
  }

  /**
    * Returns the given road links with road address attributes
    * @param roadLinks The road link sequence
    * @return
    */
  def roadLinkWithRoadAddress(roadLinks: Seq[RoadLink]): Seq[RoadLink] = {
    try {
      val addressData = groupRoadAddress(getAllByLinkIds(roadLinks.map(_.linkId))).map(a => (a.linkId, a)).toMap
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
    }
  }

  /**
    * Returns the given linear assets with road address attributes
    * @param pieceWiseLinearAssets The linear assets sequence
    * @return
    */
  def linearAssetWithRoadAddress(pieceWiseLinearAssets: Seq[Seq[PieceWiseLinearAsset]]): Seq[Seq[PieceWiseLinearAsset]] ={
    try{
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
    }
  }

  /**
    * Returns the given speed limits with road address attributes
    * @param speedLimits
    * @return
    */
  def speedLimitWithRoadAddress(speedLimits: Seq[Seq[SpeedLimit]]): Seq[Seq[SpeedLimit]] ={
    try{
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
    }
  }

  private def roadAddressAttributes(roadAddress: RoadAddress) = {
    Map(
      "VIITE_ROAD_NUMBER" -> roadAddress.roadNumber,
      "VIITE_ROAD_PART_NUMBER" -> roadAddress.roadPartNumber,
      "VIITE_TRACK" -> roadAddress.track.value,
      "VIITE_SIDECODE" -> roadAddress.sideCode.value,
      "VIITE_START_ADDR" -> roadAddress.startAddrMValue,
      "VIITE_END_ADDR" -> roadAddress.endAddrMValue
    )
  }

  private def groupRoadAddress(roadAddresses: Seq[RoadAddress]): Seq[RoadAddress] ={
    roadAddresses.groupBy(ra => (ra.linkId, ra.roadNumber, ra.roadPartNumber)).mapValues(ras => (ras.minBy(_.startAddrMValue),ras.maxBy(_.endAddrMValue))).map{
      case (key, (startRoadAddress, endRoadAddress)) => startRoadAddress.copy(endAddrMValue = endRoadAddress.endAddrMValue)
    }.toSeq
  }

  @deprecated
  def getRoadAddressPropertiesByLinkId(assetCoordinates: Point, linkId: Long, roadLink: RoadLinkLike, oldProperties: Seq[Property]): Seq[Property] = {
    //TODO this method is deleted at DROTH-1276
    throw new NotImplementedException
  }

}
