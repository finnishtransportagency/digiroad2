package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, UnknownSpeedLimit, NewLimit, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mock.MockitoSugar
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

import scala.language.implicitConversions

class OracleSpeedLimitProviderSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val provider = new OracleSpeedLimitProvider(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val roadLink = RoadLink(
    1l, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1,
    TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(roadLink))
  when(mockRoadLinkService.getRoadLinksFromVVH(any[Int])).thenReturn(List(roadLink))

  when(mockVVHClient.fetchVVHRoadlinks(Set(362964704l, 362955345l, 362955339l)))
    .thenReturn(Seq(VVHRoadlink(362964704l, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
                    VVHRoadlink(362955345l, 91,  List(Point(117.318, 0.0), Point(127.239, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
                    VVHRoadlink(362955339l, 91,  List(Point(127.239, 0.0), Point(146.9, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  def passingMunicipalityValidation(code: Int): Unit = {}
  def failingMunicipalityValidation(code: Int): Unit = { throw new IllegalArgumentException }

  val roadLinkForSeparation = RoadLink(388562360, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.BothDirections, UnknownLinkType, None, None)
  when(mockRoadLinkService.getRoadLinkFromVVH(388562360l)).thenReturn(Some(roadLinkForSeparation))

  test("create new speed limit") {
    runWithRollback {
      val roadLink = VVHRoadlink(1l, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockVVHClient.fetchVVHRoadlink(1l)).thenReturn(Some(roadLink))
      when(mockVVHClient.fetchVVHRoadlinks(Set(1l))).thenReturn(Seq(roadLink))

      val id = provider.create(Seq(NewLimit(1, 0.0, 150.0)), 30, "test", (_) => Unit)

      val createdLimit = provider.find(id.head).get
      createdLimit.value should equal(Some(NumericValue(30)))
      createdLimit.createdBy should equal(Some("test"))
    }
  }

  test("split existing speed limit") {
    runWithRollback {
      val roadLink = VVHRoadlink(388562360, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockVVHClient.fetchVVHRoadlink(388562360l)).thenReturn(Some(roadLink))
      when(mockVVHClient.fetchVVHRoadlinks(Set(388562360l))).thenReturn(Seq(roadLink))
      val speedLimits = provider.split(200097, 100, 50, 60, "test", (_) => Unit)

      val existing = speedLimits.find(_.id == 200097).get
      val created = speedLimits.find(_.id != 200097).get
      existing.value should be(Some(NumericValue(50)))
      created.value should be(Some(NumericValue(60)))
    }
  }

  test("request unknown speed limit persist in bounding box fetch") {
    runWithRollback {
      val eventBus = MockitoSugar.mock[DigiroadEventBus]
      val provider = new OracleSpeedLimitProvider(eventBus, mockVVHClient, mockRoadLinkService) {
        override def withDynTransaction[T](f: => T): T = f
      }

      provider.get(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)), Set(235))

      verify(eventBus, times(1)).publish("speedLimits:persistUnknownLimits", Seq(UnknownSpeedLimit(1, 235, Municipality)))
    }
  }

  test("request unknown speed limit persist in municipality fetch") {
    runWithRollback {
      val eventBus = MockitoSugar.mock[DigiroadEventBus]
      val provider = new OracleSpeedLimitProvider(eventBus, mockVVHClient, mockRoadLinkService) {
        override def withDynTransaction[T](f: => T): T = f
      }

      provider.get(235)

      verify(eventBus, times(1)).publish("speedLimits:persistUnknownLimits", Seq(UnknownSpeedLimit(1, 235, Municipality)))
    }
  }

  test("separate speed limit to two") {
    runWithRollback {
      val createdId = provider.separate(200097, 50, 40, "test", passingMunicipalityValidation).filter(_.id != 200097).head.id
      val createdLimit = provider.get(Seq(createdId)).head
      val oldLimit = provider.get(Seq(200097l)).head

      oldLimit.mmlId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing)
      oldLimit.value should be (Some(NumericValue(50)))
      oldLimit.modifiedBy should be (Some("test"))

      createdLimit.mmlId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing)
      createdLimit.value should be (Some(NumericValue(40)))
      createdLimit.createdBy should be (Some("test"))
    }
  }

  test("separation should call municipalityValidation") {
    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separate(200097, 50, 40, "test", failingMunicipalityValidation)
      }
    }
  }

  test("speed limit separation fails if no speed limit is found") {
    runWithRollback {
      intercept[NoSuchElementException] {
        provider.separate(0, 50, 40, "test", passingMunicipalityValidation)
      }
    }
  }

  test("speed limit separation fails if speed limit is one way") {
    val roadLink = RoadLink(388551862, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.BothDirections, UnknownLinkType, None, None)
    when(mockRoadLinkService.getRoadLinkFromVVH(388551862l)).thenReturn(Some(roadLink))

    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separate(300388, 50, 40, "test", passingMunicipalityValidation)
      }
    }
  }

  test("speed limit separation fails if road link is one way") {
    val roadLink = RoadLink(1068804929, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.TowardsDigitizing, UnknownLinkType, None, None)
    when(mockRoadLinkService.getRoadLinkFromVVH(1068804929l)).thenReturn(Some(roadLink))

    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separate(200299, 50, 40, "test", passingMunicipalityValidation)
      }
    }
  }
}
