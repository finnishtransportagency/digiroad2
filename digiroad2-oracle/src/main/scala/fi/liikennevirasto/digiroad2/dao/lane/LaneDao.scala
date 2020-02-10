package fi.liikennevirasto.digiroad2.dao.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.lane.{PersistedLane, _}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.Sequences
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}

import scala.language.implicitConversions


case class LaneRow ( id: Long, linkId: Long, sideCode: Int, value: LanePropertyRow,
                   startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime],
                   modifiedBy: Option[String], modifiedDate: Option[DateTime], expired: Boolean,
                   vvhTimeStamp: Long, municipalityCode: Long, laneCode: Int, geomModifiedDate: Option[DateTime])

case class LanePropertyRow ( publicId: String, propertyValue: Option[Any] )



class LaneDao(val vvhClient: VVHClient, val roadLinkService: RoadLinkService ){

  val MAIN_LANES = Seq(11,21,31)

  implicit val getLightLane = new GetResult[LightLane] {
    def apply(r: PositionedResult) = {
      val expired = r.nextBoolean()
      val value = r.nextInt()
      val startPoint_x = r.nextDouble()
      val startPoint_y = r.nextDouble()
      val endPoint_x = r.nextDouble()
      val endPoint_y = r.nextDouble()
      val geometry = Seq(Point(startPoint_x, startPoint_y), Point(endPoint_x, endPoint_y))
      val sideCode = r.nextInt()

      LightLane(geometry, value, expired, sideCode)
    }
  }

  implicit val getLinearAsset = new GetResult[LaneRow] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
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
      val municipalityCode =  r.nextLong()
      val laneCode =  r.nextInt()

