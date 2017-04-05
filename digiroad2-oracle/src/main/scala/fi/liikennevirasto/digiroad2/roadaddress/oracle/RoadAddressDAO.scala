package fi.liikennevirasto.digiroad2.roadaddress.oracle

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.Track
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, StaticQuery => Q}


case class RoadAddress(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track,
                            discontinuity: Int, startAddrMValue: Long, endAddrMValue: Long,
                            lrmPositionId : Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                            floating: Boolean = false, geom: Seq[Point])
class RoadAddressDAO {

  def fetchRoadAddresses() : Seq[RoadAddress] = {
    val query =
      s"""
			 select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
       ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id,
       pos.start_measure, pos.end_measure, pos.side_code, ra.floating
       from road_address ra
       join lrm_position pos on ra.lrm_position_id = pos.id
		  """

    queryRoadAddresses(query)
  }


  def fetchByLinkIdAndMeasures(linkId: Long, startM: Double, endM: Double):  List[RoadAddress] = {

    val where =
      s""" where pos.link_id = $linkId and
         (( pos.start_measure >= $startM and pos.end_measure <= $endM ) or
         ( $endM >= pos.start_measure and $endM <= pos.end_measure)) """

    val query =
      s"""
			 select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
       ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
       pos.side_code, ra.floating, t.X, t.Y, t2.X, t2.Y
       from road_address ra cross join
       TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
       TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
       join lrm_position pos on ra.lrm_position_id = pos.id
			 $where
		  """

    queryList(query)
  }

  implicit val getTrack = GetResult[Track]( r=> Track.apply(r.nextInt()))

  private def queryList(query: String) = {
    val tuples = Q.queryNA[(Long, Long, Long, Int, Int, Long, Long, Long, Long, Double, Double, Int,
      Boolean, Double, Double, Double, Double)](query).list

    tuples.map {
      case (id, roadNumber, roadPartNumber, track, discontinuity, startAddrMValue, endAddrMValue, lrmPositionId,
      linkId, startMValue, endMValue, sideCode, floating, x, y, x2, y2) =>

        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), discontinuity,
          startAddrMValue, endAddrMValue, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode),
          floating, Seq(Point(x,y), Point(x2,y2)))


    }
  }

  private def queryRoadAddresses(query: String) = {
    val tuples = Q.queryNA[(Long, Long, Long, Int, Int, Long, Long, Long, Long, Double, Double, Int,
      Boolean)](query).list

    tuples.map {
      case (id, roadNumber, roadPartNumber, track, discontinuity, startAddrMValue, endAddrMValue, lrmPositionId,
      linkId, startMValue, endMValue, sideCode, floating) =>

        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), discontinuity,
          startAddrMValue, endAddrMValue, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode),
          floating, Seq())


    }
  }
}
