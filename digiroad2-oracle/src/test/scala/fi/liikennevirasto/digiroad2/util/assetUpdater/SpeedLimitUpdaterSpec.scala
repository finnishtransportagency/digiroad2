package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISSpeedLimitDao}
import fi.liikennevirasto.digiroad2.linearasset.{MTKClassWidth, NewLimit, NumericValue, RoadLink, SpeedLimitValue, UnknownSpeedLimit, Value}
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
  val speedLimitDao = new PostGISLinearAssetDao()
  val mockDynamicLinearAssetDao: DynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val service = new SpeedLimitService(mockEventBus,mockRoadLinkService)
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
    override val speedLimitDao: PostGISSpeedLimitDao = new PostGISSpeedLimitDao(mockRoadLinkService)
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  object TestLinearAssetUpdaterNoRoadLinkMock extends SpeedLimitUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
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
    val linksid = "f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1"
    val newLinks = Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1","c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", "c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1")
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

  /*  service.create(Seq(NewLimit(linksid, 0.0, 10.0), NewLimit(oldLinkId2, 0.0, 10.0),
      NewLimit(oldLinkId2, 0.0, 5.0)), SpeedLimitValue(30), "testuser", 
      (_, _) => Unit
    )*/

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid).get
      val oldRoadLinkRaw = roadLinkService. getExpiredRoadLinkByLinkIdNonEncrished(linksid)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linksid)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = service.createWithoutTransaction(SpeedLimitAsset.typeId, linksid,
        SpeedLimitValue(30), SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)

      //val newAsset = service.getExistingAssetByRoadLinkId("", false).head
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
  
/*test("case 8.2 Road changes to two ways ") {
  val linksid = generateRandomLinkId()
  val geometry = generateGeometry(0, 5)
  val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
    1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  val geometryNew = generateGeometry(0, 10)
  val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  val change = changeReplaceLenghenedFromEndBothDirections(linksid)

  runWithRollback {
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
    val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

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

  val linksid = generateRandomLinkId()
  val geometry = generateGeometry(0, 5)
  val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val geometryNew = generateGeometry(0, 10)
  val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
    1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val change = changeReplaceLenghenedFromEndAgainstDigitizing(linksid)

  runWithRollback {
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
    val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

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

  val linksid = generateRandomLinkId()
  val geometry = generateGeometry(0, 5)
  val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val geometryNew = generateGeometry(0, 10)
  val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
    1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val change = changeReplaceLenghenedFromEndTowardsDigitizing(linksid)

  runWithRollback {
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
    val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

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
  val linksid = generateRandomLinkId()
  val geometry = generateGeometry(0, 5)
  val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val geometryNew = generateGeometry(0, 10)
  val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
    1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val change = changeReplaceLenghenedFromEndAgainstDigitizing(linksid)

  runWithRollback {
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
    val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
    val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

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

test("case 10.2 Road changes to one ways and there more than one asset to both direction ") {
  val linksid = generateRandomLinkId()
  val geometry = generateGeometry(0, 5)
  val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val geometryNew = generateGeometry(0, 10)
  val newLink = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", geometryNew._1, geometryNew._2, Municipality,
    1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
    ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val change = changeReplaceLenghenedFromEndTowardsDigitizing(linksid)

  runWithRollback {
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", false)).thenReturn(Some(newLink))
    val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.AgainstDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
    val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(4), SideCode.TowardsDigitizing.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

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

test("case 11 Roads digitization direction changes") {
  val linksid = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
  val linksidNew = "eca24369-a77b-4e6f-875e-57dc85176003:1"

  val change = roadLinkChangeClient.convertToRoadLinkChange(source)

  runWithRollback {
    val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid).get
    val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.TowardsDigitizing.value, Measures(0, 18.081), "testuser", 0L, Some(oldRoadLink), false, None, None)

    val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
    assetsBefore.size should be(1)
    assetsBefore.head.expired should be(false)

    TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(TrafficVolume.typeId, change)
    val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq(linksidNew), false)

    val sorted = assetsAfter.sortBy(_.endMeasure)

    sorted.head.startMeasure should be(0.001)
    sorted.head.endMeasure should be(18.082)
    sorted.head.value.isEmpty should be(false)
    sorted.head.value.get should be(NumericValue(3))
    SideCode.apply(sorted.head.sideCode) should be(SideCode.AgainstDigitizing)
  }
}*/




/*


test("Should map winter speed limits of old link to three new road links, asset covers part of road link") {
  val oldLinkId = LinkIdGenerator.generateRandom()
  val newLinkId1 = LinkIdGenerator.generateRandom()
  val newLinkId2 = LinkIdGenerator.generateRandom()
  val newLinkId3 = LinkIdGenerator.generateRandom()
  val municipalityCode = 235
  val administrativeClass = Municipality
  val trafficDirection = TrafficDirection.BothDirections
  val functionalClass = 1
  val linkType = Freeway
  val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

  val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

  val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
    RoadLink(newLinkId2, List(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
    RoadLink(newLinkId3, List(Point(20.0, 0.0), Point(25.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

  val change = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
    ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 5, Some(10), Some(20), Some(0), Some(10), 144000000),
    ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 5, Some(20), Some(25), Some(0), Some(5), 144000000))

  runWithRollback {
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(oldLinkId), false)).thenReturn(Seq(oldRoadLink))
    val id = service.createWithoutTransaction(WinterSpeedLimit.typeId, oldRoadLink.linkId, NumericValue(80), 1, Measures(0, 25), "testuser", 0L, Some(oldRoadLink), false)
    val assetsBefore = service.getPersistedAssetsByIds(WinterSpeedLimit.typeId, Set(id), false)
    assetsBefore.size should be(1)
    assetsBefore.foreach(asset => asset.expired should be(false))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(newLinkId1, newLinkId2, newLinkId3), false)).thenReturn(newRoadLinks)
    TestLinearAssetUpdater.updateByRoadLinks(WinterSpeedLimit.typeId, 1, newRoadLinks, change)
    val assetsAfter = service.dao.fetchLinearAssetsByLinkIds(WinterSpeedLimit.typeId, Seq(oldLinkId, newLinkId1, newLinkId2, newLinkId3), "mittarajoitus", true)
    val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
    expiredAssets.size should be(1)
    expiredAssets.head.linkId should be(oldLinkId)
    validAssets.size should be(3)
    validAssets.map(_.linkId).sorted should be(List(newLinkId1, newLinkId2, newLinkId3).sorted)
    val sortedValidAssets = validAssets.sortBy(asset => (asset.startMeasure, asset.endMeasure))
    sortedValidAssets.head.startMeasure should be(0)
    sortedValidAssets.head.endMeasure should be(5)
    sortedValidAssets(1).startMeasure should be(0)
    sortedValidAssets(1).endMeasure should be(10)
    sortedValidAssets.last.startMeasure should be(0)
    sortedValidAssets.last.endMeasure should be(10)
  }
}*/
}
