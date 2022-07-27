package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation

import java.sql.PreparedStatement
import scala.collection.parallel.{ForkJoinTaskSupport, ParIterable}
import scala.concurrent.forkjoin.ForkJoinPool
import scala.language.higherKinds

object Parallel {
  private var parallelismLevel: Int = 1
  private var forkJoinPool: ForkJoinPool = null
  private def prepare[T](list: ParIterable[T]): ParIterable[T] = {
    forkJoinPool = new ForkJoinPool(parallelismLevel)
    list.tasksupport = new ForkJoinTaskSupport(forkJoinPool)
    list
  }
  def runOperation[T, U](list: ParIterable[T], parallelism: Int = 1)(f: ParIterable[T] => U): Unit = {
    parallelismLevel = parallelism
    f(prepare[T](list))
    forkJoinPool.shutdown()
  }
}

object LinkIdImporter {
  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  val logger = LoggerFactory.getLogger(getClass)
  def changeLinkIdIntoKMTKVersion(): Unit = {
    val tableNames = Seq(
      "lane_history_position", "lane_position", "lrm_position", "lrm_position_history",
      "temp_road_address_info", "road_link_attributes", "administrative_class",
      "traffic_direction", "monouvre_element_history", "monouvre_element", "inaccurate_asset"
    )
    LogUtils.time(logger,s"Changing vvh id into kmtk id "){
      Parallel.runOperation(tableNames.par,20){ p=>p.foreach(updateTable)}
      updateTableRoadLink("roadlink")
    }
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
      val (batches, total) = prepare(tableName)
      LogUtils.time(logger,s"[${DateTime.now}] Table $tableName: Fetching $total batches of links"){
        flipColumns(tableName, "linkid")
        batches.foreach { case (min, max) =>
          val ids = page(tableName, min, max).as[Int].list
          MassQuery.executeBatch(updateCommand("linkid")) {
            statement => {ids.foreach(i => {createRow(statement, i, tableName)})}}
        }}
    }
  }

  def updateTable(tableName: String): Unit = {
    withDynTransaction {
      val (batches, total) = prepare(tableName)
      LogUtils.time(logger,s"[${DateTime.now}] Table $tableName: Fetching $total batches of links"){
        flipColumns(tableName, "link_id")
        batches.foreach { case (min, max) =>
          val ids = page(tableName, min, max).as[Int].list
          MassQuery.executeBatch(updateCommand("link_id")) {
            statement => {ids.foreach(i => {createRow(statement, i, tableName)})}}
        }}
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