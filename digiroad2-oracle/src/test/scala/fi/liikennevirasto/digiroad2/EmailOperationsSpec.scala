package fi.liikennevirasto.digiroad2

import org.scalatest.{FunSuite, Matchers}

class EmailOperationsSpec extends FunSuite with Matchers {

  val operations = new EmailOperations()

  test("Should throw exception - port and host empty"){
    val email = Email("to@email.com", "from@email.com", None, None, "subject", "email body", "", "")

    intercept[IllegalArgumentException] {
      operations.sendEmail(email)
    }
  }

  test("Should throw exception - port is not a number"){
    val email = Email("to@email.com", "from@email.com", None, None, "subject", "email body", "host", "port")

    intercept[IllegalArgumentException] {
      operations.sendEmail(email)
    }
  }
}
