
package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkChange, RoadLinkChangeType, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class PavedRoadUpdaterSpec extends FunSuite with Matchers with UpdaterUtilsSuite {
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val dynamicLinearAssetService = new DynamicLinearAssetService(mockRoadLinkService, mockEventBus)

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

  object TestPavedRoadUpdaterMock extends PavedRoadUpdater(Service) {
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
    
    override def roadLinkService = mockRoadLinkService
  }
  
  def changeReplaceNewVersionChangePavement(oldRoadLinkId: String, newRoadLikId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 9), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 9), newRoadLikId)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_IIb.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections,
        surfaceType = SurfaceType.Paved
      )),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_IIb.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections,
          surfaceType = SurfaceType.None
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 8, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
    )
  }
  
  val assetValues = DynamicValue(DynamicAssetValue(List(DynamicProperty("paallysteluokka","single_choice",false,List(DynamicPropertyValue(99)))
    , DynamicProperty("suggest_box","checkbox",false,List())
  )))
  
  test("Create new paved") {

    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.changeType == RoadLinkChangeType.Add)

    runWithRollback {
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(linkId15), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(2.910)

      assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties.nonEmpty should be(true)
      val properties = assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties
      properties.head.values.head.value should be("99")

      val assets = TestPavedRoadUpdater.getReport().filter(_.linkId ==linkId15).map(a => PairAsset(a.before, a.after.headOption))

      assets.size should  be(1)
      TestPavedRoadUpdater.getReport().head.changeType should be(ChangeTypeReport.Creation)
    }
  }
  test("case 6 links version and no pavement, expire") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink = createRoadLink(linkIdVersion1,geometry, functionalClassI = AnotherPrivateRoad,paved = SurfaceType.Paved)
    val newRoadLink =  createRoadLink(linkIdVersion2,geometry,functionalClassI = AnotherPrivateRoad,paved = SurfaceType.None)
    
    val change = changeReplaceNewVersionChangePavement(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2), false)).thenReturn(Seq(newRoadLink))
      val id1 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, linkIdVersion1, assetValues, SideCode.BothDirections.value, Measures(0, geometry._2), AutoGeneratedUsername.mtkClassDefault, 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestPavedRoadUpdaterMock.updateByRoadLinks(PavedRoad.typeId, Seq(change))
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(linkIdVersion2), false)
      assetsAfter.size should be(0)
      
      val assets = TestPavedRoadUpdaterMock.getReport().map(a => PairAsset(a.before, a.after.headOption))
      assets.size should be(1)
      TestPavedRoadUpdaterMock.getReport().head.changeType should be(ChangeTypeReport.Deletion)

    }
  }

  test("case 1 links under asset is split, smoke test") {
    val linkId = linkId5
    val newLinks = newLinks1_2_4
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get

      val id = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, linkId,
        assetValues, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, newLinks, false)
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
