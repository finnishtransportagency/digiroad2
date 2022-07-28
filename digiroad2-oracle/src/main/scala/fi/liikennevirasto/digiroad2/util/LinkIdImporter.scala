package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import org.slf4j.LoggerFactory
import slick.jdbc.SQLInterpolationResult
import slick.jdbc.StaticQuery.interpolation

import java.sql.PreparedStatement

object LinkIdImporter {
  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  private val logger = LoggerFactory.getLogger(getClass)
  def changeLinkIdIntoKMTKVersion(): Unit = {
    val tableNames = Seq(
      "lane_history_position", "lane_position", "lrm_position", "lrm_position_history",
      "temp_road_address_info", "road_link_attributes", "administrative_class",
      "traffic_direction", "inaccurate_asset","functional_class",
      "incomplete_link","link_type","unknown_speed_limit"
    )
    LogUtils.time(logger, s"Changing vvh id into kmtk id ") {
      withDynTransaction {
        dropIndex()
        tableNames.foreach(tableName=>flipColumns(tableName, "linkid"))
        createIndex()
      }
      Parallel.operation(tableNames.par, 20) { p => p.foreach(updateTable) }
      updateTableRoadLink("roadlink")
      updateTableManoeuvre("manoeuvre_element_history")
      updateTableManoeuvre("manoeuvre_element")
    }
  }

  private def page(tableName: String, min: Int, max: Int): SQLInterpolationResult = {
    sql"""select * from (select a.*, row_number() OVER() rnum 
            from (select distinct vvh_id from #$tableName) a limit #$max ) 
            derivedMml where rnum >= #$min"""
  }

  private def flipColumns(tableName: String, columnName: String): Unit = {
    sqlu"""ALTER TABLE #${tableName} DROP COLUMN vvh_id""".execute
    sqlu"""ALTER TABLE #${tableName} RENAME COLUMN #${columnName} to vvh_id""".execute
    sqlu"""ALTER TABLE #${tableName} ADD COLUMN #${columnName} NUMERIC(38)""".execute
  }

  private def dropIndex(): Unit = {
    sqlu"""DROP INDEX lane_history_pos_link_id_idx""".execute
    sqlu"""DROP INDEX lane_hist_pos_linkid_sidec_idx""".execute
    sqlu"""DROP INDEX lane_position_link_id_idx""".execute
    sqlu"""DROP INDEX lane_pos_linkid_side_code_idx""".execute
    sqlu"""DROP INDEX lrm_position_link_id_idx""".execute
    sqlu"""DROP INDEX hist_lrm_position_link_id_idx""".execute
    sqlu"""DROP INDEX link_id_temp_address""".execute
    sqlu"""DROP INDEX traffic_dir_link_idx""".execute
    sqlu"""DROP INDEX funct_class_idx""".execute
    sqlu"""DROP INDEX incomp_linkid_idx""".execute
    sqlu"""DROP INDEX link_type_idx""".execute
  }

  private def createIndex(): Unit = {
    sqlu"""CREATE INDEX lane_history_pos_link_id_idx ON lane_history_position (link_id)""".execute
    sqlu"""CREATE INDEX lane_hist_pos_linkid_sidec_idx ON lane_history_position (link_id, side_code)""".execute
    sqlu"""CREATE INDEX lane_position_link_id_idx ON lane_position (link_id)""".execute
    sqlu"""CREATE INDEX lane_pos_linkid_side_code_idx ON lane_position (link_id, side_code)""".execute
    sqlu"""CREATE INDEX lrm_position_link_id_idx ON lrm_position (link_id)""".execute
    sqlu"""CREATE INDEX hist_lrm_position_link_id_idx ON lrm_position_history (link_id)""".execute
    sqlu"""CREATE INDEX link_id_temp_address ON temp_road_address_info (link_id)""".execute
    sqlu"""CREATE UNIQUE INDEX traffic_dir_link_idx ON traffic_direction (link_id)""".execute
    sqlu"""CREATE UNIQUE INDEX funct_class_idx ON functional_class (link_id)""".execute
    sqlu"""CREATE UNIQUE INDEX incomp_linkid_idx ON incomplete_link (link_id)""".execute
    sqlu"""CREATE UNIQUE INDEX link_type_idx ON link_type (link_id)""".execute
  }

  private def prepare(tableName: String): (List[(Int, Int)], Int) = {
    val count = sql"""select count(distinct vvh_id) from #$tableName""".as[Int].first
    val batches = getBatchDrivers(0, count, 20000)
    val total = batches.size
    (batches, total)
  }

