package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation

import java.sql.PreparedStatement

object LinkIdImporter {
  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def executeBatch[T](query: String)(f: PreparedStatement => T): T = MassQuery.executeBatch(query)(f)
  private val logger = LoggerFactory.getLogger(getClass)
  def changeLinkIdIntoKMTKVersion(): Unit = {
    val tableNames = Seq(
      "lane_history_position", "lane_position", "lrm_position", 
      "lrm_position_history", "temp_road_address_info", "road_link_attributes", 
      "administrative_class", "traffic_direction", "inaccurate_asset", 
      "functional_class", "incomplete_link", "link_type", 
      "unknown_speed_limit", "roadlink", "manoeuvre_element_history",
      "manoeuvre_element"
    )
    LogUtils.time(logger, s"Changing vvh id into kmtk id ") {
      Parallel.operation(tableNames.par, tableNames.size+1) { p => p.foreach(updateTable) }
    }
  }

  def updateTable(tableName: String): Unit = {
    tableName match {
      case "roadlink" => updateTableRoadLink(tableName)
      case "manoeuvre_element_history" => updateTableManoeuvre(tableName)
      case "manoeuvre_element" => updateTableManoeuvre(tableName)
      case _ => regularTable(tableName)
    }
  }

  private def copyIntoVVHIDAndUpdateNewID(linkIdColumn: String, tableName: String): String = {
    s"""UPDATE ${tableName} SET
       | vvh_id = ?,
       | ${linkIdColumn} = (select id FROM frozenlinks_vastintaulu_csv WHERE vvh_linkid = ? )
       | WHERE ${linkIdColumn} = ? """.stripMargin
  }

  // what about null values ?
  private def copyIntoVVHIDAndUpdateNewIDManouvre(linkIdColumn: String, tableName: String): String = {
    s"""UPDATE ${tableName} SET
       | vvh_id = ?,
       | ${linkIdColumn} = (SELECT id FROM frozenlinks_vastintaulu_csv WHERE vvh_linkid = ? ),
       | dest_vvh_id = ?,
       | dest_link_id =  COALESCE((SELECT id FROM frozenlinks_vastintaulu_csv WHERE vvh_linkid = ?),? ) 
       | WHERE ${linkIdColumn} = ? """.stripMargin
  }
  private def copyIntoVVHIDRowManouvre(statement: PreparedStatement, ids: (Int, Int)): Unit = {
    statement.setInt(1, ids._1)
    statement.setInt(2, ids._1)
    statement.setInt(3, ids._2)
    statement.setInt(4, ids._2)
    statement.setString(5, ids._2.toString)
    statement.setString(6, ids._1.toString)
    statement.addBatch()
  }
  private def copyIntoVVHIDRow(statement: PreparedStatement, id: Int): Unit = {
    statement.setInt(1, id)
    statement.setInt(2, id)
    statement.setString(3, id.toString)
    statement.addBatch()
  }


  def updateTableRoadLink(tableName: String): Unit = {
    withDynTransaction {
      LogUtils.time(logger, s"Table $tableName : drop constrain ") {
        sqlu"ALTER TABLE roadlink DROP CONSTRAINT roadlink_pkey".execute
        sqlu"ALTER TABLE roadlink DROP CONSTRAINT roadlink_linkid".execute
        sqlu"ALTER TABLE roadlink ALTER COLUMN linkid DROP NOT NULL".execute
      }
      val ids = sql"select linkid from #${tableName}".as[Int].list
      val total = ids.size
      logger.info(s"Table $tableName, size: $total  Thread ID: ${Thread.currentThread().getId}")
      LogUtils.time(logger, s"[${DateTime.now}] Table $tableName: Fetching $total batches of links converted") {
        ids.grouped(20000).foreach { ids =>
          executeBatch(copyIntoVVHIDAndUpdateNewID("linkid", tableName)) { statement => {
            ids.foreach(i => {copyIntoVVHIDRow(statement, i)})}}
        }
        LogUtils.time(logger, s"Table $tableName : create constrain ") {
          sqlu"ALTER TABLE roadlink ADD CONSTRAINT roadlink_linkid UNIQUE (linkid) DEFERRABLE INITIALLY DEFERRED".execute
          sqlu"ALTER TABLE roadlink ADD PRIMARY KEY (linkid)".execute
          sqlu"ALTER TABLE roadlink ALTER COLUMN linkid SET NOT NULL".execute
        }
      }
    }
  }

  def updateTableManoeuvre(tableName: String): Unit = {
    withDynTransaction {
      LogUtils.time(logger, s"Table $tableName :drop constrain ") {
        sqlu"ALTER TABLE manoeuvre_element DROP CONSTRAINT non_final_has_destination".execute
        sqlu"ALTER TABLE manoeuvre_element_history DROP CONSTRAINT hist_non_final_has_destination".execute
      }
      val ids = sql"select link_id,dest_link_id from #${tableName}".as[(Int, Int)].list
      val total = ids.size
      logger.info(s"Table $tableName, size: $total  Thread ID: ${Thread.currentThread().getId}")
      LogUtils.time(logger, s"Table $tableName: Fetching $total batches of links converted") {
        ids.grouped(20000).foreach { ids =>
          executeBatch(copyIntoVVHIDAndUpdateNewIDManouvre("link_id", tableName)) { statement => {
            ids.foreach(i => {copyIntoVVHIDRowManouvre(statement, i)})
          }}
        }
      }
      LogUtils.time(logger, s"Table $tableName : create constrain ") {
        sqlu"ALTER TABLE manoeuvre_element ADD CONSTRAINT non_final_has_destination CHECK (element_type = 3 OR dest_link_id IS NOT NULL)".execute
        sqlu"ALTER TABLE manoeuvre_element_history ADD CONSTRAINT hist_non_final_has_destination CHECK (element_type = 3 OR dest_link_id IS NOT NULL)".execute
      }
    }
  }

  def regularTable(tableName: String): Unit = {
    val ids = withDynSession(sql"select link_id from #${tableName}".as[Int].list)
    val total = ids.length
    logger.info(s"Table $tableName, size: $total  Thread ID: ${Thread.currentThread().getId}")
    LogUtils.time(logger, s"Table $tableName: Fetching $total batches of links converted") {
      ids.grouped(20000).foreach { ids =>
        withDynTransaction {
          executeBatch(copyIntoVVHIDAndUpdateNewID("link_id", tableName)) { statement => {
            ids.foreach(i => {copyIntoVVHIDRow(statement, i)})
          }}
        }
      }
    }
  }
}