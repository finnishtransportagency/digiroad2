
package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Replace, Split}
import fi.liikennevirasto.digiroad2.asset.{PavementClass, _}
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkChange, RoadLinkChangeType, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools}
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Creation, Deletion, Divided, Replaced}
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class PavedRoadUpdaterSpec extends FunSuite with Matchers with UpdaterUtilsSuite with BeforeAndAfter {
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val dynamicLinearAssetService = new DynamicLinearAssetService(mockRoadLinkService, mockEventBus)
  val knownAssetValueAsphalt = DynamicValue(DynamicAssetValue(List(DynamicProperty("paallysteluokka", "single_choice", false, List(DynamicPropertyValue(1)))
    , DynamicProperty("suggest_box", "checkbox", false, List())
  )))
  val unknownAssetValue = DynamicValue(DynamicAssetValue(List(DynamicProperty("paallysteluokka", "single_choice", false, List(DynamicPropertyValue(99)))
    , DynamicProperty("suggest_box", "checkbox", false, List())
  )))

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
        municipality = Some(0),
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
          municipality = Some(0),
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

  val testUsername = "K123456"

  test("Create new paved for new road link with SurfaceType 2") {

    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.changeType == RoadLinkChangeType.Add)

    runWithRollback {
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes) // check that no overlapping assets are created even if the process is run twice
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(linkId15), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(2.910)

      assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties.nonEmpty should be(true)
      val properties = assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties
      properties.head.values.head.value should be("99")
      assetsAfter.head.createdBy should equal(Some(AutoGeneratedUsername.mmlPavedDefault))

      val assets = TestPavedRoadUpdater.getReport().filter(_.linkId ==linkId15).map(a => PairAsset(a.before, a.after.headOption,a.changeType))

      assets.size should be(1)
      TestPavedRoadUpdater.getReport().head.changeType should be(ChangeTypeReport.Creation)
    }
  }
  test("case 6. Link version changed, new version has SurfaceType None, move userCreated pavedRoad asset to new link") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink =  createRoadLink(linkIdVersion1,geometry, functionalClassI = AnotherPrivateRoad,paved = SurfaceType.Paved)
    val newRoadLink =  createRoadLink(linkIdVersion2,geometry,functionalClassI = AnotherPrivateRoad,paved = SurfaceType.None)
    
    val change = changeReplaceNewVersionChangePavement(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2), false)).thenReturn(Seq(newRoadLink))
      val id1 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, linkIdVersion1, assetValues, SideCode.BothDirections.value, Measures(0, geometry._2), testUsername, 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestPavedRoadUpdaterMock.updateByRoadLinks(PavedRoad.typeId, Seq(change))
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(linkIdVersion2), false)
      assetsAfter.size should be(1)
      assetsAfter.head.id should be(id1)
      
      val assets = TestPavedRoadUpdaterMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      TestPavedRoadUpdaterMock.getReport().head.changeType should be(ChangeTypeReport.Replaced)

    }
  }

  test("case 6. Link version changed, new version has SurfaceType None, remove Samuutus generated pavedRoad asset") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink =  createRoadLink(linkIdVersion1,geometry, functionalClassI = AnotherPrivateRoad,paved = SurfaceType.Paved)
    val newRoadLink =  createRoadLink(linkIdVersion2,geometry,functionalClassI = AnotherPrivateRoad,paved = SurfaceType.None)

    val change = changeReplaceNewVersionChangePavement(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2), false)).thenReturn(Seq(newRoadLink))
      val id1 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, linkIdVersion1, assetValues, SideCode.BothDirections.value, Measures(0, geometry._2), AutoGeneratedUsername.mmlPavedDefault, 0L, Some(oldRoadLink), false, None, None)

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
      assetsAfter.map(_.id).contains(id) should be(true)
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
      assetsAfter.map(v => PavementClass.extractPavementClassValue(v.value).get.value should be(99))
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the beginning; " +
    "then the Pavement Asset on New Link should extend to the new link length") {
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
      assetsAfter.head.id should be(id)

      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetsAfter.head.linkId should be(newLinkID)
      assetLength should be(newRoadLink.length)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the end; " +
    "then the Pavement Asset on New Link should extend to the new link length") {
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
      assetsAfter.head.id should be(id)

      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetsAfter.head.linkId should be(newLinkID)
      assetLength should be(newRoadLink.length)
    }
  }

  test("Split, road link is split into two links, other has SurfaceType None. Divide user created pavedRoad asset to two links." +
    "Extend the assets to road link length."){
    val oldLinkId = "dbeea36b-16b4-4ddb-b7b7-3ea4fa4b3667:1" //ST 2
    val newLinkId1 = "4a9f1948-8bae-4cc9-9f11-218079aac595:1" //ST 1
    val newLinkId2 = "254ed5a2-bc16-440a-88f1-23868011975b:1" // ST 2
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    runWithRollback{
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val id = service.createWithoutTransaction(PavedRoad.typeId, oldLinkId, NumericValue(50), SideCode.BothDirections.value, Measures(0.0, 133.765), "testuser", 0L, Some(oldRoadLink), false, None, None)
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val oldLinkReports = changeReport.changes.filter(_.linkId == oldLinkId)
      oldLinkReports.size should equal(2)
      oldLinkReports.exists(_.changeType == Divided) should equal (true)
      oldLinkReports.exists(_.changeType == Divided) should equal (true)

      val assetsAfter = service.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId1, newLinkId2), newTransaction = false)
      assetsAfter.size should equal (2)
      assetsAfter.map(_.id).contains(id) should be(true)
      val assetOnLink1 = assetsAfter.find(_.linkId == newLinkId1).get
      val assetOnLink2 = assetsAfter.find(_.linkId == newLinkId2).get

      assetOnLink1.startMeasure should equal(0.0)
      assetOnLink1.endMeasure should equal(132.982)
      assetOnLink2.startMeasure should equal(0.0)
      assetOnLink2.endMeasure should equal(89.351)
    }
  }

  test("Split, road link is split into two links, other has SurfaceType None. Move Samuutus generated asset only to new link with SurfaceType 2" +
    "The asset on link 2 is extended to link length."){
    val oldLinkId = "dbeea36b-16b4-4ddb-b7b7-3ea4fa4b3667:1" //ST 2
    val newLinkId1 = "4a9f1948-8bae-4cc9-9f11-218079aac595:1" //ST 1
    val newLinkId2 = "254ed5a2-bc16-440a-88f1-23868011975b:1" // ST 2
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    runWithRollback{
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val id = service.createWithoutTransaction(PavedRoad.typeId, oldLinkId, NumericValue(50), SideCode.BothDirections.value, Measures(0.0, 133.765), AutoGeneratedUsername.mmlPavedDefault, 0L, Some(oldRoadLink), false, None, None)
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val oldLinkReports = changeReport.changes.filter(_.linkId == oldLinkId)
      oldLinkReports.size should equal(2)
      oldLinkReports.exists(_.changeType == Divided) should equal (true)
      oldLinkReports.exists(_.changeType == Deletion) should equal (true)

      val assetsAfter = service.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId1, newLinkId2), newTransaction = false)
      assetsAfter.size should equal (1)
      assetsAfter.map(_.id).contains(id) should be(true)
      val assetOnLink1 = assetsAfter.find(_.linkId == newLinkId1)
      val assetOnLink2 = assetsAfter.find(_.linkId == newLinkId2).get

      assetOnLink1.isDefined should equal(false)
      assetOnLink2.startMeasure should equal(0.0)
      assetOnLink2.endMeasure should equal(89.351)
    }
  }

  test("Split. RoadLink with SurfaceType 1 is split into three links with Surface type 1. " +
    "PavedRoad asset created by batch should be split into three new ones") {
    runWithRollback {
      val oldLinkId = "84ae7d02-2354-401e-bbd3-7d1bea68075f:1"
      val newLinkId1 = "f20a8c8f-2247-45dc-a94a-47262397f80f:1"
      val newLinkId2 = "7928a63d-fdaf-45ea-90f6-f612e4d8f99b:1"
      val newLinkId3 = "2430073c-1e2a-43d8-b4d2-e173c124f8f1:1"

      val newLinkIds = Seq(newLinkId1, newLinkId2, newLinkId3)

      val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val newLinks = roadLinkService.getRoadLinksByLinkIds(newLinkIds.toSet, newTransaction = false)
      val id = service.createWithoutTransaction(PavedRoad.typeId, oldLinkId, NumericValue(50), SideCode.BothDirections.value, Measures(0.0, 462.673), AutoGeneratedUsername.batchProcessPrefix + "pavedRoad", 0L, Some(oldRoadLink), false, None, None)
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val oldLinkReports = changeReport.changes.filter(_.linkId == oldLinkId).asInstanceOf[Seq[ChangedAsset]]

      oldLinkReports.size should equal(3)
      oldLinkReports.foreach(report => {
        report.changeType should equal(Divided)
        report.assetId should equal(id)
      })

      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, newLinkIds, false)
      assetsAfter.size should equal(3)
      assetsAfter.map(_.id).contains(id) should be(true)
      assetsAfter.foreach(asset => {
        asset.sideCode should equal(1)
        asset.startMeasure should equal(0)
        asset.endMeasure should equal(newLinks.find(_.linkId == asset.linkId).get.length)
      })
    }
  }

  test("Added road link has SurfaceType 2 but FeatureClass is WinterRoads, do not generate PavedRoad asset") {
    val newLinkId = LinkIdGenerator.generateRandom()
    val changes = Seq(changeAddWinterRoad(newLinkId))

    runWithRollback {
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId), false)
      assetsAfter.size should be(0)

      val reports = TestPavedRoadUpdater.getReport().filter(_.linkId ==linkId15).map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      reports.size should  be(0)
    }
  }

  test("Given a split RoadLink that has an Unknown pavement type asset; When the new part does not cover the whole new RoadLink; Then the new part should be ignored.") {
    val oldLinkId1 = "1981d30f-1a0f-4d57-bcde-759f5eedaaef:1"
    val oldLinkId2 = "0b873c9b-43d3-43fb-8ffd-c361c5366db6:1"
    val newLinkId1 = "a4310ee4-7fa2-485f-9298-701f2fa07a3e:1"
    val newLinkId2 = "8b02477e-1e74-461e-af98-f06fc73a5ee9:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(change => change.newLinks.exists(link => link.linkId == newLinkId2))
    runWithRollback {
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId2).get
      val newRoadLink1 = roadLinkService.getRoadLinkByLinkId(newLinkId1).get
      val newRoadLink2 = roadLinkService.getRoadLinkByLinkId(newLinkId2).get
      val oldAsset1Id = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId1,
        assetValues, SideCode.BothDirections.value, Measures(0, oldRoadLink1.length), "testuser", 0L, Some(oldRoadLink1), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2022-01-01")))

      val oldAsset2Id = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId2,
        knownAssetValueAsphalt, SideCode.BothDirections.value, Measures(0, oldRoadLink2.length), "testuser", 0L, Some(oldRoadLink2), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2022-01-01")))


      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(oldAsset1Id, oldAsset2Id), false)

      assetsBefore.size should be(2)
      assetsBefore.map(asset => asset.expired == false should be(true))

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId1, newLinkId2), false)
      assetsAfter.size should be(2)
      assetsAfter.map(_.id).sorted should be(assetsBefore.map(_.id).sorted)
      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted(0).linkId should be(newLinkId1)
      sorted(0).startMeasure should be(0)
      sorted(0).endMeasure should be(newRoadLink1.length)
      PavementClass.extractPavementClassValue(sorted(0).value).get.value should be(99)

      sorted(1).linkId should be(newLinkId2)
      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(newRoadLink2.length)
      PavementClass.extractPavementClassValue(sorted(1).value).get.value should be(1)

    }
  }

  test("Given a split RoadLink that has a SurfaceType Paved; When both of the new RoadLinks have SurfaceType None; Then any Pavement assets on new RoadLinks should be removed.") {
    val oldLinkId = "56f914d6-9ab0-4554-90d5-0dd056b4d289:1"
    val newLinkId1 = "7a7ef383-c697-4b26-b752-89c5674facec:1"
    val newLinkId2 = "abd55e80-f2a0-49e0-9626-8af8673905c9:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.newLinks.exists(_.linkId == newLinkId1 ))
    val knownAssetValue = DynamicValue(DynamicAssetValue(List(DynamicProperty("paallysteluokka", "single_choice", false, List(DynamicPropertyValue(4)))
      , DynamicProperty("suggest_box", "checkbox", false, List())
    )))
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val oldAssetId = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId,
        knownAssetValue, SideCode.BothDirections.value, Measures(0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))

      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(oldAssetId), false)

      assetsBefore.size should be(1)
      assetsBefore.map(asset => asset.expired == false should be(true))

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)

      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val oldLinkReport = changeReport.changes.filter(_.linkId == oldLinkId).asInstanceOf[Seq[ChangedAsset]]

      oldLinkReport.size should equal(1)
      oldLinkReport.foreach(report => {
        report.changeType should equal(Deletion)
        report.assetId should equal(oldAssetId)
      })

      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId1, newLinkId2), false)
      assetsAfter.size should be(0)

    }
  }

  test("Merge: the pavement of one link is defined and the other is unknown. The defined pavement value should cover the merged link totally.") {
    val oldLinkId1 = "9afe0595-db08-40aa-aa5c-57aaf8e385c0:1"
    val oldLinkId2 = "155a4cdc-7306-4d09-99ae-ba1abbd7ad9a:1"
    val newLinkId = "ad672c3b-0eff-470a-a64b-a274ffe20327:1"
    val oldLinkIds = Seq(oldLinkId1, oldLinkId2)
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(c => c.oldLink.nonEmpty && oldLinkIds.contains(c.oldLink.get.linkId))

    runWithRollback {
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId2).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkId).get
      val oldAssetId1 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId1,
        unknownAssetValue, SideCode.BothDirections.value, Measures(0, oldRoadLink1.length), "testuser", 0L, Some(oldRoadLink1), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))
      val oldAssetId2 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId2,
        knownAssetValueAsphalt, SideCode.BothDirections.value, Measures(0, oldRoadLink2.length), "testuser", 0L, Some(oldRoadLink2), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))
      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(oldAssetId1, oldAssetId2), false)

      assetsBefore.size should be(2)
      assetsBefore.map(asset => asset.expired == false should be(true))

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)

      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId), false)
      assetsAfter.size should be(1)
      assetsAfter.head.id should be(oldAssetId2)
      assetsAfter.head.startMeasure should be(0)
      assetsAfter.head.endMeasure should be(newRoadLink.length)
      PavementClass.extractPavementClassValue(assetsAfter.head.value).get.value should be(1)

      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val sortedChanges = changeReport.changes.asInstanceOf[Seq[ChangedAsset]].sortBy(_.assetId)
      sortedChanges.size should equal(2)
      sortedChanges.head.changeType should equal(Deletion)
      sortedChanges.head.assetId should be(oldAssetId1)
      sortedChanges.last.changeType should equal(Replaced)
      sortedChanges.last.assetId should be(oldAssetId2)
    }
  }

  test("Version change: one part of the pavement of the old link is defined the other is unknown. The defined pavement value should cover the new link version totally.") {
    val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
    val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(c => c.oldLink.nonEmpty && c.oldLink.get.linkId == oldLinkId)
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkId).get
      val oldAssetId1 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId,
        unknownAssetValue, SideCode.BothDirections.value, Measures(0, 50), "testuser", 0L, Some(oldRoadLink), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))
      val oldAssetId2 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId,
        knownAssetValueAsphalt, SideCode.BothDirections.value, Measures(50, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))
      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(oldAssetId1, oldAssetId2), false)

      assetsBefore.size should be(2)
      assetsBefore.map(asset => asset.expired == false should be(true))

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)

      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId), false)
      assetsAfter.size should be(1)
      assetsAfter.head.id should be(oldAssetId2)
      assetsAfter.head.startMeasure should be(0)
      assetsAfter.head.endMeasure should be(newRoadLink.length)
      PavementClass.extractPavementClassValue(assetsAfter.head.value).get.value should be(1)

      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val sortedChanges = changeReport.changes.asInstanceOf[Seq[ChangedAsset]].sortBy(_.assetId)
      sortedChanges.size should equal(2)
      sortedChanges.head.changeType should equal(Deletion)
      sortedChanges.head.assetId should be(oldAssetId1)
      sortedChanges.last.changeType should equal(Replaced)
      sortedChanges.last.assetId should be(oldAssetId2)
    }
  }

  test("Retain pavement unknown when it is sole asset on link.") {
    val oldLinkId =  "c4238b5c-b775-47af-835a-04a36b1c1678:1"
    val oldLinkId2 = "a7dd4dad-d6db-4452-8baf-9e78bb6be2ce:1"
    val oldLinkId3 = "d1dbb935-d2ed-4a8a-aa9e-8eccc2ce978e:1"

    val newLinkId1 = "f7399b49-ab0f-4dd4-bf18-e6b4d0fecd0f:1"
    val newLinkId2 = "3b999fbc-f968-4cf0-a3f0-0bd7d7e624e9:1"
    val newLinkId3 = "cebbe5c6-9f8e-4b4d-b418-a124ca9ff4b9:1"
    val newLinkId4 = "0ab09cac-606d-4cd3-a56e-9612da029940:1"

    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    val assetValue1 = Some(DynamicValue(DynamicAssetValue(List(DynamicProperty("paallysteluokka", "single_choice", false, List(DynamicPropertyValue(99)))
      , DynamicProperty("suggest_box", "checkbox", false, List())
    ))))
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId2).get
      val oldRoadLink3 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId3).get

      val oldAssetId1 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId,
        assetValue1.get, SideCode.BothDirections.value, Measures(0, 197.487), "testuser", 0L, Some(oldRoadLink), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))
      val oldAssetId2 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId2,
        assetValue1.get, SideCode.BothDirections.value, Measures(0, 812.152), "testuser", 0L, Some(oldRoadLink2), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))

      val oldAssetId3 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId3,
        assetValue1.get, SideCode.BothDirections.value, Measures(0, 61.108), "testuser", 0L, Some(oldRoadLink3), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))


      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(oldAssetId1, oldAssetId2,oldAssetId3), false)

      assetsBefore.size should be(3)
      assetsBefore.map(asset => asset.expired == false should be(true))

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)

      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId1,newLinkId2,newLinkId3,newLinkId4), false)
      assetsAfter.size should be(4)
      PavementClass.extractPavementClassValue(assetsAfter(0).value).get.value should be(99)
      PavementClass.extractPavementClassValue(assetsAfter(1).value).get.value should be(99)
      PavementClass.extractPavementClassValue(assetsAfter(2).value).get.value should be(99)
      PavementClass.extractPavementClassValue(assetsAfter(3).value).get.value should be(99)
    }
  }

  test("A road link without pavement is split into two links. One has surface type Paved and the other None. " +
    "A new pavement should be generated for the link with Paved surface type") {
    val oldLinkId = "d4fcf9bb-f84d-487d-aa69-0e8b26facc19:1" //ST 1
    val newLinkId1 = "f35f7416-c37d-403a-a4bb-b3b693fd1ca2:1" //ST 2
    val newLinkId2 = "7c835caf-3884-4d86-b0c7-05bbce5321a4:1" // ST 1
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    runWithRollback {
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(oldLinkId), newTransaction = false)
      assetsBefore.size should be(0)
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId1, newLinkId2), newTransaction = false)
      assetsAfter.size should be(1)
      assetsAfter.head.linkId should be(newLinkId1)
      assetsAfter.head.startMeasure should be(0)
      assetsAfter.head.endMeasure should be(19.423) // newLink1 length
      assetsAfter.head.createdBy should be(Some(AutoGeneratedUsername.mmlPavedDefault))
      PavementClass.extractPavementClassValue(assetsAfter.head.value).get.value should be(99)

      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val wantedChange = changeReport.changes.asInstanceOf[Seq[ChangedAsset]].find(_.linkId == newLinkId1).get
      wantedChange.changeType should be(Creation)
      wantedChange.roadLinkChangeType should be(Split)
      wantedChange.after.size should be(1)
    }
  }

  test("A road link without pavement is replaced by another link. Both have surface type Paved, but the old link lacks PavedRoad asset." +
    "A PavedRoad asset should be generated to a new link.") {
    val oldLinkId = "d0660339-eb82-41a7-a0b9-e0c8303b4e7d:1" //ST 2
    val newLinkId = "1a5dc9c7-69f0-4235-bf58-874b613a8851:1" //ST 2
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    runWithRollback {
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(oldLinkId), newTransaction = false)
      assetsBefore.size should be(0)
      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId), newTransaction = false)
      assetsAfter.size should be(1)
      assetsAfter.head.linkId should be(newLinkId)
      assetsAfter.head.startMeasure should be(0)
      assetsAfter.head.endMeasure should be(16.437) // newLink1 length
      assetsAfter.head.createdBy should be(Some(AutoGeneratedUsername.mmlPavedDefault))
      PavementClass.extractPavementClassValue(assetsAfter.head.value).get.value should be(99)

      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val wantedChange = changeReport.changes.asInstanceOf[Seq[ChangedAsset]].find(_.linkId == newLinkId).get
      wantedChange.changeType should be(Creation)
      wantedChange.roadLinkChangeType should be(Replace)
      wantedChange.after.size should be(1)
    }
  }

  test("Merge: given 2 Unknown pavement assets with different SideCodes on 2 RoadLinks;" +
    "When the RoadLinks are merged;" +
    "Then 1 Pavement asset should be Deleted and the other moved to the new Link.") {
    val oldLinkId1 = "d05200c7-62cf-43b0-bdc9-22178c230e87:1"
    val oldLinkId2 = "5095a4b4-6dc3-445e-9e50-b49bf034bcd9:1"
    val newLinkId = "e741939b-774b-4011-b9c1-67fa1932c5a9:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.newLinks.exists(_.linkId == newLinkId))
    runWithRollback {
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId2).get
      val oldAssetId1 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId1,
        assetValues, SideCode.BothDirections.value, Measures(0, oldRoadLink1.length), "generated_in_update", 0L, Some(oldRoadLink1), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))
      val oldAssetId2 = dynamicLinearAssetService.createWithoutTransaction(PavedRoad.typeId, oldLinkId2,
        assetValues, SideCode.AgainstDigitizing.value, Measures(0, oldRoadLink2.length), "generated_in_update", 0L, Some(oldRoadLink2), true, Some(AutoGeneratedUsername.generatedInUpdate),
        Some(DateTime.parse("2020-01-01")), Some(AutoGeneratedUsername.generatedInUpdate), Some(DateTime.parse("2022-01-01")))

      val assetsBefore = dynamicLinearAssetService.getPersistedAssetsByIds(PavedRoad.typeId, Set(oldAssetId1, oldAssetId2), false)
      assetsBefore.size should be(2)

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)

      val assetsAfter = dynamicLinearAssetService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId), false)
      assetsAfter.size should be(1)
      (assetsAfter.head.endMeasure - (assetsBefore.head.endMeasure + assetsBefore.last.endMeasure) < 0.5) should be(true)

      val changeReport = ChangeReport(PavedRoad.typeId, TestPavedRoadUpdater.getReport())
      val sortedChanges = changeReport.changes.asInstanceOf[Seq[ChangedAsset]].sortBy(_.assetId)
      sortedChanges.size should equal(2)
      sortedChanges.head.changeType should equal(Deletion)
      sortedChanges.head.assetId should be(oldAssetId1)
      sortedChanges.last.changeType should equal(Replaced)
      sortedChanges.last.assetId should be(oldAssetId2)
    }
  }

  test("PavedRoad asset on WinterRoad should be expired when link is replaced with another WinterRoad with SurfaceType 2") {
    val oldLinkId = "ac33b29c-241e-41c8-a65b-ddc8096afbc4:1"
    val newLinkId = "710cdd1b-e221-48c8-b52d-92abbc1f35af:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.newLinks.exists(_.linkId == newLinkId))

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId).get
      val id1 = service.createWithoutTransaction(PavedRoad.typeId, oldLinkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, 22.035), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(PavedRoad.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfterOnOldLink = service.getPersistedAssetsByIds(PavedRoad.typeId, Set(id1), false)
      assetsAfterOnOldLink.size should be(1)
      assetsAfterOnOldLink.head.expired should be(true)
      val assetsAfterOnNewLink = service.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newLinkId), false)
      assetsAfterOnNewLink.size should equal (0)

      val reports = TestPavedRoadUpdater.getReport()
      reports.size should equal(1)
      reports.head.changeType should equal(ChangeTypeReport.Deletion)
      reports.head.roadLinkChangeType should equal(RoadLinkChangeType.Replace)
      reports.head.before.get.assetId should equal(assetsBefore.head.id)
      reports.head.after.size should equal(0)
    }
  }
}
