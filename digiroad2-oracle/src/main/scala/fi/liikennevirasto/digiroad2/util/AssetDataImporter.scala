package fi.liikennevirasto.digiroad2.util

import java.io.{FileWriter, BufferedWriter, File}
import java.util.Properties
import javax.sql.DataSource

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.{SideCode, BoundingRectangle, TrafficDirection, LinkType}
import fi.liikennevirasto.digiroad2.linearasset.{Prohibitions, ProhibitionValue, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import org.joda.time.format.{PeriodFormatterBuilder, ISOPeriodFormat, DateTimeFormatter}

import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.oracle.{OracleSpatialAssetDao, Sequences}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.{SimpleBusStop, _}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.updateAssetGeometry
import _root_.oracle.sql.STRUCT
import org.joda.time._
import org.slf4j.LoggerFactory
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._
import com.github.tototoshi.slick.MySQLJodaSupport._

import slick.util.CloseableIterator

object AssetDataImporter {

  case class SimpleBusStop(shelterType: Int,
                           assetId: Option[Long] = None,
                           busStopId: Option[Long],
                           busStopType: Seq[Int],
                           lrmPositionId: Long,
                           validFrom: LocalDate = LocalDate.now,
                           validTo: Option[LocalDate] = None,
                           point: Point,
                           roadLinkId: Long,
                           municipalityCode: Int,
                           bearing: Double)
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
      val selectStartTime = System.currentTimeMillis()
      val segments = dataSet.database().withDynSession {
        query.as[(Int, String)].list
      }
      OracleDatabase.withDynSession {
        val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, 20, SYSDATE, 'dr1_conversion')")
        val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
        val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
        val speedLimitPS = dynamicSession.prepareStatement("insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date, modified_by) values (?, (select id from enumerated_value where value = ? and property_id = (select id from property where public_id = 'rajoitus')), (select id from property where public_id = 'rajoitus'), sysdate, 'dr1_conversion')")

        segments.foreach { segment =>
          val assetId = Sequences.nextPrimaryKeySeqValue
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
            val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

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

  def exportCsv(fileName: String, droppedLimits: List[(Long, Long, Int, Int, Int, Int, Boolean)]): Unit = {
    val headerLine = "mml_id; road_link_id; start_measure; end_measure; value \n"
    val data = droppedLimits.map { x =>
      s"""${x._1}; ${x._2}; ${x._3}; ${x._4}; ${x._5}"""
    }.mkString("\n")

    val file = new File(fileName + ".csv")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(headerLine + data + "\n")
    bw.close()
  }

  def generateDroppedNumericalLimits(vvhServiceHost: String): Unit = {
    val roadLinkService = new VVHRoadLinkService(new VVHClient(vvhServiceHost), null)
    val startTime = DateTime.now()
    val asset_name = Map(
      30 -> "total_weight_limits",
      40 -> "trailer_truck_weight_limits",
      50 -> "axle_weight_limits",
      60 -> "bogie_weight_limits",
      70 -> "height_limits",
      80 -> "length_limits",
      90 -> "width_limits",
      100 -> "lit_roads",
      110 -> "paved_roads",
      120 -> "road_widths",
      130 -> "roads_affected_by_thawing",
      150 -> "congestion_tendency",
      170 -> "traffic_volumes",
      140 -> "number_of_lanes",
      160 -> "mass_transit_lanes")

    val limits = OracleDatabase.withDynSession {
      sql"""
           select pos.MML_ID, pos.road_link_id, pos.start_measure, pos.end_measure, s.value, a.asset_type_id, a.floating
           from asset a
           join ASSET_LINK al on a.id = al.asset_id
           join LRM_POSITION pos on al.position_id = pos.id
           left join number_property_value s on s.asset_id = a.id
           where a.asset_type_id in (#${asset_name.keys.mkString(",")})
           and (valid_to is null or valid_to >= sysdate)
         """.as[(Long, Long, Int, Int, Int, Int, Boolean)].list
    }
    println("*** fetched all numerical limits from DB " + Seconds.secondsBetween(startTime, DateTime.now()).getSeconds)

    val existingMmlIds = roadLinkService.fetchVVHRoadlinks(limits.map(_._1).toSet).map(_.mmlId)
    println("*** fetched all road links from VVH "  + Seconds.secondsBetween(startTime, DateTime.now()).getSeconds)

    val nonExistingLimits = limits.filter { limit => !existingMmlIds.contains(limit._1) }
    println("*** calculated dropped links "  + Seconds.secondsBetween(startTime, DateTime.now()).getSeconds)

    val floatingLimits = limits.filter(_._7)

    (nonExistingLimits ++ floatingLimits).groupBy(_._6).foreach { case (key, values) =>
      exportCsv(asset_name(key), values)
    }
    println("*** exported CSV files "  + Seconds.secondsBetween(startTime, DateTime.now()).getSeconds)
  }

  def expireSplitAssetsWithoutMml(typeId: Int) = {
    val chunkSize = 1000
    val splitAssetsWithoutMmlIdFilter = """
      a.created_by like 'split_linearasset_%'
      and lrm.mml_id is null
      and (a.valid_to > sysdate or a.valid_to is null)"""
    val (minId, maxId) = getAssetIdRangeWithFilter(typeId, splitAssetsWithoutMmlIdFilter)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case(chunkStart, chunkEnd) =>
      val start = System.currentTimeMillis()
      expireSplitLinearAssetsWithoutMmlId(typeId, chunkStart, chunkEnd)
      println("*** Processed linear assets between " + chunkStart + " and " + chunkEnd + " in " + (System.currentTimeMillis() - start) + " ms.")
    }
  }

  def importPavedRoadsFromConversion(conversionDatabase: DatabaseDef) = {
    importLinearAssetsFromConversion(conversionDatabase, 26, 110)
  }

  def importRoadWidthsFromConversion(conversionDatabase: DatabaseDef) = {
    importLinearAssetsFromConversion(conversionDatabase, 8, 120)
  }

  def importRoadsAffectedByThawingFromConversion(conversionDatabase: DatabaseDef) = {
    importLinearAssetsFromConversion(conversionDatabase, 6, 130)
  }

  def importTrafficVolumesFromConversion(conversionDatabase: DatabaseDef) = {
    importLinearAssetsFromConversion(conversionDatabase, 33, 170)
  }

  def importNumberOfLanesFromConversion(conversionDatabase: DatabaseDef) = {
    importLinearAssetsFromConversion(conversionDatabase, 5, 140)
  }

  def importWinterSpeedLimits(conversionDatabase: DatabaseDef) = {
    importLinearAssetsFromConversion(conversionDatabase, 31, 180)
  }

  def importProhibitions(conversionDatabase: DatabaseDef, vvhServiceHost: String) = {
    val municipality = 235
    val conversionTypeId = 29
    val typeId = 190
    val vvhClient = new VVHClient(vvhServiceHost)

    println("*** Fetching prohibitions from conversion database")
    val startTime = DateTime.now()

    val allLinks = conversionDatabase.withDynSession {
      sql"""
          select s.segm_id, s.tielinkki_id, t.mml_id, s.alkum, s.loppum, t.kunta_nro, s.arvo, s.puoli
          from segments s
          join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
          where s.tyyppi = $conversionTypeId
          and kunta_nro = $municipality
       """.as[(Long, Long, Long, Double, Double, Int, Int, Int)].list
    }

    println(s"*** Fetched ${allLinks.length} prohibitions from conversion database in ${humanReadableDurationSince(startTime)}")

    val roadLinks = vvhClient.fetchVVHRoadlinks(allLinks.map(_._3).toSet)
    val groupSize = 10000
    val groupedLinks = allLinks.grouped(groupSize).toList
    val totalGroupCount = groupedLinks.length

    OracleDatabase.withDynTransaction {
      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, ?, SYSDATE, 'dr1_conversion')")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
      val valuePS = dynamicSession.prepareStatement("insert into prohibition_value (id, asset_id, type) values (?, ?, ?)")

      println(s"*** Importing ${allLinks.length} links in $totalGroupCount groups of $groupSize each")

      groupedLinks.zipWithIndex.foreach { case (links, i) =>
        val startTime = DateTime.now()
        val convertedProhibitions = convertToProhibitions(links, roadLinks)
        convertedProhibitions.foreach {
          case Right(asset) =>
            val assetId = Sequences.nextPrimaryKeySeqValue
            assetPS.setLong(1, assetId)
            assetPS.setInt(2, typeId)
            assetPS.addBatch()

            val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
            lrmPositionPS.setLong(1, lrmPositionId)
            lrmPositionPS.setLong(2, asset.mmlId)
            lrmPositionPS.setDouble(3, asset.startMeasure)
            lrmPositionPS.setDouble(4, asset.endMeasure)
            lrmPositionPS.setInt(5, asset.sideCode)
            lrmPositionPS.addBatch()

            assetLinkPS.setLong(1, assetId)
            assetLinkPS.setLong(2, lrmPositionId)
            assetLinkPS.addBatch()

            val value: Prohibitions = asset.value.get.asInstanceOf[Prohibitions]
            value.prohibitions.foreach { prohibitionValue =>
              val valueId = Sequences.nextPrimaryKeySeqValue
              valuePS.setLong(1, valueId)
              valuePS.setLong(2, assetId)
              valuePS.setLong(3, prohibitionValue.typeId)
              valuePS.addBatch()
            }
          case Left(validationError) => println(s"*** $validationError")
        }

        assetPS.executeBatch()
        lrmPositionPS.executeBatch()
        assetLinkPS.executeBatch()
        valuePS.executeBatch()

        println(s"*** Imported ${links.length} linear assets in ${humanReadableDurationSince(startTime)} (done ${i + 1}/$totalGroupCount)" )
      }
      assetPS.close()
      lrmPositionPS.close()
      assetLinkPS.close()
      valuePS.close()
    }

    println(s"Imported ${allLinks.length} linear assets in ${humanReadableDurationSince(startTime)}")
  }

  private def expandSegments(segments: Seq[(Long, Long, Long, Double, Double, Int, Int, Int)]): Seq[(Long, Long, Long, Double, Double, Int, Int, Int)] = {
    if (segments.forall(_._8 == 1)) segments
    else {
      val (bothSided, oneSided) = segments.partition(_._8 == 1)
      val splitSegments = bothSided.flatMap { x => Seq(x.copy(_8 = 2), x.copy(_8 = 3)) }
      splitSegments ++ oneSided
    }
  }

  def convertToProhibitions(prohibitionSegments: Seq[(Long, Long, Long, Double, Double, Int, Int, Int)], roadLinks: Seq[VVHRoadlink]): Seq[Either[String, PersistedLinearAsset]] = {
    val (segmentsWithRoadLink, segmentsWithoutRoadLink) = prohibitionSegments.partition { s => roadLinks.exists(_.mmlId == s._3) }
    val (segmentsOfInvalidType, validSegments) = segmentsWithRoadLink.partition { s => Set(21, 22).contains(s._7) }
    val segmentsByMmlId = validSegments.groupBy(_._3)
    segmentsByMmlId.flatMap { case (mmlId, segments) =>
      val roadLinkLength = GeometryUtils.geometryLength(roadLinks.find(_.mmlId == mmlId).get.geometry)
      val expandedSegments = expandSegments(segments)

      expandedSegments.groupBy(_._8).map { case (sideCode, segmentsPerSide) =>
        val prohibitionValues = segmentsPerSide.map { x => ProhibitionValue(x._7, Nil, Nil) }
        Right(PersistedLinearAsset(0l, mmlId, sideCode, Some(Prohibitions(prohibitionValues)), 0.0, roadLinkLength, None, None, None, None, false, 190))
      }
    }.toSeq ++
      segmentsWithoutRoadLink.map { s => Left(s"No VVH road link found for mml id ${s._3}. ${s._1} dropped.") } ++
      segmentsOfInvalidType.map { s => Left(s"Invalid type for prohibition. ${s._1} dropped.") }
  }

  def importLinearAssetsFromConversion(conversionDatabase: DatabaseDef, conversionTypeId: Int, typeId: Int) = {
    println("*** Fetching asset links from conversion database")
    val startTime = DateTime.now()

    val allLinks = conversionDatabase.withDynSession {
      sql"""
          select s.tielinkki_id, t.mml_id, s.alkum, s.loppum, t.kunta_nro, s.arvo, s.puoli
          from segments s
          join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
          where s.tyyppi = $conversionTypeId
       """.as[(Long, Long, Double, Double, Int, Int, Int)].list
    }

    println(s"*** Fetched ${allLinks.length} asset links from conversion database in ${humanReadableDurationSince(startTime)}")

    val groupSize = 10000
    val groupedLinks = allLinks.grouped(groupSize).toList
    val totalGroupCount = groupedLinks.length

    OracleDatabase.withDynTransaction {
      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, ?, SYSDATE, 'dr1_conversion')")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?, ?)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
      val valuePS = dynamicSession.prepareStatement("insert into number_property_value (id, asset_id, property_id, value) values (?, ?, (select id from property where public_id = 'mittarajoitus'), ?)")

      println(s"*** Importing ${allLinks.length} links in $totalGroupCount groups of $groupSize each")

      groupedLinks.zipWithIndex.foreach { case (links, i) =>
        val startTime = DateTime.now()

        links.foreach { case (roadLinkId, mmlId, startMeasure, endMeasure, _, value, sideCode) =>
          val assetId = Sequences.nextPrimaryKeySeqValue
          assetPS.setLong(1, assetId)
          assetPS.setInt(2, typeId)
          assetPS.addBatch()

          val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
          lrmPositionPS.setLong(1, lrmPositionId)
          lrmPositionPS.setLong(2, roadLinkId)
          lrmPositionPS.setLong(3, mmlId)
          lrmPositionPS.setDouble(4, startMeasure)
          lrmPositionPS.setDouble(5, endMeasure)
          lrmPositionPS.setInt(6, sideCode)
          lrmPositionPS.addBatch()

          assetLinkPS.setLong(1, assetId)
          assetLinkPS.setLong(2, lrmPositionId)
          assetLinkPS.addBatch()

          val valueId = Sequences.nextPrimaryKeySeqValue
          valuePS.setLong(1, valueId)
          valuePS.setLong(2, assetId)
          valuePS.setLong(3, value)
          valuePS.addBatch()
        }

        assetPS.executeBatch()
        lrmPositionPS.executeBatch()
        assetLinkPS.executeBatch()
        valuePS.executeBatch()

        println(s"*** Imported ${links.length} linear assets in ${humanReadableDurationSince(startTime)} (done ${i + 1}/$totalGroupCount)" )
      }
      assetPS.close()
      lrmPositionPS.close()
      assetLinkPS.close()
      valuePS.close()
    }

    println(s"Imported ${allLinks.length} linear assets in ${humanReadableDurationSince(startTime)}")
  }

  def humanReadableDurationSince(startTime: DateTime): String = {
    val periodFormatter = new PeriodFormatterBuilder().appendDays().appendSuffix(" days, ").appendHours().appendSuffix(" hours, ").appendSeconds().appendSuffix(" seconds").toFormatter
    val humanReadablePeriod = periodFormatter.print(new Period(startTime, DateTime.now()))
    humanReadablePeriod
  }

  def importLitRoadsFromConversion(conversionDatabase: DatabaseDef) = {
    val litRoadLinks: Seq[(Long, Long, Double, Double, Int)] = conversionDatabase.withDynSession {
      sql"""
          select s.tielinkki_id, t.mml_id, s.alkum, s.loppum, t.kunta_nro
          from segments s
          join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
          where s.tyyppi = 27
       """.as[(Long, Long, Double, Double, Int)].list
    }
    println("*** found " + litRoadLinks.length + " lit road links from Conversion DB")

    val litRoadLinksByMunicipality = litRoadLinks.groupBy(_._5)
    val totalMunicipalityCount = litRoadLinksByMunicipality.keys.size
    var municipalityCount = 0

    OracleDatabase.withDynTransaction {
      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, ?, SYSDATE, 'dr1_conversion')")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, MML_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?, 1)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")

      litRoadLinksByMunicipality.foreach { case (municipalityCode, litRoads) =>
        val startTime = DateTime.now()
        litRoads.foreach { case (roadLinkId, mmlId, startMeasure, endMeasure, _) =>
          val assetId = Sequences.nextPrimaryKeySeqValue
          assetPS.setLong(1, assetId)
          assetPS.setInt(2, 100)
          assetPS.addBatch()

          val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
          lrmPositionPS.setLong(1, lrmPositionId)
          lrmPositionPS.setLong(2, roadLinkId)
          lrmPositionPS.setLong(3, mmlId)
          lrmPositionPS.setDouble(4, startMeasure)
          lrmPositionPS.setDouble(5, endMeasure)
          lrmPositionPS.addBatch()

          assetLinkPS.setLong(1, assetId)
          assetLinkPS.setLong(2, lrmPositionId)
          assetLinkPS.addBatch()
        }
        assetPS.executeBatch()
        lrmPositionPS.executeBatch()
        assetLinkPS.executeBatch()

        val seconds = Seconds.secondsBetween(startTime, DateTime.now()).getSeconds
        municipalityCount += 1
        println("*** imported lit roads for municipality: " + municipalityCode + " in " + seconds + " seconds  (done " + municipalityCount + "/" + totalMunicipalityCount + " municipalities)" )
      }
      assetPS.close()
      lrmPositionPS.close()
      assetLinkPS.close()
    }
  }

  def importTotalWeightLimits(database: DatabaseDef) = {
    importNumericalLimits(database, 22, 30)
  }

  def importNumericalLimits(database: DatabaseDef, sourceTypeId: Int, targetTypeId: Int): Unit = {
    val query =
      sql"""
       select segm_id, tielinkki_id, puoli, alkum, loppum, arvo
         from segments
         where tyyppi = $sourceTypeId

       """
    val numericalLimitLinks: Seq[(Long, Long, Int, Double, Double, Int)] = database.withDynSession {
      query.as[(Long, Long, Int, Double, Double, Int)].list
    }
    val numericalLimits: Map[Long, Seq[(Long, Long, Int, Double, Double, Int)]] = numericalLimitLinks.groupBy(_._1)
    OracleDatabase.withDynTransaction {
      numericalLimits.foreach { numericalLimit ⇒
        val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, ?, SYSDATE, 'dr1_conversion')")
        val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
        val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
        val numericalLimitPS = dynamicSession.prepareStatement("insert into number_property_value(id, asset_id, property_id, value) values (primary_key_seq.nextval, ?, (select id from property where public_id = 'mittarajoitus'), ?)")

        val (_, links) = numericalLimit
        val assetId = Sequences.nextPrimaryKeySeqValue
        assetPS.setLong(1, assetId)
        assetPS.setInt(2, targetTypeId)
        assetPS.addBatch()

        links.foreach { link ⇒
          val (_, roadLinkId, sideCode, startMeasure, endMeasure, _) = link
          val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
          lrmPositionPS.setLong(1, lrmPositionId)
          lrmPositionPS.setLong(2, roadLinkId)
          lrmPositionPS.setDouble(3, startMeasure)
          lrmPositionPS.setDouble(4, endMeasure)
          lrmPositionPS.setInt(5, sideCode)
          lrmPositionPS.addBatch()

          assetLinkPS.setLong(1, assetId)
          assetLinkPS.setLong(2, lrmPositionId)
          assetLinkPS.addBatch()
        }

        val limit = links.head._6
        numericalLimitPS.setLong(1, assetId)
        numericalLimitPS.setInt(2, limit)
        numericalLimitPS.addBatch()

        assetPS.executeBatch()
        lrmPositionPS.executeBatch()
        assetLinkPS.executeBatch()
        numericalLimitPS.executeBatch()
        assetPS.close()
        lrmPositionPS.close()
        assetLinkPS.close()
        numericalLimitPS.close()
      }
    }
  }

  def importManoeuvres(database: DatabaseDef): Unit = {
    throw new NotImplementedError("TODO: Fix importManoeuvres to fetch manoeuvre ids from manoeuvre sequence. Current implementation imports manoeuvres with clashing ids")
    val query =
      sql"""
        select kaan_id, kaan_tyyppi, tl_dr1_id, elem_jarjestyslaji
          from kaantymismaarays
        """
    val manoeuvres: Seq[(Long, Int, Long, Int)] = database.withDynSession {
      query.as[(Long, Int, Long, Int)].list
    }
    OracleDatabase.withDynTransaction {
      manoeuvres.foreach { manoeuvre =>
        val (id, manoeuvreType, roadLinkId, elementType) = manoeuvre
        sqlu"""
          insert into manoeuvre (id, type, road_link_id, element_type, modified_by)
          values ($id, $manoeuvreType, $roadLinkId, $elementType, 'dr1_conversion')
          """.execute
      }
    }
  }

  def getTypeProperties = {
    OracleDatabase.withDynSession {
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
    OracleDatabase.withDynSession {
      val assetId = busStop.assetId.getOrElse(Sequences.nextPrimaryKeySeqValue)

      sqlu"""
        insert into asset(id, external_id, asset_type_id, created_by, valid_from, valid_to, municipality_code, bearing)
        values($assetId, ${busStop.busStopId}, ${typeProps.busStopAssetTypeId},
               $Modifier, ${busStop.validFrom}, ${busStop.validTo.getOrElse(null)},
               ${busStop.municipalityCode}, ${busStop.bearing})
      """.execute

      sqlu"""
         insert into asset_link(asset_id, position_id)
         values($assetId, ${busStop.lrmPositionId})
      """.execute

      updateAssetGeometry(assetId, busStop.point)
      busStop.busStopType.foreach { busStopType =>
        insertMultipleChoiceValue(typeProps.busStopTypePropertyId, assetId, busStopType)
      }

      insertTextPropertyData(typeProps.accessibilityPropertyId, assetId, "Ei tiedossa")
      insertSingleChoiceValue(typeProps.administratorPropertyId, assetId, 2)
      insertSingleChoiceValue(typeProps.shelterTypePropertyId, assetId, busStop.shelterType)
    }
  }

  def importMMLIdsOnMassTransitStops(conversionDB: DatabaseDef) {
    OracleDatabase.withDynSession {
      conversionDB.withSession { conversionSession =>
        val municipalityCodes: CloseableIterator[Int] = sql"""select id from municipality""".as[Int].iterator
        municipalityCodes.foreach { municipalityCode =>
          println(s"Importing MML IDs on mass transit stops in municipality: $municipalityCode")
          val roadLinkIds: CloseableIterator[(Long, Long, Option[Long], Option[Long])] =
            sql"""select a.id, lrm.id, lrm.road_link_id, lrm.prod_road_link_id
                from asset a
                join asset_link al on a.id = al.asset_id
                join lrm_position lrm on lrm.id = al.position_id
                where a.asset_type_id = 10 and a.municipality_code = $municipalityCode"""
              .as[(Long, Long, Option[Long], Option[Long])].iterator
          val mmlIds: CloseableIterator[(Long, Long, Option[Long])] =
            roadLinkIds.map { roadLinkId =>
              val (assetId, lrmId, testRoadLinkId, productionRoadLinkId) = roadLinkId
              val mmlId: Option[Long] = (testRoadLinkId, productionRoadLinkId) match {
                case (_, Some(prodId)) => sql"""select mml_id from tielinkki_ctas where dr1_id = $prodId""".as[Long].firstOption(conversionSession)
                case (Some(testId), None) => sql"""select mml_id from tielinkki where objectid = $testId""".as[Long].firstOption(conversionSession)
                case _ => None
              }
              (assetId, lrmId, mmlId)
            }

          mmlIds.foreach { case (assetId, lrmId, mmlId) =>
            sqlu"""update lrm_position set mml_id = $mmlId where id = $lrmId""".execute
            if (mmlId.isEmpty) {
              sqlu"""update asset set floating = 1 where id = $assetId""".execute
            }
          }
        }
      }
    }
  }

  def importMMLIdsOnNumericalLimit(conversionDB: DatabaseDef, assetTypeId: Int) {
    OracleDatabase.withDynSession {
      conversionDB.withSession { conversionSession =>
        val roadLinkIds: CloseableIterator[(Long, Option[Long])] =
          sql"""select lrm.id, lrm.road_link_id
                from asset a
                join asset_link al on a.id = al.asset_id
                join lrm_position lrm on lrm.id = al.position_id
                where a.asset_type_id = $assetTypeId"""
            .as[(Long, Option[Long])].iterator
        val mmlIds: CloseableIterator[(Long, Option[Long])] =
          roadLinkIds.map { roadLink =>
            val (lrmId, roadLinkId) = roadLink
            val mmlId: Option[Long] = roadLinkId match {
              case Some(dr1Id) => sql"""select mml_id from tielinkki_ctas where dr1_id = $dr1Id""".as[Long].firstOption(conversionSession)
              case _ => None
            }
            (lrmId, mmlId)
          }
        mmlIds.foreach { case (lrmId, mmlId) =>
          sqlu"""update lrm_position set mml_id = $mmlId where id = $lrmId""".execute
        }
      }
    }
  }
  
  def generateValuesForLitRoads(): Unit = {
    processInChunks(100, "lit roads") { (chunkStart, chunkEnd) =>
      withDynTransaction {
        val litRoads = sql"""
          select a.id, p.id
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            join property p on p.public_id = 'mittarajoitus'
            left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
            where a.id between $chunkStart and $chunkEnd and a.asset_type_id = 100
        """.as[(Long, Long)].list

        litRoads.foreach { case (assetId, propertyId) =>
          val id = Sequences.nextPrimaryKeySeqValue
          sqlu"""
            insert into number_property_value (id, asset_id, property_id, value)
            values ($id, $assetId, $propertyId, 1)
          """.execute
        }

        println(s"Assigned value to ${litRoads.length} lit road assets")
      }
    }
  }

  def processInChunks(typeId: Int, assetTypeName: String)(f: (Int, Int) => Unit): Unit = {
    val chunkSize = 1000
    val (minId, maxId) = getAssetIdRange(typeId, true)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case (chunkStart, chunkEnd) =>
      val startTime = System.currentTimeMillis()
      f(chunkStart, chunkEnd)
      println(s"*** Processed $assetTypeName between $chunkStart and $chunkEnd in ${System.currentTimeMillis() - startTime} ms.")
    }
  }
  private def getAssetIdRange(typeId: Int, includeSingleLinkAssets: Boolean = false): (Int, Int) = {
    val multiSegmentFilter = if (includeSingleLinkAssets) "" else "and (select count(*) from asset_link where asset_id = a.id) > 1"
    withDynSession {
      sql"""
        select min(a.id), max(a.id)
        from asset a
        where a.asset_type_id = $typeId and floating = 0 #$multiSegmentFilter
      """.as[(Int, Int)].first
    }
  }

  private def getAssetIdRangeWithFilter(typeId: Int, filter: String): (Int, Int) = {
    withDynSession {
      sql"""
        select min(a.id), max(a.id)
        from asset a
        join asset_link al on al.asset_id = a.id
        join lrm_position lrm on lrm.id = al.position_id
        where a.asset_type_id = $typeId and #$filter
      """.as[(Int, Int)].first
    }
  }
  private def getAssetIdRangeOfMultiLinkAssets(typeId: Int): (Int, Int) = {
    withDynSession {
      sql"""
        select min(a.id), max(a.id)
        from asset a
        where a.asset_type_id = $typeId and floating = 0 and (select count(*) from asset_link where asset_id = a.id) > 1
      """.as[(Int, Int)].first
    }
  }
  
  private def splitSpeedLimits(chunkStart: Long, chunkEnd: Long) = {
    val dao = new OracleLinearAssetDao {
      override val roadLinkService: RoadLinkService = null
    }

    withDynTransaction {
      val speedLimitLinks = sql"""
            select a.id, pos.mml_id, pos.side_code, e.value, pos.start_measure, pos.end_measure
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
            join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
            join enumerated_value e on s.enumerated_value_id = e.id
            where a.asset_type_id = 20
            and floating = 0
            and (select count(*) from asset_link where asset_id = a.id) > 1
            and a.id between $chunkStart and $chunkEnd
          """.as[(Long, Long, Int, Option[Int], Double, Double)].list

      speedLimitLinks.foreach { speedLimitLink =>
        val (id, mmlId, sideCode, value, startMeasure, endMeasure) = speedLimitLink
        dao.forceCreateLinearAsset(s"split_speedlimit_$id", 20, mmlId, (startMeasure, endMeasure), SideCode(sideCode), value, dao.insertEnumeratedValue(_, "rajoitus"))
      }
      println(s"created ${speedLimitLinks.length} new single link speed limits")

      val speedLimitsToFloat = speedLimitLinks.map(_._1).toSet
      dao.floatLinearAssets(speedLimitsToFloat)
      println(s"removed ${speedLimitsToFloat.size} multilink speed limits")
    }
  }

  private def splitLinearAssets(typeId: Int, chunkStart: Long, chunkEnd: Long) = {
    val dao = new OracleLinearAssetDao {
      override val roadLinkService: RoadLinkService = null
    }

    withDynTransaction {
      val linearAssetLinks = sql"""
            select a.id, pos.mml_id, pos.side_code, pos.start_measure, pos.end_measure, n.value
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            left join number_property_value n on a.id = n.asset_id
            where a.asset_type_id = $typeId
            and floating = 0
            and (select count(*) from asset_link where asset_id = a.id) > 1
            and a.id between $chunkStart and $chunkEnd
          """.as[(Long, Long, Int, Double, Double, Option[Int])].list

      linearAssetLinks.foreach { case (id, mmlId, sideCode, startMeasure, endMeasure, value) =>
        dao.forceCreateLinearAsset(s"split_linearasset_$id", typeId, mmlId, (startMeasure, endMeasure), SideCode(sideCode), value, dao.insertValue(_, "mittarajoitus"))
      }

      println(s"created ${linearAssetLinks.length} new single link linear assets")

      val assetsIdsToExpire = linearAssetLinks.map(_._1).toSet
      if (assetsIdsToExpire.size > 0) {
        val assetsIdsToExpireString = assetsIdsToExpire.mkString(",")
        sqlu"""update asset
               set modified_by = 'expired_splitted_linearasset', modified_date = sysdate, valid_to = sysdate
               where id in (#$assetsIdsToExpireString)""".execute
      }
      println(s"removed ${assetsIdsToExpire.size} multilink assets")
    }
  }

  private def expireSplitLinearAssetsWithoutMmlId(typeId: Int, chunkStart: Long, chunkEnd: Long) = {
    withDynTransaction {
      sqlu"""
        update asset
          set modified_by = 'expired_asset_without_mml', modified_date = sysdate, valid_to = sysdate
          where id in (
            select a.id
            from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.created_by like 'split_linearasset_%'
            and lrm.mml_id is null
            and (a.valid_to > sysdate or a.valid_to is null)
            and a.id between $chunkStart and $chunkEnd
            and a.asset_type_id = $typeId)
        """.execute

      println(s"expired assets with ids between $chunkStart and $chunkEnd")
    }
  }

  def splitMultiLinkSpeedLimitsToSingleLinkLimits() {
    val chunkSize = 1000
    val (minId, maxId) = getAssetIdRange(20)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case(chunkStart, chunkEnd) =>
      val start = System.currentTimeMillis()
      splitSpeedLimits(chunkStart, chunkEnd)
      println("*** Processed speed limits between " + chunkStart + " and " + chunkEnd + " in " + (System.currentTimeMillis() - start) + " ms.")
    }
  }

  def splitMultiLinkAssetsToSingleLinkAssets(typeId: Int) {
    val chunkSize = 1000
    val multiLinkLinearAssetsFilter = """
        (a.valid_to > sysdate or a.valid_to is null)
        and (select count(*) from asset_link where asset_id = a.id) > 1"""
    val (minId, maxId) = getAssetIdRangeWithFilter(typeId, multiLinkLinearAssetsFilter)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case(chunkStart, chunkEnd) =>
      val start = System.currentTimeMillis()
      splitLinearAssets(typeId, chunkStart, chunkEnd)
      println("*** Processed linear assets between " + chunkStart + " and " + chunkEnd + " in " + (System.currentTimeMillis() - start) + " ms.")
    }
  }

  def unfloatLinearAssets(): Unit = {
    withDynTransaction {
      sqlu"""
        update asset a set floating=0
        where a.asset_type_id in (30,40,50,60,70,80,90,100)
        and (select count(*) from asset_link where asset_id = a.id) > 1""".execute
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
