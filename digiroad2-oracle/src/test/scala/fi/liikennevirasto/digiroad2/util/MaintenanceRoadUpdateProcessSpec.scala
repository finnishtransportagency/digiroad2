package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.{CombinedRemovedPart, Removed}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, RoadLinkClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NewLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class MaintenanceRoadUpdateProcessSpec extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockRoadLinkClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set.empty[String])).thenReturn(Seq())

  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockMaintenanceDao = MockitoSugar.mock[PostGISMaintenanceDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao(mockRoadLinkClient, mockRoadLinkService)
  val maintenanceDao = new PostGISMaintenanceDao(mockRoadLinkClient, mockRoadLinkService)
  val dynamicLinearAssetDAO = new DynamicLinearAssetDao

  object TestMaintenanceRoadUpdateProcess extends MaintenanceRoadUpdateProcess(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def maintenanceDAO: PostGISMaintenanceDao = maintenanceDao
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = new PostGISAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = dynamicLinearAssetDAO
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Maintenance road asset on a removed road link should be expired") {
    val oldRoadLinkId = "115L"
    val oldRoadLink = RoadLink(
      oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val propIns1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue( "1")))
    val propIns2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue( "2")))
    val propIns3 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue( "text")))
    val propIns4 = DynamicProperty("suggest_box", "checkbox",  false, Seq())
    val propIns5 = DynamicProperty("huoltotie_tarkistettu", "checkbox",  false, Seq())

    val propIns :Seq[DynamicProperty] = List(propIns1, propIns2, propIns3, propIns4, propIns5)
    val maintenanceRoadIns = DynamicValue(DynamicAssetValue(propIns))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val linearAssetId = TestMaintenanceRoadUpdateProcess.create(Seq(NewLinearAsset(oldRoadLinkId, 0, 20, maintenanceRoadIns, 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      val change = ChangeInfo(Some(oldRoadLinkId), None, 123L, Removed.value, Some(0), Some(10), None, None, 99L)
      val assetsBefore = TestMaintenanceRoadUpdateProcess.dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(linearAssetId.toSet)
      assetsBefore.head.expired should be(false)
      TestMaintenanceRoadUpdateProcess.updateByRoadLinks(MaintenanceRoadAsset.typeId, 1, Seq(), Seq(change))
      val assetsAfter = TestMaintenanceRoadUpdateProcess.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, linearAssetId.toSet, false)
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
    val functionalClass = 5
    val linkType = Freeway
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldRoadLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldRoadLinkId2, List(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newRoadLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val change = Seq(ChangeInfo(Some(oldRoadLinkId1), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldRoadLinkId2), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(0), Some(10), Some(10), Some(20), 1L))

    val propIns1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue( "1")))
    val propIns2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue( "2")))
    val propIns3 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue( "text")))
    val propIns4 = DynamicProperty("suggest_box", "checkbox",  false, Seq())
    val propIns5 = DynamicProperty("huoltotie_tarkistettu", "checkbox",  false, Seq())

    val propIns :Seq[DynamicProperty] = List(propIns1, propIns2, propIns3, propIns4, propIns5)
    val maintenanceRoadIns = DynamicValue(DynamicAssetValue(propIns))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId1, oldRoadLinkId2), false)).thenReturn(oldRoadLinks)
      val linearAssetIds = TestMaintenanceRoadUpdateProcess.create(Seq(NewLinearAsset(oldRoadLinkId1, 0, 10, maintenanceRoadIns, 1, 0, None),
        NewLinearAsset(oldRoadLinkId2, 10, 20, maintenanceRoadIns, 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      val assetsBefore = TestMaintenanceRoadUpdateProcess.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, linearAssetIds.toSet, false)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newRoadLinkId), false)).thenReturn(Seq(newRoadLink))
      TestMaintenanceRoadUpdateProcess.updateByRoadLinks(MaintenanceRoadAsset.typeId, 1, Seq(newRoadLink), change)
      val expiredAssets = TestMaintenanceRoadUpdateProcess.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, linearAssetIds.toSet, false)
      expiredAssets.size should be(2)
      expiredAssets.sortBy(_.linkId).map(_.linkId) should be(List(oldRoadLinkId1, oldRoadLinkId2))
      val validAssets = TestMaintenanceRoadUpdateProcess.dao.fetchAssetsByLinkIds(Set(MaintenanceRoadAsset.typeId), Seq(newRoadLinkId))
      validAssets.size should be(2)
      validAssets.map(_.linkId) should be(List(newRoadLinkId, newRoadLinkId))
    }
  }
}
