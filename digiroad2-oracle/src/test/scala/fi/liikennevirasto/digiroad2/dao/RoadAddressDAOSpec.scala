package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import org.scalatest.{FunSuite, Matchers}

import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class RoadAddressDAOSpec extends FunSuite with Matchers {

  val roadAddressDAO = new RoadAddressDAO()

  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  test("LRM calculation on Road Address") {
    val towards = RoadAddress(1L, 1L, 1L, Track.RightSide, 5, 100, 110, None, None, 1L, 123L, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
    val against = RoadAddress(1L, 1L, 1L, Track.RightSide, 5, 100, 110, None, None, 1L, 123L, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
    towards.addressMValueToLRM(100L).get should be (1.5 +- .001)
    against.addressMValueToLRM(100L).get should be (11.4 +- .001)
    towards.addressMValueToLRM(110L).get should be (11.4 +- .001)
    against.addressMValueToLRM(110L).get should be (1.5 +- .001)
    towards.addressMValueToLRM(103L).get should be (4.470 +- .001)
    against.addressMValueToLRM(103L).get should be (8.430 +- .001)
    towards.addressMValueToLRM(99L)  should be (None)
    towards.addressMValueToLRM(111L) should be (None)
    against.addressMValueToLRM(99L)  should be (None)
    against.addressMValueToLRM(111L) should be (None)
  }

  test("testGetByLinkId") {
    runWithRollback {
      val sets = roadAddressDAO.getByLinkId(Set(5170942, 5170947))
      sets.size should be (2)
      sets.forall(_.floating == false) should be (true)
    }
  }
}
