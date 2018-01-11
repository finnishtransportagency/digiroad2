package fi.liikennevirasto.digiroad2.roadaddress.oracle

import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.Point
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing, Unknown}
import fi.liikennevirasto.digiroad2.util.Track
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation


case class RoadAddress(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track, discontinuity: Int, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, lrmPositionId: Long, linkId: Long,
                       startMValue: Double, endMValue: Double, sideCode: SideCode, floating: Boolean = false, geom: Seq[Point],
                       expired: Boolean, createdBy: Option[String], createdDate: Option[DateTime], modifiedDate: Option[DateTime]) {
  def addressMValueToLRM(addrMValue: Long): Option[Double] = {
    if (addrMValue < startAddrMValue || addrMValue > endAddrMValue)
      None
    else
    // Linear approximation: addrM = a*mValue + b <=> mValue = (addrM - b) / a
      sideCode match {
        case TowardsDigitizing => Some((addrMValue - startAddrMValue) * lrmLength / addressLength + startMValue)
        case AgainstDigitizing => Some(endMValue - (addrMValue - startAddrMValue) * lrmLength / addressLength)
        case _ => None
      }
  }
  private val addressLength: Long = endAddrMValue - startAddrMValue
  private val lrmLength: Double = Math.abs(endMValue - startMValue)
}

case class RoadAddressRow(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track, discontinuity: Int, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                          endDate: Option[DateTime] = None, lrmPositionId: Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode, floating: Boolean = false,
                          geom: Seq[Point], expired: Boolean, createdBy: Option[String], createdDate: Option[DateTime], modifiedDate: Option[DateTime])

class RoadAddressDAO {

  def getRoadAddress(queryFilter: String => String): Seq[RoadAddress] = {
    val query =
      s"""
           select ra.id, ra.road_number, ra.road_part_number, ra.track_code, ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.start_date, ra.end_date,
           ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure, pos.side_code,
           ra.floating,
           (SELECT X FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 1) as X,
           (SELECT Y FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 1) as Y,
           (SELECT X FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 2) as X2,
           (SELECT Y FROM TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t WHERE id = 2) as Y2,
           case when ra.valid_to <= sysdate then 1 else 0 end as expired, ra.created_by, ra.start_date, pos.modified_date
           from road_address ra
           join lrm_position pos on ra.lrm_position_id = pos.id"""

    queryList(queryFilter(query))
  }

  def withRoadNumber(road: Long, roadPart:Long, track: Int)(query: String): String = {
    query + s" WHERE ra.road_number = $road AND ra.TRACK_CODE = $track AND ra.road_part_number = $roadPart and ra.floating = 0" + withValidatyCheck
  }

  def withRoadNumber(road: Long, trackCodes: Set[Int])(query: String): String = {
    query + s" WHERE ra.road_number = $road AND ra.TRACK_CODE in (${trackCodes.mkString(",")}) AND ra.floating = 0" + withValidatyCheck
  }

  def withRoadNumber(road: Long, roadPart:Long)(query: String): String = {
    query + s" WHERE ra.road_number = $road AND ra.road_part_number = $roadPart and ra.floating = 0" +withValidatyCheck
  }

  def withRoadAddress(road: Long, roadPart: Long, track: Int, mValue: Double)(query: String): String = {
    query + s" WHERE ra.road_number = $road AND ra.road_part_number = $roadPart " +
      s"  AND ra.track_code = $track AND ra.start_addr_M <= $mValue AND ra.end_addr_M > $mValue" + withValidatyCheck
  }

  def withLinkIdAndMeasure(linkId: Long, startM: Long, endM: Long, road: Option[Int] = None)(query: String): String = {

    val qfilter = (road) match {
      case Some(road) => "AND road_number = " + road
      case (_) => " "
    }
    query + s" WHERE pos.link_id = $linkId AND pos.start_Measure <= $startM AND pos.end_Measure > $endM " + qfilter + withValidatyCheck
  }