      LaneRow(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate,
            expired, vvhTimeStamp, municipalityCode, laneCode, geomModifiedDate )

    }
  }

  //TODO Rever a query
  def fetchLanes( bounds: BoundingRectangle, linkSource: Option[LinkGeomSource] = None): Seq[LightLane] = {
    val linkGeomCondition = linkSource match {
      case Some(LinkGeomSource.NormalLinkInterface) => s" and pos.link_source = ${LinkGeomSource.NormalLinkInterface.value}"
      case _ => ""
    }

    val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
    sql"""SELECT CASE WHEN l.valid_to <= sysdate THEN 1 ELSE 0 END AS expired,
                1 AS value,
                t.X, t.Y, t2.X, t2.Y, pos.side_code
          FROM lane l
          JOIN lane_link ll ON l.id = ll.lane_id
          JOIN lane_position pos ON  ll.lane_position_id = pos.id
          CROSS JOIN TABLE(SDO_UTIL.GETVERTICES(a.geometry)) t
          CROSS JOIN TABLE(SDO_UTIL.GETVERTICES(a.geometry)) t2
          WHERE l.valid_to IS NULL
          AND #$boundingBoxFilter
          #$linkGeomCondition
     """.as[LightLane].list
  }


  /**
    * Iterates a set of link ids  and returns lanes. Used by LaneService.getByRoadLinks.
    */
  def fetchMainLanesByLinkIds( linkIds: Seq[Long], includeExpired: Boolean = false): Seq[PersistedLane] = {
    val filterExpired = if (includeExpired) "" else " and (l.valid_to > sysdate or l.valid_to is null)"


    val lanes = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""SELECT l.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure,
               l.created_by, l.created_date, l.modified_by, l.modified_date,
               CASE WHEN l.valid_to <= sysdate THEN 1 ELSE 0 END AS expired,
               pos.adjusted_timestamp, pos.modified_date,
               la.name, la.value, l.municipality_code, l.lane_code
          FROM lane l
          JOIN lane_link ll ON l.id = ll.lane_id
          JOIN lane_position pos ON ll.lane_position_id = pos.id
          JOIN lane_attribute la ON la.lane_id = l.id
          JOIN #$idTableName i ON i.id = pos.link_id
          WHERE l.lane_code IN (11, 21, 31)
          #$filterExpired
       """.as[LaneRow].list
    }

    convertLaneRowToPersistedLane (lanes)

  }


  def fetchLanesByLinkIdAndSideCode( linkId: Long, sideCode: Int): Seq[PersistedLane] = {

    val lanes = MassQuery.withIds(Set(linkId)) { idTableName =>
      sql"""SELECT l.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure,
               l.created_by, l.created_date, l.modified_by, l.modified_date,
               CASE WHEN l.valid_to <= sysdate THEN 1 ELSE 0 END AS expired,
               pos.adjusted_timestamp, pos.modified_date,
               la.name, la.value, l.municipality_code, l.lane_code
          FROM lane l
          JOIN lane_link ll ON l.id = ll.lane_id
          JOIN lane_position pos ON ll.lane_position_id = pos.id
          JOIN lane_attribute la ON la.lane_id = l.id
          JOIN #$idTableName i ON i.id = pos.link_id
          WHERE (l.valid_to > sysdate OR l.valid_to IS NULL)
          AND pos.side_code = $sideCode
      """.as[LaneRow].list
    }

    convertLaneRowToPersistedLane (lanes)
  }


  /**
    * Iterates a set of lanes ids. Used by LaneService.getPersistedLanesByIds,
    * laneService.split and laneService.separate.
    */
  def fetchLanesByIds(ids: Set[Long] ): Seq[PersistedLane] = {
    val lanes = MassQuery.withIds(ids) { idTableName =>
      sql"""SELECT l.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure,
               l.created_by, l.created_date, l.modified_by, l.modified_date,
               CASE WHEN l.valid_to <= sysdate THEN 1 ELSE 0 END AS expired,
               pos.adjusted_timestamp, pos.modified_date,
               la.name, la.value, l.municipality_code, l.lane_code
          from lane l
          JOIN lane_link ll on l.id = ll.lane_id
          JOIN lane_position pos on ll.lane_position_id = pos.id
          JOIN lane_attribute la ON la.lane_id = l.id
          JOIN #$idTableName i on i.id = l.id
      """.as[LaneRow].list
    }

    convertLaneRowToPersistedLane (lanes)

  }


  def convertLaneRowToPersistedLane( lanes: Seq[LaneRow]): Seq[PersistedLane] = {

    lanes.groupBy(_.id).map { case (id, assetRows) =>
        val row = assetRows.head
        val attributeValues = LanePropertiesValues(laneRowToProperty(assetRows))

        id -> PersistedLane ( id = row.id, linkId = row.linkId, sideCode = row.sideCode, laneCode = row.laneCode,
          municipalityCode = row.municipalityCode, startMeasure = row.startMeasure, endMeasure = row.endMeasure,
          createdBy = row.createdBy, createdDateTime = row.createdDate,
          modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate,  expired = row.expired,
          vvhTimeStamp = row.vvhTimeStamp, geomModifiedDate = row.geomModifiedDate, attributes = attributeValues)

    }.values.toSeq

  }


  def laneRowToProperty( laneRows: Iterable[LaneRow]): Seq[LaneProperty] = {

    val laneCodeAttribute = Seq(LaneProperty( "lane_code", Seq(LanePropertyValue(laneRows.head.laneCode))))

    val props = laneRows.groupBy(_.value.publicId).map { case (key, rows) =>
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

    val finalAttributes = props ++ laneCodeAttribute
    finalAttributes
  }

  /**
    * Updates validity of lane in db.
    */
  def updateLaneExpiration(id: Long, username: String): Unit = {

    sqlu"""UPDATE LANE
          SET valid_to = sysdate,  expired_date = sysdate, expired_by = $username
          WHERE id = $id
    """.execute

  }


  def updateLane( id: Long, lane: PieceWiseLane, username: String): Option[Long]  = {
    val laneCode =lane.laneAttributes.properties.find( _.publicId == "lane_code" ).head.values

  val laneUpdated =
    sqlu"""UPDATE lane
          SET lane_code =${laneCode.head.value.toString},  modified_date = sysdate, modified_by = $username
          WHERE id = $id
          AND lane_code = ${lane.linkId}
    """.first

    if ( laneUpdated == 1)
      Some(id)
    else
      None
  }


  def updateLanePosition (newIncomeLane: PersistedLane): Unit ={
    sqlu"""UPDATE LANE_POSITION
          SET start_measure = ${newIncomeLane.startMeasure}, end_measure = ${newIncomeLane.endMeasure},
              modified_date = sysdate, adjusted_timestamp = ${newIncomeLane.vvhTimeStamp}
          WHERE link_id = ${newIncomeLane.linkId}
    """.execute
  }


  def createLanePosition(persistedLane: PersistedLane, username: String): Long = {
    val lanePositionId = Sequences.nextPrimaryKeySeqValue

    sqlu"""INSERT INTO LANE_POSITION (id, side_code, start_measure, end_measure, link_id, adjusted_timestamp)
          VALUES ( $lanePositionId, ${persistedLane.sideCode}, ${persistedLane.startMeasure}, ${persistedLane.endMeasure},
              ${persistedLane.linkId}, ${persistedLane.vvhTimeStamp})
    """.execute

    lanePositionId
  }

  def createLanePositionRelation (laneId: Long, lanePositionId: Long): Unit = {
    sqlu"""INSERT INTO LANE_LINK (lane_id, lane_position_id)
          VALUES ($laneId, $lanePositionId )
    """.execute
  }

  def createLane ( newIncomeLane: PersistedLane, username: String): Long = {

    val laneId = Sequences.nextPrimaryKeySeqValue

    sqlu"""INSERT INTO LANE (id, lane_code, created_date, created_by, valid_from, municipality_code)
          VALUES ($laneId, ${newIncomeLane.laneCode}, sysdate, ${username}, sysdate, ${newIncomeLane.municipalityCode} )
    """.execute

    laneId
  }


  def insertLaneAttributes(laneId: Long, laneProp: LaneProperty, username: String): Long = {

    val laneAttributeId = Sequences.nextPrimaryKeySeqValue
    val laneAttrValue = if( laneProp.values.isEmpty ) ""
                        else laneProp.values.head.value.toString

    sqlu"""INSERT INTO lane_attribute (id, lane_id, name, value, created_date, created_by)
            VALUES( $laneAttributeId, $laneId, ${laneProp.publicId}, ${laneAttrValue}, sysdate, $username)
    """.execute

    laneAttributeId
  }


  def deleteEntryLane( laneId: Long ): Unit = {

    val laneCode = sql"""SELECT lane_code FROM LANE WHERE id = $laneId""".as[Long].first

    if ( MAIN_LANES.contains(laneCode) )
      throw new IllegalArgumentException("Cannot Delete a main lane!")

    val lanePositionId = sql"""SELECT lane_position_id FROM LANE_LINK WHERE lane_id = $laneId""".as[Long].first

    sqlu"""DELETE FROM LANE_ATTRIBUTE WHERE lane_id = $laneId""".execute
    sqlu"""DELETE FROM LANE_LINK WHERE lane_id = $laneId""".execute
    sqlu"""DELETE FROM LANE WHERE id = $laneId""".execute
    sqlu"""DELETE FROM LANE_POSITION WHERE id = $lanePositionId""".execute
  }


  def updateEntryLane( lane: PersistedLane, username: String ): Long = {

    updateLane(lane, username)
    updateLanePosition(lane, username)

    lane.attributes match {
      case props: LanePropertiesValues =>
        props.properties.filterNot( _.publicId == "lane_code" )
                        .foreach( attr => updateLaneAttributes(lane.id, attr, username) )

      case _ => None
    }

    lane.id
  }

  def updateLane(lane: PersistedLane, username: String ): Unit = {

    val oldLaneCode = sql"""Select lane_code FROM LANE WHERE id = ${lane.id}""".as[Int].first

    if ( MAIN_LANES.contains(oldLaneCode) && oldLaneCode != lane.laneCode )
      throw new IllegalArgumentException("Cannot change the code of main lane!")

    sqlu"""UPDATE LANE
          SET LANE_CODE = ${lane.laneCode}, MODIFIED_BY = $username, MODIFIED_DATE = sysdate, MUNICIPALITY_CODE = ${lane.municipalityCode}
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
    sqlu"""UPDATE LANE_ATTRIBUTE
           SET VALUE = ${props.values.head.value.toString}, MODIFIED_BY = $username, MODIFIED_DATE = sysdate
          WHERE LANE_ID =  $laneId AND NAME = ${props.publicId}
    """.execute
  }

}
