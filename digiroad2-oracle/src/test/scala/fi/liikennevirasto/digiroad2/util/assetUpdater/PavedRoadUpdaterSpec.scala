package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.{CombinedRemovedPart, Removed}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, OldVVHRoadLinkClient, RoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NumericValue, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class PavedRoadUpdaterSpec extends FunSuite with Matchers{
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockRoadLinkData = MockitoSugar.mock[OldVVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val linearAssetDao = new PostGISLinearAssetDao(mockRoadLinkClient, mockRoadLinkService)
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  when(mockRoadLinkClient.roadLinkData).thenReturn(mockRoadLinkData)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object Service extends PavedRoadService(mockRoadLinkService, mockEventBus) {
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = new PostGISAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  object TestPavedRoadUpdater extends PavedRoadUpdater(Service) {
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
  }

  test("Asset on a removed road link should be expired") {
    val oldRoadLinkId = LinkIdGenerator.generateRandom()
    val oldRoadLink = RoadLink(
      oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val linearAssetId = Service.createWithoutTransaction(PavedRoad.typeId, oldRoadLinkId, NumericValue(99), 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink), false)
      val change = ChangeInfo(Some(oldRoadLinkId), None, 123L, Removed.value, Some(0), Some(10), None, None, 99L)
      val assetsBefore = Service.dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(linearAssetId))
      assetsBefore.head.expired should be(false)
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, 1, Seq(), Seq(change))
      val assetsAfter = Service.getPersistedAssetsByIds(PavedRoad.typeId, Set(linearAssetId), false)
      assetsAfter.head.expired should be(true)
    }
  }

  test("Assets should be mapped to a new road link combined from two smaller links") {
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
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId1, oldRoadLinkId2), false)).thenReturn(oldRoadLinks)
      val id1 = Service.createWithoutTransaction(PavedRoad.typeId, oldRoadLinkId1, DynamicValue(DynamicAssetValue(Seq(
        DynamicProperty("paallysteluokka", "single_choice", false, Seq(DynamicPropertyValue("99")))))), 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink1), false)
      val id2 = Service.createWithoutTransaction(PavedRoad.typeId, oldRoadLinkId2, DynamicValue(DynamicAssetValue(Seq(
        DynamicProperty("paallysteluokka", "single_choice", false, Seq(DynamicPropertyValue("99")))))), 1, Measures(10, 20), "testuser", 0L, Some(oldRoadLink1), false)
      val assetsBefore = Service.getPersistedAssetsByIds(PavedRoad.typeId, Set(id1, id2), false)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newRoadLinkId), false)).thenReturn(Seq(newRoadLink))
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, 1, Seq(newRoadLink), change)
      val assetsAfter = Service.dao.fetchLinearAssetsByLinkIds(PavedRoad.typeId, Seq(oldRoadLinkId1, oldRoadLinkId2, newRoadLinkId), "paallysteluokka", true)
      val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
      expiredAssets.size should be(2)
      expiredAssets.map(_.linkId).sorted should be(List(oldRoadLinkId1, oldRoadLinkId2).sorted)
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
