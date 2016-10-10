package fi.liikennevirasto.viite.dao

import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing, Unknown}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.CalibrationCode.{AtBeginning, AtBoth, AtEnd, No}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import slick.jdbc.{GetResult, StaticQuery => Q}
import com.github.tototoshi.slick.MySQLJodaSupport._
import org.joda.time.DateTime

//TIETYYPPI (1= yleinen tie, 2 = lauttaväylä yleisellä tiellä, 3 = kunnan katuosuus, 4 = yleisen tien työmaa, 5 = yksityistie, 9 = omistaja selvittämättä)
sealed trait RoadType {
  def value: Int
}
object RoadType {
  val values = Set(Public, Ferry, MunicipalityStreet, PublicUnderConstruction, Private, UnknownOwner)

  def apply(intValue: Int): RoadType = {
    values.find(_.value == intValue).getOrElse(UnknownOwner)
  }

  case object Public extends RoadType { def value = 1 }
  case object Ferry extends RoadType { def value = 2 }
  case object MunicipalityStreet extends RoadType { def value = 3 }
  case object PublicUnderConstruction extends RoadType { def value = 4 }
  case object Private extends RoadType { def value = 5 }
  case object UnknownOwner extends RoadType { def value = 9 }
}

//JATKUVUUS (1 = Tien loppu, 2 = epäjatkuva (esim. vt9 välillä Akaa-Tampere), 3 = ELY:n raja, 4 = Lievä epäjatkuvuus (esim kiertoliittymä), 5 = jatkuva)

sealed trait Discontinuity {
  def value: Int
}
object Discontinuity {
  val values = Set(EndOfRoad, Discontinuous, ChangingELYCode, MinorDiscontinuity, Continuous)

  def apply(intValue: Int): Discontinuity = {
    values.find(_.value == intValue).getOrElse(Continuous)
  }

  case object EndOfRoad extends Discontinuity { def value = 1 }
  case object Discontinuous extends Discontinuity { def value = 2 }
  case object ChangingELYCode extends Discontinuity { def value = 3 }
  case object MinorDiscontinuity extends Discontinuity { def value = 4 }
  case object Continuous extends Discontinuity { def value = 5 }
}

sealed trait CalibrationCode {
  def value: Int
}
object CalibrationCode {
  val values = Set(No, AtEnd, AtBeginning, AtBoth)

  def apply(intValue: Int): CalibrationCode = {
    values.find(_.value == intValue).getOrElse(No)
  }

  case object No extends CalibrationCode { def value = 0 }
  case object AtEnd extends CalibrationCode { def value = 1 }
  case object AtBeginning extends CalibrationCode { def value = 2 }
  case object AtBoth extends CalibrationCode { def value = 3 }
}
case class CalibrationPoint(linkId: Long, segmentMValue: Double, addressMValue: Long)

case class RoadAddress(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track, ely: Long, roadType: RoadType,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, startDate: DateTime, endDate: DateTime, linkId: Long,
                       startMValue: Double, endMValue: Double, calibrationPoints: (Option[CalibrationPoint],Option[CalibrationPoint]) = (None, None)
                      )

case class MissingRoadAddress(linkId: Long, startAddrMValue: Long, endAddrMValue: Long)

object RoadAddressDAO {

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
  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")

  def dateTimeParse(string: String) = {
    try {
      DateTime.parse(string, formatter)
    } catch {
      case ex: Exception => null
    }
  }

