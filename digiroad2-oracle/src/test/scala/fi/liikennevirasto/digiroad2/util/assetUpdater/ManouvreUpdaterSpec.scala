package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Municipality, SingleCarriageway, TrafficDirection, TrafficVolume}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, RoadLink, ValidityPeriod}
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, MaintenanceService, ManoeuvreService, NewManoeuvre}
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.mockito.ArgumentMatchers.any
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
    val unchangedLink = createRoadLink(linkId7, generateGeometry(8, 26))
    val change = changeReplaceNewVersion(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkIdVersion1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2))).thenReturn(Seq(newLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion1,linkId7))).thenReturn(Seq(oldRoadLink,unchangedLink))
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkIdVersion1,linkId7))).thenReturn(Seq(oldRoadLink,unchangedLink))
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkIdVersion1),newTransaction = true)).thenReturn(Seq(oldRoadLink))
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkIdVersion2),newTransaction = true)).thenReturn(Seq(newLink))
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId7))).thenReturn(Seq(unchangedLink))
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkIdVersion1,linkId7),false)).thenReturn(Seq(oldRoadLink,unchangedLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2,linkId7))).thenReturn(Seq(newLink,unchangedLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId7))).thenReturn(Seq(unchangedLink))
      val roadLink2 = Seq(
        RoadLink(linkIdVersion1, List(Point(0.0, 0.0), Point(8.0, 0.0)), 8, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId7, List(Point(8.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val roadLink3 = Seq(
        RoadLink(linkIdVersion2, List(Point(0.0, 0.0), Point(8.0, 0.0)), 8, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId7, List(Point(8.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )
      
      val newManoeuvres1 = NewManoeuvre(Set.empty[ValidityPeriod],
        Seq(), None, linkIds = roadLink2.map(_.linkId), trafficSignId = None, isSuggested = false
      )
      val id = Service.createManoeuvre("test",newManoeuvres1,roadLink2,newTransaction = false)
      val assetsBefore = Service.getByRoadLinkId(roadLink2.map(_.linkId).toSet, newTransaction = false)
      assetsBefore.exists(p => p.id == id) should be(true)
      assetsBefore.find(p=>p.id ==id).get.id should be(id)
      
      TestManouvreUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = Service.getByRoadLinkId(roadLink3.map(_.linkId).toSet, false)

      assetsAfter.exists(p => p.id == id) should be(true)
      
      assetsAfter.find(p=>p.id ==id).get.elements.map(a=> {
        val (source,destination )=  (a.sourceLinkId,a.destLinkId )
        if (a.elementType== ElementTypes.FirstElement) {
          source should  be(linkIdVersion2)
          destination should  be(linkId7)
          roadLink3.map(_.linkId).contains(source) should be(true)
          roadLink3.map(_.linkId).contains(destination) should be(true)
        }
        if (a.elementType == ElementTypes.LastElement) {
          source should be(linkId7)
          roadLink3.map(_.linkId).contains(source) should be(true)
        }
      })
    }
  }
  test("case 4 links under asset is replaced with shorter") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceShortenedFromEnd(linkId)
    val newLink = createRoadLink(linkId1, generateGeometry(0, 5))
    val unchangedLink = createRoadLink(linkId7, generateGeometry(10, 10))

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link3))

      val roadLink2 = Seq(
        RoadLink(linkId, List(Point(0.0, 0.0), Point(9.0, 0.0)), 9, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId7, List(Point(10.0, 0.0), Point(9.0, 0.0)), 9.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val roadLink3 = Seq( // this highly unlikely scenario in real world
        RoadLink(linkId1, List(Point(0.0, 0.0), Point(4.0, 0.0)), 4, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId7, List(Point(10.0, 0.0), Point(9.0, 0.0)), 9.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val newManoeuvres1 = NewManoeuvre(Set.empty[ValidityPeriod],
        Seq(), None, linkIds = roadLink2.map(_.linkId), trafficSignId = None, isSuggested = false
      )

      val id = Service.createManoeuvre("test",newManoeuvres1,roadLink2,newTransaction = false)
      val assetsBefore = Service.getByRoadLinkId(roadLink2.map(_.linkId).toSet, newTransaction = false)
      assetsBefore.exists(p => p.id == id) should be(true)
      assetsBefore.find(p => p.id == id).get.id should be(id)

      val changed =  TestManouvreUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = Service.getByRoadLinkId(roadLink2.map(_.linkId).toSet, false)

      assetsAfter.exists(p => p.id == id) should be(true)
      assetsAfter.find(p => p.id == id).get.id should be(id)

      changed.size should be(1)
      
    }
  }
  
  

}
