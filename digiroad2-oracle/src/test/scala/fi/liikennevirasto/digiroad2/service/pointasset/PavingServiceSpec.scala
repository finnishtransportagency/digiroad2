package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, GeometryUtils, Point}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class PavingServiceSpec extends FunSuite with Matchers {
  val PavingAssetTypeId = 110

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = Seq(RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(388562360l, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(388562361l, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(388562362l, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))

  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinkWithLinkSource, Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(roadLinkWithLinkSource)

  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(1), "mittarajoitus"))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None)))

  object ServiceWithDao extends PavingService(mockRoadLinkService, mockEventBus) {
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

  private def createService() = {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
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
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(0))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val attributes3 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
    val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
      functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink2 = newRoadLink1.copy(linkId=newLinkId2, attributes = attributes2)
    val newRoadLink3 = newRoadLink1.copy(linkId=newLinkId3, attributes = attributes3)
    val newRoadLink4 = newRoadLink1.copy(linkId=newLinkId4, attributes = attributes3)
    List(newRoadLink1, newRoadLink2, newRoadLink3, newRoadLink4)
  }

  private def createChangeInfo(roadLinks: Seq[RoadLink], vvhTimeStamp: Long) = {
    roadLinks.map(rl => ChangeInfo(Some(rl.linkId), Some(rl.linkId), 0L, 1, None, None, None, None, vvhTimeStamp))
  }

  test("Should not create new paving assets and return the existing paving assets when VVH doesn't have change information") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    when(mockRoadLinkService.vvhClient).thenReturn(mockVVHClient)
    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(newRoadLink))
    runWithRollback {

      val newAssetId = service.create(Seq(NewLinearAsset(newLinkId, 0, 20, NumericValue(1), 1, 0, None)), PavingAssetTypeId, "testuser")
      val newAsset = service.getPersistedAssetsByIds(PavingAssetTypeId, newAssetId.toSet)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), Nil))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String], any[Boolean])).thenReturn(newAsset)

      val existingAssets = service.getByBoundingBox(PavingAssetTypeId, boundingBox).toList.flatten

      val existingAssetData = existingAssets.filter(p => (p.linkId == newLinkId && p.value.isDefined)).head

      existingAssets.length should be (1)
      existingAssetData.typeId should be (PavingAssetTypeId)
      existingAssetData.vvhTimeStamp should be (0)
      existingAssetData.value should be (Some(NumericValue(1)))
      existingAssetData.id should be (newAsset.head.id)
    }
  }

  test("Should be created only 1 new paving asset when get 3 roadlink change information from vvh and only 1 roadlink have surfacetype equal 2") {

    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
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
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val attributes0 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(0))
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

      val existingAssets = service.getByBoundingBox(PavingAssetTypeId, boundingBox).toList.flatten

      val filteredCreatedAssets = existingAssets.filter(p => p.linkId == newLinkId2 && p.value.isDefined)

      existingAssets.length should be (3)
      filteredCreatedAssets.length should be (1)
      filteredCreatedAssets.head.typeId should be (PavingAssetTypeId)
      filteredCreatedAssets.head.value should be (Some(NumericValue(1)))
      filteredCreatedAssets.head.vvhTimeStamp should be (vvhTimeStamp)
    }
  }

  test("Paving asset changes: new roadlinks") {
    val municipalityCode = 564
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, updated) = service.getPavingAssetChanges(Seq(), roadLinks, changeInfo, PavingAssetTypeId)
    expiredIds should have size (0)
    updated.forall(_.vvhTimeStamp == 11L) should be (true)
    updated.forall(_.value.isDefined) should be (true)
    updated should have size (2)
  }

  test("Paving asset changes: outdated") {
    def createPaving(id: Long, linkId: Long, value: Option[Value], vvhTimeStamp: Long) = {
      PersistedLinearAsset(id, linkId, SideCode.BothDirections.value,
        value, 0.0, 20.0, None, None, None, None, expired = false, PavingAssetTypeId, vvhTimeStamp, None, LinkGeomSource.NormalLinkInterface, None, None)
    }
    val municipalityCode = 564
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val unpaved1 = createPaving(1, newLinkId1, None, 10L)
    val unpaved2 = createPaving(2, newLinkId2, None, 10L)
    val unpaved3 = createPaving(3, newLinkId3, None, 10L)
    val unpaved4 = createPaving(4, newLinkId4, None, 10L)
    val paved1 = createPaving(1, newLinkId1, Some(NumericValue(1)), 10L)
    val paved2 = createPaving(2, newLinkId2, Some(NumericValue(1)), 10L)
    val paved3 = createPaving(3, newLinkId3, Some(NumericValue(1)), 10L)
    val paved4 = createPaving(4, newLinkId4, Some(NumericValue(1)), 10L)

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, updated) = service.getPavingAssetChanges(Seq(unpaved1, unpaved2, unpaved3, unpaved4), roadLinks, changeInfo, PavingAssetTypeId)
    expiredIds should be (Set(2))
    updated.forall(_.vvhTimeStamp == 11L) should be (true)
    //    updated.foreach(println)
    updated.forall(_.value.isDefined) should be (true)
    updated.exists(_.id == 1) should be (false)

    val (expiredIds2, updated2) = service.getPavingAssetChanges(Seq(paved1, paved2, paved3, paved4), roadLinks, changeInfo, PavingAssetTypeId)
    expiredIds2 should be (Set(2))
    updated2.forall(_.vvhTimeStamp == 11L) should be (true)
    updated2.exists(_.id == 1) should be (false)
  }

  test("Paving asset changes: override not affected") {
    def createPaving(id: Long, linkId: Long, value: Option[Value], vvhTimeStamp: Long) = {
      PersistedLinearAsset(id, linkId, SideCode.BothDirections.value,
        value, 0.0, 20.0, None, None, None, None, expired = false, PavingAssetTypeId, vvhTimeStamp, None, LinkGeomSource.NormalLinkInterface, None, None)
    }
    val municipalityCode = 564
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val unpaved1 = createPaving(1, newLinkId1, None, 12L)
    val unpaved2 = createPaving(2, newLinkId2, None, 12L)
    val unpaved3 = createPaving(3, newLinkId3, None, 12L)
    val unpaved4 = createPaving(4, newLinkId4, None, 12L)
    val paved1 = createPaving(1, newLinkId1, Some(NumericValue(1)), 12L)
    val paved2 = createPaving(2, newLinkId2, Some(NumericValue(1)), 12L)
    val paved3 = createPaving(3, newLinkId3, Some(NumericValue(1)), 12L)
    val paved4 = createPaving(4, newLinkId4, Some(NumericValue(1)), 12L)

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, updated) = service.getPavingAssetChanges(Seq(unpaved1, unpaved2, unpaved3, unpaved4), roadLinks, changeInfo, PavingAssetTypeId)
    expiredIds should have size (0)
    updated should have size (0)

    val (expiredIds2, updated2) = service.getPavingAssetChanges(Seq(paved1, paved2, paved3, paved4), roadLinks, changeInfo, PavingAssetTypeId)
    expiredIds should have size (0)
    updated should have size (0)
  }

  test("Paving asset changes: stability test") {
    def createPaving(id: Long, linkId: Long, value: Option[Value], vvhTimeStamp: Long) = {
      PersistedLinearAsset(id, linkId, SideCode.BothDirections.value,
        value, 0.0, 20.0, None, None, None, None, expired = false, PavingAssetTypeId, vvhTimeStamp, None, LinkGeomSource.NormalLinkInterface, None, None)
    }
    val municipalityCode = 564
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val unpaved1 = createPaving(1, newLinkId1, None, 11L)
    val unpaved2 = createPaving(2, newLinkId2, None, 11L)
    val unpaved3 = createPaving(3, newLinkId3, None, 11L)
    val unpaved4 = createPaving(4, newLinkId4, None, 11L)
    val paved1 = createPaving(1, newLinkId1, Some(NumericValue(1)), 11L)
    val paved2 = createPaving(2, newLinkId2, Some(NumericValue(1)), 11L)
    val paved3 = createPaving(3, newLinkId3, Some(NumericValue(1)), 11L)
    val paved4 = createPaving(4, newLinkId4, Some(NumericValue(1)), 11L)

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, updated) = service.getPavingAssetChanges(Seq(unpaved1, unpaved2, unpaved3, unpaved4), roadLinks, changeInfo, PavingAssetTypeId)
    expiredIds should have size (0)
    updated should have size (0)

    val (expiredIds2, updated2) = service.getPavingAssetChanges(Seq(paved1, paved2, paved3, paved4), roadLinks, changeInfo, PavingAssetTypeId)
    expiredIds should have size (0)
    updated should have size (0)
  }

  test("Should create new paving assets from vvh roadlinks infromation through the actor") {
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavingService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val linkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val vvhTimeStamp = 11121

    val newRoadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfoSeq = Seq(
      ChangeInfo(Some(linkId), Some(linkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String], any[Boolean])).thenReturn(List())

      service.getByBoundingBox(PavingAssetTypeId, boundingBox)

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.Matchers.eq("paving:saveProjectedPaving"), captor.capture())

      val linearAssets = captor.getValue

      linearAssets.length should be (1)

      val linearAsset = linearAssets.filter(p => (p.linkId == linkId)).head

      linearAsset.typeId should be (PavingAssetTypeId)
      linearAsset.vvhTimeStamp should be (vvhTimeStamp)

    }
  }

  // Tests for DROTH-4: Paving from VVH

  test("If VVH does not supply a change Information then no new asset should be created.") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), Nil))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String], any[Boolean])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(PavingAssetTypeId, boundingBox).toList.flatten

      val createdAssetData = createdAsset.filter(p => (p.linkId == newLinkId && p.value.isDefined))

      createdAssetData.length should be (0)
    }
  }

  test("Should apply pavement on whole segment") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp(-5)
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 4393233
    val municipalityCode = 444
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val geom = List(Point(428906.372,6693954.166),Point(428867.234,6693997.01),Point(428857.131,6694009.293))
    val len = GeometryUtils.geometryLength(geom)

    val roadLinks = Seq(RoadLink(oldLinkId1, geom, len, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId1), Some(oldLinkId1), 1204467577, 3, Some(0), Some(46.23260977), Some(27.86340569), Some(73.93340102), 1470653580000L),
      ChangeInfo(Some(oldLinkId1), Some(oldLinkId1), 1204467577, 3, None, None, Some(0), Some(27.86340569), 1470653580000L))
    OracleDatabase.withDynTransaction {
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES (1, $oldLinkId1, 0.0, 46.233, ${SideCode.BothDirections.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT 1, 1, id, 1 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute

      val assets = service.getPersistedAssetsByIds(assetTypeId, Set(1L))
      assets should have size(1)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinks, changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox, Set(municipalityCode))
      after should have size(1)

      dynamicSession.rollback()
    }
  }

  test("Should expire the assets if vvh gives change informations and the roadlink surface type is equal to 1") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val vvhTimeStamp = 14440000


    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val changeInfoSeq = Seq(ChangeInfo(Some(newLinkId), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp))

    runWithRollback {

      val newAssetId = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId.toSet)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String], any[Boolean])).thenReturn(List(newAsset.head))

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten.filter(_.value.isDefined)

      createdAsset.length should be (0)
    }
  }

  test("Expire OTH Assets and create new assets based on VVH RoadLink data") {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def vvhClient: VVHClient = mockVVHClient
    }
    val assetTypeId = 110
    val municipalityCode = 564
    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(0))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val attributes3 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))


    //RoadLinks from VVH to compare at the end
    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val newRoadLink2 = VVHRoadlink(newLinkId2, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes2)
    val newRoadLink3 = VVHRoadlink(newLinkId3, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes3)
    val newRoadLink4 = VVHRoadlink(newLinkId4, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes3)


    runWithRollback {
      val newAssetId1 = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId1, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset1 = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId1.toSet)
      val newAssetId2 = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId2, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset2 = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId2.toSet)
      val newAssetId3 = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId3, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset3 = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId3.toSet)
      val newAssetList = List(newAsset1.head, newAsset2.head,newAsset3.head)

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockRoadLinkService.getVVHRoadLinksF(municipalityCode)).thenReturn(List(newRoadLink1, newRoadLink2, newRoadLink3, newRoadLink4))
      when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(12222L)

      service.expireImportRoadLinksVVHtoOTH(assetTypeId)

      val assetListAfterChanges = ServiceWithDao.dao.fetchLinearAssetsByLinkIds(assetTypeId, Seq(newLinkId1, newLinkId2, newLinkId3, newLinkId4), "mittarajoitus")

      assetListAfterChanges.size should be (2)

      //AssetId1 - Expired
      var assetToVerifyNotExist = assetListAfterChanges.find(p => (p.id == newAssetId1(0).toInt) )
      assetToVerifyNotExist.size should be (0)

      //AssetId2 - Expired
      assetToVerifyNotExist = assetListAfterChanges.find(p => (p.id == newAssetId2(0).toInt) )
      assetToVerifyNotExist.size should be (0)

      //AssetId3 - Expired
      assetToVerifyNotExist = assetListAfterChanges.find(p => (p.id == newAssetId3(0).toInt))
      assetToVerifyNotExist.size should be (0)

      //AssetId3 - Update Asset
      val assetToVerifyUpdated = assetListAfterChanges.find(p => (p.id != newAsset3 && p.linkId == newLinkId3)).get
      assetToVerifyUpdated.id should not be (newAssetId3)
      assetToVerifyUpdated.linkId should be (newLinkId3)
      assetToVerifyUpdated.expired should be (false)

      //AssetId4 - Create Asset
      val assetToVerify = assetListAfterChanges.find(p => (p.linkId == newLinkId4)).get
      assetToVerify.linkId should be (newLinkId4)
      assetToVerify.expired should be (false)
    }
  }

  test("should do anything when change information link id doesn't exists on vvh roadlinks"){
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId = 5001
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val vvhTimeStamp = 11121

    val changeInfoSeq = Seq(
      ChangeInfo(Some(newLinkId), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String], any[Boolean])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      createdAsset.length should be (0)

    }
  }

  test ("If neither OTH or VVH have existing assets and changeInfo then nothing should be created and returned") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavingService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), Nil))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String], any[Boolean])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten
      val filteredAssets = createdAsset.filter(p => (p.linkId == newLinkId && p.value.isDefined))

      createdAsset.length should be (1)
      filteredAssets.length should be (0)
    }
  }

  test("Adjust projected asset with creation"){
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp(-5)
    when(mockRoadLinkService.vvhClient).thenReturn(mockVVHClient)
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)

    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val service = new PavingService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId = 6000
    val municipalityCode = 444
    val functionalClass = 1
    val assetTypeId = 110
    val geom = List(Point(0, 0), Point(300, 0))
    val len = GeometryUtils.geometryLength(geom)
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val roadLinks = Seq(RoadLink(oldLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, attributes),
      RoadLink(newLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, attributes)
    )
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId), Some(newLinkId), 1204467577, 1, Some(0), Some(150), Some(100), Some(200), 1461970812000L))


    OracleDatabase.withDynTransaction {
      val (lrm, asset) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES ($lrm, $oldLinkId, 0.0, 150.0, ${SideCode.BothDirections.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset,$assetTypeId, null ,'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset,$lrm)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT $asset, $asset, id, 4779 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute


      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((roadLinks, changeInfo))
      service.getByMunicipality(assetTypeId, municipalityCode)

      verify(mockEventBus, times(1))
        .publish("linearAssets:update", ChangeSet(Set.empty[Long], Nil, Nil, Set.empty[Long]))

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.Matchers.eq("paving:saveProjectedPaving"), captor.capture())
      val projectedAssets = captor.getValue
      projectedAssets.length should be(1)
      projectedAssets.foreach { proj =>
        proj.id should be (0)
        proj.linkId should be (6000)
      }
      dynamicSession.rollback()
    }
  }
}
