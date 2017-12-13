package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import javax.sql.DataSource

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.pointasset.oracle.{Obstacle, OracleObstacleDao}
import org.joda.time.format.{DateTimeFormat, PeriodFormat}
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.{SimpleBusStop, _}
import org.joda.time._
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._

import scala.collection.mutable

object
AssetDataImporter {
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
                             busStopAssetTypeId: Long, busStopTypePropertyId: Long, busStopLiViPropertyId: Long)

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

  def expireSplitAssetsWithoutMml(typeId: Int) = {
    val chunkSize = 1000
    val splitAssetsWithoutLinkIdFilter = """
      a.created_by like 'split_linearasset_%'
      and lrm.link_id is null
      and (a.valid_to > sysdate or a.valid_to is null)"""
    val (minId, maxId) = getAssetIdRangeWithFilter(typeId, splitAssetsWithoutLinkIdFilter)
    val chunks: List[(Int, Int)] = getBatchDrivers(minId, maxId, chunkSize)
    chunks.foreach { case(chunkStart, chunkEnd) =>
      val start = System.currentTimeMillis()
      expireSplitLinearAssetsWithoutLinkId(typeId, chunkStart, chunkEnd)
      println("*** Processed linear assets between " + chunkStart + " and " + chunkEnd + " in " + (System.currentTimeMillis() - start) + " ms.")
    }
  }

  def importEuropeanRoads(conversionDatabase: DatabaseDef, vvhHost: String) = {
    val roads = conversionDatabase.withDynSession {
      sql"""select link_id, eur_nro from eurooppatienumero""".as[(Long, String)].list
    }

    val roadsByLinkId = roads.foldLeft(Map.empty[Long, (Long, String)]) { (m, road) => m + (road._1 -> road) }

    val vvhClient = new VVHClient(vvhHost)
    val vvhLinks = vvhClient.roadLinkData.fetchByLinkIds(roadsByLinkId.keySet)
    val linksByLinkId = vvhLinks.foldLeft(Map.empty[Long, VVHRoadlink]) { (m, link) => m + (link.linkId -> link) }

    val roadsWithLinks = roads.map { road => (road, linksByLinkId.get(road._1)) }

    OracleDatabase.withDynTransaction {
      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, floating, CREATED_DATE, CREATED_BY) values (?, ?, ?, SYSDATE, 'dr1_conversion')")
      val propertyPS = dynamicSession.prepareStatement("insert into text_property_value (id, asset_id, property_id, value_fi) values (?, ?, ?, ?)")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure) values (?, ?, ?, ?, ?)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")

      val propertyId = Queries.getPropertyIdByPublicId("eurooppatienumero")

      roadsWithLinks.foreach { case ((linkId, eRoad), link) =>
        val assetId = Sequences.nextPrimaryKeySeqValue

        assetPS.setLong(1, assetId)
        assetPS.setInt(2, 260)
        assetPS.setBoolean(3, link.isEmpty)
        assetPS.addBatch()

        val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

        lrmPositionPS.setLong(1, lrmPositionId)
        lrmPositionPS.setLong(2, linkId)
        lrmPositionPS.setInt(3, SideCode.BothDirections.value)
        lrmPositionPS.setDouble(4, 0)
        lrmPositionPS.setDouble(5, link.map(_.geometry).map(GeometryUtils.geometryLength).getOrElse(0))
        lrmPositionPS.addBatch()

        assetLinkPS.setLong(1, assetId)
        assetLinkPS.setLong(2, lrmPositionId)
        assetLinkPS.addBatch()

        propertyPS.setLong(1, Sequences.nextPrimaryKeySeqValue)
        propertyPS.setLong(2, assetId)
        propertyPS.setLong(3, propertyId)
        propertyPS.setString(4, eRoad)
        propertyPS.addBatch()
      }

      assetPS.executeBatch()
      lrmPositionPS.executeBatch()
      assetLinkPS.executeBatch()
      propertyPS.executeBatch()

      assetPS.close()
      assetLinkPS.close()
      lrmPositionPS.close()
      propertyPS.close()
    }
  }

  def importProhibitions(conversionDatabase: DatabaseDef, vvhServiceHost: String) = {
    val conversionTypeId = 29
    val exceptionTypeId = 1
    val vvhClient = new VVHClient(vvhServiceHost)
    val typeId = 190

    println("*** Fetching prohibitions from conversion database")
    val startTime = DateTime.now()

    val prohibitions = conversionDatabase.withDynSession {
      sql"""
          select s.segm_id, t.link_id, s.alkum, s.loppum, t.kunta_nro, s.arvo, s.puoli, s.aika
          from segments s
          join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
          where s.tyyppi = $conversionTypeId and s.kaista is null
       """.as[(Long, Long, Double, Double, Int, Int, Int, Option[String])].list
    }

    val exceptions = conversionDatabase.withDynSession {
      sql"""
          select s.segm_id, t.link_id, s.arvo, s.puoli
          from segments s
          join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
          where s.tyyppi = $exceptionTypeId and s.kaista is null
       """.as[(Long, Long, Int, Int)].list
    }

    println(s"*** Fetched ${prohibitions.length} prohibitions from conversion database in ${humanReadableDurationSince(startTime)}")

    val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(prohibitions.map(_._2).toSet)

    val conversionResults = convertToProhibitions(prohibitions, roadLinks, exceptions)
    println(s"*** Importing ${prohibitions.length} prohibitions")

    val insertCount = OracleDatabase.withDynTransaction {
      insertProhibitions(typeId, conversionResults)
    }
    println(s"*** Persisted $insertCount linear assets in ${humanReadableDurationSince(startTime)}")
  }

  def insertProhibitions(typeId: Int, conversionResults: Seq[Either[String, PersistedLinearAsset]]): Int = {
      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, ?, SYSDATE, 'dr1_conversion')")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
      val valuePS = dynamicSession.prepareStatement("insert into prohibition_value (id, asset_id, type) values (?, ?, ?)")
      val exceptionPS = dynamicSession.prepareStatement("insert into prohibition_exception (id, prohibition_value_id, type) values (?, ?, ?)")
      val validityPeriodPS = dynamicSession.prepareStatement("insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (?, ?, ?, ?, ?)")

      conversionResults.foreach {
        case Right(asset) =>
          val assetId = Sequences.nextPrimaryKeySeqValue
          assetPS.setLong(1, assetId)
          assetPS.setInt(2, typeId)
          assetPS.addBatch()

          val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
          lrmPositionPS.setLong(1, lrmPositionId)
          lrmPositionPS.setLong(2, asset.linkId)
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
            prohibitionValue.exceptions.foreach { exceptionType =>
              val exceptionId = Sequences.nextPrimaryKeySeqValue
              exceptionPS.setLong(1, exceptionId)
              exceptionPS.setLong(2, valueId)
              exceptionPS.setInt(3, exceptionType)
              exceptionPS.addBatch()
            }
            prohibitionValue.validityPeriods.foreach { validityPeriod =>
              val validityPeriodId = Sequences.nextPrimaryKeySeqValue
              validityPeriodPS.setLong(1, validityPeriodId)
              validityPeriodPS.setLong(2, valueId)
              validityPeriodPS.setInt(3, validityPeriod.days.value)
              validityPeriodPS.setInt(4, validityPeriod.startHour)
              validityPeriodPS.setInt(5, validityPeriod.endHour)
              validityPeriodPS.addBatch()
            }
          }
        case Left(validationError) => println(s"*** $validationError")
      }

      val executedAssetInserts = assetPS.executeBatch().length
      lrmPositionPS.executeBatch()
      assetLinkPS.executeBatch()
      valuePS.executeBatch()
      exceptionPS.executeBatch()
      validityPeriodPS.executeBatch()


      assetPS.close()
      lrmPositionPS.close()
      assetLinkPS.close()
      valuePS.close()
      exceptionPS.close()
      validityPeriodPS.close()
      executedAssetInserts
  }

  private def expandSegments(segments: Seq[(Long, Long, Double, Double, Int, Int, Int, Option[String])], exceptionSideCodes: Seq[Int]): Seq[(Long, Long, Double, Double, Int, Int, Int, Option[String])] = {
    if (segments.forall(_._7 == 1) && exceptionSideCodes.forall(_ == 1)) segments
    else {
      val (bothSided, oneSided) = segments.partition(_._7 == 1)
      val splitSegments = bothSided.flatMap { x => Seq(x.copy(_7 = 2), x.copy(_7 = 3)) }
      splitSegments ++ oneSided
    }
  }

  def expandExceptions(exceptions: Seq[(Long, Long, Int, Int)], prohibitionSideCodes: Seq[(Int)]) = {
    if (exceptions.forall(_._4 == 1) && prohibitionSideCodes.forall(_ == 1)) exceptions
    else {
      val (bothSided, oneSided) = exceptions.partition(_._4 == 1)
      val splitExceptions = bothSided.flatMap { x => Seq(x.copy(_4 = 2), x.copy(_4 = 3)) }
      splitExceptions ++ oneSided
    }
  }

  private def parseProhibitionValues(segments: Seq[(Long, Long, Double, Double, Int, Int, Int, Option[String])], exceptions: Seq[(Long, Long, Int, Int)], linkId: Long, sideCode: Int): Seq[Either[String, ProhibitionValue]] = {
    val timeDomainParser = new TimeDomainParser
    segments.map { segment =>
      val exceptionsForProhibition = exceptions.filter { z => z._2 == linkId && z._4 == sideCode }.map(_._3).toSet

      segment._8 match {
        case None => Right(ProhibitionValue(segment._6, Set.empty, exceptionsForProhibition))
        case Some(timeDomainString) =>
          timeDomainParser.parse(timeDomainString) match {
            case Left(err) => Left(s"${err}. Dropped prohibition ${segment._1}.")
            case Right(periods) => Right(ProhibitionValue(segment._6, periods.toSet, exceptionsForProhibition))
          }
      }
    }
  }

  def convertToProhibitions(prohibitionSegments: Seq[(Long, Long, Double, Double, Int, Int, Int, Option[String])], roadLinks: Seq[VVHRoadlink], exceptions: Seq[(Long, Long, Int, Int)]): Seq[Either[String, PersistedLinearAsset]] = {
    def hasInvalidExceptionType(exception: (Long, Long, Int, Int)): Boolean = {
      !Set(21, 22, 10, 9, 27, 5, 8, 7, 6, 4, 15, 19, 13, 14, 24, 25).contains(exception._3)
    }

    def hasInvalidProhibitionType(prohibition: (Long, Long, Double, Double, Int, Int, Int, Option[String])): Boolean = {
      !Set(3, 2, 23, 12, 11, 26, 10, 9, 27, 5, 8, 7, 6, 4, 15, 19, 13, 14, 24, 25).contains(prohibition._6)
    }
    val (segmentsWithRoadLink, segmentsWithoutRoadLink) = prohibitionSegments.partition { s => roadLinks.exists(_.linkId == s._2) }
    val (segmentsOfInvalidType, validSegments) = segmentsWithRoadLink.partition { s => hasInvalidProhibitionType(s) }
    val segmentsByLinkId = validSegments.groupBy(_._2)
    val (exceptionsWithProhibition, exceptionsWithoutProhibition) = exceptions.partition { x => segmentsByLinkId.keySet.contains(x._2) }
    val (exceptionWithInvalidCode, validExceptions) = exceptionsWithProhibition.partition { x => hasInvalidExceptionType(x) }

    segmentsByLinkId.flatMap { case (linkId, segments) =>
      val roadLinkLength = GeometryUtils.geometryLength(roadLinks.find(_.linkId == linkId).get.geometry)
      val expandedSegments = expandSegments(segments, validExceptions.filter(_._2 == linkId).map(_._4))
      val expandedExceptions = expandExceptions(validExceptions.filter(_._2 == linkId), segments.map(_._7))
      val roadLinkSource = roadLinks.find(_.linkId == linkId).get.linkSource

      expandedSegments.groupBy(_._7).flatMap { case (sideCode, segmentsPerSide) =>
        val prohibitionResults = parseProhibitionValues(segmentsPerSide, expandedExceptions, linkId, sideCode)
        val linearAssets = prohibitionResults.filter(_.isRight).map(_.right.get) match {
          case Nil => Nil
          case prohibitionValues => Seq(Right(PersistedLinearAsset(0l, linkId, sideCode, Some(Prohibitions(prohibitionValues)), 0.0, roadLinkLength, None, None, None, None, false, 190, 0, None, roadLinkSource)))
        }
        val parseErrors = prohibitionResults.filter(_.isLeft).map(_.left.get).map(Left(_))
        linearAssets ++ parseErrors
      }
    }.toSeq ++
      segmentsWithoutRoadLink.map { s => Left(s"No VVH road link found for mml id ${s._2}. ${s._1} dropped.") } ++
      segmentsOfInvalidType.map { s => Left(s"Invalid type for prohibition. ${s._1} dropped.") } ++
      exceptionsWithoutProhibition.map { ex => Left(s"No prohibition found on mml id ${ex._2}. Dropped exception ${ex._1}.")} ++
      exceptionWithInvalidCode.map { ex => Left(s"Invalid exception. Dropped exception ${ex._1}.")}
  }

  def fetchProhibitionsByLinkIds(prohibitionAssetTypeId: Int, ids: Seq[Long], includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else "and a.floating = 0"

    val assets = MassQuery.withIds(ids.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code,
               pv.id, pv.type,
               pvp.type, pvp.start_hour, pvp.end_hour,
               pe.type,
               pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, pvp.start_minute, pvp.end_minute, pos.link_source
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join prohibition_value pv on pv.asset_id = a.id
          join #$idTableName i on i.id = a.id
          left join prohibition_validity_period pvp on pvp.prohibition_value_id = pv.id
          left join prohibition_exception pe on pe.prohibition_value_id = pv.id
          where a.asset_type_id = $prohibitionAssetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)
          #$floatingFilter"""
        .as[(Long, Long, Int, Long, Int, Option[Int], Option[Int], Option[Int], Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Int, Int)].list
    }

    val groupedByAssetId = assets.groupBy(_._1)
    val groupedByProhibitionId = groupedByAssetId.mapValues(_.groupBy(_._4))

    groupedByProhibitionId.map { case (assetId, rowsByProhibitionId) =>
      val (_, linkId, sideCode, _, _, _, _, _, _, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, _, _, linkSource) = groupedByAssetId(assetId).head
      val prohibitionValues = rowsByProhibitionId.keys.toSeq.sorted.map { prohibitionId =>
        val rows = rowsByProhibitionId(prohibitionId)
        val prohibitionType = rows.head._5
        val exceptions = rows.flatMap(_._9).toSet
        val validityPeriods = rows.filter(_._6.isDefined).map { case row =>
          ValidityPeriod(row._7.get, row._8.get, ValidityPeriodDayOfWeek(row._6.get), 1, 0)
        }.toSet
        ProhibitionValue(prohibitionType, validityPeriods, exceptions)
      }
      // TODO: when linear assets get included in change history
      PersistedLinearAsset(assetId, linkId, sideCode, Some(Prohibitions(prohibitionValues)), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, prohibitionAssetTypeId, 0, None, LinkGeomSource.apply(linkSource))
    }.toSeq
  }

  def importHazmatProhibitions() = {
    OracleDatabase.withDynTransaction {
      val assetIds =
        sql"""
          select a.id from asset a
             join prohibition_value pv on a.id = pv.ASSET_ID
             where pv.type in (24, 25)
             and a.asset_type_id = 190
        """.as[Long].list

      if (assetIds.nonEmpty) {
        val linearAssets = fetchProhibitionsByLinkIds(190, assetIds, true)
        val hazmatAssets = linearAssets.map { linearAsset =>
          val newValue = linearAsset.value.map {
            case Prohibitions(prohibitionValues) =>
              val hazMatProhibitions = prohibitionValues.filter { p => Set(24, 25).contains(p.typeId) }
              Prohibitions(hazMatProhibitions.map(_.copy(exceptions = Set.empty)))
            case x =>
              throw new IllegalArgumentException
          }
          linearAsset.copy(value = newValue)
        }

        insertProhibitions(210, hazmatAssets.map(Right(_)))

        val prohibitionValueIds = MassQuery.withIds(assetIds.toSet) { idTableName =>
           sql"""
              select pv.id from prohibition_value pv
              join #$idTableName i on i.id = pv.asset_id
              where type in (24, 25)""".as[Long].list
        }

        MassQuery.withIds(prohibitionValueIds.toSet) { idTableName =>
          sqlu"""delete from prohibition_validity_period
                 where prohibition_value_id in (select id from #$idTableName)""".execute
          sqlu"""delete from prohibition_exception where prohibition_value_id in (select id from #$idTableName)""".execute
        }

        MassQuery.withIds(assetIds.toSet) { idTableName =>
          sqlu"""delete from prohibition_value where asset_id in (select id from #$idTableName) and type in (24, 25)""".execute
        }

        sqlu"""delete from asset_link where asset_id in (select id from asset where asset_type_id=190 and id not in (select asset_id from prohibition_value))""".execute
        sqlu"""delete from asset where asset_type_id=190 and id not in (select asset_id from prohibition_value)""".execute
        sqlu"""delete from lrm_position pos where NOT EXISTS (select position_id from asset_link where position_id = pos.id)""".execute
      } else {
        println("No hazardous material prohibitions with old type id found for import")
      }
    }
  }

  def getTypeProperties = {
    OracleDatabase.withDynSession {
      val shelterTypePropertyId = sql"select p.id from property p where p.public_id = 'katos'".as[Long].first
      val accessibilityPropertyId = sql"select p.id from property p where p.public_id = 'esteettomyys_liikuntarajoitteiselle'".as[Long].first
      val administratorPropertyId = sql"select p.id from property p where p.public_id = 'tietojen_yllapitaja'".as[Long].first
      val busStopTypePropertyId = sql"select p.id from property p where p.public_id = 'pysakin_tyyppi'".as[Long].first
      val busStopLiViPropertyId = sql"select p.id from property p where p.public_id = 'yllapitajan_koodi'".as[Long].first
      val busStopAssetTypeId = sql"select id from asset_type where name = 'Bussipysäkit'".as[Long].first
      PropertyWrapper(shelterTypePropertyId, accessibilityPropertyId, administratorPropertyId,
                      busStopAssetTypeId, busStopTypePropertyId, busStopLiViPropertyId)
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
      insertTextPropertyData(typeProps.busStopLiViPropertyId, assetId, "OTHJ%d".format(busStop.busStopId.get))
    }
  }

  def adjustToNewDigitization(vvhHost: String) = {
    val vvhClient = new VVHClient(vvhHost)
    val municipalities = OracleDatabase.withDynSession { Queries.getMunicipalities }
    val processedLinkIds = mutable.Set[Long]()

    withDynTransaction {
      municipalities.foreach { municipalityCode =>
        val startTime = DateTime.now()

        println(s"*** Fetching from VVH with municipality: $municipalityCode")

        val flippedLinks = vvhClient.roadLinkData.fetchByMunicipality(municipalityCode)
          .filter(isHereFlipped)
          .filterNot(link => processedLinkIds.contains(link.linkId))

        var updatedCount = MassQuery.withIds(flippedLinks.map(_.linkId).toSet) { idTableName =>
          sqlu"""
            update lrm_position pos
            set pos.side_code = 5 - pos.side_code
            where exists(select * from #$idTableName i where i.id = pos.link_id)
            and pos.side_code in (2, 3)
          """.first +
          sqlu"""
            update traffic_direction td
            set td.traffic_direction = 7 - td.traffic_direction
            where exists(select id from #$idTableName i where i.id = td.link_id)
            and td.traffic_direction in (3, 4)
          """.first +
          sqlu"""
            update asset a
            set a.bearing = mod((a.bearing + 180), 360)
            where a.id in (select al.asset_id from asset_link al
                           join lrm_position pos on al.position_id = pos.id
                           join #$idTableName i on i.id = pos.link_id)
            and a.bearing is not null
            and a.asset_type_id in (10, 240)
          """.first
        }

        flippedLinks.foreach { link =>
          val length = GeometryUtils.geometryLength(link.geometry)

          updatedCount += sqlu"""
              update lrm_position
              set end_measure = greatest(0, ${length} - COALESCE(start_measure, 0)),
                  start_measure = greatest(0, ${length} - COALESCE(end_measure, start_measure, 0))
              where link_id = ${link.linkId}
            """.first
        }

        processedLinkIds ++= flippedLinks.map(_.linkId)

        println(s"*** Made $updatedCount updates in ${humanReadableDurationSince(startTime)}")
      }
    }
  }

  private def isHereFlipped(roadLink: VVHRoadlink) = {
    val NotFlipped = 0
    val Flipped = 1
    roadLink.attributes.getOrElse("MTKHEREFLIP", NotFlipped).asInstanceOf[BigInt] == Flipped
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
    val dao = new OracleLinearAssetDao(null, null)

    withDynTransaction {
      val speedLimitLinks = sql"""
            select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, pos.link_source
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
          """.as[(Long, Long, Int, Option[Int], Double, Double, Int)].list

      speedLimitLinks.foreach { speedLimitLink =>
        val (id, linkId, sideCode, value, startMeasure, endMeasure, linkSource) = speedLimitLink
        dao.forceCreateLinearAsset(s"split_speedlimit_$id", 20, linkId, Measures(startMeasure, endMeasure), SideCode(sideCode), value, (id, value) => dao.insertEnumeratedValue(id, "rajoitus", value), None, None, None, None, LinkGeomSource.apply(linkSource))
      }
      println(s"created ${speedLimitLinks.length} new single link speed limits")

      val speedLimitsToFloat = speedLimitLinks.map(_._1).toSet
      dao.floatLinearAssets(speedLimitsToFloat)
      println(s"removed ${speedLimitsToFloat.size} multilink speed limits")
    }
  }

  private def splitLinearAssets(typeId: Int, chunkStart: Long, chunkEnd: Long) = {
    val dao = new OracleLinearAssetDao(null, null)

    withDynTransaction {
      val linearAssetLinks = sql"""
            select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, n.value, pos.link_source
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            left join number_property_value n on a.id = n.asset_id
            where a.asset_type_id = $typeId
            and floating = 0
            and (select count(*) from asset_link where asset_id = a.id) > 1
            and a.id between $chunkStart and $chunkEnd
          """.as[(Long, Long, Int, Double, Double, Option[Int], Int)].list

      linearAssetLinks.foreach { case (id, linkId, sideCode, startMeasure, endMeasure, value, linkSource) =>
        dao.forceCreateLinearAsset(s"split_linearasset_$id", typeId, linkId, Measures(startMeasure, endMeasure), SideCode(sideCode), value, (id, value) => dao.insertValue(id, "mittarajoitus", value), None, None, None, None, linkSource = LinkGeomSource.apply(linkSource))
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

  private def expireSplitLinearAssetsWithoutLinkId(typeId: Int, chunkStart: Long, chunkEnd: Long) = {
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
            and lrm.link_id is null
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

def insertNumberPropertyData(propertyId: Long, assetId: Long, value:Int) {
    sqlu"""
      insert into number_property_value(id, property_id, asset_id, value)
      values (primary_key_seq.nextval, $propertyId, $assetId, $value)
    """.execute
  }

  def updateNumberPropertyData(propertyId: Long, assetId: Long, value: Int) {
    sqlu"""
       update number_property_value set value = $value
       where asset_id = $assetId and property_id = $propertyId
    """.execute
  }

  def updateFloating(id: Long, floating: Boolean) {
    sqlu"""
         update asset set floating = $floating where id = $id
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

  def getPropertyTypeByPublicId(publicId : String): Long ={
    sql"select p.id from property p where p.public_id = $publicId".as[Long].first
  }

  def getExistingLiviIds(): Seq[String] ={
    withDynSession {
      sql"""
        select  tv.value_fi
        from property p
        inner join text_property_value tv on tv.property_id = p.id
        where public_id = 'yllapitajan_koodi'
      """.as[String].list
    }
  }

  def getFloatingAssetsWithNumberPropertyValue(assetTypeId: Long, publicId: String, municipality: Int) : Seq[(Long, Long, Point, Double, Option[Int])] = {
    implicit val getPoint = GetResult(r => bytesToPoint(r.nextBytes))
    sql"""
      select a.id, lrm.link_id, geometry, lrm.start_measure, np.value
      from
      asset a
      join asset_link al on al.asset_id = a.id
      join lrm_position lrm on al.position_id  = lrm.id
      join property p on a.asset_type_id = p.asset_type_id and p.public_id = $publicId
      left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
      where a.asset_type_id = $assetTypeId and a.floating = 1 and a.municipality_code = $municipality
      """.as[(Long, Long, Point, Double, Option[Int])].list
  }

  def getNonFloatingAssetsWithNumberPropertyValue(assetTypeId: Long, publicId: String, municipality: Int): Seq[(Long, Long, Option[Int])] ={
    sql"""
      select a.id, lrm.link_id, np.value
      from
      asset a
      join asset_link al on al.asset_id = a.id
      join lrm_position lrm on al.position_id  = lrm.id
      join property p on a.asset_type_id = p.asset_type_id and p.public_id = $publicId
      left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
      where a.asset_type_id = $assetTypeId and a.floating = 0 and a.municipality_code = $municipality
      """.as[(Long, Long, Option[Int])].list
  }

  /**
    * Finds closest road link from classes 6-8 for an obstacle and updates the location and clears the floating
    * if one is found according to the rules:
    * - at most 10 meters away
    * - at most .5 meters away and no other candidate locations within 5 times the distance if there are multiple
    *
    * @param obstacle A floating obstacle to update
    * @param roadLinkService RoadLinkService to use (reusing for speed)
    * @return
    */
  def updateObstacleToRoadLink(obstacle : Obstacle, roadLinkService: RoadLinkService) : Obstacle = {
    def recalculateObstaclePosition(obstacle: Obstacle, roadlink: RoadLink) = {
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(obstacle.lon, obstacle.lat, 0), roadlink.geometry)
      val point = GeometryUtils.calculatePointFromLinearReference(roadlink.geometry, mValue).get
      obstacle.copy(mValue = mValue, linkId = roadlink.linkId, lon = point.x, lat = point.y,
        municipalityCode = roadlink.municipalityCode, modifiedBy = Some("automatic_correction"),
        modifiedAt = Some(DateTime.now()), floating = false)
    }
    //FunctionalClass 7 equal to FeatureClass TractorRoad
    //FunctionalClass 6 equal to FeatureClass DrivePath
    //FunctionalClass 8 equal to FeatureClass CycleOrPedestrianPath
    val allowedFunctionalClasses = Set(6,7,8)

    val diagonal = Vector3d(10, 10, 0)

    val obstaclePoint = Point(obstacle.lon, obstacle.lat, 0)
    //Get from vvh service all roadlinks in 10 meters rectangle arround the obstacle and filter
    val (roadLinks, otherLinks) = roadLinkService.getRoadLinksFromVVH(BoundingRectangle(obstaclePoint - diagonal, obstaclePoint + diagonal)).
      filter(rl => GeometryUtils.minimumDistance(obstaclePoint, rl.geometry) <= 10.0).
      partition(rl => allowedFunctionalClasses.contains(rl.functionalClass))

    roadLinks.length match {
      case 0 =>
        println("! No road link found for obstacle id=" + obstacle.id)
        obstacle // Let it float, then
      case 1 => recalculateObstaclePosition(obstacle, roadLinks.head)
      //If exists multiple road link get the closest with less than 0.5 meters distance
      case _ =>
        val roadLinksByDistance = roadLinks.map(rl => GeometryUtils.minimumDistance(obstaclePoint, rl.geometry) -> rl).sortBy(_._1)
        val (rl1, rl2) = (roadLinksByDistance.head, roadLinksByDistance.tail.head)
        // Has to be up to 50 cm away and the second closest at least 5 times this distance away
        if (rl1._1 <= .5 && rl1._1 * 5 <= rl2._1) {
          println("* Accepted closest for obstacle id=" + obstacle.id + ": road links and distances are " +
            "%d -> %2.3f m, %d -> %2.3f m".format(rl1._2.linkId, rl1._1, rl2._2.linkId, rl2._1))
          recalculateObstaclePosition(obstacle, rl1._2)
        } else {
          val closestOther = otherLinks.map(rl => GeometryUtils.minimumDistance(obstaclePoint, rl.geometry)).sorted.headOption
          val rl3 = roadLinksByDistance.tail.tail.headOption.map(_._1)
          if (rl1._1 <= .5 && rl2._1 <= .5 && closestOther.getOrElse(10.0) > 0.5 && rl3.getOrElse(10.0) > 0.5) {
            println("* Accepted closest in joining segments for obstacle id=" + obstacle.id + ": road links and distances are " +
              "%d -> %2.3f m, %d -> %2.3f m, next links (1-5): %2.3f m, (6-8): %2.3f m".format(
                rl1._2.linkId, rl1._1, rl2._2.linkId, rl2._1, closestOther.getOrElse(10.0), rl3.getOrElse(10.0)))
            recalculateObstaclePosition(obstacle, rl1._2)
          } else {
            println("! Rejected; multiple candidates for obstacle id=" + obstacle.id + ": road links and distances are " +
              "%d -> %2.3f m, %d -> %2.3f m".format(rl1._2.linkId, rl1._1, rl2._2.linkId, rl2._1))
            obstacle
          }
        }
    }

  }

  def createFloatingObstacle(incomingObstacle: IncomingObstacle) = {
    def getPropertyId: Long = {
      StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("esterakennelma").first
    }
    val id = OracleObstacleDao.create(incomingObstacle, 0.0, "test_data", 749, 0, NormalLinkInterface)
    sqlu"""update asset set floating = 1 where id = $id""".execute
    id
  }

  def importRoadAddressData(conversionDatabase: DatabaseDef, vvhClient: VVHClient) = {
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
            from vvh_tieosoite_nyky""".as[(Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, Long, String, Option[String], String, String, Long)].list
    }

    print(s"${DateTime.now()} - ")
    println("Read %d rows from conversion database".format(roads.size))
    val lrmList = roads.map(r => (r._16, r._1, r._2.toDouble, r._3.toDouble)).groupBy(_._2) // linkId -> (id, linkId, startM, endM)
    val addressList = roads.map(r => (r._16, (r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13, r._14, r._15))).toMap

    print(s"${DateTime.now()} - ")
    println("Total of %d link ids".format(lrmList.keys.size))
    val linkIdGroups = lrmList.keys.toSet.grouped(500) // Mapping LinkId -> Id

    val linkLengths = linkIdGroups.flatMap (
      linkIds => vvhClient.roadLinkData.fetchByLinkIds(linkIds).map(roadLink => roadLink.linkId -> GeometryUtils.geometryLength(roadLink.geometry))
    ).toMap
    print(s"${DateTime.now()} - ")
    println("Read %d road links from vvh".format(linkLengths.size))

    val unFilteredLrmPositions = linkLengths.flatMap {
      case (linkId, length) => cutter(filler(lrmList.getOrElse(linkId, List()), length), length)
    }
    val lrmPositions = unFilteredLrmPositions.filterNot(x => x._3 == x._4)

    print(s"${DateTime.now()} - ")
    println("%d zero length segments removed".format(unFilteredLrmPositions.size - lrmPositions.size))

    OracleDatabase.withDynTransaction {
      sqlu"""ALTER TABLE ROAD_ADDRESS DISABLE ALL TRIGGERS""".execute
      sqlu"""DELETE FROM ROAD_ADDRESS""".execute
      sqlu"""DELETE FROM LRM_POSITION WHERE NOT EXISTS (SELECT POSITION_ID FROM ASSET_LINK WHERE POSITION_ID=LRM_POSITION.ID)""".execute
      println(s"${DateTime.now()} - Old address data removed")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure) values (?, ?, ?, ?, ?)")
      val addressPS = dynamicSession.prepareStatement("insert into ROAD_ADDRESS (id, lrm_position_id, road_number, road_part_number, " +
        "track_code, ely, road_type, discontinuity, START_ADDR_M, END_ADDR_M, start_date, end_date, created_by, " +
        "created_date) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), TO_DATE(?, 'YYYY-MM-DD'), ?, TO_DATE(?, 'YYYY-MM-DD'))")
      lrmPositions.foreach { case (id, linkId, startM, endM) =>
        val lrmId = Sequences.nextLrmPositionPrimaryKeySeqValue
        val addressId = Sequences.nextViitePrimaryKeySeqValue
        val address = addressList.get(id).head
        val (startAddrM, endAddrM, sideCode) = address._7 < address._8 match {
          case true => (address._7, address._8, SideCode.TowardsDigitizing.value)
          case false => (address._8, address._7, SideCode.AgainstDigitizing.value)
        }
        lrmPositionPS.setLong(1, lrmId)
        lrmPositionPS.setLong(2, linkId)
        lrmPositionPS.setLong(3, sideCode)
        lrmPositionPS.setDouble(4, startM)
        lrmPositionPS.setDouble(5, endM)
        lrmPositionPS.addBatch()
        addressPS.setLong(1, addressId)
        addressPS.setLong(2, lrmId)
        addressPS.setLong(3, address._1)
        addressPS.setLong(4, address._2)
        addressPS.setLong(5, address._3)
        addressPS.setLong(6, address._4)
        addressPS.setLong(7, address._5)
        addressPS.setLong(8, address._6)
        addressPS.setLong(9, startAddrM)
        addressPS.setLong(10, endAddrM)
        addressPS.setString(11, address._9)
        addressPS.setString(12, address._10.getOrElse(""))
        addressPS.setString(13, address._11)
        addressPS.setString(14, address._12)
        addressPS.addBatch()
      }
      lrmPositionPS.executeBatch()
      println(s"${DateTime.now()} - LRM Positions saved")
      addressPS.executeBatch()
      println(s"${DateTime.now()} - Road addresses saved")
      lrmPositionPS.close()
      addressPS.close()
    }

    println(s"${DateTime.now()} - Updating calibration point information")

    OracleDatabase.withDynTransaction {
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

  /**
    * Get address information to mass transit stop assets from VVH road link (DROTH-221).
    *
    * @param vvhRestApiEndPoint
    */
  def getMassTransitStopAddressesFromVVH(vvhRestApiEndPoint: String) = {
    val vvhClient = new VVHClient(vvhRestApiEndPoint)
    withDynTransaction {
      val idAddressFi = sql"""select p.id from property p where p.public_id = 'osoite_suomeksi'""".as[Int].list.head
      val idAddressSe = sql"""select p.id from property p where p.public_id = 'osoite_ruotsiksi'""".as[Int].list.head
      println("Phase 1 out of 2. Getting stops with both names missing")
      val municipalities = getMTStopsMunicipalitycodeBothMissing(idAddressFi, idAddressSe)
      municipalities.foreach { municipalityCode =>
        val startTime = DateTime.now()
        println(s"*** Processing municipality: $municipalityCode")
        val listOfStops = getMTStopsWOAddresses(municipalityCode, idAddressFi, idAddressSe)
        val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(listOfStops.map(_._2).toSet)
        listOfStops.foreach { stops =>
          roadLinks.foreach { rlinks =>
            if (rlinks.linkId == stops._2) {
              val finRoadName = rlinks.attributes.get("ROADNAME_FI").getOrElse("none").toString
              val seRoadName = rlinks.attributes.get("ROADNAME_SE").getOrElse("none").toString
              if (finRoadName != null && finRoadName!="none")
              {
                createTextPropertyValue(stops._1, idAddressFi, finRoadName)
              }
              if ((seRoadName != null && seRoadName!="none"))
              {
                createTextPropertyValue(stops._1, idAddressSe, seRoadName)
              }
            }
          }
        }
      }
      println("Phase 2 out of 2. Getting stops with one address missing.")
      println("Adding other languages street address name to db only if address in db corresponds to VVH address")
      val municipalitiesone=getMTStopsMunicipalitycodeOneMissing(idAddressFi, idAddressSe)
      municipalitiesone.foreach { municipalityCode =>
        println(s"*** Processing municipality: $municipalityCode")
        val listOfStops = getMTStopsMissingOneAddress(municipalityCode, idAddressFi, idAddressSe)
        val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(listOfStops.map(_._2).toSet)
        val finstops=getFinnishStopAddressRSe(municipalityCode, idAddressFi, idAddressSe)
        val swedishstops= getSwedishStopAddressRFi(municipalityCode, idAddressFi, idAddressSe)
        listOfStops.foreach { stops =>   //stop_1:asset_id, stop_2:link_id
          roadLinks.foreach { rlinks =>
            if (rlinks.linkId == stops._2) //stop's link if found from vvh list
              {
                var sweAddressAdded=false
                finstops.foreach{finstop=> //finstop  _1:asset_id, _2:address,_3:link-id
                {
                if (finstop._1==stops._1 && finstop._3==rlinks.linkId )
                  {
                    val swedishaddress=rlinks.attributes.getOrElse("ROADNAME_SE","none").toString
                    if (swedishaddress!="none" && swedishaddress!=null && rlinks.attributes.getOrElse("ROADNAME_FI","null").toString.toUpperCase()==finstop._2.toUpperCase())
                     {
                       createTextPropertyValue(stops._1, idAddressSe, swedishaddress)
                       sweAddressAdded=true
                     }
                  }
                }}
                  if (!sweAddressAdded)
                  swedishstops.foreach{swestop=> //swestop  _1:asset_id, _2:address,_3:link-id
                    if (swestop._1==stops._1 && swestop._3==rlinks.linkId)
                    {
                      val finAddress=rlinks.attributes.getOrElse("ROADNAME_FI","none").toString
                      if (finAddress!="none" && finAddress!=null && rlinks.attributes.getOrElse("ROADNAME_SE","null").toString.toUpperCase()==swestop._2.toUpperCase())
                      {
                        createTextPropertyValue(stops._1, idAddressFi, finAddress)
                      }
                    }

                  }
                }
              }
          }
        }
      }
    }

    /**
      * Gets municipalitycodes of stops which have updateable masstransitstops
      * returns list of int
      */
    def getMTStopsMunicipalitycodeBothMissing(idAddressFi: Int, idAddressSe: Int) =
    {

      sql"""
              Select distinct MUNICIPALITY_CODE
                            From Asset
                   WHERE
                   Asset_Type_ID=10
                    AND
                    (
                   (ID NOT IN (SELECT ASSET_ID FROM Text_property_value WHERE PROPERTY_ID = $idAddressSe OR PROPERTY_ID = $idAddressFi ))
                    )
               ORDER BY MUNICIPALITY_CODE DESC""".as[(Int)].list
    }

  /**
    * Retrives distinct int list filled with  municipalitycodes of mass transit stops which have only finnish OR swedish address
    *
    * @return
    */

  def getMTStopsMunicipalitycodeOneMissing(idAddressFi: Int, idAddressSe: Int) =
  {

    sql"""
                    Select distinct a.MUNICIPALITY_CODE
                    From Asset a,  Text_property_value fiv
                    WHERE
                     a.Asset_Type_ID=10
                      AND
                      ((fiv.PROPERTY_ID = $idAddressSe  AND   (fiv.Asset_ID NOT IN (Select Asset_ID From Text_property_value Where PROPERTY_ID = $idAddressFi)  AND a.ID=fiv.ASSET_ID))
                      OR
                      (fiv.PROPERTY_ID = $idAddressFi  AND   (fiv.Asset_ID NOT IN (Select Asset_ID From Text_property_value Where PROPERTY_ID = $idAddressSe)  AND a.ID=fiv.ASSET_ID)))
               ORDER BY MUNICIPALITY_CODE DESC""".as[(Int)].list
  }

      /**
      * Retrives Masstransitstops which do not have BOTH finnish AND swedish name (street name with out numbers)
      * Returns list of |Asset ID, Link-ID, Finnish Street Name (w/o number), Swedish Street Name (w/o number), finnish txt_property id, swedish txt_property id|
      */
    def getMTStopsWOAddresses(municipalityNumber: Long, idAddressFi: Int, idAddressSe: Int) =
    {

      sql"""
         Select a.id, l.link_ID
                      From Asset a
                      join ASSET_LINK lt on (lt.asset_id=a.id) join LRM_POSITION l on (l.id=lt.position_id)
                       WHERE
                       asset_type_id = 10 and
                       not exists (select 1 from Text_property_value fiv where a.id = fiv.asset_id and fiv.property_id=$idAddressFi)
                       and
                       not exists (select 1 from Text_property_value sev where a.id = sev.asset_id and sev.property_id=$idAddressSe)
                       AND a.MUNICIPALITY_CODE=$municipalityNumber""".as[(Long, Long)].list
    }

  /**
    * Retrives Masstransitstops which do not have either finnish or swedish name (street name with out numbers)
    * Returns list of |Asset ID, Link-ID, Finnish Street Name (w/o number), Swedish Street Name (w/o number), finnish txt_property id, swedish txt_property id|
    */
  def getMTStopsMissingOneAddress(municipalityNumber: Long, idAddressFi: Int, idAddressSe: Int) = {

    sql"""
       			   		 Select distinct a.id, l.link_ID
                     From Asset a
       			  join ASSET_LINK lt on (lt.asset_id = a.ID) join LRM_POSITION l on (l.id=lt.position_id) join Text_property_value fiv on (a.id=fiv.ASSET_ID)
                     WHERE
                     a.Asset_Type_ID=10 AND a.MUNICIPALITY_CODE=$municipalityNumber
                      AND ((fiv.PROPERTY_ID = $idAddressSe)  AND   (not exists (select 1 from Text_property_value fiv where a.id = fiv.asset_id and fiv.property_id=$idAddressFi))
                      OR
                      (fiv.PROPERTY_ID = $idAddressFi  AND  ( not exists (select 1 from Text_property_value sev where a.id = sev.asset_id and sev.property_id=$idAddressSe))))
                      ORDER BY a.id""".as[(Long, Long)].list
  }
/**
  * Gets masstransitstop asset_id, street name and link-id for stops that have ONLY swedish address
*/
  def getSwedishStopAddressRFi(municipalityNumber: Int, idAddressFi: Int, idAddressSe: Int) =
  {
    sql"""
       			    Select distinct a.id, se.Value_FI, l.link_ID
                       From Asset a
       			           join ASSET_LINK lt on (lt.asset_id = a.ID) join LRM_POSITION l on (l.id=lt.position_id) join Text_property_value se on (a.id=se.ASSET_ID)
                       WHERE
                       a.Asset_Type_ID=10 AND  a.MUNICIPALITY_CODE =$municipalityNumber AND se.PROPERTY_ID = $idAddressSe
                       AND (a.ID NOT IN (SELECT ASSET_ID FROM Text_property_value WHERE PROPERTY_ID = $idAddressFi))
         """.as[(Long, String,Long)].list
    //asset_id,stop's street name,link-id
  }

  /**
    * Gets masstransitstop asset_id, street name and link-id for stops that have ONLY finnish address
    */
  def getFinnishStopAddressRSe(municipalityNumber: Int, idAddressFi: Int, idAddressSe: Int) =
    {
      sql"""
                  Select distinct a.id, fiv.Value_FI, l.link_ID
                  From Asset a
                  join ASSET_LINK lt on (lt.asset_id = a.ID) join LRM_POSITION l on (l.id=lt.position_id) join Text_property_value fiv on (a.id=fiv.ASSET_ID)
                  WHERE
                  a.Asset_Type_ID=10 AND  a.MUNICIPALITY_CODE=$municipalityNumber AND fiv.PROPERTY_ID = $idAddressFi
                  AND (a.ID NOT IN (SELECT ASSET_ID FROM Text_property_value WHERE PROPERTY_ID = $idAddressSe))
         """.as[(Long, String,Long)].list
      //asset_id,stop's street name,link-id
    }

  /**
    * Adds text property to TEXT_PROPERTY_VALUE table. Created for getMassTransitStopAddressesFromVVH
    * to create address information for missing mass transit stops
    *
    * @param assetId
    * @param propertyVal
    * @param vname
    */
    def createTextPropertyValue(assetId: Long, propertyVal: Int, vname : String) = {
      sqlu"""
        INSERT INTO TEXT_PROPERTY_VALUE(ID,ASSET_ID,PROPERTY_ID,VALUE_FI,CREATED_BY)
        VALUES(primary_key_seq.nextval,$assetId,$propertyVal,$vname,'vvh_generated')
      """.execute
    }

    /**
    * Adds text property to TEXT_PROPERTY_VALUE table.
    *
    * @param assetId
    * @param propertyVal
    * @param vname
    */
    def createTextPropertyValue(assetId: Long, propertyVal: Long, vname : String, modifiedBy: String) = {
      sqlu"""
          INSERT INTO TEXT_PROPERTY_VALUE(ID,ASSET_ID,PROPERTY_ID,VALUE_FI,CREATED_BY)
          VALUES(primary_key_seq.nextval,$assetId,$propertyVal,$vname,$modifiedBy)
        """.execute
    }

    /**
      * Add or update text property to TEXT_PROPERTY_VALUE table.
      *
      * @param assetId
      * @param propertyVal
      * @param vname
      */
    def createOrUpdateTextPropertyValue(assetId: Long, propertyVal: Long, vname : String, modifiedBy: String) = {
      val notExist = sql"""select id from text_property_value where asset_id = $assetId and property_id = $propertyVal""".as[Long].firstOption.isEmpty
      if(notExist){
        sqlu"""
          INSERT INTO TEXT_PROPERTY_VALUE(ID,ASSET_ID,PROPERTY_ID,VALUE_FI,CREATED_BY)
          VALUES(primary_key_seq.nextval,$assetId,$propertyVal,$vname,$modifiedBy)
        """.execute
      }else{
        sqlu"update text_property_value set value_fi = $vname, modified_date = sysdate, modified_by = $modifiedBy where asset_id = $assetId and property_id = $propertyVal".execute
      }
    }

  def insertNewAsset(typeId: Int, linkId: Long, startMeasure: Double, endMeasure: Double, sideCode: Int, value: Int, createdBy: String) {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val propertyId = sql"""select id from property where public_id = 'mittarajoitus'""".as[Long].first

    sqlu"""
         insert into asset(id, asset_type_id, created_by, created_date)
         values ($assetId, $typeId, $createdBy, sysdate)
    """.execute

    sqlu"""
         insert into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date)
         values ($lrmPositionId, $startMeasure, $endMeasure, $linkId, $sideCode, SYSDATE)
      """.execute

    sqlu"""
         insert into asset_link(asset_id, position_id)
         values ($assetId, $lrmPositionId)
      """.execute

    insertNumberPropertyData(propertyId, assetId, value)
  }

  def getAllLinkIdByAsset(typeId: Long, linkId: Seq[Long]) = {
    MassQuery.withIds(linkId.toSet) { idTableName =>
      sql"""
            select pos.LINK_ID, prop.VALUE, a.id
            from ASSET a
            join ASSET_LINK al on a.id = al.asset_id
            join LRM_POSITION pos on al.position_id = pos.id
            join number_property_value prop on prop.asset_id = a.id
            join #$idTableName i on i.id = pos.link_id
            where a.asset_type_id = $typeId
            and (a.valid_to >= sysdate or a.valid_to is null)""".as[(Long, Int, Long)].list
    }
  }
  def getAssetsByLinkIds(typeId: Long, linkId: Seq[Long], includeExpire: Boolean) = {
    val filter = if (includeExpire) "" else "and (a.valid_to >= sysdate or a.valid_to is null)"
    MassQuery.withIds(linkId.toSet) { idTableName =>
      sql"""
            select a.id, pos.link_id, pos.start_measure, pos.end_measure
            from ASSET a
            join ASSET_LINK al on a.id = al.asset_id
            join LRM_POSITION pos on al.position_id = pos.id
            join #$idTableName i on i.id = pos.link_id
            where a.asset_type_id = $typeId
            #$filter""".as[(Long, Long, Long, Long)].list
    }
  }
}

