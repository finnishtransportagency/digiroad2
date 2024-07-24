package fi.liikennevirasto.digiroad2.service.feedback

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.{Email, EmailOperations}
import fi.liikennevirasto.digiroad2.dao.feedback.FeedbackDao
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, SmtpPropertyReader}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class FeedbackInfo(id: Long, createdBy: Option[String], createdAt: Option[DateTime], body: Option[String],
                        subject: Option[String], status: Boolean, statusDate: Option[DateTime])

case class FeedbackApplicationBody(feedbackType: Option[String], headline: Option[String], freeText: Option[String], name: Option[String], email: Option[String], phoneNumber: Option[String])
case class FeedbackDataBody(linkId: Option[Seq[String]], assetId: Option[Seq[Long]], assetName: Option[String], feedbackDataType: Option[String], freeText: Option[String], name: Option[String], email: Option[String], phoneNumber: Option[String], typeId: Option[Int])

trait Feedback {

  val logger = LoggerFactory.getLogger(getClass)
  private val smtpProp = new SmtpPropertyReader
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def dao: FeedbackDao
  def emailOperations: EmailOperations
  def to: String = smtpProp.getDestination
  def from: String
  def subject: String
  def body: String
  type FeedbackBody

  def stringifyBody(username: String, body: FeedbackBody) : String

  def insertFeedback(username: String, body: FeedbackBody): Long = {
    val message = stringifyBody(username, body)
    withDynSession {
      dao.insertFeedback(username, message, subject, status = false)
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
        if (emailOperations.sendEmail(Email(to, from, None, None, feedback.subject.getOrElse(subject), feedback.body.getOrElse(body)))) {
          val id = updateApplicationFeedbackStatus(feedback.id)
          logger.info(s"Sent feedback with id $id")
        } else {
          logger.error(s"Something happened when sending the email")
        }
      }
    }
  }
}

class FeedbackApplicationService extends Feedback {

  def dao: FeedbackDao = new FeedbackDao
  def emailOperations = new EmailOperations
  type FeedbackBody = FeedbackApplicationBody

  override def from: String = Digiroad2Properties.emailFrom
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
}

class FeedbackDataService extends Feedback {

  def dao: FeedbackDao = new FeedbackDao
  def emailOperations = new EmailOperations
  type FeedbackBody = FeedbackDataBody

  override def from: String = Digiroad2Properties.emailFrom
  override def subject: String = "Aineistopalaute"
  override def body: String = ""
  def directLink: String = Digiroad2Properties.feedbackAssetsEndPoint

  override def stringifyBody(username: String, body: FeedbackBody): String = {
    val ids = body.assetId.getOrElse(Seq.empty[Long]).mkString(",")
    val linkIds = body.linkId.getOrElse(Seq.empty[String]).mkString(",")


    val url = body.typeId match {

      case Some(id) if id == Manoeuvres.typeId=>
        s"""<a href=$directLink#${AssetTypeInfo.apply(id).layerName}/$linkIds>#${AssetTypeInfo.apply(id).layerName}/$linkIds</a>"""

      case  Some(id)if id == SpeedLimitAsset.typeId=>{(body.assetId.flatMap(_.headOption), body.linkId.flatMap(_.headOption))} match {
        case (Some(assetId), _) => s"""<a href=$directLink#${AssetTypeInfo.apply(id).layerName.dropRight(1)}/$assetId>#${AssetTypeInfo.apply(id).layerName.dropRight(1)}/$assetId</a>"""
        case (None, Some(linkId)) => s"""<a href=$directLink#${AssetTypeInfo.apply(id).layerName.dropRight(1)}/linkId/$linkId>linkProperty/$linkId</a>"""
        case _ => ""
      }
      case Some(id) => {(body.assetId.flatMap(_.headOption), body.linkId.flatMap(_.headOption))} match {
        case (Some(assetId), _) => s"""<a href=$directLink#${AssetTypeInfo.apply(id).layerName}/$assetId>#${AssetTypeInfo.apply(id).layerName}/$assetId</a>"""
        case (None, Some(linkId)) => s"""<a href=$directLink#${AssetTypeInfo.apply(id).layerName}/linkId/$linkId>linkProperty/$linkId</a>"""
        case _ => ""
      }

      case _ => body.linkId.flatMap(_.headOption) match {
        case Some(linkId) => s"""<a href=$directLink#linkProperty/$linkId>linkProperty/$linkId</a>"""
        case _ => ""
      }
    }

    val idName = if(body.typeId.getOrElse(UnknownAssetTypeId.typeId) == MassTransitStopAsset.typeId) "Valtakunnallinen id" else "Kohteen id"

    s"""<br>
    <b>Linkin id: </b> $linkIds <br>
    <b>${idName}: </b> $ids <br>
    <b>Tietolaji: </b> ${body.assetName.getOrElse("-")} <br>
    <b>Palautteen tyyppi: </b> ${body.feedbackDataType.getOrElse("-")} <br>
    <b>K-tunnus: </b> $username <br>
    <b>Nimi: </b> ${body.name.getOrElse("-")} <br>
    <b>Sähköposti: </b>${body.email.getOrElse("-")} <br>
    <b>Puhelinnumero: </b>${body.phoneNumber.getOrElse("-")} <br>
    <b>Palautteelle: </b>${body.freeText.getOrElse("-")} <br>
    <b>URL: </b>$url"""
  }
}