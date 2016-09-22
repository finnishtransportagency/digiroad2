package fi.liikennevirasto.viite.dao

import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing, Unknown}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.CalibrationCode.{AtBeginning, AtBoth, AtEnd, No}
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
case class CalibrationPoint(linkId: Long, mValue: Double, addressMValue: Long)

case class RoadAddress(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track, ely: Long, roadType: RoadType,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, endDate: String, linkId: Long,
                       startMValue: Double, endMValue: Double, calibrationPoints: Seq[CalibrationPoint] = Seq()
                      )

object RoadAddressDAO {

  private def logger = LoggerFactory.getLogger(getClass)

  private def calibrations(calibrationCode: CalibrationCode, linkId: Long, startMValue: Double, endMValue: Double,
                           startAddrMValue: Long, endAddrMValue: Long, sideCode: SideCode): Seq[CalibrationPoint] = {
    sideCode match {
      case BothDirections => Seq() // Invalid choice
      case TowardsDigitizing => calibrations(calibrationCode, linkId, startMValue, endMValue, startAddrMValue, endAddrMValue)
      case AgainstDigitizing => calibrations(calibrationCode, linkId, endMValue, startMValue, startAddrMValue, endAddrMValue)
      case Unknown => Seq()  // Invalid choice
    }
  }

  private def calibrations(calibrationCode: CalibrationCode, linkId: Long, startMValue: Double, endMValue: Double,
                           startAddrMValue: Long, endAddrMValue: Long): Seq[CalibrationPoint] = {
    calibrationCode match {
      case No => Seq()
      case AtEnd => Seq(CalibrationPoint(linkId, endMValue, endAddrMValue))
      case AtBeginning => Seq(CalibrationPoint(linkId, startMValue, startAddrMValue))
      case AtBoth => Seq(CalibrationPoint(linkId, startMValue, startAddrMValue),
        CalibrationPoint(linkId, endMValue, endAddrMValue))
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
          Discontinuity.apply(discontinuity), startAddrMValue, endAddrMValue, endDate, linkId,
          startMValue, endMValue, calibrations(CalibrationCode.apply(calibrationCode), linkId, startMValue, endMValue, startAddrMValue, endAddrMValue, SideCode.apply(sideCode)))
    }

  }

  def fetchPartsByRoadNumbers(roadNumbers: Seq[(Int, Int)]): List[RoadAddress] = {
    val filter = roadNumbers.map(n => "road_number >= " + n._1 + " and road_number <= " + n._2)
      .mkString("(", ") OR (", ")")
      //.foldLeft("")((c, r) => c + ") OR (" + r)
    val where = roadNumbers.isEmpty match {
      case true => return List()
//      case false => s""" where calibration_points != 0 AND track_code in (0,1) AND $filter"""
      case false => s""" where track_code in (0,1) AND $filter"""
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
    println(query)
    val tuples = Q.queryNA[(Long, Long, Long, Int, Long, Int, Int, Long, Long, Long, Double, Double, Int,
      String, String, String, String, Int)](query).list
    tuples.map {
      case (id, roadNumber, roadPartNumber, track, elyCode, roadType, discontinuity, startAddrMValue, endAddrMValue,
      linkId, startMValue, endMValue, sideCode, startDate, endDate, createdBy, createdDate, calibrationCode) =>
        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), elyCode, RoadType.apply(roadType),
          Discontinuity.apply(discontinuity), startAddrMValue, endAddrMValue, endDate, linkId,
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
              Discontinuity.apply(discontinuity), startAddrMValue, endAddrMValue, endDate, linkId,
              startMValue, endMValue, calibrations(CalibrationCode.apply(calibrationCode), linkId, startMValue, endMValue, startAddrMValue, endAddrMValue, SideCode.apply(sideCode)))
        }
    }
  }

  private def fetchRoadAddressData(linkIds: Set[Long]) = {
    val query =
      """
        select id, pos.link_id, a.geometry, pos.start_measure, a.floating, a.municipality_code, ev.value, a.created_by, a.created_date, a.modified_by, a.modified_date
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join property p on p.asset_type_id = a.asset_type_id
        left join single_choice_value scv on scv.asset_id = a.id
        left join enumerated_value ev on (ev.property_id = p.id AND scv.enumerated_value_id = ev.id)
      """

  }

  implicit val getDiscontinuity = GetResult[Discontinuity]( r=> Discontinuity.apply(r.nextInt()))

  implicit val getRoadType = GetResult[RoadType]( r=> RoadType.apply(r.nextInt()))

  implicit val getTrack = GetResult[Track]( r=> Track.apply(r.nextInt()))

  implicit val getCalibrationCode = GetResult[CalibrationCode]( r=> CalibrationCode.apply(r.nextInt()))
}
