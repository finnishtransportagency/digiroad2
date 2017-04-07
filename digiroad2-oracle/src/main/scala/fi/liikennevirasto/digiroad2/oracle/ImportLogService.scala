package fi.liikennevirasto.digiroad2.oracle

import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object ImportLogService {
  val logger = LoggerFactory.getLogger(getClass)

  def save(content: String): Long = {
    OracleDatabase.withDynTransaction {
      val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
      sqlu"""
        insert into import_log(id, content)
        values ($id, $content)
      """.execute
      id
    }
  }

  def save(id: Long, content: String): Long = {
    OracleDatabase.withDynTransaction {
      sqlu"""
        update import_log set content = $content
          where id = $id
      """.execute
      id
    }
  }

  def get(id: Long): Option[String] = {
    OracleDatabase.withDynTransaction {
      sql"select content from import_log where id = $id".as[String].firstOption
    }
  }

}
