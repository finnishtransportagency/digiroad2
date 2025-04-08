
package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao, RoadLinkOverrideDAO}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue}
import fi.liikennevirasto.digiroad2.service.LinkProperties
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, Measures}
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class MaintenanceRoadUpdaterSpec extends FunSuite with Matchers with UpdaterUtilsSuite {
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
        assetValues, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2021-01-01")))
      val assetsBefore = Service.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestMaintenanceRoadUpdater.updateByRoadLinks(MaintenanceRoadAsset.typeId, changes)
      val assetsAfter = Service.getPersistedAssetsByLinkIds(MaintenanceRoadAsset.typeId, newLinks, false)
      assetsAfter.size should be(3)
      assetsAfter.map(_.id).contains(id) should be(true)
      assetsAfter.forall(_.createdBy.get == "testCreator") should be(true)
      assetsAfter.forall(_.createdDateTime.get.toString().startsWith("2020-01-01")) should be(true)
      assetsAfter.forall(_.modifiedBy.get == "testModifier") should be(true)
      assetsAfter.forall(_.modifiedDateTime.get.toString().startsWith("2021-01-01")) should be(true)

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

  test("Replace. Given a roadlink that is replaced by another roadlink; When the new roadlink has an inappropriate functional class; Then that asset should be expired.") {
    val oldLinkId = "40ace33b-d9b4-4b99-aabe-44c2ed1e4a35:1"
    val newLinkId = "e6724c48-99ff-49d6-8efb-5f12068d8415:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.newLinks.map(_.linkId).contains(newLinkId))

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkId).get
      val linkProperty = LinkProperties(newLinkId, 4, LinkType.apply(99), TrafficDirection.apply(99), AdministrativeClass.apply(99))
      RoadLinkOverrideDAO.insert("functional_class", linkProperty, Some("test"))

      val id = Service.createWithoutTransaction(MaintenanceRoadAsset.typeId, oldLinkId,
        assetValues, SideCode.BothDirections.value, Measures(0, 219.114), "testuser", 0L, Some(oldRoadLink), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2021-01-01")))
      val assetsBefore = Service.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestMaintenanceRoadUpdater.updateByRoadLinks(MaintenanceRoadAsset.typeId, changes)
      val assetsAfter = Service.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, Set(id), false)

      assetsAfter.size should be(1)
      assetsAfter.map(_.id).contains(id) should be(true)
      assetsAfter.forall(_.linkId == oldLinkId) should be(true)
      assetsAfter.forall(_.expired) should be(true)
    }
  }

  test("Split. Given a roadlink that is split into 2 new roadlinks; When the new roadlinks have lifecycle status 5; Then those asset should be expired.") {
    val oldLinkId = "16012727-a9c3-4272-95f9-d4016e132c4c:3"
    val newLinkId1 = "6eb20588-f184-4f9a-a590-3d677f1d9ef2:1"
    val newLinkId2 = "ecda3882-7f24-4efb-9df5-e9ba0d03978e:1"

    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.newLinks.map(_.linkId).contains(newLinkId1))

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get

      val linkProperty = LinkProperties(newLinkId1, 6, LinkType.apply(99), TrafficDirection.apply(99), AdministrativeClass.apply(99))
      RoadLinkOverrideDAO.insert("functional_class", linkProperty, Some("test"))
      val linkProperty2 = LinkProperties(newLinkId2, 6, LinkType.apply(99), TrafficDirection.apply(99), AdministrativeClass.apply(99))
      RoadLinkOverrideDAO.insert("functional_class", linkProperty2, Some("test"))

      val id1 = Service.createWithoutTransaction(MaintenanceRoadAsset.typeId, oldLinkId,
        assetValues, SideCode.BothDirections.value, Measures(160.181, 358.696), "testuser", 0L, Some(oldRoadLink), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2021-01-01")))
      val id2 = Service.createWithoutTransaction(MaintenanceRoadAsset.typeId, oldLinkId,
        assetValues, SideCode.BothDirections.value, Measures(142.726, 160.181), "testuser", 0L, Some(oldRoadLink), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2021-01-01")))

      val assetsBefore = Service.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, Set(id1, id2), false)

      assetsBefore.size should be(2)
      assetsBefore.forall(_.expired) should be(false)

      TestMaintenanceRoadUpdater.updateByRoadLinks(MaintenanceRoadAsset.typeId, changes)

      val assetsAfter = Service.getPersistedAssetsByIds(MaintenanceRoadAsset.typeId, Set(id1, id2), false)
      assetsAfter.size should be(2)
      assetsAfter.forall(_.linkId == oldLinkId) should be(true)
      assetsAfter.forall(_.expired) should be(true)
    }
  }
}
