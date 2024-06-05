package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.{Saturday, Weekday}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools, TestTransactions}
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
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]

  val (linkId1, linkId2) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())

  when(mockRoadLinkService.fetchByLinkId(linkId1)).thenReturn(Some(RoadLinkFetched(linkId1, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq(RoadLinkFetched(linkId1, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(any[String])).thenReturn(Some(RoadLinkFetched(linkId1, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = RoadLink(
    linkId2, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(any[String], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(linkId2), "mittarajoitus", false))
    .thenReturn(Seq(PersistedLinearAsset(1, linkId2, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao()
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
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)


  test("Update prohibition") {
    val linkId = LinkIdGenerator.generateRandom()
    when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(linkId)).thenReturn(Some(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
    runWithRollback {
      val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val asset = ServiceWithDao.create(Seq(NewLinearAsset(linkId, 0, 20, prohibition, 1, 0, None)), 190, "testUser")

      when(mockAssetDao.getAssetTypeId(Seq(asset.head))).thenReturn(Seq((asset.head, LinearAssetTypes.ProhibitionAssetTypeId)))
      ServiceWithDao.update(Seq(asset.head), Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty))), "lol")

      val limit = linearAssetDao.fetchProhibitionsByLinkIds(LinearAssetTypes.ProhibitionAssetTypeId, Seq(linkId)).filter(_.createdBy.get == "testUser").head

      limit.value should be (Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))))
      limit.expired should be (false)
    }
  }

  test("Create new prohibition") {
    val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 0, 20, prohibition, 1, 0, None)), 190, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchProhibitionsByLinkIds(190, Seq(linkId1)).head
      asset.value should be (Some(prohibition))
      asset.expired should be (false)
    }
  }

  test("Separate prohibition asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(linkId1, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), LinearAssetTypes.ProhibitionAssetTypeId, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2), "")))

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, LinearAssetTypes.ProhibitionAssetTypeId)))
     
      val createdId = ServiceWithDao.separate(assetId, Some(prohibitionA), Some(prohibitionB), "unittest", (i, _) => Unit,adjust = false)
      val createdProhibition = ServiceWithDao.getPersistedAssetsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(createdId(1))).head
      val oldProhibition = ServiceWithDao.getPersistedAssetsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(createdId.head)).head

      val limits = linearAssetDao.fetchProhibitionsByLinkIds(LinearAssetTypes.ProhibitionAssetTypeId, Seq(linkId1))

      oldProhibition.linkId should be (linkId1)
      oldProhibition.sideCode should be (SideCode.TowardsDigitizing.value)
      oldProhibition.value should be (Some(prohibitionA))
      oldProhibition.modifiedBy should be (None)

      createdProhibition.linkId should be (linkId1)
      createdProhibition.sideCode should be (SideCode.AgainstDigitizing.value)
      createdProhibition.value should be (Some(prohibitionB))
      createdProhibition.createdBy should be (Some("unittest"))
    }
  }

  test("Split prohibition") {
    runWithRollback {
      val newProhibition = NewLinearAsset(linkId1, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newProhibition), LinearAssetTypes.ProhibitionAssetTypeId, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2), "")))

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, LinearAssetTypes.ProhibitionAssetTypeId)))
      val ids = ServiceWithDao.split(assetId, 6.0, Some(prohibitionA), Some(prohibitionB), "unittest", (i, _) => Unit,false)
      val createdId = ids(1)
      val createdProhibition = ServiceWithDao.getPersistedAssetsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(createdId)).head
      val oldProhibition = ServiceWithDao.getPersistedAssetsByIds(LinearAssetTypes.ProhibitionAssetTypeId, Set(ids.head)).head

      oldProhibition.linkId should be (linkId1)
      oldProhibition.sideCode should be (SideCode.BothDirections.value)
      oldProhibition.value should be (Some(prohibitionA))
      oldProhibition.modifiedBy should be (None)
      oldProhibition.startMeasure should be (0.0)
      oldProhibition.endMeasure should be (6.0)

      createdProhibition.linkId should be (linkId1)
      createdProhibition.sideCode should be (SideCode.BothDirections.value)
      createdProhibition.value should be (Some(prohibitionB))
      createdProhibition.createdBy should be (Some("unittest"))
      createdProhibition.startMeasure should be (6.0)
      createdProhibition.endMeasure should be (10.0)
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

  test("get unVerified prohibition assets") {
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(Set(235))).thenReturn(List(MunicipalityInfo(235, 9, "Kauniainen")))
    runWithRollback {
      val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(linkId2, 0, 20, prohibition, 1, 0, None)), 190, AutoGeneratedUsername.dr1Conversion)
      val newAssets2 = ServiceWithDao.create(Seq(NewLinearAsset(linkId2, 20, 60, prohibition, 1, 0, None)), 190, "testuser")

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(190, Set())
      unVerifiedAssets.keys.head should be ("Kauniainen")
      unVerifiedAssets.flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.flatMap(_._2).values.head should be (newAssets1)
    }
  }

  test("Update prohibition and verify asset") {
    runWithRollback {
      val linkId = LinkIdGenerator.generateRandom()
      val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, "")))
      val asset = ServiceWithDao.create(Seq(NewLinearAsset(linkId, 0, 20, prohibition, 1, 0, None)), 190, "testUser")

      when(mockAssetDao.getAssetTypeId(Seq(asset.head))).thenReturn(Seq((asset.head, LinearAssetTypes.ProhibitionAssetTypeId)))
      ServiceWithDao.update(Seq(asset.head), Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty))), "testUser")

      val limit = linearAssetDao.fetchProhibitionsByLinkIds(LinearAssetTypes.ProhibitionAssetTypeId, Seq(linkId)).filter(_.createdBy.get == "testUser").head

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
