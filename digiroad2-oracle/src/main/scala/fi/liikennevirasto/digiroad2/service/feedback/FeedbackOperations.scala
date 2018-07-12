package fi.liikennevirasto.digiroad2.service.feedback

import fi.liikennevirasto.digiroad2.{Email, EmailOperations}
import fi.liikennevirasto.digiroad2.dao.feedback.FeedbackDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.SmtpPropertyReader
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class FeedbackInfo(id: Long, receiver: Option[String], createdBy: Option[String], createdAt: Option[DateTime], body: Option[String],
                        subject: Option[String], status: Boolean, statusDate: Option[DateTime])

trait Feedback {

  val logger = LoggerFactory.getLogger(getClass)
  private val smtpProp = new SmtpPropertyReader
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def dao: FeedbackDao
  def emailOperations: EmailOperations
  def to: String = smtpProp.getDestination
  def from: String
  def subject: String
  def body: String
  type FeedbackBody

  def stringifyBody(username: String, body: FeedbackBody) : String
  def insertFeedback(username: String, body: FeedbackBody): Long

  def updateApplicationFeedbackStatus(id: Long) : Long = {
    withDynSession {
      dao.updateFeedback(id)
    }
  }

  def getNotSentFeedbacks : Seq[FeedbackInfo] = getFeedbackByStatus()
  def getSentFeedbacks : Seq[FeedbackInfo] = getFeedbackByStatus(true)

  def getFeedbackByStatus(status: Boolean = false): Seq[FeedbackInfo] = {
    withDynSession {
      dao.getFeedback(dao.byStatus(status))
    }
  }

  def getAllFeedbacks: Seq[FeedbackInfo] = {
    withDynSession {
      dao.getFeedback(dao.byAll())
    }
  }

  def getFeedbacksByIds(ids: Set[Long]): Seq[FeedbackInfo] = {
    withDynSession {
      dao.getFeedback(dao.byId(ids))
    }
  }

  def sendFeedbacks(): Unit = {
    getNotSentFeedbacks.foreach{
      feedback => {
        if (emailOperations.sendEmail(Email(feedback.receiver.getOrElse(to), from, None, None, feedback.subject.getOrElse(subject), feedback.body.getOrElse(body)))) {
          val id = updateApplicationFeedbackStatus(feedback.id)
          logger.info(s"Sent feedback with id $id")
        } else {
          logger.error(s"Something happened when sending the email")
        }
      }
    }
  }
}
