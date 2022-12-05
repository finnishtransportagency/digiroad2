package fi.liikennevirasto.digiroad2.service.feedback

import fi.liikennevirasto.digiroad2.dao.feedback.FeedBackError
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}

import java.nio.charset.StandardCharsets
import scala.util.Random

class FeedbackOperationsSpec extends FunSuite with Matchers {

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  val service = new FeedbackApplicationService(){
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }


  test("Create new feedback") {
    runWithRollback {
      var feedbackbody = FeedbackApplicationBody(Some("bugi"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)

      val id = service.insertFeedback("feedback_createdBy", feedbackbody)
      val feedbacks = service.getFeedbacksByIds(Set(id)).head
      feedbacks.createdBy should be (Some("feedback_createdBy"))
      feedbacks.id should be (id)
    }
  }


  test("Get all not sent feedbacks") {
    runWithRollback {
      var feedbackbody = FeedbackApplicationBody(Some("bugi"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)
      var feedbackbody1 = FeedbackApplicationBody(Some("Kehitysehdotus"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)
      var feedbackbody2 = FeedbackApplicationBody(Some("Vapaa palaute"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)

      val (id, id1, id2) =(
        service.insertFeedback("feedback_createdBy", feedbackbody),
        service.insertFeedback("feedback_createdBy1", feedbackbody1),
        service.insertFeedback("feedback_createdBy2", feedbackbody2))

      service.updateApplicationFeedbackStatus(id2)

      val feedbacks = service.getNotSentFeedbacks
      feedbacks.length should be (2)
      feedbacks.map(_.id) should contain (id)
      feedbacks.map(_.id) should contain (id1)
    }
  }


  test("Get all feedbacks") {
    runWithRollback {
      var feedbackbody = FeedbackApplicationBody(Some("bugi"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)
      var feedbackbody1 = FeedbackApplicationBody(Some("Kehitysehdotus"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)
      var feedbackbody2 = FeedbackApplicationBody(Some("Vapaa palaute"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)

      val (id, id1, id2) =(
        service.insertFeedback("feedback_createdBy", feedbackbody),
        service.insertFeedback("feedback_createdBy1", feedbackbody1),
        service.insertFeedback("feedback_createdBy2", feedbackbody2))

      val feedbacks = service.getAllFeedbacks
      feedbacks.length should be (3)
      feedbacks.map(_.id) should contain (id)
      feedbacks.map(_.id) should contain (id1)
      feedbacks.map(_.id) should contain (id2)

    }
  }


  test("Update feedback status") {
    runWithRollback {
      var feedbackbody = FeedbackApplicationBody(Some("bugi"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)
      var feedbackbody1 = FeedbackApplicationBody(Some("Kehitysehdotus"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)
      var feedbackbody2 = FeedbackApplicationBody(Some("Vapaa palaute"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)

      val (id, id1, id2) =(
        service.insertFeedback("feedback_createdBy", feedbackbody),
        service.insertFeedback("feedback_createdBy1", feedbackbody1),
        service.insertFeedback("feedback_createdBy2", feedbackbody2))

      service.updateApplicationFeedbackStatus(id1)
      service.updateApplicationFeedbackStatus(id2)

      val feedbacks = service.getNotSentFeedbacks
      feedbacks.length should be (1)
      feedbacks.map(_.id) should contain (id)
    }
  }

  test("insert too big feedback, should throw error") {
    runWithRollback {
      // source: https://stackoverflow.com/a/64086683/5398840 
      val rand = new Random()
      val Alphanumeric = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes
      def mkStr(chars: Array[Byte], length: Int): String = {
        val bytes = new Array[Byte](length)
        for (i <- 0 until length) bytes(i) = chars(rand.nextInt(chars.length))
        new String(bytes, StandardCharsets.US_ASCII)
      }
      
      def nextAlphanumeric(length: Int): String = mkStr(Alphanumeric, length)
      
      val messages = nextAlphanumeric(3704)
      
      val feedbackbody = FeedbackApplicationBody(Some("bugi"), Some("feedback headline"), Some(messages), Some("My name"), Some("My email"), None)

      val error = intercept[FeedBackError](service.insertFeedback("feedback_createdBy", feedbackbody))

      error.msg should be("Message was too big")
    }
  }

}
