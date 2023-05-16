package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{MTKClassWidth, NumericValue, RoadLink, Value}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinkIdGenerator, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer, GeometryUtils, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

import java.util.UUID
import scala.collection.mutable.ListBuffer

class LinearAssetUpdaterSpec extends FunSuite with Matchers {

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
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

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

  val roadLinkChangeClient = new RoadLinkChangeClient

  lazy val source = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json").mkString

  private def generateRandomKmtkId(): String = s"${UUID.randomUUID()}"
  private def generateRandomLinkId(): String = LinkIdGenerator.generateRandom()

  // pseudo geometry 
  def generateGeometry(startPoint: Double, numberPoint: Long): (List[Point], Double) = {
    val points = new ListBuffer[Point]
    for (i <- 1 to numberPoint.toInt) {
      points.append(Point(i + startPoint, 0))
    }
    (points.toList, GeometryUtils.geometryLength(points))
  }

  def createAsset(measures: Measures, value: Value, link: RoadLink, sideCode:SideCode = SideCode.BothDirections ): Long = {
    service.createWithoutTransaction(TrafficVolume.typeId, link.linkId, value, sideCode.value,
      measures, "testuser", 0L, Some(link), false, None, None)
  }

  def createRoadLink(id: String, length: Double) = {
    RoadLink(id, Seq(Point(0.0, 0.0), Point(length, 0.0)), length, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
  }

  def changeAdd(newID: String): RoadLinkChange = {
    val generatedGeometry = generateGeometry(0, 10)
    RoadLinkChange(
      changeType = RoadLinkChangeType.Add,
      oldLink = None,
      newLinks = Seq(RoadLinkInfo(
        linkId = newID,
        linkLength = generatedGeometry._2,
        geometry = generatedGeometry._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections
      )),
      replaceInfo = Seq.empty[ReplaceInfo])
  }

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
          ReplaceInfo(oldId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 8, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
    )
  }

  def changeReplaceLenghenedFromEnd(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")

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
          ReplaceInfo(oldRoadLinkId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
    )
  }

  def changeReplaceLenghenedFromEndBothDirections(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")

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
          ReplaceInfo(oldRoadLinkId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
    )
  }

  def changeReplaceLenghenedFromEndTowardsDigitizing(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")

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
          ReplaceInfo(oldRoadLinkId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
    )
  }

  def changeReplaceLenghenedFromEndAgainstDigitizing(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")

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
          ReplaceInfo(oldRoadLinkId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
    )
  }
  
