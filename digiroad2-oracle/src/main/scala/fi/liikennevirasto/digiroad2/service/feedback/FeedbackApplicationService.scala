package fi.liikennevirasto.digiroad2.service.feedback

import fi.liikennevirasto.digiroad2.EmailOperations
import fi.liikennevirasto.digiroad2.dao.feedback.FeedbackDao

case class FeedbackApplicationBody(feedbackType: Option[String], headline: Option[String], freeText: Option[String], name: Option[String], email: Option[String], phoneNumber: Option[String])

class FeedbackApplicationService extends Feedback {

  def dao: FeedbackDao = new FeedbackDao
  def emailOperations = new EmailOperations
  type FeedbackBody = FeedbackApplicationBody

  override def from: String = "oth-feedback@no-reply.com"
  override def subject: String = "Palaute työkalusta"
  override def body: String = ""

  override def stringifyBody(username: String, body: FeedbackBody): String = {
    s"""<br>
    <b>Palautteen tyyppi: </b> ${body.feedbackType.getOrElse("-")} <br>
    <b>Palautteen otsikko: </b> ${body.headline.getOrElse("-")} <br>
    <b>K-tunnus: </b> $username <br>
    <b>Nimi: </b> ${body.name.getOrElse("-")} <br>
    <b>Sähköposti: </b>${body.email.getOrElse("-")} <br>
    <b>Puhelinnumero: </b>${body.phoneNumber.getOrElse("-")} <br>
    <b>Vapaa tekstikenttä palautteelle: </b>${body.freeText.getOrElse("-")}
   """
  }

  override def insertFeedback(username: String, body: FeedbackBody): Long = {
    val message = stringifyBody(username, body)
    withDynSession {
      dao.insertFeedback(to, username, message, subject, status = false)
    }
  }
}

