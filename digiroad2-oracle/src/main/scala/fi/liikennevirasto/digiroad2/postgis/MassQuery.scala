package fi.liikennevirasto.digiroad2.postgis

import fi.liikennevirasto.digiroad2.util.LogUtils
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import java.sql.PreparedStatement

object MassQuery {
  val logger = LoggerFactory.getLogger(getClass)
  
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