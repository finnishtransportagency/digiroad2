package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.{Saturday, Weekday}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class ProhibitionServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(1), "mittarajoitus", false))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  object ServiceWithDao extends ProhibitionService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(ServiceWithDao.dataSource)(test)

  test("Should map hazmat prohibitions of two old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new ProhibitionService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 210
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset1,$asset1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset1,$asset1,1,11,12)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset2,$asset2,25)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset2,$asset2,2,12,13)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset3,$asset3,24)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (4)
      before.count(_.value.nonEmpty) should be (3)

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (2)
      linearAssets1.head.startMeasure should be (0)
      linearAssets1.head.endMeasure should be (10)
      val linearAssets2 = beforeByLinkId(oldLinkId2)
      linearAssets2.length should be (2)
      linearAssets2.filter(l => l.id > 0).head.startMeasure should be (0)
      linearAssets2.filter(l => l.id > 0).head.endMeasure should be (5)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      //      after.foreach(println)
      after.length should be(4)
      after.count(_.value.nonEmpty) should be (3)

      val linearAssetBothDirections = after.filter(p => (p.sideCode == SideCode.BothDirections) && p.value.nonEmpty).head
      val prohibitionBothDirections = Prohibitions(Seq(ProhibitionValue(24, Set.empty, Set.empty, null)))
      val linearAssetTowardsDigitizing = after.filter(p => p.sideCode == SideCode.TowardsDigitizing).head
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set.empty, null)))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null)))

      linearAssetBothDirections.value should be (Some(prohibitionBothDirections))
      linearAssetTowardsDigitizing.value should be (Some(prohibitionTowardsDigitizing))
      linearAssetAgainstDigitizing.value should be (Some(prohibitionAgainstDigitizing))

      dynamicSession.rollback()
    }
  }

  test("Update prohibition") {
    when(mockVVHRoadLinkClient.fetchByLinkId(1610349)).thenReturn(Some(VVHRoadlink(1610349, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

    runWithRollback {
      val ids = ServiceWithDao.update(Seq(600020l), Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty))), "lol")
      ids should have size (1)
      val limits = linearAssetDao.fetchProhibitionsByLinkIds(190, Seq(1610349))
      limits should have size(1)
      val limit = limits.head

      limit.value should be (Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, null)))))
      limit.expired should be (false)
    }
  }

  test("Create new prohibition") {
    val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, null)))
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, prohibition, 1, 0, None)), 190, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchProhibitionsByLinkIds(190, Seq(388562360l)).head
      asset.value should be (Some(prohibition))
      asset.expired should be (false)
    }
  }

  test("Separate prohibition asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 190, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, null)))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2), null)))

      val createdId = ServiceWithDao.separate(assetId, Some(prohibitionA), Some(prohibitionB), "unittest", (i) => Unit)
      val createdProhibition = ServiceWithDao.getPersistedAssetsByIds(190, Set(createdId(1))).head
      val oldProhibition = ServiceWithDao.getPersistedAssetsByIds(190, Set(createdId.head)).head

      val limits = linearAssetDao.fetchProhibitionsByLinkIds(190, Seq(388562360))

      oldProhibition.linkId should be (388562360)
      oldProhibition.sideCode should be (SideCode.TowardsDigitizing.value)
      oldProhibition.value should be (Some(prohibitionA))
      oldProhibition.modifiedBy should be (Some("unittest"))

      createdProhibition.linkId should be (388562360)
      createdProhibition.sideCode should be (SideCode.AgainstDigitizing.value)
      createdProhibition.value should be (Some(prohibitionB))
      createdProhibition.createdBy should be (Some("unittest"))
    }
  }

  test("Split prohibition") {
    runWithRollback {
      val newProhibition = NewLinearAsset(388562360, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newProhibition), 190, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, null)))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2), null)))

      val ids = ServiceWithDao.split(assetId, 6.0, Some(prohibitionA), Some(prohibitionB), "unittest", (i) => Unit)
      val createdId = ids(1)
      val createdProhibition = ServiceWithDao.getPersistedAssetsByIds(190, Set(createdId)).head
      val oldProhibition = ServiceWithDao.getPersistedAssetsByIds(190, Set(ids.head)).head

      oldProhibition.linkId should be (388562360)
      oldProhibition.sideCode should be (SideCode.BothDirections.value)
      oldProhibition.value should be (Some(prohibitionA))
      oldProhibition.modifiedBy should be (Some("unittest"))
      oldProhibition.startMeasure should be (0.0)
      oldProhibition.endMeasure should be (6.0)

      createdProhibition.linkId should be (388562360)
      createdProhibition.sideCode should be (SideCode.BothDirections.value)
      createdProhibition.value should be (Some(prohibitionB))
      createdProhibition.createdBy should be (Some("unittest"))
      createdProhibition.startMeasure should be (6.0)
      createdProhibition.endMeasure should be (10.0)
    }
  }

  test("Should map vehicle prohibition of two old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new ProhibitionService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 190
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset1,$asset1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset1,$asset1,1,11,12)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600010, $asset1, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset2,$asset2,25)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset2,$asset2,2,12,13)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600011, $asset2, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset3,$asset3,24)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600012, $asset3, 10)""".execute


      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (4)
      before.count(_.value.nonEmpty) should be (3)

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (2)
      linearAssets1.head.startMeasure should be (0)
      linearAssets1.head.endMeasure should be (10)

      val linearAssets2 = beforeByLinkId(oldLinkId2)
      linearAssets2.length should be (2)
      linearAssets2.filter(l => l.id > 0).head.startMeasure should be (0)
      linearAssets2.filter(l => l.id > 0).head.endMeasure should be (5)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten
      //      after.foreach(println)
      after.length should be(4)
      after.count(_.value.nonEmpty) should be (3)

      val linearAssetBothDirections = after.filter(p => (p.sideCode == SideCode.BothDirections) && p.value.nonEmpty).head
      val prohibitionBothDirections = Prohibitions(Seq(ProhibitionValue(24, Set.empty, Set(10), null)))
      val linearAssetTowardsDigitizing = after.filter(p => p.sideCode == SideCode.TowardsDigitizing).head
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set(10), null)))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set(10), null)))

      linearAssetBothDirections.value should be (Some(prohibitionBothDirections))
      linearAssetTowardsDigitizing.value should be (Some(prohibitionTowardsDigitizing))
      linearAssetAgainstDigitizing.value should be (Some(prohibitionAgainstDigitizing))

      dynamicSession.rollback()
    }
  }

  test("Should not create prohibitions on actor update") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new ProhibitionService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 1234
    val oldLinkId2 = 1235
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 190
    val vvhTimeStamp = 14440000

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset1,$asset1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset1,$asset1,1,11,12)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600010, $asset1, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset2,$asset2,25)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset2,$asset2,2,12,13)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600011, $asset2, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset3,$asset3,24)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600012, $asset3, 10)""".execute


      val original = service.getPersistedAssetsByIds(assetTypeId, Set(asset1)).head
      val projectedProhibitions = Seq(original.copy(startMeasure = 0.1, endMeasure = 10.1, sideCode = 1, vvhTimeStamp = vvhTimeStamp))

      service.persistProjectedLinearAssets(projectedProhibitions)
      val all = service.dao.fetchProhibitionsByIds(assetTypeId, Set(asset1,asset2,asset3), false)
      all.size should be (3)
      val persisted = service.getPersistedAssetsByIds(assetTypeId, Set(asset1))
      persisted.size should be (1)
      val head = persisted.head
      head.id should be (original.id)
      head.vvhTimeStamp should be (vvhTimeStamp)
      head.startMeasure should be (0.1)
      head.endMeasure should be (10.1)
      head.expired should be (false)

      dynamicSession.rollback()
    }
  }

  test("Should extend vehicle prohibition on road extension") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new ProhibitionService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 6000
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 190
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 3, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(None, Some(newLinkId), 12345, 4, None, None, Some(10), Some(20), 144000000))

    OracleDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset1,$asset1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset1,$asset1,1,11,12)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600010, $asset1, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId1, 0, 9.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset2,$asset2,25)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset2,$asset2,2,12,13)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600011, $asset2, 10)""".execute



      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (3)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      //      after.foreach(println)
      after.length should be(3)
      after.count(_.value.nonEmpty) should be (2)

      val linearAssetTowardsDigitizing = after.filter(p => p.sideCode == SideCode.TowardsDigitizing).head
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set(10), null)))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set(10), null)))

      linearAssetTowardsDigitizing.value should be (Some(prohibitionTowardsDigitizing))
      linearAssetAgainstDigitizing.value should be (Some(prohibitionAgainstDigitizing))
      linearAssetAgainstDigitizing.startMeasure should be (0.0)
      linearAssetAgainstDigitizing.endMeasure should be (20.0)

      linearAssetTowardsDigitizing.startMeasure should be (0.0)
      linearAssetTowardsDigitizing.endMeasure should be (9.0)

      dynamicSession.rollback()
    }
  }

  test("Two prohibitions with same exceptions and validityPeriods in diferent order should return true"){
    val validityPeriods1 = Set(ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Saturday, 1, 3), ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Unknown, 3, 3))
    val prohibition1 = Prohibitions(Seq(ProhibitionValue(1, validityPeriods1, Set(1,3), "test")))
    val validityPeriods2 = Set(ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Unknown, 3, 3), ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Saturday, 1, 3))
    val prohibition2 = Prohibitions(Seq(ProhibitionValue(1, validityPeriods2, Set(3,1), "test")))
    prohibition1 == prohibition2 should be (true)
  }

  test("Two prohibitions without same exceptions and the same validityPeriods in diferent order should return false"){
    val validityPeriods1 = Set(ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Saturday, 1, 3), ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Unknown, 3, 3))
    val prohibition1 = Prohibitions(Seq(ProhibitionValue(1, validityPeriods1, Set(1,3), "test")))
    val validityPeriods2 = Set(ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Unknown, 3, 3), ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Saturday, 1, 3))
    val prohibition2 = Prohibitions(Seq(ProhibitionValue(1, validityPeriods2, Set(2,1), "test")))
    prohibition1 == prohibition2 should be (false)
  }

  test("Two prohibitions with same exceptions and the without same validityPeriods in diferent order should return false"){
    val validityPeriods1 = Set(ValidityPeriod(1,1, ValidityPeriodDayOfWeek.Saturday, 1, 3), ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Unknown, 3, 3))
    val prohibition1 = Prohibitions(Seq(ProhibitionValue(1, validityPeriods1, Set(1,3), "test")))
    val validityPeriods2 = Set(ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Unknown, 3, 3), ValidityPeriod(0,1, ValidityPeriodDayOfWeek.Saturday, 1, 3))
    val prohibition2 = Prohibitions(Seq(ProhibitionValue(1, validityPeriods2, Set(3,1), "test")))
    prohibition1 == prohibition2 should be (false)
  }

}