  def withRoadAddressSinglePart(roadNumber: Long, startRoadPartNumber: Long, track: Int, startM: Long, endM: Option[Long], optFloating: Option[Int] = None)(query: String): String = {
    val floating = optFloating match {
      case Some(floatingValue) => s"AND ra.floating = $floatingValue"
      case None => ""
    }

    val endAddr = endM match {
      case Some(endValue) => s"AND ra.start_addr_m <= $endValue"
      case _ => ""
    }

    query + s" where ra.road_number = $roadNumber " +
      s" AND (ra.road_part_number = $startRoadPartNumber AND ra.end_addr_m >= $startM $endAddr) " +
      s" AND ra.TRACK_CODE = $track " + floating + withValidatyCheck +
      s" ORDER BY ra.road_number, ra.road_part_number, ra.track_code, ra.start_addr_m "
  }

  def withRoadAddressTwoParts(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long, track: Int, startM: Long, endM: Long, optFloating: Option[Int] = None)(query: String): String = {
    val floating = optFloating match {
      case Some(floatingValue) => "ra.floating = " + floatingValue + ""
      case None => ""
    }

    query + s" where ra.road_number = $roadNumber " +
      s" AND ((ra.road_part_number = $startRoadPartNumber AND ra.end_addr_m >= $startM) OR (ra.road_part_number = $endRoadPartNumber AND ra.start_addr_m <= $endM)) " +
      s" AND ra.TRACK_CODE = $track " + floating + withValidatyCheck +
      s" ORDER BY ra.road_number, ra.road_part_number, ra.track_code, ra.start_addr_m "
  }

  def withRoadAddressMultiParts(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long, track: Int, startM: Double, endM: Double, optFloating: Option[Int] = None)(query: String): String = {
    val floating = optFloating match {
      case Some(floatingValue) => "ra.floating = " + floatingValue + ""
      case None => ""
    }

    query + s" where ra.road_number = $roadNumber " +
      s" AND ((ra.road_part_number = $startRoadPartNumber AND ra.end_addr_m >= $startM) " +
      s" OR (ra.road_part_number = $endRoadPartNumber AND ra.start_addr_m <= $endM) " +
      s" OR ((ra.road_part_number > $startRoadPartNumber) AND (ra.road_part_number < $endRoadPartNumber))) " +
      s" AND ra.TRACK_CODE = $track " + floating + withValidatyCheck +
      s" ORDER BY ra.road_number, ra.road_part_number, ra.track_code, ra.start_addr_m "
  }

  def withBetweenDates(sinceDate: DateTime, untilDate: DateTime)(query: String): String = {
    query + s" WHERE ra.start_date >= CAST(TO_TIMESTAMP_TZ(REPLACE(REPLACE('$sinceDate', 'T', ''), 'Z', ''), 'YYYY-MM-DD HH24:MI:SS.FFTZH:TZM') AS DATE)" +
      s" AND ra.start_date <= CAST(TO_TIMESTAMP_TZ(REPLACE(REPLACE('$untilDate', 'T', ''), 'Z', ''), 'YYYY-MM-DD HH24:MI:SS.FFTZH:TZM') AS DATE)"
  }

  def withValidatyCheck(): String = {
    s" AND (ra.valid_to IS NULL OR ra.valid_to > sysdate) AND (ra.valid_from IS NULL OR ra.valid_from <= sysdate) " +
    s" AND (ra.end_date IS NULL OR ra.end_date > sysdate) AND (ra.start_date IS NULL OR ra.start_date <= sysdate) "
  }

  def getRoadNumbers(): Seq[Long] = {
    sql"""
			select distinct (ra.road_number)
      from road_address ra
      where ra.valid_to is null OR ra.valid_to > SYSDATE
		  """.as[Long].list
  }

  def getRoadAddressesFiltered(roadNumber: Long, roadPartNumber: Long, startM: Double, endM: Double): Seq[RoadAddress] = {
    val where =
      s""" where (( ra.start_addr_m >= $startM and ra.end_addr_m <= $endM ) or ( $startM >= ra.start_addr_m and $startM < ra.end_addr_m) or
         ( $endM > ra.start_addr_m and $endM <= ra.end_addr_m)) and ra.road_number= $roadNumber and ra.road_part_number= $roadPartNumber
         and ra.floating = 0 """ + withValidatyCheck

    val query =
      s"""
			select distinct ra.id, ra.road_number, ra.road_part_number, ra.track_code,
      ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.start_date, ra.end_date, ra.lrm_position_id, pos.link_id,
      pos.start_measure, pos.end_measure, pos.side_code, ra.floating,
      case when ra.valid_to <= sysdate then 1 else 0 end as expired, ra.created_by, ra.start_date, pos.modified_date
      from road_address ra
      join lrm_position pos on ra.lrm_position_id = pos.id
      $where
      """
    queryRoadAddresses(query)
  }

