package fi.liikennevirasto.digiroad2.dao.lane

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimeSimplifiedFormat
import fi.liikennevirasto.digiroad2.asset.Decode
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, OldLaneWithNewId, PersistedHistoryLane, PersistedLane}
import fi.liikennevirasto.digiroad2.postgis.MassQuery
import org.joda.time.DateTime
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import slick.jdbc.StaticQuery.interpolation

import java.sql.Timestamp
import scala.language.implicitConversions

case class LaneHistoryRow(id: Long, newId: Long, oldId: Long, linkId: String, sideCode: Int, value: LanePropertyRow,
                          startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime],
                          modifiedBy: Option[String], modifiedDate: Option[DateTime], expired: Boolean,
                          timeStamp: Long, municipalityCode: Long, laneCode: Int, geomModifiedDate: Option[DateTime],
                          historyCreatedDate: DateTime, historyCreatedBy: String, changeEventOrderNumber: Option[Int])
case class laneToHistoryLane(oldId: Long, historyId: Long, historyPositionId: Long)
case class HistoryLaneWithIds(newLaneId: Long, laneHistoryId: Long, positionHistoryId: Long, historyEventOrderNumber: Long, oldLane: PersistedLane)

class LaneHistoryDao() {

  implicit val getLaneHistoryAsset: GetResult[LaneHistoryRow] = new GetResult[LaneHistoryRow] {
    def apply(r: PositionedResult): LaneHistoryRow = {
      val id = r.nextLong()
      val newId = r.nextLong()
      val oldId = r.nextLong()
      val linkId = r.nextString()
      val sideCode = r.nextInt()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean()
      val timeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val atrrName = r.nextString()
      val atrrValue = r.nextStringOption()
      val atrrCreatedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val atrrCreatedBy = r.nextStringOption()
      val atrrModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val atrrModifiedBy = r.nextStringOption()
      val value = LanePropertyRow(atrrName, atrrValue, atrrCreatedDate, atrrCreatedBy, atrrModifiedDate, atrrModifiedBy)
      val municipalityCode = r.nextLong()
      val laneCode = r.nextInt()
      val historyCreatedDate = new DateTime(r.nextTimestamp())
      val historyCreatedBy = r.nextString()
      val changeEventOrderNumber = r.nextIntOption()

      LaneHistoryRow(id, newId, oldId, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate,
        expired, timeStamp, municipalityCode, laneCode, geomModifiedDate, historyCreatedDate, historyCreatedBy, changeEventOrderNumber)
    }
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  private def query(): String = {
    """SELECT l.id, l.new_id, l.old_id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure,
    l.created_by, l.created_date, l.modified_by, l.modified_date,
    CASE WHEN l.valid_to <= current_timestamp THEN 1 ELSE 0 END AS expired,
    pos.adjusted_timestamp, pos.modified_date,
    la.name, la.value, la.created_date, la.created_by, la.modified_date, la.modified_by, l.municipality_code, l.lane_code,
    l.history_created_date, l.history_created_by, l.event_order_number
    FROM LANE_HISTORY l
       JOIN LANE_HISTORY_LINK ll ON l.id = ll.lane_id
       JOIN LANE_HISTORY_POSITION pos ON ll.lane_position_id = pos.id
       JOIN LANE_HISTORY_ATTRIBUTE la ON la.lane_history_id = l.id """
  }

  def insertHistoryLanes(oldLaneIds: Seq[Long], username: String): Seq[Long] = {
    val laneHistoryIds = Sequences.nextPrimaryKeySeqValues(oldLaneIds.size)
    val laneHistoryPositionIds = Sequences.nextPrimaryKeySeqValues(oldLaneIds.size)
    val oldLanesWithHistory = oldLaneIds.zipWithIndex.map { case (oldId, index) =>
      laneToHistoryLane(oldId, laneHistoryIds(index), laneHistoryPositionIds(index))
    }

    val insertLaneHistory =
      s"""insert into lane_history
         |  select (?), 0, l.*, current_timestamp, '$username'
         |  from lane l where id = (?)""".stripMargin
    MassQuery.executeBatch(insertLaneHistory) { statement =>
      oldLanesWithHistory.foreach(lane => {
        statement.setLong(1, lane.historyId)
        statement.setLong(2, lane.oldId)
        statement.addBatch()
      })
    }

    val insertHistoryPosition =
      s"""insert into lane_history_position
         |  select (?), side_code, start_measure, end_measure, link_id, adjusted_timestamp, modified_date
         |  from lane_position where id = (select lane_position_id from lane_link where lane_id = (?))""".stripMargin
    MassQuery.executeBatch(insertHistoryPosition) { statement =>
      oldLanesWithHistory.foreach(lane => {
        statement.setLong(1, lane.historyPositionId)
        statement.setLong(2, lane.oldId)
        statement.addBatch()
      })
    }

    val insertLaneHistoryLink =
      s"""insert into lane_history_link (lane_id, lane_position_id)
         |values ((?), (?))""".stripMargin
    MassQuery.executeBatch(insertLaneHistoryLink) { statement =>
      oldLanesWithHistory.foreach(lane => {
        statement.setLong(1, lane.historyId)
        statement.setLong(2, lane.historyPositionId)
        statement.addBatch()
      })
    }

    val insertLaneHistoryAttribute =
      s"""insert into lane_history_attribute
         |  select nextval('primary_key_seq'), (?), name, value, required, created_date, created_by,
         |  modified_date, modified_by from lane_attribute where lane_id = (?)""".stripMargin
    MassQuery.executeBatch(insertLaneHistoryAttribute) { statement =>
      oldLanesWithHistory.foreach(lane => {
        statement.setLong(1, lane.historyId)
        statement.setLong(2, lane.oldId)
        statement.addBatch()
      })
    }

    laneHistoryIds
  }

  /**
    * Creates expired history lanes with given new_ids
    * Optimized method for samuutus usage.
    * @param expiredLanesWithNewIds Lanes with optional newId
    * @param username User name to be used on expired_by and history_created_by fields
    * @return Created lane history IDs
    */
  def createHistoryLanesWithNewIdsBatch(expiredLanesWithNewIds: Seq[OldLaneWithNewId], username: String): Seq[Long] = {
    val laneHistoryIds = Sequences.nextPrimaryKeySeqValues(expiredLanesWithNewIds.size)
    val lanePositionHistoryIds = Sequences.nextPrimaryKeySeqValues(expiredLanesWithNewIds.size)
    val laneHistoryEventNumbers = Sequences.nextLaneHistoryEventOrderNumberValues(expiredLanesWithNewIds.size)
    val oldLanesWithHistory = expiredLanesWithNewIds.zipWithIndex.map{ case (laneToExpire, index) =>
      val newId = laneToExpire.newId.getOrElse(0L)
      HistoryLaneWithIds(newId, laneHistoryIds(index), lanePositionHistoryIds(index), laneHistoryEventNumbers(index), laneToExpire.lane)
    }

    val insertLaneHistory =
      s"""insert into lane_history
         |  values( (?), (?), (?), (?), (?), (?), (?), (?), current_timestamp, '$username', null,  current_timestamp, (?), current_timestamp, '$username', (?) )
         |  """.stripMargin
    MassQuery.executeBatch(insertLaneHistory) { statement =>
      oldLanesWithHistory.foreach(lane => {
        val createdTimeStamp = new Timestamp(lane.oldLane.createdDateTime.get.getMillis)

        statement.setLong(1, lane.laneHistoryId)
        statement.setLong(2, lane.newLaneId)
        statement.setLong(3, lane.oldLane.id)
        statement.setInt(4, lane.oldLane.laneCode)
        statement.setTimestamp(5, createdTimeStamp)
        if(lane.oldLane.createdBy.nonEmpty) {
          statement.setString(6, lane.oldLane.createdBy.get)
        } else statement.setNull(6, java.sql.Types.VARCHAR)
        if(lane.oldLane.modifiedDateTime.nonEmpty) {
          statement.setTimestamp(7, new Timestamp(lane.oldLane.modifiedDateTime.get.getMillis))
        } else statement.setNull(7, java.sql.Types.TIMESTAMP)
        if(lane.oldLane.modifiedBy.nonEmpty) {
          statement.setString(8, lane.oldLane.modifiedBy.get)
        } else statement.setNull(8, java.sql.Types.VARCHAR)
        statement.setLong(9, lane.oldLane.municipalityCode)
        statement.setLong(10, lane.historyEventOrderNumber)
        statement.addBatch()
      })
    }

    val insertHistoryPosition =
      s"""insert into lane_history_position
         |  values( (?), (?), (?), (?), (?), (?), (?), null)""".stripMargin
    MassQuery.executeBatch(insertHistoryPosition) { statement =>
      oldLanesWithHistory.foreach(lane => {
        statement.setLong(1, lane.positionHistoryId)
        statement.setLong(2, lane.oldLane.sideCode)
        statement.setDouble(3, lane.oldLane.startMeasure)
        statement.setDouble(4, lane.oldLane.endMeasure)
        statement.setString(5, lane.oldLane.linkId)
        statement.setLong(6, lane.oldLane.timeStamp)
        if(lane.oldLane.geomModifiedDate.nonEmpty) {
          statement.setTimestamp(7, new Timestamp(lane.oldLane.geomModifiedDate.get.getMillis))
        } else statement.setNull(7, java.sql.Types.TIMESTAMP)

        statement.addBatch()
      })
    }

    val insertLaneHistoryLink =
      s"""insert into lane_history_link (lane_id, lane_position_id)
         |values ((?), (?))""".stripMargin
    MassQuery.executeBatch(insertLaneHistoryLink) { statement =>
      oldLanesWithHistory.foreach(lane => {
        statement.setLong(1, lane.laneHistoryId)
        statement.setLong(2, lane.positionHistoryId)
        statement.addBatch()
      })
    }

    val insertLaneHistoryAttribute =
      s"""insert into lane_history_attribute
         |values (nextval('primary_key_seq'), (?), (?), (?), (?), (?), (?), (?), (?))
         |""".stripMargin
    MassQuery.executeBatch(insertLaneHistoryAttribute) { statement =>
      oldLanesWithHistory.foreach(lane => {
        lane.oldLane.attributes.filterNot(_.publicId == "lane_code").foreach(attribute => {
          statement.setLong(1, lane.laneHistoryId)
          statement.setString(2, attribute.publicId)
          statement.setString(3, attribute.values.head.value.toString)
          statement.setBoolean(4, false)
          if(attribute.createdDate.nonEmpty) {
            statement.setTimestamp(5, new Timestamp(attribute.createdDate.get.getMillis))
          } else statement.setNull(5, java.sql.Types.TIMESTAMP)
          if(attribute.createdBy.nonEmpty) {
            statement.setString(6, attribute.createdBy.get)
          } else statement.setNull(6, java.sql.Types.VARCHAR)
          if(attribute.modifiedDate.nonEmpty) {
            statement.setTimestamp(7, new Timestamp(attribute.modifiedDate.get.getMillis))
          } else statement.setNull(7, java.sql.Types.TIMESTAMP)
          if(attribute.modifiedBy.nonEmpty) {
            statement.setString(8, attribute.modifiedBy.get)
          } else statement.setNull(8, java.sql.Types.VARCHAR)
          statement.addBatch()
        })
      })
    }

    laneHistoryIds
  }

  def insertHistoryLane(oldLaneId: Long, newLaneId: Option[Long], username: String): Long = {
    val laneHistoryId = Sequences.nextPrimaryKeySeqValue
    val laneHistoryPositionId = Sequences.nextPrimaryKeySeqValue
    val changeEventOrderNumber = Sequences.nextLaneHistoryEventOrderNumberValue
    val newLaneIdToRelate = newLaneId match {
      case Some(id) => id
      case _ => 0
    }

    sqlu"""
        INSERT INTO LANE_HISTORY
          SELECT $laneHistoryId, $newLaneIdToRelate, l.*, current_timestamp, $username, $changeEventOrderNumber FROM LANE l WHERE id = $oldLaneId
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
           SELECT nextval('primary_key_seq'), $laneHistoryId, NAME, VALUE, REQUIRED, CREATED_DATE, CREATED_BY,
                         MODIFIED_DATE, MODIFIED_BY FROM LANE_ATTRIBUTE WHERE LANE_ID = $oldLaneId
      """.execute

    laneHistoryId
  }

  def expireHistoryLane(historyLaneId: Long, username: String): Unit = {
    sqlu"""
           UPDATE LANE_HISTORY
           SET EXPIRED_DATE = current_timestamp,
               EXPIRED_BY = $username,
               VALID_TO = current_timestamp
           WHERE id = $historyLaneId
    """.execute
  }

  def expireHistoryLanes(historyLaneIds: Seq[Long], username: String): Unit = {
    MassQuery.withIds(historyLaneIds.toSet) { idTableName =>
      sqlu"""
           UPDATE LANE_HISTORY
           SET EXPIRED_DATE = current_timestamp,
               EXPIRED_BY = $username,
               VALID_TO = current_timestamp
           WHERE ID IN (SELECT id FROM #$idTableName)
    """.execute
    }
  }

  def convertLaneRowToPersistedLane(lanes: Seq[LaneHistoryRow]): Seq[PersistedHistoryLane] = {
    lanes.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val attributeValues = laneRowToProperty(assetRows)

      id -> PersistedHistoryLane(id = row.id, newId = row.newId, oldId = row.oldId, linkId = row.linkId, sideCode = row.sideCode, laneCode = row.laneCode,
        municipalityCode = row.municipalityCode, startMeasure = row.startMeasure, endMeasure = row.endMeasure,
        createdBy = row.createdBy, createdDateTime = row.createdDate,
        modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired,
        timeStamp = row.timeStamp, geomModifiedDate = row.geomModifiedDate, attributes = attributeValues,
        row.historyCreatedDate, row.historyCreatedBy, row.changeEventOrderNumber)

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
        ).toSeq,
        createdDate = row.value.createdDate,
        createdBy = row.value.createdBy,
        modifiedDate = row.value.modifiedDate,
        modifiedBy = row.value.modifiedBy
      )
    }.toSeq

    props ++ laneCodeAttribute
  }

  def fetchHistoryLanesByLinkIdsAndLaneCode(linkIds: Seq[String], laneCode: Seq[Int], includeExpired: Boolean = false): Seq[PersistedHistoryLane] = {
    fetchAllHistoryLanesByLinkIds(linkIds, includeExpired, laneCode)
  }

  def fetchAllHistoryLanesByLinkIds(linkIds: Seq[String], includeExpired: Boolean = false, laneCodeFilter: Seq[Int] = Seq()): Seq[PersistedHistoryLane] = {
    val filterExpired = s" (l.valid_to > current_timestamp OR l.valid_to IS NULL ) "
    val laneCodeClause = s" l.lane_code in (${laneCodeFilter.mkString(",")})"

    val whereClause = (includeExpired, laneCodeFilter.nonEmpty) match {
      case (false, true) => s" WHERE $filterExpired AND $laneCodeClause ORDER BY l.lane_code ASC"
      case (_, true) => s" WHERE $laneCodeClause ORDER BY l.lane_code ASC"
      case (false, _) => s" WHERE $filterExpired ORDER BY l.lane_code ASC"
      case _ => " ORDER BY l.lane_code ASC"
    }

    MassQuery.withStringIds(linkIds.toSet) { idTableName =>
      val filter = s" JOIN $idTableName i ON i.id = pos.link_id $whereClause"

      getHistoryLanesFilterQuery(withFilter(filter))
    }
  }

  def getHistoryLanesFilterQuery(queryFilter: String => String): Seq[PersistedHistoryLane] = {
    val historyLanes = StaticQuery.queryNA[LaneHistoryRow](queryFilter(query()))(getLaneHistoryAsset).iterator.toSeq

    convertLaneRowToPersistedLane(historyLanes)
  }

  def getHistoryLanesChangedSince(sinceDate: DateTime, untilDate: DateTime, withAdjust: Boolean): Seq[PersistedHistoryLane] = {
    val querySinceDate = s"to_date('${DateTimeSimplifiedFormat.print(sinceDate)}', 'YYYYMMDDHH24MI')"
    val queryUntilDate = s"to_date('${DateTimeSimplifiedFormat.print(untilDate)}', 'YYYYMMDDHH24MI')"

    val withAutoAdjustFilter = if (withAdjust) "" else "and (l.modified_by is null OR l.modified_by != 'generated_in_update')"

    val filter = s"""WHERE ((l.HISTORY_CREATED_DATE > $querySinceDate and l.HISTORY_CREATED_DATE <= $queryUntilDate) $withAutoAdjustFilter)"""

    getHistoryLanesFilterQuery(withFilter(filter))
  }
}
