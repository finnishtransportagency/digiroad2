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

  test("remove asset type verification") {
    runWithRollback {
      ServiceWithDao.removeAssetTypeVerification(20, 100)
      ServiceWithDao.verifyAssetType(20, 100, "testuser")
      ServiceWithDao.removeAssetTypeVerification(20, 100)
      val newVerificationInfo = ServiceWithDao.getAssetVerification(20, 100)
      newVerificationInfo.head.verifiedBy should be (None)
      newVerificationInfo.head.verifiedDate should be (None)
    }
  }

  test("same method creates new record if does not exist from earlier, updates if exists"){
    runWithRollback{
      OracleDatabase.withDynTransaction {
        sqlu"""delete from municipality_verification where municipality_id = 235 and asset_type_id = 100""".execute
      }
      ServiceWithDao.verifyAssetType(235, 100, "creatingTestuser")
      val createdVerification = ServiceWithDao.getAssetVerification(235, 100)
      createdVerification.head.verifiedBy should equal (Some("creatingTestuser"))
      ServiceWithDao.verifyAssetType(235, 100, "updatingTestuser")
      val updatedVerification = ServiceWithDao.getAssetVerification(235, 100)
      updatedVerification.head.verifiedBy should be (Some("updatingTestuser"))
    }
  }

  test("get asset verification"){
    runWithRollback {
      ServiceWithDao.verifyAssetType(235, 120, "testuser")
      val oldVerificationInfo = ServiceWithDao.getAssetVerification(235, 120)
      oldVerificationInfo.head.verifiedBy should equal (Some("testuser"))
    }
  }
}
