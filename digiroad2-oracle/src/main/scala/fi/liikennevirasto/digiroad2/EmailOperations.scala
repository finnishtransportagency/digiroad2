package fi.liikennevirasto.digiroad2

import java.util.Properties
import fi.liikennevirasto.digiroad2.util.EmailAuthPropertyReader
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeMessage}

case class Email( to: String, from: String, cc: Option[String], bcc: Option[String], subject: String, body: String, smtpHost: String, smtpPort: String)

class EmailOperations() {

  protected val auth = new EmailAuthPropertyReader
  private def isNumeric(str:String): Boolean = str.matches("[-+]?\\d+(\\.\\d+)?")

  private def initEmail(smtpHost: String, smtpPort: String): MimeMessage = {
    if( (smtpHost.isEmpty  || smtpPort.isEmpty) || !isNumeric(smtpPort) )
      throw new IllegalArgumentException

    val properties = new Properties()
    properties.put("mail.smtp.host", smtpHost)
    properties.put("mail.smtp.port", smtpPort)

    val session = Session.getDefaultInstance(properties)
    new MimeMessage(session)
  }

  private def createMessage(email: Email): Message = {
    val message = initEmail(email.smtpHost, email.smtpPort)
    message.setSubject(email.subject)
    message.setText(email.body)
    message.setFrom(new InternetAddress(email.from))
    message.setRecipients(Message.RecipientType.TO, email.to)
    message
  }

  def sendEmail(email: Email): Unit = {
    val message = createMessage(email)
    Transport.send(message, auth.getUsername, auth.getPassword)
  }
}