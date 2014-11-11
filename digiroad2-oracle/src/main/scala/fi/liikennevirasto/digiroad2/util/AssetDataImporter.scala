package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import java.util.{Locale, Properties}
import fi.liikennevirasto.digiroad2.GeometryUtils
import scala.slick.driver.JdbcDriver.backend.{Database, DatabaseDef, Session}
import scala.slick.jdbc.{StaticQuery => Q, _}
import Database.dynamicSession
import Q.interpolation
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.{Interval, DateTime, LocalDate}
import com.github.tototoshi.slick.MySQLJodaSupport._
import oracle.sql.STRUCT
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.util.AssetDataImporter._
import scala.collection.parallel.{ParSeq, ForkJoinTaskSupport}
import scala.concurrent.forkjoin.ForkJoinPool
import scala.Some
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.SimpleBusStop
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.SimpleRoadLink
import org.joda.time.format.PeriodFormatterBuilder
import java.sql.Statement
import java.text.{DecimalFormat, NumberFormat}
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao.{nextPrimaryKeySeqValue, nextLrmPositionPrimaryKeySeqValue}
import fi.liikennevirasto.digiroad2.RoadLinkService
import fi.liikennevirasto.digiroad2.Point

object AssetDataImporter {

  case class SimpleBusStop(shelterType: Int,
                           assetId: Option[Long] = None,
                           busStopId: Option[Long],
                           busStopType: Seq[Int],
                           lrmPositionId: Long,
                           validFrom: LocalDate = LocalDate.now,
                           validTo: Option[LocalDate] = None,
                           point: Point)
  case class SimpleRoadLink(id: Long, roadType: Int, roadNumber: Int, roadPartNumber: Int, functionalClass: Int, rStartHn: Int, lStartHn: Int,
                            rEndHn: Int, lEndHn: Int, municipalityNumber: Int, geom: STRUCT)

  case class PropertyWrapper(shelterTypePropertyId: Long, accessibilityPropertyId: Long, administratorPropertyId: Long,
                             busStopAssetTypeId: Long, busStopTypePropertyId: Long)

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
}

class AssetDataImporter {
  val logger = LoggerFactory.getLogger(getClass)
  lazy val ds: DataSource = initDataSource

  val Modifier = "dr1conversion"

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

  private def getBatchDrivers(n: Int, m: Int, step: Int): List[(Int, Int)] = {
    if (m < step) {
      List((n, m))
    } else {
      val x = ((n to m by step).sliding(2).map(x => (x(0), x(1) - 1))).toList
      x :+ (x.last._2 + 1, m)
    }
  }

  var insertSpeedLimitsCount = 0;

