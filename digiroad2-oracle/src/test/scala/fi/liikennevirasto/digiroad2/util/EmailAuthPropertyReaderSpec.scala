package fi.liikennevirasto.digiroad2.util

import org.scalatest.{FunSuite, Matchers}

class EmailAuthPropertyReaderSpec extends FunSuite with Matchers {
  val reader = new EmailAuthPropertyReader

  test("Should get username and password to Email authentication ") {
    val username = reader.getUsername
    val password = reader.getPassword

    //TODO: Change when we have username and password
    username should be("emailUsername")
    password should be("emailPassword")
  }

}
