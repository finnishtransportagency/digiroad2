package fi.liikennevirasto.digiroad2.dao.feedback

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import slick.jdbc.StaticQuery.interpolation
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.service.feedback.FeedbackInfo
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

class FeedbackDao {


  def getFeedback(queryFilter: String => String): Seq[FeedbackInfo] ={
    val query =  s"""
            select id, created_by, created_date, subject, body, status, status_date
            from feedback
            """
    StaticQuery.queryNA[FeedbackInfo](queryFilter(query)).iterator.toSeq
  }

  def byAll()(query: String):  String = {
    query
  }

  def byStatus(status: Boolean)(query: String): String = {
    val feedbackFilter = if (status) " status = 1 " else " status = 0 "
    query + s"where $feedbackFilter"
  }

  def byId(ids: Set[Long])(query: String): String = {
    val idsToQuery = ids.mkString(",")
    query + s"where id in ($idsToQuery)"
  }

  implicit val feedback = new GetResult[FeedbackInfo] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val createdBy = r.nextStringOption()
      val createdAt = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val subject = r.nextStringOption()
      val body = r.nextStringOption()
      val status = r.nextBoolean()
      val statusDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      FeedbackInfo(id, createdBy, createdAt, body, subject, status, statusDate)
    }
  }

  def insertFeedback(createdBy: String, body: String, subject: String, status: Boolean): Long = {
   val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
      sqlu"""
          insert into feedback (id, created_by, created_date, subject, body, status, status_date)
          values ($id, ${createdBy}, sysdate, ${subject},
                ${body},${status}, sysdate)""".execute
    id
  }

  def updateFeedback(id: Long): Long = {
    sqlu"""
          update feedback set status = 1, status_date = sysdate where id = ${id} """.execute
    id
  }
}
