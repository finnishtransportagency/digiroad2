package fi.liikennevirasto.digiroad2

import org.scalatest.{FunSuite, Matchers}

class EmailOperationsSpec extends FunSuite with Matchers {

  val operations = new EmailOperations()
  val emailWithoutHostAndPort = Email("to@email.com", "from@email.com", None, None, "subject", "email body", "", "")
  val emailWithPortInvalid = Email("to@email.com", "from@email.com", None, None, "subject", "email body", "host", "port")

  test("Should throw exception - port and host empty"){
    intercept[IllegalArgumentException] {
      operations.sendEmail(emailWithoutHostAndPort)
    }
  }

  test("Should throw exception - port is not a number"){
    intercept[IllegalArgumentException] {
      operations.sendEmail(emailWithPortInvalid)
    }
}
}
