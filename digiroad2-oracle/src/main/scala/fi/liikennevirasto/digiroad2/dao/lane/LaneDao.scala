package fi.liikennevirasto.digiroad2.dao.lane

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.postgis.MassQuery
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimeSimplifiedFormat
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.lane.LaneNumberOneDigit.MainLane
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

import scala.language.implicitConversions


case class LaneRow(id: Long, linkId: String, sideCode: Int, value: LanePropertyRow,
                   startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime],
                   modifiedBy: Option[String], modifiedDate: Option[DateTime], expiredBy: Option[String], expiredDate: Option[DateTime],
                   expired: Boolean, timeStamp: Long, municipalityCode: Long, laneCode: Int, geomModifiedDate: Option[DateTime])

case class LanePropertyRow(publicId: String, propertyValue: Option[Any])
case class NewLaneWithIds(laneId: Long, positionId: Long, lane: PersistedLane)


class LaneDao(){

  implicit val getLightLane = new GetResult[LightLane] {
    def apply(r: PositionedResult) = {
      val expired = r.nextBoolean()
      val value = r.nextInt()
      val sideCode = r.nextInt()

      LightLane(value, expired, sideCode)
    }
  }

  implicit val getLaneAsset: GetResult[LaneRow] = new GetResult[LaneRow] {
    def apply(r: PositionedResult) : LaneRow = {
      val id = r.nextLong()
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
      val value = LanePropertyRow(atrrName, atrrValue)
      val municipalityCode =  r.nextLong()
      val laneCode =  r.nextInt()
      val expiredBy = r.nextStringOption()
      val expiredDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      LaneRow(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate,
        expiredBy, expiredDate, expired, timeStamp, municipalityCode, laneCode, geomModifiedDate)
    }
  }

  def fetchLanes( linkSource: Option[LinkGeomSource] = None): Seq[LightLane] = {
    val linkGeomCondition = linkSource match {
      case Some(LinkGeomSource.NormalLinkInterface) => s" AND pos.link_source = ${LinkGeomSource.NormalLinkInterface.value}"
      case _ => ""
    }

    sql"""SELECT CASE WHEN l.valid_to <= current_timestamp THEN 1 ELSE 0 END AS expired,
                1 AS value,
                pos.side_code
          FROM lane l
          JOIN lane_link ll ON l.id = ll.lane_id
          JOIN lane_position pos ON  ll.lane_position_id = pos.id
          WHERE l.valid_to IS NULL
          #$linkGeomCondition
     """.as[LightLane].list
  }

