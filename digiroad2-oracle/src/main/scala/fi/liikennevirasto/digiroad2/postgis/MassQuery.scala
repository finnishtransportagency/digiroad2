package fi.liikennevirasto.digiroad2.postgis

import fi.liikennevirasto.digiroad2.util.LogUtils
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import java.sql.PreparedStatement

object MassQuery {
  private val logger = LoggerFactory.getLogger(getClass)
 def withNumberIdsValuesJoin(column: String, ids: Set[Long]) = {
  if (ids.nonEmpty) {
   val values = ids.map(id => s"($id)").mkString(",")
   s"JOIN ( VALUES $values ) links(link) ON ($column = link) "
  }
  else ""
 }

 def withStringIdsValuesJoin(column: String, ids: Set[String]) = {
  if (ids.nonEmpty) {
   val values = ids.map(id => s"('${id}')").mkString(",")
   s"JOIN ( VALUES $values ) links(link) ON ($column = link) "
  }
  else ""
 }
 
 /**
   * Builds a SQL fragment that represents a VALUES table of string IDs, optionally
   * emitting a JOIN clause that links the VALUES table to an existing table column.
   *
   * Typical usages:
   *  - When `joinColumn` is empty, the method returns a VALUES table fragment that can
   *    be used directly in the FROM clause (you must provide the appropriate surrounding
   *    SQL and aliasing).
   *  - When `joinColumn` is non-empty, the method returns a complete JOIN clause that
   *    joins the generated VALUES table (aliased by `tableName` with a single column
   *    named `id`) to the provided join expression.
   *
   * Examples:
   * 1) VALUES fragment (no joinColumn) - put into a FROM clause:
   * val ids = Set("a", "b", "c")
   * // returns: " ( VALUES ('a'),('b'),('c') ) links(id) "
   * val fragment = withStringIdsValues(ids, tableName = "links")
   * val sql = s"SELECT i.* FROM ${fragment} JOIN your_table i ON (i.id = links.id)"
   *
   * 2) JOIN fragment (joinColumn specified) - inject directly into FROM clause:
   * val ids = Set("a", "b")
   * // returns: "join ( VALUES ('a'),('b') ) links(id)  ON (i.id = link)"
   * val joinFrag = withStringIdsValues(ids, tableName = "links", joinColumn = "i.id")
   * val sql = s"SELECT i.* FROM your_table i ${joinFrag}"*
   * Parameters:
   *
   * @param ids        Set[String]  the set of string ids to include in the VALUES list (each id
   *                   will be wrapped in single quotes).
   * @param tableName  String  alias/name used for the generated VALUES table (default "links").
   * @param joinColumn String  if empty (default) just emit the VALUES table fragment;
   *                   if non-empty emit a "join ( VALUES ... ) <tableName>(id) ON (<joinColumn> = link)"
   *
   *                   Returns:
   * @return String  a SQL fragment; either the VALUES fragment or a JOIN clause, or the
   *         empty string if `ids` is empty.
   */
 def withStringIdsValues(ids: Set[String],tableName: String = "links",joinColumn:String = ""): String = {
  if (ids.nonEmpty) {
   val values = ids.map(id => s"('${id}')").mkString(",")
   if (joinColumn == "") 
    s" ( VALUES $values ) $tableName(id) "
   else 
    s"join ( VALUES $values ) $tableName(id)  ON ($joinColumn = id)"
  }
  else ""
 }
 /**
   * Builds a SQL fragment that represents a VALUES table of long IDs, optionally
   * emitting a JOIN clause that links the VALUES table to an existing table column.
   *
   * Typical usages:
   *  - When `joinColumn` is empty, the method returns a VALUES table fragment that can
   *    be used directly in the FROM clause (you must provide the appropriate surrounding
   *    SQL and aliasing).
   *  - When `joinColumn` is non-empty, the method returns a complete JOIN clause that
   *    joins the generated VALUES table (aliased by `tableName` with a single column
   *    named `id`) to the provided join expression.
   *
   * Examples:
   * 1) VALUES fragment (no joinColumn) - put into a FROM clause:
   * val ids = Set(1, 2, 3)
   * // returns: " ( VALUES (1),(2),(3) ) links(id) "
   * val fragment = withStringIdsValues(ids, tableName = "links")
   * val sql = s"SELECT i.* FROM ${fragment} JOIN your_table i ON (i.id = links.id)"
   *
   * 2) JOIN fragment (joinColumn specified) - inject directly into FROM clause:
   * val ids = Set(1, 2)
   * // returns: "join ( VALUES (1),(2) ) links(id)  ON (i.id = link)"
   * val joinFrag = withStringIdsValues(ids, tableName = "links", joinColumn = "i.id")
   * val sql = s"SELECT i.* FROM your_table i ${joinFrag}"*
   * Parameters:
   *
   * @param ids        Set[Long]  the set of long  to include in the VALUES list.
   * @param tableName  String  alias/name used for the generated VALUES table (default "links").
   * @param joinColumn String  if empty (default) just emit the VALUES table fragment;
   *                   if non-empty emit a "join ( VALUES ... ) <tableName>(id) ON (<joinColumn> = link)"
   *
   *                   Returns:
   * @return String  a SQL fragment; either the VALUES fragment or a JOIN clause, or the
   *         empty string if `ids` is empty.
   */
 def withNumberIdsValues(ids: Set[Long], tableName: String = "links", joinColumn: String = ""): String = {
  if (ids.nonEmpty) {
   val values = ids.map(id => s"(${id})").mkString(",")
   if (joinColumn == "")
    s" ( VALUES $values ) $tableName(id) "
   else
    s"join ( VALUES $values ) $tableName(id)  ON ($joinColumn = id)"
  }
  else ""
 }
  
