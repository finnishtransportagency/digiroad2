package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.dao.{Sequences, VerificationDao}
import fi.liikennevirasto.digiroad2.linearasset.TinyRoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
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

  object ServiceWithDao extends VerificationService(mockEventBus, mockRoadLinkService){
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override def dao: VerificationDao = new VerificationDao
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)


  test("get last linear asset modification") {
    runWithRollback {
      val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
      val tinyRoadLinkMunicipality90 = Seq(TinyRoadLink(linkId1), TinyRoadLink(linkId2), TinyRoadLink(linkId3))
      when(mockRoadLinkService.getTinyRoadLinksByMunicipality(90)).thenReturn(tinyRoadLinkMunicipality90)

      val assetId = Sequences.nextPrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values ($assetId ,190,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH , 'testuser_Old', (current_timestamp - interval '5' day ))""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId, $linkId1, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values ($assetId, $lrmPositionId)""".execute

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values (($assetId + 1),190,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH , 'testuser_new', (current_timestamp - interval '3' day ))""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId +1, $linkId2, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values (($assetId + 1), $lrmPositionId + 1)""".execute

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE) values (($assetId + 2),100,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH)""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId + 2, $linkId2, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values (($assetId + 2), $lrmPositionId + 2)""".execute

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values (($assetId + 3),100,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH, 'testuser', (current_timestamp - interval '1' day ))""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId + 3, $linkId3, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values (($assetId + 3), $lrmPositionId + 3)""".execute

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY, CREATED_DATE) values (($assetId + 4),210,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH)""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId + 4, $linkId2, 0, 100, 1)""".execute
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
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values ($assetId ,220, 235,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH, 'testuser_Old', (current_timestamp - interval '5' day ))""".execute
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values (($assetId + 1),220, 235,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH, 'testuser_new', (current_timestamp - interval '3' day ))""".execute
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE) values (($assetId + 2),230, 235,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH)""".execute
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE, MODIFIED_BY, MODIFIED_DATE) values (($assetId + 3),230, 235,'dr2_test_data', current_timestamp-INTERVAL'1' MONTH, 'testuser', (current_timestamp - interval '1' day ))""".execute
      sqlu"""insert into ASSET (ID, ASSET_TYPE_ID, MUNICIPALITY_CODE, CREATED_BY, CREATED_DATE) values (($assetId + 4),240, 235, 'dr2_test_data', current_timestamp-INTERVAL'1' MONTH)""".execute

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
      val id = sql"""select nextval('primary_key_seq')""".as[Long].first
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id, 235, 10, (current_timestamp - interval '1' year), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+1, 235, 20, current_timestamp-INTERVAL'23' MONTH, 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+2, 235, 30, (current_timestamp - interval '2' year), 'testuser')""".execute
      sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id+3, 235, 190, current_timestamp, 'testuser')""".execute

      val verificationInfo = ServiceWithDao.getCriticalAssetTypesByMunicipality(235)
      verificationInfo should have size 8
      verificationInfo.filter(info => Set(10,20,30,190,380).contains(info.assetTypeCode)) should have size 4
      verificationInfo.filter(_.municipalityCode == 235) should have size 8
      verificationInfo.filter(_.verifiedBy.contains("testuser")) should have size 3
    }
  }

  test("get assets Latests Modifications with one municipality") {
    runWithRollback {
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('da2a39b3-82fc-48f1-a1f9-0e63ca09d454:1', 235)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('76e9fdef-d743-416d-b1d8-1529e98cbb21:3', 235)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('ae2ffef1-353d-4c82-8246-108bb89809e1:5', 235)""".execute
      val municipalities = Set(235)
      val latestModificationInfoMunicipality = ServiceWithDao.dao.getModifiedAssetTypes(municipalities)
      latestModificationInfoMunicipality should have size 3
      latestModificationInfoMunicipality.head.assetTypeCode should be(70)
      latestModificationInfoMunicipality.last.assetTypeCode should be(100)
    }
  }

  test("get assets Latests Modifications for Ely user with two municipalities"){
    runWithRollback {
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('703cdc6d-61f4-4e40-9da8-0efc51e9943d:2', 100)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('c9f4c80d-a0c8-4ede-977b-9b048cd7099c:4', 100)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('4e339ea7-0d1c-4b83-ac34-d8cfeebe4066:6', 100)""".execute

      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('da2a39b3-82fc-48f1-a1f9-0e63ca09d454:1', 235)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('76e9fdef-d743-416d-b1d8-1529e98cbb21:3', 235)""".execute
      sqlu"""insert into kgv_roadlink (linkId, municipalitycode) values ('ae2ffef1-353d-4c82-8246-108bb89809e1:5', 235)""".execute
      
      val municipalities = Set(100, 235)
      val latestModificationInfo = ServiceWithDao.dao.getModifiedAssetTypes(municipalities)
      latestModificationInfo should have size 4
      latestModificationInfo.head.assetTypeCode should be (90)
      latestModificationInfo.last.assetTypeCode should be (30)
    }
  }

  test("get assets latest modifications with one municipality") {
    runWithRollback {
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (235, 70, 'testUser1', current_timestamp)""".execute
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (235, 100, 'testUser2', current_timestamp)""".execute
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (749, 80, 'testUser1', current_timestamp)""".execute
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (749, 200, 'testUser3', current_timestamp)""".execute

      val latestModificationInfo = ServiceWithDao.getAssetsLatestModifications(Set(235))
      latestModificationInfo should have size 2

      latestModificationInfo.map(_.assetTypeCode).min should be (70)
      latestModificationInfo.map(_.assetTypeCode).max should be (100)

      latestModificationInfo.filter(_.assetTypeCode == 70).head.modifiedBy should be (Some("testUser1"))
      latestModificationInfo.filter(_.assetTypeCode == 100).head.modifiedBy should be (Some("testUser2"))
    }
  }

  test("get assets latest modifications for Ely user with two municipalities") {
    runWithRollback {
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (235, 70, 'testUser1', current_timestamp)""".execute
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (235, 100, 'testUser2', current_timestamp)""".execute
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (749, 80, 'testUser1', current_timestamp)""".execute
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (749, 200, 'testUser3', current_timestamp)""".execute
      sqlu"""insert into dashboard_info (municipality_id, asset_type_id, modified_by, last_modified_date)
      values (766, 200, 'testUser4', current_timestamp)""".execute

      val latestModificationInfo = ServiceWithDao.getAssetsLatestModifications(Set(235, 749))
      latestModificationInfo should have size 4

      latestModificationInfo.map(_.assetTypeCode).min should be (70)
      latestModificationInfo.map(_.assetTypeCode).max should be (200)

      latestModificationInfo.filter(_.assetTypeCode == 70).head.modifiedBy should be (Some("testUser1"))
      latestModificationInfo.filter(_.assetTypeCode == 100).head.modifiedBy should be (Some("testUser2"))
      latestModificationInfo.filter(_.assetTypeCode == 80).head.modifiedBy should be (Some("testUser1"))
      latestModificationInfo.filter(_.assetTypeCode == 200).head.modifiedBy should be (Some("testUser3"))
    }
  }
}
