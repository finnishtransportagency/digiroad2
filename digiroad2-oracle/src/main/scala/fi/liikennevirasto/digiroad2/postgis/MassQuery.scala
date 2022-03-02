package fi.liikennevirasto.digiroad2.postgis

import fi.liikennevirasto.digiroad2.util.LogUtils
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import java.sql.PreparedStatement

object MassQuery {
  def withIds[T](ids: Set[Long])(f: String => T): T = {
    
    val logger = LoggerFactory.getLogger(getClass)
    LogUtils.time(logger, "TEST LOG MassQuery withIds"){

      if (ids.size > 1000) {
        logger.info("over thousand "+ids.size)
      }else{
        logger.info("under thousand "+ids.size)
      }
      LogUtils.time(logger, "TEST LOG create TEMP_ID table"){
        sqlu"""
      CREATE TEMPORARY TABLE IF NOT EXISTS TEMP_ID (
        ID BIGINT NOT NULL,
	      CONSTRAINT TEMP_ID_PK PRIMARY KEY (ID)
      ) ON COMMIT DELETE ROWS
    """.execute
      }
      val insertLinkIdPS = dynamicSession.prepareStatement("insert into temp_id (id) values (?)")

      LogUtils.time(logger, "TEST LOG insert into TEMP_ID"){
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

  def executeBatch[T](query: String)(f: PreparedStatement => T): Unit = {
    val statement = dynamicSession.prepareStatement(query)
    try {
      f(statement)
      statement.executeBatch()
    } finally {
      statement.close()
    }
  }
}