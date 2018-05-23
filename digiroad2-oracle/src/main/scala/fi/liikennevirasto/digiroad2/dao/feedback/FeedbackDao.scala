package fi.liikennevirasto.digiroad2.dao.feedback

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.service.feedback.FeedbackInfo
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

class FeedbackDao {

  implicit val getFeedback = new GetResult[FeedbackInfo] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val receiver = r.nextStringOption()
      val createdBy = r.nextStringOption()
      val createdAt = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val subject = r.nextStringOption()
      val body = r.nextStringOption()
      val status = r.nextBoolean()
      val statusDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      FeedbackInfo(id, receiver, createdBy, createdAt, body, subject, status, statusDate)
    }
  }

  def getApplicationFeedbackByStatus(status: Boolean): Seq[FeedbackInfo] = {
    val feedbackFilter = if (status) " status = 1 " else " status = 0 "
    val query =
      s"""
        select id, receiver, createdBy, createdAt, subject, body, status, statusDate
        from feedback
        where $feedbackFilter
        """
    StaticQuery.queryNA[FeedbackInfo](query).iterator.toSeq
  }

  def getAllFeedbacks(): Seq[FeedbackInfo] = {
    val query =  s"""
            select id, receiver, createdBy, createdAt, subject, body, status, statusDate
            from feedback
            """
      StaticQuery.queryNA[FeedbackInfo](query).iterator.toSeq
  }


  def getFeedbackByIds(ids: Set[Long]): Seq[FeedbackInfo] = {
      val idsToQuery = ids.mkString(",")
      val query =  s"""
            select f.id, f.receiver, f.createdBy, f.createdAt, f.subject, f.body, f.status, f.statusDate
            from feedback f
            where f.id in ($idsToQuery)"""
      StaticQuery.queryNA[FeedbackInfo](query).iterator.toSeq
  }


  def insertFeedback(receiver: Option[String], createdBy: Option[String], body: Option[String],
                     subject: Option[String], status: Boolean, statusDate: Option[DateTime]): Long = {
   val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
      sqlu"""
          insert into feedback (id, receiver, createdBy, createdAt, subject, body, status, statusDate)
          values ($id, ${receiver}, ${createdBy}, sysdate, ${subject},
                ${body},${status},${statusDate})""".execute
    id
  }

  def updateFeedback(id: Long): Long = {
    sqlu"""
          update feedback set status = 1, statusDate = sysdate where id = ${id} """.execute
    id
  }
}
