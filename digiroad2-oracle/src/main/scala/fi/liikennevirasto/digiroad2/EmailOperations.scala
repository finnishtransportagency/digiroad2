package fi.liikennevirasto.digiroad2

import java.util.Properties

import javax.mail._
import javax.mail.internet.{InternetAddress, MimeMessage}

case class Email( username:String, password: String, to: String, from: String, cc: Option[String], bcc: Option[String], subject: String, body: String, smtpHost: String, smtpPort: String)

class EmailOperations() {

  private def initEmail(smtpHost: String, smtpPort: String): MimeMessage = {

    if(smtpHost.isEmpty || smtpPort.isEmpty)
      throw new IllegalArgumentException

    val properties = new Properties()
    properties.put("mail.smtp.host", smtpHost)
    properties.put("mail.smtp.prot", smtpPort)

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
    try{
      Transport.send(message, email.username, email.password)
      //TODO: update database table feedback
    }catch {
      case messagingException: MessagingException=>
        println(s"Error on email sending: ${messagingException.toString}" )
    }
  }
}


object MyMail{

  def main(args:Array[String]) : Unit = {
    val email = Email("975630ac706d80", "a49899bd37cd5c", "operaattori@digiroad", "OTH_application_Feedback", None, None, "Palaute työkalusta",
                      "Message body, should be sent on email body, and can have 4000 characters...", "smtp.mailtrap.io", "2525")
    new EmailOperations().sendEmail(email)

  }
}