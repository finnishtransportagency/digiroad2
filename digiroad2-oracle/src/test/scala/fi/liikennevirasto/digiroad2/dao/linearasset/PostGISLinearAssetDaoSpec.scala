package fi.liikennevirasto.digiroad2.dao.linearasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Weekday
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers, Tag}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.client.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.client.RoadLinkFetched
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import slick.jdbc.StaticQuery.interpolation

class PostGISLinearAssetDaoSpec extends FunSuite with Matchers {
  val linkId: String = LinkIdGenerator.generateRandom()
  val roadLink = RoadLinkFetched(linkId, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.BothDirections, AllOthers)
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val dao = new PostGISLinearAssetDao()
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  def setupTestProhibition(linkId: String,
                           prohibitionValues: Set[ProhibitionValue]): Unit = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

    sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) values ($assetId,190,'dr2_test_data')""".execute
    sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId, $linkId, 0, 100, 1)""".execute
    sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values ($assetId, $lrmPositionId)""".execute

    prohibitionValues.foreach { prohibition =>
      val prohibitionId = Sequences.nextPrimaryKeySeqValue
      val prohibitionType = prohibition.typeId
      val prohibitionAddInfo = prohibition.additionalInfo
      sqlu"""insert into PROHIBITION_VALUE (ID, ASSET_ID, TYPE, ADDITIONAL_INFO) values ($prohibitionId, $assetId, $prohibitionType, $prohibitionAddInfo)""".execute

      prohibition.validityPeriods.map { validityPeriod =>
        val validityId = Sequences.nextPrimaryKeySeqValue
        val startHour = validityPeriod.startHour
        val endHour = validityPeriod.endHour
        val daysOfWeek = validityPeriod.days.value
        sqlu"""insert into PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR)
               values ($validityId, $prohibitionId, $daysOfWeek, $startHour, $endHour)""".execute
      }
      prohibition.exceptions.foreach { exceptionType =>
        val exceptionId = Sequences.nextPrimaryKeySeqValue
        sqlu""" insert into PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) values ($exceptionId, $prohibitionId, $exceptionType)""".execute
      }
    }
  }

  test("fetch simple prohibition without validity periods or exceptions") {
    val linkId = LinkIdGenerator.generateRandom()
    val fixtureProhibitionValues = Set(ProhibitionValue(typeId = 10, validityPeriods = Set.empty, exceptions = Set.empty, ""))

    runWithRollback {
      setupTestProhibition(linkId, fixtureProhibitionValues)

      val persistedAssets = dao.fetchProhibitionsByLinkIds(190, Seq(linkId))

      persistedAssets.size should be(1)
      persistedAssets.head.linkId should be(linkId)

      val fetchedProhibitionValues = persistedAssets.head.value.get.asInstanceOf[Prohibitions].prohibitions.toSet
      fetchedProhibitionValues should equal(fixtureProhibitionValues)
    }
  }

  test("fetch prohibition with validity period") {
    val linkId = LinkIdGenerator.generateRandom()
    val fixtureProhibitionValues = Set(ProhibitionValue(typeId = 10, Set(ValidityPeriod(12, 16, Weekday)), exceptions = Set.empty, ""))

    runWithRollback {
      setupTestProhibition(linkId, fixtureProhibitionValues)

      val persistedAssets = dao.fetchProhibitionsByLinkIds(190, Seq(linkId))

      persistedAssets.size should be(1)
      persistedAssets.head.linkId should be(linkId)

      val fetchedProhibitionValues = persistedAssets.head.value.get.asInstanceOf[Prohibitions].prohibitions.toSet
      fetchedProhibitionValues should equal(fixtureProhibitionValues)
    }
  }

  test("fetch prohibition with validity period and exceptions") {
    val linkId = LinkIdGenerator.generateRandom()
    val fixtureProhibitionValues = Set(
      ProhibitionValue(typeId = 10, Set(ValidityPeriod(12, 16, Weekday)), exceptions = Set(1, 2, 3), ""))

    runWithRollback {
      setupTestProhibition(linkId, fixtureProhibitionValues)

      val persistedAssets = dao.fetchProhibitionsByLinkIds(190, Seq(linkId))

      persistedAssets.size should be(1)
      persistedAssets.head.linkId should be(linkId)

      val fetchedProhibitionValues = persistedAssets.head.value.get.asInstanceOf[Prohibitions].prohibitions.toSet
      fetchedProhibitionValues should equal(fixtureProhibitionValues)
    }
  }

  test("fetch prohibition with validity period, exceptions and additional information") {
    val linkId = LinkIdGenerator.generateRandom()
    val fixtureProhibitionValues = Set(
      ProhibitionValue(typeId = 10, Set(ValidityPeriod(12, 16, Weekday)), exceptions = Set(1, 2, 3), "test value string"))

    runWithRollback {
      setupTestProhibition(linkId, fixtureProhibitionValues)

      val persistedAssets = dao.fetchProhibitionsByLinkIds(190, Seq(linkId))

      persistedAssets.size should be(1)
      persistedAssets.head.linkId should be(linkId)

      val fetchedProhibitionValues = persistedAssets.head.value.get.asInstanceOf[Prohibitions].prohibitions.toSet
      fetchedProhibitionValues should equal(fixtureProhibitionValues)
    }
  }

  test("fetch multiple prohibitions") {
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()
    val fixtureProhibitionValues1 = Set(
      ProhibitionValue(typeId = 10, Set(
        ValidityPeriod(12, 16, Weekday), ValidityPeriod(19, 21, Weekday)), exceptions = Set(1, 2, 3), additionalInfo = ""),
      ProhibitionValue(typeId = 9, validityPeriods = Set.empty, exceptions = Set(1, 2), additionalInfo = ""))
    val fixtureProhibitionValues2 = Set(ProhibitionValue(typeId = 3, validityPeriods = Set.empty, exceptions = Set.empty, additionalInfo = ""))
    val fixtureProhibitionValues3 = Set(ProhibitionValue(typeId = 10, validityPeriods = Set.empty, exceptions = Set(1), additionalInfo = ""))
    val fixtureProhibitionValues4 = Set(ProhibitionValue(typeId = 10, Set(ValidityPeriod(12, 16, Weekday)), exceptions = Set.empty, additionalInfo = ""))
    val fixtureProhibitionValues5 = Set(ProhibitionValue(typeId = 10, Set(ValidityPeriod(9, 19, Weekday)), exceptions = Set(1), additionalInfo = "Value Test"))

    runWithRollback {
      setupTestProhibition(linkId1, fixtureProhibitionValues1)
      setupTestProhibition(linkId2, fixtureProhibitionValues2)
      setupTestProhibition(linkId3, fixtureProhibitionValues3)
      setupTestProhibition(linkId4, fixtureProhibitionValues4)
      setupTestProhibition(linkId5, fixtureProhibitionValues5)

      val persistedAssets = dao.fetchProhibitionsByLinkIds(190, Seq(linkId1, linkId2, linkId3, linkId4, linkId5))
      persistedAssets.size should be(5)

      val persistedAsset1 = persistedAssets.find(_.linkId == linkId1)
      persistedAsset1 should not be None
      persistedAsset1.get.value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues1)
      val persistedAsset2 = persistedAssets.find(_.linkId == linkId2)
      persistedAsset2 should not be None
      persistedAsset2.get.value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues2)
      val persistedAsset3 = persistedAssets.find(_.linkId == linkId3)
      persistedAsset3 should not be None
      persistedAsset3.get.value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues3)
      val persistedAsset4 = persistedAssets.find(_.linkId == linkId4)
      persistedAsset4 should not be None
      persistedAsset4.get.value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues4)
      val persistedAsset5 = persistedAssets.find(_.linkId == linkId5)
      persistedAsset5 should not be None
      persistedAsset5.get.value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues5)
    }
  }

  test("all entities should be expired") {
    val typeId = 110
    val linkId = LinkIdGenerator.generateRandom()
    runWithRollback {

      val assetId = Sequences.nextPrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) values ($assetId,$typeId,'dr2_test_data')""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId, $linkId, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values ($assetId, $lrmPositionId)""".execute

      val counting = sql"""select count(*) from asset where asset_type_id = $typeId and (valid_to > current_timestamp or valid_to is null)""".as[Int].firstOption
      counting.get should be > 0
      dao.expireAllAssetsByTypeId(typeId)
      val countingAfterExpire = sql"""select count(*) from asset where asset_type_id = $typeId and (valid_to > current_timestamp or valid_to is null)""".as[Int].firstOption
      countingAfterExpire.get should be (0)
    }
  }
}
