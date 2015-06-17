package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{MValueAdjustment, SpeedLimitChangeSet}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.linearasset.NewLimit
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.asset.{UnknownLinkType, UnknownDirection, Municipality, BoundingRectangle}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

import scala.language.implicitConversions

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.Tag
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation

class OracleLinearAssetProviderSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val provider = new OracleLinearAssetProvider(new DummyEventBus, mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val roadLink = VVHRoadLinkWithProperties(1105998302l, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120.0, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(roadLink))
  when(mockRoadLinkService.getRoadLinksFromVVH(Seq.empty[Long])).thenReturn(Seq.empty[VVHRoadLinkWithProperties])

  when(mockRoadLinkService.fetchVVHRoadlink(362964704))
    .thenReturn(Some(VVHRoadlink(362964704l, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchVVHRoadlink(362955345))
    .thenReturn(Some(VVHRoadlink(362955345l, 91,  List(Point(117.318, 0.0), Point(127.239, 0.0)), Municipality, UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchVVHRoadlink(362955339))
    .thenReturn(Some(VVHRoadlink(362955339l, 91,  List(Point(127.239, 0.0), Point(146.9, 0.0)), Municipality, UnknownDirection, FeatureClass.AllOthers)))

  private def runWithCleanup(test: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      test
      dynamicSession.rollback()
    }
  }

  test("get speed limit endpoints by id", Tag("db")) {
    runWithCleanup {
      val speedLimit = provider.getSpeedLimit(200114)
      speedLimit.get.endpoints shouldBe Set(Point(0.0, 0.0, 0.0), Point(146.9, 0.0, 0.0))
    }
  }

  test("should complete topology with speed limit segments outside bounding box") {
    runWithCleanup {
      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val provider = new OracleLinearAssetProvider(eventbus, mockRoadLinkService)
      val roadLink = VVHRoadLinkWithProperties(389010100, List(Point(0.0, 0.0), Point(80.0, 0.0)), 80.0, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      val roadLink2 = VVHRoadLinkWithProperties(388551994, List(Point(80.0, 0.0), Point(110.0, 0.0)), 30.0, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      val linkOutsideBounds = VVHRoadLinkWithProperties(388552024, List(Point(80.0, 0.0), Point(80.0, 14.587)), 14.587, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(roadLink, roadLink2))
      when(mockRoadLinkService.getRoadLinksFromVVH(Seq(388552024l))).thenReturn(Seq(linkOutsideBounds))
      val speedLimits = provider.getSpeedLimits(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)), Set.empty)
      speedLimits.map(_.id) should be(Seq(200204, 200204, 0))
      verify(eventbus).publish(
        org.mockito.Matchers.eq("speedLimits:update"),
        org.mockito.Matchers.eq(SpeedLimitChangeSet(Set(200205), Nil))
      )
    }
  }

  test("should fill speed limit gaps in the middle of a speed limit") {
    runWithCleanup {
      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val provider = new OracleLinearAssetProvider(eventbus, mockRoadLinkService)
      val roadLink3 = VVHRoadLinkWithProperties(388552024, List(Point(0.0, 0.0), Point(14.587, 0.0)), 14.587, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      val roadLink = VVHRoadLinkWithProperties(389010100, List(Point(0.0, 0.0), Point(100.0, 0.0)), 100.0, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      val roadLink2 = VVHRoadLinkWithProperties(388551994, List(Point(100.0, 0.0), Point(130.0, 0.0)), 30.0, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(roadLink, roadLink2, roadLink3))
      when(mockRoadLinkService.getRoadLinksFromVVH(Seq.empty[Long])).thenReturn(Seq.empty[VVHRoadLinkWithProperties])
      val speedLimits = provider.getSpeedLimits(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)), Set.empty)
      speedLimits.filter(_.id == 200205).map(_.points).apply(1) should be(List(Point(82.489, 0.0), Point(100.0, 0.0)))
      verify(eventbus).publish(
        org.mockito.Matchers.eq("speedLimits:update"),
        org.mockito.Matchers.eq(SpeedLimitChangeSet(Set.empty, Seq(MValueAdjustment(200205, 389010100, 100.0))))
      )
    }
  }

  test("create new speed limit") {
    runWithCleanup {
      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
      val provider = new OracleLinearAssetProvider(null, mockRoadLinkService)
      val roadLink = VVHRoadlink(1l, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchVVHRoadlink(1)).thenReturn(Some(roadLink))

      val id = provider.createSpeedLimits(Seq(NewLimit(1, 0.0, 150.0)), 30, "test", (_) => Unit)

      val createdLimit = provider.getSpeedLimit(id.head).get
      createdLimit.value should equal(Some(30))
      createdLimit.createdBy should equal(Some("test"))
      createdLimit.speedLimitLinks.length should equal(1)
    }
  }
}
