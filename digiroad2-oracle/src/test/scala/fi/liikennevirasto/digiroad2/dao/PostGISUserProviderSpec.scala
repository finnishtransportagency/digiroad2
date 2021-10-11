package fi.liikennevirasto.digiroad2.dao

import java.time.LocalDate

import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.user.Configuration
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class PostGISUserProviderSpec extends FunSuite with Matchers {

    val testUserName = "PostGISuserprovidertest"
    val north = 1000
    val east = 3000
    val newNorth = 2000
    val newEast = 6000
    val municipalityNumber = 235

  val provider = new PostGISUserProvider

  test("create and get user") {
    PostGISDatabase.withDynTransaction {
      provider.getUser(testUserName, false) shouldBe (None)
      provider.createUser(testUserName, Configuration(north = Some(1000)), newTransaction = false)
      val user = provider.getUser(testUserName, false).get
      user.username should be(testUserName.toLowerCase)
      user.configuration.north should be(Some(north))
      dynamicSession.rollback()
    }
  }

    test("update user last notification date field without update modified_date") {
      PostGISDatabase.withDynTransaction {
     provider.getUser(testUserName, false) should be(None)
      provider.createUser(testUserName, Configuration(municipalityNumber = Some(municipalityNumber)), newTransaction = false)
      val user = provider.getUser(testUserName, false).get

      val updatedUser = user.copy(configuration = user.configuration.copy(lastNotificationDate = Some(LocalDate.now.toString)))
      provider.updateUserConfiguration(updatedUser, false)
      val userInfo = provider.getUser(testUserName, false).get
      userInfo.username should be(testUserName.toLowerCase)
      userInfo.configuration.lastNotificationDate should be(Some(LocalDate.now.toString))
      userInfo.configuration.municipalityNumber should be(Some(municipalityNumber))
        dynamicSession.rollback()
      }
    }

    test("update current user default map location") {
      PostGISDatabase.withDynTransaction {
      provider.getUser(testUserName, false) should be(None)
      provider.createUser(testUserName, Configuration(east = Some(east), north = Some(north)), newTransaction = false)
      val user = provider.getUser(testUserName, false).get

      val updatedUser = user.copy(configuration = user.configuration.copy(east = Some(newEast), north = Some(newNorth)))
      provider.updateUserConfiguration(updatedUser, false)
      val userInfo = provider.getUser(testUserName, false).get
      userInfo.username should be(testUserName.toLowerCase)
      userInfo.configuration.east should be(Some(newEast))
      userInfo.configuration.north should be(Some(newNorth))
        dynamicSession.rollback()
      }
    }

    test("update user last login date field") {
      PostGISDatabase.withDynTransaction {
      provider.getUser(testUserName, false) should be(None)
      provider.createUser(testUserName, Configuration(municipalityNumber = Some(municipalityNumber)), newTransaction = false)
      val user = provider.getUser(testUserName, false).get

      val updatedUser = user.copy(configuration = user.configuration.copy(lastLoginDate = Some(LocalDate.now().toString())))
      provider.updateUserConfiguration(updatedUser, false)
      val userInfo = provider.getUser(testUserName, false).get
      userInfo.username should be(testUserName.toLowerCase)
      userInfo.configuration.lastLoginDate should be(Some(LocalDate.now.toString))
      userInfo.configuration.municipalityNumber should be(Some(municipalityNumber))
        dynamicSession.rollback()
      }
    }
}