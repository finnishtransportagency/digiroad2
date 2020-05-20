package fi.liikennevirasto.digiroad2.dao.lane

import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.Sequences
import slick.jdbc.StaticQuery.interpolation

import scala.language.implicitConversions

class LaneHistoryDao(val vvhClient: VVHClient, val roadLinkService: RoadLinkService ){

  def insertHistoryLane(oldLaneId: Long, newLaneId: Option[Long], username: String): Long = {
    val laneHistoryId = Sequences.nextPrimaryKeySeqValue
    val laneHistoryPositionId = Sequences.nextPrimaryKeySeqValue

    sqlu"""
        INSERT ALL
          INTO LANE_HISTORY
          SELECT $laneHistoryId, $newLaneId, l.* FROM LANE l WHERE id = $oldLaneId

          INTO LANE_HISTORY_POSITION
          SELECT $laneHistoryPositionId, SIDE_CODE, START_MEASURE, END_MEASURE, LINK_ID, ADJUSTED_TIMESTAMP, MODIFIED_DATE
            FROM LANE_POSITION WHERE id = (SELECT LANE_POSITION_ID FROM LANE_LINK WHERE LANE_ID = $oldLaneId)

          INTO LANE_HISTORY_LINK (lane_id, lane_position_id)
          VALUES ($laneHistoryId, $laneHistoryPositionId )
        SELECT * FROM dual
      """.execute

    laneHistoryId
  }

  def expireHistoryLane(historyLaneId: Long, username: String): Unit = {
    sqlu"""
           UPDATE LANE_HISTORY
           SET VALID_TO = sysdate,
               MODIFIED_DATE = sysdate,
               MODIFIED_BY = $username
           WHERE id = $historyLaneId
    """.execute
  }

}
