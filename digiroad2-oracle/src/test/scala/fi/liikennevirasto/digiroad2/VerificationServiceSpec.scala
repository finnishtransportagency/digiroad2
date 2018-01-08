package fi.liikennevirasto.digiroad2.util

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, RoadLinkService, VerificationService}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.verification.oracle.VerificationDao
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class VerificationServiceSpec extends FunSuite with Matchers {

  val mockVerificationService = MockitoSugar.mock[VerificationService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val verificationDao = new VerificationDao

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  object ServiceWithDao extends VerificationService(mockEventBus, mockRoadLinkService){
    override def withDynTransaction[T](f: => T): T = f
    override def dao: VerificationDao = new VerificationDao
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(dataSource)(test)

  test("get asset verification"){
    runWithRollback {
      ServiceWithDao.setAssetTypeVerification(235, Set(120), "testuser")
      val newVerification = ServiceWithDao.getAssetVerification(235, 120)
      newVerification should have size 1
      newVerification.head.verifiedBy should equal (Some("testuser"))
    }
  }

  test("remove asset type verification") {
    runWithRollback {
      ServiceWithDao.setAssetTypeVerification(20, Set(100), "testuser")
      ServiceWithDao.removeAssetTypeVerification(20, 100, "testuser")
      val verificationInfo = ServiceWithDao.getAssetVerification(20, 100)
      verificationInfo should have size 0
    }
  }

  test("update asset type verification") {
    runWithRollback {
      ServiceWithDao.setAssetTypeVerification(20, Set(100), "testuser")
      ServiceWithDao.verifyAssetType(20, Set(100), "updateuser")
      val newVerification = ServiceWithDao.getAssetVerification(20, 100)
      newVerification should have size 1
      newVerification.head.verifiedBy should be (Some("updateuser"))
    }
  }

  test("set verify an unverified asset type") {
    runWithRollback {
      val oldVerification = ServiceWithDao.getAssetVerification(20, 120)
      ServiceWithDao.verifyAssetType(20, Set(120), "testuser")
      val newVerification = ServiceWithDao.getAssetVerification(20, 120)
      oldVerification should have size 0
      newVerification should have size 1
      newVerification.head.verifiedBy should be (Some("testuser"))
    }
  }
}
