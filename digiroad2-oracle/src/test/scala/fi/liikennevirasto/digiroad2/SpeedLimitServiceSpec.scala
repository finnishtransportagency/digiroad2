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

//      before.foreach(println)

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

//      after.foreach(println)

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

  // Works locally, won't work on CI because of number format.
  ignore("should return sensible repaired geometry after projection on overlapping data") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20
    val oldLinkId = 1
    val newLinkId = 6628024
    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(256.069, 0.0)), 256.069, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq()

    OracleDatabase.withDynTransaction {
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040875',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040876',null,'20',to_timestamp('28.10.2014 15:36:17','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040877',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040878',null,'20',to_timestamp('28.10.2014 15:36:17','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040879',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040880',null,'20',to_timestamp('28.10.2014 15:36:17','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040881',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040882',null,'20',to_timestamp('28.10.2014 15:36:17','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040883',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040884',null,'20',to_timestamp('28.10.2014 15:36:17','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040885',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040886',null,'20',to_timestamp('28.10.2014 15:36:17','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040889',null,'20',to_timestamp('28.10.2014 15:30:48','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040890',null,'20',to_timestamp('28.10.2014 15:32:25','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040891',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040892',null,'20',to_timestamp('28.10.2014 15:36:17','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040893',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040894',null,'20',to_timestamp('28.10.2014 15:36:17','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040895',null,'20',to_timestamp('02.07.2015 13:13:11','DD.MM.RRRR HH24:MI:SS'),'split_speedlimit_1307787',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040896',null,'20',to_timestamp('01.04.2016 14:11:22','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040897',null,'20',to_timestamp('01.04.2016 14:10:58','DD.MM.RRRR HH24:MI:SS'),'k127773',to_timestamp('01.04.2016 14:11:22','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040898',null,'20',to_timestamp('28.10.2014 15:36:02','DD.MM.RRRR HH24:MI:SS'),'dr1_conversion',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18040899',null,'20',to_timestamp('02.07.2015 13:13:11','DD.MM.RRRR HH24:MI:SS'),'split_speedlimit_1307787',to_timestamp('01.04.2016 14:11:14','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,235,'0')""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646676',null,'2','132,516','148,995',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646677',null,'3','132,516','148,995',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646678',null,'2','106,916','122,602',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646679',null,'3','106,916','122,602',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646680',null,'2','122,593','132,51',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646681',null,'3','122,593','132,51',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646682',null,'2','226,462','256,069',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646683',null,'3','226,462','256,069',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646684',null,'2','221,024','226,465',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646685',null,'3','221,024','226,465',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646686',null,'2','149,002','166,793',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646687',null,'3','149,002','166,793',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646690',null,'2','4,026','27,146',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646691',null,'3','4,026','27,146',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646692',null,'2','27,146','28,156',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646693',null,'3','27,146','28,156',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646694',null,'2','28,161','65,622',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646695',null,'3','28,161','65,622',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646696',null,'1','28,161','106,916',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646697',null,'3','166,782','183,357',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646698',null,'3','183,357','221,024',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646699',null,'2','166,782','196,273',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46646700',null,'2','178,636','221,024',null,'6628024','1460044024000',to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'))""".execute

      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040875','46646676')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040876','46646677')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040877','46646678')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040878','46646679')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040879','46646680')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040880','46646681')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040881','46646682')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040882','46646683')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040883','46646684')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040884','46646685')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040885','46646686')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040886','46646687')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040889','46646690')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040890','46646691')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040891','46646692')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040892','46646693')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040893','46646694')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040894','46646695')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040895','46646696')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040896','46646697')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040897','46646698')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040898','46646699')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18040899','46646700')""".execute

      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040877',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040892',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040889',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 50 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040891',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040898',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040899',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 50 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040890',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 50 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040894',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040896',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 50 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040878',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040881',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040882',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040884',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040886',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040880',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040893',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040883',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040885',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040895',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040897',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 60 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040875',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040876',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18040879',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'), null from dual""".execute



      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))

      val after = service.get(boundingBox, Set(municipalityCode)).toList
      after.flatten.length should be (10)
      after.flatten.exists(_.id == 18040892) should be (false) // Removed too short, ~ 1 meter long
      after.flatten.filter(_.id == 0) should have size 1  // One orphaned or unknown segment in source data
      dynamicSession.rollback()
    }


  }
  test("Must not expire assets that are outside of the current search even if mentioned in VVH change info") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val eventBus = MockitoSugar.mock[DigiroadEventBus]
    val service = new SpeedLimitService(eventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20
    val oldLinkId = 3055878L
    val newLinkId = 3055879L
    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(424.557, 0.0)), 424.557, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq(ChangeInfo(None, Option(oldLinkId), 0, 4, None, None, Option(0.0), Option(2.5802222500000003), Option(1461325625000L)),
      ChangeInfo(Option(oldLinkId), Option(oldLinkId), 0, 3, Option(0.0), Option(422.63448371), Option(2.5802222500000003), Option(424.55709429), Option(1461325625000L)))

    OracleDatabase.withDynTransaction {
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050499',null,'20',to_timestamp('20.04.2016 13:16:01','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050501',null,'20',to_timestamp('20.04.2016 13:16:01','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46647958',null,'1','0','321',null,'3055878','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46647960',null,'1','321','424',null,'3055878','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute

      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18050499','46647958')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18050501','46647960')""".execute

      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18050499',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18050501',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 50 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))

      val after = service.get(boundingBox, Set(municipalityCode)).toList
      provider.get(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)), Set(235))

      verify(eventBus, times(1)).publish("linearAssets:update", ChangeSet(Set(), Seq(), Seq(), Set()))
      dynamicSession.rollback()
    }


  }
}