package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, TrafficVolume}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, ValidityPeriod}
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, ManoeuvreService, NewManoeuvre}
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.mockito.Mockito.when
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.Matchers.{be, convertToAnyShouldWrapper}
import org.scalatest.mockito.MockitoSugar

class ManouvreUpdaterSpec extends FunSuite with Matchers with  UpdaterUtilsSuite {
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty[String])).thenReturn(Seq())

  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  val maintenanceDao = new PostGISMaintenanceDao()
  val dynamicLinearAssetDAO = new DynamicLinearAssetDao

  object Service extends ManoeuvreService(mockRoadLinkService, mockEventBus) {
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = new PostGISAssetDao
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  object TestManouvreUpdater extends ManouvreUpdater() {
    override def withDynTransaction[T](f: => T): T = f
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  test("case 6 links version is changes, move to new version") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink = createRoadLink(linkIdVersion1, generateGeometry(0, 9))
    val newLink = createRoadLink(linkIdVersion2, geometry)
    val change = changeReplaceNewVersion(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkIdVersion1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2))).thenReturn(Seq(newLink))
      val id1 = 1
      val newManoeuvres = Seq(NewManoeuvre(Set.empty[ValidityPeriod],
        Seq(), None, linkIds = Seq(), trafficSignId = None, isSuggested = false

      ))
      Service.createManoeuvres(newManoeuvres,"test")
      val assetsBefore = Service.getByRoadLinkId(Set(id1), false)
      assetsBefore.size should be(1)


      TestManouvreUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = Service.getByRoadLinkId(Set(linkIdVersion2), false)
 /*     assetsAfter.size should be(1)
      assetsAfter.head.linkId should be(linkIdVersion2)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(8)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))*/

/*      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption, a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be(a.newAsset.get.assetId)
      })*/
    }
  }
  
  

}
