package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.TractorRoad
import fi.liikennevirasto.digiroad2.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.digiroad2.linearasset.{PieceWiseLinearAsset, RoadLink, RoadLinkLike, SpeedLimit}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import sun.reflect.generics.reflectiveObjects.NotImplementedException

//TODO add parameters after merge of DROTH-1276
class RoadAddressesService {

  val logger = LoggerFactory.getLogger(getClass)

  /**
    * Return all the current existing road numbers
    * @return
    */
  def getAllRoadNumbers(): Seq[Long] ={
    throw new NotImplementedError
  }

  /**
    * Returns all the existing road address for given road number
    * @param roadNumber The road number
    * @return
    */
  def getAllByRoadNumber(roadNumber: Long): Seq[RoadAddress] = {
    throw new NotImplementedError
  }

  /**
    * Returns all the existing road address for the given road number and road parts
    * @param roadNumber The road number
    * @param roadParts All the road number parts
    * @return
    */
  def getAllByRoadNumberAndParts(roadNumber: Long, roadParts: Seq[Long]): Seq[RoadAddress] = {
    throw new NotImplementedError
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
    throw  new NotImplementedError
  }

  /**
    * Returns the road address at given road link id and geometry measure
    * @param linkId Road link ID
    * @param mValue Road geometry measure
    */
  def getByLrmPosition(linkId: Long, mValue: Double): Option[RoadAddress] = {
    throw new NotImplementedError
  }

  /**
    * Returns the road address at given road link id and geometry measures
    * @param linkId Road link ID
    * @param startMeasure Start measure
    * @param endMeasure End measure
    * @return
    */
  def getAllByLrmPositions(linkId: Long, startMeasure: Double, endMeasure: Double): Seq[RoadAddress] = {
    throw new NotImplementedError
  }

  /**
    * Returns all the current road address on the given road link
    * @param linkIds The road link ids
    * @return
    */
  def getAllByLinkIds(linkIds: Seq[Long]): Seq[RoadAddress] = {
    throw new NotImplementedError
  }

  /**
    * Returns the given road links with road address attributes
    * @param roadLinks The road link sequence
    * @return
    */
  def roadLinkWithRoadAddress(roadLinks: Seq[RoadLink]): Seq[RoadLink] = {
    val addressData = getAllByLinkIds(roadLinks.map(_.linkId)).map(a => (a.linkId, a)).toMap
    roadLinks.map(rl =>
      if (addressData.contains(rl.linkId))
        rl.copy(attributes = rl.attributes ++ roadAddressAttributes(addressData(rl.linkId)))
      else
        rl
    )
  }

  /**
    * Returns the given linear assets with road address attributes
    * @param pieceWiseLinearAssets The linear assets sequence
    * @return
    */
  def linearAssetWithRoadAddress(pieceWiseLinearAssets: Seq[Seq[PieceWiseLinearAsset]]): Seq[Seq[PieceWiseLinearAsset]] ={
    val addressData = getAllByLinkIds(pieceWiseLinearAssets.flatMap(pwa => pwa.map(_.linkId))).map(a => (a.linkId, a)).toMap
    pieceWiseLinearAssets.map(
      _.map(pwa =>
        if (addressData.contains(pwa.linkId))
          pwa.copy(attributes = pwa.attributes ++ roadAddressAttributes(addressData(pwa.linkId)))
        else
          pwa
      ))
  }

  /**
    * Returns the given speed limits with road address attributes
    * @param speedLimits
    * @return
    */
  def speedLimitWithRoadAddress(speedLimits: Seq[Seq[SpeedLimit]]): Seq[Seq[SpeedLimit]] ={
    val addressData = getAllByLinkIds(speedLimits.flatMap(pwa => pwa.map(_.linkId))).map(a => (a.linkId, a)).toMap
    speedLimits.map(
      _.map(pwa =>
        if (addressData.contains(pwa.linkId))
          pwa.copy(attributes = pwa.attributes ++ roadAddressAttributes(addressData(pwa.linkId)))
        else
          pwa
      ))
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

  @deprecated
  def getRoadAddressPropertiesByLinkId(assetCoordinates: Point, linkId: Long, roadLink: RoadLinkLike, oldProperties: Seq[Property]): Seq[Property] = {
    //TODO this method is deleted at DROTH-1276
    throw new NotImplementedException
  }

}
