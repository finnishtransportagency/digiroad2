package fi.liikennevirasto.digiroad2.dao

import java.time.LocalDate

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.Configuration
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}

class OracleUserProviderSpec extends FunSuite with Matchers {
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

    val TestUserName = "Oracleuserprovidertest"
    val north = 1000
    val east = 3000
    val newNorth = 2000
    val newEast = 6000
    val municipalityNumber = 235

    test("create and get user") {
      runWithRollback {
        val provider = new OracleUserProvider
        provider.deleteUser(TestUserName)

      provider.getUser(TestUserName) shouldBe (None)
      provider.createUser(TestUserName, Configuration(north = Some(1000)))
      val user = provider.getUser(TestUserName).get
      user.username should be(TestUserName.toLowerCase)
      user.configuration.north should be(Some(north))
      }
    }

    test("update user last notification date field without update modified_date") {
      runWithRollback {
        val provider = new OracleUserProvider
        provider.deleteUser(TestUserName)

      provider.getUser(TestUserName) should be(None)
      provider.createUser(TestUserName, Configuration(municipalityNumber = Some(municipalityNumber)))
      val user = provider.getUser(TestUserName).get

      val updatedUser = user.copy(configuration = user.configuration.copy(lastNotificationDate = Some(LocalDate.now.toString)))
      provider.updateUserConfiguration(updatedUser)
      val userInfo = provider.getUser(TestUserName).get
      userInfo.username should be(TestUserName.toLowerCase)
      userInfo.configuration.lastNotificationDate should be(Some(LocalDate.now.toString))
      userInfo.configuration.municipalityNumber should be(Some(municipalityNumber))
      }
    }

    test("update current user default map location") {
      runWithRollback {
        val provider = new OracleUserProvider
        provider.deleteUser(TestUserName)

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


    test("update user last login date field") {
      runWithRollback {
        val provider = new OracleUserProvider
        provider.deleteUser(TestUserName)

      provider.getUser(TestUserName) should be(None)
      provider.createUser(TestUserName, Configuration(municipalityNumber = Some(municipalityNumber)))
      val user = provider.getUser(TestUserName).get

      val updatedUser = user.copy(configuration = user.configuration.copy(lastLoginDate = Some(LocalDate.now().toString())))
      provider.updateUserConfiguration(updatedUser)
      val userInfo = provider.getUser(TestUserName).get
      userInfo.username should be(TestUserName.toLowerCase)
      userInfo.configuration.lastLoginDate should be(Some(LocalDate.now.toString))
      userInfo.configuration.municipalityNumber should be(Some(municipalityNumber))
        provider.deleteUser(TestUserName)
      }
    }
}