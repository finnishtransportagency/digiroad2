package fi.liikennevirasto.digiroad2.service.feedback

import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}

class FeedbackApplicationServiceSpec extends FunSuite with Matchers {

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  val service = new FeedbackApplicationService(){
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }


  test("Create new feedback") {
    runWithRollback {
      var feedbackbody = FeedbackApplicationBody(Some("bugi"), Some("feedback headline"), Some("Feedback free tex..."), Some("My name"), Some("My email"), None)

      val id = service.insertApplicationFeedback("feedback_createdBy", feedbackbody)
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
        service.insertApplicationFeedback("feedback_createdBy", feedbackbody),
        service.insertApplicationFeedback("feedback_createdBy1", feedbackbody1),
        service.insertApplicationFeedback("feedback_createdBy2", feedbackbody2))

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
        service.insertApplicationFeedback("feedback_createdBy", feedbackbody),
        service.insertApplicationFeedback("feedback_createdBy1", feedbackbody1),
        service.insertApplicationFeedback("feedback_createdBy2", feedbackbody2))

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
        service.insertApplicationFeedback("feedback_createdBy", feedbackbody),
        service.insertApplicationFeedback("feedback_createdBy1", feedbackbody1),
        service.insertApplicationFeedback("feedback_createdBy2", feedbackbody2))

      service.updateApplicationFeedbackStatus(id1)
      service.updateApplicationFeedbackStatus(id2)

      val feedbacks = service.getNotSentFeedbacks
      feedbacks.length should be (1)
      feedbacks.map(_.id) should contain (id)
    }
  }

}
