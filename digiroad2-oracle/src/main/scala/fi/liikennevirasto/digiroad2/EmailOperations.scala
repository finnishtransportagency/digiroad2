package fi.liikennevirasto.digiroad2

import java.util.Properties

import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, SmtpPropertyReader}
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeMessage}
import org.slf4j.LoggerFactory

case class Email( to: String, from: String, cc: Option[String], bcc: Option[String], subject: String, body: String)

class EmailOperations() {

  private val smtpProp = new SmtpPropertyReader

  private def isNumeric(str:String): Boolean = str.matches("[-+]?\\d+(\\.\\d+)?")
  val logger = LoggerFactory.getLogger(getClass)

  private def setEmailProperties(): Session = {
    if( (smtpProp.getHost.isEmpty  || smtpProp.getPort.isEmpty) || !isNumeric(smtpProp.getPort) )
      throw new IllegalArgumentException

    val properties = new Properties()
    properties.put("mail.transport.protocol", "smtp")
    properties.put("mail.smtp.host", smtpProp.getHost)
    properties.put("mail.smtp.port", smtpProp.getPort)
    properties.put("mail.smtp.starttls.enable", "true")
    properties.put("mail.smtp.starttls.required", "true")
    properties.put("mail.smtp.auth", "true")

    Session.getDefaultInstance(properties)
  }

  private def createMessage(email: Email, session: Session): Message = {
    val message =  new MimeMessage(session)
    message.setFrom(new InternetAddress(email.from))
    message.setSubject(email.subject)
    message.setHeader("Content-Type", "text/html; charset=UTF-8")
    message.setContent(email.body, "text/html; charset=UTF-8")
    setRecipients(message, email)
  }

  private def setRecipients(message: MimeMessage, email: Email): MimeMessage ={
    message.setRecipients(Message.RecipientType.TO, smtpProp.getDestination)
    email.cc match {
      case Some(cc)  => message.setRecipients(Message.RecipientType.CC, cc)
      case _ => None
    }

    email.bcc match {
      case Some(bcc) => message.setRecipients(Message.RecipientType.BCC, bcc)
      case _ => None
    }
    message
  }

  def sendEmail(email: Email): Boolean = {
    val message = createMessage(email, setEmailProperties())
    val sesUsername = Digiroad2Properties.sesUsername
    val sesPassword = Digiroad2Properties.sesPassword
    try {
      Transport.send(message, sesUsername, sesPassword)
      true
    }catch {
      case ex: MessagingException =>
        logger.error(s"Exception at send feedback: ${ex.getMessage}")
        false
    }
  }
}