  def fetchLanesByMunicipality(municipalityCode: Int): Seq[PersistedLane] = {
    val filter = s""" WHERE l.municipality_code = $municipalityCode"""
    getLanesFilterQuery(withFilter(filter))
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  private def query(): String = {
    """SELECT l.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure,
    l.created_by, l.created_date, l.modified_by, l.modified_date,
    CASE WHEN l.valid_to <= current_timestamp THEN 1 ELSE 0 END AS expired,
    pos.adjusted_timestamp, pos.modified_date,
    la.name, la.value, l.municipality_code, l.lane_code,
    l.expired_by, l.expired_date
    FROM lane l
       JOIN lane_link ll ON l.id = ll.lane_id
       JOIN lane_position pos ON ll.lane_position_id = pos.id
       JOIN lane_attribute la ON la.lane_id = l.id """
  }

  def getLanesChangedSince(sinceDate: DateTime, untilDate: DateTime, withAdjust: Boolean): Seq[PersistedLane] = {
    val querySinceDate = s"to_date('${DateTimeSimplifiedFormat.print(sinceDate)}', 'YYYYMMDDHH24MI')"
    val queryUntilDate = s"to_date('${DateTimeSimplifiedFormat.print(untilDate)}', 'YYYYMMDDHH24MI')"

    val withAutoAdjustFilter = if (withAdjust) "" else "and (l.modified_by is null OR l.modified_by != 'vvh_generated')"

    val query = s"""
                SELECT id, link_id, side_code, start_measure, end_measure, created_by, created_date, modified_by,
                modified_date, expired, adjusted_timestamp, "pos_modified_date", name, value, municipality_code,
                lane_code, expired_by, expired_date
                FROM (SELECT l.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, l.created_by,
                l.created_date, l.modified_by, l.modified_date, CASE WHEN l.valid_to <= current_timestamp THEN 1 ELSE 0 END AS expired,
                pos.adjusted_timestamp, pos.modified_date "pos_modified_date", la.name, la.value, l.municipality_code, l.lane_code,
                l.expired_by, l.expired_date
                FROM lane l
                JOIN lane_link ll ON l.id = ll.lane_id
                JOIN lane_position pos ON ll.lane_position_id = pos.id
                JOIN lane_attribute la ON la.lane_id = l.id
                WHERE ((l.modified_date > $querySinceDate and l.modified_date <= $queryUntilDate) or
                (l.created_date > $querySinceDate and l.created_date <= $queryUntilDate))
                $withAutoAdjustFilter) derivedLane"""

    val lanesRow = StaticQuery.queryNA[LaneRow](query)(getLaneAsset).iterator.toSeq
    convertLaneRowToPersistedLane(lanesRow)
  }

  /**
    * Iterates a set of link ids  and returns lanes. Used by LaneService.getByRoadLinks.
    */
  def fetchLanesByLinkIds(linkIds: Seq[String], includeExpired: Boolean = false, mainLanes: Boolean = false): Seq[PersistedLane] = {
    val lanesCodeToFilter =
      if (mainLanes)
        Seq(MainLane.laneCode)
      else
        Seq()

    fetchAllLanesByLinkIds(linkIds, includeExpired, lanesCodeToFilter)
  }

  def getLanesFilterQuery( queryFilter: String => String ): Seq[PersistedLane] = {
    val lanes = StaticQuery.queryNA[LaneRow](queryFilter(query()))(getLaneAsset).iterator.toSeq

    convertLaneRowToPersistedLane(lanes)
  }

  def fetchLanesByLinkIdsAndLaneCode(linkIds: Seq[String], laneCode: Seq[Int] = Seq(), includeExpired: Boolean = false): Seq[PersistedLane] = {
    fetchAllLanesByLinkIds(linkIds, includeExpired, laneCode)
  }

  def fetchAllLanesByLinkIds(linkIds: Seq[String], includeExpired: Boolean = false, laneCodeFilter: Seq[Int] = Seq()): Seq[PersistedLane] = {
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

      getLanesFilterQuery(withFilter(filter))
    }
  }


  def fetchLanesByLinkIdAndSideCode( linkId: String, sideCode: Int): Seq[PersistedLane] = {

    MassQuery.withStringIds(Set(linkId)) { idTableName =>
      val filter = s""" JOIN $idTableName i ON i.id = pos.link_id
                    WHERE (l.valid_to > current_timestamp OR l.valid_to IS NULL)
                    AND pos.side_code = $sideCode """

      getLanesFilterQuery( withFilter(filter))
    }
  }


  /**
    * Iterates a set of lanes ids. Used by LaneService.getPersistedLanesByIds,
    * laneService.split and laneService.separate.
    */
  def fetchLanesByIds(ids: Set[Long] ): Seq[PersistedLane] = {

    MassQuery.withIds(ids) { idTableName =>
      val filter = s" JOIN $idTableName i ON i.id = l.id "

      getLanesFilterQuery( withFilter(filter))
    }
  }


  def convertLaneRowToPersistedLane(lanes: Seq[LaneRow]): Seq[PersistedLane] = {
    lanes.groupBy(_.id).map { case (id, assetRows) =>
        val row = assetRows.head
        val attributeValues = laneRowToProperty(assetRows)

        id -> PersistedLane (id = row.id, linkId = row.linkId, sideCode = row.sideCode, laneCode = row.laneCode,
          municipalityCode = row.municipalityCode, startMeasure = row.startMeasure, endMeasure = row.endMeasure,
          createdBy = row.createdBy, createdDateTime = row.createdDate,
          modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate,
          expiredBy = row.expiredBy, expiredDateTime = row.expiredDate, expired = row.expired,
          timeStamp = row.timeStamp, geomModifiedDate = row.geomModifiedDate, attributes = attributeValues)

    }.values.toSeq
  }


