package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISMaintenanceDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, ValidityPeriod, ValidityPeriodDayOfWeek}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, ManoeuvreService, NewManoeuvre}
import fi.liikennevirasto.digiroad2.util.PolygonTools
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ManoeuvreUpdaterSpec extends FunSuite with Matchers with  UpdaterUtilsSuite {
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty[String])).thenReturn(Seq())

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
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

  object TestManoeuvreUpdater extends ManoeuvreUpdater() {
    override def withDynTransaction[T](f: => T): T = f
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  test("Links version changes, move to new version") {
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
      val assetsBefore = Service.getByRoadLinkIdsNoValidation(roadLink2.map(_.linkId).toSet, newTransaction = false)
      assetsBefore.exists(p => p.id == id) should be(true)
      assetsBefore.find(p=>p.id ==id).get.id should be(id)

      val changed =  TestManoeuvreUpdater.updateByRoadLinks(Manoeuvres.typeId, Seq(change))
      val assetsAfter = Service.getByRoadLinkIdsNoValidation(roadLink3.map(_.linkId).toSet, false)

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

      changed.isEmpty should be(true)
    }
  }
  
  test("test version upgrade separation logic"){
    val linkId = generateRandomLinkId(); val linkId2 = generateRandomKmtkId()
    val change = changeReplaceShortenedFromEnd(linkId)
    val linkId1 = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId1:1";    val linkIdVersion2 = s"$linkId1:2";
    val change1 = changeReplaceNewVersion(linkIdVersion1, linkIdVersion2)
    val linkIdVersion3 = s"$linkId2:1";    val linkIdVersion4 = s"$linkId2:2";
    val change2 = changeReplaceNewVersion(linkIdVersion3, linkIdVersion4)

    val (upgrade, other) = TestManoeuvreUpdater.separateVersionUpgradeAndOther(Seq(change))
    upgrade.size should be(0);     other.size should be(1)
    val (upgrade1, other1) = TestManoeuvreUpdater.separateVersionUpgradeAndOther(Seq(change,change1,change2))
    upgrade1.size should be(2);    other1.size should be(1)
    val (upgrade2, other2) = TestManoeuvreUpdater.separateVersionUpgradeAndOther(Seq(change1, change2))
    upgrade2.size should be(2);    other2.size should be(0)
    val (upgrade3, other3) = TestManoeuvreUpdater.separateVersionUpgradeAndOther(Seq(change2))
    upgrade3.size should be(1);    other3.size should be(0)
  }
  
  test("Link under manoeuvre asset changed, add into worklist ") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceShortenedFromEnd(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link3))

      val roadLink2 = Seq(
        RoadLink(linkId, List(Point(0.0, 0.0), Point(9.0, 0.0)), 9, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId7, List(Point(10.0, 0.0), Point(9.0, 0.0)), 9.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val validityPeriodWeekDay = ValidityPeriod(8,16,ValidityPeriodDayOfWeek.apply(1))
      val validityPeriodSunday = ValidityPeriod(9,12,ValidityPeriodDayOfWeek.apply(3))
      val newManoeuvres1 = NewManoeuvre(Set(validityPeriodWeekDay, validityPeriodSunday),
        Seq(1,2,3), Some("Lisätietoa kääntymisrajoituksesta"), linkIds = roadLink2.map(_.linkId), trafficSignId = None, isSuggested = false
      )

      val id = Service.createManoeuvre("test",newManoeuvres1,roadLink2,newTransaction = false)
      val assetsBefore = Service.getByRoadLinkIdsNoValidation(roadLink2.map(_.linkId).toSet, newTransaction = false)
      assetsBefore.exists(p => p.id == id) should be(true)
      assetsBefore.find(p => p.id == id).get.id should be(id)

      val changed =  TestManoeuvreUpdater.updateByRoadLinks(Manoeuvres.typeId, Seq(change))
      val assetsAfter = Service.getByRoadLinkIdsNoValidation(roadLink2.map(_.linkId).toSet, newTransaction = false)

      assetsAfter.exists(p => p.id == id) should be(true)
      assetsAfter.find(p => p.id == id).get.id should be(id)

      changed.size should be(1)
      changed.head.id should be(id)

      val workListItems = Service.getManoeuvreSamuutusWorkList(false)
      workListItems.size should be(1)
      workListItems.head.assetId should be(id)
    }
  }
}
