package fi.liikennevirasto.digiroad2

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers

class DbConnectionSpec extends FunSuite with ShouldMatchers {
  test("Oracle dev server connection") {
    if (System.getProperty("digiroad2.nodatabase") != "true") {
      val conn = Digiroad2Context.connectionPool.getConnection
      conn should not be (null)
      conn.close
    }
  }
}