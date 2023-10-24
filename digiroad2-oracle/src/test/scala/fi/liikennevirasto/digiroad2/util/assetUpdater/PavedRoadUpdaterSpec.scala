
package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkChange, RoadLinkChangeType, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Deletion, Divided}
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class PavedRoadUpdaterSpec extends FunSuite with Matchers with UpdaterUtilsSuite with BeforeAndAfter {
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

  before {
    TestPavedRoadUpdater.resetReport()
    TestPavedRoadUpdaterMock.resetReport()
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
          ReplaceInfo(Option(oldId), Option(newLinkId1),
            oldFromMValue = Option(0.0), oldToMValue = Option(8), newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
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

      val assets = TestPavedRoadUpdater.getReport().filter(_.linkId ==linkId15).map(a => PairAsset(a.before, a.after.headOption,a.changeType))

      assets.size should  be(1)
      TestPavedRoadUpdater.getReport().head.changeType should be(ChangeTypeReport.Creation)
    }
  }
  test("case 6 links version and no pavement, expire") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink =  createRoadLink(linkIdVersion1,geometry, functionalClassI = AnotherPrivateRoad,paved = SurfaceType.Paved)
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
      
      val assets = TestPavedRoadUpdaterMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
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
        assetValues, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2022-01-01")))
      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, newLinks, false)
      assetsAfter.size should be(3)
      assetsAfter.forall(_.createdBy.get == "testCreator") should be(true)
      assetsAfter.forall(_.createdDateTime.get.toString().startsWith("2020-01-01")) should be(true)
      assetsAfter.forall(_.modifiedBy.get == "testModifier") should be(true)
      assetsAfter.forall(_.modifiedDateTime.get.toString().startsWith("2022-01-01")) should be(true)
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

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the beginning; " +
    "then the Pavement Asset on New Link should not grow") {
    val oldLinkID = "deb91a05-e182-44ae-ad71-4ba169d57e41:1"
    val newLinkID = "0a4cb6e7-67c3-411e-9446-975c53c0d054:1"

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      val id = service.createWithoutTransaction(PavedRoad.typeId, oldLinkID, NumericValue(50), SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(PavedRoad.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByIds(PavedRoad.typeId, Set(id), false)
      assetsAfter.size should be(1)

      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetsAfter.head.linkId should be(newLinkID)
      assetLength should be(oldRoadLink.length)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the end; " +
    "then the Pavement Asset on New Link should not grow") {
    val oldLinkID = "18ce7a01-0ddc-47a2-9df1-c8e1be193516:1"
    val newLinkID = "016200a1-5dd4-47cc-8f4f-38ab4934eef9:1"

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      val id = service.createWithoutTransaction(PavedRoad.typeId, oldLinkID, NumericValue(50), SideCode.BothDirections.value, Measures(0.0, 20.0), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(PavedRoad.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByIds(PavedRoad.typeId, Set(id), false)
      assetsAfter.size should be(1)

      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetsAfter.head.linkId should be(newLinkID)
      //Check that calculated value is approx same as given
      (Math.abs(assetLength - 20) < 0.5) should equal(true)
    }
  }

  test("Split, road link is split into two links, other has SurfaceType None. " +
    "PavedRoad asset should not be moved to SurfaceType None link" +
    "Report should have 1 Divided and 1 Deletion for asset"){
    val oldLinkId = "dbeea36b-16b4-4ddb-b7b7-3ea4fa4b3667:1"
    val newLinkId1 = "4a9f1948-8bae-4cc9-9f11-218079aac595:1"
    val newLinkId2 = "254ed5a2-bc16-440a-88f1-23868011975b:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    runWithRollback{
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val id = service.createWithoutTransaction(PavedRoad.typeId, oldLinkId, NumericValue(50), SideCode.BothDirections.value, Measures(0.0, 133.765), "testuser", 0L, Some(oldRoadLink), false, None, None)
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val oldLinkReports = changeReport.changes.filter(_.linkId == oldLinkId)
      oldLinkReports.size should equal(2)
      oldLinkReports.exists(_.changeType == Divided) should equal (true)
      oldLinkReports.exists(_.changeType == Deletion) should equal (true)

      val assetsAfter = service.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId1, newLinkId2), newTransaction = false)
      assetsAfter.size should equal (1)
      assetsAfter.head.startMeasure should equal(23.65)
      assetsAfter.head.endMeasure should equal(89.351)
    }
  }
}
