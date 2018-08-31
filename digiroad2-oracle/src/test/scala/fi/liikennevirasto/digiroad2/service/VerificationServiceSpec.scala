package fi.liikennevirasto.digiroad2.service

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.dao.{Queries, VerificationDao}
import fi.liikennevirasto.digiroad2.linearasset.TinyRoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import org.mockito.Mockito._

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
    override def withDynSession[T](f: => T): T = f
    override def dao: VerificationDao = new VerificationDao
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(dataSource)(test)

  test("get verification asset types info"){
    runWithRollback {
      val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id, 235, 30, (sysdate - interval '1' year), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+2, 235, 50, (sysdate - interval '2' year), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+3, 235, 60, sysdate, 'testuser')""".execute

      val verificationInfo = ServiceWithDao.getAssetTypesByMunicipality(235)
      verificationInfo should have size 25
      verificationInfo.filter(_.municipalityCode == 235) should have size 25
      verificationInfo.filter(_.verifiedBy.isDefined) should have size 3
      verificationInfo.find(_.assetTypeCode == 30).map(_.verified).head should be (true)
      verificationInfo.find(_.assetTypeCode == 50).map(_.verified).head should be (false)
      verificationInfo.find(_.assetTypeCode == 60).map(_.verified).head should be (true)
      verificationInfo.filter(info => Set(30,50,60).contains(info.assetTypeCode)).map(_.verifiedBy) should have size 3
      verificationInfo.filter(info => Set(30,50,60).contains(info.assetTypeCode)).map(_.verifiedBy).head should equal (Some("testuser"))
    }
  }
//TODO: The test belows contains a "23 months before" insert, can not be run on 31st day of the month.
//  test("get verification asset types info"){
//    runWithRollback {
//      val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
//      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
//           values ($id, 235, 30, (sysdate - interval '1' year), 'testuser')""".execute
//      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
//           values ($id+1, 235, 40, (sysdate - interval '23' month), 'testuser')""".execute
//      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
//           values ($id+2, 235, 50, (sysdate - interval '2' year), 'testuser')""".execute
//      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
//           values ($id+3, 235, 60, sysdate, 'testuser')""".execute
//
//      val verificationInfo = ServiceWithDao.getAssetTypesByMunicipality(235)
//      verificationInfo should have size 25
//      verificationInfo.filter(_.municipalityCode == 235) should have size 25
//      verificationInfo.filter(_.verifiedBy.isDefined) should have size 4
//      verificationInfo.find(_.assetTypeCode == 30).map(_.verified).head should be (true)
//      verificationInfo.find(_.assetTypeCode == 40).map(_.verified).head should be (true)
//      verificationInfo.find(_.assetTypeCode == 50).map(_.verified).head should be (false)
//      verificationInfo.find(_.assetTypeCode == 60).map(_.verified).head should be (true)
//      verificationInfo.filter(info => Set(30,40,50,60).contains(info.assetTypeCode)).map(_.verifiedBy) should have size 4
//      verificationInfo.filter(info => Set(30,40,50,60).contains(info.assetTypeCode)).map(_.verifiedBy).head should equal (Some("testuser"))
//    }
//  }


  test("get asset verification"){
    runWithRollback {
      ServiceWithDao.setAssetTypeVerification(235, Set(120), "testuser")
      val newVerification = ServiceWithDao.getAssetVerification(235, 120)
      newVerification should have size 1
      newVerification.head.verifiedBy should equal (Some("testuser"))
    }
  }
  test("get point asset verification and check if it has count"){
    runWithRollback {
      ServiceWithDao.setAssetTypeVerification(235, Set(10), "testuser")
      val newVerification = ServiceWithDao.getAssetVerification(235, 10)
      newVerification.head.counter should not be None
    }
  }

  test("get linear asset verification and check if doesn't have count "){
    runWithRollback {
      ServiceWithDao.setAssetTypeVerification(235, Set(120), "testuser")
      val newVerification = ServiceWithDao.getAssetVerification(235, 120)
      newVerification.head.counter should be (None)
    }
  }

  test("remove asset type verification") {
    runWithRollback {
      ServiceWithDao.setAssetTypeVerification(20, Set(100), "testuser")
      ServiceWithDao.removeAssetTypeVerification(20, Set(100), "testuser")
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

  test("not update with wrong asset type") {
    runWithRollback {
      val thrown = intercept[IllegalStateException] {
        ServiceWithDao.verifyAssetType(235, Set(150), "testuser")
      }
      thrown.getMessage should be("Asset type not allowed")
    }
  }

  test("not insert with wrong asset type") {
    runWithRollback {
      val thrown = intercept[IllegalStateException] {
        ServiceWithDao.setAssetTypeVerification(235, Set(150), "testuser")
      }
      thrown.getMessage should be("Asset type not allowed")
    }
  }

  test("get critical asset types info"){
    runWithRollback {
      val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id, 235, 10, (sysdate - interval '1' year), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+2, 235, 30, (sysdate - interval '2' year), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+3, 235, 190, sysdate, 'testuser')""".execute

      val verificationInfo = ServiceWithDao.getCriticalAssetTypesByMunicipality(235)
      verificationInfo should have size 5
      verificationInfo.filter(info => Set(10,30,190,380).contains(info.assetTypeCode)) should have size 4
      verificationInfo.filter(_.municipalityCode == 235) should have size 5
      verificationInfo.filter(_.verifiedBy.contains("testuser")) should have size 3
    }
  }
  //TODO: The test belows contains a "23 months before" insert, can not be run on 31st day of the month.

//  test("get critical asset types info"){
//    runWithRollback {
//      val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
//      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
//           values ($id, 235, 10, (sysdate - interval '1' year), 'testuser')""".execute
//      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
//           values ($id+1, 235, 20, (sysdate - interval '23' month), 'testuser')""".execute
//      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
//           values ($id+2, 235, 30, (sysdate - interval '2' year), 'testuser')""".execute
//      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
//           values ($id+3, 235, 190, sysdate, 'testuser')""".execute
//
//      val verificationInfo = ServiceWithDao.getCriticalAssetTypesByMunicipality(235)
//      verificationInfo should have size 5
//      verificationInfo.filter(info => Set(10,20,30,190,380).contains(info.assetTypeCode)) should have size 5
//      verificationInfo.filter(_.municipalityCode == 235) should have size 5
//      verificationInfo.filter(_.verifiedBy.contains("testuser")) should have size 4
//    }
//  }

  test("get assets Latests Modifications with one municipality") {
    runWithRollback {

      val tinyRoadLinkMunicipality235 = Seq( TinyRoadLink(1000),  TinyRoadLink(3000), TinyRoadLink(5000))
      when(mockRoadLinkService.getTinyRoadLinkFromVVH(235)).thenReturn(tinyRoadLinkMunicipality235)

      val latestModificationInfoMunicipality = ServiceWithDao.getAssetLatestModifications(Set(235))
      latestModificationInfoMunicipality should have size 3
      latestModificationInfoMunicipality.head.assetTypeCode should be(70)
      latestModificationInfoMunicipality.last.assetTypeCode should be(100)
    }
  }

  test("get assets Latests Modifications for Ely user with two municipalities"){
    runWithRollback {
      val tinyRoadLinkMunicipality100 = Seq( TinyRoadLink(2000), TinyRoadLink(4000), TinyRoadLink(6000))
      val tinyRoadLinkMunicipality235 = Seq( TinyRoadLink(1000),  TinyRoadLink(3000), TinyRoadLink(5000))

      when(mockRoadLinkService.getTinyRoadLinkFromVVH(235)).thenReturn(tinyRoadLinkMunicipality235)
      when(mockRoadLinkService.getTinyRoadLinkFromVVH(100)).thenReturn(tinyRoadLinkMunicipality100)

      val latestModificationInfo = ServiceWithDao.getAssetLatestModifications(Set(100, 235))
      latestModificationInfo should have size 4
      latestModificationInfo.head.assetTypeCode should be (90)
      latestModificationInfo.last.assetTypeCode should be (30)
    }
  }
}
