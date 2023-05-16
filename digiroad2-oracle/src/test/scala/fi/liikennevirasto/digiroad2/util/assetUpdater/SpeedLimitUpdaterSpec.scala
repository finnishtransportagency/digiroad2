package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinkIdGenerator, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer, GeometryUtils, Point}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

import java.util.UUID
import scala.collection.mutable.ListBuffer

class SpeedLimitUpdaterSpec extends FunSuite with Matchers {

  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient: RoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  //val mockFunctionalClassDao: RoadLinkOverrideDAO = MockitoSugar.mock[RoadLinkOverrideDAO.FunctionalClassDao]
  val speedLimitDao = new PostGISLinearAssetDao()
  val mockDynamicLinearAssetDao: DynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val service = new SpeedLimitService(mockEventBus,mockRoadLinkService)
  val serviceNoMock = new SpeedLimitService(mockEventBus,roadLinkService)
  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, mockEventBus, new DummySerializer)
  }
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  
  object TestLinearAssetUpdater extends SpeedLimitUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  object TestLinearAssetUpdaterNoRoadLinkMock extends SpeedLimitUpdater(serviceNoMock) {
    override def withDynTransaction[T](f: => T): T = f
    override def eventBus: DigiroadEventBus = mockEventBus
  }

  val roadLinkChangeClient = new RoadLinkChangeClient

  lazy val source = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json").mkString

  private def generateRandomKmtkId(): String = s"${UUID.randomUUID()}"
  private def generateRandomLinkId(): String = LinkIdGenerator.generateRandom()
  
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
        municipality = 60,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 60,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 8, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
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
        municipality = 60,
        trafficDirection = TrafficDirection.TowardsDigitizing)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 60,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldRoadLinkId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false))
    )
  }
  
  test("case 1 links under asset is split, smoke test") {
    val linksid = "f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1"
    val newLinks = Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1","c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", "c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1")
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid).get
      val oldRoadLinkRaw = roadLinkService. getExpiredRoadLinkByLinkIdNonEncrished(linksid)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linksid)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      //when(mockFunctionalClassDao.getExistingValues(any[Seq[String]])).thenReturn(Seq.empty[RoadLinkValue])
      val id = service.createWithoutTransaction(SpeedLimitAsset.typeId, linksid,
        SpeedLimitValue(30), SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(SpeedLimitAsset.typeId, newLinks, false)
      assetsAfter.size should be(3)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9.334)

      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(11.841)

      sorted(2).startMeasure should be(0)
      sorted(2).endMeasure should be(34.906)

      assetsAfter.map(v => v.value.isEmpty should be(false))
      assetsAfter.map(v => v.value.get should be(SpeedLimitValue(30)))
    }
  }
  
  test("Create unknown speed limit when there is new links"){
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.changeType == RoadLinkChangeType.Add)

    runWithRollback {
      val newlinks = "624df3a8-b403-4b42-a032-41d4b59e1840:1"
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
    val unknown =  service.getUnknownByLinkIds(Set(newlinks),newTransaction = false)
      unknown.size should be(1)
    }
  }
  
  test("Remove unknown speed limit when links is removed") {
    val linksid = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      5, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(60), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeRemove(linksid)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linksid), expiredAlso = true)).thenReturn(Seq(oldRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      service.persistUnknown(Seq(UnknownSpeedLimit(linksid,60,Municipality)))
      val unknownBefore = service.getUnknownByLinkIds(Set(linksid))
      unknownBefore .size should be(1)
      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, Seq(change))
      val unknown = service.getUnknownByLinkIds(Set(linksid), newTransaction = false)
      unknown .size should be(0)
    }
  }

  test("Move unknown speed limit into new links") {
    val linksid = generateRandomKmtkId()
    val linkIdVersion1 = s"$linksid:1"
    val linkIdVersion2 = s"$linksid:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink = RoadLink(linkIdVersion1, geometry._1, geometry._2, Municipality,
      5, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(60), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceNewVersion(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkIdVersion1), expiredAlso = true)).thenReturn(Seq(oldRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      service.persistUnknown(Seq(UnknownSpeedLimit(linkIdVersion1, 60, Municipality)))
      val unknownBefore = service.getUnknownByLinkIds(Set(linkIdVersion1))
      unknownBefore.size should be(1)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, Seq(change))
      val unknown = service.getUnknownByLinkIds(Set(linkIdVersion2),newTransaction = false)
      unknown.size should be(1)
    }
  }
  
  test("case 8.1 Road changes to two ways raise links into work list"){
    val linksid = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val geometryNew = generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      60, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
      60, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceLenghenedFromEndBothDirections(linksid)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linksid), expiredAlso = true)).thenReturn(Seq(oldRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, Seq(change))
      val unknown =  service.getUnknownByLinkIds(Set("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"),newTransaction = false)
      unknown .size should be(1)
    }
  }
}
