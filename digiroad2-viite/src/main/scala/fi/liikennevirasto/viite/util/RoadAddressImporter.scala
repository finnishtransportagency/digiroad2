package fi.liikennevirasto.viite.util

import java.sql.PreparedStatement

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import org.joda.time.format.ISODateTimeFormat
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHHistoryRoadLink, VVHRoadlink}
import org.joda.time._
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._


class RoadAddressImporter(conversionDatabase: DatabaseDef, vvhClient: VVHClient, importOptions: ImportOptions) {

  case class IncomingLrmPosistion(id: Long, linkId: Long, startM: Double, endM: Double, sideCode: SideCode, linkSource: LinkGeomSource, commonHistoryId: Long)
  case class IncomingRoadAddress(roadNumber: Long, roadPartNumber: Long, trackCode: Long, discontinuity: Long,
                                         startAddrM: Long, endAddrM: Long, startDate: DateTime, endDate: Option[DateTime],
                                         createdBy: String, validFrom: Option[DateTime], x1: Option[Double], y1: Option[Double],
                                         x2: Option[Double], y2: Option[Double], roadType: Long, ely: Long, commonHistoryId: Long)

  case class ConversionRoadAddress(roadNumber: Long, roadPartNumber: Long, trackCode: Long, discontinuity: Long,
                                           startAddrM: Long, endAddrM: Long, startM: Double, endM : Double, startDate: Option[DateTime], endDate: Option[DateTime],
                                           validFrom: Option[DateTime], validTo: Option[DateTime], ely: Long, roadType: Long,
                                           terminated: Long, linkId: Long, userId: String, x1: Option[Double], y1: Option[Double],
                                           x2: Option[Double], y2: Option[Double], lrmId: Long, commonHistoryId: Long, sideCode: SideCode)

  val dateFormatter = ISODateTimeFormat.basicDate()

