package fi.liikennevirasto.viite.util

import java.util.Properties
import javax.sql.DataSource

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.pointasset.oracle.{Obstacle, OracleObstacleDao}
import org.joda.time.format.PeriodFormat
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.{SimpleBusStop, _}
import fi.liikennevirasto.digiroad2.util.VVHSerializer
import fi.liikennevirasto.viite.{RoadAddressLinkBuilder, RoadAddressService}
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}
import org.joda.time._
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._

import scala.collection.mutable

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
    println("time for insert "+(System.nanoTime-s)/1e6+"ms")
    ret
  }

  private def getBatchDrivers(size: Int): List[(Int, Int)] = {
    println(s"""creating batching for $size items""")
    getBatchDrivers(1, size, 500)
  }

  def getBatchDrivers(n: Int, m: Int, step: Int): List[(Int, Int)] = {
    if ((m - n) < step) {
      List((n, m))
    } else {
      val x = ((n to m by step).sliding(2).map(x => (x(0), x(1) - 1))).toList
      x :+ (x.last._2 + 1, m)
    }
  }

  case class LRMPos(id: Long, linkId: Long, startM: Double, endM: Double)

  private def importRoadAddressData(conversionDatabase: DatabaseDef, vvhClient: VVHClient, ely: Int, complementaryLinks: Boolean,
                                    vvhClientProd: Option[VVHClient]): Unit = {
    def printRow(r: (Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, String, Option[String], String, String, Long, Double, Double, Double, Double)): String ={
      s"""linkid: %d, alku: %d, loppu: %d, tie: %d, aosa: %d, ajr: %d, ely: %d, tietyyppi: %d, jatkuu: %d, aet: %d, let: %d, alkupvm: %s, loppupvm: %s, kayttaja: %s, muutospvm or rekisterointipvm: %s""".
        format(r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13, r._14, r._15)
    }
    // Adjust the LRM Positions so that the link is filled with full data
    def adjust(lrmPos: Seq[LRMPos], length: Double): Seq[LRMPos] = {
      val coefficient: Double = length / (lrmPos.map(_.endM).max - lrmPos.map(_.startM).min)
      lrmPos.map(lrm => lrm.copy(startM = lrm.startM * coefficient, endM = lrm.endM * coefficient))
    }
    val roads = conversionDatabase.withDynSession {
      if (complementaryLinks)
        sql"""select linkid, alku, loppu,
            tie, aosa, ajr,
            ely, tietyyppi,
            jatkuu, aet, let,
            TO_CHAR(alkupvm, 'YYYY-MM-DD'), TO_CHAR(loppupvm, 'YYYY-MM-DD'),
            kayttaja, TO_CHAR(COALESCE(muutospvm, rekisterointipvm), 'YYYY-MM-DD'), linkid * 10000 + ajr*1000 + aet as id,
            alkux, alkuy, loppux, loppuy
            from vvh_tieosoite_taydentava WHERE ely=$ely""".as[(Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, String, Option[String], String, String, Long, Double, Double, Double, Double)].list
      else
        sql"""select linkid, alku, loppu,
            tie, aosa, ajr,
            ely, tietyyppi,
            jatkuu, aet, let,
            TO_CHAR(alkupvm, 'YYYY-MM-DD'), TO_CHAR(loppupvm, 'YYYY-MM-DD'),
            kayttaja, TO_CHAR(COALESCE(muutospvm, rekisterointipvm), 'YYYY-MM-DD'), linkid * 10000 + ajr*1000 + aet as id,
            alkux, alkuy, loppux, loppuy
            from vvh_tieosoite_nyky WHERE ely=$ely""".as[(Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, String, Option[String], String, String, Long, Double, Double, Double, Double)].list
    }

    print(s"\n${DateTime.now()} - ")
    println("Read %d rows from conversion database for ELY %d".format(roads.size, ely))
    val lrmList = roads.map(r => LRMPos(r._16, r._1, r._2.toDouble, r._3.toDouble)).groupBy(_.linkId) // linkId -> (id, linkId, startM, endM)
    val addressList = roads.map(r => r._16 -> (r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13, r._14, r._15, r._17, r._18, r._19, r._20)).toMap

    print(s"${DateTime.now()} - ")
    println("Total of %d link ids".format(lrmList.keys.size))
    val linkIdSet = lrmList.keys.toSet // Mapping LinkId -> Id

    val roadLinks = linkIdSet.grouped(4000).flatMap(group =>
      // DEV complementary link load
      if (complementaryLinks)
        vvhClientProd.getOrElse(vvhClient).complementaryData.fetchByLinkIds(group)
      else {
        // If in production or QA environment -> load complementary links, too
        if (vvhClientProd.isEmpty)
          vvhClient.roadLinkData.fetchByLinkIds(group) ++ vvhClient.complementaryData.fetchByLinkIds(group)
        else
          // If in DEV environment -> don't load complementary links at this stage (no data for them)
          vvhClientProd.get.roadLinkData.fetchByLinkIds(group) ++ vvhClientProd.get.complementaryData.fetchByLinkIds(group)
      }
    ).toSeq

    val linkLengths = roadLinks.map{
      roadLink =>
        roadLink.linkId -> GeometryUtils.geometryLength(roadLink.geometry)
    }.toMap

    print(s"${DateTime.now()} - ")
    println("Read %d road links from vvh".format(linkLengths.size))

    val floatingLinks = vvhClient.historyData.fetchVVHRoadLinkByLinkIds(roads.filterNot(r => linkLengths.get(r._1).isDefined).map(_._1).toSet).groupBy(_.linkId).mapValues(_.maxBy(_.endDate))
    print(s"${DateTime.now()} - ")
    println(floatingLinks.size + " links can be saved as floating addresses")

    roads.filterNot(r => linkLengths.get(r._1).isDefined || floatingLinks.get(r._1).nonEmpty).foreach{
      row => println("Suppressed row ID %d with reason 1: 'LINK-ID is not found in the VVH Interface' %s".format(row._16, printRow(row)))
    }

    val allLinkLengths = linkLengths ++ floatingLinks.mapValues(x => GeometryUtils.geometryLength(x.geometry))

    val lrmPositions = allLinkLengths.flatMap {
      case (linkId, length) => adjust(lrmList.getOrElse(linkId, List()), length)
    }

    print(s"${DateTime.now()} - ")
    println("%d segments with invalid link id removed".format(roads.filterNot(r => linkLengths.get(r._1).isDefined).size))

    val linkIdMapping: Map[Long,Long] = if (vvhClientProd.nonEmpty) {
      print(s"${DateTime.now()} - ")
      println("Converting link ids to DEV link ids")
      val mmlIdMaps = roadLinks.filter(_.attributes.get("MTKID").nonEmpty).map(rl => rl.attributes("MTKID").asInstanceOf[BigInt].longValue() -> rl.linkId).toMap
      val links = mmlIdMaps.keys.toSet.grouped(4000).flatMap(grp => vvhClient.roadLinkData.fetchByMmlIds(grp)).toSeq
      val fromMmlIdMap = links.map(rl => rl.attributes("MTKID").asInstanceOf[BigInt].longValue() -> rl.linkId).toMap
      val (differ, same) = fromMmlIdMap.map { case (mmlId, devLinkId) =>
        mmlIdMaps(mmlId) -> devLinkId
      }.partition { case (pid, did) => pid != did }
      print(s"${DateTime.now()} - ")
      println("Removing the non-differing mmlId+linkId combinations from mapping: %d road links".format(same.size))
      differ
    } else {
      Map()
    }

    print(s"${DateTime.now()} - ")
    println("Link mapping contains %d entries".format(linkIdMapping.size))

    val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure) values (?, ?, ?, ?, ?)")
    val addressPS = dynamicSession.prepareStatement("insert into ROAD_ADDRESS (id, lrm_position_id, road_number, road_part_number, " +
      "track_code, discontinuity, START_ADDR_M, END_ADDR_M, start_date, end_date, created_by, " +
      "VALID_FROM, geometry, floating) values (viite_general_seq.nextval, ?, ?, ?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), " +
      "TO_DATE(?, 'YYYY-MM-DD'), ?, TO_DATE(?, 'YYYY-MM-DD'), MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(" +
      "?,?,0.0,0.0,?,?,0.0,?)), ?)")
    val ids = sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level <= ${lrmPositions.size}""".as[Long].list
    assert(ids.size == lrmPositions.size || lrmPositions.isEmpty)
    lrmPositions.zip(ids).foreach { case ((pos), (lrmId)) =>
      assert(addressList.get(pos.id).size == 1)
      val address = addressList.get(pos.id).head
      val (startAddrM, endAddrM, sideCode) = if (address._7 < address._8) {
        (address._7, address._8, SideCode.TowardsDigitizing.value)
      } else {
        (address._8, address._7, SideCode.AgainstDigitizing.value)
      }
      val (x1, y1, x2, y2) = if (sideCode == SideCode.TowardsDigitizing.value)
        (address._13, address._14, address._15, address._16)
      else
        (address._15, address._16, address._13, address._14)

      lrmPositionPS.setLong(1, lrmId)
      lrmPositionPS.setLong(2, linkIdMapping.getOrElse(pos.linkId, pos.linkId))
      lrmPositionPS.setLong(3, sideCode)
      lrmPositionPS.setDouble(4, pos.startM)
      lrmPositionPS.setDouble(5, pos.endM)
      lrmPositionPS.addBatch()
      addressPS.setLong(1, lrmId)
      addressPS.setLong(2, address._1)
      addressPS.setLong(3, address._2)
      addressPS.setLong(4, address._3)
      addressPS.setLong(5, address._6)
      addressPS.setLong(6, startAddrM)
      addressPS.setLong(7, endAddrM)
      addressPS.setString(8, address._9)
      addressPS.setString(9, address._10.getOrElse(""))
      addressPS.setString(10, address._11)
      addressPS.setString(11, address._12)
      addressPS.setDouble(12, x1)
      addressPS.setDouble(13, y1)
      addressPS.setDouble(14, x2)
      addressPS.setDouble(15, y2)
      addressPS.setDouble(16, endAddrM - startAddrM)
      addressPS.setInt(17, if (floatingLinks.contains(pos.linkId)) 1 else 0)
      addressPS.addBatch()
    }
    lrmPositionPS.executeBatch()
    println(s"${DateTime.now()} - LRM Positions saved")
    addressPS.executeBatch()
    println(s"${DateTime.now()} - Road addresses saved")
    lrmPositionPS.close()
    addressPS.close()
  }

  def importRoadAddressData(conversionDatabase: DatabaseDef, vvhClient: VVHClient, vvhClientProd: Option[VVHClient], geometryAdjusted: Long): Unit = {
    val roadMaintainerElys = Seq(0, 1, 2, 3, 4, 8, 9, 10, 12, 14)

    OracleDatabase.withDynTransaction {
      sqlu"""ALTER TABLE ROAD_ADDRESS DISABLE ALL TRIGGERS""".execute
      sqlu"""DELETE FROM ROAD_ADDRESS""".execute
      sqlu"""DELETE FROM LRM_POSITION WHERE NOT EXISTS (SELECT POSITION_ID FROM ASSET_LINK WHERE POSITION_ID=LRM_POSITION.ID)""".execute
      println (s"${DateTime.now ()} - Old address data removed")

      roadMaintainerElys.foreach(ely => importRoadAddressData(conversionDatabase, vvhClient, ely, complementaryLinks = false, vvhClientProd))
      // If running in DEV environment then include some testing complementary links
      if (vvhClientProd.nonEmpty)
        roadMaintainerElys.foreach(ely => importRoadAddressData(conversionDatabase, vvhClient, ely, complementaryLinks = true, None))

      println(s"${DateTime.now()} - Updating geometry adjustment timestamp to $geometryAdjusted")
      sqlu"""UPDATE LRM_POSITION
        SET ADJUSTED_TIMESTAMP = $geometryAdjusted WHERE ID IN (SELECT LRM_POSITION_ID FROM ROAD_ADDRESS)""".execute

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
    }
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
    val service = new RoadAddressService(linkService, eventBus)
    var counter = 0
    var changed = 0
    OracleDatabase.withDynTransaction {
      val roadNumbers = Queries.getDistinctRoadNumbers(filterRoadAddresses)
      roadNumbers.foreach(roadNumber =>{
        counter +=1
        println("Processing roadNumber %d (%d of %d) at time: %s".format(roadNumber, counter, roadNumbers.size,  DateTime.now().toString))
        val linkIds = Queries.getLinkIdsByRoadNumber(roadNumber)
        val roadLinksFromVVH = linkService.getCurrentAndComplementaryRoadLinksFromVVH(linkIds, false)
        val addresses = RoadAddressDAO.fetchByLinkId(roadLinksFromVVH.map(_.linkId).toSet, false, true).groupBy(_.linkId)

        roadLinksFromVVH.foreach(roadLink => {
          val segmentsOnViiteDatabase = addresses.getOrElse(roadLink.linkId, Set())
          segmentsOnViiteDatabase.foreach(segment =>{
              val newGeom = GeometryUtils.truncateGeometry3D(roadLink.geometry, segment.startMValue, segment.endMValue)
            if(!segment.geometry.equals(Nil) && !newGeom.equals(Nil)) {

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

