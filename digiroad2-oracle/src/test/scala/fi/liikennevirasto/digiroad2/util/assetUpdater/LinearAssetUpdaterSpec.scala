package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.AgainstDigitizing
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Add, Remove, Replace, Split}
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, _}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.MTKClassWidth
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetService
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcProfile.capabilities.mutable

import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.util.Random

class LinearAssetUpdaterSpec extends FunSuite with Matchers {

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao()
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val service = new LinearAssetService(mockRoadLinkService, mockEventBus)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestLinearAssetUpdater extends LinearAssetUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}}"

  val (linkId1, linkId2, linkId3) = (generateRandomLinkId(), generateRandomLinkId(), generateRandomLinkId())
  // pseudo geometry 
  val generateGeometry = (startpoint: Double, numberPoint: Long) => {
    val points = new ListBuffer[Point]
    for (i <- 1 to numberPoint.toInt) {
      points.append(Point(i + startpoint, 0))
    }
    (points.toList, GeometryUtils.geometryLength(points))
  }

  val changeAdd = (newID: String) => RoadLinkChange(
    changeType = RoadLinkChangeType.Add,
    oldLink = None,
    newLinks = Seq(RoadLinkInfo(
      linkId = newID,
      linkLength = generateGeometry(0, 10)._2,
      geometry = generateGeometry(0, 10)._1,
      roadClass = MTKClassWidth.CarRoad_Ia.value,
      adminClass = Municipality,
      municipality = 0,
      trafficDirection = TrafficDirection.BothDirections
    )),
    replaceInfo = Seq.empty[ReplaceInfo])

  val changeRemove = (oldRoadLinkId: String) => RoadLinkChange(
    changeType = RoadLinkChangeType.Add,
    oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = generateGeometry(1, 10)._2,
      geometry = generateGeometry(1, 10)._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
      adminClass = Municipality,
      municipality = 0,
      trafficDirection = TrafficDirection.BothDirections)),
    newLinks = Seq.empty[RoadLinkInfo],
    replaceInfo = Seq.empty[ReplaceInfo])

  val changeSplit = (oldRoadLinkId: String) => RoadLinkChange(
    changeType = RoadLinkChangeType.Split,
    oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = generateGeometry(1, 56)._2,
      geometry = generateGeometry(1, 56)._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
      adminClass = Municipality,
      municipality = 0,
      trafficDirection = TrafficDirection.BothDirections)),
    newLinks = Seq(
      RoadLinkInfo(
        linkId = "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1",
        linkLength = generateGeometry(0, 9)._2,
        geometry = generateGeometry(0, 9)._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections
      ),
      RoadLinkInfo(
        linkId = "c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1",
        linkLength = generateGeometry(9, 11)._2,
        geometry = generateGeometry(9, 11)._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections
      ),
      RoadLinkInfo(
        linkId = "753279ca-5a4d-4713-8609-0bd35d6a30fa:1",
        linkLength = generateGeometry(11, 56)._2,
        geometry = generateGeometry(11, 56)._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections
      )),
    replaceInfo =
      List(
        ReplaceInfo(oldRoadLinkId, "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", oldFromMValue = 0.0,oldToMValue = 9,   newFromMValue = 0.0, newToMValue = generateGeometry(0, 9)._2, false), // sort by oldFromMValue
        ReplaceInfo(oldRoadLinkId, "c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1", oldFromMValue = 9,  oldToMValue = 21,  newFromMValue = 0.0, newToMValue = generateGeometry(9, 11)._2, false),
        ReplaceInfo(oldRoadLinkId, "753279ca-5a4d-4713-8609-0bd35d6a30fa:1", oldFromMValue = 21, oldToMValue = 56,  newFromMValue = 0.0, newToMValue = generateGeometry(11, 56)._2, false))
  )

  val changeReplaceNewVersion = (oldRoadLinkId: String) => RoadLinkChange(
    changeType = RoadLinkChangeType.Replace,
    oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = generateGeometry(0, 9)._2,
      geometry = generateGeometry(0, 9)._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
      adminClass = Municipality,
      municipality = 0,
      trafficDirection = TrafficDirection.BothDirections)),
    newLinks = Seq(
      RoadLinkInfo(
        linkId = oldRoadLinkId+":2",
        linkLength = generateGeometry(0, 9)._2,
        geometry = generateGeometry(0, 9)._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.TowardsDigitizing
      )),
    replaceInfo =
      List(
        ReplaceInfo(oldRoadLinkId, oldRoadLinkId+":2", oldFromMValue = 0.0, oldToMValue = 8, newFromMValue = 0.0, newToMValue = generateGeometry(0, 9)._2, false))

  )

  val changeReplaceLenghened = (oldRoadLinkId: String) => RoadLinkChange(
    changeType = RoadLinkChangeType.Replace,
    oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = generateGeometry(0, 5)._2,
      geometry = generateGeometry(0, 5)._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
      adminClass = Municipality,
      municipality = 0,
      trafficDirection = TrafficDirection.BothDirections)),
    newLinks = Seq(
      RoadLinkInfo(
        linkId = "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1",
        linkLength = generateGeometry(0, 10)._2,
        geometry = generateGeometry(0, 10)._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.TowardsDigitizing
      )),
    replaceInfo =
      List(
        ReplaceInfo(oldRoadLinkId, "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 0.0, newToMValue = 9, false))

  )

  val changeReplaceShortened = (oldRoadLinkId: String) => RoadLinkChange(
    changeType = RoadLinkChangeType.Replace,
    oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = generateGeometry(0, 5)._2,
      geometry = generateGeometry(0, 5)._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
      adminClass = Municipality,
      municipality = 0,
      trafficDirection = TrafficDirection.BothDirections)),
    newLinks = Seq(
      RoadLinkInfo(
        linkId = "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1",
        linkLength = generateGeometry(0, 1)._2,
        geometry = generateGeometry(0, 1)._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.TowardsDigitizing
      )),
    replaceInfo =
      List(
        ReplaceInfo(oldRoadLinkId, "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 0.0, newToMValue = generateGeometry(0, 1)._2, false))

  )

  val changeReplaceMerge =() => Seq(RoadLinkChange(
    changeType = RoadLinkChangeType.Replace,
    oldLink = Some(RoadLinkInfo(linkId =  "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", linkLength = generateGeometry(0, 9)._2,
      geometry = generateGeometry(0, 9)._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
      adminClass = Municipality,
      municipality = 0,
      trafficDirection = TrafficDirection.BothDirections)),
    newLinks = Seq(
      RoadLinkInfo(
        linkId = "753279ca-5a4d-4713-8609-0bd35d6a30fa:1",
        linkLength = generateGeometry(0, 9)._2,
        geometry = generateGeometry(0, 9)._1,
        roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.TowardsDigitizing
      )),
    replaceInfo =
      List(
        ReplaceInfo( "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1","753279ca-5a4d-4713-8609-0bd35d6a30fa:1", oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 0.0, newToMValue = 4, false)
      )
  ),
    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId =  "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1", linkLength = generateGeometry(0, 9)._2,
        geometry = generateGeometry(0, 9)._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = "753279ca-5a4d-4713-8609-0bd35d6a30fa:1",
          linkLength = generateGeometry(0, 9)._2,
          geometry = generateGeometry(0, 9)._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.TowardsDigitizing
        )),
      replaceInfo =
        List(ReplaceInfo( "c63d66e9-89fe-4b18-8f5b-f9f2121e3db7:1","753279ca-5a4d-4713-8609-0bd35d6a30fa:1" , oldFromMValue = 0.0, oldToMValue = 4, newFromMValue = 4.0, newToMValue = 10, false))
    )
  
  )


  test("case 1 links under asset is split") {
    val testdata = changeReplaceNewVersion(generateRandomLinkId)
    println(testdata)
  }

  test("case 2 links under asset is merged") {

  }

  test("case 3 links under asset is replaced with longer links") {

  }

  test("case 4 links under asset is replaced with longer shorter") {

  }


  test("case 5 asset retain all it old values") {

  }

  test("case 6 links version is changes, move to new version") {

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
