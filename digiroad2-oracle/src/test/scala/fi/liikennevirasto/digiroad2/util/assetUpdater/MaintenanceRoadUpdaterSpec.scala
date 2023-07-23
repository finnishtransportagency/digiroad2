
package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChangeClient, RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, Measures}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class MaintenanceRoadUpdaterSpec extends FunSuite with Matchers with UtilsSuite {
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty[String])).thenReturn(Seq())
  
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  val maintenanceDao = new PostGISMaintenanceDao()
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

  test("case 1 links under asset is split, smoke test") {
    val linkId = linkId5
    val newLinks = newLinks1_2_4
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(linkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = Service.createWithoutTransaction(MaintenanceRoadAsset.typeId, linkId,
        assetValues, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = Service.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestMaintenanceRoadUpdater.updateByRoadLinks(MaintenanceRoadAsset.typeId, changes)
      val assetsAfter = Service.getPersistedAssetsByLinkIds(MaintenanceRoadAsset.typeId, newLinks, false)
      assetsAfter.size should be(3)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9.334)

      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(11.841)

      sorted(2).startMeasure should be(0)
      sorted(2).endMeasure should be(34.906)

      assetsAfter.map(v => v.value.isEmpty should be(false))
      assetsAfter.map(v => v.value.get.equals(assetValues))
    }
  }
}

