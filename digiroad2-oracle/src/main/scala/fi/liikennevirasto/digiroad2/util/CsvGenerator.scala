package fi.liikennevirasto.digiroad2.util

import java.io.{FileWriter, BufferedWriter, File}

import fi.liikennevirasto.digiroad2.linearasset.{VVHRoadLinkWithProperties, ProhibitionValue, Prohibitions}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2._
import org.joda.time.{Seconds, DateTime}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession

class CsvGenerator(vvhServiceHost: String) {
  val roadLinkService = new VVHRoadLinkService(new VVHClient(vvhServiceHost), new DummyEventBus)
  val linearAssetDao = new OracleLinearAssetDao(roadLinkService)

  val Source = 1
  val Destination = 3

  def generateDroppedManoeuvres() = {
    val manoeuvres = OracleDatabase.withDynSession {
      sql"""
           select m.id, m.ADDITIONAL_INFO, m.TYPE, me.MML_ID, me.ROAD_LINK_ID, me.ELEMENT_TYPE
           from manoeuvre m
           join MANOEUVRE_ELEMENT me on me.MANOEUVRE_ID = m.id
        """.as[(Long, Option[String], Int, Long, Long, Int)].list
    }

    val groupedManoeuvres = manoeuvres.groupBy(_._1)
    val roadLinksWithProperties = roadLinkService.getRoadLinksFromVVH(manoeuvres.map(_._4).toSet)
    val roadLinksByMmlId = roadLinksWithProperties.groupBy(_.mmlId).mapValues(_.head)
    val roadLinkMmlIds = roadLinksWithProperties.map(_.mmlId).toSet
    val (manoeuvresWithIntactLinks, manoeuvresWithDroppedLinks) = groupedManoeuvres.partition { case (id, rows) => rows.forall(row => roadLinkMmlIds.contains(row._4)) }
    val (okManoeuvres, manoeuvresWithCycleOrPedestrianLink) = manoeuvresWithIntactLinks.partition { case (id, rows) => rows.forall { row => roadLinksByMmlId(row._4).isCarTrafficRoad }}
    val (_, detachedManoeuvres) = okManoeuvres.partition { case (id, rows) =>
      val source = rows.find(_._6 == Source).get
      val adjacents: Seq[VVHRoadLinkWithProperties] = roadLinkService.getAdjacent(source._4)
      val destination = rows.find(_._6 == Destination).get
      adjacents.exists(_.mmlId == destination._4)
    }
    val droppedManoeuvres = manoeuvresWithDroppedLinks ++ manoeuvresWithCycleOrPedestrianLink ++ detachedManoeuvres
    val droppedManoeuvresWithExceptions =
      droppedManoeuvres.mapValues { rows =>
        val exceptions = OracleDatabase.withDynSession { sql"""select exception_type from manoeuvre_exceptions where manoeuvre_id = ${rows(0)._1}""".as[Int].list }
        rows.map { x => (x._1, x._2, x._3, x._4, x._5, x._6, exceptions) }
      }
    exportManoeuvreCsv("dropped_manoeuvres", droppedManoeuvresWithExceptions)
  }

