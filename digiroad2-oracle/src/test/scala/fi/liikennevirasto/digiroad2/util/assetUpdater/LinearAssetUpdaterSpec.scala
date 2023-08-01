package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinkIdGenerator, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer, GeometryUtils, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

import java.util.UUID
import scala.collection.mutable.ListBuffer

trait UpdaterUtilsSuite {

  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient: RoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao()
  val mockDynamicLinearAssetDao: DynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]

  val service = new LinearAssetService(mockRoadLinkService, mockEventBus)
  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, mockEventBus, new DummySerializer)
  }

  val roadLinkChangeClient = new RoadLinkChangeClient

  lazy val source = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json").mkString

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  def generateRandomKmtkId(): String = s"${UUID.randomUUID()}"
  def generateRandomLinkId(): String = LinkIdGenerator.generateRandom()

  // pseudo geometry 
  def generateGeometry(startPoint: Double, numberPoint: Long): (List[Point], Double) = {
    val points = new ListBuffer[Point]
    for (i <- 1 to numberPoint.toInt) {
      points.append(Point(i + startPoint, 0))
    }
    (points.toList, GeometryUtils.geometryLength(points))
  }

  val linkId1 = "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1";   val linkId2 = "753279ca-5a4d-4713-8609-0bd35d6a30fa:1";  
  val linkId4 = "c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1";   val linkId5 = "f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1";  
  val linkId6 = "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1";   val linkId7 = "d6a67594-6069-4295-a4d3-8495b7f76ef0:1";  
  val linkId8 = "7c21ce6f-d81b-481f-a67e-14778ad2b59c:1";   val linkId9 = "609d430a-de96-4cb7-987c-3caa9c72c08d:1";
  val linkId10 = "4b061477-a7bc-4b72-b5fe-5484e3cec03d:1";  val linkId11 = "b0b54052-7a0e-4714-80e0-9de0ea62a347:1";
  val linkId12 = "6d283aeb-a0f5-4606-8154-c6702bd69f2e:1";  val linkId13 = "769e6848-e77b-44d3-9d38-e63cdc7b1677:1"; 
  val linkId14 = "524c67d9-b8af-4070-a4a1-52d7aec0526c:1";  val linkId15 = "624df3a8-b403-4b42-a032-41d4b59e1840:1"
  val linkId1011 = Seq(linkId10, linkId11)
  val newLinks1_2_4 = Seq(linkId2, linkId1, linkId4)


  def createAsset(measures: Measures, value: Value, link: RoadLink, sideCode: SideCode = SideCode.BothDirections, typeId: Int = TrafficVolume.typeId): Long = {
    service.createWithoutTransaction(typeId, link.linkId, value, sideCode.value,
      measures, "testuser", 0L, Some(link), false, None, None)
  }

  def createRoadLink(id: String, geometry: (List[Point], Double),
                     roadClass: MTKClassWidth = MTKClassWidth.CarRoad_Ia, paved: SurfaceType = SurfaceType.Paved,
                     administrativeClassI: AdministrativeClass = Municipality,
                     functionalClassI: FunctionalClass = FunctionalClass1,
                     linkType: LinkType = Motorway,
                     trafficDirection: TrafficDirection = TrafficDirection.BothDirections
                    ): RoadLink = RoadLink(
    linkId = id, geometry = geometry._1, length = geometry._2,
    administrativeClass = administrativeClassI, functionalClass = functionalClassI.value,
    trafficDirection = trafficDirection, linkType = linkType, modifiedAt = None, modifiedBy = None,
    attributes = Map(
      "MTKCLASS" -> BigInt(roadClass.value),
      "SURFACETYPE" -> BigInt(paved.value),
      "CREATED_DATE" -> BigInt(0),
      "MUNICIPALITYCODE" -> BigInt(1)))

  val link1 = createRoadLink(linkId1, generateGeometry(0, 10))
  val link2 = createRoadLink(linkId2, generateGeometry(0, 15))
  val link3 = createRoadLink(linkId1, generateGeometry(0, 5))
  val link4 = createRoadLink(linkId1, generateGeometry(0, 10),
    trafficDirection = TrafficDirection.AgainstDigitizing)

  val link5 = createRoadLink(linkId1, generateGeometry(0, 10),
    trafficDirection = TrafficDirection.TowardsDigitizing)
  val link7 = createRoadLink(linkId1, generateGeometry(0, 9))

  val oldLink1 = (linkId: String) => createRoadLink(linkId, generateGeometry(0, 5))

  def changeRemove(oldRoadLinkId: String): RoadLinkChange = {
    val generatedGeometry = generateGeometry(0, 10)
    RoadLinkChange(
      changeType = RoadLinkChangeType.Remove,
      oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = generatedGeometry._2,
        geometry = generatedGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq.empty[RoadLinkInfo],
      replaceInfo = Seq.empty[ReplaceInfo])
  }

  def changeReplaceNewVersion(oldRoadLinkId: String, newRoadLikId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 9), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 9), newRoadLikId)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldId, Option(newLinkId1),
            oldFromMValue = 0.0, oldToMValue = 8, newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }

  def changeReplaceLenghenedFromEnd(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), linkId1)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldRoadLinkId, Option(newLinkId1),
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }

  def changeReplaceLenghenedFromEndBothDirections(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), linkId1)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.TowardsDigitizing)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldRoadLinkId, Option(newLinkId1),
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }

  def changeReplaceLenghenedFromEndTowardsDigitizing(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), linkId1)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.TowardsDigitizing
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldRoadLinkId, Option(newLinkId1),
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }

  def changeReplaceLenghenedFromEndAgainstDigitizing(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), linkId1)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.AgainstDigitizing
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldRoadLinkId, Option(newLinkId1),
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }

  def changeReplaceShortenedFromEnd(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 10), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 5), linkId1)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldRoadLinkId, Option(newLinkId1),
            oldFromMValue = 0.0, oldToMValue = oldLinkGeometry._2, newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }

  def changeReplaceMergeLongerLink(): Seq[RoadLinkChange] = {
    val (oldLinkGeometry1, oldId1) = (generateGeometry(0, 6), linkId1)
    val (oldLinkGeometry2, oldId2) = (generateGeometry(6, 6), linkId6)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 15), linkId2)
    Seq(RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(
        linkId = oldId1,
        linkLength = oldLinkGeometry1._2,
        geometry = oldLinkGeometry1._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldId1, Option(newLinkId1),
            oldFromMValue = 0.0, oldToMValue = 5, newFromMValue = Option(0.0), newToMValue = Option(5), false)
        )
    ),
      RoadLinkChange(
        changeType = RoadLinkChangeType.Replace,
        oldLink = Some(RoadLinkInfo(
          linkId = oldId2,
          linkLength = oldLinkGeometry2._2,
          geometry = oldLinkGeometry2._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections)),
        newLinks = Seq(
          RoadLinkInfo(
            linkId = newLinkId1,
            linkLength = newLinkGeometry1._2,
            geometry = newLinkGeometry1._1,
            roadClass = MTKClassWidth.CarRoad_Ia.value,
            adminClass = Municipality,
            municipality = 0,
            trafficDirection = TrafficDirection.BothDirections
          )),
        replaceInfo =
          List(ReplaceInfo(oldId2, Option(newLinkId1),
            oldFromMValue = 0.0, oldToMValue = 5, newFromMValue = Option(5.0), newToMValue = Option(newLinkGeometry1._2), false))
      )
    )
  }
}