  def getByLinkIdAndMeasures(linkId: Long, startM: Double, endM: Double): List[RoadAddress] = {

    val where =
      s""" where pos.link_id = $linkId and
         (( pos.start_measure >= $startM and pos.end_measure <= $endM ) or
         ( $endM >= pos.start_measure and $endM <= pos.end_measure)) """

    val query =
      s"""
			 select ra.id, ra.road_number, ra.road_part_number, ra.track_code,
       ra.discontinuity, ra.start_addr_m, ra.end_addr_m, ra.start_date, ra.end_date, ra.lrm_position_id, pos.link_id, pos.start_measure, pos.end_measure,
       pos.side_code, ra.floating, t.X, t.Y, t2.X, t2.Y,
       case when ra.valid_to <= sysdate then 1 else 0 end as expired, ra.created_by, ra.start_date, pos.modified_date
       from road_address ra cross join
       TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t cross join
       TABLE(SDO_UTIL.GETVERTICES(ra.geometry)) t2
       join lrm_position pos on ra.lrm_position_id = pos.id
			 $where
		  """

    queryList(query)
  }

  implicit val getTrack = GetResult[Track](r => Track.apply(r.nextInt()))

  implicit val getRoadAddressRow = new GetResult[RoadAddressRow] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong
      val roadNumber = r.nextLong
      val roadPartNumber = r.nextLong
      val track = r.nextInt
      val discontinuity = r.nextInt
      val startAddrMValue = r.nextLong
      val endAddrMValue = r.nextLong
      val startDate = r.nextTimestampOption().map(new DateTime(_))
      val endDate = r.nextTimestampOption().map(new DateTime(_))
      val lrmPositionId = r.nextLong
      val linkId = r.nextLong
      val startMValue = r.nextDouble
      val endMValue = r.nextDouble
      val sideCode = r.nextInt
      val floating = r.nextBoolean
      val geom = Seq(Point(x = r.nextDouble, y = r.nextDouble), Point(x = r.nextDouble, y = r.nextDouble))
      val expired = r.nextBoolean
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(new DateTime(_))
      val modifiedDate = r.nextTimestampOption().map(new DateTime(_))
      RoadAddressRow(id, roadNumber, roadPartNumber, Track.apply(track), discontinuity,
        startAddrMValue, endAddrMValue, startDate, endDate, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode),
        floating, geom, expired, createdBy, createdDate, modifiedDate)
    }
  }
  private def queryList(query: String) = {
    val resultList = Q.queryNA[RoadAddressRow](query).list

    resultList.map {row =>
      RoadAddress(id = row.id, roadNumber = row.roadNumber, roadPartNumber = row.roadPartNumber, track = row.track, discontinuity = row.discontinuity, startAddrMValue = row.startAddrMValue, endAddrMValue = row.endAddrMValue, startDate = row.startDate,
      endDate = row.endDate, lrmPositionId = row.lrmPositionId, linkId = row.linkId, startMValue = row.startMValue, endMValue = row.endMValue, sideCode = row.sideCode, floating = row.floating,
      geom = row.geom, expired = row.expired, createdBy = row.createdBy, createdDate = row.createdDate, modifiedDate = row.modifiedDate)
    }
  }

  private def queryRoadAddresses(query: String) = {
    val tuples = Q.queryNA[(Long, Long, Long, Int, Int, Long, Long, Option[DateTime],
      Option[DateTime], Long, Long, Double, Double, Int,
      Boolean, Boolean, Option[String], Option[DateTime], Option[DateTime])](query).list

    tuples.map {
      case (id, roadNumber, roadPartNumber, track, discontinuity, startAddrMValue, endAddrMValue, startDate, endDate, lrmPositionId,
      linkId, startMValue, endMValue, sideCode, floating, expired, createdBy, createdDate, modifiedDate) =>

        RoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), discontinuity,
          startAddrMValue, endAddrMValue, startDate, endDate, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode),
          floating, Seq(), expired, createdBy, createdDate, modifiedDate)
    }
  }

}
