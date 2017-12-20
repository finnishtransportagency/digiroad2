package fi.liikennevirasto.viite.util

import java.text.DecimalFormat
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
import fi.liikennevirasto.viite.{RoadAddressLinkBuilder, RoadAddressService, RoadType}
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.process.{RoadAddressChangeInfoMapper, RoadAddressMapping}
import org.joda.time._
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._

import scala.collection.mutable
import scala.math.BigDecimal.RoundingMode

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
  case class RoadTypeChangePoints(roadNumber: Long, roadPartNumber: Long, addrM: Long, before: RoadType, after: RoadType, elyCode: Long)

  /**
    * Get road type for road address object with a list of road type change points
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

  private def importRoadAddressData(conversionDatabase: DatabaseDef, vvhClient: VVHClient, ely: Int, importOptions: ImportOptions,
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
      if (importOptions.onlyComplementaryLinks)
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

    val vvhRoadLinkClient = if (importOptions.useFrozenLinkService) vvhClient.frozenTimeRoadLinkData else vvhClient.roadLinkData
    val roadLinks = linkIdSet.grouped(4000).flatMap(group =>
      // DEV complementary link load
      if (importOptions.onlyComplementaryLinks)
        // Import only complementary links
        vvhClientProd.getOrElse(vvhClient).complementaryData.fetchByLinkIds(group)
      else {
        vvhRoadLinkClient.fetchByLinkIds(group) ++ vvhClient.complementaryData.fetchByLinkIds(group)
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

    // TODO: When production and dev differ enough, uncomment this and adjust: now the code above doesn't check the production first
    // This must be changed to check from production all the links that weren't found and then mapping again. See also the sql batch below.
//    val linkIdMapping: Map[Long,Long] = if (vvhClientProd.nonEmpty) {
//      print(s"${DateTime.now()} - ")
//      println("Converting link ids to DEV link ids")
//      val mmlIdMaps = roadLinks.filter(_.attributes.get("MTKID").nonEmpty).map(rl => rl.attributes("MTKID").asInstanceOf[BigInt].longValue() -> rl.linkId).toMap
//      val links = mmlIdMaps.keys.toSet.grouped(4000).flatMap(grp => vvhClient.roadLinkData.fetchByMmlIds(grp)).toSeq
//      val fromMmlIdMap = links.map(rl => rl.attributes("MTKID").asInstanceOf[BigInt].longValue() -> rl.linkId).toMap
//      val (differ, same) = fromMmlIdMap.map { case (mmlId, devLinkId) =>
//        mmlIdMaps(mmlId) -> devLinkId
//      }.partition { case (pid, did) => pid != did }
//      print(s"${DateTime.now()} - ")
//      println("Removing the non-differing mmlId+linkId combinations from mapping: %d road links".format(same.size))
//      differ
//    } else {
//      Map()
//    }

//    print(s"${DateTime.now()} - ")
//    println("Link mapping contains %d entries".format(linkIdMapping.size))

    val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure) values (?, ?, ?, ?, ?)")
    val addressPS = dynamicSession.prepareStatement("insert into ROAD_ADDRESS (id, lrm_position_id, road_number, road_part_number, " +
      "track_code, discontinuity, START_ADDR_M, END_ADDR_M, start_date, end_date, created_by, " +
      "VALID_FROM, geometry, floating, road_type, ely) values (viite_general_seq.nextval, ?, ?, ?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), " +
      "TO_DATE(?, 'YYYY-MM-DD'), ?, TO_DATE(?, 'YYYY-MM-DD'), MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(" +
      "?,?,0.0,0.0,?,?,0.0,?)), ?, ?, ?)")
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
      // TODO: link id mapping, see above
//      lrmPositionPS.setLong(2, linkIdMapping.getOrElse(pos.linkId, pos.linkId))
      lrmPositionPS.setLong(2, pos.linkId)
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
      addressPS.setLong(18, address._5)
      addressPS.setLong(19, address._4)
      addressPS.addBatch()
    }
    lrmPositionPS.executeBatch()
    println(s"${DateTime.now()} - LRM Positions saved")
    addressPS.executeBatch()
    println(s"${DateTime.now()} - Road addresses saved")
    lrmPositionPS.close()
    addressPS.close()
  }

  private def importRoadAddressHistoryData(conversionDatabase: DatabaseDef, ely: Long, importDate: String): Unit = {

    //Get current roadHistory
    val currentHistory =
      sql"""SELECT RA.ROAD_NUMBER, RA.ROAD_PART_NUMBER, RA.TRACK_CODE, RA.DISCONTINUITY, RA.START_ADDR_M, RA.END_ADDR_M,
         TO_CHAR(RA.START_DATE,'YYYY-MM-DD'), TO_CHAR(RA.END_DATE,'YYYY-MM-DD'), TO_CHAR(RA.VALID_FROM,'YYYY-MM-DD'),
         TO_CHAR(RA.VALID_TO,'YYYY-MM-DD'), RA.ELY, RA.ROAD_TYPE, LR.LINK_ID FROM ROAD_ADDRESS RA, LRM_POSITION LR
         WHERE RA.END_DATE IS NOT NULL AND RA.LRM_POSITION_ID = LR.ID AND RA.ELY = $ely""".
        as[(Long, Long, Long, Long, Long, Long, String, Option[String], String, Option[String], Long, Long, Long)].list

    print(s"\n${DateTime.now()} - ")
    println("Got %d current road addresses history".format(currentHistory.size))

    val roadHistory = conversionDatabase.withDynSession {
      if (importDate != "") {
        sql"""select linkid, alku, loppu,
            tie, aosa, ajr,
            ely, tietyyppi,
            jatkuu, aet, let,
            TO_CHAR(alkupvm, 'YYYY-MM-DD'), TO_CHAR(loppupvm, 'YYYY-MM-DD'),
            kayttaja, TO_CHAR(COALESCE(muutospvm, rekisterointipvm), 'YYYY-MM-DD'), linkid * 10000 + ajr*1000 + aet as id,
            alkux, alkuy, loppux, loppuy
            from VVH_TIEHISTORIA_HEINA2017 WHERE ely=$ely AND loppupvm IS NOT NULL AND TO_CHAR(loppupvm, 'YYYY-MM-DD') <= $importDate """
          .as[(Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, String, Option[String], String, String, Long, Double, Double, Double, Double)].list
      } else {
        sql"""select linkid, alku, loppu,
            tie, aosa, ajr,
            ely, tietyyppi,
            jatkuu, aet, let,
            TO_CHAR(alkupvm, 'YYYY-MM-DD'), TO_CHAR(loppupvm, 'YYYY-MM-DD'),
            kayttaja, TO_CHAR(COALESCE(muutospvm, rekisterointipvm), 'YYYY-MM-DD'), linkid * 10000 + ajr*1000 + aet as id,
            alkux, alkuy, loppux, loppuy
            from VVH_TIEHISTORIA_HEINA2017 WHERE ely=$ely AND loppupvm IS NOT NULL"""
          .as[(Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, String, Option[String], String, String, Long, Double, Double, Double, Double)].list
      }
    }

    print(s"\n${DateTime.now()} - ")
    println("Read %d rows from conversion database for ELY %d".format(roadHistory.size, ely))

    val lrmList = roadHistory.map(r => LRMPos(r._16, r._1, r._2.toDouble, r._3.toDouble)) // linkId -> (id, linkId, startM, endM)
    val addressList = roadHistory.map(r => r._16 -> (r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13, r._14, r._15, r._17, r._18, r._19, r._20)).filterNot(rh => {
      currentHistory.exists(ch => {
          rh._2._2 == ch._5 &&    //startAddressM
          rh._2._3 == ch._6 &&    //endAddressM
          rh._2._4 == ch._1 &&    //roadNumber
          rh._2._5 == ch._2 &&    //roadPartNumber
          rh._2._12 == ch._7 &&   //startDate
          rh._2._13 == ch._8 &&   //endDate
          rh._2._16 == ch._13 &&  //linkId
          rh._2._8 == ch._12 &&   //roadType
          rh._2._9 == ch._4 &&    //discontinuity
          rh._2._6 == ch._3 &&    //trackCode
          rh._2._7 == ch._11      //ely
      })
    }).toMap

    val lrmAddresses = lrmList.filterNot(_.linkId == 0)
    val roadList = addressList.filterNot(_._1 == 0)
    print(s"${DateTime.now()} - ")
    println("%d segments with invalid link id removed".format(lrmList.filterNot(_.linkId != 0).size))

    val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure) values (?, ?, ?, ?, ?)")
    val addressPS = dynamicSession.prepareStatement("insert into ROAD_ADDRESS (id, lrm_position_id, road_number, road_part_number, " +
      "track_code, discontinuity, START_ADDR_M, END_ADDR_M, start_date, end_date, created_by, " +
      "VALID_FROM, geometry, floating, road_type, ely, terminated) values (viite_general_seq.nextval, ?, ?, ?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), " +
      "TO_DATE(?, 'YYYY-MM-DD'), ?, TO_DATE(?, 'YYYY-MM-DD'), MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(" +
      "?,?,0.0,0.0,?,?,0.0,?)), ?, ?, ?, ?)")
    val ids = sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level <= ${lrmAddresses.size}""".as[Long].list
    val df = new DecimalFormat("#.###")
    assert(ids.size == lrmAddresses.size || lrmAddresses.isEmpty)
    lrmAddresses.zip(ids).foreach { case ((pos), (lrmId)) =>
      assert(roadList.get(pos.id).size == 1)
      val address = roadList.get(pos.id).head
      val (startAddrM, endAddrM, sideCode) = if (address._7 < address._8) {
        (address._7, address._8, SideCode.TowardsDigitizing.value)
      } else {
        (address._8, address._7, SideCode.AgainstDigitizing.value)
      }
      val (x1, y1, x2, y2) = if (sideCode == SideCode.TowardsDigitizing.value)
        (address._13, address._14, address._15, address._16)
      else
        (address._15, address._16, address._13, address._14)

      println(s"insert into ROAD_ADDRESS values (id, lrm_position_id, road_number, road_part_number, " +
        s"track_code, discontinuity, START_ADDR_M, END_ADDR_M, start_date, end_date, created_by, " +
        s"VALID_FROM, geometry, floating, road_type, ely, terminated)" +
        s"(viite_general_seq.nextval, $lrmId, ${address._1}, ${address._2}, ${address._3}, ${address._6}, ${startAddrM}), ${endAddrM}, ${address._9}, ${address._10.getOrElse("")}, ${address._11}), ${address._12}, $x1,$y1, $x2, $y2, ${endAddrM - startAddrM}, 0, ${address._5}, ${address._4}, 2)")

      lrmPositionPS.setLong(1, lrmId)
      lrmPositionPS.setLong(2, pos.linkId)
      lrmPositionPS.setLong(3, sideCode)
      lrmPositionPS.setDouble(4, df.format(pos.startM).toDouble)
      lrmPositionPS.setDouble(5, df.format(pos.endM).toDouble)
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
      addressPS.setInt(17, 0)
      addressPS.setLong(18, address._5)
      addressPS.setLong(19, address._4)
      addressPS.setInt(20, 2)
      addressPS.addBatch()
    }
    lrmPositionPS.executeBatch()
    println(s"${DateTime.now()} - LRM Positions saved")
    addressPS.executeBatch()
    println(s"${DateTime.now()} - Road addresses saved")
    lrmPositionPS.close()
    addressPS.close()
  }

  def importRoadAddressData(conversionDatabase: DatabaseDef, vvhClient: VVHClient, vvhClientProd: Option[VVHClient],
                            importOptions: ImportOptions): Unit = {
    val roadMaintainerElys = Seq(0, 1, 2, 3, 4, 8, 9, 10, 12, 14)

    OracleDatabase.withDynTransaction {
      sqlu"""ALTER TABLE ROAD_ADDRESS DISABLE ALL TRIGGERS""".execute
      sqlu"""DELETE FROM ROAD_ADDRESS""".execute
      sqlu"""DELETE FROM LRM_POSITION WHERE
            NOT EXISTS (SELECT LRM_POSITION_ID FROM PROJECT_LINK WHERE LRM_POSITION_ID=LRM_POSITION.ID) AND
            NOT EXISTS (SELECT LRM_POSITION_ID FROM PROJECT_LINK_HISTORY WHERE LRM_POSITION_ID=LRM_POSITION.ID) AND
            NOT EXISTS (SELECT POSITION_ID FROM ASSET_LINK WHERE POSITION_ID=LRM_POSITION.ID)""".execute
      println (s"${DateTime.now ()} - Old address data removed")

      roadMaintainerElys.foreach(ely => importRoadAddressData(conversionDatabase, vvhClient, ely, importOptions, vvhClientProd))
      // If running in DEV environment then include some testing complementary links
      if (vvhClientProd.nonEmpty)
        roadMaintainerElys.foreach(ely => importRoadAddressData(conversionDatabase, vvhClient, ely, importOptions.copy(onlyComplementaryLinks = true), None))

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
    }
  }

  def importRoadAddressHistory(conversionDatabase: DatabaseDef, importDate: String): Unit = {

    print(s"\n${DateTime.now()} - ")
    println("Starting to import history data %s ".format(importDate))

    val roadMaintainerElys = Seq(0, 1, 2, 3, 4, 8, 9, 10, 12, 14)

    OracleDatabase.withDynTransaction{
      sqlu"""ALTER TABLE ROAD_ADDRESS DISABLE ALL TRIGGERS""".execute
      roadMaintainerElys.map(ely => importRoadAddressHistoryData(conversionDatabase, ely, importDate))
      sqlu"""ALTER TABLE ROAD_ADDRESS ENABLE ALL TRIGGERS""".execute
    }
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

      roadsWithMultipleValues.groupBy(m => (m.roadNumber, m.roadPartNumber)).foreach{ case ((roadNumber, roadPartNumber), roadMaps) =>{
        val addresses = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber, true, true)
        println(s"updating tie = $roadNumber, aosa = $roadPartNumber: (${addresses.size} rows)")
        addresses.foreach(address => {
          val ely = roadMaps.head.elyCode
          roadType(roadMaps, address) match {
            case Left(roadType) =>
              sqlu"""UPDATE ROAD_ADDRESS SET ROAD_TYPE = ${roadType.value}, ELY= $ely where ID = ${address.id}""".execute
            case Right((addrM, roadTypeBefore, roadTypeAfter)) =>
              val roadLinkFromVVH = linkService.getCurrentAndComplementaryRoadLinksFromVVH(Set(address.linkId), false)
              if (roadLinkFromVVH.isEmpty)
                println(s"WARNING! LinkId ${address.linkId} not found in current or complementary links list, using address geometry")
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

  def splitRoadAddresses(roadAddress : RoadAddress, addrMToSplit: Long, roadTypeBefore : RoadType, roadTypeAfter : RoadType, elyCode : Long) : Seq[RoadAddress] = {
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
    val service = new RoadAddressService(linkService, eventBus)
    var counter = 0
    var changed = 0
    OracleDatabase.withDynTransaction {
      val roadNumbers = RoadAddressDAO.getCurrentValidRoadNumbers(if (filterRoadAddresses)
        "AND (ROAD_NUMBER <= 20000 or (road_number >= 40000 and road_number <= 70000))" else "")
      roadNumbers.foreach(roadNumber =>{
        counter +=1
        println("Processing roadNumber %d (%d of %d) at time: %s".format(roadNumber, counter, roadNumbers.size,  DateTime.now().toString))
        val linkIds = RoadAddressDAO.fetchByRoad(roadNumber).map(_.linkId).toSet
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

case class ImportOptions(onlyComplementaryLinks: Boolean, useFrozenLinkService: Boolean, geometryAdjustedTimeStamp: Long)
case class RoadPart(roadNumber: Long, roadPart: Long, ely: Long)

