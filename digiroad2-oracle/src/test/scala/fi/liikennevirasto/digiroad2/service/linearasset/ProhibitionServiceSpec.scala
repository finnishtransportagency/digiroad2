package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.{Municipality, _}
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.{Saturday, Weekday}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class ProhibitionServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]

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
  when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(1), "mittarajoitus", false))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockManoeuvreService = MockitoSugar.mock[ManoeuvreService]


  val trafficSignService = new TrafficSignService(mockRoadLinkService, new DummyEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  object ServiceWithDao extends ProhibitionService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Should map hazmat prohibitions of two old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new ProhibitionService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
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

    PostGISDatabase.withDynTransaction {
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
      val prohibitionBothDirections = Prohibitions(Seq(ProhibitionValue(24, Set.empty, Set.empty, "")))
      val linearAssetTowardsDigitizing = after.filter(p => p.sideCode == SideCode.TowardsDigitizing).head
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set.empty, "")))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, "")))

      linearAssetBothDirections.value should be (Some(prohibitionBothDirections))
      linearAssetTowardsDigitizing.value should be (Some(prohibitionTowardsDigitizing))
      linearAssetAgainstDigitizing.value should be (Some(prohibitionAgainstDigitizing))

      dynamicSession.rollback()
    }
  }

  test("Update prohibition") {
    when(mockVVHRoadLinkClient.fetchByLinkId(1610349)).thenReturn(Some(VVHRoadlink(1610349, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
    runWithRollback {
      val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val asset = ServiceWithDao.create(Seq(NewLinearAsset(1610349, 0, 20, prohibition, 1, 0, None)), 190, "testUser")

      when(mockAssetDao.getAssetTypeId(Seq(asset.head))).thenReturn(Seq((asset.head, LinearAssetTypes.ProhibitionAssetTypeId)))
      ServiceWithDao.update(Seq(asset.head), Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty))), "lol")

      val limit = linearAssetDao.fetchProhibitionsByLinkIds(LinearAssetTypes.ProhibitionAssetTypeId, Seq(1610349)).filter(_.createdBy.get == "testUser").head

      limit.value should be (Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))))
      limit.expired should be (false)
    }
  }

  test("Create new prohibition") {
    val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
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
      val assetId = ServiceWithDao.create(Seq(newLimit), LinearAssetTypes.ProhibitionAssetTypeId, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2), "")))

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, LinearAssetTypes.ProhibitionAssetTypeId)))

      val createdId = ServiceWithDao.separate(assetId, Some(prohibitionA), Some(prohibitionB), "unittest", (i, _) => Unit)
      val createdProhibition = ServiceWithDao.getPersistedAssetsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(createdId(1))).head
      val oldProhibition = ServiceWithDao.getPersistedAssetsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(createdId.head)).head

      val limits = linearAssetDao.fetchProhibitionsByLinkIds(LinearAssetTypes.ProhibitionAssetTypeId, Seq(388562360))

      oldProhibition.linkId should be (388562360)
      oldProhibition.sideCode should be (SideCode.TowardsDigitizing.value)
      oldProhibition.value should be (Some(prohibitionA))
      oldProhibition.modifiedBy should be (None)

      createdProhibition.linkId should be (388562360)
      createdProhibition.sideCode should be (SideCode.AgainstDigitizing.value)
      createdProhibition.value should be (Some(prohibitionB))
      createdProhibition.createdBy should be (Some("unittest"))
    }
  }

  test("Split prohibition") {
    runWithRollback {
      val newProhibition = NewLinearAsset(388562360, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newProhibition), LinearAssetTypes.ProhibitionAssetTypeId, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2), "")))

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, LinearAssetTypes.ProhibitionAssetTypeId)))
      val ids = ServiceWithDao.split(assetId, 6.0, Some(prohibitionA), Some(prohibitionB), "unittest", (i, _) => Unit)
      val createdId = ids(1)
      val createdProhibition = ServiceWithDao.getPersistedAssetsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(createdId)).head
      val oldProhibition = ServiceWithDao.getPersistedAssetsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(ids.head)).head

      oldProhibition.linkId should be (388562360)
      oldProhibition.sideCode should be (SideCode.BothDirections.value)
      oldProhibition.value should be (Some(prohibitionA))
      oldProhibition.modifiedBy should be (None)
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
      override def withDynSession[T](f: => T): T = f
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

    PostGISDatabase.withDynTransaction {
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
      val prohibitionBothDirections = Prohibitions(Seq(ProhibitionValue(24, Set.empty, Set(10), "")))
      val linearAssetTowardsDigitizing = after.filter(p => p.sideCode == SideCode.TowardsDigitizing).head
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set(10), "")))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set(10), "")))

      linearAssetBothDirections.value should be (Some(prohibitionBothDirections))
      linearAssetTowardsDigitizing.value should be (Some(prohibitionTowardsDigitizing))
      linearAssetAgainstDigitizing.value should be (Some(prohibitionAgainstDigitizing))

      dynamicSession.rollback()
    }
  }

  test("Create prohibitions on actor update when exist sideCode adjustment") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp(-5)
    when(mockRoadLinkService.vvhClient).thenReturn(mockVVHClient)
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)

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

    val roadLinkWithLinkSource = RoadLink(
      oldLinkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))

    PostGISDatabase.withDynTransaction {
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

      val changeSet = projectedProhibitions.foldLeft(ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil)) {
        case (acc, proj) =>
          acc.copy(adjustedMValues = acc.adjustedMValues ++ Seq(MValueAdjustment(proj.id, proj.linkId, proj.startMeasure, proj.endMeasure)), adjustedVVHChanges=acc.adjustedVVHChanges, adjustedSideCodes = acc.adjustedSideCodes ++ Seq(SideCodeAdjustment(proj.id, SideCode.apply(proj.sideCode), original.typeId)))
      }

      service.updateChangeSet(changeSet)
      val all = service.dao.fetchProhibitionsByIds(assetTypeId, Set(asset1,asset2,asset3), false)
      all.size should be (2)
      val persisted = service.dao.fetchProhibitionsByLinkIds(assetTypeId, Seq(oldLinkId1), includeFloating = false)
      persisted.size should be (2)
      persisted.exists(_.id == asset2) should be (true)
      persisted.exists(_.id == asset1) should be (false)
      val newAsset = persisted.find(_.id !== asset2).get
      newAsset.id should not be original.id
      newAsset.startMeasure should be (0.1)
      newAsset.endMeasure should be (10.1)
      newAsset.expired should be (false)

      dynamicSession.rollback()
    }
  }

  test("Should extend vehicle prohibition on road extension") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new ProhibitionService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
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

    PostGISDatabase.withDynTransaction {
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
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set(10), "")))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set(10), "")))

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

  test("Adjust projected asset with creation"){
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]

    val service = new ProhibitionService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId = 6000
    val municipalityCode = 444
    val functionalClass = 1
    val assetTypeId = 190
    val geom = List(Point(0, 0), Point(300, 0))
    val len = GeometryUtils.geometryLength(geom)

    val roadLinks = Seq(RoadLink(oldLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId), Some(newLinkId), 1204467577, 1, Some(0), Some(150), Some(100), Some(200), 1461970812000L))

    PostGISDatabase.withDynTransaction {
      val (lrm1) = (Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1) = (Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values ($asset1,$asset1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values ($asset1,$asset1,1,11,12)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600010, $asset1, 10)""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((roadLinks, changeInfo))
      service.getByMunicipality(assetTypeId, municipalityCode)

      verify(mockEventBus, times(1))
        .publish("prohibition:update", ChangeSet(Set.empty[Long], Nil, Nil, Nil, Set.empty[Long], Nil))

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("prohibition:saveProjectedProhibition"), captor.capture())
      val projectedAssets = captor.getValue.asInstanceOf[Seq[PersistedLinearAsset]]
      projectedAssets.length should be(1)
      projectedAssets.foreach { proj =>
        proj.id should be (0)
        proj.linkId should be (6000)
      }
      dynamicSession.rollback()
    }
  }

  test("get unVerified prohibition assets") {
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(Set(235))).thenReturn(List(MunicipalityInfo(235, 9, "Kauniainen")))
    runWithRollback {
      val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(1, 0, 20, prohibition, 1, 0, None)), 190, "dr1_conversion")
      val newAssets2 = ServiceWithDao.create(Seq(NewLinearAsset(1, 20, 60, prohibition, 1, 0, None)), 190, "testuser")

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(190, Set())
      unVerifiedAssets.keys.head should be ("Kauniainen")
      unVerifiedAssets.flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.flatMap(_._2).values.head should be (newAssets1)
    }
  }

  test("Update prohibition and verify asset") {
    runWithRollback {
      val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val asset = ServiceWithDao.create(Seq(NewLinearAsset(1610349, 0, 20, prohibition, 1, 0, None)), 190, "testUser")

      when(mockAssetDao.getAssetTypeId(Seq(asset.head))).thenReturn(Seq((asset.head, LinearAssetTypes.ProhibitionAssetTypeId)))
      ServiceWithDao.update(Seq(asset.head), Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty))), "testUser")

      val limit = linearAssetDao.fetchProhibitionsByLinkIds(LinearAssetTypes.ProhibitionAssetTypeId, Seq(1610349)).filter(_.createdBy.get == "testUser").head

      limit.verifiedBy should be (Some("testUser"))
      limit.verifiedDate.get.toString("yyyy-MM-dd") should be (DateTime.now().toString("yyyy-MM-dd"))
      limit.value should be (Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))))
      limit.expired should be (false)
    }
  }

  test("Update verified info prohibitions") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new ProhibitionService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    PostGISDatabase.withDynTransaction {
      val assetNotVerified = service.dao.fetchProhibitionsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(600020, 600024), false)
      service.updateVerifiedInfo(Set(600020, 600024), "test", LinearAssetTypes.ProhibitionAssetTypeId)
      val verifiedAsset = service.dao.fetchProhibitionsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(600020, 600024), false)
      assetNotVerified.find(_.id == 600020).flatMap(_.verifiedBy) should  be (None)
      assetNotVerified.find(_.id == 600024).flatMap(_.verifiedBy) should be (None)
      verifiedAsset.find(_.id == 600020).flatMap(_.verifiedBy) should be (Some("test"))
      verifiedAsset.find(_.id == 600024).flatMap(_.verifiedBy) should be (Some("test"))
      verifiedAsset.find(_.id == 600020).flatMap(_.verifiedDate).get.toString("yyyy-MM-dd") should be (DateTime.now().toString("yyyy-MM-dd"))
      verifiedAsset.find(_.id == 600024).flatMap(_.verifiedDate).get.toString("yyyy-MM-dd") should be (DateTime.now().toString("yyyy-MM-dd"))

      dynamicSession.rollback()
    }
  }
}
