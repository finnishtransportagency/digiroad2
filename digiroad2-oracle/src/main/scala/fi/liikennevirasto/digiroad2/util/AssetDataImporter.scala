package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import java.util.{Locale, Properties}
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
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.SimpleLRMPosition
import org.joda.time.format.PeriodFormatterBuilder
import java.sql.Statement
import java.text.{DecimalFormat, NumberFormat}
import fi.liikennevirasto.digiroad2.DummyEventBus


object AssetDataImporter {

  case class SimpleBusStop(shelterType: Int, assetId: Option[Long] = None, busStopId: Option[Long], busStopType: Seq[Int], lrmPositionId: Long, validFrom: LocalDate = LocalDate.now, validTo: Option[LocalDate] = None)
  case class SimpleLRMPosition(id: Long, roadLinkId: Long, laneCode: Int, sideCode: Int, startMeasure: Double, endMeasure: Double)
  case class SimpleRoadLink(id: Long, roadType: Int, roadNumber: Int, roadPartNumber: Int, functionalClass: Int, rStartHn: Int, lStartHn: Int,
                            rEndHn: Int, lEndHn: Int, municipalityNumber: Int, geom: STRUCT)

  case class PropertyWrapper(shelterTypePropertyId: Long, accessibilityPropertyId: Long, administratorPropertyId: Long,
                             busStopAssetTypeId: Long, busStopTypePropertyId: Long)

  sealed trait ImportDataSet {
    def database(): DatabaseDef
    val roadLinkTable: String
    val busStopTable: String
  }

