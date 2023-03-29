package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkInfo, _}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{MTKClassWidth, NumericValue, PersistedLinearAsset, RoadLink, Value}
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

  val roadLinkChangeClient = new RoadLinkChangeClient

  lazy val source = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json").mkString
  
  private def generateRandomKmtkId(): String = s"${UUID.randomUUID()}"
  private def generateRandomLinkId():String = LinkIdGenerator.generateRandom()
  
  // pseudo geometry 
  def generateGeometry(startPoint: Double, numberPoint: Long): (List[Point], Double)= {
    val points = new ListBuffer[Point]
    for (i <- 1 to numberPoint.toInt) {
      points.append(Point(i + startPoint, 0))
    }
    (points.toList, GeometryUtils.geometryLength(points))
  }

  def createAsset(measures: Measures, value: Value, link: RoadLink): Long = {
    service.createWithoutTransaction(TrafficVolume.typeId, link.linkId, value, SideCode.BothDirections.value,
      measures, "testuser", 0L, Some(link), false, None, None)
  }

  def changeAdd (newID: String):RoadLinkChange = {
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

  def changeRemove (oldRoadLinkId: String): RoadLinkChange = {
    val generatedGeometry = generateGeometry(0, 10)
    RoadLinkChange(
      changeType = RoadLinkChangeType.Add,
      oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = generatedGeometry._2,
        geometry = generatedGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq.empty[RoadLinkInfo],
      replaceInfo = Seq.empty[ReplaceInfo])
  }

  def changeSplit (oldRoadLinkId: String): (RoadLinkChange,Set[RoadLink]) = {
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.oldLink.nonEmpty).find(_.oldLink.get.linkId=="f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1")
    
    val newLink1 = roadLinkService.getRoadLinkAndComplementaryByLinkId("753279ca-5a4d-4713-8609-0bd35d6a30fa:1").get
    val newLink2 = roadLinkService.getRoadLinkAndComplementaryByLinkId("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1").get
    val newLink3 = roadLinkService.getRoadLinkAndComplementaryByLinkId("c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1").get
    
    (changes.get,Set(newLink1,newLink2,newLink3))
  }

  def changeVeturiKatu(): (Seq[RoadLinkChange], Set[RoadLink]) = {

    val testLinkIds2 = Seq(
    "b36981f2-9e7e-4ee6-8ac0-9734ef3d390f:1",
    "592a1077-f9c2-4678-b223-0ce5022f5001:1",
    "1e2390ff-0910-4ffe-b1e7-2b281428e855:1", 
      "1d231ff5-1133-4d7d-b688-374ebcdb8f21:1"
    );
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.oldLink.nonEmpty).filter(link=>testLinkIds2.contains(link.oldLink.get.linkId))

    val newLink1 = roadLinkService.getRoadLinkAndComplementaryByLinkId("ff8f3894-6312-4f38-9b51-e68ee919043a:1").get
    val newLink2 = roadLinkService.getRoadLinkAndComplementaryByLinkId("23e5cf5e-c478-48df-bf47-ab92b537c363:2").get
    val newLink3 = roadLinkService.getRoadLinkAndComplementaryByLinkId("619d3fb8-056c-4745-8988-d143c29af659:1").get

    (changes, Set(newLink1, newLink2, newLink3))
  }
  
  def changeReplaceNewVersion (oldRoadLinkId: String,newRoadLikId: String): RoadLinkChange={
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
  def changeReplaceShortenedFromEnd(oldRoadLinkId: String): RoadLinkChange={
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
          linkId =newLinkId1,
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
  // double check this possibility
  def changeReplaceLenghenedFromBegind(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(5, 5), oldRoadLinkId)
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
            oldFromMValue = 0.0, oldToMValue = 9, newFromMValue = 5, newToMValue = 9, digitizationChange = false))
    )
  }
  def changeReplaceShortenedFromBegind(oldRoadLinkId: String): RoadLinkChange = {
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
            oldFromMValue = 0.0, oldToMValue = oldLinkGeometry._2, newFromMValue = 2, newToMValue = newLinkGeometry1._2, false))
    )
  }
  def changeReplaceMerge2(): (Seq[RoadLinkChange],Seq[RoadLink]) ={
    val oldIds = Seq("41a67ca5-886f-44ff-a9ca-92d7d9bea129:1","7ce91531-42e8-424b-a528-b72af5cb1180:1",
      "9d23b85a-d7bf-4c6f-83ff-aa391ff4879f:1","b05075a5-45e1-447e-9813-752ba3e07fe5:1")
    
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.oldLink.nonEmpty)
      .filter(p=>oldIds.contains(p.oldLink.get.linkId))
    
    (changes,  oldIds.map(p=>roadLinkService.getExpiredRoadLinkByLinkId(p).get))
  }

  def changeReplaceMerge(): (Seq[RoadLinkChange], Seq[RoadLink]) = {
    val oldIds = Seq( "d6a67594-6069-4295-a4d3-8495b7f76ef0:1","7c21ce6f-d81b-481f-a67e-14778ad2b59c:1")

    val source: scala.io.BufferedSource = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json")
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source.mkString).filter(_.oldLink.nonEmpty)
      .filter(p => oldIds.contains(p.oldLink.get.linkId))

    (changes, oldIds.map(p => roadLinkService.getExpiredRoadLinkByLinkId(p).get))
  }

  def changeReplaceMergeLongerLink(): Seq[RoadLinkChange] = {
    val (oldLinkGeometry1, oldId1) = (generateGeometry(0, 6), "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")
    val (oldLinkGeometry2, oldId2) = (generateGeometry(6, 6), "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1")
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 15), "753279ca-5a4d-4713-8609-0bd35d6a30fa:1")

    println(s"merged size ${oldLinkGeometry2._2 + oldLinkGeometry1._2}")
    println(s"new link size ${newLinkGeometry1._2}")
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

    val (change,roadLinks) = changeSplit(linksid)
    
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId("f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1").get
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(roadLinks.map(_.linkId), false)).thenReturn(roadLinks.toSeq)
      val id = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.BothDirections.value, Measures(0, 56.06107671), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, change.newLinks.map(_.linkId), false)
      assetsAfter.size should be(3)

      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = oldRoadLink
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        val link = roadLinks.find(_.linkId == p.linkId)
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.get.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9.334)

      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(11.841)

      sorted(2).startMeasure should be(0)
      sorted(2).endMeasure should be(34.906)
      
      assetsAfter.map(v=>v.value.isEmpty should be(false))
      assetsAfter.map(v=>v.value.get should be(NumericValue(3)))
    }
  }

  test("case 2 links under asset is merged") {
    
    runWithRollback {
      val linksid1 = "d6a67594-6069-4295-a4d3-8495b7f76ef0:1"
      val linksid2 = "7c21ce6f-d81b-481f-a67e-14778ad2b59c:1"
      val change = changeReplaceMerge()
      val oldRoadLink = change._2.find(_.linkId  == linksid1).get
      val oldRoadLink2 = change._2.find(_.linkId == linksid2).get
      
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid1, NumericValue(3), SideCode.BothDirections.value, Measures(0, 23.48096698), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid2, NumericValue(3), SideCode.BothDirections.value, Measures(0, 66.25297685), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1,id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)
      
      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change._1)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("524c67d9-b8af-4070-a4a1-52d7aec0526c:1"), false)
      //assetsAfter.size should be(1)

      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = Seq(oldRoadLink,oldRoadLink2).find(_.linkId==p.linkId).get
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        val link = change._1.head.newLinks.find(_.linkId == p.linkId)
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.get.linkLength}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      
      sorted.head.linkId should be(change._1.head.replaceInfo.head.newLinkId)
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
      
      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1"), false)
      assetsAfter.size should be(1)
      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = Seq(oldRoadLink,oldRoadLink2).find(_.linkId == p.linkId).get
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      val sorted = assetsAfter.sortBy(_.endMeasure)
      println("after")
      sorted.foreach(p => {
        val link = change.head.newLinks.head.linkLength
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      sorted.head.linkId should be(change.head.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(14)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }

  test("case 3 links under asset is replaced with longer links") {
    val linksid = generateRandomLinkId()
    val geometry =  generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceLenghenedFromEnd(linksid)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)
      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = Seq(oldRoadLink).find(_.linkId == p.linkId).get
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      val sorted = assetsAfter.sortBy(_.endMeasure)
      println("after")
      sorted.foreach(p => {
        val link = change.newLinks.head.linkLength
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }

  test("case 4 links under asset is replaced with shorter") {
    val linksid = generateRandomLinkId()
    val geometry =  generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceShortenedFromEnd(linksid)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
     
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)

      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = Seq(oldRoadLink).find(_.linkId == p.linkId).get
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      val sorted = assetsAfter.sortBy(_.endMeasure)
      println("after")
      sorted.foreach(p => {
        val link = change.newLinks.head.linkLength
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }
  
  test("case 6 links version is changes, move to new version") {
    val linksid = generateRandomKmtkId()
    val linkIdVersion1 =s"$linksid:1"
    val linkIdVersion2 =s"$linksid:2"
    val geometry =  generateGeometry(0, 9)
    val oldRoadLink = RoadLink(linkIdVersion1, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceNewVersion(linkIdVersion1,linkIdVersion2)
    
    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkIdVersion1, false)).thenReturn(Some(oldRoadLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linkIdVersion1, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
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

  test("Test calculator: link lengthen, asset was half in old") {
    
    println("-----")
    println("link lenghend")
    val newPosition4 = TestLinearAssetUpdater.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 0, endMeasure = 5, sideCode = 2),
      ProjectionForSamuutus(
        oldStart=0, oldEnd= 10, newStart= 0, newEnd= 20), 20)
    
    println(s"new start: ${newPosition4._1}, new end: ${newPosition4._2}, side code: ${newPosition4._3}")

    newPosition4._1 should be(0)
    newPosition4._2 should be(10)
    
  }

  test("Test calculator: link lengthened , asset fill whole link") {
    println("-----")
    val newPosition5 = TestLinearAssetUpdater.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 0, endMeasure = 10, sideCode = 2),
      ProjectionForSamuutus(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)
    println(s"new start: ${newPosition5._1}, new end: ${newPosition5._2}, side code: ${newPosition5._3}")
    newPosition5._1 should be(0)
    newPosition5._2 should be(20)

  }

  test("Test calculator: link lengthened , asset is split from start") {
    println("-----")
    val newPosition5 = TestLinearAssetUpdater.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 5, endMeasure = 10, sideCode = 2),
      ProjectionForSamuutus(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)
    println(s"new start: ${newPosition5._1}, new end: ${newPosition5._2}, side code: ${newPosition5._3}")
    newPosition5._1 should be(10)
    newPosition5._2 should be(20)
  }

  test("Test calculator: link lengthened , asset is split from end") {
    println("-----")
    val newPosition5 = TestLinearAssetUpdater.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 0, endMeasure = 5, sideCode = 2),
      ProjectionForSamuutus(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)
    println(s"new start: ${newPosition5._1}, new end: ${newPosition5._2}, side code: ${newPosition5._3}")
    newPosition5._1 should be(0)
    newPosition5._2 should be(10)
  }

  test("Test calculator: link shortened , asset is bigger than new location") {
    println("-----")
    val newPosition5 = TestLinearAssetUpdater.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 0, endMeasure = 10, sideCode = 2),
      ProjectionForSamuutus(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 5), 5)
    println(s"new start: ${newPosition5._1}, new end: ${newPosition5._2}, side code: ${newPosition5._3}")
    newPosition5._1 should be(0)
    newPosition5._2 should be(5)
  }

  test("Test calculator: asset middle of link, keep relative size") {

    val newPosition2 = TestLinearAssetUpdater.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 2, endMeasure = 4, sideCode = 2),
      ProjectionForSamuutus(
        oldStart=0, oldEnd= 10, newStart= 0, newEnd= 20), 20)

    println(s"new start: ${newPosition2._1}, new end: ${newPosition2._2}, side code: ${newPosition2._3}")

    newPosition2._1 should be(4)
    newPosition2._2 should be(8)
    
  }

  test("Test calculator: link split from begin,  asset cover link only partially") {
    val newPosition3 = TestLinearAssetUpdater.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 2, endMeasure = 10, sideCode = 2),
      ProjectionForSamuutus(
        oldStart=0, oldEnd= 10, newStart= 0, newEnd= 20), 20)
    println(s"new start: ${newPosition3._1}, new end: ${newPosition3._2}, side code: ${newPosition3._3}")

    newPosition3._1 should be(4)
    newPosition3._2 should be(20)
    
  }
  
  // TODO adjust length to cover whole link
  test("case 7, asset cover link only partially, from end") {
    // recognize that asset is not whole links.
    fail("Need to be implemented")
  }
  test("case 7, asset cover link only partially, from begin") {
    fail("Need to be implemented")
  }

  test("case 8, link is removed") {
    fail("Need to be implemented")
  }
  // TODO not adjustment just move to new position
  test("case 9, asset is in middle of link ") {
    // is this possible scenarios ? 
    // is possible, how realistic is different thing
    fail("Need to be implemented")
  }
  
  test("case 7, asset is split into multiple part, link split") {
    val linksid = "f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1"
    
    val (change,roadLinks)  = changeSplit(linksid)
    
    val oldMaxLength = 56.06107671
    
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId("f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1").get
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(roadLinks.filter(_.linkId !="c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1").map(_.linkId), false)).thenReturn(roadLinks.filter(_.linkId !="c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1").toSeq)
      val id1 = createAsset(Measures(0,20 ),NumericValue(3),oldRoadLink) // 36,36%
      val id2 = createAsset(Measures(20,40 ),NumericValue(4),oldRoadLink) // 36,36%
      val id3 = createAsset(Measures(40,oldMaxLength ),NumericValue(5),oldRoadLink) // 18,36%
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1,id2,id3), false)
      assetsBefore.size should be(3)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, change.newLinks.map(_.linkId), false)
      
      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = oldRoadLink
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        val link = roadLinks.find(_.linkId==p.linkId)
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.get.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      sorted.size should be(4)
      sorted.map(v => v.value.isEmpty should be(false))
      val asset1 = sorted.head
      val asset2 = sorted(1)
      val asset3 = sorted(2)
      val asset4 = sorted(3)
      
      println(s"testing: ${asset1.id}")
      asset1.startMeasure should be(0)
      asset1.endMeasure should be(9.334)
      asset1.value.get should be(NumericValue(3))
      println(s"testing: ${asset2.id}")
      asset2.startMeasure should be(0)
      asset2.endMeasure should be(11.841212723230687)
      asset2.value.get should be(NumericValue(3))

      println(s"testing: ${asset3.id}")
      asset3.startMeasure should be(0)
      asset3.endMeasure should be(18.842)
      asset3.value.get should be(NumericValue(4))
      
      println(s"testing: ${asset4.id}")
      asset4.startMeasure should be(18.842)
      asset4.endMeasure should be(34.906)
      asset4.value.get should be(NumericValue(5))
      
    }
  }

  test("case 7, asset is split into multiple part, link merged") {
    // extension happen in wrong place
    runWithRollback {
      val linksid1 = "d6a67594-6069-4295-a4d3-8495b7f76ef0:1"
      val linksid2 = "7c21ce6f-d81b-481f-a67e-14778ad2b59c:1"
      val change = changeReplaceMerge()
      val oldRoadLink = change._2.find(_.linkId == linksid1).get
      val oldRoadLink2 = change._2.find(_.linkId == linksid2).get
      // when getting links from database it give more precise measure than smallChangSet
      val oldMaxLength = 23.48096698
      val oldMaxLength2 = 66.25297685
      val newMaxLength = change._1.head.newLinks.head.linkLength
      
      
      val id1 = createAsset(Measures(0, 10), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(10, oldMaxLength), NumericValue(4), oldRoadLink)
      val id3 = createAsset(Measures(0, 30), NumericValue(5), oldRoadLink2)
      val id4 = createAsset(Measures(30, oldMaxLength2), NumericValue(6), oldRoadLink2)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3,id4), false)
      
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)
// validate result
      // code return asset which have startMvalue 4 and end mvalue 10
      // old implementaton lenthent only from end not in begind
      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change._1)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("524c67d9-b8af-4070-a4a1-52d7aec0526c:1"), false)
      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = Seq(oldRoadLink,oldRoadLink2).find(_.linkId==p.linkId).get
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        //val link = roadLinks.find(_.linkId == p.linkId)
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${newMaxLength}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      sorted.size should be(4)
      sorted.map(v=>v.value.isEmpty should be(false))
      val asset1 = sorted.find(_.id == id1).get
      val asset2 = sorted.find(_.id == id2).get
      val asset3 = sorted.find(_.id == id3).get
      val asset4 = sorted.find(_.id == id4).get

      // from link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      println(s"testing: ${asset1.id}")
      asset1.startMeasure should be(0)
      asset1.endMeasure should be(9.998)
      asset1.value.get should be(NumericValue(3))

      println(s"testing: ${asset2.id}")
      asset2.startMeasure should be(9.998)
      asset2.endMeasure should be(23.476)
      asset2.value.get should be(NumericValue(4))

      println(s"testing: ${asset3.id}")
      // from link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      asset3.startMeasure should be(23.476)
      asset3.endMeasure should be(53.476)
      asset3.value.get should be(NumericValue(5))

      println(s"testing: ${asset4.id}")
      asset4.startMeasure should be(53.476)
      asset4.endMeasure should be(89.728)
      asset4.value.get should be(NumericValue(6))
      
    }
  }


  test("case 7, asset is split into multiple part, link merged, use Veturikatu") {
    runWithRollback {
      val linksid1 = "1e2390ff-0910-4ffe-b1e7-2b281428e855:1"
      val linksid2 = "b36981f2-9e7e-4ee6-8ac0-9734ef3d390f:1"
      val linksid3 = "1d231ff5-1133-4d7d-b688-374ebcdb8f21:1"
      val change = changeVeturiKatu()

      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(linksid2).get
      val oldRoadLink3 = roadLinkService.getExpiredRoadLinkByLinkId(linksid3).get
      val oldMaxLength2 = 16.568
      val oldMaxLength3 = 202.283
      val newMaxLength = 223.87217891 // ff8f3894-6312-4f38-9b51-e68ee919043a:1
      
      val id1 = createAsset(Measures(0, 8), NumericValue(5), oldRoadLink2)
      val id2 = createAsset(Measures(8, oldMaxLength2), NumericValue(6), oldRoadLink2)
      val id3 = createAsset(Measures(0, 100), NumericValue(7), oldRoadLink3)
      val id4 = createAsset(Measures(100, oldMaxLength3), NumericValue(8), oldRoadLink3)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2,id3,id4), false)

      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid3, false)).thenReturn(Some(oldRoadLink3))
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)
      
      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change._1)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("ff8f3894-6312-4f38-9b51-e68ee919043a:1"), false)
      println("before")
      assetsBefore.sortBy(_.linkId).foreach(p => {
        val link = Seq(oldRoadLink, oldRoadLink2,oldRoadLink3).find(_.linkId == p.linkId).get
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        //val link = roadLinks.find(_.linkId == p.linkId)
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${newMaxLength}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      sorted.size should be(4)
      sorted.map(v => v.value.isEmpty should be(false))
      
      println(s"testing: ${sorted(0).id}")
      // from link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      sorted(0).startMeasure should be(0)
      sorted(0).endMeasure should be(102.482)
      sorted(0).value.get should be(NumericValue(7))

      println(s"testing: ${sorted(1).id}")
      sorted(1).startMeasure should be(102.482)
      sorted(1).endMeasure should be(207.304)
      sorted(1).value.get should be(NumericValue(8))
      
      // add logic to select two concetive link and snap into next if whole is smaller than x
      println(s"testing: ${sorted(2).id}")
      sorted(2).startMeasure should be(207.304)
      sorted(2).endMeasure should be(215.304) 
      sorted(2).value.get should be(NumericValue(5))

      println(s"testing: ${sorted(3).id}")
      sorted(3).startMeasure should be(215.304)
      sorted(3).endMeasure should be(223.872)
      sorted(3).value.get should be(NumericValue(6))

    }
  }


  test("case 7, asset is split into multiple part, link merged, use Veturikatu, link position is moved into different place") {
    runWithRollback {
      val linksid1 = "1e2390ff-0910-4ffe-b1e7-2b281428e855:1"
      val linksid2 = "b36981f2-9e7e-4ee6-8ac0-9734ef3d390f:1"
      val linksid3 = "1d231ff5-1133-4d7d-b688-374ebcdb8f21:1"
      val change = changeVeturiKatu()

      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(linksid2).get
      val oldRoadLink3 = roadLinkService.getExpiredRoadLinkByLinkId(linksid3).get
      val oldMaxLength = 11.715
      val newMaxLength = 223.87217891 // ff8f3894-6312-4f38-9b51-e68ee919043a:1

      val id1 = createAsset(Measures(0, 5), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(5, oldMaxLength), NumericValue(4), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)

      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid3, false)).thenReturn(Some(oldRoadLink3))
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change._1)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("619d3fb8-056c-4745-8988-d143c29af659:1"), false)
      println("before")
      assetsBefore.sortBy(_.linkId).foreach(p => {
        val link = Seq(oldRoadLink, oldRoadLink2, oldRoadLink3).find(_.linkId == p.linkId).get
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${newMaxLength}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      sorted.size should be(2)
      sorted.map(v => v.value.isEmpty should be(false))
            val asset1 = sorted.find(_.id == id1).get
            val asset2 = sorted.find(_.id == id2).get
      
            println(s"testing: ${asset1.id}")
            asset1.startMeasure should be(101.42)
            asset1.endMeasure should be(103.841)
            asset1.value.get should be(NumericValue(3))
      
            println(s"testing: ${asset2.id}")
            asset2.startMeasure should be(103.841)
            asset2.endMeasure should be(107.093)
            asset2.value.get should be(NumericValue(4))
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
    val newMaxLength = change.head.newLinks.head.linkLength

    // extension happen in wrong place
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

      // code return asset which have startMvalue 4 and end mvalue 10
      // old implementaton lenthent only from end not in begind
      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1"), false)

      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = oldRoadLink
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${newMaxLength}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      sorted.size should be(4)
      sorted.map(v => v.value.isEmpty should be(false))
      val asset1 = sorted.find(_.id == id1).get
      val asset2 = sorted.find(_.id == id2).get
      val asset3 = sorted.find(_.id == id3).get
      val asset4 = sorted.find(_.id == id4).get

      // from link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      println(s"testing: ${asset1.id}")
      asset1.startMeasure should be(0)
      asset1.endMeasure should be(3)
      asset1.value.get should be(NumericValue(3))

      println(s"testing: ${asset2.id}")
      asset2.startMeasure should be(3)
      asset2.endMeasure should be(5)
      asset2.value.get should be(NumericValue(4)) // between asset 2 and asset 3 we have whole

      println(s"testing: ${asset3.id}")
      // from link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      asset3.startMeasure should be(5)
      asset3.endMeasure should be(10.4)
      asset3.value.get should be(NumericValue(5))

      println(s"testing: ${asset4.id}")
      asset4.startMeasure should be(10.4)
      asset4.endMeasure should be(14.0)
      asset4.value.get should be(NumericValue(6))
    }
  }

  test("case 7, asset is split into multiple part, link is longer") {
    val linksid = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceLenghenedFromEnd(linksid)

    val oldMaxLength = geometry._2
    val newMaxLength =change.newLinks.head.linkLength

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      val id1 = createAsset(Measures(0, 2), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(2, oldMaxLength), NumericValue(5), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)

      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))

      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)


      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = oldRoadLink
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${newMaxLength}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      
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
    val linksid = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceShortenedFromEnd(linksid)
    val oldMaxLength = geometry._2
    val newMaxLength = change.newLinks.head.linkLength
    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      val id1 = createAsset(Measures(0, 6), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(6, oldMaxLength), NumericValue(5), oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)

      println("before")
      assetsBefore.sortBy(_.endMeasure).foreach(p => {
        val link = oldRoadLink
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${link.length}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      println("after")
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.foreach(p => {
        println(s"id: ${p.id}, value: ${p.value.get} , linkId: ${p.linkId}, link measure: ${newMaxLength}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }


  test("case 5 asset retain all it old values, group 1 (position)") {
    fail("Need to be implemented")
  }

  test("case 5 asset retain all it old values,group 2, (position,value, different value types)") {
    fail("Need to be implemented")
  }

  test("case 5 asset retain all it old values,group 3, (position,value, side code)") {
    fail("Need to be implemented")
  }

  test("case 5 asset retain all it old values,group 4, (position,value, side code, validity period)") {
    fail("Need to be implemented")
  }

  test("case 5 asset retain all it old values,group 4, (position,value, side code, validity period, exception)") {
    fail("Need to be implemented")
  }

  test("case 5 asset retain all it old values,group 5, (external id)") {
    fail("Need to be implemented")
  }


  /*test("Case 1 Asset on a removed road link should be expired") {
    val oldRoadLinkId = LinkIdGenerator.generateRandom()
    val oldRoadLink = RoadLink(
      oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = RoadLinkChange(
      changeType = RoadLinkChangeType.Remove, 
      oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = 10L, geometry = Nil, roadClass = 0, adminClass = Municipality, municipality = 0, trafficDirection = TrafficDirection.BothDirections)) , 
      newLinks = Seq[RoadLinkInfo],
      replaceInfo = Nil)
    
    
    
    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val id = service.createWithoutTransaction(NumberOfLanes.typeId, oldRoadLinkId, NumericValue(3), 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(NumberOfLanes.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)
      
      TestLinearAssetUpdater.updateByRoadLinks(NumberOfLanes.typeId, 1, Seq(), Seq(change))
      val assetsAfter = service.getPersistedAssetsByIds(NumberOfLanes.typeId, Set(id), false)
      assetsAfter.size should be(1)
      assetsAfter.head.expired should be(true)
    }
  }

  test("Case 2 Should map asset of two old links to one new link, merge") {
    val oldLinkId1 = LinkIdGenerator.generateRandom()
    val oldLinkId2 = LinkIdGenerator.generateRandom()
    val newLinkId = LinkIdGenerator.generateRandom()
    val municipalityCode = 1
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink1 = RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val oldRoadLink2 = RoadLink(oldLinkId2, List(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val oldRoadLinks = Seq(oldRoadLink1, oldRoadLink2)
    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val change = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(10), Some(20), Some(10), Some(20), 144000000))
      
    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(oldLinkId1, oldLinkId2), false)).thenReturn(oldRoadLinks)
      val id1 = service.createWithoutTransaction(WinterSpeedLimit.typeId, oldLinkId1, NumericValue(80), 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink1), false)
      val id2 = service.createWithoutTransaction(WinterSpeedLimit.typeId, oldLinkId2, NumericValue(80), 1, Measures(10, 20), "testuser", 0L, Some(oldRoadLink1), false)
      val assetsBefore = service.getPersistedAssetsByIds(WinterSpeedLimit.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.foreach(asset => asset.expired should be(false))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(newLinkId), false)).thenReturn(Seq(newRoadLink))
      TestLinearAssetUpdater.updateByRoadLinks(WinterSpeedLimit.typeId, 1, Seq(newRoadLink), change)
      val assetsAfter = service.dao.fetchLinearAssetsByLinkIds(WinterSpeedLimit.typeId, Seq(oldLinkId1, oldLinkId2, newLinkId), "mittarajoitus", true)
      val (expiredAssets, validAssets) = assetsAfter.partition(_.expired)
      expiredAssets.size should be(2)
      val expiredLinkIds = expiredAssets.map(_.linkId)
      expiredLinkIds should contain(oldLinkId1)
      expiredLinkIds should contain(oldLinkId2)
      validAssets.size should be(2)
      validAssets.map(_.linkId) should be(List(newLinkId, newLinkId))
      validAssets.foreach(asset => asset.value.get should be(NumericValue(80)))
      val sortedValidAssets = validAssets.sortBy(_.startMeasure)
      sortedValidAssets.head.startMeasure should be(0)
      sortedValidAssets.head.endMeasure should be(10)
      sortedValidAssets.last.startMeasure should be(10)
      sortedValidAssets.last.endMeasure should be(20)
    }
  }



  test("case 6 Lengthene asset") {
    val oldRoadLinkId = LinkIdGenerator.generateRandom()
    val oldRoadLink = RoadLink(
      oldRoadLinkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(oldRoadLinkId), false)).thenReturn(Seq(oldRoadLink))
      val id = service.createWithoutTransaction(WinterSpeedLimit.typeId, oldRoadLink.linkId, NumericValue(80), 1, Measures(0, 10), "testuser", 0L, Some(oldRoadLink), false)
      val newLinkId = LinkIdGenerator.generateRandom()
      val newRoadLink = oldRoadLink.copy(linkId = newLinkId, geometry = Seq(Point(0.0, 0.0), Point(15, 0.0)), length = 15)
      val change = ChangeInfo(Some(oldRoadLinkId), Some(newLinkId), 123L, 2, Some(0), Some(10), Some(0), Some(15), 99L)
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(newLinkId), false)).thenReturn(Seq(newRoadLink))
      TestLinearAssetUpdater.updateByRoadLinks(WinterSpeedLimit.typeId, 1, Seq(newRoadLink), Seq(change))
      val assets = service.dao.fetchLinearAssetsByLinkIds(WinterSpeedLimit.typeId, Seq(oldRoadLinkId, newLinkId), "mittarajoitus", true)
      val (expiredAssets, validAssets) = assets.partition(_.expired)
      expiredAssets.size should be(1)
      expiredAssets.head.linkId should be(oldRoadLinkId)
      expiredAssets.head.endMeasure should be(10.0)
      validAssets.size should be(1)
      validAssets.head.linkId should be(newLinkId)
      validAssets.head.endMeasure should be(15.0)
    }
  }

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