  /**
    * Remember to add check for size of list, golden rule more than 1000 in array some improvement can be found
    * otherwise use normal id in () query. Till remember measure with or without massQuery.
    * @param ids 
    * @param f sql query function
    * @tparam T
    * @return
    */
  def withIds[T](ids: Set[Long])(f: String => T): T = {
    LogUtils.time(logger, s"TEST LOG MassQuery withIds ${ids.size}"){
      LogUtils.time(logger, "TEST LOG create TEMP_ID table"){
        sqlu"""
      CREATE TEMPORARY TABLE IF NOT EXISTS TEMP_ID (
        ID BIGINT NOT NULL,
	      CONSTRAINT TEMP_ID_PK PRIMARY KEY (ID)
      ) ON COMMIT DELETE ROWS
    """.execute
      }
      val insertLinkIdPS = dynamicSession.prepareStatement("insert into temp_id (id) values (?)")

      LogUtils.time(logger, s"TEST LOG insert into TEMP_ID ${ids.size}"){
        try {
          ids.foreach { id =>
            insertLinkIdPS.setLong(1, id)
            insertLinkIdPS.addBatch()
          }
          insertLinkIdPS.executeBatch()
          val ret = f("temp_id")
          sqlu"TRUNCATE TABLE TEMP_ID".execute
          ret
        } finally {
          insertLinkIdPS.close()
        }
      }
    }
  }
  
  /**
    * Remember to add check for size of list, golden rule more than 1000 in array some improvement can be found
    * otherwise use normal id in () query. Till remember measure with or without massQuery.
    * @param ids
    * @param f sql query function
    * @tparam T
    * @return
    */
  def withStringIds[T](ids: Set[String])(f: String => T): T = {
    LogUtils.time(logger, s"TEST LOG MassQuery withIds ${ids.size}"){
      LogUtils.time(logger, "TEST LOG create TEMP_STRING_ID table"){
        sqlu"""
          CREATE TEMPORARY TABLE IF NOT EXISTS TEMP_STRING_ID (
            ID VARCHAR(40) NOT NULL,
            CONSTRAINT TEMP_STRING_ID_PK PRIMARY KEY (ID)
          ) ON COMMIT DELETE ROWS
        """.execute
      }
      val insertLinkIdPS = dynamicSession.prepareStatement("insert into temp_string_id (id) values (?)")

      LogUtils.time(logger, s"TEST LOG insert into TEMP_STRING_ID ${ids.size}"){
        try {
          ids.foreach { id =>
            insertLinkIdPS.setString(1, id)
            insertLinkIdPS.addBatch()
          }
          insertLinkIdPS.executeBatch()
          val ret = f("temp_string_id")
          sqlu"TRUNCATE TABLE TEMP_STRING_ID".execute
          ret
        } finally {
          insertLinkIdPS.close()
        }
      }
    }
  }

  def executeBatch[T](query: String)(f: PreparedStatement => T): T = {
    val statement = dynamicSession.prepareStatement(query)
    try {
      val ret = f(statement)
      statement.executeBatch()
      ret
    } finally {
      statement.close()
    }
  }
}
