package fi.liikennevirasto.digiroad2.roadaddress.oracle

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.Point
import org.joda.time.DateTime

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery



case class RoadAddress(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, modifiedBy: Option[String] = None, lrmPositionId : Long, linkId: Long,
                       startMValue: Double, endMValue: Double, sideCode: SideCode, floating: Boolean = false, geom: Seq[Point])

object RoadAddressDAO {

    def getRoadAddress(queryFilter: String => String): Seq[RoadAddress] = {
      val query =
        s"""
           select ra.id, ra.road_number, ra.road_part_number, ra.track_code, ra.start_addr_m, ra.end_addr_m, ra.start_date, ra.end_date,
           ra.created_by, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure, pos.side_code,
           ra.floating,
           (SELECT X FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 1) as X,
           (SELECT Y FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 1) as Y,
           (SELECT X FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 2) as X2,
           (SELECT Y FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 2) as Y2
           from road_address ra
           join lrm_position pos on ra.lrm_position_id = pos.id"""

      val queryWithFilter = queryFilter(query) + " and (ra.valid_to > sysdate or ra.valid_to is null) "
      StaticQuery.queryNA[(Long, Long, Long, Int, Long, Long, Option[DateTime],
      Option[DateTime], Option[String], Long, Long, Double, Double, Int, Boolean, Double, Double, Double, Double)](queryWithFilter).list.map {
        case (id, roadNumber, roadPartNumber, track, startAddrMValue, endAddrMValue, startDate, endDate, modifiedBy,
        lrmPositionId, linkId, startMValue, endMValue, sideCode, floating, x, y, x2, y2) =>
          RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), startAddrMValue, endAddrMValue, startDate,
        endDate, modifiedBy, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode), floating, Seq(Point(x,y), Point(x2,y2)))
      }
    }

   def withRoadAddress(road: Long, roadPart: Long, track: Int, mValue: Double)(query: String): String = {
    query + s" WHERE ra.road_number = $road AND ra.road_part_number = $roadPart " +
      s"  AND ra.track_code = $track AND ra.start_addr_M <= $mValue AND ra.end_addr_M > $mValue"
  }

   def withLinkIdAndMeasure(linkId: Long, startM: Long, endM: Long, road: Option[Int] = None)(query: String): String = {

    val qfilter = (road) match {
      case Some(road) => "AND road_number = " + road
      case (_) => " "
    }
    query + s" WHERE pos.link_id = $linkId AND pos.start_Measure <= $startM AND pos.end_Measure > $endM " + qfilter
  }

}
