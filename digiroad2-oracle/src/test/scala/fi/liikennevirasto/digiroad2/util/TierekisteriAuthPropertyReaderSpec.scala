package fi.liikennevirasto.digiroad2.util

import org.scalatest.{FunSuite, Matchers}


class TierekisteriAuthPropertyReaderSpec extends FunSuite with Matchers {
  val authtest= new TierekisteriAuthPropertyReader

  test("Basic64 authentication for TR client") {
val authenticate=authtest.getAuthinBase64()
    authenticate  should be ("dXNlclhZWjpwYXNzd29yZFhZWg==")
  }
}
