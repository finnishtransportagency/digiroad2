package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Freeway, HazmatTransportProhibition, Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.DividedNewPart
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, RoadLinkClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{ProhibitionValue, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{HazmatTransportProhibitionService, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class HazMatTransportProhibitionUpdaterSpec extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao(mockRoadLinkClient, mockRoadLinkService)
  when(mockRoadLinkClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  val service = new HazmatTransportProhibitionService(mockRoadLinkService, mockEventBus)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestHazMatProhibitionUpdater extends HazMatTransportProhibitionUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  test("Hazmat asset should be shortened when the road link is shortened") {
    val oldRoadLinkId = "160L"
    val newRoadLinkId = "310L"
    val municipalityCode = 1
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.TowardsDigitizing
    val functionalClass = 1
    val linkType = Freeway
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink = RoadLink(oldRoadLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val newRoadLink = RoadLink(newRoadLinkId, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val change = Seq(ChangeInfo(Some(oldRoadLinkId), Some(newRoadLinkId), 12345, DividedNewPart.value, Some(0), Some(10), Some(0), Some(10), 144000000))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val id = service.createWithoutTransaction(HazmatTransportProhibition.typeId, oldRoadLinkId, Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty))),
        1, Measures(0, 20), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(HazmatTransportProhibition.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newRoadLinkId), false)).thenReturn(Seq(newRoadLink))
      TestHazMatProhibitionUpdater.updateByRoadLinks(HazmatTransportProhibition.typeId, 1, Seq(newRoadLink), change)
      val assetsAfter = service.dao.fetchLinearAssetsByLinkIds(HazmatTransportProhibition.typeId, Seq(oldRoadLinkId, newRoadLinkId), LinearAssetTypes.getValuePropertyId(HazmatTransportProhibition.typeId), true)
      val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
      expiredAssets.size should be(1)
      expiredAssets.map(_.linkId) should be(List(oldRoadLinkId))
      expiredAssets.head.endMeasure should be(20)
      validAssets.size should be(1)
      validAssets.map(_.linkId) should be(List(newRoadLinkId))
      validAssets.head.endMeasure should be(10)
    }
  }
}
