package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.{LengthenedCommonPart, Removed, ReplacedCommonPart, ReplacedRemovedPart}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, RoadLinkClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, NumericValue, RoadLink, TextualValue}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class LinearAssetUpdateProcessSpec extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao(mockRoadLinkClient, mockRoadLinkService)
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  when(mockRoadLinkClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestLinearAssetUpdateProcess extends LinearAssetUpdateProcess(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  test("Asset on a removed road link should be expired") {
    val oldRoadLinkId = "150L"
    val oldRoadLink = RoadLink(
      oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      TestLinearAssetUpdateProcess.create(Seq(NewLinearAsset(oldRoadLinkId, 0, 10, NumericValue(3), 1, 1L, None)), NumberOfLanes.typeId, "testuser").head
      val change = ChangeInfo(Some(oldRoadLinkId), None, 123L, Removed.value, Some(0), Some(10), None, None, 99L)
      TestLinearAssetUpdateProcess.updateByRoadLinks(NumberOfLanes.typeId, 1, Seq(), Seq(change))
      val linksWithExpiredAssets = TestLinearAssetUpdateProcess.dao.getLinksWithExpiredAssets(Seq(oldRoadLinkId), NumberOfLanes.typeId)
      linksWithExpiredAssets should be(List(oldRoadLinkId))
    }
  }

  test("Should map winter speed limits of two old links to one new link") {
    val oldLinkId1 = "5001"
    val oldLinkId2 = "5002"
    val newLinkId = "6000"
    val municipalityCode = 1
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val change = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(10), Some(20), Some(10), Some(20), 144000000))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldLinkId1, oldLinkId2), false)).thenReturn(oldRoadLinks)
      val linearAssetIds = TestLinearAssetUpdateProcess.create(Seq(NewLinearAsset(oldLinkId1, 0, 10, TextualValue("mittarajoitus"), 1, 0, None),
        NewLinearAsset(oldLinkId2, 10, 20, TextualValue("mittarjoitus"), 1, 0, None)), WinterSpeedLimit.typeId, "testuser")
      val assetsBefore = TestLinearAssetUpdateProcess.getPersistedAssetsByIds(WinterSpeedLimit.typeId, linearAssetIds.toSet, false)
      assetsBefore.size should be(2)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newLinkId), false)).thenReturn(Seq(newRoadLink))
      TestLinearAssetUpdateProcess.updateByRoadLinks(WinterSpeedLimit.typeId, 1, Seq(newRoadLink), change)
      val assetsAfter = TestLinearAssetUpdateProcess.dao.fetchLinearAssetsByLinkIds(WinterSpeedLimit.typeId, Seq(oldLinkId1, oldLinkId2, newLinkId), "mittarajoitus", true)
      val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
      expiredAssets.size should be(2)
      val expiredLinkIds = expiredAssets.map(_.linkId)
      expiredLinkIds should contain(oldLinkId1)
      expiredLinkIds should contain(oldLinkId2)
      validAssets.size should be(2)
      validAssets.map(_.linkId) should be(List(newLinkId, newLinkId))
    }
  }

  test("Should map linear assets (lit road) of old link to three new road links, asset covers part of road link") {
    val oldLinkId = "5000"
    val newLinkId1 = "6001"
    val newLinkId2 = "6002"
    val newLinkId3 = "6003"
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 100
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val change = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldLinkId), false)).thenReturn(Seq(oldRoadLink))
      val linearAssetIds = TestLinearAssetUpdateProcess.create(Seq(NewLinearAsset(oldLinkId, 0, 10, TextualValue("mittarajoitus"), 1, 0, None)), LitRoad.typeId, "testuser")
      val assetsBefore = TestLinearAssetUpdateProcess.getPersistedAssetsByIds(LitRoad.typeId, linearAssetIds.toSet, false)
      assetsBefore.size should be(1)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newLinkId1, newLinkId2, newLinkId3), false)).thenReturn(newRoadLinks)
      TestLinearAssetUpdateProcess.updateByRoadLinks(LitRoad.typeId, 1, newRoadLinks, change)
      val assetsAfter = TestLinearAssetUpdateProcess.dao.fetchLinearAssetsByLinkIds(LitRoad.typeId, Seq(oldLinkId, newLinkId1, newLinkId2, newLinkId3), "mittarajoitus", true)
      val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
      expiredAssets.size should be(1)
      expiredAssets.head.linkId should be(oldLinkId)
      validAssets.size should be(3)
      validAssets.sortBy(_.linkId).map(_.linkId) should be(List(newLinkId1, newLinkId2, newLinkId3))
    }
  }

  test("Asset on a lengthened road link should be lengthened") {
    val oldRoadLinkId = "150L"
    val oldRoadLink = RoadLink(
      oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      TestLinearAssetUpdateProcess.create(Seq(NewLinearAsset(oldRoadLinkId, 0, 10, TextualValue("mittarajoitus"), 1, 1L, None)), LitRoad.typeId, "testuser").head
      val newLinkId = "160L"
      val newRoadLink = oldRoadLink.copy(linkId = newLinkId, geometry = Seq(Point(0.0, 0.0), Point(15, 0.0)), length = 15)
      val change = ChangeInfo(Some(oldRoadLinkId), Some(newLinkId), 123L, 2, Some(0), Some(10), Some(0), Some(15), 99L)
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newLinkId), false)).thenReturn(Seq(newRoadLink))
      TestLinearAssetUpdateProcess.updateByRoadLinks(LitRoad.typeId, 1, Seq(newRoadLink), Seq(change))
      val assets = TestLinearAssetUpdateProcess.dao.fetchLinearAssetsByLinkIds(LitRoad.typeId, Seq(oldRoadLinkId, newLinkId), "mittarajoitus", true)
      val (expiredAssets, validAssets) = assets.partition(_.expired)
      expiredAssets.size should be(1)
      expiredAssets.head.linkId should be(oldRoadLinkId)
      expiredAssets.head.endMeasure should be(10.0)
      validAssets.size should be(1)
      validAssets.head.linkId should be(newLinkId)
      validAssets.head.endMeasure should be(15.0)
    }
  }
}
