package fi.liikennevirasto.digiroad2.util

import org.scalatest.{FunSuite, Matchers}

class TierekisteriAuthPropertyReaderSpec extends FunSuite with Matchers {
  val reader = new TierekisteriAuthPropertyReader

  test("Basic64 authentication for TR client") {
    val authenticate = reader.getAuthInBase64
    authenticate should be ("dHJyZXN0b3RoOmxva2FrdXUyMDE2dGllcmVraXN0ZXJp")
  }

  test("Old Basic64 authentication for TR client") {
    val authenticate = reader.getOldAuthInBase64
    authenticate should be ("dXNlclhZWjpwYXNzd29yZFhZWg==")
  }

}
