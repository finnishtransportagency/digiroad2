package fi.liikennevirasto.digiroad2.service

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences, VerificationDao}
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


  test("get last linear asset modification") {
    runWithRollback {
      val tinyRoadLinkMunicipality90 = Seq(TinyRoadLink(1100), TinyRoadLink(3100), TinyRoadLink(5100))
      when(mockRoadLinkService.getTinyRoadLinkFromVVH(90)).thenReturn(tinyRoadLinkMunicipality90)

      val assetId = Sequences.nextPrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values ($assetId ,190,'dr2_test_data', (sysdate - interval '1' month ), 'testuser_Old', (sysdate - interval '5' day ))""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId, 1100, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values ($assetId, $lrmPositionId)""".execute

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values (($assetId + 1),190,'dr2_test_data', (sysdate - interval '1' month ), 'testuser_new', (sysdate - interval '3' day ))""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId +1, 3100, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values (($assetId + 1), $lrmPositionId + 1)""".execute

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE) values (($assetId + 2),100,'dr2_test_data', (sysdate - interval '1' month ))""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId + 2, 3100, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values (($assetId + 2), $lrmPositionId + 2)""".execute

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values (($assetId + 3),100,'dr2_test_data', (sysdate - interval '1' month ), 'testuser', (sysdate - interval '1' day ))""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId + 3, 5100, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values (($assetId + 3), $lrmPositionId + 3)""".execute

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE) values (($assetId + 4),210,'dr2_test_data', (sysdate - interval '1' month ))""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId + 4, 3100, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values (($assetId + 4), $lrmPositionId + 4)""".execute

      val lastModification = ServiceWithDao.getLastModificationLinearAssets(tinyRoadLinkMunicipality90.map(_.linkId))

      lastModification should have size 3
      lastModification.find(_.assetTypeCode == 190).get.modifiedBy should be(Some("testuser_new"))
      lastModification.find(_.assetTypeCode == 100).get.modifiedBy should be(Some("testuser"))
      lastModification.exists(_.assetTypeCode == 210) should be (true)
    }
  }

  test("get last point asset modification") {
    runWithRollback {
      val assetId = Sequences.nextPrimaryKeySeqValue
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values ($assetId ,220, 235,'dr2_test_data', (sysdate - interval '1' month ), 'testuser_Old', (sysdate - interval '5' day ))""".execute
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values (($assetId + 1),220, 235,'dr2_test_data', (sysdate - interval '1' month ), 'testuser_new', (sysdate - interval '3' day ))""".execute
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE) values (($assetId + 2),230, 235,'dr2_test_data', (sysdate - interval '1' month ))""".execute
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values (($assetId + 3),230, 235,'dr2_test_data', (sysdate - interval '1' month ), 'testuser', (sysdate - interval '1' day ))""".execute
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE) values (($assetId + 4),240, 235, 'dr2_test_data', (sysdate - interval '1' month ))""".execute

      val lastModification = ServiceWithDao.getLastModificationPointAssets(235)

      lastModification should have size 2
      lastModification.find(_.assetTypeCode == 220).get.modifiedBy should be(Some("testuser_new"))
      lastModification.find(_.assetTypeCode == 230).get.modifiedBy should be(Some("testuser"))
    }
  }

  test("set verify an unverified asset type") {
    runWithRollback {
      ServiceWithDao.insertAssetTypeVerification(20, 120, None, None, None, 0, None, "")
      val oldVerification = ServiceWithDao.getAssetVerification(20, 120)
      ServiceWithDao.verifyAssetType(20, Set(120), "testuser")
      val newVerification = ServiceWithDao.getAssetVerification(20, 120)
      oldVerification should have size 1
      oldVerification.head.verified should be (false)
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

  test("get critical asset types info"){
    runWithRollback {
      val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id, 235, 10, (sysdate - interval '1' year), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+1, 235, 20, add_months(sysdate, -23), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+2, 235, 30, (sysdate - interval '2' year), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+3, 235, 190, sysdate, 'testuser')""".execute

      val verificationInfo = ServiceWithDao.getCriticalAssetTypesByMunicipality(235)
      verificationInfo should have size 5
      verificationInfo.filter(info => Set(10,20,30,190,380).contains(info.assetTypeCode)) should have size 5
      verificationInfo.filter(_.municipalityCode == 235) should have size 5
      verificationInfo.filter(_.verifiedBy.contains("testuser")) should have size 4
    }
  }

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
