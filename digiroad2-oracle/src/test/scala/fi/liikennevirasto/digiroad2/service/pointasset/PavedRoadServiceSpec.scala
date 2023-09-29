package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, Sequences}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, GeometryUtils, Point}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class PavedRoadServiceSpec extends FunSuite with Matchers {

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao()
  val dynamicLinearAssetDao = new DynamicLinearAssetDao()

  val multiTypePropSeq = DynamicAssetValue(Seq(DynamicProperty("suggest_box","checkbox",required = false,List()),DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue("50")))))
  val multiTypePropSeq1 = DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue("40")))))
  val multiTypePropSeq2 = DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue("10")))))
  val multiTypePropSeq3 = DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue("99")))))
  val multiTypePropSeq4 = DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue("0")))))

  val propertyData = DynamicValue(multiTypePropSeq)
  val propertyData1 = DynamicValue(multiTypePropSeq1)
  val propertyData2 = DynamicValue(multiTypePropSeq2)
  val propertyData3 = DynamicValue(multiTypePropSeq3)
  val propertyData4 = DynamicValue(multiTypePropSeq4)

  val linkId1: String = LinkIdGenerator.generateRandom()
  val linkId2: String = LinkIdGenerator.generateRandom()
  val linkId3: String = LinkIdGenerator.generateRandom()
  val linkId4: String = LinkIdGenerator.generateRandom()

  val randomLinkId1: String = LinkIdGenerator.generateRandom()
  val randomLinkId2: String = LinkIdGenerator.generateRandom()
  val randomLinkId3: String = LinkIdGenerator.generateRandom()
  val randomLinkId4: String = LinkIdGenerator.generateRandom()

  when(mockRoadLinkService.fetchByLinkId(linkId2)).thenReturn(Some(RoadLinkFetched(linkId2, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq(RoadLinkFetched(linkId2, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(any[String])).thenReturn(Some(RoadLinkFetched(linkId2, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = Seq(RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(linkId2, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(linkId3, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(linkId4, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))

  when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((roadLinkWithLinkSource, Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLinkWithLinkSource)
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(any[String], any[Boolean])).thenReturn(roadLinkWithLinkSource.headOption)

  when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(30, Seq(linkId1)))
    .thenReturn(Seq(PersistedLinearAsset(1, linkId1, 1, Some(propertyData), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

  object ServiceWithDao extends PavedRoadService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = assetLock.synchronized {
    TestTransactions.runWithRollback()(test)
  }

  val assetLock = "Used to prevent deadlocks"

  private def createService() = {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavedRoadService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    service
  }

  private def createRoadLinks(municipalityCode: Int) = {
    val newLinkId1 = randomLinkId1
    val newLinkId2 = randomLinkId2
    val newLinkId3 = randomLinkId3
    val newLinkId4 = randomLinkId4
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

  private def createChangeInfo(roadLinks: Seq[RoadLink], timeStamp: Long) = {
    roadLinks.map(rl => ChangeInfo(Some(rl.linkId), Some(rl.linkId), 0L, 1, None, None, None, None, timeStamp))
  }

  test("Should not create new paved road assets and return the existing paved road assets when VVH doesn't have change information") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  
    val service = new PavedRoadService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val newLinkId = LinkIdGenerator.generateRandom()
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(newRoadLink))
    runWithRollback {

      val newAssetId = service.create(Seq(NewLinearAsset(newLinkId, 0, 20, propertyData, 1, 0, None)), PavedRoad.typeId, "testuser")
      val newAsset = service.getPersistedAssetsByIds(PavedRoad.typeId, newAssetId.toSet)

      when(mockRoadLinkService.getRoadLinksByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(List(newRoadLink))
      when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(any[Int], any[Seq[String]], any[Boolean], any[Boolean])).thenReturn(newAsset)

      val existingAssets = service.getByBoundingBox(PavedRoad.typeId, boundingBox).toList.flatten

      val existingAssetData = existingAssets.filter(p => (p.linkId == newLinkId && p.value.isDefined)).head

      existingAssets.length should be (1)
      existingAssetData.typeId should be (PavedRoad.typeId)
      existingAssetData.value should be (Some(propertyData))
      existingAssetData.id should be (newAsset.head.id)
    }
  }

  ignore("Should be created only 1 new paved road asset when get 3 roadlink change information from vvh and only 1 roadlink have surfacetype equal 2") {

    val service = new PavedRoadService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId2 = LinkIdGenerator.generateRandom()
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val newLinkId0 = LinkIdGenerator.generateRandom()
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val attributes0 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(0))
    val timeStamp = 14440000

    val newRoadLink2 = RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes2)
    val newRoadLink1 = RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink0 = RoadLink(newLinkId0, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes0)

    val changeInfoSeq = Seq(ChangeInfo(Some(newLinkId2), Some(newLinkId2), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp),
      ChangeInfo(Some(newLinkId1), Some(newLinkId1), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp),
      ChangeInfo(Some(newLinkId0), Some(newLinkId0), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(newRoadLink2, newRoadLink1, newRoadLink0), changeInfoSeq))
      when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(any[Int], any[Seq[String]], any[Boolean], any[Boolean])).thenReturn(List())

      val existingAssets = service.getByBoundingBox(PavedRoad.typeId, boundingBox).toList.flatten

      val filteredCreatedAssets = existingAssets.filter(p => p.linkId == newLinkId2 && p.value.isDefined)

      existingAssets.length should be (3)
      filteredCreatedAssets.length should be (1)
      filteredCreatedAssets.head.typeId should be (PavedRoad.typeId)
      filteredCreatedAssets.head.value should be (Some(propertyData3))
      filteredCreatedAssets.head.timeStamp should be (timeStamp)
    }
  }

  ignore("Should create new paved road assets from vvh roadlinks infromation through the actor") {
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavedRoadService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val linkId = LinkIdGenerator.generateRandom()
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val timeStamp = 11121

    val newRoadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfoSeq = Seq(
      ChangeInfo(Some(linkId), Some(linkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(newRoadLink), changeInfoSeq))
      when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(any[Int], any[Seq[String]], any[Boolean], any[Boolean])).thenReturn(List())

      service.getByBoundingBox(PavedRoad.typeId, boundingBox)

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("pavedRoad:saveProjectedPavedRoad"), captor.capture())

      val linearAssets = captor.getValue.asInstanceOf[Seq[PersistedLinearAsset]]

      linearAssets.length should be (1)

      val linearAsset = linearAssets.filter(p => (p.linkId == linkId)).head

      linearAsset.typeId should be (PavedRoad.typeId)
      linearAsset.timeStamp should be (timeStamp)

    }
  }

  // Tests for DROTH-4: Paved Road from VVH

  ignore("If VVH does not supply a change Information then no new asset should be created.") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavedRoadService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId = LinkIdGenerator.generateRandom()
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(newRoadLink), Nil))
      when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(any[Int], any[Seq[String]], any[Boolean], any[Boolean])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(PavedRoad.typeId, boundingBox).toList.flatten

      val createdAssetData = createdAsset.filter(p => (p.linkId == newLinkId && p.value.isDefined))

      createdAssetData.length should be (0)
    }
  }

  ignore("Should apply pavement on whole segment") {
    val service = new PavedRoadService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = LinkIdGenerator.generateRandom()
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
    PostGISDatabase.withDynTransaction {
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES (1, $oldLinkId1, 0.0, 46.233, ${SideCode.BothDirections.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT 1, 1, id, 1 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute

      val assets = service.getPersistedAssetsByIds(assetTypeId, Set(1L))
      assets should have size(1)

      when(mockRoadLinkService.getRoadLinksByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn(roadLinks)
      val after = service.getByBoundingBox(assetTypeId, boundingBox, Set(municipalityCode))
      after should have size(1)

      dynamicSession.rollback()
    }
  }
// ignore this unstable test for now, we are not getting any new changes at this moment DROTH-2327
  ignore("Should expire the assets if vvh gives change informations and the roadlink surface type is equal to 1") {
    val service = new PavedRoadService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId = LinkIdGenerator.generateRandom()
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val timeStamp = 14440000


    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val changeInfoSeq = Seq(ChangeInfo(Some(newLinkId), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp))

    runWithRollback {

      val newAssetId = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId, 0, 20, propertyData, 1, 0, None)), assetTypeId, "testuser")
      val newAsset = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId.toSet)

      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(newRoadLink), changeInfoSeq))
      when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(any[Int], any[Seq[String]], any[Boolean], any[Boolean])).thenReturn(List(newAsset.head))

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten.filter(_.value.isDefined)

      createdAsset.length should be (0)
    }
  }

  ignore("should do anything when change information link id doesn't exists on vvh roadlinks"){
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavedRoadService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId = LinkIdGenerator.generateRandom()
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val timeStamp = 11121

    val changeInfoSeq = Seq(
      ChangeInfo(Some(newLinkId), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), timeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(), changeInfoSeq))
      when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(any[Int], any[Seq[String]], any[Boolean], any[Boolean])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      createdAsset.length should be (0)

    }
  }

  ignore("If neither OTH or VVH have existing assets and changeInfo then nothing should be created and returned") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new PavedRoadService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId = LinkIdGenerator.generateRandom()
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
      when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(newRoadLink), Nil))
      when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(any[Int], any[Seq[String]], any[Boolean], any[Boolean])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten
      val filteredAssets = createdAsset.filter(p => (p.linkId == newLinkId && p.value.isDefined))

      createdAsset.length should be (1)
      filteredAssets.length should be (0)
    }
  }

  ignore("Adjust projected asset with creation"){

    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val service = new PavedRoadService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val oldLinkId = LinkIdGenerator.generateRandom()
    val newLinkId = LinkIdGenerator.generateRandom()
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


    PostGISDatabase.withDynTransaction {
      val (lrm, asset) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES ($lrm, $oldLinkId, 0.0, 150.0, ${SideCode.BothDirections.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset,$assetTypeId, null ,'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset,$lrm)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT $asset, $asset, id, 4779 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute


      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((roadLinks, changeInfo))
      service.getByMunicipality(assetTypeId, municipalityCode)

      verify(mockEventBus, times(1))
        .publish("dynamicAsset:update", ChangeSet(Set.empty[Long], Nil, Nil, Set.empty[Long], Nil))

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("pavedRoad:saveProjectedPavedRoad"), captor.capture())
      val projectedAssets = captor.getValue.asInstanceOf[Seq[PersistedLinearAsset]]
      projectedAssets.length should be(1)
      projectedAssets.foreach { proj =>
        proj.id should be (0)
        proj.linkId should be (newLinkId)
      }
      dynamicSession.rollback()
    }
  }

  test("create pavedRoad and check if informationSource is Municipality Maintainer ") {
    val service = createService()

    val toInsert = Seq(NewLinearAsset(LinkIdGenerator.generateRandom(), 0, 20, propertyData, 1, 0, None),
      NewLinearAsset(LinkIdGenerator.generateRandom(), 0, 20, propertyData, 1, 0, None))
    runWithRollback {
      val assetsIds = ServiceWithDao.create(toInsert, PavedRoad.typeId, "test")
      val assetsCreated = service.getPersistedAssetsByIds(PavedRoad.typeId, assetsIds.toSet)

      assetsCreated.length should be(2)
      assetsCreated.foreach { asset =>
        asset.informationSource should be(Some(MunicipalityMaintenainer))
      }
    }
  }

  test("update pavedRoad and check if informationSource is Municipality Maintainer "){

    val service = createService()
    val toInsert = Seq(NewLinearAsset(LinkIdGenerator.generateRandom(), 0, 20, propertyData1, BothDirections.value, 0, None), NewLinearAsset(LinkIdGenerator.generateRandom(), 0, 20, propertyData2, BothDirections.value, 0, None))
    runWithRollback {
      val assetsIds = ServiceWithDao.create(toInsert, PavedRoad.typeId, "test")
      val updated = ServiceWithDao.update(assetsIds, propertyData, "userTest")

      val assetsUpdated = ServiceWithDao.getPersistedAssetsByIds(PavedRoad.typeId, updated.toSet)

      assetsUpdated.length should be (2)
      assetsUpdated.foreach{asset =>
        asset.informationSource should be (Some(MunicipalityMaintenainer))
        asset.modifiedBy should be (Some("userTest"))
        asset.value should be (Some(propertyData))
      }
    }
  }
}
