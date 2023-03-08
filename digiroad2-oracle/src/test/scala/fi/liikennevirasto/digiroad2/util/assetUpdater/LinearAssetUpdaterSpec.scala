package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkInfo, _}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{MTKClassWidth, NumericValue, RoadLink, Value}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
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

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestLinearAssetUpdater extends LinearAssetUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

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

  def changeSplit (oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry,oldId)       =   (generateGeometry(0, 56),oldRoadLinkId)
    val (newLinkGeometry1,newLinkId1) =   (generateGeometry(0, 9),"c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")
    val (newLinkGeometry2,newLinkId2) =   (generateGeometry(9, 11),"c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1")
    val (newLinkGeometry3,newLinkId3) =   (generateGeometry(11, 56),"753279ca-5a4d-4713-8609-0bd35d6a30fa:1")
    RoadLinkChange(
      changeType = RoadLinkChangeType.Split,
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
        ),
        RoadLinkInfo(
          linkId = newLinkId2,
          linkLength = newLinkGeometry2._2,
          geometry =newLinkGeometry2._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        ),
        RoadLinkInfo(
          linkId = newLinkId3,
          linkLength = newLinkGeometry3._2,
          geometry = newLinkGeometry3._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(oldId, newLinkId1,
            oldFromMValue = 0.0, oldToMValue = 9, newFromMValue = 0.0, newToMValue = newLinkGeometry1._2, false), // sort by oldFromMValue
          ReplaceInfo(oldId, newLinkId2,
            oldFromMValue = 9, oldToMValue = 21, newFromMValue = 0.0, newToMValue = newLinkGeometry2._2, false),
          ReplaceInfo(oldId, newLinkId3,
            oldFromMValue = 21, oldToMValue = 56, newFromMValue = 0.0, newToMValue = newLinkGeometry3._2, false))
    )
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
  def changeReplaceMerge(): Seq[RoadLinkChange] ={
    val (oldLinkGeometry1, oldId1) = (generateGeometry(0, 6), "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1")
    val (oldLinkGeometry2, oldId2) = (generateGeometry(6, 6), "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1")
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 11), "753279ca-5a4d-4713-8609-0bd35d6a30fa:1")
    
    println(s"merged size ${oldLinkGeometry2._2+oldLinkGeometry1._2}")
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
          ReplaceInfo(oldId1,newLinkId1,
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
    val linksid = generateRandomLinkId()
    val geometry = generateGeometry(0, 56)
    val oldRoadLink = RoadLink( linksid, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink1 = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", generateGeometry(0, 9)._1, generateGeometry(0, 9)._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink2 = RoadLink("c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1", generateGeometry(9, 11)._1, generateGeometry(9, 11)._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink3 = RoadLink("753279ca-5a4d-4713-8609-0bd35d6a30fa:1", generateGeometry(11, 56)._1, generateGeometry(11, 56)._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeSplit(linksid)
    
    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(newLink1,newLink2,newLink3).map(_.linkId), false)).thenReturn(Seq(newLink1,newLink2,newLink3))
      val id = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, change.newLinks.map(_.linkId), false)
      assetsAfter.size should be(3)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(8)

      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(10)

      sorted(2).startMeasure should be(0)
      sorted(2).endMeasure should be(55)
      assetsAfter.map(v=>v.value.isEmpty should be(false))
      assetsAfter.map(v=>v.value.get should be(NumericValue(3)))
    }
  }

  test("case 2 links under asset is merged") {
    val linksid1 = "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
    val linksid2 = "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1"
    val linkGeometry1 = generateGeometry(0, 6)
    val linkGeometry2 = generateGeometry(6, 6)
    
    val oldRoadLink = RoadLink(linksid1,linkGeometry1._1 , linkGeometry1._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val oldRoadLink2 = RoadLink(linksid2, linkGeometry2._1, linkGeometry2._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceMerge()
    // extension happen in wrong place
    runWithRollback {
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid1, NumericValue(3), SideCode.BothDirections.value, Measures(0, linkGeometry1._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid2, NumericValue(3), SideCode.BothDirections.value, Measures(0, linkGeometry2._2), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1,id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)
      
      // code return asset which have startMvalue 4 and end mvalue 10
      // old implementaton lenthent only from end not in begind
      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1"), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.linkId should be(change.head.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0) 
      sorted.head.endMeasure should be(10)
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
    // extension happen in wrong place
    runWithRollback {
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid1, NumericValue(3), SideCode.BothDirections.value, Measures(0, linkGeometry1._2), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = service.createWithoutTransaction(TrafficVolume.typeId, linksid2, NumericValue(3), SideCode.BothDirections.value, Measures(0, linkGeometry2._2), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      // code return asset which have startMvalue 4 and end mvalue 10
      // old implementaton lenthent only from end not in begind
      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1"), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.startMeasure)
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
      val sorted = assetsAfter.sortBy(_.startMeasure)
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
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))
    }
  }


  test("case 3.2 links under asset is replaced with longer links, change from begin") {
    val linksid = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceLenghenedFromBegind(linksid)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
     /* val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))*/
    }
    fail("Need to be implemented")
  }

  test("case 4.2 links under asset is replaced with shorter, change from begin") {
    val linksid = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeReplaceShortenedFromBegind(linksid)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      val id1 = service.createWithoutTransaction(TrafficVolume.typeId, linksid, NumericValue(3), SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
      /*val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))*/
    }
    fail("Need to be implemented")
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
    
    println()
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
  test("changeSetTest") {
    val linksid = generateRandomKmtkId()
    val linkIdVersion1 = linksid + ":1"
    val linkIdVersion2 = linksid + ":2"
    
    val changes = Seq(changeReplaceNewVersion(linkIdVersion1, linkIdVersion2),
      changeReplaceShortenedFromEnd(generateRandomLinkId()),
      changeReplaceLenghenedFromEnd(generateRandomLinkId()),
      changeSplit(generateRandomLinkId())) ++ changeReplaceMerge()
    fail("debug test")
  }

  test("test calculator") {
    val asset = AssetLinearReference(id = 1, startMeasure = 5, endMeasure = 10, sideCode = 2)
    val projection = Projection(oldStart = 0, oldEnd = 5, newStart = 5, newEnd = 10, timeStamp = 0)
    val newPosition = TestLinearAssetUpdater.calculateNewMValuesAndSideCode(asset,projection,10)
    
    println(newPosition.toString())
    fail("debug test")

  }

  test("test calculator splitted") {
    val asset = AssetLinearReference(id = 1, startMeasure = 3, endMeasure = 5, sideCode = 2)
    val projection = Projection(oldStart = 0, oldEnd = 5, newStart = 0, newEnd = 10, timeStamp = 0)
    val newPosition = TestLinearAssetUpdater.calculateNewMValuesAndSideCode(asset, projection, 10)

    println(newPosition.toString())
    fail("debug test")

  }
  
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
  test("case 9, asset is in middle of link ") {
    // is this possible scenarios ? 
    // is possible, how realistic is different thing
    fail("Need to be implemented")
  }

  // TODO what about situation where link is split or merge and replacing one is shorter or longer
  test("case 7, asset is split into multiple part, link split") {
    val linksid = generateRandomLinkId()
    val geometry = generateGeometry(0, 56)
    val oldRoadLink = RoadLink(linksid, geometry._1, geometry._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink1 = RoadLink("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", generateGeometry(0, 9)._1, generateGeometry(0, 9)._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink2 = RoadLink("c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1", generateGeometry(9, 11)._1, generateGeometry(9, 11)._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink3 = RoadLink("753279ca-5a4d-4713-8609-0bd35d6a30fa:1", generateGeometry(11, 56)._1, generateGeometry(11, 56)._2, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val change = changeSplit(linksid)
    
    val oldMaxLength = geometry._2
    
    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(newLink1, newLink2, newLink3).map(_.linkId), false)).thenReturn(Seq(newLink1, newLink2, newLink3))
      val id1 = createAsset(Measures(0,20 ),NumericValue(3),oldRoadLink)
      val id2 = createAsset(Measures(20,40 ),NumericValue(4),oldRoadLink)
      val id3 = createAsset(Measures(40,oldMaxLength ),NumericValue(5),oldRoadLink)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1,id2,id3), false)
      assetsBefore.size should be(3)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, Seq(change))
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, change.newLinks.map(_.linkId), false)
      assetsAfter.size should be(3)
      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.foreach(p => {
        println(s"id: ${p.id} , value: ${p.value.get} , startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      sorted.size should be(4)
      sorted.map(v => v.value.isEmpty should be(false))
      val asset1 = sorted.find(_.id == id1).get
      val asset2 = sorted.find(_.id == id2).get
      val asset3 = sorted.find(_.id == id3).get

      // to link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      println(s"testing: ${asset1.id}")
      asset1.startMeasure should be(0)
      asset1.endMeasure should be(3)
      asset1.value.get should be(NumericValue(3))

      println(s"testing: ${asset2.id}")
      asset2.startMeasure should be(3)
      asset2.endMeasure should be(5)
      asset2.value.get should be(NumericValue(4))

      println(s"testing: ${asset3.id}")
      // to link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      asset3.startMeasure should be(5)
      asset3.endMeasure should be(8)
      asset3.value.get should be(NumericValue(5))
    }
    
    fail("Need to be implemented")
  }

  test("case 7, asset is split into multiple part, link merged") {
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
    val change = changeReplaceMerge()

    val oldMaxLength = linkGeometry1._2
    val oldMaxLength2 = linkGeometry2._2
    val newMaxLength = change.head.newLinks.head.linkLength
    
    // extension happen in wrong place
    runWithRollback {
      val id1 = createAsset(Measures(0, 3), NumericValue(3), oldRoadLink)
      val id2 = createAsset(Measures(3, oldMaxLength), NumericValue(4), oldRoadLink)
      val id3 = createAsset(Measures(0, 3), NumericValue(5), oldRoadLink2)
      val id4 = createAsset(Measures(3, oldMaxLength2), NumericValue(6), oldRoadLink2)
      val assetsBefore = service.getPersistedAssetsByIds(TrafficVolume.typeId, Set(id1, id2, id3,id4), false)
      
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      // code return asset which have startMvalue 4 and end mvalue 10
      // old implementaton lenthent only from end not in begind
      TestLinearAssetUpdater.updateByRoadLinks2(TrafficVolume.typeId, change)
      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1"), false)
      val sorted = assetsAfter.sortBy(_.startMeasure)

      sorted.foreach(p=>{
        println(s"id: ${p.id} , value: ${p.value.get} , startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      println("dummy")

      sorted.size should be(4)
      sorted.map(v=>v.value.isEmpty should be(false))
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
      asset2.value.get should be(NumericValue(4))

      println(s"testing: ${asset3.id}")
      // from link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      asset3.startMeasure should be(5)
      asset3.endMeasure should be(8)
      asset3.value.get should be(NumericValue(5))

      println(s"testing: ${asset4.id}")
      asset4.startMeasure should be(8)
      asset4.endMeasure should be(10)
      asset4.value.get should be(NumericValue(6))
      
    }
    //fail("Need to be implemented")
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
      val sorted = assetsAfter.sortBy(_.startMeasure)

      sorted.foreach(p => {
        println(s"id: ${p.id} , value: ${p.value.get} , startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })
      println("dummy")

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
      asset2.value.get should be(NumericValue(4))

      println(s"testing: ${asset3.id}")
      // from link "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"
      asset3.startMeasure should be(5)
      asset3.endMeasure should be(8) // here we extend two last part, need should we be doing it
      asset3.value.get should be(NumericValue(5))

      println(s"testing: ${asset4.id}")
      asset4.startMeasure should be(8)
      asset4.endMeasure should be(14)
      asset4.value.get should be(NumericValue(6))

    }
    //fail("Need to be implemented")
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
/*      val assetsAfter = service.getPersistedAssetsByLinkIds(TrafficVolume.typeId, Seq("c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1"), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))*/
    }
    
    fail("Need to be implemented")
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
  /*    val assetsAfter = service.getPersistedAssetsByIds(TrafficVolume.typeId, Seq(id1).toSet, false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.linkId should be(change.replaceInfo.head.newLinkId)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(4)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get should be(NumericValue(3))*/
    }
    
    fail("Need to be implemented")
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
