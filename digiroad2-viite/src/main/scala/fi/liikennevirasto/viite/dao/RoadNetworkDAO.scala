package fi.liikennevirasto.viite.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object RoadNetworkDAO {

  def createPublishedRoadNetwork: Unit = {
    sqlu"""INSERT INTO published_road_network (id, created) VALUES (published_road_network_key_seq.NEXTVAL, sysdate)""".execute
  }

  def expireRoadNetwork: Unit = {
    sqlu"""UPDATE published_road_network SET valid_to = sysdate WHERE id = (SELECT MAX(ID) FROM published_road_network)""".execute
  }

  def createPublishedRoadAddress(roadAddressId: Long): Unit = {
    sqlu"""INSERT INTO published_road_address (network_id, road_address_id) VALUES (published_road_network_key_seq.NEXTVAL, $roadAddressId)""".execute
  }

  def addRoadNetworkError(roadAddressId: Long, errorCode: Long): Unit = {
    sqlu"""INSERT INTO road_network_errors (id, road_address_id, error_code) VALUES (road_network_errors_key_seq.NEXTVAL, $roadAddressId, $errorCode)""".execute
  }

  def removeNetworkErrors: Unit = {
    sqlu"""DELETE FROM road_network_errors""".execute
  }

  def hasRoadNetworkErrors: Boolean = {
    sql"""SELECT COUNT(*) FROM road_network_errors """.as[Long].first > 0
  }

  def getLatestRoadNetworkVersion: Long = {
    sql"""SELECT MAX(id) FROM published_road_network""".as[Long].first
  }

}
