package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
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
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val provider = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val roadLink = RoadLink(
    1l, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1,
    TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(roadLink), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((List(roadLink), Nil))

  when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(Set(362964704l, 362955345l, 362955339l)))
    .thenReturn(Seq(VVHRoadlink(362964704l, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
      VVHRoadlink(362955345l, 91,  List(Point(117.318, 0.0), Point(127.239, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
      VVHRoadlink(362955339l, 91,  List(Point(127.239, 0.0), Point(146.9, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  def passingMunicipalityValidation(code: Int): Unit = {}
  def failingMunicipalityValidation(code: Int): Unit = { throw new IllegalArgumentException }

  val roadLinkForSeparation = RoadLink(388562360, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.BothDirections, UnknownLinkType, None, None)
  when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(388562360l)).thenReturn(Some(roadLinkForSeparation))

  test("create new speed limit") {
    runWithRollback {
      val roadLink = VVHRoadlink(1l, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(1l)).thenReturn(Some(roadLink))
      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(Set(1l))).thenReturn(Seq(roadLink))

      val id = provider.create(Seq(NewLimit(1, 0.0, 150.0)), 30, "test", (_) => Unit)

      val createdLimit = provider.find(id.head).get
      createdLimit.value should equal(Some(NumericValue(30)))
      createdLimit.createdBy should equal(Some("test"))
    }
  }

  test("split existing speed limit") {
    runWithRollback {
      val roadLink = VVHRoadlink(388562360, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(Set(388562360l))).thenReturn(Seq(roadLink))
      val speedLimits = provider.split(200097, 100, 50, 60, "test", (_) => Unit)

      val existing = speedLimits(0)
      val created = speedLimits(1)
      existing.value should not be(200097)
      created.value should not be(200097)
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
    val municipalityCode = 235
    val linkId = 388562360
    val geometry = List(Point(0.0, 0.0), Point(424.557, 0.0))
    val vvhRoadLink = VVHRoadlink(linkId, municipalityCode, geometry, AdministrativeClass.apply(1), TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map())

    runWithRollback {
      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(any[Set[Long]])).thenReturn(List(vvhRoadLink))

      val Seq(updatedLimit, createdLimit) = provider.separate(200097, 50, 40, "test", passingMunicipalityValidation)

      updatedLimit.linkId should be (388562360)
      updatedLimit.sideCode should be (SideCode.TowardsDigitizing)
      updatedLimit.value should be (Some(NumericValue(50)))
      updatedLimit.modifiedBy should be (Some("test"))

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
    when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(1611445)).thenReturn(Some(roadLink))

    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separate(300388, 50, 40, "test", passingMunicipalityValidation)
      }
    }
  }

  test("speed limit separation fails if road link is one way") {
    val roadLink = RoadLink(1611388, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.TowardsDigitizing, UnknownLinkType, None, None)
    when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(1611388)).thenReturn(Some(roadLink))

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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm, asset) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm, $oldLinkId, null, 0.000, 25.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset,$lrm)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((newRoadLinks, changeInfo))
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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, null, 0.000, 25.000, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId, null, 0.000, 25.000, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset2,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset2,(select id from enumerated_value where value = 60),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

//      before.foreach(println)

      before.length should be(2)
      val (list1, list2) = before.flatten.partition(_.sideCode == SideCode.TowardsDigitizing)
      val (limit1, limit2) = (list1.head, list2.head)
      limit1.id should be (asset1)
      limit2.id should be (asset2)
      limit1.value should be (Some(NumericValue(80)))
      limit2.value should be (Some(NumericValue(60)))
      limit1.startMeasure should be (0.0)
      limit2.startMeasure should be (0.0)
      limit1.endMeasure should be (25.0)
      limit2.endMeasure should be (25.0)

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((newRoadLinks, changeInfo))
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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, null, 0.000, 15.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId, null, 15.000, 25.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset2,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset2,(select id from enumerated_value where value = 60),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(2)
      val (list1, list2) = before.flatten.partition(_.id == asset1)
      val (limit1, limit2) = (list1.head, list2.head)
      limit1.id should be (asset1)
      limit2.id should be (asset2)
      limit1.value should be (Some(NumericValue(80)))
      limit2.value should be (Some(NumericValue(60)))
      limit1.startMeasure should be (0.0)
      limit2.startMeasure should be (limit1.endMeasure)
      limit2.endMeasure should be (25.0)
      limit1.sideCode should be (SideCode.BothDirections)
      limit2.sideCode should be (SideCode.BothDirections)

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((newRoadLinks, changeInfo))
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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId2, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset2,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset2,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId3, null, 0.000, 5.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset3,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset3,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((oldRoadLinks, Nil))

      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(3)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))

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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 3, Some(0), Some(10), Some(5), Some(15), 144000000),
      ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 4, null, null, Some(0), Some(5), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, asset1) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))
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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 7, Some(0), Some(20), Some(0), Some(15), 144000000),
      ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 8, Some(15), Some(20), null, null, 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, asset1) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, null, 0.000, 20.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))
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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 7, Some(5), Some(20), Some(0), Some(15), 144000000),
      ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 8, Some(0), Some(5), null, null, 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, asset1) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, null, 0.000, 20.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))
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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating, modified_date, modified_by) values ($asset1,$speedLimitAssetTypeId,0,TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId2, null, 0.000, 10.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating, modified_date, modified_by) values ($asset2,$speedLimitAssetTypeId,0,TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset2,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId3, null, 0.000, 5.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating, modified_date, modified_by) values ($asset3,$speedLimitAssetTypeId,0,TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset3,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((oldRoadLinks, Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList.flatten

      before.length should be(3)

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))
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

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

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

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 13, Some(0), Some(30), Some(0), Some(20), 144000000),
      ChangeInfo(Some(oldLinkId), Some(oldLinkId), 12345, 14, null, null, Some(20), Some(50), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, asset1) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, null, 0.000, 30.000, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id,asset_type_id,floating) values ($asset1,$speedLimitAssetTypeId,0)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id,enumerated_value_id,property_id) values ($asset1,(select id from enumerated_value where value = 80),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(oldRoadLink), Nil))
      val before = service.get(boundingBox, Set(municipalityCode)).toList

      //println(before)

      before.length should be(1)
      before.head.foreach(_.value should be(Some(NumericValue(80))))
      before.head.foreach(_.sideCode should be(SideCode.BothDirections))

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))
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



      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))

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

    val changeInfo = Seq(ChangeInfo(None, Option(oldLinkId), 0, 4, None, None, Option(0.0), Option(2.5802222500000003), 1461325625000L),
      ChangeInfo(Option(oldLinkId), Option(oldLinkId), 0, 3, Option(0.0), Option(422.63448371), Option(2.5802222500000003), Option(424.55709429), 1461325625000L))

    OracleDatabase.withDynTransaction {
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050499',null,'20',to_timestamp('20.04.2016 13:16:01','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050501',null,'20',to_timestamp('20.04.2016 13:16:01','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46647958',null,'1','0','321',null,'3055878','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46647960',null,'1','321','424',null,'3055878','1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute

      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18050499','46647958')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18050501','46647960')""".execute

      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18050499',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 40 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18050501',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 50 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))

      val after = service.get(boundingBox, Set(municipalityCode)).toList
      provider.get(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)), Set(235))

      verify(eventBus, times(1)).publish("linearAssets:update", ChangeSet(Set(), Seq(), Seq(), Set()))
      dynamicSession.rollback()
    }


  }

  test("Must be able to split one sided speedlimits and keep new speed limit") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
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
    val linkId = 2934660L
    val geometry = List(Point(0.0, 0.0), Point(424.557, 0.0))
    val vvhRoadLink = VVHRoadlink(linkId, municipalityCode, geometry, AdministrativeClass.apply(1), TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map())
    val newRoadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(424.557, 0.0)), 424.557, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq()

    OracleDatabase.withDynTransaction {
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050499',null,'20',to_timestamp('20.04.2016 13:16:01','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050501',null,'20',to_timestamp('20.04.2016 13:16:01','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,EXTERNAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050503',null,'20',to_timestamp('20.04.2016 13:16:02','DD.MM.RRRR HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46647958',null,'2','0','424',null,$linkId,'1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46647960',null,'3','0','424',null,$linkId,'1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'))""".execute

      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18050499','46647958')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18050501','46647960')""".execute

      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18050499',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 100 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT '18050501',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 80 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(newRoadLink), changeInfo))
      when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(newRoadLink))

      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(any[Set[Long]])).thenReturn(List(vvhRoadLink))

      val before = service.get(boundingBox, Set(municipalityCode)).toList

//      println("Before split")
//      before.foreach(_.foreach(printSL))

      val split = service.split(18050499, 419.599, 100, 80, "split", { x: Int => })

      split should have size(2);

//      println("Split")
//      split.foreach(printSL)

      val after = service.get(boundingBox, Set(municipalityCode)).toList

//      println("After split")
//      after.foreach(_.foreach(printSL))

      after.forall(_.forall(_.id != 0)) should be (true)

//      provider.get(BoundingRectangle(Point(0.0, 0.0), Point(425.0, 1.0)), Set(235))
//
//      verify(eventBus, times(0)).publish("linearAssets:update", ChangeSet(Set(), Seq(), Seq(), Set()))

      dynamicSession.rollback()
    }


  }


  ignore("Projecting and filling should return proper geometry on Integration API calls, too") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val eventBus = MockitoSugar.mock[DigiroadEventBus]
    val service = new SpeedLimitService(eventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val municipalityCode = 286
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val geometries = Seq(
      List(Point(249156.065, 6776941.738, 48.649999999994179), Point(249140.548, 6776951.891, 48.702999999994063), Point(249121.451, 6776963.018, 48.763000000006286), Point(249096.783, 6776976.871, 48.843999999997322), Point(249073.729, 6776989.653, 48.827000000004773), Point(249045.39, 6777005.322, 48.688999999998487), Point(249014.905, 6777022.54, 48.831999999994878), Point(248997.676, 6777033.343, 48.872000000003027)),
      List(Point(372499.326, 7128159.101, 60.56699999999546), Point(372508.96, 7128177.363, 60.740000000005239), Point(372522.026, 7128199.654, 60.978000000002794), Point(372536.446, 7128223.17, 61.054000000003725), Point(372551.382, 7128244.076, 61.025999999998021), Point(372569.75, 7128269.012, 61.101999999998952), Point(372586.008, 7128290.497, 61.198000000003958), Point(372602.941, 7128312.597, 61.350999999995111), Point(372625.434, 7128342.497, 61.580000000001746), Point(372646.154, 7128369.253, 61.599000000001979), Point(372663.321, 7128390.462, 61.687000000005355), Point(372683.671, 7128416.267, 61.695999999996275), Point(372701.958, 7128439.591, 61.75800000000163), Point(372721.922, 7128464.124, 61.898000000001048), Point(372741.581, 7128482.533, 61.770000000004075), Point(372767.689, 7128504.746, 61.309999999997672)),
      List(Point(402710.142, 6817081.47, 86.085000000006403), Point(402693.882, 6817088.352, 86.739000000001397), Point(402684.15, 6817092.608, 87.001999999993131), Point(402668.137, 6817099.745, 87.161999999996624), Point(402662.147, 6817102.129, 87.115000000005239), Point(402636.253, 6817109.544, 86.452000000004773), Point(402613.148, 6817114.9, 86.203999999997905), Point(402576.65, 6817119.035, 86.546000000002095), Point(402575.712, 6817119.132, 86.559999999997672), Point(402567.762, 6817120.005, 86.638000000006286)),
      List(Point(400822.22, 6818919.299, 128.21499999999651), Point(400824.267, 6818928.907, 128.13800000000629), Point(400827.155, 6818939.945, 127.97900000000664), Point(400830.584, 6818952.859, 127.68499999999767), Point(400834.316, 6818967.193, 127.21899999999732), Point(400838.131, 6818980.289, 126.91000000000349), Point(400844.861, 6819001.987, 126.74800000000687), Point(400851.492, 6819030.669, 127.07600000000093), Point(400859.501, 6819063.655, 127.26499999999942), Point(400865.907, 6819088.293, 126.58699999999953), Point(400870.946, 6819107.391, 125.74499999999534)),
      List(Point(400132.586, 6817557.582, 126.13300000000163), Point(400135.254, 6817559.85, 126.05400000000373), Point(400148.189, 6817571.617, 125.6710000000021), Point(400158.18, 6817581.975, 125.6420000000071), Point(400167.018, 6817591.656, 126.12600000000384), Point(400176.55, 6817603.374, 126.84799999999814), Point(400178.2, 6817605.574, 126.95799999999872), Point(400185.314, 6817615.536, 127.44700000000012), Point(400196.31, 6817631.607, 127.90499999999884), Point(400214.865, 6817657.183, 128.5399999999936), Point(400228.29, 6817675.907, 128.82099999999627), Point(400234.899, 6817685.126, 128.97999999999593), Point(400252.877, 6817710.361, 129.875), Point(400268.455, 6817730.702, 129.08400000000256), Point(400284.017, 6817753.098, 128.88899999999558), Point(400299.431, 6817772.663, 129.56699999999546), Point(400308.535, 6817784.047, 130.13800000000629)),
      List(Point(373590.033, 7129915.211, 54.932000000000698), Point(373586.562, 7129926.819, 54.868000000002212), Point(373577.558, 7129943.771, 54.855999999999767), Point(373575.712, 7129947.075, 54.892000000007101), Point(373565.604, 7129961.472, 55.036999999996624), Point(373551.341, 7129975.889, 55.278000000005704), Point(373537.545, 7129989.334, 55.448999999993248), Point(373522.64, 7130004.795, 55.448999999993248), Point(373512.04, 7130017.585, 55.054000000003725), Point(373503.042, 7130034.454, 54.267000000007101), Point(373493.963, 7130053.66, 53.551999999996042), Point(373493.527, 7130054.618, 53.532000000006519), Point(373484.935, 7130073.167, 53.161999999996624), Point(373478.436, 7130085.438, 53.055999999996857), Point(373465.667, 7130105.838, 53.317999999999302), Point(373463.724, 7130108.646, 53.392000000007101)),
      List(Point(383007.131, 7174034.401, 16.076000000000931), Point(382997.945, 7174043.118, 16.085999999995693), Point(382980.622, 7174053.428, 16.07499999999709), Point(382957.691, 7174062.279, 16.203999999997905))
    )
    val linkIds = Seq(
      6798918, 6808127, 6808222, 6808234, 6808258, 6808324, 6808402
    )

    val roadLinks = geometries.zip(linkIds).map {
      case (geometry, linkId) => RoadLink(linkId, geometry, GeometryUtils.geometryLength(geometry),
        administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    }

    val vvhRoadLinks = roadLinks.map(rl =>
      VVHRoadlink(rl.linkId, municipalityCode, rl.geometry, rl.administrativeClass, rl.trafficDirection, FeatureClass.DrivePath, None, Map()))

    val changeInfo =
      Seq(ChangeInfo(Option(5469033), Option(6798918), 6798918, 1, Option(0.0), Option(92.949498199999994), Option(2.35612749), Option(95.297583340000003), 1471647624000L),
        ChangeInfo(Option(5469032), Option(6798918), 6798918, 2, Option(0.0), Option(97.350365389999993), Option(95.297583340000003), Option(183.0270462), 1471647624000L),
        ChangeInfo(Option(22917), Option(6808127), 6808127, 1, Option(0.0), Option(256.77566583999999), Option(0), Option(256.77566583999999), 1472684413000L),
        ChangeInfo(Option(22643), Option(6808127), 6808127, 2, Option(0.0), Option(182.62904929999999), Option(256.77566583999999), Option(439.40471513), 1472684413000L),
        ChangeInfo(Option(2201030), Option(6808222), 6808222, 1, Option(0.0), Option(102.77169051), Option(45.80984256), Option(148.58153307000001), 1472684413000L),
        ChangeInfo(Option(2201031), Option(6808222), 6808222, 2, Option(0.0), Option(45.80984256), Option(0), Option(45.80984256), 1472684413000L),
        ChangeInfo(Option(2204342), Option(6808234), 6808234, 1, Option(0.0), Option(159.76164267999999), Option(34.594686179999997), Option(194.35632885999999), 1472684413000L),
        ChangeInfo(Option(2204346), Option(6808234), 6808234, 2, Option(0.0), Option(34.594686179999997), Option(0), Option(34.594686179999997), 1472684413000L),
        ChangeInfo(Option(2205372), Option(6808258), 6808258, 1, Option(0.0), Option(221.05559743000001), Option(66.343229129999997), Option(287.39882655999997), 1472684413000L),
        ChangeInfo(Option(2205373), Option(6808258), 6808258, 2, Option(0.0), Option(66.343229129999997), Option(0), Option(66.343229129999997), 1472684413000L),
        ChangeInfo(Option(3170857), Option(6808324), 6808324, 1, Option(0.0), Option(170.68020304000001), Option(0), Option(170.68020304000001), 1472684413000L),
        ChangeInfo(Option(3170862), Option(6808324), 6808324, 2, Option(0.0), Option(62.86203708), Option(170.68020304000001), Option(233.54224012), 1472684413000L),
        ChangeInfo(Option(4424920), Option(6808402), 6808402, 5, Option(0.0), Option(57.402494449999999), Option(0), Option(57.402494449999999), 1472684413000L))

    val assetData = Seq(
      ("1850798", "20", "02.07.2015 10:54:30", "split_speedlimit_1175012", "0"),
      ("2224155", "20", "02.07.2015 12:43:13", "split_speedlimit_1288833", "0"),
      ("2224613", "20", "02.07.2015 12:43:19", "split_speedlimit_1288879", "0"),
      ("2244404", "20", "02.07.2015 12:48:09", "split_speedlimit_1291211", "0"),
      ("2244414", "20", "02.07.2015 12:48:10", "split_speedlimit_1291211", "0"),
      ("2257662", "20", "02.07.2015 12:51:25", "split_speedlimit_1292796", "0"),
      ("2257665", "20", "02.07.2015 12:51:25", "split_speedlimit_1292796", "0"),
      ("2290194", "20", "02.07.2015 12:59:28", "split_speedlimit_1298288", "0"),
      ("2381817", "20", "02.07.2015 13:22:32", "split_speedlimit_1315437", "0"),
      ("2381825", "20", "02.07.2015 13:22:32", "split_speedlimit_1315437", "0"),
      ("2386962", "20", "02.07.2015 13:23:53", "split_speedlimit_1315942", "0"),
      ("2392492", "20", "02.07.2015 13:25:13", "split_speedlimit_1316465", "0"),
      ("2516235", "20", "02.07.2015 14:03:50", "split_speedlimit_1328525", "0"),
      ("22696668", "20", "02.07.2015 10:54:30", "split_speedlimit_1175012", "0"),
      ("22696716", "20", "02.07.2015 10:54:30", "split_speedlimit_1175012", "0"),
      ("22696720", "20", "02.07.2015 10:54:30", "split_speedlimit_1175012", "0")
    )

    val lrmData = Seq(
      ("1850798", "41635739", "", "1", "0", "97,35", "540089958", "5469032", "0", "02.07.2015 10:54:30"),
      ("2224155", "42009096", "", "1", "0", "182,629", "420491248", "22643", "0", "09.08.2016 11:37:19"),
      ("2224613", "42009554", "", "1", "0", "256,769", "818575129", "22917", "0", "02.07.2015 12:43:19"),
      ("2244404", "42029345", "", "1", "0", "159,762", "68706862", "2204342", "0", "02.07.2015 12:48:09"),
      ("2244414", "42029355", "", "1", "0", "34,595", "68706856", "2204346", "0", "02.07.2015 12:48:10"),
      ("2257662", "42042603", "", "1", "0", "66,343", "68654169", "2205373", "0", "02.07.2015 12:51:25"),
      ("2257665", "42042606", "", "1", "0", "210,106", "68654924", "2205372", "0", "09.08.2016 11:53:12"),
      ("2290194", "42075135", "", "1", "210,106", "221,056", "68654924", "2205372", "0", "09.08.2016 11:53:12"),
      ("2381817", "42166758", "", "1", "0,003", "45,81", "785898029", "2201031", "0", "02.07.2015 13:22:32"),
      ("2381825", "42166766", "", "1", "0", "102,772", "68654511", "2201030", "0", "02.07.2015 13:22:32"),
      ("2386962", "42171903", "", "1", "0,003", "306,088", "326195837", "4424920", "0", "02.07.2015 13:23:53"),
      ("2392492", "42177433", "", "1", "0", "170,68", "398139531", "3170857", "0", "02.07.2015 13:25:13"),
      ("2516235", "42301176", "", "1", "0,011", "62,862", "398161817", "3170862", "0", "02.07.2015 14:03:50"),
      ("22696668", "46776579", "", "1", "2,356", "95,297", "", "6798918", "1471647624000", "25.08.2016 14:11:36"),
      ("22696716", "46776627", "", "1", "95,298", "183,027", "", "6798918", "1471647624000", "25.08.2016 14:11:37"),
      ("22696720", "46776631", "", "1", "0", "95,297", "", "6798918", "1471647624000", "25.08.2016 14:11:37")
    )

    val fifties = Seq("1850798", "22696668", "22696716", "22696720")

    OracleDatabase.withDynTransaction {
      sqlu"""DELETE FROM ASSET_LINK""".execute
      assetData.foreach {
        case (id, _, createdDate, createdBy, _) =>
          sqlu"""Insert into ASSET (ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,VALID_FROM,FLOATING) values ($id,'20',to_timestamp($createdDate,'DD.MM.RRRR HH24:MI:SS'),$createdBy, sysdate, '0')""".execute
          if (fifties.contains(id)) {
            sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT $id,(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 50 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
          } else {
            sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT $id,(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 80 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
          }
      }

      lrmData.foreach {
        case (assetId, id, _, sideCode, startM, endM, mmlId, linkId, adjTimeStamp, modDate) =>
          sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ($id,null,$sideCode,$startM,$endM,$mmlId,$linkId,$adjTimeStamp,to_timestamp($modDate,'DD.MM.RRRR HH24:MI:SS'))""".execute
          sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ($assetId,$id)""".execute
      }

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((roadLinks, changeInfo))
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[Int])).thenReturn((roadLinks, changeInfo))

      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(any[Set[Long]])).thenReturn(vvhRoadLinks)

      val topology = service.get(municipalityCode)
      topology.forall(sl => sl.id == 22696720 || sl.id == 0)  should be (true)
      topology.exists(_.id == 22696720) should be (true)
      topology.find(_.id == 22696720).get.value.getOrElse(0) should be (NumericValue(50))
      topology.forall(sl => sl.vvhTimeStamp > 0) should be (true)
      val newLimits = topology.filter(_.id == 0).map(sl => {
        val aId = Sequences.nextPrimaryKeySeqValue
        val lrmId = Sequences.nextLrmPositionPrimaryKeySeqValue
        (sl.value.get.value, (
          (aId.toString, "20", "09.09.2016 12:00:00", "test_generated", "0"),
          (aId.toString, lrmId.toString, "", sl.sideCode.value.toString, sl.startMeasure.toString, sl.endMeasure.toString,
            "0", sl.linkId.toString, sl.vvhTimeStamp.toString, "09.09.2016 12:00:00")))
      }
      )
      val save = newLimits.groupBy(_._1).mapValues(x => x.map(_._2))
      save.foreach {
        case (limit, data) => saveSpeedLimits(limit, data)
        case _ => throw new RuntimeException("invalid data")
      }

      // Stability check
      val topologyAfterSave = service.get(municipalityCode)
      topologyAfterSave.forall(sl =>
        topology.count(x =>
          x.linkId == sl.linkId && x.startMeasure == sl.startMeasure &&
            x.endMeasure == sl.endMeasure && x.value.get == sl.value.get) == 1) should be (true)
      dynamicSession.rollback()
    }
  }

  /**
    * Raw SQL table columns for asset (some removed), raw sql table columns for lrm position added with asset id
 *
    * @param speed
    * @param data
    */
  private def saveSpeedLimits(speed: Int, data: Seq[((String, String, String, String, String),
                              (String, String, String, String, String, String, String, String, String, String))]) = {
    val assetData = data.map(_._1)
    val lrmData = data.map(_._2)
    assetData.foreach {
      case (id, _, createdDate, createdBy, _) =>
        sqlu"""Insert into ASSET (ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,VALID_FROM,FLOATING) values ($id,'20',to_timestamp($createdDate,'DD.MM.RRRR HH24:MI:SS'),$createdBy, sysdate, '0')""".execute
        sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT $id,(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = $speed and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
    }
    lrmData.foreach {
      case (assetId, id, _, sideCode, startM, endM, mmlId, linkId, adjTimeStamp, modDate) =>
        sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ($id,null,$sideCode,${startM.toDouble},${endM.toDouble},$mmlId,$linkId,$adjTimeStamp,to_timestamp($modDate,'DD.MM.RRRR HH24:MI:SS'))""".execute
        sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ($assetId,$id)""".execute
    }
  }

  ignore ("Should stabilize on overlapping speed limits") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val eventBus = MockitoSugar.mock[DigiroadEventBus]
    val service = new SpeedLimitService(eventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val municipalityCode = 286
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val geometries = Seq(
      List(Point(381278.36,6726748.869,95.254000000000815),Point(381284.253,6726788.607,94.850999999995111),Point(381287.134,6726809.171,94.634999999994761),Point(381289.278,6726829.874,94.448000000003958),Point(381290.115,6726844.265,94.30899999999383))
    )
    val linkIds = Seq(
      602156
    )

    val roadLinks = geometries.zip(linkIds).map {
      case (geometry, linkId) => RoadLink(linkId, geometry, GeometryUtils.geometryLength(geometry),
        administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    }

    val vvhRoadLinks = roadLinks.map(rl =>
      VVHRoadlink(rl.linkId, municipalityCode, rl.geometry, rl.administrativeClass, rl.trafficDirection, FeatureClass.DrivePath, None, Map()))
    val changeInfo =
      Seq(ChangeInfo(Option(602156), Option(602156), 6798918, 7, Option(10.52131863), Option(106.62158114), Option(0.0), Option(96.166451179999996), 1459452603000L))

    val assetData = Seq(
      ("1300665", "20", "28.10.2014 15:32:25", "dr1_conversion", "0"),
      ("2263876", "20", "02.07.2015 12:52:59", "split_speedlimit_1293482", "0"),
      ("2317092", "20", "02.07.2015 13:06:27", "split_speedlimit_1302820", "0")
    )

    val lrmData = Seq(
      ("1300665", "40744223", "", "2", "0", "78,106", "329512802", "602156", "1459452603000", "09.08.2016 11:38:49"),
      ("2263876", "42048817", "", "2", "78,106", "85,704", "329512802", "602156", "1459452603000", "09.08.2016 11:38:49"),
      ("2317092", "42102033", "", "3", "0", "96,166", "329512802", "602156", "1459452603000", "09.09.2016 09:04:05")
    )

    val eighties = Seq("2263876")

    OracleDatabase.withDynTransaction {
      sqlu"""DELETE FROM ASSET_LINK""".execute
      assetData.foreach {
        case (id, _, createdDate, createdBy, _) =>
          sqlu"""Insert into ASSET (ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,VALID_FROM,FLOATING) values ($id,'20',to_timestamp($createdDate,'DD.MM.RRRR HH24:MI:SS'),$createdBy, sysdate, '0')""".execute
          if (eighties.contains(id)) {
            sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT $id,(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 80 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
          } else {
            sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) SELECT $id,(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 60 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.RRRR HH24:MI:SS'),null from dual""".execute
          }
      }

      lrmData.foreach {
        case (assetId, id, _, sideCode, startM, endM, mmlId, linkId, adjTimeStamp, modDate) =>
          sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ($id,null,$sideCode,$startM,$endM,$mmlId,$linkId,$adjTimeStamp,to_timestamp($modDate,'DD.MM.RRRR HH24:MI:SS'))""".execute
          sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ($assetId,$id)""".execute
      }

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((roadLinks, changeInfo))
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[Int])).thenReturn((roadLinks, changeInfo))

      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(any[Set[Long]])).thenReturn(vvhRoadLinks)

      val topology = service.get(municipalityCode).sortBy(_.startMeasure).sortBy(_.linkId)
      topology.filter(_.id != 0).foreach(sl => service.dao.updateMValues(sl.id, (sl.startMeasure, sl.endMeasure), sl.vvhTimeStamp))
      val newLimits = topology.filter(_.id == 0).map(sl => {
        val aId = Sequences.nextPrimaryKeySeqValue
        val lrmId = Sequences.nextLrmPositionPrimaryKeySeqValue
        (sl.value.get.value, (
          (aId.toString, "20", "09.09.2016 12:00:00", "test_generated", "0"),
          (aId.toString, lrmId.toString, "", sl.sideCode.value.toString, sl.startMeasure.toString, sl.endMeasure.toString,
            "0", sl.linkId.toString, sl.vvhTimeStamp.toString, "09.09.2016 12:00:00")))
      }
      )
      val save = newLimits.groupBy(_._1).mapValues(x => x.map(_._2))
      save.foreach {
        case (limit, data) => saveSpeedLimits(limit, data)
        case _ => throw new RuntimeException("invalid data")
      }

      // Stability check
      val topologyAfterSave = service.get(municipalityCode)

      topologyAfterSave.forall(_.id != 0) should be (true)

      topologyAfterSave.forall(sl =>
        topology.count(x =>
          (x.id == sl.id || x.id == 0) &&
          x.linkId == sl.linkId && x.startMeasure == sl.startMeasure &&
            x.endMeasure == sl.endMeasure && x.value.get == sl.value.get) == 1) should be (true)
      dynamicSession.rollback()
    }
  }

  def printSL(speedLimit: SpeedLimit) = {
    val ids = "%d (%d)".format(speedLimit.id, speedLimit.linkId)
    val dir = speedLimit.sideCode match {
      case SideCode.BothDirections => "⇅"
      case SideCode.TowardsDigitizing => "↑"
      case SideCode.AgainstDigitizing => "↓"
      case _ => "?"
    }
    val details = "%d %.1f %.1f".format(speedLimit.value.getOrElse(NumericValue(0)).value, speedLimit.startMeasure, speedLimit.endMeasure)
    if (speedLimit.expired) {
      println("N/A")
    } else {
      println("%s %s %s".format(ids, dir, details))
    }
  }

  test("Should filter out speed limits on walkways from TN-ITS message") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val service = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val roadLink1 = RoadLink(100, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 8, TrafficDirection.BothDirections, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink2 = RoadLink(200, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 5, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink3 = RoadLink(300, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 7, TrafficDirection.BothDirections, TractorRoad, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm1, 100)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset1, 20, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1, $lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id) values ($asset1,(select id from enumerated_value where value = 50),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm2, 200)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset2, 20, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2, $lrm2)""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id) values ($asset2,(select id from enumerated_value where value = 50),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm3, 300)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset3, 20, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3, $lrm3)""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id) values ($asset3,(select id from enumerated_value where value = 50),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndComplementaryByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

      val result = service.getChanged(DateTime.parse("2016-11-01T12:00Z"), DateTime.parse("2016-11-02T12:00Z"))
      result.length should be(1)
      result.head.link.linkType should not be (TractorRoad)
      result.head.link.linkType should not be (CycleOrPedestrianPath)

      dynamicSession.rollback()
    }
  }
}