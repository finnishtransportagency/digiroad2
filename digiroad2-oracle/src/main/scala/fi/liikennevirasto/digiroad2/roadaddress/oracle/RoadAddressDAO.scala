package fi.liikennevirasto.digiroad2.roadaddress.oracle

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{CalibrationCode, CalibrationPoint, Discontinuity, RoadAddress}
import org.joda.time.DateTime
import slick.jdbc.StaticQuery


case class RoadAddress(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, modifiedBy: Option[String] = None, lrmPositionId : Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                       calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = (None, None), floating: Boolean = false,
                       geom: Seq[Point])


class RoadAddressDAO {

  def getRoadAddress(queryFilter: String => String): Seq[RoadAddress] = {
    val query =
    /*     SELECT ra.id, ra.road_Number, ra.road_Part_Number, ra.track_code,  ra.discontinuity, ra.start_Addr_M, ra.end_Addr_M, ra.start_Date, ra.end_Date,
               '', ra.lrm_Position_Id, pos.link_Id, pos.start_Measure, pos.end_Measure, pos.side_Code, ra.calibration_Points, ra.floating, ra.geometry
           FROM road_address ra
           JOIN lrm_position pos ON (ra.lrm_position_id = pos.id)
       """*/

      s"""
        select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
        ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
        pos.side_code,
        ra.start_date, ra.end_date, ra.created_by, ra.valid_from, ra.CALIBRATION_POINTS, ra.floating, t.X, t.Y, t2.X, t2.Y
        from road_address ra cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
        TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
        join lrm_position pos on ra.lrm_position_id = pos.id"""

    val queryWithFilter = queryFilter(query) + " and (ra.valid_to > sysdate or ra.valid_to is null) "
    val tuples = StaticQuery.queryNA[(Long, Long, Long, Int, Int, Long, Long, Long, Long, Double, Double, Int,
      Option[DateTime], Option[DateTime], Option[String], Option[DateTime], Int, Boolean, Double, Double, Double, Double)](query).list
    tuples.map {
      case (id, roadNumber, roadPartNumber, track, discontinuity, startAddrMValue, endAddrMValue, lrmPositionId,
      linkId, startMValue, endMValue, sideCode, startDate, endDate, createdBy, createdDate, calibrationCode, floating, x, y, x2, y2) =>
        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), Discontinuity.apply(discontinuity),
          startAddrMValue, endAddrMValue, startDate, endDate, createdBy, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode),
          calibrations(CalibrationCode.apply(calibrationCode), linkId, startMValue, endMValue, startAddrMValue,
            endAddrMValue, SideCode.apply(sideCode)), floating, Seq(Point(x, y), Point(x2, y2)))
    }
  }

}
