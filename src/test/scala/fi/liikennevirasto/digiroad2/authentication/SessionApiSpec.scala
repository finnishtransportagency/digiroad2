package fi.liikennevirasto.digiroad2.authentication

import org.scalatest.{Tag, FunSuite}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import java.util.UUID

class SessionApiSpec extends FunSuite with ScalatraSuite  {
  protected implicit val jsonFormats: Formats = DefaultFormats

  addServlet(classOf[SessionApi], "/auth/*")

  test("create new user", Tag("db")) {
    val username = "test" + UUID.randomUUID().toString
    post("/auth/user", Map("username" -> username, "password" -> "testpass", "passwordConfirm" -> "testpass", "email" -> (username + "@example.com"))) {
      status should be (200)
    }
    post("/auth/user", Map("username" -> username, "password" -> "testpass", "passwordConfirm" -> "testpass", "email" -> (username + "@example.com"))) {
      status should be (400)
    }
  }

  test("validate password", Tag("db")) {
    val username = "test" + UUID.randomUUID().toString
    post("/auth/user", Map("username" -> username, "password" -> "testpass", "passwordConfirm" -> "otherpass", "email" -> (username + "@example.com"))) {
      status should be (400)
    }
    post("/auth/user", Map("username" -> username, "password" -> "", "passwordConfirm" -> "", "email" -> (username + "@example.com"))) {
      status should be (400)
    }
  }

  test("validate email", Tag("db")) {
    val username = "test" + UUID.randomUUID().toString
    post("/auth/user", Map("username" -> username, "password" -> "testpass", "passwordConfirm" -> "testpass", "email" -> "notvalidemail")) {
      status should be (400)
    }
  }
}