  def importSpeedLimits(dataSet: ImportDataSet, taskPool: ForkJoinPool) = {

    val count = dataSet.database().withDynSession {
      sql"""
        select count(sl.segm_id)
          from SPEED_LIMITS_PRODUCTION sl
          """.as[Int].list.head
    }
    val min = dataSet.database().withDynSession {
      sql"""
        select min(segm_id)
          from SPEED_LIMITS_PRODUCTION sl
          order by sl.segm_id asc
          """.as[Int].list.head
    }
    val max = dataSet.database().withDynSession {
      sql"""
        select max(segm_id)
          from SPEED_LIMITS_PRODUCTION sl
          order by sl.segm_id desc
          """.as[Int].list.head
    }
    val startSelect = System.currentTimeMillis()

    val queries = getBatchDrivers(min, max, 500).view.map { case (n, m) =>
      sql"""
       select sl.SEGM_ID,
              stragg(sl.tielinkki_id || ';' || sl.alkum || ';' || sl.loppum || ';' || sl.nopeus || ';' || sl.puoli) insert_exprs
         from SPEED_LIMITS_PRODUCTION sl
         where segm_id between $n and $m
         group by sl.SEGM_ID
         order by sl.SEGM_ID
       """
    }.par

    queries.tasksupport = new ForkJoinTaskSupport(taskPool)
    queries.foreach { query =>
      val selectStartTime = System.currentTimeMillis();
      val segments = dataSet.database().withDynSession {
        query.as[(Int, String)].list()
      }
      Database.forDataSource(ds).withDynSession {
        val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, 20, SYSDATE, 'dr1_conversion')")
        val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
        val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
        val speedLimitPS = dynamicSession.prepareStatement("insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date, modified_by) values (?, (select id from enumerated_value where value = ? and property_id = (select id from property where public_id = 'rajoitus')), (select id from property where public_id = 'rajoitus'), sysdate, 'dr1_conversion')")

        segments.foreach { segment =>
          val assetId = nextPrimaryKeySeqValue
          var speedLimit = -1
          assetPS.setLong(1, assetId)
          assetPS.addBatch()

          val insertExprs = segment._2.split("@").map(_.trim).filter(!_.isEmpty)
          insertExprs.foreach { insertExpr =>
            val insertValues = insertExpr.replace(',','.').split(";").toIterator
            val roadLinkId = insertValues.next.toLong
            val startMeasure = insertValues.next.toDouble
            val endMeasure   = insertValues.next.toDouble
            speedLimit = insertValues.next.toInt
            val sideCode = insertValues.next.toInt
            val lrmPositionId = nextLrmPositionPrimaryKeySeqValue

            lrmPositionPS.setLong(1, lrmPositionId)
            lrmPositionPS.setLong(2, roadLinkId)
            lrmPositionPS.setDouble(3, startMeasure)
            lrmPositionPS.setDouble(4, endMeasure)
            lrmPositionPS.setInt(5, sideCode)
            lrmPositionPS.addBatch()

            assetLinkPS.setLong(1, assetId)
            assetLinkPS.setLong(2, lrmPositionId)
            assetLinkPS.addBatch()

            this.synchronized {
              insertSpeedLimitsCount += 1
            }
          }
          speedLimitPS.setLong(1, assetId)
          if (Set(10, 15, 25).contains(speedLimit)) {
            speedLimit = 20
          } else if (speedLimit == 35) {
            speedLimit = 30
          } else if (speedLimit == 700) {
            speedLimit = 70
          }
          speedLimitPS.setInt(2, speedLimit)
          speedLimitPS.addBatch()
        }

        assetPS.executeBatch()
        lrmPositionPS.executeBatch()
        assetLinkPS.executeBatch()
        speedLimitPS.executeBatch()
        assetPS.close()
        lrmPositionPS.close()
        assetLinkPS.close()
        speedLimitPS.close()
      }

      this.synchronized {
        if (insertSpeedLimitsCount > 0) {
          val speedLimitsLeft = count-insertSpeedLimitsCount
          val timeNow = System.currentTimeMillis()
          val average =  (timeNow-startSelect)/insertSpeedLimitsCount
          val leftHours = speedLimitsLeft*average/(1000*60*60)
          val leftMins = speedLimitsLeft*average/(1000*60)%60
          printf("\r run time: %dmin | count: %d/%d %1.2f%% | average: %dms | time left: %dh %dmin | select time: %dms",
            (timeNow - startSelect)/60000,
            insertSpeedLimitsCount, count, insertSpeedLimitsCount/(count*1.0) * 100,
            average,
            leftHours, leftMins,
            timeNow - selectStartTime)
        }
      }
    }
  }

  def getTypeProperties = {
    Database.forDataSource(ds).withDynSession {
      val shelterTypePropertyId = sql"select p.id from property p where p.public_id = 'katos'".as[Long].first
      val accessibilityPropertyId = sql"select p.id from property p where p.public_id = 'esteettomyys_liikuntarajoitteiselle'".as[Long].first
      val administratorPropertyId = sql"select p.id from property p where p.public_id = 'tietojen_yllapitaja'".as[Long].first
      val busStopTypePropertyId = sql"select p.id from property p where p.public_id = 'pysakin_tyyppi'".as[Long].first
      val busStopAssetTypeId = sql"select id from asset_type where name = 'Bussipysäkit'".as[Long].first
      PropertyWrapper(shelterTypePropertyId, accessibilityPropertyId, administratorPropertyId,
                      busStopAssetTypeId, busStopTypePropertyId)
    }
  }

  def insertBusStops(busStop: SimpleBusStop, typeProps: PropertyWrapper) {
    Database.forDataSource(ds).withDynSession {
      val assetId = busStop.assetId.getOrElse(nextPrimaryKeySeqValue)

      sqlu"""
        insert into asset(id, external_id, asset_type_id, created_by, valid_from, valid_to)
        values($assetId, ${busStop.busStopId}, ${typeProps.busStopAssetTypeId}, $Modifier, ${busStop.validFrom}, ${busStop.validTo.getOrElse(null)})
      """.execute

      sqlu"""
         insert into asset_link(asset_id, position_id)
         values($assetId, ${busStop.lrmPositionId})
      """.execute

      OracleSpatialAssetDao.updateAssetGeometry(assetId, busStop.point)

      val bearing = OracleSpatialAssetDao.getAssetById(assetId) match {
        case Some(a) =>
          RoadLinkService.getRoadLinkGeometry(a.roadLinkId) match {
            case Some(geometry) => GeometryUtils.calculateBearing((a.lon, a.lat), geometry.map { point => (point.x, point.y) })
            case None =>
              println(s"No road link found for Asset: $assetId")
              0.0
          }
        case None =>
          println(s"No Asset found: $assetId")
          0.0
      }

      sqlu"update asset set bearing = $bearing where id = $assetId".execute
      busStop.busStopType.foreach { busStopType =>
        insertMultipleChoiceValue(typeProps.busStopTypePropertyId, assetId, busStopType)
      }

      insertTextPropertyData(typeProps.accessibilityPropertyId, assetId, "Ei tiedossa")
      insertSingleChoiceValue(typeProps.administratorPropertyId, assetId, 2)
      insertSingleChoiceValue(typeProps.shelterTypePropertyId, assetId, busStop.shelterType)
    }
  }

  def insertTextPropertyData(propertyId: Long, assetId: Long, text:String) {
    sqlu"""
      insert into text_property_value(id, property_id, asset_id, value_fi, value_sv, created_by)
      values (primary_key_seq.nextval, $propertyId, $assetId, $text, ' ', $Modifier)
    """.execute
  }

  def insertMultipleChoiceValue(propertyId: Long, assetId: Long, value: Int) {
    sqlu"""
      insert into multiple_choice_value(id, property_id, asset_id, enumerated_value_id, modified_by)
      values (primary_key_seq.nextval, $propertyId, $assetId,
        (select id from enumerated_value where value = $value and property_id = $propertyId), $Modifier)
    """.execute
  }

  def insertSingleChoiceValue(propertyId: Long, assetId: Long, value: Int) {
    sqlu"""
      insert into single_choice_value(property_id, asset_id, enumerated_value_id, modified_by)
      values ($propertyId, $assetId, (select id from enumerated_value where value = $value and property_id = $propertyId), $Modifier)
    """.execute
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
