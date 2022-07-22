package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.CarRoad_IIIa
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, RoadLinkClient, RoadLinkFetched, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISSpeedLimitDao}
import fi.liikennevirasto.digiroad2.linearasset.{NewLimit, RoadLink, SpeedLimitValue}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class SpeedLimitUpdaterSpec extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao(mockRoadLinkClient, mockRoadLinkService)
  when(mockRoadLinkClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  val service = new SpeedLimitService(mockEventBus, mockRoadLinkClient, mockRoadLinkService)
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestSpeedLimitUpdater extends SpeedLimitUpdater(mockEventBus, mockRoadLinkClient, mockRoadLinkService, service) {
    override val dao: PostGISSpeedLimitDao = new PostGISSpeedLimitDao(mockRoadLinkClient, mockRoadLinkService)
  }

  test("Should map the speed limit of an old link to three new links") {

    val oldLinkId = "5000"
    val newLinkId1 = "6001"
    val newLinkId2 = "6002"
    val newLinkId3 = "6003"
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))


    runWithRollback {

      when(mockRoadLinkClient.createVVHTimeStamp()).thenReturn(0L)
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(oldLinkId)).thenReturn(Some(RoadLinkFetched(oldLinkId, oldRoadLink.municipalityCode, oldRoadLink.geometry,
        administrativeClass, trafficDirection, CarRoad_IIIa, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface, 25)))
      service.create(Seq(NewLimit(oldLinkId, 0.0, 25.0)), SpeedLimitValue(30), "test", (_, _) => Unit)
      when(mockRoadLinkClient.roadLinkData.fetchByLinkIds(any[Set[String]])).thenReturn(Seq())
      TestSpeedLimitUpdater.updateByRoadLinks(municipalityCode, newRoadLinks, changeInfo)
      newRoadLinks.sortBy(_.linkId).foreach { roadLink =>
        val asset = service.getExistingAssetByRoadLink(roadLink, false)
        asset.head.linkId should be(roadLink.linkId)
        asset.head.expired should be(false)
        asset.head.value.get should be(SpeedLimitValue(30))
      }
    }
  }

  /*test("Should map the speed limit of three old links to one new link") {
    val oldLinkId1 = "5001"
    val oldLinkId2 = "5002"
    val oldLinkId3 = "5003"
    val newLinkId = "6000"
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(oldLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), 144000000))

    runWithRollback {
      when(mockRoadLinkClient.createVVHTimeStamp()).thenReturn(0L)
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(oldLinkId1)).thenReturn(Some(RoadLinkFetched(oldLinkId1, oldRoadLinks(0).municipalityCode, oldRoadLinks(0).geometry,
        administrativeClass, trafficDirection, CarRoad_IIIa, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface, 10)))
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(oldLinkId2)).thenReturn(Some(RoadLinkFetched(oldLinkId2, oldRoadLinks(1).municipalityCode, oldRoadLinks(1).geometry,
        administrativeClass, trafficDirection, CarRoad_IIIa, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface, 10)))
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(oldLinkId3)).thenReturn(Some(RoadLinkFetched(oldLinkId3, oldRoadLinks(2).municipalityCode, oldRoadLinks(2).geometry,
        administrativeClass, trafficDirection, CarRoad_IIIa, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface, 5)))
      TestSpeedLimitUpdateProcess.create(Seq(NewLimit(oldLinkId1, 0.0, 10.0), NewLimit(oldLinkId1, 0.0, 10.0),
        NewLimit(oldLinkId1, 0.0, 5.0)), SpeedLimitValue(30), "test", (_, _) => Unit)
      when(mockRoadLinkClient.roadLinkData.fetchByLinkIds(any[Set[String]])).thenReturn(Seq())
      TestSpeedLimitUpdateProcess.updateByRoadLinks(municipalityCode, Seq(newRoadLink), changeInfo)
      val newAsset = TestSpeedLimitUpdateProcess.getExistingAssetByRoadLink(newRoadLink, false).head
      newAsset.linkId should be(newRoadLink.linkId)
      newAsset.expired should be(false)
      newAsset.value.get should be(SpeedLimitValue(30))
      newAsset.startMeasure should be(0.0)
      newAsset.endMeasure should be(25.0)
    }
  }*/
}