  case object TemporaryTables extends ImportDataSet {
    lazy val dataSource: DataSource = {
      val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/import.bonecp.properties"))
      new BoneCPDataSource(cfg)
    }

    def database() = Database.forDataSource(dataSource)
    val roadLinkTable: String = "temp2_tielinkki"
    val busStopTable: String = "temp2_lineaarilokaatio"
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
  lazy val assetProvider = new OracleSpatialAssetProvider(new DummyEventBus, new OracleUserProvider)

  val shelterTypes = Map[Int, Int](1 -> 1, 2 -> 2, 0 -> 99, 99 -> 99, 3 -> 99)
  val busStopTypes = Map[Int, Seq[Int]](1 -> Seq(1), 2 -> Seq(2), 3 -> Seq(3), 4 -> Seq(2, 3), 5 -> Seq(3, 4), 6 -> Seq(2, 3, 4), 7 -> Seq(99), 99 -> Seq(99),  0 -> Seq(99))
  val Modifier = "dr1conversion"

  implicit val getSimpleBusStop = GetResult[(SimpleBusStop, SimpleLRMPosition)] { r =>
      val bs = SimpleBusStop(shelterTypes.getOrElse(r.<<, 99), None, r.<<, busStopTypes(r.<<), r.<<)
      val lrm = SimpleLRMPosition(bs.lrmPositionId, r.<<, r.<<, r.<<, r.<<, r.<<)
    (bs, lrm)
  }
  implicit val getSimpleRoadLink = GetResult[SimpleRoadLink](r => SimpleRoadLink(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.nextObject().asInstanceOf[STRUCT]))

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

  def importRoadlinks(dataSet: ImportDataSet, taskPool: ForkJoinPool) = roadLinksToImport(dataSet, taskPool)

  private def getRoadlinkCount(dataSet: ImportDataSet) = {
    val table = dataSet.roadLinkTable
    dataSet.database().withDynSession {
      sql"""select max(objectid) from #$table""".as[Int].first
    }
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

  private def roadLinksToImport(dataSet: ImportDataSet, taskPool: ForkJoinPool) = {
    val count = getRoadlinkCount(dataSet)
    val parallerSeq = getBatchDrivers(count).par
    println(s"""batching done.""")
    Database.forDataSource(ds).withSession(targetDbSession => {
      parallerSeq.tasksupport = new ForkJoinTaskSupport(taskPool)
      val totalItems = count
      val startTime = DateTime.now()
      lastCheckpoint = DateTime.now()
      parallerSeq.foreach(x => doConversion(dataSet, x, targetDbSession, totalItems, startTime))
    })
  }

  var processedItems = 0
  var counterForProcessed = 1
  var lastCheckpoint: DateTime = null
  private def updateStatus(size: Int, totalItems: Int, startTime: DateTime) = {
    this.synchronized {
      processedItems = processedItems + size
      if(counterForProcessed % 20 == 0) {
        val percentage = (processedItems / (totalItems * 1.0)) * 100
        val currentTime = DateTime.now()
        val formatter = getPeriodFormatter
        val lastBatchExecTime = formatter.print(new Interval(lastCheckpoint, currentTime).toDuration.toPeriod)
        val totalExecTime = formatter.print(new Interval(startTime, currentTime).toDuration.toPeriod)
        println(f"""$processedItems / $totalItems  items ($percentage%1.2f %%) processed. Last batch took $lastBatchExecTime. Total execution time $totalExecTime""")
        lastCheckpoint = currentTime
      }
      counterForProcessed = counterForProcessed + 1
    }
  }

  private def getPeriodFormatter = {
    new PeriodFormatterBuilder()
      .appendHours()
      .appendSuffix("h ")
      .appendMinutes()
      .appendSuffix("m ")
      .appendSeconds()
      .appendSuffix("s ")
      .appendMillis()
      .appendSuffix("ms ")
      .toFormatter()
  }

  private def doConversion(dataSet: ImportDataSet, page: (Int, Int), targetDbSession: Session, totalItems: Int, startTime: DateTime) = {
    val links = getOldRoadlinksByPage(dataSet, page)
    insertRoadLink(links, targetDbSession)
    updateStatus(links.size, totalItems, startTime)
  }

  private def getOldRoadlinksByPage(dataSet: ImportDataSet, page: (Int, Int)) = {
     val start = page._1
     val end = page._2
     val s = dataSet.database().createSession()

      val query = Q.query[(Int, Int), SimpleRoadLink]("""
        select objectid, nvl(formofway,99), tienro, tieosanro, functionalroadclass, ens_talo_o, ens_talo_v,
               viim_talo_o, viim_talo_v, kunta_nro, shape from tielinkki where objectid between ? and ?""")
      val result = query.list(start, end)(s)
      s.close()
      result
  }

  private def insertRoadLink(roadlinks: List[SimpleRoadLink], targetDbSession: Session) {
    val ps = targetDbSession.prepareStatement("insert into road_link (id, road_type, road_number, road_part_number, functional_class, r_start_hn, l_start_hn, r_end_hn, l_end_hn, municipality_number, geom) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")

    def batch(rl: SimpleRoadLink) {
      ps.setLong(1, rl.id)
      ps.setInt(2, rl.roadType)
      ps.setInt(3, rl.roadNumber)
      ps.setInt(4, rl.roadPartNumber)
      ps.setInt(5, rl.functionalClass)
      ps.setInt(6, rl.rStartHn)
      ps.setInt(7, rl.lStartHn)
      ps.setInt(8, rl.rEndHn)
      ps.setInt(9, rl.lEndHn)
      ps.setInt(10, rl.municipalityNumber)
      ps.setObject(11, rl.geom)
      ps.addBatch
    }

    roadlinks foreach batch
    ps.executeBatch
    ps.close()
  }

  def importBusStops(dataSet: ImportDataSet, taskPool: ForkJoinPool) = {
    val (busStops, lrmPositions) = busStopsToImport(dataSet).unzip
    Database.forDataSource(ds).withSession(targetDbSession => {
      val insertLrmPositionsSequence: ParSeq[List[SimpleLRMPosition]] = lrmPositions.grouped(250).toList.par
      insertLrmPositionsSequence.tasksupport = new ForkJoinTaskSupport(taskPool)
      insertLrmPositionsSequence.foreach(lrmPositions => insertLrmPositions(lrmPositions, targetDbSession))
    })
    val typeProps = getTypeProperties
    val insertBusStopsSequence: ParSeq[SimpleBusStop] = busStops.toList.par
    insertBusStopsSequence.tasksupport = new ForkJoinTaskSupport(taskPool)
    insertBusStopsSequence.foreach(x => insertBusStops(x, typeProps))
  }

  def generateId = sql"select primary_key_seq.nextval from dual".as[Long].first

  var insertSpeedLimitsCount = 0;

  def importSpeedLimits(dataSet: ImportDataSet, taskPool: ForkJoinPool) = {

    val count = dataSet.database().withDynSession {
      sql"""
        select count(sl.segm_id)
          from SPEED_LIMITS sl
          """.as[Int].list().head
    }
    val min = dataSet.database().withDynSession {
      sql"""
        select min(segm_id)
          from SPEED_LIMITS sl
          order by sl.segm_id asc
          """.as[Int].list().head
    }
    val max = dataSet.database().withDynSession {
      sql"""
        select max(segm_id)
          from SPEED_LIMITS sl
          order by sl.segm_id desc
          """.as[Int].list().head
    }
    val startSelect = System.currentTimeMillis()

    val queries = getBatchDrivers(min, max, 500).view.map { case (n, m) =>
      sql"""
       select sl.SEGM_ID,
              stragg(sl.tielinkki_id || ';' || sl.alkum || ';' || sl.loppum) insert_exprs
         from SPEED_LIMITS sl
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
        val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id) values (?, 20)")
        val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, START_MEASURE, END_MEASURE) values (?, ?, ?, ?)")
        val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")

        segments.foreach { segment =>
          val assetId = generateId
          assetPS.setLong(1, assetId)
          assetPS.addBatch()

          val insertExprs = segment._2.split("@").map(_.trim).filter(!_.isEmpty)
          insertExprs.foreach { insertExpr =>
            val insertValues = insertExpr.replace(',','.').split(";").toIterator
            val roadLinkId = insertValues.next.toLong
            val startMeasure = insertValues.next.toDouble
            val endMeasure   = insertValues.next.toDouble
            val lrmPositionId = generateId

            lrmPositionPS.setLong(1, lrmPositionId)
            lrmPositionPS.setLong(2, roadLinkId)
            lrmPositionPS.setDouble(3, startMeasure)
            lrmPositionPS.setDouble(4, endMeasure)
            lrmPositionPS.addBatch()

            assetLinkPS.setLong(1, assetId);
            assetLinkPS.setLong(2, lrmPositionId);
            assetLinkPS.addBatch();
            this.synchronized {
              insertSpeedLimitsCount += 1;
            }
          }
        }
        assetPS.executeBatch()
        lrmPositionPS.executeBatch()
        assetLinkPS.executeBatch()
        assetPS.close();
        lrmPositionPS.close()
        assetLinkPS.close()
      }

      this.synchronized {
        if (insertSpeedLimitsCount > 0) {
          val speedLimitsLeft = count-insertSpeedLimitsCount;
          val timeNow = System.currentTimeMillis()
          val average =  (timeNow-startSelect)/insertSpeedLimitsCount
          val leftHours = speedLimitsLeft*average/(1000*60*60)
          val leftMins = speedLimitsLeft*average/(1000*60)%60
          printf("\r run time: %dmin | count: %d/%d %1.2f%% | average: %dms | time left: %dh %dmin | select time: %dms",
            (timeNow - startSelect)/60000,
            insertSpeedLimitsCount, count, insertSpeedLimitsCount/(count*1.0) * 100,
            average,
            leftHours, leftMins,
            timeNow - selectStartTime);
        }
      }
    }
  }

  private def busStopsToImport(dataSet: ImportDataSet) = {
    val table = dataSet.busStopTable
    dataSet.database().withDynSession {
      sql"""
        select katos, pysakki_id, pysakkityyppi, objectid, tielinkkitunnus, kaista, puoli, alkum, loppum from #$table where tielinkkitunnus is not null
      """.as[(SimpleBusStop, SimpleLRMPosition)].list
    }
  }

  def insertLrmPositions(lrmPositions: Seq[SimpleLRMPosition], targetDbSession: Session) {
    var elementCount = 0
      val ps = targetDbSession.prepareStatement("insert into lrm_position (id, road_link_id, event_type, lane_code, side_code, start_measure, end_measure) values (?, ?, 1, ?, ?, ?, ?)")
    def batch(lrm: SimpleLRMPosition) {
      ps.setLong(1, lrm.id)
      ps.setLong(2, lrm.roadLinkId)
      ps.setInt(3, lrm.laneCode)
      ps.setInt(4, lrm.sideCode)
      ps.setDouble(5, lrm.startMeasure)
      ps.setDouble(6, lrm.endMeasure)
      ps.addBatch
      elementCount = elementCount + 1
      println("Added LRM " + lrm.id + " to batch as element " + elementCount)
    }

    lrmPositions foreach batch
    ps.executeBatch
    println("Executed batch of " + lrmPositions.length + " LRM positions")
    ps.close
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
      val assetId = busStop.assetId.getOrElse(sql"select primary_key_seq.nextval from dual".as[Long].first)

      sqlu"""
        insert into asset(id, external_id, asset_type_id, lrm_position_id, created_by, valid_from, valid_to)
        values($assetId, ${busStop.busStopId}, ${typeProps.busStopAssetTypeId}, ${busStop.lrmPositionId}, $Modifier, ${busStop.validFrom}, ${busStop.validTo.getOrElse(null)})
      """.execute

      val bearing = assetProvider.getAssetById(assetId) match {
        case Some(a) =>
          assetProvider.getRoadLinkById(a.roadLinkId) match {
            case Some(rl) => GeometryUtils.calculateBearing(a, rl)
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
      insertSingleChoiceValue(typeProps.administratorPropertyId, assetId, 4)
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
