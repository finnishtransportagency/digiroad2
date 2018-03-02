package fi.liikennevirasto.digiroad2.user

import org.scalatest.{BeforeAndAfterAll, Tag}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import java.util.UUID

import fi.liikennevirasto.digiroad2.AuthenticatedApiSpec
import fi.liikennevirasto.digiroad2.dao.OracleUserProvider

class UserConfigurationApiSpec extends AuthenticatedApiSpec {
  protected implicit val jsonFormats: Formats = DefaultFormats

  val TestUsername = "Test" + UUID.randomUUID().toString
  addServlet(classOf[UserConfigurationApi], "/userconfig/*")

  test("create user record", Tag("db")) {
    val user = User(0, TestUsername, Configuration(authorizedMunicipalities = Set(1, 2, 3), roles = Set(Role.Operator, Role.Administrator)))
    postJsonWithUserAuth("/userconfig/user", write(user)) {
      status should be (200)
      val u = parse(body).extract[User]
      u.id should not be 0
      u.username should be (TestUsername.toLowerCase)
      u.configuration.authorizedMunicipalities should contain only (1, 2, 3)
      u.configuration.roles should contain only (Role.Operator, Role.Administrator)
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

  test("set roles for user") {
    putJsonWithUserAuth("/userconfig/user/" + TestUsername + "/roles", write(List(Role.Operator, Role.Administrator))) {
      status should be(200)
      getWithUserAuth("/userconfig/user/" + TestUsername) {
        parse(body).extract[User].configuration.roles should contain only(Role.Operator, Role.Administrator)
      }
    }
  }

  test("batch load users with municipalities") {
    val batchString =
      s"""
        test2; ; 4, 5, 6, 49, 235
        test49; ; 1, 2, 3, 49
        newuser; ; 2, 3, 6
        testEly; 0;
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
        getWithUserAuth("/userconfig/user/testEly") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (35, 43, 60, 62, 65, 76, 170, 295, 318, 417, 438, 478, 736, 766, 771, 941)
        }
      }
    } finally {
      val provider = new OracleUserProvider
      provider.deleteUser("newuser")
      provider.deleteUser("testEly")
      putJsonWithUserAuth("/userconfig/user/test2/municipalities", write(List(235, 49)), Map("Content-type" -> "application/json")) {}
      putJsonWithUserAuth("/userconfig/user/test49/municipalities", write(List(49)), Map("Content-type" -> "application/json")) {}
    }
  }

  test("parse command line parameters") {
    val userConfiguration = new UserConfigurationApi()
    userConfiguration.parseInputToInts(Seq(" "), 0) shouldBe empty
    userConfiguration.parseInputToInts(Seq("1, 2,3,"), 0).get should contain only (1, 2, 3)
    userConfiguration.parseInputToInts(Seq("1, 2,3,"), 1) shouldEqual None
  }
}
