package fi.liikennevirasto.digiroad2.service.feedback

import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}

class FeedbackServiceSpec extends FunSuite with Matchers {

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  val service = new FeedbackService(){
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }


  test("Create new feedback") {
    runWithRollback {
      val id = service.insertApplicationFeedback("feedback_createdBy", Some("Feedback body..."))
      val feedbacks = service.getFeedbacksByIds(Set(id)).head
      feedbacks.body should be (Some("Feedback body..."))
      feedbacks.createdBy should be (Some("feedback_createdBy"))
      feedbacks.id should be (id)
    }
  }


  test("Get all not sent feedbacks") {
    runWithRollback {
      val (id, id1, id2) =(
        service.insertApplicationFeedback("feedback_createdBy", Some("Feedback body...")),
        service.insertApplicationFeedback("feedback_createdBy1", Some("Feedback body 1...")),
        service.insertApplicationFeedback("feedback_createdBy2", Some("Feedback body 2...")))

      service.updateApplicationFeedbackStatus(id2)

      val feedbacks = service.getNotSentFeedbacks
      feedbacks.length should be (2)
      feedbacks.map(_.id) should contain (id)
      feedbacks.map(_.id) should contain (id1)
    }
  }


  test("Get all feedbacks") {
    runWithRollback {
      val (id, id1, id2) =(
        service.insertApplicationFeedback("feedback_createdBy", Some("Feedback body...")),
        service.insertApplicationFeedback("feedback_createdBy1", Some("Feedback body 1...")),
        service.insertApplicationFeedback("feedback_createdBy2", Some("Feedback body 2...")))

      val feedbacks = service.getAllFeedbacks
      feedbacks.length should be (3)
      feedbacks.map(_.id) should contain (id)
      feedbacks.map(_.id) should contain (id1)
      feedbacks.map(_.id) should contain (id2)

    }
  }


  test("Update feedback status") {
    runWithRollback {
      val (id, id1, id2) =(
        service.insertApplicationFeedback("feedback_createdBy", Some("Feedback body...")),
        service.insertApplicationFeedback("feedback_createdBy1", Some("Feedback body 1...")),
        service.insertApplicationFeedback("feedback_createdBy2", Some("Feedback body 2...")))

      service.updateApplicationFeedbackStatus(id1)
      service.updateApplicationFeedbackStatus(id2)

      val feedbacks = service.getNotSentFeedbacks
      feedbacks.length should be (1)
      feedbacks.map(_.id) should contain (id)
    }
  }

}
