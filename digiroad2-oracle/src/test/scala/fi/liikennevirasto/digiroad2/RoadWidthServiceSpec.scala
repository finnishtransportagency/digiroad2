package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mock.MockitoSugar

class RoadWidthServiceSpec extends FunSuite with Matchers {
  val RoadWidthAssetTypeId = 120

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  object ServiceWithDao extends RoadWidthService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)

  private def createChangeInfo(roadLinks: Seq[RoadLink], vvhTimeStamp: Long) = {
    roadLinks.map(rl => ChangeInfo(Some(rl.linkId), Some(rl.linkId), 0L, 1, None, None, None, None, vvhTimeStamp))
  }

  private def createService() = {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
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

  test("Should be created only 1 new road with asset when get 3 roadlink change information from vvh and only 1 roadlink have MTKClass allowed") {

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
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(List())

      val existingAssets = service.getByBoundingBox(RoadWidthAssetTypeId, boundingBox).toList.flatten

      val filteredCreatedAssets = existingAssets.filter(p => p.linkId == newLinkId2 && p.value.isDefined)

      existingAssets.length should be (3)
      filteredCreatedAssets.length should be (1)
      filteredCreatedAssets.head.typeId should be (RoadWidthAssetTypeId)
      filteredCreatedAssets.head.value should be (Some(NumericValue(1)))
      filteredCreatedAssets.head.vvhTimeStamp should be (vvhTimeStamp)
    }
  }



  test("Should be created only 1 new road width asset with that last change info") {
    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, 5000, 1, Some(NumericValue(40000)), 0, 20, None, None, None, None, false, RoadWidthAssetTypeId, 0, None, LinkGeomSource.NormalLinkInterface))
    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, newAsset) = service.getRoadWidthAssetChanges(assets, roadLinks, changeInfo)
    expiredIds should have size 0
    newAsset.forall(_.vvhTimeStamp == 11L) should be (true)
    newAsset.forall(_.value.isDefined) should be (true)
    newAsset should have size 1
    newAsset.head.linkId should be (5001)
    newAsset.head.value should be (Some(NumericValue(650)))
  }

  test("Should be created 2 new road width asset with that last change info") {

    val municipalityCode = 235
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assets = Seq(PersistedLinearAsset(1, 5000, 1, Some(NumericValue(12000)), 0, 5, None, None, None, None, false, RoadWidthAssetTypeId, 0, None, LinkGeomSource.NormalLinkInterface),
                     PersistedLinearAsset(1, 5000, 1, Some(NumericValue(15000)), 8, 16, None, None, None, None, false, RoadWidthAssetTypeId, 0, None, LinkGeomSource.NormalLinkInterface))

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, newAssets) = service.getRoadWidthAssetChanges(assets, roadLinks, changeInfo)
    expiredIds should have size (0)
    newAssets.forall(_.vvhTimeStamp == 11L) should be (true)
    newAssets.forall(_.value.isDefined) should be (true)
    val newAsset = newAssets.filterNot(_.linkId == 5000)
    val fillAssets = newAssets.filterNot(_.linkId == 5001)
    newAsset should have size 1
    newAsset.head.startMeasure should be (0)
    newAsset.head.endMeasure should be (20)
    fillAssets should have size 2
    fillAssets.sortBy(_.startMeasure).head.startMeasure should be (5)
    fillAssets.sortBy(_.startMeasure).head.endMeasure should be (8)
    fillAssets.sortBy(_.startMeasure).last.startMeasure should be (16)
    fillAssets.sortBy(_.startMeasure).last.endMeasure should be (20)
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
    val (expiredIds, newAsset) = service.getRoadWidthAssetChanges(Seq(), roadLinks, changeInfo)
    expiredIds should have size 0
    newAsset should have size 0
  }

}

