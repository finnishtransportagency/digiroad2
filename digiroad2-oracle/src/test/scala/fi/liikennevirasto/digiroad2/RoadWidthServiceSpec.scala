package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, NumericValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mock.MockitoSugar
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class RoadWidthServiceSpec extends FunSuite with Matchers {
  val RoadWidthAssetTypeId = 120

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)

  val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp(5)
  when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)

  val roadLinkWithLinkSource = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  object ServiceWithDao extends RoadWidthService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = assetLock.synchronized {
    TestTransactions.runWithRollback()(test)
  }

  val assetLock = "Used to prevent deadlocks"

  private def createChangeInfo(roadLinks: Seq[RoadLink], vvhTimeStamp: Long) = {
    roadLinks.map(rl => ChangeInfo(Some(rl.linkId), Some(rl.linkId), 0L, 1, None, None, None, None, vvhTimeStamp))
  }

  private def createService() = {
    val service = new RoadWidthService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def vvhClient: VVHClient = mockVVHClient
    }
    service
  }

  private def createRoadLinks(municipalityCode: Int) = {
    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(12112))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(12122))
    val attributes3 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(2))

    val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
    val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
      functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink2 = newRoadLink1.copy(linkId=newLinkId2, attributes = attributes2)
    val newRoadLink3 = newRoadLink1.copy(linkId=newLinkId3, attributes = attributes3)
    val newRoadLink4 = newRoadLink1.copy(linkId=newLinkId4, attributes = attributes3)
    List(newRoadLink1, newRoadLink2, newRoadLink3, newRoadLink4)
  }

  test("Should be created only 1 new road with asset when get 3 roadlink change information from vvh and only 1 roadlink have MTKClass valid") {

    val service = new RoadWidthService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId2 = 5002
    val newLinkId1 = 5001
    val newLinkId0 = 5000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes0 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(12112))
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(100))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(2))
    val vvhTimeStamp = 14440000

    val newRoadLink2 = RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes2)
    val newRoadLink1 = RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink0 = RoadLink(newLinkId0, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes0)

    val changeInfoSeq = Seq(ChangeInfo(Some(newLinkId2), Some(newLinkId2), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp),
      ChangeInfo(Some(newLinkId1), Some(newLinkId1), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp),
      ChangeInfo(Some(newLinkId0), Some(newLinkId0), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink2, newRoadLink1, newRoadLink0), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String], any[Boolean])).thenReturn(List())

      val existingAssets = service.getByBoundingBox(RoadWidthAssetTypeId, boundingBox).toList.flatten

      val filteredCreatedAssets = existingAssets.filter(p => p.linkId == newLinkId0 && p.value.isDefined)

      existingAssets.length should be (3)
      filteredCreatedAssets.length should be (1)
      filteredCreatedAssets.head.typeId should be (RoadWidthAssetTypeId)
      filteredCreatedAssets.head.value should be (Some(NumericValue(1100)))
      filteredCreatedAssets.head.vvhTimeStamp should be (vvhTimeStamp)
    }
  }

  test("Should not created road width asset when exists an asset created by UI (same linkid)") {

    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, 5000, 1, Some(NumericValue(12000)), 0, 5, None, None, None, None, false, RoadWidthAssetTypeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None))

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, newAssets) = service.getRoadWidthAssetChanges(assets, Seq(), roadLinks, changeInfo)
    expiredIds should have size 0
    newAssets.filter(_.linkId == 5000) should have size 0
    newAssets.filter(_.linkId == 5001) should have size 1
    newAssets.filter(_.linkId == 5001).head.value should be (Some(NumericValue(650)))
  }

  test("Should not create any new asset (MTKClass not valid)") {

    val municipalityCode = 235
    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(120))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> BigInt(2))

    val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
    val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
      functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink2 = newRoadLink1.copy(linkId=newLinkId2, attributes = attributes2)
    val roadLinks = List(newRoadLink1, newRoadLink2)
    val service = createService()

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, newAsset) = service.getRoadWidthAssetChanges(Seq(), Seq(), roadLinks, changeInfo)
    expiredIds should have size 0
    newAsset should have size 0
  }

  test("Should not create new road with if the road doesn't have MTKClass attribute") {

    val newLinkId2 = 5001
    val newLinkId1 = 5000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
    val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
      functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink2 = newRoadLink1.copy(linkId=newLinkId2, attributes = attributes2)
    val roadLinks = List(newRoadLink1, newRoadLink2)
    val service = createService()

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, newAsset) = service.getRoadWidthAssetChanges(Seq(), Seq(), roadLinks, changeInfo)
    expiredIds should have size 0
    newAsset should have size 0
  }

  test("Only update road width assets auto generated ") {
    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, 5000, 1, Some(NumericValue(4000)), 0, 20,  Some("vvh_mtkclass_default"), None, None, None, false, RoadWidthAssetTypeId, 10L, None, LinkGeomSource.NormalLinkInterface, None, None),
      PersistedLinearAsset(2, 5001, 1, Some(NumericValue(2000)), 0, 20, None, None, None, None, false, RoadWidthAssetTypeId, 10L, None, LinkGeomSource.NormalLinkInterface, None, None))

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, newAsset) = service.getRoadWidthAssetChanges(assets, Seq(), roadLinks, changeInfo)
    expiredIds should have size 1
    expiredIds should be (Set(1))
    newAsset.forall(_.vvhTimeStamp == 11L) should be (true)
    newAsset.forall(_.value.isDefined) should be (true)
    newAsset should have size 1
    newAsset.head.linkId should be (5000)
    newAsset.head.value should be (Some(NumericValue(1100)))
  }

  test("Do not updated asset created or expired by the user") {
    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, 5000, 1, Some(NumericValue(4000)), 0, 20,  Some("test"), None, None, None, false, RoadWidthAssetTypeId, 10L, None, LinkGeomSource.NormalLinkInterface, None, None))
    val expiredAssets = Seq(PersistedLinearAsset(2, 5001, 1, Some(NumericValue(2000)), 0, 20, Some("test"), None, Some("test2"), None, true, RoadWidthAssetTypeId, 10L, None, LinkGeomSource.NormalLinkInterface, None, None))

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, newAsset) = service.getRoadWidthAssetChanges(assets, expiredAssets, roadLinks, changeInfo)
    expiredIds should have size 0
    newAsset should have size 0
  }

  test("Create linear asset on a road link that has changed previously"){
    val oldLinkId1 = 5000
    val linkId1 = 5001
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val service = createService()

    val roadLinks = Seq(
      RoadLink(linkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))),
      RoadLink(newLinkId, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))

    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(100), Some(0), Some(100), 1476468913000L),
      ChangeInfo(Some(linkId1), Some(newLinkId), 12345, 2, Some(0), Some(20), Some(100), Some(120), 1476468913000L)
    )

    OracleDatabase.withDynTransaction {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinks, changeInfo))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(roadLinks)
      val newAsset1 = NewLinearAsset(linkId1, 0.0, 20, NumericValue(2017), 1, 234567, None)
      val id1 = service.create(Seq(newAsset1), RoadWidthAssetTypeId, "KX2")

      val newAsset = NewLinearAsset(newLinkId, 0.0, 120, NumericValue(4779), 1, 234567, None)
      val id = service.create(Seq(newAsset), RoadWidthAssetTypeId, "KX2")

      id should have size (1)
      id.head should not be (0)

      val assets = service.getPersistedAssetsByIds(RoadWidthAssetTypeId, Set(1L, id.head, id1.head))
      assets should have size (2)
      assets.forall(_.vvhTimeStamp > 0L) should be (true)

      val after = service.getByBoundingBox(RoadWidthAssetTypeId, BoundingRectangle(Point(0.0, 0.0), Point(120.0, 120.0)), Set(municipalityCode))
      after should have size(2)
      after.flatten.forall(_.id != 0) should be (true)
      dynamicSession.rollback()
    }
  }

  test("get unVerified road width assets") {
    val linkId1 = 5001
    val linkId2 = 5002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val service = createService()

    val roadLinks = Seq(
      RoadLink(linkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))),
      RoadLink(linkId2, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))

    OracleDatabase.withDynTransaction {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinks, Nil))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(roadLinks)

      val newAssets1 = service.create(Seq(NewLinearAsset(linkId1, 0.0, 20, NumericValue(2017), 1, 234567, None)), RoadWidthAssetTypeId, "dr1_conversion")
      val newAssets2 = service.create(Seq(NewLinearAsset(linkId2, 40.0, 120, NumericValue(4779), 1, 234567, None)), RoadWidthAssetTypeId, "testuser")

      val unVerifiedAssets = service.getUnverifiedLinearAssets(RoadWidthAssetTypeId)

      unVerifiedAssets.flatMap(_._2).keys.head should be("235")
      unVerifiedAssets.flatMap(_._2).values.head should be(newAssets1)
      unVerifiedAssets.flatMap(_._2).values.head should not be newAssets2
      dynamicSession.rollback()
    }
  }

}