  def changeReplaceShortenedFromEnd(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 10), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 5), "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")

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
          ReplaceInfo(oldRoadLinkId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = oldLinkGeometry._2, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
    )
  }

  def changeReplaceMergeLongerLink(): Seq[RoadLinkChange] = {
    val (oldLinkGeometry1, oldId1) = (generateGeometry(0, 6), "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")
    val (oldLinkGeometry2, oldId2) = (generateGeometry(6, 6), "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1")
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 15), "753279ca-5a4d-4713-8609-0bd35d6a30fa:1")
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
          ReplaceInfo(oldId1, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 5, newFromMValue = 0.0, newToMValue = 5, false)
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
          List(ReplaceInfo(oldId2, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 5, newFromMValue = 5.0, newToMValue = newLinkGeometry1._2, false))
      )
    )
  }

  test("case 1 links under asset is split") {
    val linkId = "f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1"
    val newLinks = Seq( "753279ca-5a4d-4713-8609-0bd35d6a30fa:1","c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", "c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1")
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
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
    }
  }

  test("case 2 links under asset is merged") {

    runWithRollback {
      val linksid1 = "d6a67594-6069-4295-a4d3-8495b7f76ef0:1"
      val linksid2 = "7c21ce6f-d81b-481f-a67e-14778ad2b59c:1"
      val change = roadLinkChangeClient.convertToRoadLinkChange(source)
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(linksid2).get

      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid1, NumericValue(3), SideCode.BothDirections.value, Measures(0, 23.48096698), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid2, NumericValue(3), SideCode.BothDirections.value, Measures(0, 66.25297685), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("524c67d9-b8af-4070-a4a1-52d7aec0526c:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)
      
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(89.728)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }

  test("case 2.1 links under asset is merged, longer one") {
    val linksid1 = "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
    val linksid2 = "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1"
    val linkGeometry1 = generateGeometry(0, 6)
    val linkGeometry2 = generateGeometry(6, 6)

    val oldRoadLink = RoadLink(linksid1, linkGeometry1._1, linkGeometry1._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val oldRoadLink2 = RoadLink(linksid2, linkGeometry2._1, linkGeometry2._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceMergeLongerLink()

    runWithRollback {
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid1, NumericValue(3), SideCode.BothDirections.value, Measures(0, linkGeometry1._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid2, NumericValue(3), SideCode.BothDirections.value, Measures(0, linkGeometry2._2), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.head.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(14)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }

  test("case 3 links under asset is replaced with longer links") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceLenghenedFromEnd(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }

  test("case 4 links under asset is replaced with shorter") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceShortenedFromEnd(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }

  test("case 6 links version is changes, move to new version") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink = RoadLink(linkIdVersion1, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceNewVersion(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkIdVersion1, false)).thenReturn(Some(oldRoadLink))
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
    }
  }

  test("case 8, link is removed") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeRemove(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)
      assetsAfter.head.expired should be(true)
    }
  }
  
  test("case 7, asset is split into multiple part, link split") {
    val oldMaxLength = 56.061
    val newLinks = Set(
      roadLinkService.getRoadLinkAndComplementaryByLinkId("753279ca-5a4d-4713-8609-0bd35d6a30fa:1").get,
      roadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1").get,
      roadLinkService.getRoadLinkAndComplementaryByLinkId("c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1").get)
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId("f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1").get
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

    }
  }

  test("case 7, asset is split into multiple part, link merged") {
    
    runWithRollback {
      val linksid1 = "d6a67594-6069-4295-a4d3-8495b7f76ef0:1"
      val linksid2 = "7c21ce6f-d81b-481f-a67e-14778ad2b59c:1"

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
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("524c67d9-b8af-4070-a4a1-52d7aec0526c:1"), false)
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

    }
  }

  test("case 7, asset is split into multiple part, link merged, use Veturikatu, link position is moved into different place") {
    runWithRollback {
      val linksid1 = "1e2390ff-0910-4ffe-b1e7-2b281428e855:1"
      val change = roadLinkChangeClient.convertToRoadLinkChange(source)

      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid1).get
      val oldMaxLength = 11.715

      val id1 = createAsset(Measures(0, 5), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(5, oldMaxLength), NumericValue(4), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("619d3fb8-056c-4745-8988-d143c29af659:1"), false)
      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.size should be(2)
      sorted.map(v => v.value.isEmpty should be(false))
      
      sorted.head.startMeasure should be(101.42)
      sorted.head.endMeasure should be(103.841)
      sorted.head.value.get should be(NumericValue(3))
      
      sorted.last.startMeasure should be(103.841)
      sorted.last.endMeasure should be(107.093)
      sorted.last.value.get should be(NumericValue(4))
    }
  }

  test("case 7, asset is split into multiple part, link merged and link is longer") {
    val linksid1 = "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
    val linksid2 = "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1"
    val linkGeometry1 = generateGeometry(0, 6)
    val linkGeometry2 = generateGeometry(6, 6)

    val oldRoadLink = RoadLink(linksid1, linkGeometry1._1, linkGeometry1._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val oldRoadLink2 = RoadLink(linksid2, linkGeometry2._1, linkGeometry2._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceMergeLongerLink()

    val oldMaxLength = linkGeometry1._2
    val oldMaxLength2 = linkGeometry2._2

    runWithRollback {
      val id1 = createAsset(Measures(0, 3), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(3, oldMaxLength), NumericValue(4), oldRoadLink)
      val id3 = createAsset(Measures(0, 3), NumericValue(5), oldRoadLink2)
      val id4 = createAsset(Measures(3, oldMaxLength2), NumericValue(6), oldRoadLink2)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3, id4), false)

      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1"), false)

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
    }
  }

  test("case 7, asset is split into multiple part, link is longer") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceLenghenedFromEnd(linkId)

    val oldMaxLength = geometry._2

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      val id1 = createAsset(Measures(0, 2), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(2, oldMaxLength), NumericValue(5), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)

      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))

      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      assetsAfter.size should be(2)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4.5)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))

      sorted(1).linkId should be(change.replaceInfo.head.newLinkId)
      sorted(1).startMeasure should be(4.5)
      sorted(1).endMeasure should be(9.0)
      sorted(1).value.isEmpty should be(false)
      sorted(1).value.get should be(NumericValue(5))
    }
  }
  test("case 7, asset is split into multiple part, link shortened") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceShortenedFromEnd(linkId)
    val oldMaxLength = geometry._2
    
    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      val id1 = createAsset(Measures(0, 6), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(6, oldMaxLength), NumericValue(5), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)

      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }
  
  test("case 8.1 Road changes to two ways "){

    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val geometryNew = generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceLenghenedFromEndBothDirections(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.TowardsDigitizing)
    }
  }

  test("case 8.2 Road changes to two ways ") {

    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceLenghenedFromEndBothDirections(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode)  should be(SideCode.AgainstDigitizing)
    }
  }
  
  test("case 9.1 Road changes to one ways, only one asset") {

    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    
    val change = changeReplaceLenghenedFromEndAgainstDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode)  should be(SideCode.AgainstDigitizing)
    }
  }

  test("case 9.2 Road changes to one ways, only one asset") {

    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    
    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode)  should be(SideCode.TowardsDigitizing)
    }
    
  }
  // check for split situation
  // and merge
  test("case 10.1 Road changes to one ways and there more than one asset to both direction ") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val change = changeReplaceLenghenedFromEndAgainstDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1,id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.AgainstDigitizing)
    }
  }

  test("case 10.2 Road changes to one ways and there more than one asset to both direction , case 2") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1,id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(4))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.TowardsDigitizing)
    }
  }
  
  test("case 10.3 each sides has two value and mirroring, case 4 ") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5) //4
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(2,4), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id3 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(5), SideCode.TowardsDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id4 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(6), SideCode.AgainstDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2,id3,id4), false)
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(2)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
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
    }
  }
  test("case 10.3.1 each sides has two value and mirroring, case 4 ") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5) //4
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val change = changeReplaceLenghenedFromEndAgainstDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id3 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(5), SideCode.TowardsDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id4 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(6), SideCode.AgainstDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3, id4), false)
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(2)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
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
    }
  }
  test("case 10.4 consecutive with different side code but same value") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5) //4
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)
      
   
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(4.5)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.TowardsDigitizing)
    }
  }

  test("case 10.4.1 consecutive with different side code but same value") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5) //4
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val change = changeReplaceLenghenedFromEndAgainstDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)


      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4.5)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.AgainstDigitizing)
    }
  }

  test("case 10.5 one side full length but but other side splitted") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5) //4
    val oldRoadLink = RoadLink(linkId, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val geometryNew = generateGeometry(0, 10)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    val change = changeReplaceLenghenedFromEndTowardsDigitizing(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, 2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id3 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.AgainstDigitizing.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2,id3), false)
      assetsBefore.size should be(3)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4.5)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
      SideCode.apply(assetsAfter.head.sideCode) should be(SideCode.TowardsDigitizing)
    }
  }