  def fetchByLinkId(linkIds: Set[Long]): List[RoadAddress] = {
    if (linkIds.size > 1000) {
      return fetchByLinkIdMassQuery(linkIds)
    }
    val linkIdString = linkIds.mkString(",")
    val where = linkIds.isEmpty match {
      case true => return List()
      case false => s""" where pos.link_id in ($linkIdString)"""
    }
    val query =
      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code, ra.ely,
        ra.road_type, ra.discontinuity, ra.start_addr_m, ra.end_addr_m, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.created_date, ra.CALIBRATION_POINTS
        from road_address ra
        join lrm_position pos on ra.lrm_position_id = pos.id
        $where
      """
    val tuples = Q.queryNA[(Long, Long, Long, Int, Long, Int, Int, Long, Long, Long, Double, Double, Int,
      String, String, String, String, Int)](query).list
    tuples.map {
      case (id, roadNumber, roadPartNumber, track, elyCode, roadType, discontinuity, startAddrMValue, endAddrMValue,
      linkId, startMValue, endMValue, sideCode, startDate, endDate, createdBy, createdDate, calibrationCode) =>
        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), elyCode, RoadType.apply(roadType),
          Discontinuity.apply(discontinuity), startAddrMValue, endAddrMValue, dateTimeParse(startDate), dateTimeParse(endDate), linkId,
          startMValue, endMValue, calibrations(CalibrationCode.apply(calibrationCode), linkId, startMValue, endMValue, startAddrMValue, endAddrMValue, SideCode.apply(sideCode)))
    }

  }

  def fetchPartsByRoadNumbers(roadNumbers: Seq[(Int, Int)], coarse: Boolean = false): List[RoadAddress] = {
    val filter = roadNumbers.map(n => "road_number >= " + n._1 + " and road_number <= " + n._2)
      .mkString("(", ") OR (", ")")
      //.foldLeft("")((c, r) => c + ") OR (" + r)
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
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code, ra.ely,
        ra.road_type, ra.discontinuity, ra.start_addr_m, ra.end_addr_m, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.created_date, ra.CALIBRATION_POINTS
        from road_address ra
        join lrm_position pos on ra.lrm_position_id = pos.id
        $where $coarseWhere
      """
    val tuples = Q.queryNA[(Long, Long, Long, Int, Long, Int, Int, Long, Long, Long, Double, Double, Int,
      String, String, String, String, Int)](query).list
    tuples.map {
      case (id, roadNumber, roadPartNumber, track, elyCode, roadType, discontinuity, startAddrMValue, endAddrMValue,
      linkId, startMValue, endMValue, sideCode, startDate, endDate, createdBy, createdDate, calibrationCode) =>
        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), elyCode, RoadType.apply(roadType),
          Discontinuity.apply(discontinuity), startAddrMValue, endAddrMValue, dateTimeParse(startDate), dateTimeParse(endDate), linkId,
          startMValue, endMValue, calibrations(CalibrationCode.apply(calibrationCode), linkId, startMValue, endMValue, startAddrMValue, endAddrMValue, SideCode.apply(sideCode)))
    }

  }

  def fetchByLinkIdMassQuery(linkIds: Set[Long]): List[RoadAddress] = {
    MassQuery.withIds(linkIds) {
      idTableName =>
        val query =
          s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code, ra.ely,
        ra.road_type, ra.discontinuity, ra.start_addr_m, ra.end_addr_m, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.created_date, ra.CALIBRATION_POINTS
        from road_address ra
        join lrm_position pos on ra.lrm_position_id = pos.id
        join $idTableName i on i.id = pos.link_id
      """
        val tuples = Q.queryNA[(Long, Long, Long, Int, Long, Int, Int, Long, Long, Long, Double, Double, Int,
          String, String, String, String, Int)](query).list
        tuples.map {
          case (id, roadNumber, roadPartNumber, track, elyCode, roadType, discontinuity, startAddrMValue, endAddrMValue,
          linkId, startMValue, endMValue, sideCode, startDate, endDate, createdBy, createdDate, calibrationCode) =>
            RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), elyCode, RoadType.apply(roadType),
              Discontinuity.apply(discontinuity), startAddrMValue, endAddrMValue, dateTimeParse(startDate), dateTimeParse(endDate), linkId,
              startMValue, endMValue, calibrations(CalibrationCode.apply(calibrationCode), linkId, startMValue, endMValue, startAddrMValue, endAddrMValue, SideCode.apply(sideCode)))
        }
    }
  }

  def fetchByRoadPart(roadNumber: Int, roadPartNumber: Int) = {
    val query =
      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code, ra.ely,
        ra.road_type, ra.discontinuity, ra.start_addr_m, ra.end_addr_m, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.created_date, ra.CALIBRATION_POINTS
        from road_address ra
        join lrm_position pos on ra.lrm_position_id = pos.id
        where road_number = $roadNumber AND road_part_number = $roadPartNumber
        ORDER BY road_number, road_part_number, track_code, start_addr_m
      """
    val tuples = Q.queryNA[(Long, Long, Long, Int, Long, Int, Int, Long, Long, Long, Double, Double, Int,
      String, String, String, String, Int)](query).list
    tuples.map{
      case (id, roadNumber, roadPartNumber, track, elyCode, roadType, discontinuity, startAddrMValue, endAddrMValue,
      linkId, startMValue, endMValue, sideCode, startDate, endDate, createdBy, createdDate, calibrationCode) =>
        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), elyCode, RoadType.apply(roadType),
          Discontinuity.apply(discontinuity), startAddrMValue, endAddrMValue, dateTimeParse(startDate), dateTimeParse(endDate), linkId,
          startMValue, endMValue, calibrations(CalibrationCode.apply(calibrationCode), linkId, startMValue, endMValue, startAddrMValue, endAddrMValue, SideCode.apply(sideCode)))
    }
  }

  def fetchNextRoadNumber(current: Int) = {
    val query =
      s"""
          SELECT * FROM (
            SELECT ra.road_number
            FROM road_address ra
            WHERE road_number > $current AND (end_date < sysdate OR end_date IS NULL)
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
            WHERE road_number = $roadNumber  AND road_part_number > $current AND (end_date < sysdate OR end_date IS NULL)
            ORDER BY road_part_number ASC
          ) WHERE ROWNUM < 2
      """
    Q.queryNA[Int](query).firstOption
  }

  def update(roadAddress: RoadAddress) = {
    sqlu"""UPDATE ROAD_ADDRESS
        SET road_number = ${roadAddress.roadNumber},
            road_part_number= ${roadAddress.roadPartNumber},
            track_code = ${roadAddress.track.value},
            ely= ${roadAddress.ely},
            road_type= ${roadAddress.roadType.value},
            discontinuity= ${roadAddress.discontinuity.value},
            START_ADDR_M= ${roadAddress.startAddrMValue},
            END_ADDR_M= ${roadAddress.endAddrMValue},
            start_date= ${roadAddress.startDate},
            end_date= ${roadAddress.endDate}
        WHERE id = ${roadAddress.id}""".execute
  }


  def createMissingRoadAddress (linkId: Long, start_addr_m: Long, end_addr_m: Long, road_number: Long, road_part_number: Long, anomaly_code: Int) = {
    sqlu"""
           insert into missing_road_address (link_id, start_addr_m, end_addr_m, road_number, road_part_number, anomaly_code)
           values ($linkId, $start_addr_m, $end_addr_m, $road_number, $road_part_number, $anomaly_code)
           """.execute
  }

  def createMissingRoadAddress (linkId: Long, start_addr_m: Long, end_addr_m: Long, anomaly_code: Int) = {
    sqlu"""
           insert into missing_road_address (link_id, start_addr_m, end_addr_m,anomaly_code)
           values ($linkId, $start_addr_m, $end_addr_m, $anomaly_code)
           """.execute
  }

  def getAllMissingRoadAddresses = {
    sql"""SELECT link_id, start_addr_m, end_addr_m
            FROM missing_road_address""".as[(Long, Long, Long)].list
  }

  def getMissingRoadAddresses(linkIds: Set[Long]): List[MissingRoadAddress] = {
    if (linkIds.size > 500) {
      getMissingByLinkIdMassQuery(linkIds)
    } else {
      val where = linkIds.isEmpty match {
        case true => return List()
        case false => s""" where pos.link_id in (${linkIds.mkString(",")})"""
      }
      val query =
        s"""SELECT link_id, start_addr_m, end_addr_m
            FROM missing_road_address $where"""
      Q.queryNA[(Long, Long, Long)](query).list.map {
        case (linkId, startAddrM, endAddrM) => MissingRoadAddress(linkId, startAddrM, endAddrM)
      }
    }
  }

  def getMissingByLinkIdMassQuery(linkIds: Set[Long]): List[MissingRoadAddress] = {
    MassQuery.withIds(linkIds) {
      idTableName =>
        val query =
          s"""SELECT link_id, start_addr_m, end_addr_m
            FROM missing_road_address mra join $idTableName i on i.id = mra.link_id"""
        Q.queryNA[(Long, Long, Long)](query).list.map {
          case (linkId, startAddrM, endAddrM) => MissingRoadAddress(linkId, startAddrM, endAddrM)
        }
    }
  }

  def getLrmPositionByLinkId(linkId: Long) = {
    sql"""
       select lrm.id, lrm.start_measure, lrm.end_measure
       from lrm_position lrm, road_address rda
       where lrm.id = rda.lrm_position_id and lrm.link_id = $linkId""".as[(Long, Double, Double)].list
  }

  def getLrmPositionMeasures(linkId: Long) = {
    sql"""
       select lrm.id, lrm.start_measure, lrm.end_measure
       from lrm_position lrm, road_address rda
       where lrm.id = rda.lrm_position_id
       and (lrm.start_measure != rda.start_addr_m or lrm.end_measure != rda.end_addr_m) and lrm.link_id = $linkId""".as[(Long, Double, Double)].list
  }

  def getLrmPositionRoadParts(linkId: Long, roadPart: Long) = {
    sql"""
       select lrm.id, lrm.start_measure, lrm.end_measure
       from lrm_position lrm, road_address rda
       where lrm.id = rda.lrm_position_id
       and lrm.link_id = $linkId and rda.road_part_number!= $roadPart""".as[(Long, Double, Double)].list
  }



  implicit val getDiscontinuity = GetResult[Discontinuity]( r=> Discontinuity.apply(r.nextInt()))

  implicit val getRoadType = GetResult[RoadType]( r=> RoadType.apply(r.nextInt()))

  implicit val getTrack = GetResult[Track]( r=> Track.apply(r.nextInt()))

  implicit val getCalibrationCode = GetResult[CalibrationCode]( r=> CalibrationCode.apply(r.nextInt()))
}
