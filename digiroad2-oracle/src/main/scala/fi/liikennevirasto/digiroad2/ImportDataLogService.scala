package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries.nextPrimaryKeyId
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation


object ImportDataLogService {

  val logger = LoggerFactory.getLogger(getClass)

  def nextPrimaryKeySeqValue = {
    nextPrimaryKeyId.as[Long].first
  }

  def save(content: String): Long = {
    OracleDatabase.withDynTransaction {
      val id = nextPrimaryKeySeqValue
      sqlu"""
        insert into import_data_log(id, content)
        values($id, $content)
        """.execute
      id
    }
  }

  def save(id: Long, content: String): Long = {
    OracleDatabase.withDynTransaction {
      sqlu"""
        update import_data_log set content = $content
          where id = $id
        """.execute
      id
    }
  }

  def get(id: Long): Option[String] = {
    OracleDatabase.withDynTransaction {
      sql"select content from import_data_log where id = $id".as[String].firstOption
    }
  }
}



