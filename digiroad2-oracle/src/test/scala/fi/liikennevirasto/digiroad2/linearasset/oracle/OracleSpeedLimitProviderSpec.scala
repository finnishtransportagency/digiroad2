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
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

import scala.concurrent.Promise

import scala.language.implicitConversions

class OracleSpeedLimitProviderSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val provider = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val roadLink = RoadLink(
    1l, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1,
    TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(roadLink))
  when(mockRoadLinkService.getRoadLinksFromVVH(any[Int])).thenReturn(List(roadLink))
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(roadLink), Nil))
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[Int])).thenReturn((List(roadLink), Nil))

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
      val provider = new SpeedLimitService(eventBus, mockVVHClient, mockRoadLinkService) {
        override def withDynTransaction[T](f: => T): T = f
      }

      provider.get(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)), Set(235))

      verify(eventBus, times(1)).publish("speedLimits:persistUnknownLimits", Seq(UnknownSpeedLimit(1, 235, Municipality)))
    }
  }

  test("request unknown speed limit persist in municipality fetch") {
    runWithRollback {
      val eventBus = MockitoSugar.mock[DigiroadEventBus]
      val provider = new SpeedLimitService(eventBus, mockVVHClient, mockRoadLinkService) {
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

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing)
      oldLimit.value should be (Some(NumericValue(50)))
      oldLimit.modifiedBy should be (Some("test"))

      createdLimit.linkId should be (388562360)
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
    val roadLink = RoadLink(1611445, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.BothDirections, UnknownLinkType, None, None)
    when(mockRoadLinkService.getRoadLinkFromVVH(1611445)).thenReturn(Some(roadLink))

    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separate(300388, 50, 40, "test", passingMunicipalityValidation)
      }
    }
  }

  test("speed limit separation fails if road link is one way") {
    val roadLink = RoadLink(1611388, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.TowardsDigitizing, UnknownLinkType, None, None)
    when(mockRoadLinkService.getRoadLinkFromVVH(1611388)).thenReturn(Some(roadLink))

    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separate(200299, 50, 40, "test", passingMunicipalityValidation)
      }
    }
  }

  // --- Tests for DROTH-1 Automatics for fixing speed limits after geometry update

  test("Divided road link (change types 5&6): Should map speed limit of old link to two new links") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 4l
    val newLinkId1 = 5l
    val newLinkId2 = 6l
    val municipalityCode = 235
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))

    val oldVVHRoadLink = VVHRoadlink(oldLinkId, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(0.0, 11.0)), 11.0, Municipality, 1, TrafficDirection.UnknownDirection, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newVVHRoadLinks = Seq(VVHRoadlink(newLinkId1, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      VVHRoadlink(newLinkId2, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))
    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(0.0, 0.0)), 0.0, Municipality, 1, TrafficDirection.UnknownDirection, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(0.0, 0.0)), 0.0, Municipality, 1, TrafficDirection.UnknownDirection, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(1), Some(0), Some(1), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 5, Some(10), Some(1), Some(0), Some(1), Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, null, 0.000, 15.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,20,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 70),(select id from property where public_id = 'rajoitus'))""".execute


      when(mockVVHClient.fetchVVHRoadlinks(boundingBox, Set())).thenReturn(Seq(oldVVHRoadLink))
      when(mockVVHClient.fetchVVHRoadlinksF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldVVHRoadLink)).future)
      when(mockVVHClient.fetchChangesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)

      when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(oldRoadLink))
      when(mockRoadLinkService.getRoadLinksFromVVH(any[Int])).thenReturn(List(oldRoadLink))
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[Int])).thenReturn((List(oldRoadLink), Nil))

      val before = service.get(boundingBox, Set(municipalityCode))

      //println(before)
      before.length should be(1)
      //before.value should be(Some(NumericValue(70)))
      /*
      when(mockVVHClient.fetchVVHRoadlinks(boundingBox, Set())).thenReturn(newVVHRoadLinks)
      when(mockVVHClient.fetchVVHRoadlinksF(boundingBox, Set())).thenReturn(Promise.successful(newVVHRoadLinks).future)
      when(mockVVHClient.fetchChangesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)

      when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(newRoadLinks)
      when(mockRoadLinkService.getRoadLinksFromVVH(any[Int])).thenReturn(newRoadLinks)
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, Nil))
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[Int])).thenReturn((newRoadLinks, Nil))

      val after = provider.get(boundingBox, Set(municipalityCode))
      println(after)
      */

      //after.length should be(2)

      dynamicSession.rollback()    }
  }

}