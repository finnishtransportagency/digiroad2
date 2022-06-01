package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.{CombinedRemovedPart, Removed}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, RoadLink, TextualValue}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class DynamicLinearAssetUpdateProcessSpec extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestDynamicLinearAssetUpdateProcess extends DynamicLinearAssetUpdateProcess(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
  }

  test("Asset on a removed road link should be expired") {
    val oldRoadLinkId = 150L
    val oldRoadLink = RoadLink(
    oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val linearAssetId = TestDynamicLinearAssetUpdateProcess.create(Seq(NewLinearAsset(oldRoadLinkId, 0, 10, TextualValue("kelirikko"), 1, 1L, None)), DamagedByThaw.typeId, "testuser").head
      val change = ChangeInfo(Some(oldRoadLinkId), None, 123L, Removed.value, Some(0), Some(10), None, None, 99L)
      val assetsBefore = TestDynamicLinearAssetUpdateProcess.dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(linearAssetId))
      assetsBefore.head.expired should be(false)
      TestDynamicLinearAssetUpdateProcess.updateByRoadLinks(DamagedByThaw.typeId, 1, Seq(), Seq(change))
      val assetsAfter = TestDynamicLinearAssetUpdateProcess.getPersistedAssetsByIds(DamagedByThaw.typeId, Set(linearAssetId), false)
      assetsAfter.head.expired should be(true)
    }
  }

  test("Assets should be mapped to a new road link combined from two smaller links") {
    val oldRoadLinkId1 = 160L
    val oldRoadLinkId2 = 170L
    val newRoadLinkId = 310L
    val municipalityCode = 1
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.TowardsDigitizing
    val functionalClass = 1
    val linkType = Freeway
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldRoadLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldRoadLinkId2, List(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newRoadLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val change = Seq(ChangeInfo(Some(oldRoadLinkId1), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldRoadLinkId2), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(0), Some(10), Some(10), Some(20), 1L))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId1, oldRoadLinkId2), false)).thenReturn(oldRoadLinks)
      val linearAssetIds = TestDynamicLinearAssetUpdateProcess.create(Seq(NewLinearAsset(oldRoadLinkId1, 0, 10, TextualValue("kelirikko"), 1, 0, None),
        NewLinearAsset(oldRoadLinkId2, 10, 20, TextualValue("kelirikko"), 1, 0, None)), DamagedByThaw.typeId, "testuser")
      val assetsBefore = TestDynamicLinearAssetUpdateProcess.getPersistedAssetsByIds(DamagedByThaw.typeId, linearAssetIds.toSet, false)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newRoadLinkId), false)).thenReturn(Seq(newRoadLink))
      TestDynamicLinearAssetUpdateProcess.updateByRoadLinks(DamagedByThaw.typeId, 1, Seq(newRoadLink), change)
      val assetsAfter = TestDynamicLinearAssetUpdateProcess.dao.fetchLinearAssetsByLinkIds(DamagedByThaw.typeId, Seq(oldRoadLinkId1, oldRoadLinkId2, newRoadLinkId), "kelirikko", true)
      val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
      expiredAssets.size should be(2)
      expiredAssets.map(_.linkId) should be(List(oldRoadLinkId1, oldRoadLinkId2))
      validAssets.size should be(2)
      validAssets.map(_.linkId) should be(List(newRoadLinkId, newRoadLinkId))
    }
  }
}
