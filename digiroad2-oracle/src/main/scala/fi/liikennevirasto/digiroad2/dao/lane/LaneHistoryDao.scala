package fi.liikennevirasto.digiroad2.dao.lane

import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, PersistedHistoryLane, PersistedLane}
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import org.joda.time.DateTime
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import slick.jdbc.StaticQuery.interpolation

import scala.language.implicitConversions

case class LaneHistoryRow(id: Long, newId: Long, oldId: Long, linkId: Long, sideCode: Int, value: LanePropertyRow,
                          startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime],
                          modifiedBy: Option[String], modifiedDate: Option[DateTime], expired: Boolean,
                          vvhTimeStamp: Long, municipalityCode: Long, laneCode: Int, geomModifiedDate: Option[DateTime])

class LaneHistoryDao(val vvhClient: VVHClient, val roadLinkService: RoadLinkService) {

  implicit val getLaneHistoryAsset: GetResult[LaneHistoryRow] = new GetResult[LaneHistoryRow] {
    def apply(r: PositionedResult): LaneHistoryRow = {
      val id = r.nextLong()
      val newId = r.nextLong()
      val oldId = r.nextLong()
      val linkId = r.nextLong()
      val sideCode = r.nextInt()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean()
      val vvhTimeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val atrrName = r.nextString()
      val atrrValue = r.nextStringOption()
      val value = LanePropertyRow(atrrName, atrrValue)
      val municipalityCode = r.nextLong()
      val laneCode = r.nextInt()

      LaneHistoryRow(id, newId, oldId, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate,
        expired, vvhTimeStamp, municipalityCode, laneCode, geomModifiedDate)
    }
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  private def query(): String = {
    """SELECT l.id, l.new_id, l.old_id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure,
    l.created_by, l.created_date, l.modified_by, l.modified_date,
    CASE WHEN l.valid_to <= sysdate THEN 1 ELSE 0 END AS expired,
    pos.adjusted_timestamp, pos.modified_date,
    la.name, la.value, l.municipality_code, l.lane_code
    FROM LANE_HISTORY l
       JOIN LANE_HISTORY_LINK ll ON l.id = ll.lane_id
       JOIN LANE_HISTORY_POSITION pos ON ll.lane_position_id = pos.id
       JOIN LANE_HISTORY_ATTRIBUTE la ON la.lane_history_id = l.id """
  }

  def insertHistoryLane(oldLaneId: Long, newLaneId: Option[Long], username: String): Long = {
    val laneHistoryId = Sequences.nextPrimaryKeySeqValue
    val laneHistoryPositionId = Sequences.nextPrimaryKeySeqValue
    val newLaneIdToRelate = newLaneId match {
      case Some(id) => id
      case _ => 0
    }

    sqlu"""
        INSERT INTO LANE_HISTORY
          SELECT $laneHistoryId, $newLaneIdToRelate, l.*, sysdate, $username FROM LANE l WHERE id = $oldLaneId
      """.execute

    sqlu"""
        INSERT INTO LANE_HISTORY_POSITION
           SELECT $laneHistoryPositionId, SIDE_CODE, START_MEASURE, END_MEASURE, LINK_ID, ADJUSTED_TIMESTAMP, MODIFIED_DATE
              FROM LANE_POSITION WHERE id = (SELECT LANE_POSITION_ID FROM LANE_LINK WHERE LANE_ID = $oldLaneId)
      """.execute

    sqlu"""
        INSERT INTO LANE_HISTORY_LINK (lane_id, lane_position_id)
        VALUES ($laneHistoryId, $laneHistoryPositionId )
      """.execute

    sqlu"""
        INSERT INTO LANE_HISTORY_ATTRIBUTE
           SELECT primary_key_seq.nextval, $laneHistoryId, NAME, VALUE, REQUIRED, CREATED_DATE, CREATED_BY,
                         MODIFIED_DATE, MODIFIED_BY FROM LANE_ATTRIBUTE WHERE LANE_ID = $oldLaneId
      """.execute

    laneHistoryId
  }

  def expireHistoryLane(historyLaneId: Long, username: String): Unit = {
    sqlu"""
           UPDATE LANE_HISTORY
           SET HISTORY_CREATED_DATE = sysdate,
               HISTORY_CREATED_BY = $username,
               EXPIRED_DATE = sysdate,
               EXPIRED_BY = $username,
               VALID_TO = sysdate
           WHERE id = $historyLaneId
    """.execute
  }

  def convertLaneRowToPersistedLane(lanes: Seq[LaneHistoryRow]): Seq[PersistedHistoryLane] = {
    lanes.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val attributeValues = laneRowToProperty(assetRows)

      id -> PersistedHistoryLane(id = row.id, newId = row.newId, oldId = row.oldId, linkId = row.linkId, sideCode = row.sideCode, laneCode = row.laneCode,
        municipalityCode = row.municipalityCode, startMeasure = row.startMeasure, endMeasure = row.endMeasure,
        createdBy = row.createdBy, createdDateTime = row.createdDate,
        modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired,
        vvhTimeStamp = row.vvhTimeStamp, geomModifiedDate = row.geomModifiedDate, attributes = attributeValues)

    }.values.toSeq
  }

  def laneRowToProperty(laneRows: Iterable[LaneHistoryRow]): Seq[LaneProperty] = {
    val laneCodeAttribute = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(laneRows.head.laneCode))))

    val props = laneRows.groupBy(_.value.publicId).map { case (_, rows) =>
      val row = rows.head

      LaneProperty(
        publicId = row.value.publicId,
        values = rows.flatMap(laneRow =>
          laneRow.value.propertyValue match {
            case Some(value) => Some(LanePropertyValue(value))
            case _ => None
          }
        ).toSeq
      )
    }.toSeq

    props ++ laneCodeAttribute
  }

  def fetchHistoryLanesByLinkIdsAndLaneCode(linkIds: Seq[Long], laneCode: Seq[Int], includeExpired: Boolean = false): Seq[PersistedHistoryLane] = {
    fetchAllHistoryLanesByLinkIds(linkIds, includeExpired, laneCode)
  }

  def fetchAllHistoryLanesByLinkIds(linkIds: Seq[Long], includeExpired: Boolean = false, laneCodeFilter: Seq[Int] = Seq()): Seq[PersistedHistoryLane] = {
    val filterExpired = s" (l.valid_to > sysdate OR l.valid_to IS NULL ) "
    val laneCodeClause = s" l.lane_code in (${laneCodeFilter.mkString(",")})"

    val whereClause = (includeExpired, laneCodeFilter.nonEmpty) match {
      case (false, true) => s" WHERE $filterExpired AND $laneCodeClause ORDER BY l.lane_code ASC"
      case (_, true) => s" WHERE $laneCodeClause ORDER BY l.lane_code ASC"
      case (false, _) => s" WHERE $filterExpired ORDER BY l.lane_code ASC"
      case _ => " ORDER BY l.lane_code ASC"
    }

    MassQuery.withIds(linkIds.toSet) { idTableName =>
      val filter = s" JOIN $idTableName i ON i.id = pos.link_id $whereClause"

      getHistoryLanesFilterQuery(withFilter(filter))
    }
  }

  def getHistoryLanesFilterQuery(queryFilter: String => String): Seq[PersistedHistoryLane] = {
    val historyLanes = StaticQuery.queryNA[LaneHistoryRow](queryFilter(query()))(getLaneHistoryAsset).iterator.toSeq

    convertLaneRowToPersistedLane(historyLanes)
  }

}
