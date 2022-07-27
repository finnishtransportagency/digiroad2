package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import java.sql.PreparedStatement
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool

object LinkIdImporter {
  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)

  def changeLinkIdIntoKMTKVersion(): Unit = {
    val forkJoinPool = new ForkJoinPool(20)
    // what todo when doing historia tables
    val tableNames = Seq(
      "lane_history_position", "lane_position", "lrm_position", "lrm_position_history",
      "temp_road_address_info", "road_link_attributes", "administrative_class",
      "traffic_direction", "monouvre_element_history", "monouvre_element", "inaccurate_asset"
    )
    val tableNamesPar = tableNames.par
    tableNamesPar.tasksupport = new ForkJoinTaskSupport(forkJoinPool)
    tableNames.par.foreach { table =>
      updateTable(table)
    }

    updateTableRoadLink("roadlink")
  }

  def page(tableName: String, min: Int, max: Int) = {
    sql"""select * from (select a.*, row_number() OVER() rnum 
            from (select distinct vvh_id from #$tableName) a limit #$max ) 
            derivedMml where rnum >= #$min"""
  }

  def flipColumns(tableName: String, columnName: String): Unit = {
    sqlu"""ALTER TABLE #${tableName} DROP COLUMN vvh_id""".execute
    sqlu"""ALTER TABLE #${tableName} RENAME COLUMN #${columnName} to vvh_id""".execute
    sqlu"""ALTER TABLE #${tableName} ADD COLUMN #${columnName} NUMERIC(38)""".execute
  }

  def prepare(tableName: String) = {
    val count = sql"""select count(distinct vvh_id) from #$tableName""".as[Int].first
    val batches = getBatchDrivers(0, count, 20000)
    val total = batches.size
    (batches, total)
  }

  def updateCommand(columnName: String): String = {
    s"""update ? SET ${columnName} = (select id from frozenlinks_vastintaulu_csv 
                where vvh_linkid = ? ) where vvh_id = ? """
  }

  def createRow(statement: PreparedStatement, id: Int, tableName: String): Unit = {
    statement.setString(1, tableName)
    statement.setInt(2, id)
    statement.setInt(3, id)
    statement.addBatch()
  }

  def updateTableRoadLink(tableName: String): Unit = {
    withDynTransaction {
      val startTime = DateTime.now()
      val (batches, total) = prepare(tableName)

      print(s"[${DateTime.now}] Table $tableName: Fetching $total batches of links… ")
      flipColumns(tableName, "linkid")
      batches.foreach { case (min, max) =>
        val ids = page(tableName, min, max).as[Int].list
        MassQuery.executeBatch(updateCommand("linkid")) {
          statement => {ids.foreach(i => {createRow(statement, i, tableName)})}}
      }
      println(s"done in ${AssetDataImporter.humanReadableDurationSince(startTime)}.")
    }
  }

  def updateTable(tableName: String): Unit = {
    withDynTransaction {
      val startTime = DateTime.now()
      val (batches, total) = prepare(tableName)
      print(s"[${DateTime.now}] Table $tableName: Fetching $total batches of links… ")
      flipColumns(tableName, "link_id")
      batches.foreach { case (min, max) =>
        val ids = page(tableName, min, max).as[Int].list
        MassQuery.executeBatch(updateCommand("link_id")) { 
          statement => {ids.foreach(i => {createRow(statement, i, tableName)})}}
      }
      println(s"done in ${AssetDataImporter.humanReadableDurationSince(startTime)}.")
    }
  }

  def getBatchDrivers(n: Int, m: Int, step: Int): List[(Int, Int)] = {
    if ((m - n) < step) {
      List((n, m))
    } else {
      val x = (n to m by step).sliding(2).map(x => (x(0), x(1) - 1)).toList
      x :+ (x.last._2 + 1, m)
    }
  }
}