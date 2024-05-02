
package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class LinearSevenRestrictionsServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]

  val linearAssetDao = new PostGISLinearAssetDao()
  val mVLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]

  val linkId = LinkIdGenerator.generateRandom()
  val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
  
  when(mockRoadLinkService.fetchByLinkId(linkId)).thenReturn(Some(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(any[String])).thenReturn(Some(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(linkId1), "mittarajoitus", false))
    .thenReturn(Seq(PersistedLinearAsset(1, linkId1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

  val roadLinkWithLinkSource = RoadLink(
    linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  object Service extends LinearSevenRestrictionsService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao

    def dynamicLinearAssetDao: DynamicLinearAssetDao = mockDynamicLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  }

  object ServiceWithDao extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Should filter out linear assets on walkways from TN-ITS message") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val (linkId1, linkId2, linkId3) = ("100", "200", "300")
    val roadLink1 = RoadLink(linkId1, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 8, TrafficDirection.BothDirections, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink2 = RoadLink(linkId2, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 5, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink3 = RoadLink(linkId3, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 7, TrafficDirection.BothDirections, TractorRoad, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val heightLimitAssetId = 70

    PostGISDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm1, $linkId1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset1, ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1, $lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1, $asset1, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm2, $linkId2)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset2,  ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2, $lrm2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset2, $asset2, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm3, $linkId3)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset3,  ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3, $lrm3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset3, $asset3, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute

      when(mockRoadLinkService.getRoadLinksByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.getHistoryDataLinks(any[Set[String]], any[Boolean])).thenReturn(Seq.empty[RoadLink])

      val result = service.getChanged(heightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.parse("2016-11-02T12:00Z"))
      result.length should be(1)
      result.head.link.linkType should not be (TractorRoad)
      result.head.link.linkType should not be (CycleOrPedestrianPath)

      dynamicSession.rollback()
    }
  }

  test("Create new linear asset") {
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(linkId, 0, 40, NumericValue(1000), 1, 0, None)), 30, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchLinearAssetsByIds(Set(newAssets.head), "mittarajoitus").head
      asset.value should be (Some(NumericValue(1000)))
      asset.expired should be (false)
      mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId), newTransaction = false).head.linkSource.value should be (1)
    }
  }
  test("Create new linear asset with verified info") {
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(linkId, 0, 40, NumericValue(1000), 1, 0, None)), 30, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchLinearAssetsByIds(Set(newAssets.head), "mittarajoitus").head
      asset.value should be (Some(NumericValue(1000)))
      asset.expired should be (false)
      asset.verifiedBy.get should be ("testuser")
      asset.verifiedDate.get.toString("yyyy-MM-dd") should be (DateTime.now().toString("yyyy-MM-dd"))
    }
  }

  test("Create new linear asset without informationSource") {
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(linkId, 0, 40, NumericValue(1000), 1, 0, None)), 30, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchLinearAssetsByIds(Set(newAssets.head), "mittarajoitus").head
      asset.informationSource should be (None)
    }
  }

  ignore("adjust linear asset to cover whole link when the difference in asset length and link length is less than maximum allowed error") {
    val linearAssets = Service.getByBoundingBox(30, BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0))).head
    linearAssets should have size 1
    linearAssets.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    linearAssets.map(_.linkId) should be(Seq(linkId1))
    linearAssets.map(_.value) should be(Seq(Some(NumericValue(40000))))
    verify(mockEventBus, times(1))
      .publish("linearAssets:update", ChangeSet(Set.empty[Long], Seq(MValueAdjustment(1, linkId1, 0.0, 10.0)), Nil, Nil, Set.empty[Long], Nil))
  }
  test("Update verified info") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new RoadWidthService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    PostGISDatabase.withDynTransaction {
      val assetNotVerified = service.dao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus")
      service.updateVerifiedInfo(Set(11111), "test", 30)
      val verifiedAsset = service.dao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus")
      assetNotVerified.find(_.id == 11111).flatMap(_.verifiedBy) should be(None)
      verifiedAsset.find(_.id == 11111).flatMap(_.verifiedBy) should be(Some("test"))
      verifiedAsset.find(_.id == 11111).flatMap(_.verifiedDate).get.toString("yyyy-MM-dd") should be(DateTime.now().toString("yyyy-MM-dd"))

      dynamicSession.rollback()
    }
  }

  test("get unVerified linear assets") {
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(any[Set[Int]])).thenReturn(List(MunicipalityInfo(235, 9, "Kauniainen")))
    runWithRollback {
      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 0, 5, NumericValue(1000), 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)
      val newAssets2 = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 5, 10, NumericValue(800), 1, 0, None)), 40, "testuser")

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(40, Set())
      unVerifiedAssets.keys.head should be ("Kauniainen")
      unVerifiedAssets.flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.flatMap(_._2).values.head should be (newAssets1)
    }
  }

  test("Verify if we have all changes between given date after update a NumericValue Field in OTH") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val roadLink1 = RoadLink("dd8bdb73-b8b4-4c81-a404-1126c4f4e714:1", List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 8, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val totalWeightLimitAssetId = 30

    PostGISDatabase.withDynTransaction {
      when(mockRoadLinkService.getRoadLinksByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLink1))
      when(mockRoadLinkService.getHistoryDataLinks(any[Set[String]], any[Boolean])).thenReturn(Seq.empty[RoadLink])

      //Linear assets that have been changed in OTH between given date values Before Update
      val resultBeforeUpdate = service.getChanged(totalWeightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))

      //Update Numeric Values
      val assetToUpdate = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      when(mockAssetDao.getAssetTypeId(Seq(assetToUpdate.id))).thenReturn(Seq((assetToUpdate.id, totalWeightLimitAssetId)))

      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(11111l), NumericValue(2000), "UnitTestsUser", sideCode = Some(2))
      val assetUpdated = linearAssetDao.fetchLinearAssetsByIds(newAssetIdCreatedWithUpdate.toSet, "mittarajoitus").head

      //Linear assets that have been changed in OTH between given date values After Update
      val resultAfterUpdate = service.getChanged(totalWeightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))

      val oldAssetInMessage = resultAfterUpdate.find { changedLinearAsset => changedLinearAsset.linearAsset.id == assetToUpdate.id }
      val newAssetInMessage = resultAfterUpdate.find { changedLinearAsset => changedLinearAsset.linearAsset.id == assetUpdated.id }

      resultAfterUpdate.size should be (resultBeforeUpdate.size + 1)
      oldAssetInMessage.size should be (1)
      newAssetInMessage.size should be (1)

      oldAssetInMessage.head.linearAsset.expired should be (true)
      oldAssetInMessage.head.linearAsset.value should be (assetToUpdate.value)

      dynamicSession.rollback()
    }
  }

  test("get unVerified linear assets only for specific municipalities") {
    val roadLink = Seq(RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface),
      RoadLink(linkId2, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(92)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface),
      RoadLink(linkId3, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))

    val municipalitiesInfo = List(MunicipalityInfo(91, 9, "Helsinki"), MunicipalityInfo(92, 9, "Vantaa"))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLink)
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(any[Set[Int]])).thenReturn(municipalitiesInfo)

    runWithRollback {
      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 0, 30, NumericValue(1000), 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)
      val newAssets2 = ServiceWithDao.create(Seq(NewLinearAsset(linkId2, 0, 60, NumericValue(800), 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)
      val newAssets3 = ServiceWithDao.create(Seq(NewLinearAsset(linkId3, 0, 30, NumericValue(100), 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(40, Set(91,92))
      unVerifiedAssets.keys.size should be (2)
      unVerifiedAssets.keys.forall(List("Vantaa", "Helsinki").contains) should be (true)
      unVerifiedAssets.filter(_._1 == "Vantaa").flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.filter(_._1 == "Vantaa").flatMap(_._2).values.head should be (newAssets2)
      unVerifiedAssets.filter(_._1 == "Helsinki").flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.filter(_._1 == "Helsinki").flatMap(_._2).values.head should be (newAssets1)
    }
  }

  test("should not get administrative class 'State'") {
    val roadLink = Seq(RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, State,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(Set(91))).thenReturn(List(MunicipalityInfo(91, 9, "Helsinki")))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLink)

    runWithRollback {
      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 0, 30, NumericValue(1000), 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(40, Set(91))
      unVerifiedAssets should be(empty)
    }
  }

  test("get inaccurate values (id)") {

  }
}
