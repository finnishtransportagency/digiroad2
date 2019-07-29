package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.{ImportStatusInfo, Status}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation

class ImportLogDAO {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val getResult = new GetResult[ImportStatusInfo] {
    def apply(r: PositionedResult) : ImportStatusInfo = {
      val id = r.nextLong()
      val fileName = r.nextString()
      val status = r.nextInt()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val createdBy = r.nextStringOption()
      val logType = r.nextString()
      val content = r.nextStringOption()

      ImportStatusInfo(id, status, Status.apply(status).descriptionFi, fileName, createdBy, createdDate, logType, content)
    }
  }

  def create(username: String, fileName: String): Long = {
    val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
    sqlu"""
        insert into import_log(id, file_name, created_by)
        values ($id, $fileName, $username)
      """.execute
    id
  }

  def update(id: Long, status: Status, content: Option[String]): Long = {
    sqlu"""
        update import_log
        set content = $content
           ,status = ${status.value}
        where id = $id
      """.execute
    id
  }

  def updateLogInfo(id: Long, importType: String): Long = {
    sqlu"""
        update import_log
        set import_type = $importType
        where id = $id
      """.execute
    id
  }

  def get(logId: Long): Option[ImportStatusInfo] = {
    sql"""select ID, FILE_NAME, STATUS, CREATED_DATE, CREATED_BY, IMPORT_TYPE, CONTENT
          from import_log
          where id = $logId
      """.as[ImportStatusInfo].firstOption
  }

  def getByIds(logIds: Set[Long]): Seq[ImportStatusInfo] = {
    sql"""select ID, FILE_NAME, STATUS, CREATED_DATE, CREATED_BY, IMPORT_TYPE, NULL
          from import_log
          where id in (#${logIds.mkString(",")})
      """.as[ImportStatusInfo].list
  }

  def getByUser(username: String): Seq[ImportStatusInfo] = {
    sql"""select ID, FILE_NAME, STATUS, CREATED_DATE, CREATED_BY, IMPORT_TYPE, NULL
          from import_log
          where created_by = $username
          order by created_date desc
      """.as[ImportStatusInfo].list
  }

}
