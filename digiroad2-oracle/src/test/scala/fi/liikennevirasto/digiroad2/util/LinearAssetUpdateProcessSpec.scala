package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.Removed
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, NumericValue, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class LinearAssetUpdateProcessSpec extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestLinearAssetUpdateProcess extends LinearAssetUpdateProcess(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
  }

  test("Asset on a removed road link should be expired") {
    val oldRoadLinkId = 150L
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
      linksWithExpiredAssets should be(List(150L))
    }
  }
}
