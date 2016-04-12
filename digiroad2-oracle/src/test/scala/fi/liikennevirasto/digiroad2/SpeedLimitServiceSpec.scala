package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.{NewLimit, NumericValue, RoadLink, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

import scala.language.implicitConversions

class SpeedLimitServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val provider = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val roadLink = RoadLink(
    1l, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1,
    TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
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

  // --- Tests for DROTH-1 Automatics for fixing speed limits after geometry update (using VVH change info data)

  test("Should map speed limit of old link to three new links, same speed limit both directions ") {

    // Divided road link (change types 5 and 6)
    // Speed limit case 1

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId1 = 6001
    val newLinkId2 = 6002
    val newLinkId3 = 6003
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, null, 0.000, 25.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = service.get(boundingBox, Set(municipalityCode)).toList

      after.length should be(3)
      after.head.foreach(_.value should be(Some(NumericValue(80))))
      after.head.foreach(_.sideCode should be(SideCode.BothDirections))

      dynamicSession.rollback()
    }
  }

  test("Should map speed limit of old link to three new links, two old speed limits, different speed limits to different directions (separate) ") {

    // Divided road link (change types 5 and 6)
    // Speed limit case 2

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId1 = 6001
    val newLinkId2 = 6002
    val newLinkId3 = 6003
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, null, 0.000, 25.000, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId, null, 0.000, 25.000, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (2,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (2,2)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (2,(select id from enumerated_value where value = 60),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      //println(before)

      before.length should be(2)
      val (list1, list2) = before.flatten.partition(_.sideCode == SideCode.TowardsDigitizing)
      val (limit1, limit2) = (list1.head, list2.head)
      limit1.id should be (1)
      limit2.id should be (2)
      limit1.value should be (Some(NumericValue(80)))
      limit2.value should be (Some(NumericValue(60)))
      limit1.startMeasure should be (0.0)
      limit2.startMeasure should be (0.0)
      limit1.endMeasure should be (25.0)
      limit2.endMeasure should be (25.0)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = service.get(boundingBox, Set(municipalityCode)).toList

      //println(after)

      after.length should be(6)
      after.flatten.forall(sl => sl.sideCode != SideCode.BothDirections) should be (true)
      after.flatten.forall(sl => sl.vvhTimeStamp == 144000000L) should be (true)
      val towards = after.flatten.filter(sl => sl.sideCode == SideCode.TowardsDigitizing)
      val against = after.flatten.filter(sl => sl.sideCode == SideCode.AgainstDigitizing)

      towards.length should be (3)
      against.length should be (3)

      towards.forall(sl=> sl.value.get.value == 80) should be (true)
      against.forall(sl=> sl.value.get.value == 60) should be (true)

      dynamicSession.rollback()
    }
  }

  test("Should map speed limit of old link to three new links, two old speed limits, same speed limit both directions (split) ") {

    // Divided road link (change types 5 and 6)
    // Speed limit case 3

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId1 = 6001
    val newLinkId2 = 6002
    val newLinkId3 = 6003
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, null, 0.000, 15.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId, null, 15.000, 25.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (2,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (2,2)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (2,(select id from enumerated_value where value = 60),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(2)
      val (list1, list2) = before.flatten.partition(_.id == 1)
      val (limit1, limit2) = (list1.head, list2.head)
      limit1.id should be (1)
      limit2.id should be (2)
      limit1.value should be (Some(NumericValue(80)))
      limit2.value should be (Some(NumericValue(60)))
      limit1.startMeasure should be (0.0)
      limit2.startMeasure should be (limit1.endMeasure)
      limit2.endMeasure should be (25.0)
      limit1.sideCode should be (SideCode.BothDirections)
      limit2.sideCode should be (SideCode.BothDirections)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = service.get(boundingBox, Set(municipalityCode)).toList

      after.length should be(4)
      after.flatten.forall(sl => sl.sideCode eq SideCode.BothDirections) should be (true)
      after.flatten.forall(sl => sl.vvhTimeStamp == 144000000L) should be (true)
      val link1Limits = after.flatten.filter(sl => sl.linkId == newLinkId1)
      val link2Limits = after.flatten.filter(sl => sl.linkId == newLinkId2)
      val link3Limits = after.flatten.filter(sl => sl.linkId == newLinkId3)

      link1Limits.length should be (1)
      link2Limits.length should be (2)
      link3Limits.length should be (1)

      link1Limits.head.value should be (Some(NumericValue(80)))
      link3Limits.head.value should be (Some(NumericValue(60)))
      link2Limits.filter(_.startMeasure == 0.0).head.value should be (Some(NumericValue(80)))
      link2Limits.filter(_.startMeasure > 0.0).head.value should be (Some(NumericValue(60)))
      dynamicSession.rollback()
    }
  }

  test("Should map speed limit of three old links to one new link") {

    // Combined road link (change types 1 and 2)
    // Speed limit case 1

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val oldLinkId3 = 5003
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(oldLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), Some(144000000)),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), Some(144000000)),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId2, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (2,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (2,2)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (2,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId3, null, 0.000, 5.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (3,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (3,3)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (3,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))

      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(3)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))

      val after = service.get(boundingBox, Set(municipalityCode)).toList

      after.length should be(1)
      after.head.foreach(_.value should be(Some(NumericValue(80))))
      after.head.foreach(_.sideCode should be(SideCode.BothDirections))

      dynamicSession.rollback()
    }
  }

  test("Should map speed limit of old link to lengthened new link with same id ") {

    // Lengthened road link (change types 3 and 4)
    // Speed limit case 1

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 3, Some(0), Some(10), Some(5), Some(15), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 4, null, null, Some(0), Some(5), Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.get(boundingBox, Set(municipalityCode)).toList

      after.length should be(1)
      after.head.foreach(_.value should be(Some(NumericValue(80))))
      after.head.foreach(_.sideCode should be(SideCode.BothDirections))

      dynamicSession.rollback()
    }
  }

  test("Should map speed limit of old link to shortened new link with same id (common part + removed part)") {

    // Shortened road link (change types 7 and 8)
    // 1. Common part + 2. Removed part
    // Speed limit case 1

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 7, Some(0), Some(20), Some(0), Some(15), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 8, Some(15), Some(20), null, null, Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, null, 0.000, 20.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.get(boundingBox, Set(municipalityCode)).toList

      after.length should be(1)
      after.head.foreach(_.value should be(Some(NumericValue(80))))
      after.head.foreach(_.sideCode should be(SideCode.BothDirections))

      dynamicSession.rollback()
    }
  }

  test("Should map speed limit of old link to shortened new link with same id (removed part + common part)") {

    // Shortened road link (change types 7 and 8)
    // 1. Removed part + 2. Common part
    // Speed limit case 1

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 7, Some(5), Some(20), Some(0), Some(15), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 8, Some(0), Some(5), null, null, Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, null, 0.000, 20.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.get(boundingBox, Set(municipalityCode)).toList

      after.length should be(1)
      after.head.foreach(_.value should be(Some(NumericValue(80))))
      after.head.foreach(_.sideCode should be(SideCode.BothDirections))

      dynamicSession.rollback()
    }
  }

  test("Should take latest time stamp from old speed limits to combined road link") {

    // Combined road link (change types 1 and 2)
    // Speed limit case 1

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val oldLinkId3 = 5003
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(oldLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), Some(144000000)),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), Some(144000000)),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating, modified_date, modified_by) values (1,$speedLimitAssetTypeId,0,TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId2, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating, modified_date, modified_by) values (2,$speedLimitAssetTypeId,0,TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (2,2)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (2,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId3, null, 0.000, 5.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating, modified_date, modified_by) values (3,$speedLimitAssetTypeId,0,TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (3,3)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (3,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList.flatten

      before.length should be(3)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.get(boundingBox, Set(municipalityCode)).toList.flatten

      after.length should be(1)
      after.head.modifiedBy should be(Some("KX2"))

      val formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
      val latestModifiedDate = DateTime.parse("2016-02-17 10:03:51.047483", formatter)
      after.head.modifiedDateTime should be(Some(latestModifiedDate))

      dynamicSession.rollback()
    }
  }


  test("Should pass change information through the actor"){

    //This test pass if the actors are called even when there are any information changed
    val municipalityCode = 235
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))

    runWithRollback {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

      val provider = new SpeedLimitService(mockEventBus, mockVVHClient, mockRoadLinkService) {
        override def withDynTransaction[T](f: => T): T = f
      }

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(), Nil))

      provider.get(boundingBox, Set(municipalityCode))

      verify(mockEventBus, times(1)).publish("linearAssets:update", ChangeSet(Set(), List(), List(), Set()))
      verify(mockEventBus, times(1)).publish("speedLimits:saveProjectedSpeedLimits", List())
      verify(mockEventBus, times(1)).publish("speedLimits:purgeUnknownLimits", Set())
      verify(mockEventBus, times(1)).publish("speedLimits:persistUnknownLimits", List())

    }
  }

  test("Should map speed limit of old link to replaced link ") {

    // Replaced road link (change types 13 and 14)
    // Speed limit case 1
    /*
    Example change data:
   {"attributes": {
   "OLD_ID": 743821,
   "OLD_START": 0,
   "OLD_END": 139.97443241,
   "NEW_ID": 743821,
   "NEW_START": 0,
   "NEW_END": 126.90040119,
   "CREATED_DATE": 1457442664000,
   "CHANGETYPE": 13,
   "MTKID": 1718763071
  }},
  {"attributes": {
   "OLD_ID": null,
   "OLD_START": null,
   "OLD_END": null,
   "NEW_ID": 743821,
   "NEW_START": 126.90040119,
   "NEW_END": 172.33287217,
   "CREATED_DATE": 1457442664000,
   "CHANGETYPE": 14,
   "MTKID": 1718763071
   }},
    */

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(30.0, 0.0)), 30.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(50.0, 0.0)), 50.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 13, Some(0), Some(30), Some(0), Some(20), Some(144000000)),
      ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 14, null, null, Some(20), Some(50), Some(144000000)))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, null, 0.000, 30.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values (1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values (1,1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values (1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      //println(before)

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.get(boundingBox, Set(municipalityCode)).toList
      //println(after)

      after.length should be(1)
      after.head.foreach(_.value should be(Some(NumericValue(80))))
      after.head.foreach(_.sideCode should be(SideCode.BothDirections))
      after.head.foreach(_.endMeasure should be(50))

      dynamicSession.rollback()
    }
  }

}