/*  "old": {
    "linkId": "c19bd6b4-9923-4ce9-a9cb-09779708913e:1",
    "linkLength": 121.673,
    "roadClass": 12131,
    "adminClass": 1,
    "municipality": 49,
    "trafficDirection": 1
  }
  ,*/

  test("case 10.7 links under asset is split, different values each side first part change to one direction") {
    val linkId = "609d430a-de96-4cb7-987c-3caa9c72c08d:1"
    val newLinks = Seq("4b061477-a7bc-4b72-b5fe-5484e3cec03d:1", "b0b54052-7a0e-4714-80e0-9de0ea62a347:1")
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
      
      val oneDirectionalLink = assetsAfter.filter(_.linkId == "b0b54052-7a0e-4714-80e0-9de0ea62a347:1")
      oneDirectionalLink.head.startMeasure should be(0)
      oneDirectionalLink.head.endMeasure should be(13.906)
      oneDirectionalLink.head.value.get should  be(NumericValue(3))
      SideCode(oneDirectionalLink.head.sideCode) should be(SideCode.AgainstDigitizing)
      
      val twoDirectionalLink = assetsAfter.filter(_.linkId == "4b061477-a7bc-4b72-b5fe-5484e3cec03d:1").sortBy(_.sideCode)
      twoDirectionalLink.head.startMeasure should be(0)
      twoDirectionalLink.head.endMeasure should be(35.010)
      twoDirectionalLink.head.value.get should  be(NumericValue(4))
      SideCode(twoDirectionalLink.head.sideCode) should be(SideCode.TowardsDigitizing)

      twoDirectionalLink.last.startMeasure should be(0)
      twoDirectionalLink.last.endMeasure should be(35.010)
      twoDirectionalLink.last.value.get should be(NumericValue(3))
      SideCode(twoDirectionalLink.last.sideCode) should be(SideCode.AgainstDigitizing)
    }
  }
  test("case 10.8 links under asset is split, different values each side first part change to one direction") {
    val linkId = "609d430a-de96-4cb7-987c-3caa9c72c08d:1"
    val newLinks = Seq("4b061477-a7bc-4b72-b5fe-5484e3cec03d:1", "b0b54052-7a0e-4714-80e0-9de0ea62a347:1")
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
    }
  }
  
  test("case 11 Roads digitization direction changes, case 3b") {
    val linkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val linkIdNew = "eca24369-a77b-4e6f-875e-57dc85176003:1"

    val change = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      fail("Need to be implemented")
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.BothDirections.value, Measures(0, 18.081), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.BothDirections.value, Measures(2, 4), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkIdNew), false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(18.082)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))
      SideCode.apply(sorted.head.sideCode) should be(SideCode.AgainstDigitizing)
    }
  }

  test("case 11 Roads digitization direction changes, case 3c") {
    val linkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val linkIdNew = "eca24369-a77b-4e6f-875e-57dc85176003:1"

    val change = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value,Measures(0, 10.081) , "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(4), SideCode.AgainstDigitizing.value,Measures(10.081, 18.081), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkIdNew), false)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(18.082)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))
      SideCode.apply(sorted.head.sideCode) should be(SideCode.AgainstDigitizing)
    }
  }
  
  test("case 11 Roads digitization direction changes") {
    val linkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val linkIdNew = "eca24369-a77b-4e6f-875e-57dc85176003:1"

    val change = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkId, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, 18.081), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linkIdNew), false)
      
      val sorted = assetsAfter.sortBy(_.endMeasure)
      
      sorted.head.startMeasure should be(0) 
      sorted.head.endMeasure should be(18.082)
      sorted.head.value.isEmpty should be(false)
      sorted.head.value.get should be(NumericValue(3))
      SideCode.apply(sorted.head.sideCode) should be(SideCode.AgainstDigitizing)
    }
  }
}
