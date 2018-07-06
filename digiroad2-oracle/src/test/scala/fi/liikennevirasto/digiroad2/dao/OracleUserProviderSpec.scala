package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.user.Configuration
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import org.scalatest.{FunSuite, Matchers}

class OracleUserProviderSpec extends FunSuite with Matchers {
  val TestUserName = "Oracleuserprovidertest"
  val north = 1000
  val east = 3000
  val newNorth = 2000
  val newEast = 6000

  val provider = new OracleUserProvider

  test("create and get user") {
    executeStatement("DELETE FROM service_user WHERE username = '" + TestUserName.toLowerCase() + "'");
    provider.getUser(TestUserName) shouldBe (None)
    provider.createUser(TestUserName, Configuration(north = Some(1000)))
    val user = provider.getUser(TestUserName).get
    user.username should be (TestUserName.toLowerCase)
    user.configuration.north should be (Some(north))
  }

  test("update current user default map location") {
    executeStatement("DELETE FROM service_user WHERE username = '" + TestUserName.toLowerCase() + "'")
    provider.getUser(TestUserName) should be(None)
    provider.createUser(TestUserName, Configuration(east = Some(east), north = Some(north)))
    val user = provider.getUser(TestUserName).get

    val updatedUser = user.copy(configuration = user.configuration.copy(east = Some(newEast), north = Some(newNorth)))
    provider.updateUserConfiguration(updatedUser)
    val userInfo = provider.getUser(TestUserName).get
    userInfo.username should be(TestUserName.toLowerCase)
    userInfo.configuration.east should be(Some(newEast))
    userInfo.configuration.north should be(Some(newNorth))
  }
}