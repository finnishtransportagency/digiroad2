package fi.liikennevirasto.viite.dao

import java.sql.Timestamp

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing, Unknown}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.CalibrationCode._
import fi.liikennevirasto.viite.model.Anomaly
import fi.liikennevirasto.viite.process.RoadAddressFiller.LRMValueAdjustment
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, StaticQuery => Q}


//JATKUVUUS (1 = Tien loppu, 2 = epäjatkuva (esim. vt9 välillä Akaa-Tampere), 3 = ELY:n raja, 4 = Lievä epäjatkuvuus (esim kiertoliittymä), 5 = jatkuva)

sealed trait Discontinuity {
  def value: Int
  def description: String
}
object Discontinuity {
  val values = Set(EndOfRoad, Discontinuous, ChangingELYCode, MinorDiscontinuity, Continuous)

  def apply(intValue: Int): Discontinuity = {
    values.find(_.value == intValue).getOrElse(Continuous)
  }

  case object EndOfRoad extends Discontinuity { def value = 1; def description="Tien loppu" }
  case object Discontinuous extends Discontinuity { def value = 2 ; def description = "Epäjatkuva"}
  case object ChangingELYCode extends Discontinuity { def value = 3 ; def description = "ELY:n raja"}
  case object MinorDiscontinuity extends Discontinuity { def value = 4 ; def description= "Lievä epäjatkuvuus" }
  case object Continuous extends Discontinuity { def value = 5 ; def description = "Jatkuva"}
}

sealed trait CalibrationCode {
  def value: Int
}
object CalibrationCode {
  val values = Set(No, AtEnd, AtBeginning, AtBoth)

  def apply(intValue: Int): CalibrationCode = {
    values.find(_.value == intValue).getOrElse(No)
  }

  def getFromAddress(roadAddress: RoadAddress): CalibrationCode = {
    (roadAddress.calibrationPoints._1.isEmpty, roadAddress.calibrationPoints._2.isEmpty) match {
      case (true, true)   => No
      case (true, false)  => AtEnd
      case (false, true)  => AtBeginning
      case (false, false) => AtBoth
    }
  }

  case object No extends CalibrationCode { def value = 0 }
  case object AtEnd extends CalibrationCode { def value = 1 }
  case object AtBeginning extends CalibrationCode { def value = 2 }
  case object AtBoth extends CalibrationCode { def value = 3 }
}
case class CalibrationPoint(linkId: Long, segmentMValue: Double, addressMValue: Long)

case class RoadAddress(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, modifiedBy: Option[String] = None, lrmPositionId : Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                       calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = (None, None), floating: Boolean = false,
                       geom: Seq[Point])

case class RoadAddressProject(id: Long, status: Long, name: String, createdBy: String, createdDate: DateTime, modifiedBy:String, startDate: DateTime, dateModified: DateTime, additionalInfo :String, roadNumber: Long, startPart: Long, endPart: Long)

case class RoadAddressProjectLink(id : Long, projectId: Long, roadType: Long, discontinuityType: Discontinuity, roadNumber: Long, roadPartNumber: Long, startAddrM: Long, endAddrM: Long, lrmPositionId: Long, cratedBy: String, modifiedBy: String, linkId: Long, length: Double)

case class RoadAddressProjectFormLine(projectId: Long, roadNumber: Long, roadPartNumber: Long, RoadLength: Long, ely : Long, discontinuity: String)

case class RoadAddressCreator(administrativeClass : String, anomaly: Long, calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = (None, None),
                              constructionType: Long, discontinuity: Int, elyCode: Long, endAddressM : Long, endDate: String, endMValue: Double,
                              id : Long, linkId: Long, linkType: Long, mmlId: Long, modifiedAt : String, modifiedBy: String, municipalityCode : Long, points: Seq[Point],
                              roadClass : Long, roadLinkType: Long, roadNameFi: String, roadNumber : Long, roadPartNumber: Long,
                              roadType: String, segmentId : Long, sideCode : Int, startAddressM : Long, startDate:String, startMValue: Long, trackCode : Int)

