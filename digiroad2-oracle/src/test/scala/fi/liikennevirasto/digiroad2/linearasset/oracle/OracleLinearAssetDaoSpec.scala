package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.Tag

import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

class OracleLinearAssetDaoSpec extends FunSuite with Matchers {
  test("splitting speed limit updates existing speed limit and creates a new one", Tag("db")) {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.splitSpeedLimit(700114, 5537, 50, "test")
      val (modifiedBy, _, _, _, _) = OracleLinearAssetDao.getSpeedLimitDetails(700114)
      modifiedBy shouldBe Some("test")
      dynamicSession.rollback()
    }
  }
}
