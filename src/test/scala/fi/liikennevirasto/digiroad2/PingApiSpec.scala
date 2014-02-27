package fi.liikennevirasto.digiroad2

import org.scalatest.FunSuite
import org.scalatra.test.scalatest.ScalatraSuite
import fi.liikennevirasto.digiroad2.authentication.SessionApi

trait PingApiSpec extends FunSuite with ScalatraSuite {
  addServlet(classOf[Pin], "/auth/*")

  test("create new user") {
    val username = "test" + UUID.randomUUID().toString
    post("/auth/user", Map("username" -> username, "password" -> "testpass", "passwordConfirm" -> "testpass", "email" -> (username + "@example.com"))) {
      status should be (200)
    }
    post("/auth/user", Map("username" -> username, "password" -> "testpass", "passwordConfirm" -> "testpass", "email" -> (username + "@example.com"))) {
      status should be (400)
    }
  }}
