package fi.liikennevirasto.digiroad2.service.feedback

import fi.liikennevirasto.digiroad2.{Email, EmailOperations}
import fi.liikennevirasto.digiroad2.dao.feedback.FeedbackDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import javax.mail.MessagingException
import org.joda.time.DateTime


case class FeedbackInfo(id: Long, receiver: Option[String], createdBy: Option[String], createdAt: Option[DateTime], body: Option[String],
                        subject: Option[String], status: Boolean, statusDate: Option[DateTime])


trait Feedback {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def dao: FeedbackDao
  def emailOperations: EmailOperations
  def to: String
  def from: String
  def subject: String
  def body: String
  def smtpHost: String
  def smtpPort: String

  def insertApplicationFeedback(receiver: Option[String], createdBy: Option[String], body: Option[String], subject: Option[String], status: Boolean, statusDate: Option[DateTime]): Long = {
    withDynSession {
      dao.insertFeedback(receiver, createdBy, body, subject, status, statusDate)
    }
  }

  def updateApplicationFeedbackStatus(id: Long) : Long = {
    withDynSession {
      dao.updateFeedback(id)
    }
  }

  def getNotSentFeedbacks : Seq[FeedbackInfo] = getFeedbackByStatus()
  def getSentFeedbacks : Seq[FeedbackInfo] = getFeedbackByStatus(true)

  def getFeedbackByStatus(status: Boolean = false): Seq[FeedbackInfo] = {
    withDynSession {
      dao.getApplicationFeedbackByStatus(status)
    }
  }

  def getAllFeedbacks: Seq[FeedbackInfo] = {
    withDynSession {
      dao.getAllApplicationFeedbacks()
    }
  }

  def getFeedbacksByIds(ids: Set[Long]): Seq[FeedbackInfo] = {
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

class FeedbackService extends Feedback {

  def dao: FeedbackDao = new FeedbackDao
  def emailOperations = new EmailOperations

  override def to: String = "operaattori@digiroad.fi"
  override def from: String =  "OTH Application Feedback"
  override def subject: String = "Palaute työkalusta"
  override def body: String = ""
  override def smtpHost: String = "smtp.mailtrap.io"
  override def smtpPort: String = "2525"

}
//
//object testFeedback{
//
//  def main(args:Array[String]) : Unit = {
//    val service = new FeedbackService()
//   /* val id = */  service.insertApplicationFeedback(None, Some("feedback_test"), Some("Teste body..... subject is xpto........1"), None, false, None)
//    service.insertApplicationFeedback(None, Some("feedback_test_2"), Some("Teste body..... subject is xpto........2"), None, false, None)
//    service.insertApplicationFeedback(None, Some("feedback_test_3"), Some("Teste body..... subject is xpto........3"), None, false, None)
//    service.insertApplicationFeedback(None, Some("feedback_test_4"), Some("Teste body..... subject is xpto........4"), None, false, None)
//    service.insertApplicationFeedback(None, Some("feedback_test_5"), Some("Teste body..... subject is xpto........5"), None, false, None)
//  /*  val x = service.getFeedbacksByIds(Set(id))
//    val not_sent = service.getNotSentFeedbacks
//    service.sendFeedbacks()*/
//  }
//}
