package fi.liikennevirasto.digiroad2.dao

import java.util.Date

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation

sealed trait Status {
  def value : Int
  def description: String
}

object Status {
  val values : Set[Status] = Set(InProgress, OK, NotOK, Abend)

  def apply(value: Int) : Status = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object InProgress extends Status {def value = 1; def description = "In progress ..."}
  case object OK extends Status {def value = 1; def description = "All records was treated "}
  case object NotOK extends Status {def value = 1; def description = "Process Executed but some fail records"}
  case object Abend extends Status {def value = 1; def description = "Process fail"}
  case object Unknown extends Status {def value = 99; def description = "Unknown Status Type"}
}

case class ImportStatusInfo(id: Long, status: Status, fileName: String, createdBy: Option[String], createdDate: Option[DateTime], logType: String, content: Option[String])

object ImportLogDAO {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val getResult = new GetResult[ImportStatusInfo] {
    def apply(r: PositionedResult) : ImportStatusInfo = {
      val id = r.nextLong()
      val status = r.nextInt()
      val fileName = r.nextString()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val createdBy = r.nextStringOption()
      val logType = r.nextString()
      val content = r.nextStringOption()

      ImportStatusInfo(id, Status.apply(status), fileName, createdBy, createdDate, logType, content)
    }
  }

  def save(username: String, importType: String, fileName: String): Long = {
    OracleDatabase.withDynTransaction {
      val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
      sqlu"""
        insert into import_log(id, import_type, file_name, created_by)
        values ($id, $importType, $fileName, $username)
      """.execute
      id
    }
  }

  def update(id: Long, status: Status, content: Option[String] = None): Long = {
    OracleDatabase.withDynTransaction {
      sqlu"""
        update import_log
        set content = $content
           ,status = ${status.value}
         where id = $id
      """.execute
      id
    }
  }

  def get(logId: Long): Option[ImportStatusInfo] = {
    OracleDatabase.withDynTransaction {
        sql"""select ID, ID, FILE_NAME, STATUS, CREATED_DATE, CREATED_BY, IMPORT_TYPE, CONTENT
              from import_log
               where id = $logId
          """.as[ImportStatusInfo].firstOption
    }
  }

  def getByUser(username: String, importTypes: Seq[String]): Seq[ImportStatusInfo] = {
    OracleDatabase.withDynTransaction {
        sql"""select ID, FILE_NAME, STATUS, CREATED_DATE, CREATED_BY, IMPORT_TYPE, CONTENT
              from import_log
              where username = $username and import_type in (${importTypes.mkString(",")})
          """.as[ImportStatusInfo].list
    }
  }

}
