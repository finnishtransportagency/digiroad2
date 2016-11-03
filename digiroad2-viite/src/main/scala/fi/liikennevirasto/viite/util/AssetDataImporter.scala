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
import fi.liikennevirasto.viite.dao.RoadAddress
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

  private def importRoadAddressData(conversionDatabase: DatabaseDef, vvhClient: VVHClient, ely: Int, vvhClientProd: Option[VVHClient]): Unit = {
    def printRow(r: (Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, String, Option[String], String, String, Long)): String ={
      s"""linkid: %d, alku: %d, loppu: %d, tie: %d, aosa: %d, ajr: %d, ely: %d, tietyyppi: %d, jatkuu: %d, aet: %d, let: %d, alkupvm: %s, loppupvm: %s, kayttaja: %s, muutospvm or rekisterointipvm: %s""".
        format(r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13, r._14, r._15)
    }
    def filler(lrmPos: Seq[(Long, Long, Double, Double)], length: Double) = {
      val filled = lrmPos.exists(x => x._4 >= length)
      filled match {
        case true => lrmPos
        case false =>
          val maxEnd = lrmPos.map(_._4).max
          val (fixthese, good) = lrmPos.partition(_._4 == maxEnd)
          fixthese.map {
            case (xid, xlinkId, xstartM, _) => (xid, xlinkId, xstartM, length)
          } ++ good
      }
    }
    def cutter(lrmPos: Seq[(Long, Long, Double, Double)], length: Double) = {
      val (good, bad) = lrmPos.partition(_._4 < length)
      good ++ bad.map {
        case (id, linkId, startM, endM) => (id, linkId, Math.min(startM, length), Math.min(endM, length))
      }
    }
    val roads = conversionDatabase.withDynSession {
      sql"""select linkid, alku, loppu,
            tie, aosa, ajr,
            ely, tietyyppi,
            jatkuu, aet, let,
            TO_CHAR(alkupvm, 'YYYY-MM-DD'), TO_CHAR(loppupvm, 'YYYY-MM-DD'),
            kayttaja, TO_CHAR(COALESCE(muutospvm, rekisterointipvm), 'YYYY-MM-DD'), id
            from vvh_tieosoite_nyky WHERE ely=$ely""".as[(Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, String, Option[String], String, String, Long)].list
    }

    print(s"\n${DateTime.now()} - ")
    println("Read %d rows from conversion database for ELY %d".format(roads.size, ely))
    val lrmList = roads.map(r => (r._16, r._1, r._2.toDouble, r._3.toDouble)).groupBy(_._2) // linkId -> (id, linkId, startM, endM)
    val addressList = roads.map(r => (r._16, (r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13, r._14, r._15))).toMap

    print(s"${DateTime.now()} - ")
    println("Total of %d link ids".format(lrmList.keys.size))
    val linkIdSet = lrmList.keys.toSet // Mapping LinkId -> Id

    val roadLinks = linkIdSet.grouped(4000).flatMap(group => vvhClientProd.getOrElse(vvhClient).fetchVVHRoadlinks(group)).toSeq

    val linkLengths = roadLinks.map{
      roadLink =>
        roadLink.linkId -> GeometryUtils.geometryLength(roadLink.geometry)
    }.toMap

    print(s"${DateTime.now()} - ")
    println("Read %d road links from vvh".format(linkLengths.size))

    roads.filterNot(r => linkLengths.get(r._1).isDefined).foreach{
      row => println("Suppressed row ID %d with reason 1: 'LINK-ID is not found in the VVH Interface' %s".format(row._16, printRow(row)))
    }

    val (lrmPositions, warningRows) = linkLengths.flatMap {
      case (linkId, length) => cutter(filler(lrmList.getOrElse(linkId, List()), length), length)
    }.partition(x => x._3 != x._4)

    warningRows.foreach{
      warning =>
        val row = roads.find(r => r._16 == warning._1).get
        println("Suppressed row ID %d with reason 2: 'Values of the start and end fields are totally outside of the link geometry' %s".format(warning._1, printRow(row)))
    }

    print(s"${DateTime.now()} - ")
    println("%d zero length segments removed".format(warningRows.size))
    print(s"${DateTime.now()} - ")
    println("%d segments with invalid link id removed".format(roads.filterNot(r => linkLengths.get(r._1).isDefined).size))

    val linkIdMapping: Map[Long,Long] = if (vvhClientProd.nonEmpty) {
      print(s"${DateTime.now()} - ")
      println("Converting link ids to DEV link ids")
      val mmlIdMaps = roadLinks.map(rl => rl.attributes.get("MTKID").get.asInstanceOf[BigInt].longValue() -> rl.linkId).toMap
      val links = mmlIdMaps.keys.toSet.grouped(4000).flatMap(grp => vvhClient.fetchVVHRoadlinksByMmlIds(grp)).toSeq
      val fromMmlIdMap = links.map(rl => rl.attributes.get("MTKID").get.asInstanceOf[BigInt].longValue() -> rl.linkId).toMap
      val (differ, same) = fromMmlIdMap.map { case (mmlId, devLinkId) =>
        mmlIdMaps.get(mmlId).get -> devLinkId
      }.partition { case (pid, did) => pid != did }
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
      "created_date) values (viite_general_seq.nextval, ?, ?, ?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), TO_DATE(?, 'YYYY-MM-DD'), ?, TO_DATE(?, 'YYYY-MM-DD'))")
    val ids = sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level < ${lrmPositions.size}""".as[Long].list
    lrmPositions.zip(ids).foreach { case ((id, linkId, startM, endM), (lrmId)) =>
      val address = addressList.get(id).head
      val (startAddrM, endAddrM, sideCode) = address._7 < address._8 match {
        case true => (address._7, address._8, SideCode.TowardsDigitizing.value)
        case false => (address._8, address._7, SideCode.AgainstDigitizing.value)
      }
      lrmPositionPS.setLong(1, lrmId)
      lrmPositionPS.setLong(2, linkIdMapping.getOrElse(linkId, linkId))
      lrmPositionPS.setLong(3, sideCode)
      lrmPositionPS.setDouble(4, startM)
      lrmPositionPS.setDouble(5, endM)
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
      addressPS.addBatch()
    }
    lrmPositionPS.executeBatch()
    println(s"${DateTime.now()} - LRM Positions saved")
    addressPS.executeBatch()
    println(s"${DateTime.now()} - Road addresses saved")
    lrmPositionPS.close()
    addressPS.close()
  }

  def importRoadAddressData(conversionDatabase: DatabaseDef, vvhClient: VVHClient, vvhClientProd: Option[VVHClient]): Unit = {
    val roadMaintainerElys = Seq(0, 1, 2, 3, 4, 8, 9, 10, 12, 14)

    OracleDatabase.withDynTransaction {
      sqlu"""ALTER TABLE ROAD_ADDRESS DISABLE ALL TRIGGERS""".execute
      sqlu"""DELETE FROM ROAD_ADDRESS""".execute
      sqlu"""DELETE FROM LRM_POSITION WHERE NOT EXISTS (SELECT POSITION_ID FROM ASSET_LINK WHERE POSITION_ID=LRM_POSITION.ID)""".execute
      println (s"${DateTime.now ()} - Old address data removed")

      roadMaintainerElys.foreach(ely => importRoadAddressData(conversionDatabase, vvhClient, ely, vvhClientProd))

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
    RoadAddressLinkBuilder.municipalityMapping // Populate it beforehand, because it can't be done in nested TX
    OracleDatabase.withDynTransaction {
      val municipalities = Queries.getMunicipalitiesByEly(8)
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

