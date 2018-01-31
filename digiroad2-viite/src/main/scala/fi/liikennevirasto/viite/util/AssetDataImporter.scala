package fi.liikennevirasto.viite.util

import java.util.Properties
import javax.sql.DataSource

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import org.joda.time.format.{ISODateTimeFormat, PeriodFormat}
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import _root_.oracle.sql.STRUCT
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{Queries, SequenceResetterDAO}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, GeometryUtils}
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.{RoadAddressLinkBuilder, RoadAddressService, RoadType}
import org.joda.time.{DateTime, _}
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver
import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._


object
AssetDataImporter {
  sealed trait ImportDataSet {
    def database(): DatabaseDef
  }

  case object TemporaryTables extends ImportDataSet {
    lazy val dataSource: DataSource = {
      val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/import.bonecp.properties"))
      new BoneCPDataSource(cfg)
    }

    def database() = Database.forDataSource(dataSource)
  }

  case object Conversion extends ImportDataSet {
    lazy val dataSource: DataSource = {
      val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/conversion.bonecp.properties"))
      new BoneCPDataSource(cfg)
    }

    def database() = Database.forDataSource(dataSource)
    val roadLinkTable: String = "tielinkki"
    val busStopTable: String = "lineaarilokaatio"
  }

  def humanReadableDurationSince(startTime: DateTime): String = {
    PeriodFormat.getDefault.print(new Period(startTime, DateTime.now()))
  }
}

protected case class RoadAddressHistory(roadNumber: Long, roadPartNumber: Long, trackCode: Long, discontinuity: Long,
                                        startAddrM: Long, endAddrM: Long, startM: Double, endM: Double, startDate: Option[DateTime], endDate: Option[DateTime],
                                        validFrom: Option[DateTime], validTo: Option[DateTime], ely: Long, roadType: Long,
                                        terminated: Long, linkId: Long, userId: String, x1: Option[Double], y1: Option[Double],
                                        x2: Option[Double], y2: Option[Double], lrmId: Long, ajrId: Long)

class AssetDataImporter {
  val logger = LoggerFactory.getLogger(getClass)
  lazy val ds: DataSource = initDataSource

  val Modifier = "dr1conversion"

  def withDynTransaction(f: => Unit): Unit = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  implicit object SetStruct extends SetParameter[STRUCT] {
    def apply(v: STRUCT, pp: PositionedParameters) {
      pp.setObject(v, java.sql.Types.STRUCT)
    }
  }

  def time[A](f: => A) = {
    val s = System.nanoTime
    val ret = f
    println("time for insert " + (System.nanoTime - s) / 1e6 + "ms")
    ret
  }

  val dateFormatter = ISODateTimeFormat.basicDate()

  def getBatchDrivers(n: Int, m: Int, step: Int): List[(Int, Int)] = {
    if ((m - n) < step) {
      List((n, m))
    } else {
      val x = (n to m by step).sliding(2).map(x => (x(0), x(1) - 1)).toList
      x :+ (x.last._2 + 1, m)
    }
  }

  case class LRMPos(id: Long, linkId: Long, startM: Double, endM: Double, linkSource: LinkGeomSource, ajrId: Long)
  case class RoadTypeChangePoints(roadNumber: Long, roadPartNumber: Long, addrM: Long, before: RoadType, after: RoadType, elyCode: Long)

  /**
    * Get road type for road address object with a list of road type change points
    *
    * @param changePoints Road part change points for road types
    * @param roadAddress Road address to get the road type for
    * @return road type for the road address or if a split is needed then a split point (address) and road types for first and second split
    */
  def roadType(changePoints: Seq[RoadTypeChangePoints], roadAddress: RoadAddress): Either[RoadType, (Long, RoadType, RoadType)] = {
    // Check if this road address overlaps the change point and needs to be split
    val overlaps = changePoints.find(c => c.addrM > roadAddress.startAddrMValue && c.addrM < roadAddress.endAddrMValue)
    if (overlaps.nonEmpty)
      Right((overlaps.get.addrM, overlaps.get.before, overlaps.get.after))
    else {
      // There is no overlap, check if this road address is between [0, min(addrM))
      if (roadAddress.startAddrMValue < changePoints.map(_.addrM).min) {
        Left(changePoints.minBy(_.addrM).before)
      } else {
        Left(changePoints.filter(_.addrM <= roadAddress.startAddrMValue).maxBy(_.addrM).after)
      }
    }

  }