  private def updateCommand(columnName: String,columnName2: String): String = {
    s"""update ? SET ${columnName} = (select id from frozenlinks_vastintaulu_csv 
                where vvh_linkid = ? ) where ${columnName2} = ? """
  }

  private def createRow(statement: PreparedStatement, id: Int, tableName: String): Unit = {
    statement.setString(1, tableName)
    statement.setInt(2, id)
    statement.setInt(3, id)
    statement.addBatch()
  }

  def updateTableRoadLink(tableName: String): Unit = {
    logger.info(s"Thread ID: ${Thread.currentThread().getId}")
    withDynTransaction {
      val (batches, total) = prepare(tableName)
      LogUtils.time(logger, s"[${DateTime.now}] Table $tableName: Fetching $total batches of links converted") {
        sqlu"""DROP index linkid_index""".execute
        sqlu"""DROP index linkid_mtkc_index""".execute
        flipColumns(tableName, "linkid")
        sqlu"""create index linkid_index on roadlink (linkid)""".execute
        sqlu"""create index linkid_mtkc_index on roadlink (linkid,mtkclass)""".execute
        batches.foreach { case (min, max) =>
          val ids = page(tableName, min, max).as[Int].list
          MassQuery.executeBatch(updateCommand("linkid","vvh_id")) { statement => {
            ids.foreach(i => {createRow(statement, i, tableName)})
          }}
        }
      }
    }
  }

  def updateTableManoeuvre(tableName: String): Unit = {
    logger.info(s"Thread ID: ${Thread.currentThread().getId}")
    withDynTransaction {
      val (batches, total) = prepare(tableName)

      val countD = sql"""select count(distinct vvh_id) from #$tableName""".as[Int].first
      val batchesD = getBatchDrivers(0, countD, 20000)
      val totalD = batches.size
      
      LogUtils.time(logger, s"[${DateTime.now}] Table $tableName: Fetching $total batches of links converted") {
        sqlu"""DROP INDEX hist_element_source_link_idx""".execute
        sqlu"""DROP INDEX element_manoeuvre_idx""".execute
        
        sqlu"""ALTER TABLE #${tableName} DROP COLUMN vvh_id""".execute
        sqlu"""ALTER TABLE #${tableName} RENAME COLUMN link_id to vvh_id""".execute
        sqlu"""ALTER TABLE #${tableName} ADD COLUMN link_id NUMERIC(38)""".execute

        sqlu"""ALTER TABLE #${tableName} DROP COLUMN dest_vvh_id""".execute
        sqlu"""ALTER TABLE #${tableName} RENAME COLUMN dest_link_id to dest_vvh_id""".execute
        sqlu"""ALTER TABLE #${tableName} ADD COLUMN dest_link_id NUMERIC(38)""".execute

        sqlu"""CREATE INDEX hist_element_source_link_idx ON manoeuvre_element_history (link_id, element_type)""".execute
        sqlu"""CREATE INDEX element_manoeuvre_idx ON manoeuvre_element (manoeuvre_id)""".execute
        
        batches.foreach { case (min, max) =>
          val ids = page(tableName, min, max).as[Int].list
          MassQuery.executeBatch(updateCommand("link_id","vvh_id")) { statement => {
            ids.foreach(i => {createRow(statement, i, tableName)})
          }}
        }
        batchesD.foreach { case (min, max) =>
          val ids = page(tableName, min, max).as[Int].list
          MassQuery.executeBatch(updateCommand("dest_link_id","dest_vvh_id")) { statement => {
            ids.foreach(i => {createRow(statement, i, tableName)})
          }}
        }

        
      }
    }
  }

  def updateTable(tableName: String): Unit = {
    logger.info(s"Thread ID: ${Thread.currentThread().getId}")
    withDynTransaction {
      val (batches, total) = prepare(tableName)
      LogUtils.time(logger, s"[${DateTime.now}] Table $tableName: Fetching $total batches of links converted") {
        batches.foreach { case (min, max) =>
          val ids = page(tableName, min, max).as[Int].list
          MassQuery.executeBatch(updateCommand("link_id","vvh_id")) { statement => {
            ids.foreach(i => {createRow(statement, i, tableName)})
          }}
        }
      }
    }
  }

  private def getBatchDrivers(n: Int, m: Int, step: Int): List[(Int, Int)] = {
    if ((m - n) < step) {
      List((n, m))
    } else {
      val x = (n to m by step).sliding(2).map(x => (x(0), x(1) - 1)).toList
      x :+ (x.last._2 + 1, m)
    }
  }
}