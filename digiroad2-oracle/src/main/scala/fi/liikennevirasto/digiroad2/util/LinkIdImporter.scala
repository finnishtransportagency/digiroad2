package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.StaticQuery.interpolation

import java.sql.PreparedStatement
import scala.collection.TraversableLike
import scala.collection.mutable.ListBuffer
import scala.util.Try

object LinkIdImporter {
  sealed implicit class SplitByConversion(val list: TraversableLike[String, Any]) {
    def partitionByConversion(): (Seq[Int], Seq[String]) = {
      val (intList, stringList) = (new ListBuffer[Int], new ListBuffer[String])
      for (element <- list) {
        if (Try(element.toInt).isSuccess)
          intList.append(element.toInt)
        else stringList.append(element)
      }
      (intList.toList, stringList.toList)
    }
  }

  sealed implicit class SplitByConversionTuple(val list: TraversableLike[(String, String), Any]) {
    def partitionByConversion(): (Seq[(Int, Option[Int])], Seq[(String, String)]) = {
      val (intList, stringList) = (new ListBuffer[(Int, Option[Int])], new ListBuffer[(String, String)])
      for (element <- list) {
        (Try(element._1.toInt).isSuccess, Try(element._2.toInt).isSuccess) match {
          case (true, true) =>  intList.append((element._1.toInt, Some(element._2.toInt)))
          case (true, false) => intList.append((element._1.toInt, None))
          case _ =>             stringList.append(element)
        }
      }
      (intList.toList, stringList.toList)
    }
  }
  
  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def executeBatch[T](query: String)(f: PreparedStatement => T): T = MassQuery.executeBatch(query)(f)
  def time[R](logger: Logger, operationName: String, noFilter: Boolean = false)(f: => R): R = LogUtils.time(logger, operationName, noFilter)(f)
  
  val groupingSize = 20000
  
  private val logger = LoggerFactory.getLogger(getClass)
  //Resource used 3-6GB 40 thread
  def changeLinkIdIntoKMTKVersion(): Unit = {
    val tableNames = Seq(
      "lane_history_position", "lane_position", "lrm_position",
      "lrm_position_history", "road_link_attributes",
      "administrative_class", "traffic_direction", "inaccurate_asset",
      "functional_class", "incomplete_link", "link_type",
      "unknown_speed_limit", "roadlink", "manoeuvre_element_history",
      "manoeuvre_element"
    )
    
    val complementaryLinks = withDynSession(sql"""select linkid from roadlinkex where subtype = 3""".as[Int].list)
    time(logger, s"Changing vvh id into kmtk id ") {
      new Parallel().operation(tableNames.par, tableNames.size+1) {_.foreach(updateTable(_,complementaryLinks)) }
    }
  }

  private def updateTable(tableName: String,complementaryLinks:List[Int] ): Unit = {
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
  
  private def updateTableManoeuvreSQL(linkIdColumn: String, tableName: String): String = {
    s"""UPDATE $tableName SET
       | vvh_id = ?,
       | $linkIdColumn = (SELECT id FROM frozenlinks_vastintaulu_csv WHERE vvh_linkid = ? ),
       | dest_vvh_id = ?,
       | dest_link_id =  COALESCE((SELECT id FROM frozenlinks_vastintaulu_csv WHERE vvh_linkid = ?),? )
       | WHERE $linkIdColumn = ?
       | AND (dest_link_id = ? OR (? is null AND dest_link_id is null))""".stripMargin
  }
  private def updateTableManoeuvreRow(statement: PreparedStatement, ids: (Int, Option[Int])): Unit = {
    def setOptionalId(index: Int, id: Option[Int], sqlType: Int): Unit = {
      (id.isDefined, sqlType) match {
        case (true, java.sql.Types.BIGINT) =>   statement.setLong(index, id.get)
        case (true, java.sql.Types.VARCHAR) =>  statement.setString(index, id.get.toString)
        case _ => statement.setNull(index, sqlType)
      }
    }
    statement.setLong(1, ids._1)
    statement.setLong(2, ids._1)
    setOptionalId(3, ids._2, java.sql.Types.BIGINT)
    setOptionalId(4, ids._2, java.sql.Types.BIGINT)
    setOptionalId(5, ids._2, java.sql.Types.VARCHAR)
    statement.setString(6, ids._1.toString)
    setOptionalId(7, ids._2, java.sql.Types.VARCHAR)
    setOptionalId(8, ids._2, java.sql.Types.VARCHAR)
    statement.addBatch()
  }
  
  private def updateTableRow(statement: PreparedStatement, id: Int): Unit = {
    statement.setInt(1, id)
    statement.setInt(2, id)
    statement.setString(3, id.toString)
    statement.addBatch()
  }

  private def updateOperation(tableName: String, ids: Set[Int],linkIdColumn:String): Unit = {
    val total = ids.size
    val groups = ids.grouped(groupingSize).toSeq
    logger.info(s"Table $tableName, size: $total in ${groups.size} groups, Thread ID: ${Thread.currentThread().getId}")
    time(logger, s"Table $tableName: $total links converted") {
      new Parallel().operation(groups.par, 15) {_.foreach { ids =>
        withDynTransaction {
          val updating = if(ids.size < groupingSize ){s", updating: ${ids.size}"} else ""
          time(logger, s"Table ${tableName}${updating}, Thread ID: ${Thread.currentThread().getId}") {
            executeBatch(updateTableSQL(linkIdColumn, tableName)) { statement => {ids.foreach(
              id => {updateTableRow(statement, id)})}
            }
          }}
      }}
    }
  }
  
 private def updateTableManoeuvre(tableName: String): Unit = {
    withDynTransaction {
      val ids = sql"select link_id,dest_link_id from #${tableName}".as[(String, String)].list.partitionByConversion()._1
      val total = ids.size
      logger.info(s"Table $tableName, size: $total, Thread ID: ${Thread.currentThread().getId}")
      time(logger, s"Table $tableName: Fetching $total batches of links converted") {
        ids.grouped(groupingSize).foreach { ids =>executeBatch(updateTableManoeuvreSQL("link_id", tableName)) { statement => {
            ids.foreach(i => {updateTableManoeuvreRow(statement, i)})
          }}}
      }
    }
  }

  private def updateTableRoadLink(tableName: String): Unit = {
    val ids = withDynSession(sql"select linkid from #$tableName".as[String].list).partitionByConversion()._1.toSet
    updateOperation(tableName,ids, "linkid")
  }

  private def regularTable(tableName: String,complementaryLinks:List[Int]): Unit = {
    val ids = withDynSession(sql"select link_id from #${tableName}".as[String].list).partitionByConversion()._1.toSet.diff(complementaryLinks.toSet)
    updateOperation(tableName,ids, "link_id")
  }
  
  private def lrmTable(tableName: String): Unit = {
    val ids = withDynSession(
      sql"""select link_id from #${tableName} where link_source in (#${LinkGeomSource.NormalLinkInterface.value})
           """.as[String].list).partitionByConversion()._1.toSet
      updateOperation(tableName,ids, "link_id")
  }
}