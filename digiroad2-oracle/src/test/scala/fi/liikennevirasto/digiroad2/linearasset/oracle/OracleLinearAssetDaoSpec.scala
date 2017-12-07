package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Weekday
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.{FunSuite, Matchers, Tag}
import org.scalatest.mock.MockitoSugar
import org.scalatest._
import Matchers._
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient, VVHRoadlink}
import slick.jdbc.StaticQuery.interpolation

class OracleLinearAssetDaoSpec extends FunSuite with Matchers {
  val roadLink = VVHRoadlink(388562360, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.BothDirections, AllOthers)
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  private def daoWithRoadLinks(roadLinks: Seq[VVHRoadlink]): OracleLinearAssetDao = {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByLinkIds(roadLinks.map(_.linkId).toSet))
      .thenReturn(roadLinks)

    when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(roadLinks.map(_.linkId).toSet))
      .thenReturn(roadLinks)

    roadLinks.foreach { roadLink =>
      when(mockVVHRoadLinkClient.fetchByLinkId(roadLink.linkId)).thenReturn(Some(roadLink))
    }

    new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  private def truncateLinkGeometry(linkId: Long, startMeasure: Double, endMeasure: Double, vvhClient: VVHClient): Seq[Point] = {
    val geometry = vvhClient.roadLinkData.fetchByLinkId(linkId).get.geometry
    GeometryUtils.truncateGeometry3D(geometry, startMeasure, endMeasure)
  }

  def assertSpeedLimitEndPointsOnLink(speedLimitId: Long, linkId: Long, startMeasure: Double, endMeasure: Double, dao: OracleLinearAssetDao) = {
    val expectedEndPoints = GeometryUtils.geometryEndpoints(truncateLinkGeometry(linkId, startMeasure, endMeasure, dao.vvhClient).toList)
    val limitEndPoints = GeometryUtils.geometryEndpoints(dao.getLinksWithLengthFromVVH(20, speedLimitId).find { link => link._1 == linkId }.get._3)
    expectedEndPoints._1.distance2DTo(limitEndPoints._1) should be(0.0 +- 0.01)
    expectedEndPoints._2.distance2DTo(limitEndPoints._2) should be(0.0 +- 0.01)
  }

  def passingMunicipalityValidation(code: Int): Unit = {}
  
  def failingMunicipalityValidation(code: Int): Unit = { throw new IllegalArgumentException }

  private def simulateQuery[T](f: => T): T = {
    val result = f
    sqlu"""delete from temp_id""".execute
    result
  }

  test("Split should fail when user is not authorized for municipality") {
    runWithRollback {
      val dao = daoWithRoadLinks(List(roadLink))
      intercept[IllegalArgumentException] {
        dao.splitSpeedLimit(200097, 100, 120, "test", failingMunicipalityValidation)
      }
    }
  }

  test("splitting one link speed limit " +
    "where split measure is after link middle point " +
    "modifies end measure of existing speed limit " +
    "and creates new speed limit for second split", Tag("db")) {
    runWithRollback {
      val dao = daoWithRoadLinks(List(roadLink))
      val createdId = dao.splitSpeedLimit(200097, 100, 120, "test", passingMunicipalityValidation)
      val (existingModifiedBy, _, _, _, _) = dao.getSpeedLimitDetails(200097)
      val (_, _, newCreatedBy, _, _) = dao.getSpeedLimitDetails(createdId)

      assertSpeedLimitEndPointsOnLink(200097, 388562360, 0, 100, dao)
      assertSpeedLimitEndPointsOnLink(createdId, 388562360, 100, 136.788, dao)

      existingModifiedBy shouldBe Some("test")
      newCreatedBy shouldBe Some("test")
    }
  }

  test("splitting one link speed limit " +
    "where split measure is before link middle point " +
    "modifies start measure of existing speed limit " +
    "and creates new speed limit for first split", Tag("db")) {
    runWithRollback {
      val dao = daoWithRoadLinks(List(roadLink))
      val createdId = dao.splitSpeedLimit(200097, 50, 120, "test", passingMunicipalityValidation)
      val (modifiedBy, _, _, _, _) = dao.getSpeedLimitDetails(200097)
      val (_, _, newCreatedBy, _, _) = dao.getSpeedLimitDetails(createdId)

      assertSpeedLimitEndPointsOnLink(200097, 388562360, 50, 136.788, dao)
      assertSpeedLimitEndPointsOnLink(createdId, 388562360, 0, 50, dao)

      modifiedBy shouldBe Some("test")
      newCreatedBy shouldBe Some("test")
    }
  }

  test("can update speedlimit value") {
    runWithRollback {
      val dao = daoWithRoadLinks(List(roadLink))
      dao.updateSpeedLimitValue(200097, 60, "test", _ => ())
      dao.getSpeedLimitDetails(200097)._5 should equal(Some(60))
      dao.updateSpeedLimitValue(200097, 100, "test", _ => ())
      dao.getSpeedLimitDetails(200097)._5 should equal(Some(100))
    }
  }

  test("filter out floating speed limits") {
    runWithRollback {
      val roadLinks = Seq(
        RoadLink(1610980, List(Point(0.0, 0.0), Point(40.0, 0.0)), 40.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1610951, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
      val dao = new OracleLinearAssetDao(MockitoSugar.mock[VVHClient], MockitoSugar.mock[RoadLinkService])
      dao.floatLinearAssets(Set(300100, 300101))
      val (speedLimits, _) = dao.getSpeedLimitLinksByRoadLinks(roadLinks)
      speedLimits.map(_.id) should equal(Seq(200352))
    }
  }

  test("filter out disallowed link types") {
    runWithRollback {
      val roadLinks = Seq(
        RoadLink(1611552, List(Point(0.0, 0.0), Point(40.0, 0.0)), 40.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, PedestrianZone, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, CableFerry, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, UnknownLinkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      )
      val dao = new OracleLinearAssetDao(MockitoSugar.mock[VVHClient], MockitoSugar.mock[RoadLinkService])

      val speedLimits = dao.getSpeedLimitLinksByRoadLinks(roadLinks)

      speedLimits._1.map(_.id) should equal(Seq(300103))
    }
  }

  test("filter out disallowed functional classes") {
    runWithRollback {
      val roadLinks = Seq(
        RoadLink(1611552, List(Point(0.0, 0.0), Point(40.0, 0.0)), 40.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 7, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 8, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      )
      val dao = new OracleLinearAssetDao(MockitoSugar.mock[VVHClient], MockitoSugar.mock[RoadLinkService])

      val speedLimits = dao.getSpeedLimitLinksByRoadLinks(roadLinks)

      speedLimits._1.map(_.id) should equal(Seq(300103))
    }
  }

  test("speed limit creation fails if speed limit is already defined on link segment") {
    runWithRollback {
      val roadLink = VVHRoadlink(123, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(123)).thenReturn(Some(roadLink))
      val dao = daoWithRoadLinks(List(roadLink))
      val id = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.BothDirections, 40, 0, _ => ())
      }
      id shouldBe defined
      val id2 = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.BothDirections, 40, 0, _ => ())
      }
      id2 shouldBe None
    }
  }

  test("speed limit creation succeeds when speed limit is already defined on segment iff speed limits have opposing sidecodes") {
    runWithRollback {
      val roadLink = VVHRoadlink(123, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(123)).thenReturn(Some(roadLink))
      val dao = daoWithRoadLinks(List(roadLink))
      val id = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.TowardsDigitizing, 40, 0, _ => ())
      }
      id shouldBe defined
      val id2 = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.AgainstDigitizing, 40, 0, _ => ())
      }
      id2 shouldBe defined
      val id3 = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.BothDirections, 40, 0, _ => ())
      }
      id3 shouldBe None
    }
  }

  test("speed limit purge removes fully covered link from unknown speed limit list") {
    runWithRollback {
      val linkId = 1611389
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (link_id, municipality_code, administrative_class) values ($linkId, 235, 1)""".execute
      val dao = daoWithRoadLinks(Nil)
      dao.purgeFromUnknownSpeedLimits(linkId, 59.934)
      sql"""select link_id from unknown_speed_limit where link_id = $linkId""".as[Long].firstOption should be(None)
    }
  }

  test("speed limit purge does not remove partially covered link from unknown speed limit list") {
    runWithRollback {
      val linkId = 1611397
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (link_id, municipality_code, administrative_class) values ($linkId, 235, 1)""".execute
      val roadLink = VVHRoadlink(linkId, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val dao = daoWithRoadLinks(List(roadLink))

      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(linkId)).thenReturn(Some(roadLink))
      dao.createSpeedLimit("test", linkId, Measures(11.0, 16.0), SideCode.BothDirections, 40, 0, _ => ())
      dao.purgeFromUnknownSpeedLimits(linkId, 84.121)
      sql"""select link_id from unknown_speed_limit where link_id = $linkId""".as[Long].firstOption should be(Some(linkId))

      dao.createSpeedLimit("test", linkId, Measures(20.0, 54.0), SideCode.BothDirections, 40, 0, _ => ())
      dao.purgeFromUnknownSpeedLimits(linkId, 84.121)
      sql"""select link_id from unknown_speed_limit where link_id = $linkId""".as[Long].firstOption should be(None)
    }
  }

  test("unknown speed limits can be filtered by municipality") {
    runWithRollback {
      val linkId = 1
      val linkId2 = 2
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (link_id, municipality_code, administrative_class) values ($linkId, 235, 1)""".execute
      sqlu"""insert into unknown_speed_limit (link_id, municipality_code, administrative_class) values ($linkId2, 49, 1)""".execute

      val roadLink = VVHRoadlink(linkId, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val roadLink2 = VVHRoadlink(linkId2, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val dao = daoWithRoadLinks(List(roadLink, roadLink2))

      val allSpeedLimits = dao.getUnknownSpeedLimits(None)
      allSpeedLimits("Kauniainen")("State").asInstanceOf[Seq[Long]].length should be(1)
      allSpeedLimits("Espoo")("State").asInstanceOf[Seq[Long]].length should be(1)

      val kauniainenSpeedLimits = dao.getUnknownSpeedLimits(Some(Set(235)))
      kauniainenSpeedLimits("Kauniainen")("State").asInstanceOf[Seq[Long]].length should be(1)
      kauniainenSpeedLimits.keySet.contains("Espoo") should be(false)
    }
  }

  def setupTestProhibition(linkId: Long,
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
    val dao = new OracleLinearAssetDao(null, null)
    val linkId = 1l
    val fixtureProhibitionValues = Set(ProhibitionValue(typeId = 10, validityPeriods = Set.empty, exceptions = Set.empty, null))

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
    val dao = new OracleLinearAssetDao(null, null)
    val linkId = 1l
    val fixtureProhibitionValues = Set(ProhibitionValue(typeId = 10, Set(ValidityPeriod(12, 16, Weekday)), exceptions = Set.empty, null))

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
    val dao = new OracleLinearAssetDao(null, null)
    val linkId = 1l
    val fixtureProhibitionValues = Set(
      ProhibitionValue(typeId = 10, Set(ValidityPeriod(12, 16, Weekday)), exceptions = Set(1, 2, 3), null))

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
    val dao = new OracleLinearAssetDao(null, null)
    val linkId = 1l
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
    val dao = new OracleLinearAssetDao(null, null)
    val linkId1 = 1l
    val linkId2 = 2l
    val linkId3 = 3l
    val linkId4 = 4l
    val linkId5 = 5l
    val fixtureProhibitionValues1 = Set(
      ProhibitionValue(typeId = 10, Set(
        ValidityPeriod(12, 16, Weekday), ValidityPeriod(19, 21, Weekday)), exceptions = Set(1, 2, 3), additionalInfo = null),
      ProhibitionValue(typeId = 9, validityPeriods = Set.empty, exceptions = Set(1, 2), additionalInfo = null))
    val fixtureProhibitionValues2 = Set(ProhibitionValue(typeId = 3, validityPeriods = Set.empty, exceptions = Set.empty, additionalInfo = null))
    val fixtureProhibitionValues3 = Set(ProhibitionValue(typeId = 10, validityPeriods = Set.empty, exceptions = Set(1), additionalInfo = null))
    val fixtureProhibitionValues4 = Set(ProhibitionValue(typeId = 10, Set(ValidityPeriod(12, 16, Weekday)), exceptions = Set.empty, additionalInfo = null))
    val fixtureProhibitionValues5 = Set(ProhibitionValue(typeId = 10, Set(ValidityPeriod(9, 19, Weekday)), exceptions = Set(1), additionalInfo = "Value Test"))

    runWithRollback {
      setupTestProhibition(linkId1, fixtureProhibitionValues1)
      setupTestProhibition(linkId2, fixtureProhibitionValues2)
      setupTestProhibition(linkId3, fixtureProhibitionValues3)
      setupTestProhibition(linkId4, fixtureProhibitionValues4)
      setupTestProhibition(linkId5, fixtureProhibitionValues5)

      val persistedAssets = dao.fetchProhibitionsByLinkIds(190, Seq(linkId1, linkId2, linkId3, linkId4, linkId5))

      val sortedPersistedAssets = persistedAssets.sortBy(_.linkId)
      sortedPersistedAssets.size should be(5)
      sortedPersistedAssets(0).linkId should be(linkId1)
      sortedPersistedAssets(0).value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues1)
      sortedPersistedAssets(1).linkId should be(linkId2)
      sortedPersistedAssets(1).value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues2)
      sortedPersistedAssets(2).linkId should be(linkId3)
      sortedPersistedAssets(2).value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues3)
      sortedPersistedAssets(3).linkId should be(linkId4)
      sortedPersistedAssets(3).value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues4)
      sortedPersistedAssets(4).linkId should be(linkId5)
      sortedPersistedAssets(4).value.get.asInstanceOf[Prohibitions].prohibitions.toSet should equal(fixtureProhibitionValues5)
    }
  }

  test("speed limit mass query") {
    val ids = Seq.range(1L, 500L).toSet
    val dao = new OracleLinearAssetDao(null, null)
    runWithRollback {
      dao.getCurrentSpeedLimitsByLinkIds(Option(ids))
    }
  }

  test("speed limit mass query gives correct amount of results") {
    val ids = Seq.range(1610929L, 1611759L).toSet // in test fixture this contains > 400 speed limits
    val dao = new OracleLinearAssetDao(null, null)
    ids.size >= dao.MassQueryThreshold should be (true) // This should be high number enough
    runWithRollback {
      val count = dao.getCurrentSpeedLimitsByLinkIds(Option(ids)).size
      val grouped = ids.grouped(dao.MassQueryThreshold - 1).toSet
      grouped.size > 1 should be (true)
      val checked = grouped.map(i => dao.getCurrentSpeedLimitsByLinkIds(Option(i)).count(s => true)).sum
      count should be (checked) // number using mass query should be equal to those queried without using mass query
    }
  }

  test("speed limit no mass query") {
    val ids = Seq.range(1L, 2L).toSet
    val dao = new OracleLinearAssetDao(null, null)
    runWithRollback {
      dao.getCurrentSpeedLimitsByLinkIds(Option(ids))
    }
  }

  test("speed limit empty set must not crash") {
    val ids = Set():Set[Long]
    val dao = new OracleLinearAssetDao(null, null)
    runWithRollback {
      dao.getCurrentSpeedLimitsByLinkIds(Option(ids))
    }
  }

  test("speed limit no set must not crash") {
    val dao = new OracleLinearAssetDao(null, null)
    runWithRollback {
      dao.getCurrentSpeedLimitsByLinkIds(None)
    }
  }

  test("all entities should be expired") {
    val dao = new OracleLinearAssetDao(null, null)
    val typeId = 110
    val linkId = 99999
    runWithRollback {

      val assetId = Sequences.nextPrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

      sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) values ($assetId,$typeId,'dr2_test_data')""".execute
      sqlu"""insert into LRM_POSITION (ID,LINK_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId, $linkId, 0, 100, 1)""".execute
      sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values ($assetId, $lrmPositionId)""".execute

      val counting = sql"""select count(*) from asset where asset_type_id = $typeId and (valid_to >= sysdate or valid_to is null)""".as[Int].firstOption
      counting.get should be > 0
      dao.expireAllAssetsByTypeId(typeId)
      val countingAfterExpire = sql"""select count(*) from asset where asset_type_id = $typeId and (valid_to >= sysdate or valid_to is null)""".as[Int].firstOption
      countingAfterExpire.get should be (0)
    }
  }
}
