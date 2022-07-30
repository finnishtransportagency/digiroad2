package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.StaticQuery.interpolation

import java.sql.PreparedStatement

object LinkIdImporter {
  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def executeBatch[T](query: String)(f: PreparedStatement => T): T = MassQuery.executeBatch(query)(f)
  def time[R](logger: Logger, operationName: String, noFilter: Boolean = false)(f: => R): R = LogUtils.time(logger, operationName, noFilter)(f)
  
  private val logger = LoggerFactory.getLogger(getClass)
  //Resource used 3-6GB 40 thread
  def changeLinkIdIntoKMTKVersion(): Unit = {
    val tableNames = Seq(
      "lane_history_position", "lane_position", "lrm_position",
      "lrm_position_history", "temp_road_address_info", "road_link_attributes",
      "administrative_class", "traffic_direction", "inaccurate_asset",
      "functional_class", "incomplete_link", "link_type",
      "unknown_speed_limit", "roadlink", "manoeuvre_element_history",
      "manoeuvre_element"
    )
    
    val complementaryLinks = withDynSession(sql"""select linkid from roadlinkex where subtype = 3""".as[Int].list)
    time(logger, s"Changing vvh id into kmtk id ") {
      withDynSession( time(logger, s"Table $tableName : drop constrain ") {
        sqlu"ALTER TABLE roadlink DROP CONSTRAINT roadlink_pkey".execute
        sqlu"ALTER TABLE roadlink DROP CONSTRAINT roadlink_linkid".execute
        sqlu"ALTER TABLE roadlink ALTER COLUMN linkid DROP NOT NULL".execute
        sqlu"ALTER TABLE unknown_speed_limit ALTER COLUMN link_id DROP NOT NULL".execute
        sqlu"ALTER TABLE unknown_speed_limit DROP CONSTRAINT unknown_speed_limit_pkey".execute
      })
      new Parallel().operation(tableNames.par, tableNames.size+1) {_.foreach(updateTable(_,complementaryLinks)) }
      withDynSession( time(logger, s"Table $tableName : create constrain ") {
        sqlu"ALTER TABLE roadlink ADD CONSTRAINT roadlink_linkid UNIQUE (linkid) DEFERRABLE INITIALLY DEFERRED".execute
        sqlu"ALTER TABLE roadlink ADD PRIMARY KEY (linkid)".execute
        sqlu"ALTER TABLE roadlink ALTER COLUMN linkid SET NOT NULL".execute
        sqlu"ALTER TABLE unknown_speed_limit ALTER COLUMN link_id SET NOT NULL".execute
        sqlu"ALTER TABLE unknown_speed_limit ADD PRIMARY KEY (link_id);".execute
      })
    }
  }

  def updateTable(tableName: String,complementaryLinks:List[Int] ): Unit = {
    tableName match {
      case "roadlink" => updateTableRoadLink(tableName)
      case "manoeuvre_element_history" => updateTableManoeuvre(tableName)
      case "manoeuvre_element" => updateTableManoeuvre(tableName)
      case "lrm_position" => lrmTable(tableName)
      case _ => regularTable(tableName,complementaryLinks)
    }
  }

  private def updateTableSQL(linkIdColumn: String, tableName: String): String = {
    s"""UPDATE $tableName SET
       | vvh_id = ?,
       | $linkIdColumn = (select id FROM frozenlinks_vastintaulu_csv WHERE vvh_linkid = ? )
       | WHERE $linkIdColumn = ? """.stripMargin
  }

  // what about null values ?
  private def updateTableManoeuvreSQL(linkIdColumn: String, tableName: String): String = {
    s"""UPDATE $tableName SET
       | vvh_id = ?,
       | $linkIdColumn = (SELECT id FROM frozenlinks_vastintaulu_csv WHERE vvh_linkid = ? ),
       | dest_vvh_id = ?,
       | dest_link_id =  COALESCE((SELECT id FROM frozenlinks_vastintaulu_csv WHERE vvh_linkid = ?),? ) 
       | WHERE $linkIdColumn = ? """.stripMargin
  }
  private def updateTableManoeuvreRow(statement: PreparedStatement, ids: (Int, Int)): Unit = {
    statement.setInt(1, ids._1)
    statement.setInt(2, ids._1)
    statement.setInt(3, ids._2)
    statement.setInt(4, ids._2)
    statement.setString(5, ids._2.toString)
    statement.setString(6, ids._1.toString)
    statement.addBatch()
  }
  private def updateTableRow(statement: PreparedStatement, id: Int): Unit = {
    statement.setInt(1, id)
    statement.setInt(2, id)
    statement.setString(3, id.toString)
    statement.addBatch()
  }
  def updateTableRoadLink(tableName: String): Unit = {
    withDynTransaction {
      val ids = sql"select linkid from #$tableName".as[Int].list.toSet
      val total = ids.size
      logger.info(s"Table $tableName, size: $total, Thread ID: ${Thread.currentThread().getId}")
      time(logger, s"Table $tableName: Fetching $total batches of links converted") {
        updateOperation(tableName,ids, "linkid")
      }
    }
  }
  
  protected def updateOperation(tableName: String, ids: Set[Int],linkIdColumn:String): Unit = {
    val groups = ids.grouped(20000).toSeq
    new Parallel().operation(groups.par, 15) {_.foreach { ids =>
        withDynTransaction {
          time(logger, s"Table $tableName, updating: ${ids.size}, Thread ID: ${Thread.currentThread().getId}") {
            executeBatch(updateTableSQL(linkIdColumn, tableName)) { statement => {ids.foreach(
              id => {updateTableRow(statement, id)})}
            }
          }}
    }}
  }
  
  def updateTableManoeuvre(tableName: String): Unit = {
    withDynTransaction {
      val ids = sql"select link_id,dest_link_id from #${tableName}".as[(Int, Int)].list.toSet
      val total = ids.size
      logger.info(s"Table $tableName, size: $total, Thread ID: ${Thread.currentThread().getId}")
      time(logger, s"Table $tableName: Fetching $total batches of links converted") {
        ids.grouped(20000).foreach { ids =>executeBatch(updateTableManoeuvreSQL("link_id", tableName)) { statement => {
            ids.foreach(i => {updateTableManoeuvreRow(statement, i)})
          }}}
      }
    }
  }
  
  def regularTable(tableName: String,complementaryLinks:List[Int]): Unit = {
    val ids = withDynSession(sql"select link_id from #${tableName}".as[Int].list).toSet.diff(complementaryLinks.toSet)
    val total = ids.size
    logger.info(s"Table $tableName, size: $total, Thread ID: ${Thread.currentThread().getId}")
    time(logger, s"Table $tableName: Fetching $total batches of links converted") {
      updateOperation(tableName,ids, "link_id")
    }
  }
  
  def lrmTable(tableName: String): Unit = {
    val ids = withDynSession(
      sql"""select link_id from #${tableName} where link_source in (#${LinkGeomSource.NormalLinkInterface.value})
           """.as[Int].list).toSet
    val total = ids.size
    logger.info(s"Table $tableName, size: $total, Thread ID: ${Thread.currentThread().getId}")
    time(logger, s"Table $tableName: Fetching $total batches of links converted") {
      updateOperation(tableName,ids, "link_id")
    }
  }
}