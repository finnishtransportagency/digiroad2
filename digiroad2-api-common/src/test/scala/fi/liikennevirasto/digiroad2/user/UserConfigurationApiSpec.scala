package fi.liikennevirasto.digiroad2.user

import org.scalatest.{BeforeAndAfterAll, Tag}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import java.util.UUID

import fi.liikennevirasto.digiroad2.AuthenticatedApiSpec
import fi.liikennevirasto.digiroad2.dao.OracleUserProvider
import fi.liikennevirasto.digiroad2.util.TestTransactions

class UserConfigurationApiSpec extends AuthenticatedApiSpec {
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  protected implicit val jsonFormats: Formats = DefaultFormats

  val TestUsername = "Test" + UUID.randomUUID().toString
  val RealName = "John" + UUID.randomUUID().toString
  addServlet(classOf[UserConfigurationApi], "/userconfig/*")

  test("create user record", Tag("db")) {
    runWithRollback {
      val user = User(0, TestUsername, Configuration(authorizedMunicipalities = Set(1, 2, 3), roles = Set(Role.Operator, Role.Administrator)), Some(RealName))
      postJsonWithUserAuth("/userconfig/user", write(user)) {
        status should be(200)
        val u = parse(body).extract[User]
        u.id should not be 0
        u.username should be(TestUsername.toLowerCase)
        u.configuration.authorizedMunicipalities should contain only(1, 2, 3)
        u.configuration.roles should contain only(Role.Operator, Role.Administrator)
        u.name.get should be(RealName)
      }
      postJsonWithUserAuth("/userconfig/user", write(user)) {
        status should be(409)
      }
    }
  }

  test("get user data") {
    getWithUserAuth("/userconfig/user/municipality49") {
      status should be (200)
      val u = parse(body).extract[User]
      u.username should be ("municipality49")
      u.configuration.authorizedMunicipalities should contain only 49
      u.name.get should be ("Municipality Maintainer 49")
    }
    getWithUserAuth("/userconfig/user/nonexistent") {
      status should be (404)
    }
  }

  test("set authorized municipalities for user") {
    try {
      putJsonWithUserAuth("/userconfig/user/municipality49/municipalities", write(List(1, 2, 3, 4, 5, 49))) {
        status should be (200)
        getWithUserAuth("/userconfig/user/municipality49") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (1, 2, 3, 4, 5, 49)
        }
      }
    } finally {
      putJsonWithUserAuth("/userconfig/user/municipality49/municipalities", write(List(49))) {
        status should be (200)
        getWithUserAuth("/userconfig/user/municipality49") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only 49
        }
      }
    }
  }

  test("set roles for user") {
    runWithRollback {
      putJsonWithUserAuth("/userconfig/user/" + TestUsername + "/roles", write(List(Role.Operator, Role.Administrator))) {
        status should be(200)
        getWithUserAuth("/userconfig/user/" + TestUsername) {
          parse(body).extract[User].configuration.roles should contain only(Role.Operator, Role.Administrator)
        }
      }
    }
  }

  test("batch load users with municipalities") {
    val batchString =
      s"""
        municipality766_749; ; 4, 5, 6, 49, 235; Name from batch
        municipality49; ; 1, 2, 3, 49; Replaced name from batch
        newuser; ; 2, 3, 6; Another name from batch
        testEly; 0; ;
      """
    try {
      putJsonWithUserAuth("/userconfig/municipalitiesbatch", batchString, Map("Content-type" -> "text/plain")) {
        getWithUserAuth("/userconfig/user/municipality766_749") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (4, 5, 6, 49, 235)
          parse(body).extract[User].name.get should be ("Name from batch")
        }
        getWithUserAuth("/userconfig/user/municipality49") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (1, 2, 3, 49)
          parse(body).extract[User].name.get should be ("Replaced name from batch")
        }
        getWithUserAuth("/userconfig/user/newuser") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (2, 3, 6)
          parse(body).extract[User].name.get should be ("Another name from batch")

        }
        getWithUserAuth("/userconfig/user/testEly") {
          parse(body).extract[User].configuration.authorizedMunicipalities should contain only (35, 43, 60, 62, 65, 76, 170, 295, 318, 417, 438, 478, 736, 766, 771, 941)
        }
      }
    } finally {
      val provider = new OracleUserProvider
      provider.deleteUser("newuser")
      provider.deleteUser("testEly")
      putJsonWithUserAuth("/userconfig/user/municipality766_749/municipalities", write(List(766, 749)), Map("Content-type" -> "application/json")) {}
      putJsonWithUserAuth("/userconfig/user/municipality49/municipalities", write(List(49)), Map("Content-type" -> "application/json")) {}
      putJsonWithUserAuth("/userconfig/user/municipality766_749/name", write("Municipality Maintainer 766 749"), Map("Content-type" -> "application/json")) {}
      putJsonWithUserAuth("/userconfig/user/municipality49/name", write("Municipality Maintainer 49"), Map("Content-type" -> "application/json")) {}
    }
  }

  test("parse command line parameters") {
    val userConfiguration = new UserConfigurationApi()
    userConfiguration.parseInputToInts(Seq(" "), 0) shouldBe empty
    userConfiguration.parseInputToInts(Seq("1, 2,3,"), 0).get should contain only (1, 2, 3)
    userConfiguration.parseInputToInts(Seq("1, 2,3,"), 1) shouldEqual None
  }
}