  def laneRowToProperty( laneRows: Iterable[LaneRow]): Seq[LaneProperty] = {

    val laneCodeAttribute = Seq(LaneProperty( "lane_code", Seq(LanePropertyValue(laneRows.head.laneCode))))

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

  def updateLane( id: Long, lane: PieceWiseLane, username: String): Unit = {
    val laneCode =lane.laneAttributes.find( _.publicId == "lane_code" ).head.values

    sqlu"""UPDATE lane
          SET lane_code =${laneCode.head.value.toString},  modified_date = current_timestamp, modified_by = $username
          WHERE id = $id
          AND lane_code = ${lane.linkId}
    """.execute
  }


  def updateLanePosition (newIncomeLane: PersistedLane): Unit ={
    sqlu"""UPDATE LANE_POSITION
          SET start_measure = ${newIncomeLane.startMeasure}, end_measure = ${newIncomeLane.endMeasure},
              modified_date = current_timestamp, adjusted_timestamp = ${newIncomeLane.timeStamp}
          WHERE link_id = ${newIncomeLane.linkId}
    """.execute
  }


  def createLane ( newIncomeLane: PersistedLane, username: String): Long = {

    val laneId = Sequences.nextPrimaryKeySeqValue
    val lanePositionId = Sequences.nextPrimaryKeySeqValue

    sqlu"""
        INSERT  INTO LANE (id, lane_code, created_date, created_by, municipality_code)
          VALUES ($laneId, ${newIncomeLane.laneCode}, current_timestamp, $username, ${newIncomeLane.municipalityCode} );
         INSERT INTO LANE_POSITION (id, side_code, start_measure, end_measure, link_id, adjusted_timestamp)
          VALUES ( $lanePositionId, ${newIncomeLane.sideCode}, ${newIncomeLane.startMeasure}, ${newIncomeLane.endMeasure},
                   ${newIncomeLane.linkId}, ${newIncomeLane.timeStamp});
        INSERT INTO LANE_LINK (lane_id, lane_position_id)VALUES ($laneId, $lanePositionId );
      """.execute

    laneId
  }

  def createMultipleLanes(newLanes: Seq[PersistedLane], username: String): Seq[PersistedLane] = {
    val laneIds = Sequences.nextPrimaryKeySeqValues(newLanes.size)
    val lanePositionIds = Sequences.nextPrimaryKeySeqValues(newLanes.size)
    val lanesToCreate = newLanes.zipWithIndex.map{ case (lane, index) =>
      NewLaneWithIds(laneIds(index), lanePositionIds(index), lane)
    }

    val insertLane =
      s"""insert into lane (id, lane_code, created_date, created_by, municipality_code)
         |values ((?), (?), current_timestamp, '$username', (?))""".stripMargin
    val createdLanes = MassQuery.executeBatch(insertLane) { statement =>
      lanesToCreate.map { newLane =>
        statement.setLong(1, newLane.laneId)
        statement.setInt(2, newLane.lane.laneCode)
        statement.setLong(3, newLane.lane.municipalityCode)
        statement.addBatch()
        newLane.lane.copy(id = newLane.laneId)
      }
    }

    val insertLanePosition =
      s"""insert into lane_position (id, side_code, start_measure, end_measure, link_id, adjusted_timestamp)
         |values ((?), (?), (?), (?), (?), (?))""".stripMargin
    MassQuery.executeBatch(insertLanePosition) { statement =>
      lanesToCreate.foreach { newLane =>
        statement.setLong(1, newLane.positionId)
        statement.setInt(2, newLane.lane.sideCode)
        statement.setDouble(3, newLane.lane.startMeasure)
        statement.setDouble(4, newLane.lane.endMeasure)
        statement.setString(5, newLane.lane.linkId)
        statement.setLong(6, newLane.lane.timeStamp)
        statement.addBatch()
      }
    }

    val insertLaneLink =
      s"""insert into lane_link (lane_id, lane_position_id)
         |values ((?), (?))""".stripMargin
    MassQuery.executeBatch(insertLaneLink) { statement =>
      lanesToCreate.foreach { newLane =>
        statement.setLong(1, newLane.laneId)
        statement.setLong(2, newLane.positionId)
        statement.addBatch()
      }
    }
    createdLanes
  }

  def updateLaneAttributesForMultipleLanes(lanes: Seq[PersistedLane], username: String): Unit = {
    val updateAttribute =
      s"""UPDATE LANE_ATTRIBUTE SET VALUE = (?), MODIFIED_BY = (?),
         | MODIFIED_DATE = current_timestamp WHERE LANE_ID = (?) AND NAME = (?)""".stripMargin

    MassQuery.executeBatch(updateAttribute) { statement =>
      lanes.foreach( lane => {
        lane.attributes match {
          case props: Seq[LaneProperty] =>
            props.filterNot(_.publicId == "lane_code")
              .foreach(attr => {
                val attrValue = if (attr.values.nonEmpty) attr.values.head.value.toString else ""
                statement.setString(1, attrValue)
                statement.setString(2, username)
                statement.setLong(3, lane.id)
                statement.setString(4, attr.publicId)
                statement.addBatch()
              })
        }
      })
    }

  }

  def insertLaneAttributesForMultipleLanes(newLanes: Seq[PersistedLane], username: String): Unit = {
    val insertAttribute =
      s"""insert into lane_attribute (id, lane_id, name, value, created_date, created_by)
         |values (nextval('primary_key_seq'), (?), (?), (?), current_timestamp, '$username')""".stripMargin

    MassQuery.executeBatch(insertAttribute) { statement =>
      newLanes.foreach( lane => {
        lane.attributes match {
          case props: Seq[LaneProperty] =>
            props.filterNot(_.publicId == "lane_code")
                 .foreach(attr => {
                   val attrValue = if (attr.values.nonEmpty) attr.values.head.value.toString else ""
                   statement.setLong(1, lane.id)
                   statement.setString(2, attr.publicId)
                   statement.setString(3, attrValue)
                   statement.addBatch()
                 })
        }
      })
    }
  }

  def insertLaneAttributes(laneId: Long, laneProp: LaneProperty, username: String): Long = {

    val laneAttributeId = Sequences.nextPrimaryKeySeqValue
    val laneAttrValue = if( laneProp.values.isEmpty ) ""
                        else laneProp.values.head.value.toString

    sqlu"""INSERT INTO lane_attribute (id, lane_id, name, value, created_date, created_by)
          VALUES( $laneAttributeId, $laneId, ${laneProp.publicId}, $laneAttrValue, current_timestamp, $username)
    """.execute

    laneAttributeId
  }


  def deleteEntryLane(laneId: Long): Unit = {
    val lanePositionId = sql"""SELECT lane_position_id FROM LANE_LINK WHERE lane_id = $laneId""".as[Int].first

    sqlu"""DELETE FROM LANE_ATTRIBUTE WHERE lane_id = $laneId""".execute
    sqlu"""DELETE FROM LANE_LINK WHERE lane_id = $laneId""".execute
    sqlu"""DELETE FROM LANE WHERE id = $laneId""".execute
    sqlu"""DELETE FROM LANE_POSITION WHERE id = $lanePositionId""".execute
  }

  def deleteEntryLanes(laneIds: Seq[Long]): Unit = {
    MassQuery.withIds(laneIds.toSet) { idTableName =>
      val lanePositionIds = sql"""SELECT lane_position_id FROM LANE_LINK WHERE LANE_ID IN (SELECT id FROM #$idTableName)""".as[Long].list

      sqlu"""DELETE FROM LANE_ATTRIBUTE WHERE lane_id IN ( SELECT id FROM #$idTableName )""".execute
      sqlu"""DELETE FROM LANE_LINK WHERE lane_id IN ( SELECT id FROM #$idTableName )""".execute
      sqlu"""DELETE FROM LANE WHERE id IN ( SELECT id FROM #$idTableName )""".execute

      MassQuery.withIds(lanePositionIds.toSet) { lpIdTableName =>
        sqlu"""DELETE FROM LANE_POSITION WHERE id IN (SELECT id FROM #$lpIdTableName)""".execute
      }
    }
  }


  def updateEntryLane( lane: PersistedLane, username: String ): Long = {

    updateLane(lane, username)
    updateLanePosition(lane, username)

    lane.attributes match {
      case props: Seq[LaneProperty] =>
        props
          .filterNot( _.publicId == "lane_code" )
          .foreach { attr =>
            if (attr.values.isEmpty) deleteLaneAttribute(lane.id, attr)
            else updateLaneAttributes(lane.id, attr, username)
          }
      case _ => None
    }

    lane.id
  }

  def updateLane(lane: PersistedLane, username: String ): Unit = {

    sqlu"""UPDATE LANE
          SET LANE_CODE = ${lane.laneCode}, MODIFIED_BY = $username, MODIFIED_DATE = current_timestamp, MUNICIPALITY_CODE = ${lane.municipalityCode}
          WHERE id = ${lane.id}
    """.execute
  }

  def updateLanePosition(lane: PersistedLane, username: String ): Unit = {
    sqlu"""UPDATE LANE_POSITION
          SET SIDE_CODE = ${lane.sideCode}, START_MEASURE = ${lane.startMeasure}, END_MEASURE = ${lane.endMeasure}, LINK_ID = ${lane.linkId}
          WHERE ID = (SELECT LANE_POSITION_ID FROM LANE_LINK WHERE LANE_ID = ${lane.id})
     """.execute
  }

  def updateLaneAttributes(laneId: Long, props: LaneProperty, username: String ): Unit = {
    val finalValue = props.values.head.value.toString
    val laneQuery = sql" SELECT LANE_ID FROM LANE_ATTRIBUTE WHERE name = ${props.publicId} AND lane_id = $laneId ".as[Long].firstOption

    if (!laneQuery.isEmpty) {
      sqlu"""
          UPDATE LANE_ATTRIBUTE SET VALUE = $finalValue, MODIFIED_BY = $username, MODIFIED_DATE = current_timestamp
            WHERE LANE_ID =  $laneId
            AND NAME = ${props.publicId}
       """.execute
    }else {
      sqlu"""
          INSERT INTO LANE_ATTRIBUTE (id, lane_id, name, value, created_date, created_by)
            VALUES( ${Sequences.nextPrimaryKeySeqValue}, $laneId, ${props.publicId}, $finalValue, current_timestamp, $username)
       """.execute
    }

  }

  def deleteLaneAttribute(laneId: Long, props: LaneProperty): Unit = {
    sqlu"""
      delete from LANE_ATTRIBUTE where LANE_ID = $laneId and NAME = ${props.publicId}
    """.execute
  }

  def updateLaneModifiedFields (laneId: Long, username: String): Unit = {
    sqlu"""
      UPDATE LANE
      SET modified_by = $username, modified_date = current_timestamp
      WHERE id = $laneId
    """.execute
  }

  def updateSideCode(id: Long, newSideCode: Int, username: String, timeStamp: Long  = LinearAssetUtils.createTimeStamp()): Unit = {
    sqlu"""UPDATE LANE_POSITION
           SET  SIDE_CODE = $newSideCode,  modified_date = current_timestamp, adjusted_timestamp = $timeStamp
          WHERE ID = (SELECT LANE_POSITION_ID FROM LANE_LINK WHERE LANE_ID = $id )
     """.execute

    updateLaneModifiedFields(id, username)
  }

  def getAllLinkIdsInLanePosition(): List[String] = {
    sql"""SELECT DISTINCT LINK_ID FROM LANE_POSITION""".as[String].list
  }


  def expireLanesByLinkId(linkIds: Set[String], username: String) = {
    MassQuery.withStringIds(linkIds) { idTableName =>
      sqlu"""
         UPDATE LANE SET
           VALID_TO = current_timestamp,
           EXPIRED_DATE = current_timestamp,
           EXPIRED_BY = $username
         WHERE
           ID IN (SELECT ll.LANE_ID
                FROM LANE_LINK ll
                JOIN LANE_POSITION lp ON lp.ID = ll.LANE_POSITION_ID
                JOIN #$idTableName i ON i.id = lp.LINK_ID)
      """.execute

    }
  }

  def expireAdditionalLanes(username: String): Unit = {
    sqlu"""
       UPDATE LANE SET
         VALID_TO = current_timestamp,
         EXPIRED_DATE = current_timestamp,
         EXPIRED_BY = $username
       WHERE LANE_CODE NOT IN (1, 11, 21, 31)
    """.execute
  }

}