case class MissingRoadAddress(linkId: Long, startAddrMValue: Option[Long], endAddrMValue: Option[Long],
                              roadType: RoadType, roadNumber: Option[Long], roadPartNumber: Option[Long],
                              startMValue: Option[Double], endMValue: Option[Double], anomaly: Anomaly)

object RoadAddressDAO {

  def fetchByBoundingBox(boundingRectangle: BoundingRectangle, fetchOnlyFloating: Boolean): (Seq[RoadAddress], Seq[MissingRoadAddress]) = {
    val extendedBoundingRectangle = BoundingRectangle(boundingRectangle.leftBottom + boundingRectangle.diagonal.scale(.15),
      boundingRectangle.rightTop - boundingRectangle.diagonal.scale(.15))
    val filter = OracleDatabase.boundingBoxFilter(extendedBoundingRectangle, "geometry")

    val floatingFilter = fetchOnlyFloating match {
      case true => " and ra.floating = 1"
      case false => ""
    }

    val query = s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating,
        (SELECT X FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 1) as X,
        (SELECT Y FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 1) as Y,
        (SELECT X FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 2) as X2,
        (SELECT Y FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 2) as Y2
        from road_address ra
        join lrm_position pos on ra.lrm_position_id = pos.id
        where $filter $floatingFilter and
          (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
    (queryList(query), Seq())
  }


  private def logger = LoggerFactory.getLogger(getClass)

  private def calibrations(calibrationCode: CalibrationCode, linkId: Long, startMValue: Double, endMValue: Double,
                           startAddrMValue: Long, endAddrMValue: Long, sideCode: SideCode): (Option[CalibrationPoint], Option[CalibrationPoint]) = {
    sideCode match {
      case BothDirections => (None, None) // Invalid choice
      case TowardsDigitizing => calibrations(calibrationCode, linkId, 0.0, endMValue-startMValue, startAddrMValue, endAddrMValue)
      case AgainstDigitizing => calibrations(calibrationCode, linkId, endMValue-startMValue, 0.0, startAddrMValue, endAddrMValue)
      case Unknown => (None, None)  // Invalid choice
    }
  }

  private def calibrations(calibrationCode: CalibrationCode, linkId: Long, segmentStartMValue: Double, segmentEndMValue: Double,
                           startAddrMValue: Long, endAddrMValue: Long): (Option[CalibrationPoint], Option[CalibrationPoint]) = {
    calibrationCode match {
      case No => (None, None)
      case AtEnd => (None, Some(CalibrationPoint(linkId, segmentEndMValue, endAddrMValue)))
      case AtBeginning => (Some(CalibrationPoint(linkId, segmentStartMValue, startAddrMValue)), None)
      case AtBoth => (Some(CalibrationPoint(linkId, segmentStartMValue, startAddrMValue)),
        Some(CalibrationPoint(linkId, segmentEndMValue, endAddrMValue)))
    }
  }
  val formatter = ISODateTimeFormat.dateOptionalTimeParser()

  def dateTimeParse(string: String) = {
    formatter.parseDateTime(string)
  }

  val dateFormatter = ISODateTimeFormat.basicDate()

  def optDateTimeParse(string: String): Option[DateTime] = {
    try {
      if (string==null || string == "")
        None
      else
        Some(DateTime.parse(string, formatter))
    } catch {
      case ex: Exception => None
    }
  }

  def fetchByLinkId(linkIds: Set[Long], includeFloating: Boolean = false, includeHistory: Boolean = true): List[RoadAddress] = {
    if (linkIds.size > 1000) {
      return fetchByLinkIdMassQuery(linkIds, includeFloating, includeHistory)
    }
    val linkIdString = linkIds.mkString(",")
    val where = linkIds.isEmpty match {
      case true => return List()
      case false => s""" where pos.link_id in ($linkIdString)"""
    }
    val floating = if (!includeFloating)
      "AND floating='0'"
    else
      ""
    val history = if (!includeHistory)
      "AND ra.end_date is null"
    else
      ""

    val query =
      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        $where $floating $history and t.id < t2.id and
          (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
    queryList(query)
  }

  private def queryList(query: String) = {
    val tuples = Q.queryNA[(Long, Long, Long, Int, Int, Long, Long, Long, Long, Double, Double, Int,
      Option[DateTime], Option[DateTime], Option[String], Option[DateTime], Int, Boolean, Double, Double, Double, Double)](query).list
    tuples.map {
      case (id, roadNumber, roadPartNumber, track, discontinuity, startAddrMValue, endAddrMValue, lrmPositionId,
      linkId, startMValue, endMValue, sideCode, startDate, endDate, createdBy, createdDate, calibrationCode, floating, x, y, x2, y2) =>
        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), Discontinuity.apply(discontinuity),
          startAddrMValue, endAddrMValue, startDate, endDate, createdBy, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode),
          calibrations(CalibrationCode.apply(calibrationCode), linkId, startMValue, endMValue, startAddrMValue,
            endAddrMValue, SideCode.apply(sideCode)), floating, Seq(Point(x,y), Point(x2,y2)))
    }
  }

  def fetchPartsByRoadNumbers(boundingRectangle: BoundingRectangle, roadNumbers: Seq[(Int, Int)], coarse: Boolean = false): List[RoadAddress] = {
    val geomFilter = OracleDatabase.boundingBoxFilter(boundingRectangle, "geometry")
    val filter = roadNumbers.map(n => "road_number >= " + n._1 + " and road_number <= " + n._2)
      .mkString("(", ") OR (", ")")
    val where = roadNumbers.isEmpty match {
      case true => return List()
      case false => s""" where track_code in (0,1) AND $filter"""
    }
    val coarseWhere = coarse match {
      case true => " AND calibration_points != 0"
      case false => ""
    }
    val query =
      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        $where AND $geomFilter $coarseWhere AND floating='0' and t.id < t2.id and
          (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
    queryList(query)
  }

  def fetchByLinkIdMassQuery(linkIds: Set[Long], includeFloating: Boolean = false, includeHistory: Boolean = true): List[RoadAddress] = {
    MassQuery.withIds(linkIds) {
      idTableName =>
        val floating = if (!includeFloating)
          "AND floating='0'"
        else
          ""
        val history = if (!includeHistory)
          "AND ra.end_date is null"
        else
          ""
        val query =
          s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        join $idTableName i on i.id = pos.link_id
        where t.id < t2.id $floating $history and
          (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
        queryList(query)
    }
  }

  def fetchByRoadPart(roadNumber: Long, roadPartNumber: Long) = {
    val query =
      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        where floating = '0' and road_number = $roadNumber AND road_part_number = $roadPartNumber and t.id < t2.id AND
        (valid_to IS NULL OR valid_to >= sysdate) AND (valid_from IS NULL OR valid_from <= sysdate)
        ORDER BY road_number, road_part_number, track_code, start_addr_m
      """
    queryList(query)
  }

  def fetchMultiSegmentLinkIds(roadNumber: Long) = {
    val query =
      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id,pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        where link_id in (
        select pos.link_id
        from road_address ra
        join lrm_position pos on ra.lrm_position_id = pos.id
        where road_number = $roadNumber AND (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
        GROUP BY link_id
        HAVING COUNT(*) > 1) AND
        road_number = $roadNumber AND (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
    queryList(query)
  }
  def fetchNextRoadNumber(current: Int) = {
    val query =
      s"""
          SELECT * FROM (
            SELECT ra.road_number
            FROM road_address ra
            WHERE floating = '0' and road_number > $current AND (end_date < sysdate OR end_date IS NULL)
            ORDER BY road_number ASC
          ) WHERE ROWNUM < 2
      """
    Q.queryNA[Int](query).firstOption
  }

  def fetchNextRoadPartNumber(roadNumber: Int, current: Int) = {
    val query =
      s"""
          SELECT * FROM (
            SELECT ra.road_part_number
            FROM road_address ra
            WHERE floating = '0' and road_number = $roadNumber  AND road_part_number > $current AND (end_date < sysdate OR end_date IS NULL)
            ORDER BY road_part_number ASC
          ) WHERE ROWNUM < 2
      """
    Q.queryNA[Int](query).firstOption
  }

  def update(roadAddress: RoadAddress) : Unit = {
    update(roadAddress, None)
  }

  def toTimeStamp(dateTime: Option[DateTime]) = {
    dateTime.map(dt => new Timestamp(dt.getMillis))
  }

  def update(roadAddress: RoadAddress, geometry: Option[Seq[Point]]) : Unit = {
    if (geometry.isEmpty)
      updateWithoutGeometry(roadAddress)
    else {
      val startTS = toTimeStamp(roadAddress.startDate)
      val endTS = toTimeStamp(roadAddress.endDate)
      val first = geometry.get.head
      val last = geometry.get.last
      val (x1, y1, z1, x2, y2, z2) = (first.x, first.y, first.z, last.x, last.y, last.z)
      val length = GeometryUtils.geometryLength(geometry.get)
      sqlu"""UPDATE ROAD_ADDRESS
        SET road_number = ${roadAddress.roadNumber},
           road_part_number= ${roadAddress.roadPartNumber},
           track_code = ${roadAddress.track.value},
           discontinuity= ${roadAddress.discontinuity.value},
           START_ADDR_M= ${roadAddress.startAddrMValue},
           END_ADDR_M= ${roadAddress.endAddrMValue},
           start_date= $startTS,
           end_date= $endTS,
           geometry= MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(
             $x1,$y1,$z1,0.0,$x2,$y2,$z2,$length))
        WHERE id = ${roadAddress.id}""".execute
    }
  }

  private def updateWithoutGeometry(roadAddress: RoadAddress) = {
    val startTS = toTimeStamp(roadAddress.startDate)
    val endTS = toTimeStamp(roadAddress.endDate)
    sqlu"""UPDATE ROAD_ADDRESS
        SET road_number = ${roadAddress.roadNumber},
           road_part_number= ${roadAddress.roadPartNumber},
           track_code = ${roadAddress.track.value},
           discontinuity= ${roadAddress.discontinuity.value},
           START_ADDR_M= ${roadAddress.startAddrMValue},
           END_ADDR_M= ${roadAddress.endAddrMValue},
           start_date= $startTS,
           end_date= $endTS
        WHERE id = ${roadAddress.id}""".execute
  }

  def createMissingRoadAddress (missingRoadAddress: MissingRoadAddress) = {
    sqlu"""
           insert into missing_road_address
           (select ${missingRoadAddress.linkId}, ${missingRoadAddress.startAddrMValue}, ${missingRoadAddress.endAddrMValue},
             ${missingRoadAddress.roadNumber}, ${missingRoadAddress.roadPartNumber}, ${missingRoadAddress.anomaly.value},
             ${missingRoadAddress.startMValue}, ${missingRoadAddress.endMValue} FROM dual
            WHERE NOT EXISTS (SELECT * FROM MISSING_ROAD_ADDRESS WHERE link_id = ${missingRoadAddress.linkId}) AND
              NOT EXISTS (SELECT * FROM ROAD_ADDRESS ra JOIN LRM_POSITION pos ON (pos.id = lrm_position_id)
                WHERE link_id = ${missingRoadAddress.linkId} AND (valid_to IS NULL OR valid_to > sysdate) ))
           """.execute
  }

  def createMissingRoadAddress (linkId: Long, start_addr_m: Long, end_addr_m: Long, anomaly_code: Int) = {
    sqlu"""
           insert into missing_road_address (link_id, start_addr_m, end_addr_m,anomaly_code)
           values ($linkId, $start_addr_m, $end_addr_m, $anomaly_code)
           """.execute
  }

  def lockRoadAddressTable(): Unit = {
    sqlu"""
           LOCK TABLE ROAD_ADDRESS IN EXCLUSIVE MODE
          """.execute
  }

  def updateMergedSegmentsById (ids: Set[Long]): Int = {
    val query =
      s"""
          Update ROAD_ADDRESS ra Set valid_to = sysdate where valid_to IS NULL and id in (${ids.mkString(",")})
        """
    if (ids.isEmpty)
      0
    else
      Q.updateNA(query).first
  }

  def expireRoadAddresses (sourceIds: Set[Long]) = {
    if (!sourceIds.isEmpty) {
      val query =
        s"""
          Update road_address Set valid_to = sysdate Where lrm_position_id in (Select id From lrm_position where link_id in (${sourceIds.mkString(",")}))
        """
      Q.updateNA(query).first
    }
  }

  def expireMissingRoadAddresses (targetIds: Set[Long]) = {

    if (!targetIds.isEmpty) {
      val query =
        s"""
          Delete from missing_road_address Where link_id in (${targetIds.mkString(",")})
        """
      Q.updateNA(query).first
    }
  }

  def getMissingRoadAddresses(linkIds: Set[Long]): List[MissingRoadAddress] = {
    if (linkIds.size > 500) {
      getMissingByLinkIdMassQuery(linkIds)
    } else {
      val where = linkIds.isEmpty match {
        case true => return List()
        case false => s""" where link_id in (${linkIds.mkString(",")})"""
      }
      val query =
        s"""SELECT link_id, start_addr_m, end_addr_m, road_number, road_part_number, start_m, end_m, anomaly_code
            FROM missing_road_address $where"""
      Q.queryNA[(Long, Option[Long], Option[Long], Option[Long], Option[Long], Option[Double], Option[Double], Int)](query).list.map {
        case (linkId, startAddrM, endAddrM, road, roadPart, startM, endM, anomaly) =>
          MissingRoadAddress(linkId, startAddrM, endAddrM, RoadType.UnknownOwnerRoad ,road, roadPart, startM, endM, Anomaly.apply(anomaly))
      }
    }
  }

  def getMissingByLinkIdMassQuery(linkIds: Set[Long]): List[MissingRoadAddress] = {
    MassQuery.withIds(linkIds) {
      idTableName =>
        val query =
          s"""SELECT link_id, start_addr_m, end_addr_m, road_number, road_part_number, start_m, end_m, anomaly_code
            FROM missing_road_address mra join $idTableName i on i.id = mra.link_id"""
        Q.queryNA[(Long, Option[Long], Option[Long], Option[Long], Option[Long], Option[Double], Option[Double], Int)](query).list.map {
          case (linkId, startAddrM, endAddrM, road, roadPart, startM, endM, anomaly) =>
            MissingRoadAddress(linkId, startAddrM, endAddrM, RoadType.UnknownOwnerRoad, road, roadPart, startM, endM, Anomaly.apply(anomaly))
        }
    }
  }

  /**
    * Marks the road address identified by the supplied Id as eiher floating or not
    *
    * @param isFloating '0' for not floating, '1' for floating
    * @param roadAddressId The Id of a road addresss
    */
  def changeRoadAddressFloating(isFloating: Int, roadAddressId: Long, geometry: Option[Seq[Point]]): Unit = {
    if (geometry.nonEmpty) {
      val first = geometry.get.head
      val last = geometry.get.last
      val (x1, y1, z1, x2, y2, z2) = (first.x, first.y, first.z, last.x, last.y, last.z)
      val length = GeometryUtils.geometryLength(geometry.get)
      sqlu"""
           Update road_address Set floating = $isFloating,
                  geometry= MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(
                  $x1,$y1,$z1,0.0,$x2,$y2,$z2,$length))
             Where id = $roadAddressId
      """.execute
    } else {
      sqlu"""
           Update road_address Set floating = $isFloating
             Where id = $roadAddressId
      """.execute
    }
  }

  def changeRoadAddressFloating(float: Boolean, roadAddressId: Long, geometry: Option[Seq[Point]] = None): Unit = {
    changeRoadAddressFloating(if (float) 1 else 0, roadAddressId, geometry)
  }

  def getValidRoadNumbers = {
    sql"""
       select distinct road_number
              from road_address ra
              where ra.floating = '0' AND (end_date < sysdate OR end_date IS NULL)
              order by road_number
      """.as[Long].list
  }

  def getValidRoadParts(roadNumber: Long) = {
    sql"""
       select distinct road_part_number
              from road_address ra
              where road_number = $roadNumber AND ra.floating = '0' AND (end_date < sysdate OR end_date IS NULL)
      """.as[Long].list
  }

  def updateLRM(lRMValueAdjustment: LRMValueAdjustment) = {
    val (startM, endM) = (lRMValueAdjustment.startMeasure, lRMValueAdjustment.endMeasure)
    (startM, endM) match {
      case (Some(s), Some(e)) =>
        sqlu"""
           UPDATE LRM_POSITION
           SET start_measure = $s,
             end_measure = $e,
             link_id = ${lRMValueAdjustment.linkId},
             modified_date = sysdate
           WHERE id = (SELECT LRM_POSITION_ID FROM ROAD_ADDRESS WHERE id = ${lRMValueAdjustment.addressId})
      """.execute
      case (_, Some(e)) =>
        sqlu"""
           UPDATE LRM_POSITION
           SET
             end_measure = ${lRMValueAdjustment.endMeasure.get},
             link_id = ${lRMValueAdjustment.linkId},
             modified_date = sysdate
           WHERE id = (SELECT LRM_POSITION_ID FROM ROAD_ADDRESS WHERE id = ${lRMValueAdjustment.addressId})
      """.execute
      case (Some(s), _) =>
        sqlu"""
           UPDATE LRM_POSITION
           SET start_measure = ${lRMValueAdjustment.startMeasure.get},
             link_id = ${lRMValueAdjustment.linkId},
             modified_date = sysdate
           WHERE id = (SELECT LRM_POSITION_ID FROM ROAD_ADDRESS WHERE id = ${lRMValueAdjustment.addressId})
      """.execute
      case _ =>
    }
  }

  /**
    * Create the value for geometry field, using the updateSQL above.
    *
    * @param geometry Geometry, if available
    * @return
    */
  private def geometryToSQL(geometry: Option[Seq[Point]]) = {
    geometry match {
      case Some(geom) if geom.nonEmpty =>
      case _ => ""
    }
  }

  def getNextRoadAddressId: Long = {
    Queries.nextViitePrimaryKeyId.as[Long].first
  }

  implicit val getDiscontinuity = GetResult[Discontinuity]( r=> Discontinuity.apply(r.nextInt()))

  implicit val getTrack = GetResult[Track]( r=> Track.apply(r.nextInt()))

  implicit val getCalibrationCode = GetResult[CalibrationCode]( r=> CalibrationCode.apply(r.nextInt()))

  def queryFloatingByLinkIdMassQuery(linkIds: Set[Long]): List[RoadAddress] = {
    MassQuery.withIds(linkIds) {
      idTableName =>
        val query =
          s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        join $idTableName i on i.id = pos.link_id
        where floating='1' and t.id < t2.id AND
          (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
        queryList(query)
    }
  }

  def queryFloatingByLinkId(linkIds: Set[Long]): List[RoadAddress] = {
    if (linkIds.size > 1000) {
      return queryFloatingByLinkIdMassQuery(linkIds)
    }
    val linkIdString = linkIds.mkString(",")
    val where = linkIds.isEmpty match {
      case true => return List()
      case false => s""" where pos.link_id in ($linkIdString)"""
    }
    val query =
      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        $where AND floating='1' and t.id < t2.id and
          (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
    queryList(query)
  }

  /**
    * Return road address table rows that are valid by their ids
    * @param ids
    * @return
    */
  def queryById(ids: Set[Long]): List[RoadAddress] = {
    if (ids.size > 1000) {
      return queryByIdMassQuery(ids)
    }
    val idString = ids.mkString(",")
    val where = if (ids.isEmpty) {
      return List()
    } else {
      s""" where ra.id in ($idString)"""
    }
    val query =
      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        $where and t.id < t2.id and
          (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
    queryList(query)
  }

  def queryByIdMassQuery(ids: Set[Long]): List[RoadAddress] = {
    MassQuery.withIds(ids) {
      idTableName =>
        val query =
          s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id
        join $idTableName i on i.id = ra.id
        where t.id < t2.id and
          (valid_from is null or valid_from <= sysdate) and
          (valid_to is null or valid_to >= sysdate)
      """
        queryList(query)
    }
  }

  /**
    * Remove Road Addresses (mark them as removed). Don't use more than 1000 road addresses at once.
    *
    * @param roadAddresses Seq[RoadAddress]
    * @return Number of updated rows
    */
  def remove(roadAddresses: Seq[RoadAddress]): Int = {
    val idString = roadAddresses.map(_.id).mkString(",")
    val query =
      s"""
          UPDATE ROAD_ADDRESS SET VALID_TO = sysdate WHERE id IN ($idString)
        """
    Q.updateNA(query).first
  }

  def create(roadAddresses: Seq[RoadAddress], createdBy : String = "-"): Seq[Long] = {
    val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure) values (?, ?, ?, ?, ?)")
    val addressPS = dynamicSession.prepareStatement("insert into ROAD_ADDRESS (id, lrm_position_id, road_number, road_part_number, " +
      "track_code, discontinuity, START_ADDR_M, END_ADDR_M, start_date, end_date, created_by, " +
      "VALID_FROM, geometry, floating, calibration_points) values (?, ?, ?, ?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), " +
      "TO_DATE(?, 'YYYY-MM-DD'), ?, sysdate, MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(" +
      "?,?,0.0,?,?,?,0.0,?)), ?, ?)")
    val ids = sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level <= ${roadAddresses.size}""".as[Long].list
    roadAddresses.zip(ids).foreach { case ((address), (lrmId)) =>
      lrmPositionPS.setLong(1, lrmId)
      lrmPositionPS.setLong(2, address.linkId)
      lrmPositionPS.setLong(3, address.sideCode.value)
      lrmPositionPS.setDouble(4, address.startMValue)
      lrmPositionPS.setDouble(5, address.endMValue)
      lrmPositionPS.addBatch()
      addressPS.setLong(1, if (address.id == -1000) {
        Sequences.nextViitePrimaryKeySeqValue
      } else address.id)
      addressPS.setLong(2, lrmId)
      addressPS.setLong(3, address.roadNumber)
      addressPS.setLong(4, address.roadPartNumber)
      addressPS.setLong(5, address.track.value)
      addressPS.setLong(6, address.discontinuity.value)
      addressPS.setLong(7, address.startAddrMValue)
      addressPS.setLong(8, address.endAddrMValue)
      addressPS.setString(9, address.startDate match {
        case Some(dt) => dateFormatter.print(dt)
        case None => ""
      })
      addressPS.setString(10, address.endDate match {
        case Some(dt) => dateFormatter.print(dt)
        case None => ""
      })
//      addressPS.setString(11, address.modifiedBy.getOrElse("-"))
      addressPS.setString(11, createdBy)
    val (p1, p2) = (address.geom.head, address.geom.last)
      addressPS.setDouble(12, p1.x)
      addressPS.setDouble(13, p1.y)
      addressPS.setDouble(14, address.startAddrMValue)
      addressPS.setDouble(15, p2.x)
      addressPS.setDouble(16, p2.y)
      addressPS.setDouble(17, address.endAddrMValue)
      addressPS.setInt(18, if (address.floating) 1 else 0)
      addressPS.setInt(19, CalibrationCode.getFromAddress(address).value)
      addressPS.addBatch()
    }
    lrmPositionPS.executeBatch()
    addressPS.executeBatch()
    lrmPositionPS.close()
    addressPS.close()
    roadAddresses.map(_.id)
  }

  def createRoadAddressProject(roadAddressProject: RoadAddressProject): Unit ={
    sqlu"""
           insert into project (id, state, name, ely, created_by, created_date, start_date ,modified_by, modified_date, add_info)
           values (${roadAddressProject.id}, ${roadAddressProject.status}, ${roadAddressProject.name}, 0, ${roadAddressProject.createdBy}, sysdate ,${roadAddressProject.startDate}, '-' , '', ${roadAddressProject.additionalInfo})
           """.execute
  }

  def createRoadAddressProjectLink(id: Long, roadAddress: RoadAddress, roadAddressProject: RoadAddressProject) : Unit ={
    sqlu"""
           insert into project_link (id, project_id, road_type, discontinuity_type, road_number, road_part_number, start_addr_m, end_addr_m, lrm_position_id, created_by, modified_by, created_date, modified_date)
           values (${id}, ${roadAddressProject.id}, ${roadAddress.track.value}, ${roadAddress.discontinuity.value}, ${roadAddress.roadNumber}, ${roadAddress.roadPartNumber}, ${roadAddress.startAddrMValue},
            ${roadAddress.endAddrMValue}, ${roadAddress.lrmPositionId}, ${roadAddressProject.createdBy} , ${roadAddressProject.modifiedBy}, ${roadAddress.startDate}, ${roadAddressProject.dateModified})
           """.execute
  }

  def getRoadAddressProjectLinks(projectId : Long): List[RoadAddressProjectLink] ={
    val query =
      s"""select PROJECT_LINK.ID, PROJECT_LINK.PROJECT_ID, PROJECT_LINK.ROAD_TYPE, PROJECT_LINK.DISCONTINUITY_TYPE, PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.START_ADDR_M, PROJECT_LINK.END_ADDR_M, PROJECT_LINK.LRM_POSITION_ID, PROJECT_LINK.CREATED_BY, PROJECT_LINK.MODIFIED_BY, lrm_position.link_id, (LRM_POSITION.END_MEASURE - LRM_POSITION.START_MEASURE) as length
         from PROJECT_LINK join ROAD_ADDRESS join LRM_POSITION
         on LRM_POSITION.ID = ROAD_ADDRESS.LRM_POSITION_ID
         on (PROJECT_LINK.ROAD_NUMBER = ROAD_ADDRESS.ROAD_NUMBER and PROJECT_LINK.ROAD_PART_NUMBER = ROAD_ADDRESS.ROAD_PART_NUMBER and ROAD_ADDRESS.LRM_POSITION_ID = PROJECT_LINK.LRM_POSITION_ID)
         where (PROJECT_LINK.PROJECT_ID = $projectId) order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    Q.queryNA[(Long, Long, Long, Long, Long, Long, Long, Long, Long, String, String, Long, Double)](query).list.map{
      case(projectLinkId, projectId, roadType, discontinuityType, roadNumber, roadPartNumber, startAddrM, endAddrM, lrmPositionId, cratedBy, modifiedBy, linkId, length) =>
        RoadAddressProjectLink(projectLinkId, projectId, roadType, Discontinuity.apply(discontinuityType.toInt), roadNumber, roadPartNumber, startAddrM, endAddrM, lrmPositionId, cratedBy, modifiedBy, linkId, length)
    }
  }

  def updateRoadAddressProject(roadAddressProject : RoadAddressProject): Unit ={
    sqlu"""
           update project set state = ${roadAddressProject.status}, name = ${roadAddressProject.name}, modified_by = '-' ,modified_date = ${roadAddressProject.dateModified} where id = ${roadAddressProject.id}
           """.execute
  }

  def getRoadAddressProjectById(id : Long) : Option[RoadAddressProject] = {
    val where = s""" where id =${id}"""
    val query = s"""SELECT id, state, name, created_by, created_date, start_date, modified_by, modified_date, add_info
            FROM project $where"""
    Q.queryNA[(Long, Long, String, String, DateTime, DateTime, String, DateTime, String )](query).list.map{
      case(id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo) =>
        RoadAddressProject(id, state, name, createdBy, start_date, modifiedBy, createdDate, modifiedDate, addInfo, 0, 0, 0)
    }.headOption
  }

  def getRoadAddressProjects() : List[RoadAddressProject] = {
    val query = s"""SELECT id, state, name, created_by, created_date, start_date, modified_by, modified_date, add_info
            FROM project order by name, id """
    Q.queryNA[(Long, Long, String, String, DateTime, DateTime, String, DateTime, String )](query).list.map{
      case(id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo) =>
        RoadAddressProject(id, state, name, createdBy, start_date ,modifiedBy, createdDate, modifiedDate, addInfo, 0, 0, 0)
    }
  }

}
