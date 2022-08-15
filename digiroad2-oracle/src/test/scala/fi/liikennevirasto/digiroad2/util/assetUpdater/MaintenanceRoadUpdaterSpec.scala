package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.{CombinedRemovedPart, Removed}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, RoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, Measures}
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class MaintenanceRoadUpdaterSpec extends FunSuite with Matchers{

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
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

  object Service extends MaintenanceService(mockRoadLinkService, mockEventBus) {
    override def polygonTools: PolygonTools = mockPolygonTools
    override def maintenanceDAO: PostGISMaintenanceDao = maintenanceDao
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = new PostGISAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = dynamicLinearAssetDAO
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  object TestMaintenanceRoadUpdater extends MaintenanceRoadUpdater(Service) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  val prop1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue( "1")))
  val prop2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue( "2")))
  val prop3 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue( "text")))
  val prop4 = DynamicProperty("suggest_box", "checkbox",  false, Seq())
  val prop5 = DynamicProperty("huoltotie_tarkistettu", "checkbox",  false, Seq())

  val props :Seq[DynamicProperty] = List(prop1, prop2, prop3, prop4, prop5)
  val assetValues = DynamicValue(DynamicAssetValue(props))
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Maintenance road asset on a removed road link should be expired") {
    val oldRoadLinkId = "115L"
    val oldRoadLink = RoadLink(
      oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val id = Service.createWithoutTransaction(MaintenanceRoadAsset.typeId, oldRoadLinkId, assetValues, 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink))
      val change = ChangeInfo(Some(oldRoadLinkId), None, 123L, Removed.value, Some(0), Some(10), None, None, 99L)
      val assetsBefore = Service.dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))
      assetsBefore.head.expired should be(false)
      TestMaintenanceRoadUpdater.updateByRoadLinks(MaintenanceRoadAsset.typeId, 1, Seq(), Seq(change))
      val assetsAfter = Service.dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))
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

    val oldRoadLink1 = RoadLink(oldRoadLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val oldRoadLink2 = RoadLink(oldRoadLinkId2, List(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val oldRoadLinks = Seq(oldRoadLink1, oldRoadLink2)

    val newRoadLink = RoadLink(newRoadLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val change = Seq(ChangeInfo(Some(oldRoadLinkId1), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldRoadLinkId2), Some(newRoadLinkId), 12345, CombinedRemovedPart.value, Some(10), Some(20), Some(10), Some(20), 1L))

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(oldRoadLinkId1, oldRoadLinkId2), false)).thenReturn(oldRoadLinks)
      val id1 = Service.createWithoutTransaction(MaintenanceRoadAsset.typeId, oldRoadLinkId1, assetValues, 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink1), false)
      val id2 = Service.createWithoutTransaction(MaintenanceRoadAsset.typeId, oldRoadLinkId2, assetValues, 1, Measures(10, 20), "testuser", 0L, Some(oldRoadLink2), false)
      val assetsBefore = Service.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, Set(id1, id2), false)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(newRoadLinkId), false)).thenReturn(Seq(newRoadLink))
      TestMaintenanceRoadUpdater.updateByRoadLinks(MaintenanceRoadAsset.typeId, 1, Seq(newRoadLink), change)
      val expiredAssets = Service.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, Set(id1, id2), false)
      expiredAssets.size should be(2)
      expiredAssets.sortBy(_.linkId).map(_.linkId) should be(List(oldRoadLinkId1, oldRoadLinkId2))
      val validAssets = Service.dao.fetchAssetsByLinkIds(Set(MaintenanceRoadAsset.typeId), Seq(newRoadLinkId))
      validAssets.size should be(2)
      validAssets.map(_.linkId) should be(List(newRoadLinkId, newRoadLinkId))
    }
  }
}
