package fi.liikennevirasto.digiroad2.service.feedback

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Email, EmailOperations}
import fi.liikennevirasto.digiroad2.dao.feedback.FeedbackDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import javax.mail.MessagingException
import org.joda.time.DateTime


case class ApplicationFeedbackInfo(id: Long, receiver: Option[String], createdBy: Option[String], createdAt: Option[DateTime], body: Option[String],
                        subject: Option[String], status: Boolean, statusDate: Option[DateTime])


class FeedbackService(/*eventbus: DigiroadEventBus*/) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def dao: FeedbackDao = new FeedbackDao
  def emailOperations = new EmailOperations

  private val to: String = "operaattori@digiroad.fi"
  private val from: String = "OTH Application Feedback"
  private val subject: String = "Palaute työkalusta"
  private val body: String = ""
  private val smtpHost: String = "smtp.mailtrap.io"
  private val smtpPort: String = "2525"


  def insertApplicationFeedback(receiver: Option[String], createdBy: Option[String], body: Option[String],
                                subject: Option[String], status: Boolean, statusDate: Option[DateTime]): Long = {
    withDynSession {
      dao.insertFeedback(receiver, createdBy, body, subject, status, statusDate)
    }
  }

  def updateApplicationFeedbackStatus(id: Long) : Long = {
    withDynSession {
      dao.updateFeedback(id)
    }
  }

  def getNotSentFeedbacks : Seq[ApplicationFeedbackInfo] = getApplicationFeedback()
  def getSentFeedbacks : Seq[ApplicationFeedbackInfo] = getApplicationFeedback(true)

  def getApplicationFeedback(status: Boolean = false): Seq[ApplicationFeedbackInfo] = {
    withDynSession {
      dao.getApplicationFeedbackByStatus(status)
    }
  }

  def getAllApplicationFeedbacks: Seq[ApplicationFeedbackInfo] = {
    withDynSession {
      dao.getAllApplicationFeedbacks()
    }
  }

  def getApplicationFeedbacksByIds(ids: Set[Long]): Seq[ApplicationFeedbackInfo] = {
    withDynSession {
      dao.getApplicationFeedbackByIds(ids)
    }
  }


  def sendFeedbacks(): Unit = {
    getNotSentFeedbacks.foreach{
      feedback =>
        try {
          emailOperations.sendEmail(Email(feedback.receiver.getOrElse(to), feedback.createdBy.getOrElse(from), None, None, feedback.subject.getOrElse(subject), feedback.body.getOrElse(body), smtpHost, smtpPort))
          updateApplicationFeedbackStatus(feedback.id)
        }catch {
          case messagingException: MessagingException=> println( s"Error on email sending: ${messagingException.toString}" )
        }
    }
  }
}


object testFeedback{

  def main(args:Array[String]) : Unit = {

      val service = new FeedbackService()
      val id =   service.insertApplicationFeedback(None, Some("feedback_test"), Some("Teste body..... subject is xpto"), None, false, None)

      val x = service.getApplicationFeedbacksByIds(Set(id))
     val not_sent = service.getNotSentFeedbacks
     service.sendFeedbacks()
  }
}