  /**
    * Generate the specified amount of keys using the "lrm_position_primary_key_seq" sequence
    * @param amount The amount of keys needed
    * @return
    */
  private def generateLrmPositionIds(amount: Long): Seq[Long] = {
    val ids = sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level <= $amount""".as[Long].list
    assert(ids.size == amount || amount == 0)
    ids
  }

  def printConversionRoadAddress(r: ConversionRoadAddress): String = {
    s"""linkid: %d, alku: %.2f, loppu: %.2f, tie: %d, aosa: %d, ajr: %d, ely: %d, tietyyppi: %d, jatkuu: %d, aet: %d, let: %d, alkupvm: %s, loppupvm: %s, kayttaja: %s, muutospvm or rekisterointipvm: %s, ajorataId: %s""".
      format(r.linkId, r.startM, r.endM, r.roadNumber, r.roadPartNumber, r.trackCode, r.ely, r.roadType, r.discontinuity, r.startAddrM, r.endAddrM, r.startDate, r.endDate, r.userId, r.validFrom, r.commonHistoryId)
  }

  private def lrmPositionStatement() =
    dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure, link_source) values (?, ?, ?, ?, ?, ?)")

  private def insertLrmPosition(lrmPositionStatement: PreparedStatement, lrmPosition: IncomingLrmPosistion, lrmId: Long): Unit ={
    lrmPositionStatement.setLong(1, lrmId)
    lrmPositionStatement.setLong(2, lrmPosition.linkId)
    lrmPositionStatement.setLong(3, lrmPosition.sideCode.value)
    lrmPositionStatement.setDouble(4, lrmPosition.startM)
    lrmPositionStatement.setDouble(5, lrmPosition.endM)
    lrmPositionStatement.setLong(6, lrmPosition.linkSource.value)
    lrmPositionStatement.addBatch()
  }

  private def roadAddressStatement() =
    dynamicSession.prepareStatement("insert into ROAD_ADDRESS (id, lrm_position_id, road_number, road_part_number, " +
      "track_code, discontinuity, start_addr_m, end_addr_m, start_date, end_date, created_by, " +
      "valid_from, geometry, floating, road_type, ely, common_history_id) values (viite_general_seq.nextval, ?, ?, ?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), " +
      "TO_DATE(?, 'YYYY-MM-DD'), ?, TO_DATE(?, 'YYYY-MM-DD'), MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(" +
      "?,?,0.0,0.0,?,?,0.0,?)), ?, ?, ?, ?)")

  private def insertRoadAddress(roadAddressStatement: PreparedStatement, roadAddress: ConversionRoadAddress, lrmPosition: IncomingLrmPosistion, lrmId: Long): Unit ={
    def datePrinter(date: Option[DateTime]): String ={
      date match {
        case Some(dt) => dateFormatter.print(dt)
        case None => ""
      }
    }
    roadAddressStatement.setLong(1, lrmId)
    roadAddressStatement.setLong(2, roadAddress.roadNumber)
    roadAddressStatement.setLong(3, roadAddress.roadPartNumber)
    roadAddressStatement.setLong(4, roadAddress.trackCode)
    roadAddressStatement.setLong(5, roadAddress.discontinuity)
    roadAddressStatement.setLong(6, roadAddress.startAddrM)
    roadAddressStatement.setLong(7, roadAddress.endAddrM)
    roadAddressStatement.setString(8, dateFormatter.print(roadAddress.startDate.get))
    roadAddressStatement.setString(9, datePrinter(roadAddress.endDate))
    roadAddressStatement.setString(10, roadAddress.userId)
    roadAddressStatement.setString(11, datePrinter(roadAddress.validFrom))
    roadAddressStatement.setDouble(12, roadAddress.x1.get)
    roadAddressStatement.setDouble(13, roadAddress.y1.get)
    roadAddressStatement.setDouble(14, roadAddress.x2.get)
    roadAddressStatement.setDouble(15, roadAddress.y2.get)
    roadAddressStatement.setDouble(16, roadAddress.endAddrM - roadAddress.startAddrM)
    roadAddressStatement.setInt(17, if (lrmPosition.linkSource == LinkGeomSource.HistoryLinkInterface) 1 else 0)
    roadAddressStatement.setInt(17, 0)
    roadAddressStatement.setLong(18, roadAddress.roadType)
    roadAddressStatement.setLong(19, roadAddress.ely)
    roadAddressStatement.setLong(20, roadAddress.commonHistoryId)

    roadAddressStatement.addBatch()
  }

  private def fetchRoadLinksFromVVH(linkIds: Set[Long]): Map[Long, Seq[VVHRoadlink]] ={
    val vvhRoadLinkClient = if (importOptions.useFrozenLinkService) vvhClient.frozenTimeRoadLinkData else vvhClient.roadLinkData
    linkIds.grouped(4000).flatMap(group =>
      vvhRoadLinkClient.fetchByLinkIds(group) ++ vvhClient.complementaryData.fetchByLinkIds(group) ++ vvhClient.suravageData.fetchSuravageByLinkIds(group)
    ).toSeq.groupBy(_.linkId)
  }

  private def fetchHistoryRoadLinksFromVVH(linkIds: Set[Long]): Map[Long, VVHHistoryRoadLink] =
    vvhClient.historyData.fetchVVHRoadLinkByLinkIds(linkIds).groupBy(_.linkId).mapValues(_.maxBy(_.endDate))


  private def adjustLrmPosition(lrmPos: Seq[IncomingLrmPosistion], length: Double): Seq[IncomingLrmPosistion] = {
    val coefficient: Double = length / (lrmPos.maxBy(_.endM).endM - lrmPos.minBy(_.startM).startM)
    lrmPos.map(lrm => lrm.copy(startM = lrm.startM * coefficient, endM = lrm.endM * coefficient))
  }

  private def fetchRoadAddressFromConversionTable(ely: Long, filter: String): Seq[ConversionRoadAddress] ={
    conversionDatabase.withDynSession {
        val tableName = importOptions.conversionTable
        sql"""select tie, aosa, ajr, jatkuu, aet, let, alku, loppu, TO_CHAR(alkupvm, 'YYYY-MM-DD hh:mm:ss'), TO_CHAR(loppupvm, 'YYYY-MM-DD hh:mm:ss'),
               TO_CHAR(muutospvm, 'YYYY-MM-DD hh:mm:ss'), ely, tietyyppi, linkid, kayttaja, alkux, alkuy, loppux,
               loppuy, (linkid * 10000 + ajr * 1000 + aet) as id, ajorataid from #$tableName
               WHERE ely=$ely AND aet >= 0 AND let >= 0 AND lakkautuspvm IS NULL #$filter """
          .as[ConversionRoadAddress].list
    }
  }

  //TODO this method is duplicated (or almost)
  private def fetchRoadAddressFromConversionTable(minLinkId:Long, maxLinkId: Long, filter: String): Seq[ConversionRoadAddress] ={
    conversionDatabase.withDynSession {
      val tableName = importOptions.conversionTable
      sql"""select tie, aosa, ajr, jatkuu, aet, let, alku, loppu, TO_CHAR(alkupvm, 'YYYY-MM-DD hh:mm:ss'), TO_CHAR(loppupvm, 'YYYY-MM-DD hh:mm:ss'),
               TO_CHAR(muutospvm, 'YYYY-MM-DD hh:mm:ss'), ely, tietyyppi, linkid, kayttaja, alkux, alkuy, loppux,
               loppuy, (linkid * 10000 + ajr * 1000 + aet) as id, ajorataid from #$tableName
               WHERE linkid >= $minLinkId AND linkid <= $maxLinkId AND  aet >= 0 AND let >= 0 AND lakkautuspvm IS NULL #$filter """
        .as[ConversionRoadAddress].list
    }
  }

  private def fetchChunckLinkIdsFromConversionTable(chunck: Int): Seq[(Long, Long)] = {
    //TODO Try to do the group in the query
    val tableName = importOptions.conversionTable
    sql"""select distinct linkid from #$tableName order by linkid""".as[Long].list.
      grouped(chunck).map(linkIds => (linkIds.min, linkIds.max)).toSeq
  }

  private val withOnlyCurrentRoadAddress: String = " AND loppupvm IS NULL "
  private val withOnlyHistoryRoadAddress: String = " AND loppupvm IS NOT NULL "
  private val withCurrentAndHistoryRoadAddressFromDate: String = " AND (loppupvm IS NULL OR (loppupvm IS NOT NULL AND TO_CHAR(loppupvm, 'YYYY-MM-DD') <= '$date')) "
  private val withCurrentAndHistoryRoadAddress: String = ""

  def importRoadAddress(ely: Long): Unit ={

    val conversionRoadAddress = fetchRoadAddressFromConversionTable(ely, withCurrentAndHistoryRoadAddress)

    print(s"\n${DateTime.now()} - ")
    println("Read %d rows from conversion database for ELY %d".format(conversionRoadAddress.size, ely))

    importRoadAddress(conversionRoadAddress)

  }

  def importRoadAddress(): Unit ={
    //TODO the chunck count should be configurable
    fetchChunckLinkIdsFromConversionTable(20000).foreach {
      case (min, max) =>

        val conversionRoadAddress = fetchRoadAddressFromConversionTable(min, max, withCurrentAndHistoryRoadAddress)

        print(s"\n${DateTime.now()} - ")
        println("Read %d rows from conversion database".format(conversionRoadAddress.size))

        importRoadAddress(conversionRoadAddress)
    }
  }

  private def importRoadAddress(conversionRoadAddress: Seq[ConversionRoadAddress]): Unit = {

    val linkIds = conversionRoadAddress.map(_.linkId)

    print(s"${DateTime.now()} - ")
    println("Total of %d link ids".format(linkIds.size))

    val mappedRoadLinks = fetchRoadLinksFromVVH(linkIds.toSet)

    print(s"${DateTime.now()} - ")
    println("Read %d road links from vvh".format(mappedRoadLinks.size))

    val mappedHistoryRoadLinks = fetchHistoryRoadLinksFromVVH(linkIds.filterNot(linkId => mappedRoadLinks.get(linkId).isDefined).toSet)

    print(s"${DateTime.now()} - ")
    println("Read %d road links history from vvh".format(mappedHistoryRoadLinks.size))

    val suppressedRoadLinks = conversionRoadAddress.filter(ra => ra.linkId == 0 || (mappedRoadLinks.get(ra.linkId).isEmpty && mappedHistoryRoadLinks.get(ra.linkId).isEmpty))
    suppressedRoadLinks.foreach {
      ra => println("Suppressed row ID %d with reason 1: 'LINK-ID is not found in the VVH Interface' %s".format(ra.lrmId, printConversionRoadAddress(ra)))
    }

    val mappedConversionRoadAddress = conversionRoadAddress.
      groupBy(ra => (ra.linkId, ra.commonHistoryId))

    val incomingLrmPositions = mappedConversionRoadAddress.map(ra => ra._2.maxBy(_.startDate.get.getMillis)).flatMap {
      case(ra) =>
        mappedRoadLinks.getOrElse(ra.linkId, Seq()).headOption match {
          case Some(rl) =>
            Some(IncomingLrmPosistion(ra.lrmId, ra.linkId, ra.startM, ra.endM, ra.sideCode, rl.linkSource, ra.commonHistoryId))
          case _ =>
            mappedHistoryRoadLinks.get(ra.linkId) match {
              case Some(rl) =>
                Some(IncomingLrmPosistion(ra.lrmId, ra.linkId, ra.startM, ra.endM, ra.sideCode, LinkGeomSource.HistoryLinkInterface, ra.commonHistoryId))
              case _ => None
            }
        }
    }

    val allLinkGeomLength =
      mappedRoadLinks.map(ra => (ra._1, GeometryUtils.geometryLength(ra._2.head.geometry))) ++
      mappedHistoryRoadLinks.map(ra =>  (ra._1, GeometryUtils.geometryLength(ra._2.geometry)))

    val lrmPositions = allLinkGeomLength.flatMap {
      case (linkId, geomLength) =>
        adjustLrmPosition(incomingLrmPositions.filter(lrm => lrm.linkId == linkId).toSeq, geomLength)
    }

    val lrmIds = generateLrmPositionIds(lrmPositions.size)

    val lrmPositionPs = lrmPositionStatement()
    val roadAddressPs = roadAddressStatement()

    lrmPositions.zip(lrmIds).foreach {
      case ((lrmPosition), (lrmId)) =>
        val roadAddresses = mappedConversionRoadAddress.getOrElse((lrmPosition.linkId, lrmPosition.commonHistoryId), Seq())
        assert(roadAddresses.size >= 1)
        insertLrmPosition(lrmPositionPs, lrmPosition, lrmId)
        roadAddresses.foreach{
          roadAddress =>
            insertRoadAddress(roadAddressPs, roadAddress, lrmPosition, lrmId)
        }
    }

    lrmPositionPs.executeBatch()
    println(s"${DateTime.now()} - LRM Positions saved")
    roadAddressPs.executeBatch()
    println(s"${DateTime.now()} - Road addresses saved")
    lrmPositionPs.close()
    roadAddressPs.close()
  }

  implicit val getConversionRoadAddress = new GetResult[ConversionRoadAddress] {
    def apply(r: PositionedResult) = {
      val roadNumber = r.nextLong()
      val roadPartNumber = r.nextLong()
      val trackCode = r.nextLong()
      val discontinuity = r.nextLong()
      val startAddrM = r.nextLong()
      val endAddrM = r.nextLong()
      val startM = r.nextDouble()
      val endM = r.nextDouble()
      val startDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val endDateOption = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val validFrom = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val ely = r.nextLong()
      val roadType = r.nextLong()
      val linkId = r.nextLong()
      val userId =r.nextString
      val x1 = r.nextDouble()
      val y1 = r.nextDouble()
      val x2 = r.nextDouble()
      val y2 = r.nextDouble()
      val lrmId = r.nextLong()
      val commonHistoryId = r.nextLong()

      val viiteEndDate = endDateOption match {
        case Some(endDate) => Some(endDate.plusDays(1))
        case _ => None
      }

      if (startAddrM < endAddrM) {
        ConversionRoadAddress(roadNumber, roadPartNumber, trackCode, discontinuity, startAddrM, endAddrM, startM, endM, startDate, viiteEndDate, validFrom, None, ely, roadType, 0,
          linkId, userId, Option(x1), Option(y1), Option(x2), Option(y2), lrmId, commonHistoryId, SideCode.TowardsDigitizing)
      } else {
        //switch startAddrM, endAddrM, the geometry and set the side code to AgainstDigitizing
        ConversionRoadAddress(roadNumber, roadPartNumber, trackCode, discontinuity, endAddrM, startAddrM, startM, endM, startDate, viiteEndDate, validFrom, None, ely, roadType, 0,
          linkId, userId, Option(x2), Option(y2), Option(x1), Option(y1), lrmId, commonHistoryId, SideCode.AgainstDigitizing)
      }
    }
  }
}

