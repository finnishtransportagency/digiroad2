package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.Sequences
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Weekday
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.{FunSuite, Matchers, Tag}
import org.scalatest.mock.MockitoSugar

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class OracleLinearAssetDaoSpec extends FunSuite with Matchers {
  val roadLink = VVHRoadlink(388562360, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.BothDirections, AllOthers)

  private def daoWithRoadLinks(roadLinks: Seq[VVHRoadlink]): OracleLinearAssetDao = {
    val mockedRoadLinkService = MockitoSugar.mock[RoadLinkService]

    when(mockedRoadLinkService.fetchVVHRoadlinks(roadLinks.map(_.mmlId).toSet))
      .thenReturn(roadLinks)

    roadLinks.foreach { roadLink =>
      when(mockedRoadLinkService.fetchVVHRoadlink(roadLink.mmlId)).thenReturn(Some(roadLink))
    }

    new OracleLinearAssetDao {
      override val roadLinkService: RoadLinkService = mockedRoadLinkService
    }
  }

  private def truncateLinkGeometry(mmlId: Long, startMeasure: Double, endMeasure: Double, roadLinkService: RoadLinkService): Seq[Point] = {
    val geometry = roadLinkService.fetchVVHRoadlink(mmlId).get.geometry
    GeometryUtils.truncateGeometry(geometry, startMeasure, endMeasure)
  }

  def assertSpeedLimitEndPointsOnLink(speedLimitId: Long, mmlId: Long, startMeasure: Double, endMeasure: Double, dao: OracleLinearAssetDao) = {
    val expectedEndPoints = GeometryUtils.geometryEndpoints(truncateLinkGeometry(mmlId, startMeasure, endMeasure, dao.roadLinkService).toList)
    val limitEndPoints = GeometryUtils.geometryEndpoints(dao.getLinksWithLengthFromVVH(20, speedLimitId).find { link => link._1 == mmlId }.get._3)
    expectedEndPoints._1.distanceTo(limitEndPoints._1) should be(0.0 +- 0.01)
    expectedEndPoints._2.distanceTo(limitEndPoints._2) should be(0.0 +- 0.01)
  }

  def passingMunicipalityValidation(code: Int): Unit = {}
  
  def failingMunicipalityValidation(code: Int): Unit = { throw new IllegalArgumentException }

  private def simulateQuery[T](f: => T): T = {
    val result = f
    sqlu"""delete from temp_id""".execute
    result
  }

  test("Split should fail when user is not authorized for municipality") {
    Database.forDataSource(ds).withDynTransaction {
      val dao = daoWithRoadLinks(List(roadLink))
      intercept[IllegalArgumentException] {
        dao.splitSpeedLimit(200097, 100, 120, "test", failingMunicipalityValidation)
      }
      dynamicSession.rollback()
    }
  }

  test("splitting one link speed limit " +
    "where split measure is after link middle point " +
    "modifies end measure of existing speed limit " +
    "and creates new speed limit for second split", Tag("db")) {
    Database.forDataSource(ds).withDynTransaction {
      val dao = daoWithRoadLinks(List(roadLink))
      val createdId = dao.splitSpeedLimit(200097, 100, 120, "test", passingMunicipalityValidation)
      val (existingModifiedBy, _, _, _, _) = dao.getSpeedLimitDetails(200097)
      val (_, _, newCreatedBy, _, _) = dao.getSpeedLimitDetails(createdId)

      assertSpeedLimitEndPointsOnLink(200097, 388562360, 0, 100, dao)
      assertSpeedLimitEndPointsOnLink(createdId, 388562360, 100, 136.788, dao)

      existingModifiedBy shouldBe Some("test")
      newCreatedBy shouldBe Some("test")
      dynamicSession.rollback()
    }
  }

  test("splitting one link speed limit " +
    "where split measure is before link middle point " +
    "modifies start measure of existing speed limit " +
    "and creates new speed limit for first split", Tag("db")) {
    Database.forDataSource(ds).withDynTransaction {
      val dao = daoWithRoadLinks(List(roadLink))
      val createdId = dao.splitSpeedLimit(200097, 50, 120, "test", passingMunicipalityValidation)
      val (modifiedBy, _, _, _, _) = dao.getSpeedLimitDetails(200097)
      val (_, _, newCreatedBy, _, _) = dao.getSpeedLimitDetails(createdId)

      assertSpeedLimitEndPointsOnLink(200097, 388562360, 50, 136.788, dao)
      assertSpeedLimitEndPointsOnLink(createdId, 388562360, 0, 50, dao)

      modifiedBy shouldBe Some("test")
      newCreatedBy shouldBe Some("test")
      dynamicSession.rollback()
    }
  }

  test("can update speedlimit value") {
    Database.forDataSource(ds).withDynTransaction {
      val dao = daoWithRoadLinks(List(roadLink))
      dao.updateSpeedLimitValue(200097, 60, "test", _ => ())
      dao.getSpeedLimitDetails(200097)._5 should equal(Some(60))
      dao.updateSpeedLimitValue(200097, 100, "test", _ => ())
      dao.getSpeedLimitDetails(200097)._5 should equal(Some(100))
      dynamicSession.rollback()
    }
  }

  test("filter out floating speed limits") {
    Database.forDataSource(ds).withDynTransaction {
      val roadLinks = Seq(
        VVHRoadLinkWithProperties(362957727, List(Point(0.0, 0.0), Point(40.0, 0.0)), 40.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        VVHRoadLinkWithProperties(362955969, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
      val dao = new OracleLinearAssetDao {
        override val roadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
      }
      dao.floatLinearAssets(Set(300100, 300101))
      val (speedLimits, _) = dao.getSpeedLimitLinksByRoadLinks(roadLinks)
      speedLimits.map(_.id) should equal(Seq(200352))
      dynamicSession.rollback()
    }
  }

  test("filter out disallowed link types") {
    Database.forDataSource(ds).withDynTransaction {
      val roadLinks = Seq(
        VVHRoadLinkWithProperties(1088841242, List(Point(0.0, 0.0), Point(40.0, 0.0)), 40.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        VVHRoadLinkWithProperties(1088841350, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, PedestrianZone, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        VVHRoadLinkWithProperties(1088841350, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        VVHRoadLinkWithProperties(1088841350, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, CableFerry, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        VVHRoadLinkWithProperties(1088841350, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, UnknownLinkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      )
      val dao = new OracleLinearAssetDao {
        override val roadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
      }

      val speedLimits = dao.getSpeedLimitLinksByRoadLinks(roadLinks)

      speedLimits._1.map(_.id) should equal(Seq(300103))
      dynamicSession.rollback()
    }
  }

  test("filter out disallowed functional classes") {
    Database.forDataSource(ds).withDynTransaction {
      val roadLinks = Seq(
        VVHRoadLinkWithProperties(1088841242, List(Point(0.0, 0.0), Point(40.0, 0.0)), 40.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        VVHRoadLinkWithProperties(1088841350, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 7, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        VVHRoadLinkWithProperties(1088841350, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 8, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      )
      val dao = new OracleLinearAssetDao {
        override val roadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
      }

      val speedLimits = dao.getSpeedLimitLinksByRoadLinks(roadLinks)

      speedLimits._1.map(_.id) should equal(Seq(300103))
      dynamicSession.rollback()
    }
  }

  test("speed limit creation fails if speed limit is already defined on link segment") {
    Database.forDataSource(ds).withDynTransaction {
      val roadLink = VVHRoadlink(123, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val dao = daoWithRoadLinks(List(roadLink))
      val id = simulateQuery {
        dao.createSpeedLimit("test", 123, (0.0, 100.0), SideCode.BothDirections, 40, _ => ())
      }
      id shouldBe defined
      val id2 = simulateQuery {
        dao.createSpeedLimit("test", 123, (0.0, 100.0), SideCode.BothDirections, 40, _ => ())
      }
      id2 shouldBe None
      dynamicSession.rollback()
    }
  }

  test("speed limit creation succeeds when speed limit is already defined on segment iff speed limits have opposing sidecodes") {
    Database.forDataSource(ds).withDynTransaction {
      val roadLink = VVHRoadlink(123, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val dao = daoWithRoadLinks(List(roadLink))
      val id = simulateQuery {
        dao.createSpeedLimit("test", 123, (0.0, 100.0), SideCode.TowardsDigitizing, 40, _ => ())
      }
      id shouldBe defined
      val id2 = simulateQuery {
        dao.createSpeedLimit("test", 123, (0.0, 100.0), SideCode.AgainstDigitizing, 40, _ => ())
      }
      id2 shouldBe defined
      val id3 = simulateQuery {
        dao.createSpeedLimit("test", 123, (0.0, 100.0), SideCode.BothDirections, 40, _ => ())
      }
      id3 shouldBe None
      dynamicSession.rollback()
    }
  }

  test("speed limit purge removes fully covered link from unknown speed limit list") {
    Database.forDataSource(ds).withDynTransaction {
      val mmlId = 1068804942
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (mml_id, municipality_code, administrative_class) values ($mmlId, 235, 1)""".execute
      val dao = daoWithRoadLinks(Nil)
      dao.purgeFromUnknownSpeedLimits(mmlId, 59.934)
      sql"""select mml_id from unknown_speed_limit where mml_id = $mmlId""".as[Long].firstOption should be(None)
      dynamicSession.rollback()
    }
  }

  test("speed limit purge does not remove partially covered link from unknown speed limit list") {
    Database.forDataSource(ds).withDynTransaction {
      val mmlId = 1068804939
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (mml_id, municipality_code, administrative_class) values ($mmlId, 235, 1)""".execute
      val roadLink = VVHRoadlink(mmlId, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val dao = daoWithRoadLinks(List(roadLink))

      dao.createSpeedLimit("test", mmlId, (11.0, 16.0), SideCode.BothDirections, 40, _ => ())
      dao.purgeFromUnknownSpeedLimits(mmlId, 86.123)
      sql"""select mml_id from unknown_speed_limit where mml_id = $mmlId""".as[Long].firstOption should be(Some(mmlId))

      dao.createSpeedLimit("test", mmlId, (20.0, 54.0), SideCode.BothDirections, 40, _ => ())
      dao.purgeFromUnknownSpeedLimits(mmlId, 86.123)
      sql"""select mml_id from unknown_speed_limit where mml_id = $mmlId""".as[Long].firstOption should be(None)

      dynamicSession.rollback()
    }
  }

  test("unknown speed limits can be filtered by municipality") {
    Database.forDataSource(ds).withDynTransaction {
      val mmlId = 1
      val mmlId2 = 2
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (mml_id, municipality_code, administrative_class) values ($mmlId, 235, 1)""".execute
      sqlu"""insert into unknown_speed_limit (mml_id, municipality_code, administrative_class) values ($mmlId2, 49, 1)""".execute

      val roadLink = VVHRoadlink(mmlId, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val roadLink2 = VVHRoadlink(mmlId2, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val dao = daoWithRoadLinks(List(roadLink, roadLink2))

      val allSpeedLimits = dao.getUnknownSpeedLimits(None)
      allSpeedLimits("Kauniainen")("State").asInstanceOf[Seq[Long]].length should be(1)
      allSpeedLimits("Espoo")("State").asInstanceOf[Seq[Long]].length should be(1)

      val kauniainenSpeedLimits = dao.getUnknownSpeedLimits(Some(Set(235)))
      kauniainenSpeedLimits("Kauniainen")("State").asInstanceOf[Seq[Long]].length should be(1)
      kauniainenSpeedLimits.keySet.contains("Espoo") should be(false)

      dynamicSession.rollback()
    }
  }

  def setupTestProhibition(mmlId: Long, 
                           prohibitionValues: Seq[ProhibitionValue]): Unit = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

    sqlu"""insert into ASSET (ID,ASSET_TYPE_ID,CREATED_BY) values ($assetId,190,'dr2_test_data')""".execute
    sqlu"""insert into LRM_POSITION (ID,MML_ID,START_MEASURE,END_MEASURE,SIDE_CODE) values ($lrmPositionId, $mmlId, 0, 100, 1)""".execute
    sqlu"""insert into ASSET_LINK (ASSET_ID, POSITION_ID) values ($assetId, $lrmPositionId)""".execute

    prohibitionValues.foreach { prohibition =>
      val prohibitionId = Sequences.nextPrimaryKeySeqValue
      val prohibitionType = prohibition.typeId
      sqlu"""insert into PROHIBITION_VALUE (ID, ASSET_ID, TYPE) values ($prohibitionId, $assetId, $prohibitionType)""".execute

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
    val dao = new OracleLinearAssetDao { override val roadLinkService: RoadLinkService = null }
    val mmlId = 1l
    val fixtureProhibitionValues = Seq(ProhibitionValue(typeId = 10, validityPeriods = Nil, exceptions = Nil))

    Database.forDataSource(ds).withDynTransaction {
      setupTestProhibition(mmlId, fixtureProhibitionValues)

      val persistedAssets = dao.fetchProhibitionsByMmlIds(190, Seq(mmlId), "")

      persistedAssets.size should be(1)
      persistedAssets.head.mmlId should be(mmlId)

      val fetchedProhibitionValues = persistedAssets.head.value.get.asInstanceOf[Prohibitions].prohibitions
      fetchedProhibitionValues should equal(fixtureProhibitionValues)

      dynamicSession.rollback()
    }
  }

  test("fetch prohibition with validity period") {
    val dao = new OracleLinearAssetDao { override val roadLinkService: RoadLinkService = null }
    val mmlId = 1l
    val fixtureProhibitionValues = Seq(ProhibitionValue(typeId = 10, Seq(ProhibitionValidityPeriod(12, 16, Weekday)), exceptions = Nil))

    Database.forDataSource(ds).withDynTransaction {
      setupTestProhibition(mmlId, fixtureProhibitionValues)

      val persistedAssets = dao.fetchProhibitionsByMmlIds(190, Seq(mmlId), "")

      persistedAssets.size should be(1)
      persistedAssets.head.mmlId should be(mmlId)

      val fetchedProhibitionValues = persistedAssets.head.value.get.asInstanceOf[Prohibitions].prohibitions
      fetchedProhibitionValues should equal(fixtureProhibitionValues)

      dynamicSession.rollback()
    }
  }

  test("fetch prohibition with validity period and exceptions") {
    val dao = new OracleLinearAssetDao { override val roadLinkService: RoadLinkService = null }
    val mmlId = 1l
    val fixtureProhibitionValues = Seq(
      ProhibitionValue(typeId = 10, Seq(ProhibitionValidityPeriod(12, 16, Weekday)), exceptions = Seq(1, 2, 3)))

    Database.forDataSource(ds).withDynTransaction {
      setupTestProhibition(mmlId, fixtureProhibitionValues)

      val persistedAssets = dao.fetchProhibitionsByMmlIds(190, Seq(mmlId), "")

      persistedAssets.size should be(1)
      persistedAssets.head.mmlId should be(mmlId)

      val fetchedProhibitionValues = persistedAssets.head.value.get.asInstanceOf[Prohibitions].prohibitions
      fetchedProhibitionValues should equal(fixtureProhibitionValues)

      dynamicSession.rollback()
    }
  }

}
