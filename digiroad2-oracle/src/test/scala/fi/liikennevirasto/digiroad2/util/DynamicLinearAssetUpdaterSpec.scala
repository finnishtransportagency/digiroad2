package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.{CombinedRemovedPart, Removed}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, RoadLinkClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NewLinearAsset, NumericValue, RoadLink, TextualValue}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class DynamicLinearAssetUpdaterSpec extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao(mockRoadLinkClient, mockRoadLinkService)
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  when(mockRoadLinkClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  val service = new DynamicLinearAssetService(mockRoadLinkService, mockEventBus)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestDynamicLinearAssetUpdater extends DynamicLinearAssetUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
  }

  val assetValues = DynamicValue(DynamicAssetValue(Seq(
    DynamicProperty("kelirikko", "number", false, Seq(DynamicPropertyValue(10))),
    DynamicProperty("spring_thaw_period", "number", false, Seq()),
    DynamicProperty("annual_repetition", "number", false, Seq()),
    DynamicProperty("suggest_box", "checkbox", false, List())
  )))

  test("Asset on a removed road link should be expired") {
    val oldRoadLinkId = "1505L"
    val oldRoadLink = RoadLink(
    oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val id = service.createWithoutTransaction(DamagedByThaw.typeId, oldRoadLinkId, assetValues, 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink), false)
      val change = ChangeInfo(Some(oldRoadLinkId), None, 123L, Removed.value, Some(0), Some(10), None, None, 99L)
      val assetsBefore = service.dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))
      assetsBefore.head.expired should be(false)
      TestDynamicLinearAssetUpdater.updateByRoadLinks(DamagedByThaw.typeId, 1, Seq(), Seq(change))
      val assetsAfter = service.dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))
      assetsAfter.head.expired should be(true)
    }
  }

  test("Assets should be mapped to a new road link combined from two smaller links") {
    val oldRoadLinkId1 = "160L"
    val oldRoadLinkId2 = "170L"
    val newRoadLinkId = "310L"
    val municipalityCode = 1
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.TowardsDigitizing
    val functionalClass = 1
    val linkType = Freeway
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink1 = RoadLink(oldRoadLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val oldRoadLink2 = RoadLink(oldRoadLinkId2, List(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val oldRoadLinks = Seq(oldRoadLink1, oldRoadLink2)

    val newRoadLink = RoadLink(newRoadLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val change = Seq(ChangeInfo(Some(oldRoadLinkId1), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldRoadLinkId2), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(10), Some(20), Some(10), Some(20), 1L))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId1, oldRoadLinkId2), false)).thenReturn(oldRoadLinks)
      val id1 = service.createWithoutTransaction(DamagedByThaw.typeId, oldRoadLinkId1, assetValues, 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink1), false)
      val id2 = service.createWithoutTransaction(DamagedByThaw.typeId, oldRoadLinkId2, assetValues, 1, Measures(10, 20), "testuser", 0L, Some(oldRoadLink2), false)
      val assetsBefore = service.getPersistedAssetsByIds(DamagedByThaw.typeId, Set(id1, id2), false)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newRoadLinkId), false)).thenReturn(Seq(newRoadLink))
      TestDynamicLinearAssetUpdater.updateByRoadLinks(DamagedByThaw.typeId, 1, Seq(newRoadLink), change)
      val assetsAfter = service.dao.fetchLinearAssetsByLinkIds(DamagedByThaw.typeId, Seq(oldRoadLinkId1, oldRoadLinkId2, newRoadLinkId), "kelirikko", true)
      val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
      expiredAssets.size should be(2)
      val expiredLinkIds = expiredAssets.map(_.linkId)
      expiredLinkIds should contain(oldRoadLinkId1)
      expiredLinkIds should contain(oldRoadLinkId2)
      validAssets.size should be(2)
      validAssets.map(_.linkId) should be(List(newRoadLinkId, newRoadLinkId))
      validAssets.map(_.value) should be(List(Some(NumericValue(10)), Some(NumericValue(10))))
    }
  }
}
