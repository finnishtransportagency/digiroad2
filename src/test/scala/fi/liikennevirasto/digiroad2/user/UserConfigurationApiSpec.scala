package fi.liikennevirasto.digiroad2.user

import org.scalatest.{BeforeAndAfterAll, Tag}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import java.util.UUID
import fi.liikennevirasto.digiroad2.AuthenticatedApiSpec
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider

class UserConfigurationApiSpec extends AuthenticatedApiSpec {
  protected implicit val jsonFormats: Formats = DefaultFormats

  val TestUsername = "Test" + UUID.randomUUID().toString
  addServlet(classOf[UserConfigurationApi], "/userconfig/*")

  test("create user record", Tag("db")) {
    val user = User(0, TestUsername, Configuration(authorizedMunicipalities = Set(1, 2, 3)))
    postJsonWithUserAuth("/userconfig/user", write(user)) {
      status should be (200)
      val u = parse(body).extract[User]
      u.id should not be 0
      u.username should be (TestUsername.toLowerCase)
      u.configuration.authorizedMunicipalities should contain only (1, 2, 3)
    }
    postJsonWithUserAuth("/userconfig/user", write(user)) {
      status should be (409)
    }
  }

  test("get user data") {
    getWithUserAuth("/userconfig/user/test49") {
      status should be (200)
      val u = parse(body).extract[User]
      u.username should be ("test49")
      u.configuration.authorizedMunicipalities should contain only 49
    }
    getWithUserAuth("/userconfig/user/nonexistent") {
      status should be (404)
    }
  }

  test("set authorized municipalities for user") {
    try {
      putJsonWithUserAuth("/userconfig/user/test49/municipalities", write(List(1, 2, 3, 4, 5, 49))) {
        status should be (200)
        getWithUserAuth("/userconfig/user/test49") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (1, 2, 3, 4, 5, 49)
        }
      }
    } finally {
      putJsonWithUserAuth("/userconfig/user/test49/municipalities", write(List(49))) {
        status should be (200)
        getWithUserAuth("/userconfig/user/test49") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only 49
        }
      }
    }
  }

  test("batch load users with municipalities") {
    val batchString =
      s"""
        test2, 4, 5, 6, 49, 235
        test49, 1, 2, 3, 49
        newuser, 2, 3, 6
      """
    try {
      putJsonWithUserAuth("/userconfig/municipalitiesbatch", batchString, Map("Content-type" -> "text/plain")) {
        getWithUserAuth("/userconfig/user/test2") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (4, 5, 6, 49, 235)
        }
        getWithUserAuth("/userconfig/user/test49") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (1, 2, 3, 49)
        }
        getWithUserAuth("/userconfig/user/newuser") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (2, 3, 6)
        }
      }
    } finally {
      val provider = new OracleUserProvider
      provider.deleteUser("newuser")
      putJsonWithUserAuth("/userconfig/user/test2/municipalities", write(List(235, 49)), Map("Content-type" -> "application/json")) {}
      putJsonWithUserAuth("/userconfig/user/test49/municipalities", write(List(49)), Map("Content-type" -> "application/json")) {}
    }
  }
}
