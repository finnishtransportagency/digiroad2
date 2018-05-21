package fi.liikennevirasto.digiroad2

import org.scalatest.{FunSuite, Matchers}


case class Email( username:String, password: String ,to: String, from: String, cc: Option[String], bcc: Option[String], subject: String, body: String, smtpHost: String, smtpPort: String)


class EmailOperationsSpec extends FunSuite with Matchers {


  val operations = new EmailOperations()


  test("Will fail sending the email do to not having smtp port and host"){
    val email = Email("", "", "to@email.com", "from@email.com", None, None, "subject", "email body", "", "")

    intercept[IllegalArgumentException] {
      operations.sendEmail(email)
    }
  }
}
