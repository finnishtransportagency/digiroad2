package fi.liikennevirasto.digiroad2.dao

import java.time.LocalDate

import fi.liikennevirasto.digiroad2.user.Configuration
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import org.scalatest.{FunSuite, Matchers}

class OracleUserProviderSpec extends FunSuite with Matchers {
  val TestUserName = "Oracleuserprovidertest"
  val north = 1000
  val municipalityNumber = 235

  val provider = new OracleUserProvider

  test("create and get user") {
    executeStatement("DELETE FROM service_user WHERE username = '" + TestUserName.toLowerCase() + "'");
    provider.getUser(TestUserName) shouldBe (None)
    provider.createUser(TestUserName, Configuration(north = Some(1000)))
    val user = provider.getUser(TestUserName).get
    user.username should be (TestUserName.toLowerCase)
    user.configuration.north should be (Some(north))
  }

  test("update user last notification date field without update modified_date") {
    executeStatement("DELETE FROM service_user WHERE username = '" + TestUserName.toLowerCase() + "'")
    provider.getUser(TestUserName) should be (None)
    provider.createUser(TestUserName, Configuration(municipalityNumber = Some(municipalityNumber)))
    val user = provider.getUser(TestUserName).get

    val updatedUser = user.copy(configuration = user.configuration.copy(lastNotificationDate = Some(LocalDate.now.toString)))
    provider.updateUserConfiguration(updatedUser)
    val userInfo = provider.getUser(TestUserName).get
    userInfo.username should be (TestUserName.toLowerCase)
    userInfo.configuration.lastNotificationDate should be (Some(LocalDate.now.toString))
    userInfo.configuration.municipalityNumber should be (Some(municipalityNumber))
  }

  test("update user last login date field") {
    executeStatement("DELETE FROM service_user WHERE username = '" + TestUserName.toLowerCase() + "'")
    provider.getUser(TestUserName) should be(None)
    provider.createUser(TestUserName, Configuration(municipalityNumber = Some(municipalityNumber)))
    val user = provider.getUser(TestUserName).get

    val updatedUser = user.copy(configuration = user.configuration.copy(lastLoginDate = Some(LocalDate.now().toString())))
    provider.updateUserConfiguration(updatedUser)
    val userInfo = provider.getUser(TestUserName).get
    userInfo.username should be(TestUserName.toLowerCase)
    userInfo.configuration.lastLoginDate should be(Some(LocalDate.now.toString))
    userInfo.configuration.municipalityNumber should be(Some(municipalityNumber))
  }
}