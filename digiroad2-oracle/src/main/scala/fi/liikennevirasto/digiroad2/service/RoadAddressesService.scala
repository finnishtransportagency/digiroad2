package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset.{CycleOrPedestrianPath, Property, PropertyTypes, PropertyValue}
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.TractorRoad
import fi.liikennevirasto.digiroad2.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
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
    * Returns all the current road address on the given
    * @param linkIds
    * @return
    */
  def getAllByLinkIds(linkIds: Seq[Long]): Seq[RoadAddress] = {
    throw new NotImplementedError
  }

  @deprecated
  def getRoadAddressPropertiesByLinkId(assetCoordinates: Point, linkId: Long, roadLink: RoadLinkLike, oldProperties: Seq[Property]): Seq[Property] = {
    //TODO this method is deleted at DROTH-1276
    throw new NotImplementedException
  }

}