class LinearAssetUpdaterSpec extends FunSuite with BeforeAndAfter with Matchers with UpdaterUtilsSuite {
  object TestLinearAssetUpdater extends LinearAssetUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  object TestLinearAssetUpdaterNoRoadLinkMock extends LinearAssetUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
  }

  before {
    TestLinearAssetUpdater.resetReport()
    TestLinearAssetUpdaterNoRoadLinkMock.resetReport()
  }
  
  test("case 1 links under asset is split") {
    val newLinks = Seq( linkId2,linkId1, linkId4)
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId5).get
      val id = service.createWithoutTransaction(TrafficVolume.typeId, linkId5, NumericValue(3), SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, newLinks, false)
      assetsAfter.size should be(3)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9.334)

      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(11.841)

      sorted(2).startMeasure should be(0)
      sorted(2).endMeasure should be(34.906)

      assetsAfter.map(v => v.value.isEmpty should be(false))
      assetsAfter.map(v => v.value.get should be(NumericValue(3)))

      val oldIds = Seq(id)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(3)

      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })

      val (generated, oldPart) = assets.partition(a => a.newAsset.get.assetId == 0)
      generated.size should be(2)
      oldPart.size should be(1)
    }
  }

  test("case 2 links under asset is merged") {

    runWithRollback {
      val linksid1 = linkId7
      val linksid2 = linkId8
      val change = roadLinkChangeClient.convertToRoadLinkChange(source)
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(linksid2).get

      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid1, NumericValue(3), SideCode.BothDirections.value, Measures(0, 23.48096698), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid2, NumericValue(3), SideCode.BothDirections.value, Measures(0, 66.25297685), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId14), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)
      
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(89.728)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))

      val oldIds = Seq(id1,id2)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))

      assets.size should be(2)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })

      TestLinearAssetUpdaterNoRoadLinkMock.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(1)
    }
  }

  test("case 2.1 links under asset is merged, longer one") {
    val linksid1 = linkId1
    val linksid2 = linkId6
    val linkGeometry1 = generateGeometry(0, 6)
    val linkGeometry2 = generateGeometry(6, 6)

    val oldRoadLink = createRoadLink(linksid1,linkGeometry1)
    val oldRoadLink2 = createRoadLink(linksid2,linkGeometry2)
    val change = changeReplaceMergeLongerLink()

    runWithRollback {
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid1, NumericValue(3), SideCode.BothDirections.value, Measures(0, linkGeometry1._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid2, NumericValue(3), SideCode.BothDirections.value, Measures(0, linkGeometry2._2), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId2))).thenReturn(Seq(link2))
      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId2), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.head.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(14)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))

      val oldIds = Seq(id1, id2)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })

      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(1)
    }
  }

  test("case 3 links under asset is replaced with longer links") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = createRoadLink(linkId, geometry)
    
    val change = changeReplaceLenghenedFromEnd(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link1))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
      })
    }
  }

  test("case 4 links under asset is replaced with shorter") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceShortenedFromEnd(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link3))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, 9), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
      })
    }
  }

  test("case 6 links version is changes, move to new version") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink = createRoadLink(linkIdVersion1,generateGeometry(0, 9))
    val newLink = createRoadLink(linkIdVersion2, geometry)
    val change = changeReplaceNewVersion(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkIdVersion1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2))).thenReturn(Seq(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkIdVersion1, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkIdVersion2), false)
      assetsAfter.size should be(1)
      assetsAfter.head.linkId should be(linkIdVersion2)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(8)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
      })
    }
  }

  test("case 8, link is removed") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeRemove(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set.empty[String], false)).thenReturn(Seq.empty[RoadLink])
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)
      assetsAfter.head.expired should be(true)

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      TestLinearAssetUpdater.getReport().head.changeType should be(ChangeTypeReport.Deletion)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })
    }
  }
  
  test("case 7, asset is split into multiple part, link split") {
    val oldMaxLength = 56.061
    val newLinks = Set(
      roadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId2).get,
      roadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1).get,
      roadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId4).get)
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId5).get
      val id1 = createAsset(Measures(0, 20), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(20, 40), NumericValue(4), oldRoadLink)
      val id3 = createAsset(Measures(40, oldMaxLength), NumericValue(5), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3), false)
      assetsBefore.size should be(3)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, newLinks.map(_.linkId).toSeq, false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.size should be(4)
      sorted.map(v => v.value.isEmpty should be(false))
      val asset1 = sorted.head
      val asset2 = sorted(1)
      val asset3 = sorted(2)
      val asset4 = sorted(3)
      
      asset1.startMeasure should be(0)
      asset1.endMeasure should be(9.334)
      asset1.value.get should be(NumericValue(3))

      asset2.startMeasure should be(0)
      asset2.endMeasure should be(11.841)
      asset2.value.get should be(NumericValue(3))

      asset3.startMeasure should be(0)
      asset3.endMeasure should be(18.842)
      asset3.value.get should be(NumericValue(4))

      asset4.startMeasure should be(18.842)
      asset4.endMeasure should be(34.906)
      asset4.value.get should be(NumericValue(5))

      val oldIds = Seq(id1,id2,id3)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(4)
      TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(_.changeType should be(ChangeTypeReport.Divided))
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })
      val (generated,oldPart) =assets.partition(a=>a.newAsset.get.assetId == 0)
      generated.size should be(2)
      oldPart.size should be(2)
    }
  }

  test("case 7, asset is split into multiple part, link merged") {
    
    runWithRollback {
      val linksid1 = linkId7
      val linksid2 = linkId8

      val changes = roadLinkChangeClient.convertToRoadLinkChange(source.mkString)
      
      val change = (changes, Seq(linksid1, linksid2).map(p => roadLinkService.getExpiredRoadLinkByLinkId(p).get))
      val oldRoadLink = change._2.find(_.linkId == linksid1).get
      val oldRoadLink2 = change._2.find(_.linkId == linksid2).get
      val oldMaxLength = 23.481
      val oldMaxLength2 = 66.253
      
      val id1 = createAsset(Measures(0, 10), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(10, oldMaxLength), NumericValue(4), oldRoadLink)
      val id3 = createAsset(Measures(0, 30), NumericValue(5), oldRoadLink2)
      val id4 = createAsset(Measures(30, oldMaxLength2), NumericValue(6), oldRoadLink2)
      
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3, id4), false)
      
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change._1)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId14), false)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.size should be(4)
      sorted.map(v => v.value.isEmpty should be(false))
      val asset1 = sorted.find(_.id == id1).get
      val asset2 = sorted.find(_.id == id2).get
      val asset3 = sorted.find(_.id == id3).get
      val asset4 = sorted.find(_.id == id4).get

      asset1.startMeasure should be(0)
      asset1.endMeasure should be(9.998)
      asset1.value.get should be(NumericValue(3))

      asset2.startMeasure should be(9.998)
      asset2.endMeasure should be(23.476)
      asset2.value.get should be(NumericValue(4))

      asset3.startMeasure should be(23.476)
      asset3.endMeasure should be(53.476)
      asset3.value.get should be(NumericValue(5))

      asset4.startMeasure should be(53.476)
      asset4.endMeasure should be(89.728)
      asset4.value.get should be(NumericValue(6))

      val oldIds = Seq(id1, id2, id3,id4)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(4)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
      })

    }
  }


  test("case 7, asset is split into multiple part, link merged, use Veturikatu") {
    runWithRollback {
      val linkId1 = "b36981f2-9e7e-4ee6-8ac0-9734ef3d390f:1"
      val linkId2 = "1d231ff5-1133-4d7d-b688-374ebcdb8f21:1"
      val change = roadLinkChangeClient.convertToRoadLinkChange(source)

      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(linkId1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(linkId2).get
      val oldMaxLength2 = 16.568
      val oldMaxLength3 = 202.283

      val id1 = createAsset(Measures(0, 8), NumericValue(5), oldRoadLink1)
      val id2 = createAsset(Measures(8, oldMaxLength2), NumericValue(6), oldRoadLink1)
      val id3 = createAsset(Measures(0, 100), NumericValue(7), oldRoadLink2)
      val id4 = createAsset(Measures(100, oldMaxLength3), NumericValue(8), oldRoadLink2)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3, id4), false)
      
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("ff8f3894-6312-4f38-9b51-e68ee919043a:1"), false)
      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.size should be(4)
      sorted.map(v => v.value.isEmpty should be(false))
      
      sorted(0).startMeasure should be(0)
      sorted(0).endMeasure should be(102.482)
      sorted(0).value.get should be(NumericValue(7))
      
      sorted(1).startMeasure should be(102.482)
      sorted(1).endMeasure should be(207.304)
      sorted(1).value.get should be(NumericValue(8))
      
      sorted(2).startMeasure should be(207.304)
      sorted(2).endMeasure should be(215.304)
      sorted(2).value.get should be(NumericValue(5))
      
      sorted(3).startMeasure should be(215.304)
      sorted(3).endMeasure should be(223.872)
      sorted(3).value.get should be(NumericValue(6))

      val oldIds = Seq(id1, id2,id3,id4)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(4)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
      })
    }
  }

  test("case 7, asset is split into multiple part, link merged, use Veturikatu, link position is moved into different place") {
    runWithRollback {
      val linksid1 = "1e2390ff-0910-4ffe-b1e7-2b281428e855:1"
      val change = roadLinkChangeClient.convertToRoadLinkChange(source)

      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid1).get
      val oldMaxLength = 11.715

      val id1 = createAsset(Measures(0, 5), NumericValue(3), oldRoadLink, SideCode.BothDirections, HeightLimit.typeId)
      val id2 = createAsset(Measures(5, oldMaxLength), NumericValue(4), oldRoadLink, SideCode.BothDirections, HeightLimit.typeId)
      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1, id2), false)
      
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(HeightLimit.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq("619d3fb8-056c-4745-8988-d143c29af659:1"), false)
      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.size should be(2)
      sorted.map(v => v.value.isEmpty should be(false))
      
      sorted.head.startMeasure should be(101.42)
      sorted.head.endMeasure should be(103.841)
      sorted.head.value.get should be(NumericValue(3))
      
      sorted.last.startMeasure should be(103.841)
      sorted.last.endMeasure should be(107.093)
      sorted.last.value.get should be(NumericValue(4))

      val oldIds = Seq(id1, id2)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
      })
    }
  }

  test("case 7, asset is split into multiple part, link merged and link is longer") {
    val linksid1 = linkId1
    val linksid2 = linkId6
    val linkGeometry1 = generateGeometry(0, 6)
    val linkGeometry2 = generateGeometry(6, 6)

    val oldRoadLink = createRoadLink(linksid1,generateGeometry(0, 6))
    val oldRoadLink2 = createRoadLink(linksid2,generateGeometry(6, 6))
    val change = changeReplaceMergeLongerLink()

    val oldMaxLength = linkGeometry1._2
    val oldMaxLength2 = linkGeometry2._2

    runWithRollback {
      val id1 = createAsset(Measures(0, 3), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(3, oldMaxLength), NumericValue(4), oldRoadLink)
      val id3 = createAsset(Measures(0, 3), NumericValue(5), oldRoadLink2)
      val id4 = createAsset(Measures(3, oldMaxLength2), NumericValue(6), oldRoadLink2)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3, id4), false)

      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId2))).thenReturn(Seq(link2))
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId2), false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.size should be(4)
      sorted.map(v => v.value.isEmpty should be(false))
      val asset1 = sorted.find(_.id == id1).get
      val asset2 = sorted.find(_.id == id2).get
      val asset3 = sorted.find(_.id == id3).get
      val asset4 = sorted.find(_.id == id4).get

      asset1.startMeasure should be(0)
      asset1.endMeasure should be(3)
      asset1.value.get should be(NumericValue(3))

      asset2.startMeasure should be(3)
      asset2.endMeasure should be(5)
      asset2.value.get should be(NumericValue(4))

      asset3.startMeasure should be(5)
      asset3.endMeasure should be(10.4)
      asset3.value.get should be(NumericValue(5))

      asset4.startMeasure should be(10.4)
      asset4.endMeasure should be(14.0)
      asset4.value.get should be(NumericValue(6))

      val oldIds = Seq(id1, id2,id3,id4)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(4)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
      })
    }
  }

  test("case 7, asset is split into multiple part, link is longer") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceLenghenedFromEnd(linkId)

    val oldMaxLength = geometry._2

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link1))
      val id1 = createAsset(Measures(0, 2), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(2, oldMaxLength), NumericValue(5), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)

      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))

      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      assetsAfter.size should be(2)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4.5)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))

      sorted(1).linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted(1).startMeasure should be(4.5)
      sorted(1).endMeasure should be(9.0)
      sorted(1).value.isEmpty should be(false)
      sorted(1).value.get should be(NumericValue(5))

      val oldIds = Seq(id1, id2)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
      })
    }
  }
  test("case 7, asset is split into multiple part, link shortened") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceShortenedFromEnd(linkId)
    val oldMaxLength = geometry._2
    
    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link3))
      val id1 = createAsset(Measures(0, 6), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(6, oldMaxLength), NumericValue(5), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)

      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))

      val oldIds = Seq(id1, id2)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })

      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(1)
      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Replaced).get._2.size should be(1)
    }
  }
  
  test("case 8.1 Road changes to two ways "){

    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = createRoadLink(linkId,generateGeometry(0, 5),trafficDirection = TrafficDirection.TowardsDigitizing)
    val change = changeReplaceLenghenedFromEndBothDirections(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link1))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link1))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.TowardsDigitizing)

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.TowardsDigitizing.value)
      })
    }
  }

  test("case 8.2 Road changes to two ways ") {

    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = createRoadLink(linkId,geometry,trafficDirection = TrafficDirection.AgainstDigitizing)
    
    val change = changeReplaceLenghenedFromEndBothDirections(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link1))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link1))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode)  should be(SideCode.AgainstDigitizing)

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be( a.newAsset.get.assetId)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.AgainstDigitizing.value)
      })
    }
  }
  
  test("case 9.1 Road changes to one ways, only one asset") {

    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceLenghenedFromEndAgainstDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link4))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link4))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode)  should be(SideCode.AgainstDigitizing)

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be(a.newAsset.get.assetId)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.AgainstDigitizing.value)
      })
    }
  }

  test("case 9.2 Road changes to one ways, only one asset") {

    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link5))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link5))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode)  should be(SideCode.TowardsDigitizing)

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be(a.newAsset.get.assetId)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.TowardsDigitizing.value)
      })

    }
    
  }

  test("case 10.1 Road changes to one ways and there more than one asset to both direction ") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceLenghenedFromEndAgainstDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link4))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link4))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1,id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.AgainstDigitizing)

      val oldIds = Seq(id1,id2)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.filter(_.newAsset.isDefined).map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be(a.newAsset.get.assetId)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.AgainstDigitizing.value)
      })
      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(1)
    }
  }

  test("case 10.2 Road changes to one ways and there more than one asset to both direction , case 2") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = createRoadLink(linkId, geometry)
    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId2, false)).thenReturn(Some(link5))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link5))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1,id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(4))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.TowardsDigitizing)

      val oldIds = Seq(id1,id2)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.filter(_.newAsset.isDefined).map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be(a.newAsset.get.assetId)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.TowardsDigitizing.value)
      })
      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(1)
    }
  }
  
  test("case 10.3 each sides has two value and mirroring, case 4 ") {
    val linkId = generateRandomLinkId()
    val oldRoadLink = oldLink1(linkId)

    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link5))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link5))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(2,4), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id3 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(5), SideCode.TowardsDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id4 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(6), SideCode.AgainstDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2,id3,id4), false)
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(2)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4.5)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(5))
      
      sorted.last.startMeasure should be(4.5)
      sorted.last.endMeasure should be(9)
      sorted.last.value.isEmpty should be(false)
      sorted.last.value.get should be(NumericValue(4))

      sorted.foreach(p=>{
        SideCode.apply(p.sideCode) should be(SideCode.TowardsDigitizing)
      })

      val oldIds = Seq(id1,id2,id3,id4)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(4)
      assets.filter(_.newAsset.isDefined).map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.TowardsDigitizing.value)
      })

      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(2)
    }
  }
  test("case 10.3.1 each sides has two value and mirroring, case 4 ") {
    val linkId = generateRandomLinkId()
    val oldRoadLink = oldLink1(linkId)
    val change = changeReplaceLenghenedFromEndAgainstDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link4))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link4))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id3 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(5), SideCode.TowardsDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id4 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(6), SideCode.AgainstDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3, id4), false)
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkId1), false)
      assetsAfter.size should be(2)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4.5)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))

      sorted.last.startMeasure should be(4.5)
      sorted.last.endMeasure should be(9)
      sorted.last.value.isEmpty should be(false)
      sorted.last.value.get should be(NumericValue(6))

      sorted.foreach(p => {
        SideCode.apply(p.sideCode) should be(SideCode.AgainstDigitizing)
      })

      val oldIds = Seq(id1, id2, id3, id4)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(4)
      assets.filter(_.newAsset.isDefined).map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.AgainstDigitizing.value)
      })

      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(2)
    }
  }
  test("case 10.4 consecutive with different side code but same value") {
    val linkId = generateRandomLinkId()
    val oldRoadLink = oldLink1(linkId)
    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link5))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link5))
      val id1 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)
   
      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(HeightLimit.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(4.5)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.TowardsDigitizing)

      val oldIds = Seq(id1, id2)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.filter(_.newAsset.isDefined).map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.TowardsDigitizing.value)
      })

      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(1)
    }
  }

  test("case 10.4.1 consecutive with different side code but same value") {
    val linkId = generateRandomLinkId()
    val oldRoadLink = oldLink1(linkId)
    val change = changeReplaceLenghenedFromEndAgainstDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link4))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link4))
      val id1 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)


      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(HeightLimit.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4.5)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.AgainstDigitizing)

      val oldIds = Seq(id1, id2)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.filter(_.newAsset.isDefined).map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.AgainstDigitizing.value)
      })

      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(1)
    }
  }

  test("case 10.5 one side full length but but other side splitted") {
    val linkId = generateRandomLinkId()
    val oldRoadLink = createRoadLink(linkId, generateGeometry(0, 5))
    
    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(link5))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(link5))
      val id1 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id3 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(4), SideCode.AgainstDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1, id2,id3), false)
      assetsBefore.size should be(3)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(HeightLimit.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq(linkId1), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4.5)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.TowardsDigitizing)

      val oldIds = Seq(id1, id2,id3)
      val assets = TestLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(3)
      assets.filter(_.newAsset.isDefined).map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.TowardsDigitizing.value)
      })

      TestLinearAssetUpdater.getReport().groupBy(_.changeType).find(_._1 == ChangeTypeReport.Deletion).get._2.size should be(2)
    }
  }

  test("case 10.7 links under asset is split, different values each side first part change to one direction") {
    val linkId = linkId9
    val newLinks = linkId1011
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 45.230), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(0, 45.230), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id,id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, newLinks, false)
      assetsAfter.size should be(3)
      
      val oneDirectionalLink = assetsAfter.filter(_.linkId == linkId11)
      oneDirectionalLink.head.startMeasure should be(0)
      oneDirectionalLink.head.endMeasure should be(13.906)
      oneDirectionalLink.head.value.get should  be(NumericValue(3))
      SideCode(oneDirectionalLink.head.sideCode) should be(SideCode.AgainstDigitizing)
      
      val twoDirectionalLink = assetsAfter.filter(_.linkId == linkId10).sortBy(_.sideCode)
      twoDirectionalLink.head.startMeasure should be(0)
      twoDirectionalLink.head.endMeasure should be(35.010)
      twoDirectionalLink.head.value.get should  be(NumericValue(4))
      SideCode(twoDirectionalLink.head.sideCode) should be(SideCode.TowardsDigitizing)

      twoDirectionalLink.last.startMeasure should be(0)
      twoDirectionalLink.last.endMeasure should be(35.010)
      twoDirectionalLink.last.value.get should be(NumericValue(3))
      SideCode(twoDirectionalLink.last.sideCode) should be(SideCode.AgainstDigitizing)

      val oldIds =  Seq(id,id2)
      val assets= TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a=> PairAsset(a.before,a.after.headOption,a.changeType))
      assets.size should be(3)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })

    }
  }
  test("case 10.8 links under asset is split, different values each side first part change to one direction") {
    val linkId = linkId9
    val newLinks = linkId1011
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, 45.230), "testuser", 0L, Some(oldRoadLink), false, None, None)
    
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, newLinks, false)
      assetsAfter.size should be(2)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(13.906)
      SideCode(sorted.head.sideCode) should be(SideCode.AgainstDigitizing)

      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(35.010)
      SideCode(sorted(1).sideCode) should be(SideCode.BothDirections)

      assetsAfter.map(v => v.value.isEmpty should be(false))
      assetsAfter.map(v => v.value.get should be(NumericValue(3)))

      val oldIds = Seq(id)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })

    }
  }
  
  test("case 11 Roads digitization direction changes, case 3b") {
    val linkId = linkId12
    val linkIdNew = linkId13

    val change = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id1 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, 221.892), "testuser", 0L, Some(oldRoadLink), false, None, None)
 
      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(HeightLimit.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq(linkIdNew), false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.startMeasure should be(55.502)
      sorted.head.endMeasure should be(71.924)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))
      SideCode.apply(sorted.head.sideCode) should be(SideCode.BothDirections)

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })
    }
  }

  test("case 11 Roads digitization direction changes, case 3b2") {
    val linkId = linkId12
    val linkIdNew = linkId13

    val change = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id1 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, 221.892), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(4), SideCode.AgainstDigitizing.value, Measures(0, 221.892), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(HeightLimit.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq(linkIdNew), false)

      val sorted = assetsAfter.sortBy(_.sideCode)

      sorted(0).startMeasure should be(55.502)
      sorted(0).endMeasure should be(71.924)
      sorted(0).value.isEmpty should be(false)
      sorted(0).value.get should be(NumericValue(4))
      SideCode.apply(sorted(0).sideCode) should be(SideCode.TowardsDigitizing)

      sorted(1).startMeasure should be(55.502)
      sorted(1).endMeasure should be(71.924)
      sorted(1).value.isEmpty should be(false)
      sorted(1).value.get should be(NumericValue(3))
      SideCode.apply(sorted(1).sideCode) should be(SideCode.AgainstDigitizing)
      TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a=> PairAsset(a.before,a.after.headOption,a.changeType)).size should be(2)

      val oldIds = Seq(id1,id2)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })
    }
  }
  
  test("case 11 Roads digitization direction changes") {
    val linkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val linkIdNew = "eca24369-a77b-4e6f-875e-57dc85176003:1"

    val change = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id1 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, 18.081), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(HeightLimit.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq(linkIdNew), false)
      
      val sorted = assetsAfter.sortBy(_.endMeasure)
      
      sorted.head.startMeasure should be(0) 
      sorted.head.endMeasure should be(18.082)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))
      SideCode.apply(sorted.head.sideCode) should be(SideCode.AgainstDigitizing)

      TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a=> PairAsset(a.before,a.after.headOption,a.changeType)).size should be(1)

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.AgainstDigitizing.value)
      })
    }
  }

  test("change info comes originally in two parts, merged in RoadLinkChangeClient") {
    val linkId = "350e7577-020e-4abe-bf12-509f070371ed:1"
    val linkIdNew = "dfdf4f1e-9e12-4dc0-9b44-868320164539:1"

    val change = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id1 = service.createWithoutTransaction(HeightLimit.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, 189.231), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(HeightLimit.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq(linkIdNew), false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(191.552)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))
      SideCode.apply(sorted.head.sideCode) should be(SideCode.TowardsDigitizing)

      TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a=> PairAsset(a.before,a.after.headOption,a.changeType)).size should be(1)

      val oldIds = Seq(id1)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.newAsset.get.linearReference.get.sideCode.get should be(SideCode.TowardsDigitizing.value)
      })
    }
  }

  test("Split. Given a Road Link that is split into 2 new Links; when 1 new Link is deleted; then the Linear Asset's length should equal remaining Link's length.") {
    val oldLinkID = "086404cc-ffaa-46e5-a0c5-b428a846261c:1"
    val newLinkID2 = "da1ce256-2f8a-43f9-9008-5bf058c1bcd7:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID2).get
      val id = service.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, NumericValue(50), SideCode.BothDirections.value, Measures(0, 79.405), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(SpeedLimitAsset.typeId, Seq(newLinkID2), false)
      assetsAfter.size should be(1)
      assetsAfter.head.typeId should be(assetsBefore.head.typeId)
      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetLength should be(newRoadLink.length)
    }
  }
  
  
  test ("change come in multiple part, values same") {

    val oldLink1 = "ed1dff4a-b3f1-41a1-a1af-96e896c3145d:1"
    val oldLink2 = "197f22f2-3427-4412-9d2a-3848a570c996:1"

    val linkIdNew1 = "59704775-596d-46c8-99cf-e85013bbcb56:1"
    val linkIdNew2 = "d989ee2b-f6d0-4433-b5b6-0a4fe3d62400:1"
    
    val change = roadLinkChangeClient.convertToRoadLinkChange(source)
    runWithRollback {
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLink1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLink2).get
      val id1 = service.createWithoutTransaction(HeightLimit.typeId, oldLink1, NumericValue(3), SideCode.BothDirections.value, Measures(0, 389.737), "testuser", 0L, Some(oldRoadLink1), false, None, None)
      val id2 = service.createWithoutTransaction(HeightLimit.typeId, oldLink2, NumericValue(3), SideCode.BothDirections.value, Measures(0, 49.772), "testuser", 0L, Some(oldRoadLink2), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(HeightLimit.typeId, Set(id1,id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(HeightLimit.typeId, change)

      val assetsAfter = service.getPersistedAssetsByLinkIds(HeightLimit.typeId, Seq(linkIdNew1,linkIdNew2), false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.foreach(p => {
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      sorted.size should be(2)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(101.922)
      sorted.last.startMeasure should be(0)
      sorted.last.endMeasure should be(337.589)

      val oldIds = Seq(id1,id2)
      val assets = TestLinearAssetUpdaterNoRoadLinkMock.getReport().map(a => PairAsset(a.before, a.after.headOption, a.changeType))
      assets.size should be(2)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
        a.oldAsset.get.assetId should be(a.newAsset.get.assetId)
      })
    }
  }

  test("Split. Given a Road Link that is split into 2 new Links; when 1 new Link is deleted; then the Linear Asset on the deleted Link should be expired.") {
    val oldLinkID = "086404cc-ffaa-46e5-a0c5-b428a846261c:1"
    val newLinkID = "da1ce256-2f8a-43f9-9008-5bf058c1bcd7:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      val id = service.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, NumericValue(50), SideCode.BothDirections.value, Measures(70.0, 75.0), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, NumericValue(50), SideCode.BothDirections.value, Measures(50.0, 75.0), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsAfter.size should be(1)
      assetsAfter.head.linkId should be(oldLinkID)
      assetsAfter.head.expired should be(true)
    }
  }

  test("Split. Given a Road Link that is split into 2 new Links; when 1 new Link is deleted; then the Linear Asset on both the deleted and remaining Link should remain") {
    val oldLinkID = "086404cc-ffaa-46e5-a0c5-b428a846261c:1"
    val newLinkID = "da1ce256-2f8a-43f9-9008-5bf058c1bcd7:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      val id = service.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, NumericValue(50), SideCode.BothDirections.value, Measures(50.0, 75.0), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsAfter.size should be(1)
      assetsAfter.head.linkId should be(newLinkID)
      assetsAfter.head.expired should be(false)
    }
  }
}