  implicit val getRoadAddressHistory = new GetResult[RoadAddressHistory] {
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
      val endDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val validFrom = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val ely = r.nextLong()
      val roadType = r.nextLong()
      val linkId = r.nextLong()
      val userId = r.nextString
      val x1 = r.nextDouble()
      val y1 = r.nextDouble()
      val x2 = r.nextDouble()
      val y2 = r.nextDouble()
      val lrmId = r.nextLong()
      val ajrId = r.nextLong()

      RoadAddressHistory(roadNumber, roadPartNumber, trackCode, discontinuity, startAddrM, endAddrM, startM, endM, startDate, endDate, validFrom, None, ely, roadType, 0,
        linkId, userId, Option(x1), Option(y1), Option(x2), Option(y2), lrmId, ajrId)

    }
  }

  protected def fetchRoadAddressHistory(conversionDatabase: JdbcDriver.backend.DatabaseDef, ely: Int, importOptions: ImportOptions): List[RoadAddressHistory] = {
    conversionDatabase.withDynSession {
      val tableName = importOptions.conversionTable
      val where = s" WHERE ely=$ely AND aet >= 0 AND let >= 0 AND lakkautuspvm IS NULL "
      val filter = (importOptions.onlyCurrentRoads, importOptions.importDate) match {
        case (true, _) => s" AND loppupvm IS NULL "
        case (false, "") => ""
        case (false, date) => s" AND (loppupvm IS NULL OR (loppupvm IS NOT NULL AND TO_CHAR(loppupvm, 'YYYY-MM-DD') <= '$date')) "
      }
      sql"""select tie, aosa, ajr, jatkuu, aet, let, alku, loppu, TO_CHAR(alkupvm, 'YYYY-MM-DD hh:mm:ss'), TO_CHAR(loppupvm, 'YYYY-MM-DD hh:mm:ss'),
             TO_CHAR(muutospvm, 'YYYY-MM-DD hh:mm:ss'), ely, tietyyppi, linkid, kayttaja, alkux, alkuy, loppux,
             loppuy, (linkid * 10000 + ajr * 1000 + aet) as id, ajorataid from #$tableName #$where #$filter """
        .as[RoadAddressHistory].list
    }
  }

  def importRoadAddressData(conversionDatabase: DatabaseDef, vvhClient: VVHClient, vvhClientProd: Option[VVHClient],
                            importOptions: ImportOptions): Unit = {

    OracleDatabase.withDynTransaction {
      sqlu"""ALTER TABLE ROAD_ADDRESS DISABLE ALL TRIGGERS""".execute
      sqlu"""DELETE FROM ROAD_ADDRESS""".execute
      sqlu"""DELETE FROM ROAD_ADDRESS_CHANGES""".execute
      sqlu"""DELETE FROM PROJECT_LINK""".execute
      sqlu"""DELETE FROM PROJECT_LINK_HISTORY""".execute
      sqlu"""DELETE FROM PROJECT_RESERVED_ROAD_PART""".execute
      sqlu"""DELETE FROM PROJECT""".execute
      sqlu"""DELETE FROM ROAD_NETWORK_ERRORS""".execute
      sqlu"""DELETE FROM PUBLISHED_ROAD_ADDRESS""".execute
      sqlu"""DELETE FROM LRM_POSITION WHERE
            NOT EXISTS (SELECT POSITION_ID FROM ASSET_LINK WHERE POSITION_ID=LRM_POSITION.ID)""".execute
      println(s"${DateTime.now()} - Old address data removed")

      val roadAddressImporter = getRoadAddressImporter(conversionDatabase, vvhClient, importOptions)
      //roadMaintainerElys.foreach(ely => roadAddressImporter.importRoadAddress(ely))
      roadAddressImporter.importRoadAddress()

      println(s"${DateTime.now()} - Updating geometry adjustment timestamp to ${importOptions.geometryAdjustedTimeStamp}")
      sqlu"""UPDATE LRM_POSITION
        SET ADJUSTED_TIMESTAMP = ${importOptions.geometryAdjustedTimeStamp} WHERE ID IN (SELECT LRM_POSITION_ID FROM ROAD_ADDRESS)""".execute

      println(s"${DateTime.now()} - Updating calibration point information")
      // both dates are open-ended or there is overlap (checked with inverse logic)
      sqlu"""UPDATE ROAD_ADDRESS
        SET CALIBRATION_POINTS = 1
        WHERE NOT EXISTS(SELECT 1 FROM ROAD_ADDRESS RA2 WHERE RA2.ID != ROAD_ADDRESS.ID AND
        RA2.ROAD_NUMBER = ROAD_ADDRESS.ROAD_NUMBER AND
        RA2.ROAD_PART_NUMBER = ROAD_ADDRESS.ROAD_PART_NUMBER AND
        RA2.START_ADDR_M = ROAD_ADDRESS.END_ADDR_M AND
        RA2.TRACK_CODE = ROAD_ADDRESS.TRACK_CODE AND
        (ROAD_ADDRESS.END_DATE IS NULL AND RA2.END_DATE IS NULL OR
        NOT (RA2.END_DATE < ROAD_ADDRESS.START_DATE OR RA2.START_DATE > ROAD_ADDRESS.END_DATE)))""".execute
      sqlu"""UPDATE ROAD_ADDRESS
        SET CALIBRATION_POINTS = CALIBRATION_POINTS + 2
          WHERE
            START_ADDR_M = 0 OR
            NOT EXISTS(SELECT 1 FROM ROAD_ADDRESS RA2 WHERE RA2.ID != ROAD_ADDRESS.ID AND
              RA2.ROAD_NUMBER = ROAD_ADDRESS.ROAD_NUMBER AND
              RA2.ROAD_PART_NUMBER = ROAD_ADDRESS.ROAD_PART_NUMBER AND
              RA2.END_ADDR_M = ROAD_ADDRESS.START_ADDR_M AND
              RA2.TRACK_CODE = ROAD_ADDRESS.TRACK_CODE AND
              (ROAD_ADDRESS.END_DATE IS NULL AND RA2.END_DATE IS NULL OR
                NOT (RA2.END_DATE < ROAD_ADDRESS.START_DATE OR RA2.START_DATE > ROAD_ADDRESS.END_DATE)
              )
            )""".execute
      sqlu"""ALTER TABLE ROAD_ADDRESS ENABLE ALL TRIGGERS""".execute
      commonHistoryResetter()
    }
  }

  def commonHistoryResetter(): Unit = {
    val sequenceResetter = new SequenceResetterDAO()
    sql"""select MAX(common_history_id) FROM ROAD_ADDRESS""".as[Long].firstOption match {
      case Some(commonHistoryId) =>
        sequenceResetter.resetSequenceToNumber("common_history", commonHistoryId + 1)
      case _ => sequenceResetter.resetSequenceToNumber("common_history", 1)
    }
  }

  protected def getRoadAddressImporter(conversionDatabase: DatabaseDef, vvhClient: VVHClient, importOptions: ImportOptions) = {
    new RoadAddressImporter(conversionDatabase, vvhClient, importOptions)
  }

  def updateRoadAddressesValues(conversionDatabase: DatabaseDef, vvhClient: VVHClient) = {
    val eventBus = new DummyEventBus
    val linkService = new RoadLinkService(vvhClient, eventBus, new DummySerializer)

    val roadsWithMultipleValues = conversionDatabase.withDynSession {
      sql"""SELECT t.tie, t.aosa, ajr, greatest(aet,let) as addrM, tietyyppi as type_before, (SELECT tietyyppi FROM VVH_TIEOSOITE_NYKY u WHERE u.tie = t.tie AND u.aosa=t.aosa AND (u.ajr = t.ajr OR u.ajr=0 OR t.ajr=0)
            AND ((u.aet = t.let and t.aet < t.let) or (u.let = t.aet and t.aet > t.let) ) AND ROWNUM < 2) type_after, ely FROM VVH_TIEOSOITE_NYKY t
            JOIN (SELECT gr.TIE, gr.AOSA, count(distinct TIETYYPPI) FROM VVH_TIEOSOITE_NYKY gr group by gr.tie, gr.aosa having count(distinct TIETYYPPI) > 1) foo ON
            (foo.tie = t.tie AND foo.aosa = t.aosa)
            WHERE (  EXISTS (SELECT 1 FROM VVH_TIEOSOITE_NYKY chg WHERE chg.tie = t.tie and chg.aosa=t.aosa and
            (chg.ajr = t.ajr OR chg.ajr=0 OR t.ajr=0) AND  ((chg.aet = t.let and t.aet < t.let) or (chg.let = t.aet and t.aet > t.let) )
            AND chg.tietyyppi != t.tietyyppi)) order by tie, aosa, addrM, ajr
         """.as[(Long, Long, Long, Long, Int, Int, Int)].list.map {
        case (roadNumber, roadPartNumber, trackCode, addressMChangeRoadType, roadTypeBefore, roadTypeAfter, ely) =>
          RoadTypeChangePoints(roadNumber, roadPartNumber, addressMChangeRoadType, RoadType.apply(roadTypeBefore), RoadType.apply(roadTypeAfter), ely)
      }
    }

    val roadsWithSingleRoadType = conversionDatabase.withDynSession {
      sql""" select distinct tie, aosa, tietyyppi, ely from VVH_TIEOSOITE_NYKY
             where (tie, aosa) in(
             SELECT gr.TIE, gr.AOSA FROM VVH_TIEOSOITE_NYKY gr  group by gr.tie, gr.aosa  having count(distinct gr.TIETYYPPI) = 1)
              union
            select distinct tie, aosa, tietyyppi, ely from vvh_tieosoite_taydentava
            where (tie, aosa) in(
            SELECT t.TIE, t.AOSA FROM vvh_tieosoite_taydentava t  group by t.tie, t.aosa  having count(distinct t.TIETYYPPI) = 1)
            order by tie, aosa
        """.as[(Long, Long, Long, Long)].list
    }

    withDynTransaction{
      roadsWithSingleRoadType.foreach(road => {
        updateRoadWithSingleRoadType(road._1, road._2, road._3, road._4)
      })

      roadsWithMultipleValues.groupBy(m => (m.roadNumber, m.roadPartNumber)).foreach{ case ((roadNumber, roadPartNumber), roadMaps) => {
        val addresses = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber, true, true)
        println(s"updating tie = $roadNumber, aosa = $roadPartNumber: (${addresses.size} rows)")
        addresses.foreach(address => {
          val ely = roadMaps.head.elyCode
          roadType(roadMaps, address) match {
            case Left(roadType) =>
              sqlu"""UPDATE ROAD_ADDRESS SET ROAD_TYPE = ${roadType.value}, ELY= $ely where ID = ${address.id}""".execute
            case Right((addrM, roadTypeBefore, roadTypeAfter)) =>
              val roadLinkFromVVH = linkService.getCurrentAndComplementaryAndSuravageRoadLinksFromVVH(Set(address.linkId), false)
              if (roadLinkFromVVH.isEmpty)
                println(s"WARNING! LinkId ${address.linkId} not found in current, complementary or suravage links list, using address geometry")
              val splittedRoadAddresses = splitRoadAddresses(address.copy(geometry = roadLinkFromVVH.headOption.map(_.geometry).getOrElse(address.geometry)), addrM, roadTypeBefore, roadTypeAfter, ely)
              println(s"Split ${address.id} ${address.startMValue}-${address.endMValue} (${address.startAddrMValue}-${address.endAddrMValue}) into")
              println(s"  ${splittedRoadAddresses.head.startMValue}-${splittedRoadAddresses.head.endMValue} (${splittedRoadAddresses.head.startAddrMValue}-${splittedRoadAddresses.head.endAddrMValue}) and")
              println(s"  ${splittedRoadAddresses.last.startMValue}-${splittedRoadAddresses.last.endMValue} (${splittedRoadAddresses.last.startAddrMValue}-${splittedRoadAddresses.last.endAddrMValue})")
              RoadAddressDAO.expireById(Set(address.id))
              sqlu"""UPDATE ROAD_ADDRESS SET ROAD_TYPE = 99, ELY= $ely where ID = ${address.id}""".execute
              RoadAddressDAO.create(splittedRoadAddresses)
          }
        })
      }}
    }

  }

  def splitRoadAddresses(roadAddress: RoadAddress, addrMToSplit: Long, roadTypeBefore: RoadType, roadTypeAfter: RoadType, elyCode: Long): Seq[RoadAddress] = {
    // mValue at split point on a TowardsDigitizing road address:
    val splitMValue = roadAddress.startMValue + (roadAddress.endMValue - roadAddress.startMValue) / (roadAddress.endAddrMValue - roadAddress.startAddrMValue) * (addrMToSplit - roadAddress.startAddrMValue)
    println(s"Splitting road address id = ${roadAddress.id}, tie = ${roadAddress.roadNumber} and aosa = ${roadAddress.roadPartNumber}, on AddrMValue = $addrMToSplit")
    val roadAddressA = roadAddress.copy(id = fi.liikennevirasto.viite.NewRoadAddress, roadType = roadTypeBefore, endAddrMValue = addrMToSplit, startMValue = if (roadAddress.sideCode == SideCode.AgainstDigitizing)
            roadAddress.endMValue - splitMValue
          else
            0.0, endMValue = if (roadAddress.sideCode == SideCode.AgainstDigitizing)
            roadAddress.endMValue
          else
            splitMValue, geometry = GeometryUtils.truncateGeometry2D(roadAddress.geometry, 0.0, splitMValue), ely = elyCode)

    val roadAddressB = roadAddress.copy(id = fi.liikennevirasto.viite.NewRoadAddress, roadType = roadTypeAfter, startAddrMValue = addrMToSplit, startMValue = if (roadAddress.sideCode == SideCode.AgainstDigitizing)
            0.0
          else
            splitMValue, endMValue = if (roadAddress.sideCode == SideCode.AgainstDigitizing)
            roadAddress.endMValue - splitMValue
          else
            roadAddress.endMValue, geometry = GeometryUtils.truncateGeometry2D(roadAddress.geometry, splitMValue, roadAddress.endMValue), ely = elyCode)
    Seq(roadAddressA, roadAddressB)
  }

  def updateRoadWithSingleRoadType(roadNumber:Long, roadPartNumber: Long, roadType : Long, elyCode :Long) = {
    println(s"Updating road number $roadNumber and part $roadPartNumber with roadType = $roadType and elyCode = $elyCode")
    sqlu"""UPDATE ROAD_ADDRESS SET ROAD_TYPE = ${roadType}, ELY= ${elyCode} where ROAD_NUMBER = ${roadNumber} AND ROAD_PART_NUMBER = ${roadPartNumber} """.execute
  }

  def updateMissingRoadAddresses(vvhClient: VVHClient) = {
    val roadNumbersToFetch = Seq((1, 19999), (40000,49999))
    val eventBus = new DummyEventBus
    val linkService = new RoadLinkService(vvhClient, eventBus, new DummySerializer)
    val service = new RoadAddressService(linkService, eventBus)
    RoadAddressLinkBuilder.municipalityMapping               // Populate it beforehand, because it can't be done in nested TX
    RoadAddressLinkBuilder.municipalityRoadMaintainerMapping // Populate it beforehand, because it can't be done in nested TX
    OracleDatabase.withDynTransaction {
      val municipalities = Queries.getMunicipalitiesWithoutAhvenanmaa
      sqlu"""DELETE FROM MISSING_ROAD_ADDRESS""".execute
      println("Old address data cleared")
      municipalities.foreach(municipality => {
        println("Processing municipality %d at time: %s".format(municipality, DateTime.now().toString))
        val missing = service.getMissingRoadAddresses(roadNumbersToFetch, municipality)
        println("Got %d links".format(missing.size))
        missing.foreach(service.createSingleMissingRoadAddress)
        println("Municipality %d: %d links added at time: %s".format(municipality, missing.size, DateTime.now().toString))
      })
    }
  }

  def updateRoadAddressesGeometry(vvhClient: VVHClient, filterRoadAddresses: Boolean) = {
    val eventBus = new DummyEventBus
    val linkService = new RoadLinkService(vvhClient, eventBus, new DummySerializer)
    var counter = 0
    var changed = 0
    OracleDatabase.withDynTransaction {
      val roadNumbers = RoadAddressDAO.getCurrentValidRoadNumbers(if (filterRoadAddresses)
        "AND (ROAD_NUMBER <= 20000 or (road_number >= 40000 and road_number <= 70000))" else "")
      roadNumbers.foreach(roadNumber =>{
        counter += 1
        println("Processing roadNumber %d (%d of %d) at time: %s".format(roadNumber, counter, roadNumbers.size,  DateTime.now().toString))
        val linkIds = RoadAddressDAO.fetchByRoad(roadNumber).map(_.linkId).toSet
        val roadLinksFromVVH = linkService.getCurrentAndComplementaryAndSuravageRoadLinksFromVVH(linkIds, false)
        val addresses = RoadAddressDAO.fetchByLinkId(roadLinksFromVVH.map(_.linkId).toSet, false, true).groupBy(_.linkId)

        roadLinksFromVVH.foreach(roadLink => {
          val segmentsOnViiteDatabase = addresses.getOrElse(roadLink.linkId, Set())
          segmentsOnViiteDatabase.foreach(segment =>{
              val newGeom = GeometryUtils.truncateGeometry3D(roadLink.geometry, segment.startMValue, segment.endMValue)
            if (!segment.geometry.equals(Nil) && !newGeom.equals(Nil)) {

              if (((segment.geometry.head.distance2DTo(newGeom.head) > 1) && (segment.geometry.head.distance2DTo(newGeom.last) > 1)) ||
                ((segment.geometry.last.distance2DTo(newGeom.head) > 1) && (segment.geometry.last.distance2DTo(newGeom.last) > 1))) {
                RoadAddressDAO.updateGeometry(segment.id, newGeom)
                println("Changed geometry on roadAddress id " + segment.id + " and linkId ="+ segment.linkId)
                changed +=1
              }
            }
          })
        })

        println("RoadNumber:  %d: %d roadAddresses updated at time: %s".format(roadNumber, addresses.size, DateTime.now().toString))

      })
      println("Geometries changed count: %d", changed)

    }
  }

  private[this] def initDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val cfg = new BoneCPConfig(localProperties)
    new BoneCPDataSource(cfg)
  }

  lazy val localProperties: Properties = {
    val props = new Properties()
    try {
      props.load(getClass.getResourceAsStream("/bonecp.properties"))
    } catch {
      case e: Exception => throw new RuntimeException("Can't load local.properties for env: " + System.getProperty("env"), e)
    }
    props
  }

}

case class ImportOptions(onlyComplementaryLinks: Boolean, useFrozenLinkService: Boolean, geometryAdjustedTimeStamp: Long, conversionTable: String, importDate: String, onlyCurrentRoads: Boolean)
case class RoadPart(roadNumber: Long, roadPart: Long, ely: Long)

