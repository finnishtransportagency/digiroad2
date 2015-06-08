package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{MValueAdjustment, SpeedLimitChangeSet}
import fi.liikennevirasto.digiroad2._
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

  test("load speed limits with spatial bounds", Tag("db")) {
    runWithCleanup {
      val speedLimits = provider.getSpeedLimits(BoundingRectangle(Point(374700, 6677595), Point(374750, 6677560)), municipalities = Set())
      speedLimits.size shouldBe 1
    }
  }

  test("get speed limit endpoints by id", Tag("db")) {
    runWithCleanup {
      val speedLimit = provider.getSpeedLimit(200114)
      speedLimit.get.endpoints shouldBe Set(Point(0.0, 0.0, 0.0), Point(146.9, 0.0, 0.0))
    }
  }

  ignore("should ignore speed limits with segments outside link geometry") {
    runWithCleanup {
      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val provider = new OracleLinearAssetProvider(eventbus, mockRoadLinkService)
      val roadLink = VVHRoadLinkWithProperties(389010100, List(Point(0.0, 0.0), Point(80.0, 0.0)), 80.0, Municipality, 0, UnknownDirection, UnknownLinkType, None, None)
      val roadLink2 = VVHRoadLinkWithProperties(388551994, List(Point(80.0, 0.0), Point(110.0, 0.0)), 30.0, Municipality, 0, UnknownDirection, UnknownLinkType, None, None)
      when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(roadLink, roadLink2))
      val speedLimits = provider.getSpeedLimits(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)), Set.empty)
      speedLimits.map(_.id) should be(Seq(200204))
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
      val roadLink = VVHRoadLinkWithProperties(362959521, List(Point(0.0, 0.0), Point(223.834, 0.0)), 223.834, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      val roadLink2 = VVHRoadLinkWithProperties(362959407, List(Point(223.834, 0.0), Point(383.834, 0.0)), 160.0, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      val roadLink3 = VVHRoadLinkWithProperties(362964776, List(Point(474.567, 0.0), Point(383.834, 0.0)), 90.733, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
      when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(roadLink, roadLink2, roadLink3))
      val speedLimits = provider.getSpeedLimits(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)), Set.empty)
      speedLimits.filter(_.id == 200217).map(_.points).apply(1) should be(List(Point(223.834, 0.0), Point(383.834, 0.0)))
      verify(eventbus).publish(
        org.mockito.Matchers.eq("speedLimits:update"),
        org.mockito.Matchers.eq(SpeedLimitChangeSet(Set.empty, Seq(MValueAdjustment(200217, 362959407, 160.0))))
      )
    }
  }
}