  def getIdsAndMmlIdsByMunicipality(municipality: Int): Seq[(Long, Long)] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      sql"""
        select dr1_id, mml_id
          from tielinkki_ctas
          where kunta_nro = $municipality
        """.as[(Long, Long)].list
    }
  }

  def generateDroppedNumericalLimits(): Unit = {
    val startTime = DateTime.now()
    val assetNames = Map(
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
      180 -> "speed_limits_during_winter",
      160 -> "mass_transit_lanes")

    assetNames.foreach { case(assetTypeId, assetName) =>
      generateCsvForDroppedAssets(assetTypeId, assetName, startTime)
    }
  }

  def generateDroppedProhibitions(assetTypeId: Int, csvName: String): Unit = {
    val startTime = DateTime.now()
    def elapsedTime = Seconds.secondsBetween(startTime, DateTime.now()).getSeconds

    val limits = OracleDatabase.withDynSession {
      sql"""
           select pos.MML_ID, pos.road_link_id, pos.start_measure, pos.end_measure, a.floating
           from asset a
           join ASSET_LINK al on a.id = al.asset_id
           join LRM_POSITION pos on al.position_id = pos.id
           where a.asset_type_id = $assetTypeId
           and (valid_to is null or valid_to >= sysdate)
         """.as[(Long, Long, Double, Double, Boolean)].list
    }
    println(s"*** fetched prohibitions of type ID $assetTypeId from DB in $elapsedTime seconds")

    val existingMmlIds = roadLinkService.fetchVVHRoadlinks(limits.map(_._1).toSet).map(_.mmlId)
    println(s"*** fetched all road links from VVH in $elapsedTime seconds")

    val nonExistingLimits = limits.filter { limit => !existingMmlIds.contains(limit._1) }
    println(s"*** calculated dropped links in $elapsedTime seconds")

    val floatingLimits = limits.filter(_._5)
    val droppedMmlIds = (floatingLimits ++ nonExistingLimits).map(_._1)

    val droppedProhibitions =  OracleDatabase.withDynTransaction {
      linearAssetDao.fetchProhibitionsByMmlIds(assetTypeId, droppedMmlIds, includeFloating = true)
    }

    val prohibitionLines = droppedProhibitions.map { droppedProhibition =>
      droppedProhibition.value.get match {
        case Prohibitions(prohibitionValues) =>
          prohibitionValues.map { prohibitionValue =>
            val value = generateValueString(prohibitionValue)
            (droppedProhibition.mmlId, 0l, droppedProhibition.startMeasure, droppedProhibition.endMeasure, value, assetTypeId, false)
          }
      }
    }
    exportCsv(csvName, prohibitionLines.flatten)

    println(s"*** exported CSV file $csvName in $elapsedTime seconds")
  }

  def generateValueString(prohibitionValue: ProhibitionValue): String = {
    val prohibitionType = Map(
      3 -> "Ajoneuvo",
      2 -> "Moottoriajoneuvo",
      23 -> "Läpiajo",
      12 -> "Jalankulku",
      11 -> "Polkupyörä",
      26 -> "Ratsastus",
      10 -> "Mopo",
      9 -> "Moottoripyörä",
      27 -> "Moottorikelkka",
      5 -> "Linja-auto",
      8 -> "Taksi",
      7 -> "Henkilöauto",
      6 -> "Pakettiauto",
      4 -> "Kuorma-auto",
      15 -> "Matkailuajoneuvo",
      19 -> "Sotilasajoneuvo",
      13 -> "Ajoneuvoyhdistelmä",
      14 -> "Traktori tai maatalousajoneuvo",
      21 -> "Huoltoajo",
      22 -> "Tontille ajo",
      24 -> "Ryhmän A vaarallisten aineiden kuljetus",
      25 -> "Ryhmän B vaarallisten aineiden kuljetus"
    )

    val daysMap = Map(
      2 -> "Ma - Pe",
      7 -> "La",
      1 -> "Su"
    )

    val exceptions = prohibitionValue.exceptions.toSeq match {
      case Nil => ""
      case exceptions => "Poikkeukset: " + exceptions.map { exceptionCode => prohibitionType.getOrElse(exceptionCode, exceptionCode) }.mkString(", ")
    }

    val validityPeriods = prohibitionValue.validityPeriods.toSeq match {
      case Nil => ""
      case periods => "Voimassa: " + periods.map { validityPeriod => s"${daysMap(validityPeriod.days.value)} ${validityPeriod.startHour} - ${validityPeriod.endHour}" }.mkString(", ")
    }

    prohibitionType.getOrElse(prohibitionValue.typeId, prohibitionValue.typeId) + " " + exceptions + " " + validityPeriods
  }

  private def generateCsvForDroppedAssets(assetTypeId: Int,
                                          assetName: String,
                                          startTime: DateTime) = {
    val runtime = Runtime.getRuntime()
    val limits = OracleDatabase.withDynSession {
      sql"""
           select pos.MML_ID, pos.road_link_id, pos.start_measure, pos.end_measure, s.value, a.asset_type_id, a.floating
           from asset a
           join ASSET_LINK al on a.id = al.asset_id
           join LRM_POSITION pos on al.position_id = pos.id
           left join number_property_value s on s.asset_id = a.id
           where a.asset_type_id in ($assetTypeId)
           and (valid_to is null or valid_to >= sysdate)
         """.as[(Long, Long, Double, Double, Int, Int, Boolean)].list
    }
    println("*** fetched all " + assetName + " from DB " + Seconds.secondsBetween(startTime, DateTime.now()).getSeconds)
    logMemoryStatistics(runtime)

    def mmlIdFromFeature(attributes: Map[String, Any], geometry: List[List[Double]]) = {
      attributes("MTKID").asInstanceOf[BigInt].longValue()
    }
    val assetMmlIds = limits.map(_._1).toSet
    val existingMmlIds = roadLinkService.fetchVVHRoadlinks(assetMmlIds, Some("MTKID"), false, mmlIdFromFeature).toSet
    println("*** fetched associated road links from VVH " + Seconds.secondsBetween(startTime, DateTime.now()).getSeconds)
    logMemoryStatistics(runtime)

    val nonExistingLimits = limits.filter { limit => !existingMmlIds.contains(limit._1) }
    println("*** calculated dropped links " + Seconds.secondsBetween(startTime, DateTime.now()).getSeconds)
    logMemoryStatistics(runtime)

    val floatingLimits = limits.filter(_._7)
    exportCsv(assetName, nonExistingLimits ++ floatingLimits)
    println("*** exported CSV files " + Seconds.secondsBetween(startTime, DateTime.now()).getSeconds)
    logMemoryStatistics(runtime)
  }

  private def logMemoryStatistics(runtime: Runtime) = {
    val mb = 1024 * 1024
    println("Used Memory: " + (runtime.totalMemory() - runtime.freeMemory()) / mb + " MB")
    println("Free Memory: " + runtime.freeMemory() / mb + " MB")
    println("Total Memory: " + runtime.totalMemory() / mb + " MB")
    println("Max Memory: " + runtime.maxMemory() / mb + " MB")
  }


  def exportManoeuvreCsv(fileName: String, droppedManoeuvres: Map[Long, List[(Long, Option[String], Int, Long, Long, Int, Seq[Int])]]): Unit = {
    val headerLine = "manoeuvre_id; additional_info; source_link_mml_id; source_road_link_id; dest_link_mml_id; dest_road_link_id; exceptions\n"

    val data = droppedManoeuvres.map { case (key, value) =>
      val source = value.find(_._6 == Source).get
      val destination = value.find(_._6 == Destination).get
      s"""$key; ${source._2.getOrElse("")}; ${source._4}; ${source._5}; ${destination._4}; ${destination._5}; ${source._7.mkString(",")}"""
    }.mkString("\n")

    val file = new File(fileName + ".csv")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(headerLine + data + "\n")
    bw.close()
  }

  def exportCsv(fileName: String, droppedLimits: Seq[(Long, Long, Double, Double, Any, Int, Boolean)]): Unit = {
    val headerLine = "mml_id; road_link_id; start_measure; end_measure; value \n"
    val data = droppedLimits.map { x =>
      s"""${x._1}; ${x._2}; ${x._3}; ${x._4}; ${x._5}"""
    }.mkString("\n")

    val file = new File(fileName + ".csv")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(headerLine + data + "\n")
    bw.close()
  }
}
