package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.{CombinedRemovedPart, Removed}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{ProhibitionValue, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, ProhibitionService}
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ProhibitionUpdaterSpec extends FunSuite with Matchers{
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao()
  val service = new ProhibitionService(mockRoadLinkService, mockEventBus)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestProhibitionUpdater extends ProhibitionUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  test("Asset on a removed road link should be expired") {
    val oldRoadLinkId = LinkIdGenerator.generateRandom()
    val oldRoadLink = RoadLink(
      oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val linearAssetId = service.createWithoutTransaction(Prohibition.typeId, oldRoadLinkId, Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty))), 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val change = ChangeInfo(Some(oldRoadLinkId), None, 123L, Removed.value, Some(0), Some(10), None, None, 99L)
      val assetsBefore = service.getPersistedAssetsByIds(Prohibition.typeId, Set(linearAssetId), false)
      assetsBefore.head.expired should be(false)
      TestProhibitionUpdater.updateByRoadLinks(Prohibition.typeId, 1, Seq(), Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(Prohibition.typeId, Set(linearAssetId), false)
      assetsAfter.isEmpty should be(true)
    }
  }

  test("Prohibition assets should be mapped to a new road link combined from two shorter links") {
    val oldRoadLinkId1 = LinkIdGenerator.generateRandom()
    val oldRoadLinkId2 = LinkIdGenerator.generateRandom()
    val newRoadLinkId = LinkIdGenerator.generateRandom()
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
      ChangeInfo(Some(oldRoadLinkId2), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(0), Some(10), Some(10), Some(20), 1L))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(oldRoadLinkId1, oldRoadLinkId2), false)).thenReturn(oldRoadLinks)
      val id1 = service.createWithoutTransaction(Prohibition.typeId, oldRoadLinkId1, Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty))),
        1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink1), false, None, None)
      val id2 = service.createWithoutTransaction(Prohibition.typeId, oldRoadLinkId2, Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty))),
        1, Measures(10, 20), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(Prohibition.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(newRoadLinkId), false)).thenReturn(Seq(newRoadLink))
      TestProhibitionUpdater.updateByRoadLinks(Prohibition.typeId, 1, Seq(newRoadLink), change)
      val assetsAfter = service.dao.fetchLinearAssetsByLinkIds(Prohibition.typeId, Seq(oldRoadLinkId1, oldRoadLinkId2, newRoadLinkId), "parking_prohibition", true)
      val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
      expiredAssets.size should be(2)
      expiredAssets.map(_.linkId).sorted should be(Seq(oldRoadLinkId1, oldRoadLinkId2).sorted)
      validAssets.size should be(2)
      validAssets.map(_.linkId) should be(List(newRoadLinkId, newRoadLinkId))
      val sortedValidAssets = validAssets.sortBy(_.startMeasure)
      sortedValidAssets.head.startMeasure should be(0)
      sortedValidAssets.head.endMeasure should be(10)
      sortedValidAssets.last.startMeasure should be(10)
      sortedValidAssets.last.endMeasure should be(20)
    }
  }
}
