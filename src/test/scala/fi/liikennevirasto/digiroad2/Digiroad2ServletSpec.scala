package fi.liikennevirasto.digiroad2

import org.scalatra.test.scalatest._
import org.scalatest.FunSuite

class Digiroad2ApiTests extends ScalatraSuite with FunSuite {
  addServlet(classOf[Digiroad2Api], "/*")

  test("ping") {
    get("/ping") {
      status should equal (200)
      body should equal ("pong")
    }
  }
}