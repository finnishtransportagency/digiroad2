
package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao, Sequences}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class LinearAssetSpecSupport extends FunSuite with Matchers {

  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient: VVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient: VVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools: PolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360L)).thenReturn(Some(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource: RoadLink = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  val mockLinearAssetDao: PostGISLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao: PostGISAssetDao = MockitoSugar.mock[PostGISAssetDao]

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

}
class LinearAssetServiceSpec extends LinearAssetSpecSupport  {

  object ServiceWithDao extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  test("Separate linear asset") {
    val typeId = 140
    runWithRollback {
      val newLimit = NewLinearAsset(linkId = 388562360, startMeasure = 0, endMeasure = 10, value = NumericValue(1), sideCode = 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))
      val createdId = ServiceWithDao.separate(assetId, Some(NumericValue(2)), Some(NumericValue(3)), "unittest", (i, _) => Unit)

      createdId.length should be(2)

      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId.head)).head
      val createdLimit1 = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId.last)).head

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      createdLimit.value should be (Some(NumericValue(2)))
      createdLimit.modifiedBy should be (Some("unittest"))

      createdLimit1.linkId should be (388562360)
      createdLimit1.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit1.value should be (Some(NumericValue(3)))
      createdLimit1.createdBy should be (Some("test"))
    }
  }

  test("Separate with empty value towards digitization") {
    val typeId = 140
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val createdId = ServiceWithDao.separate(assetId, None, Some(NumericValue(3)), "unittest", (i, _) => Unit).filter(_ != assetId).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(assetId)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.BothDirections.value)
      oldLimit.expired should be (true)
      oldLimit.modifiedBy should be (None)

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(NumericValue(3)))
      createdLimit.expired should be (false)
      createdLimit.createdBy should be (Some("test"))
    }
  }

  test("Separate with empty value against digitization") {
    val typeId = 140
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val newAssetIdAfterUpdate = ServiceWithDao.separate(assetId, Some(NumericValue(2)), None, "unittest", (i, _) => Unit)
      newAssetIdAfterUpdate.size should be(1)

      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(newAssetIdAfterUpdate.head)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.expired should be (false)
      oldLimit.modifiedBy should be (Some("unittest"))

    }
  }

  test("Split linear asset") {
    val typeId = 140
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val ids = ServiceWithDao.split(assetId, 2.0, Some(NumericValue(2)), Some(NumericValue(3)), "unittest", (_, _) => Unit)

      val createdId = ids(1)
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(ids.head)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.BothDirections.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.modifiedBy should be (Some("unittest"))
      oldLimit.startMeasure should be (2.0)
      oldLimit.endMeasure should be (10.0)

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.BothDirections.value)
      createdLimit.value should be (Some(NumericValue(3)))
      createdLimit.createdBy should be (Some("test"))
      createdLimit.startMeasure should be (0.0)
      createdLimit.endMeasure should be (2.0)
    }
  }

  test("Separation should call municipalityValidation") {
    def failingMunicipalityValidation(code: Int, administrativeClass: AdministrativeClass): Unit = { throw new IllegalArgumentException }
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      intercept[IllegalArgumentException] {
        ServiceWithDao.separate(assetId, Some(NumericValue(1)), Some(NumericValue(2)), "unittest", failingMunicipalityValidation)
      }
    }
  }

  // Tests for DROTH-76 Automatics for fixing linear assets after geometry update (using VVH change info data)

  test("Should expire assets from deleted road links through the actor")
  {
    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val oldLinkId3 = 5003
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val assetTypeId = 100 // lit roads

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val linearAssetService = new LinearAssetService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val newRoadLinks = Seq(RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), 144000000))

    PostGISDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1,$asset1,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId2, 0, 10.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset2,$asset2,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId3, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset3,$asset3,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((newRoadLinks, changeInfo))

      linearAssetService.getByMunicipality(assetTypeId, municipalityCode)

      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("linearAssets:update"), captor.capture())
      captor.getValue.asInstanceOf[ChangeSet].expiredAssetIds should be (Set(asset1,asset2,asset3))

      dynamicSession.rollback()
    }
  }

  test("Should map linear asset (lit road) of old link to three new road links, asset covers the whole road link") {

    // Divided road link (change types 5 and 6)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T  = f
    }

    val oldLinkId = 5000
    val newLinkId1 = 6001
    val newLinkId2 = 6002
    val newLinkId3 = 6003
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 100
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    PostGISDatabase.withDynTransaction {
      val lrm1 = Sequences.nextLrmPositionPrimaryKeySeqValue
      val asset1 = Sequences.nextPrimaryKeySeqValue
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, 0.0, 25.0, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id) values ($asset1,$assetTypeId)""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1,$asset1,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList

      before.length should be (1)
      before.head.map(_.value should be (Some(NumericValue(1))))
      before.head.map(_.sideCode should be (SideCode.BothDirections))
      before.head.map(_.startMeasure should be (0))
      before.head.map(_.endMeasure should be (25))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      after.length should be (3)
//      after.foreach(println)
      after.foreach(_.value should be (Some(NumericValue(1))))
      after.foreach(_.sideCode should be (SideCode.BothDirections))

      val afterByLinkId = after.groupBy(_.linkId)
      val linearAsset1 = afterByLinkId(newLinkId1)
      linearAsset1.length should be (1)
      linearAsset1.head.startMeasure should be (0)
      linearAsset1.head.endMeasure should be (10)
      val linearAsset2 = afterByLinkId(newLinkId2)
      linearAsset2.length should be (1)
      linearAsset2.head.startMeasure should be (0)
      linearAsset2.head.endMeasure should be (10)
      val linearAsset3 = afterByLinkId(newLinkId3)
      linearAsset3.length should be (1)
      linearAsset3.head.startMeasure should be (0)
      linearAsset3.head.endMeasure should be (5)

      linearAsset1.forall(a => a.vvhTimeStamp > 0L) should be (true)
      linearAsset2.forall(a => a.vvhTimeStamp > 0L) should be (true)
      linearAsset3.forall(a => a.vvhTimeStamp > 0L) should be (true)
      dynamicSession.rollback()
    }
  }

  test("Should map linear assets (lit road) of old link to three new road links, asset covers part of road link") {

    // Divided road link (change types 5 and 6)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId1 = 6001
    val newLinkId2 = 6002
    val newLinkId3 = 6003
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 100
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    PostGISDatabase.withDynTransaction {
      val lrm1 = Sequences.nextLrmPositionPrimaryKeySeqValue
      val asset1 = Sequences.nextPrimaryKeySeqValue
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId, 5.0, 15.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id) values ($asset1,$assetTypeId)""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1,$asset1,(select id from property where public_id = 'mittarajoitus'), 1)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (3)
      before.foreach(_.sideCode should be (SideCode.BothDirections))
      before.foreach(_.linkId should be (oldLinkId))

      val beforeByValue = before.groupBy(_.value)
      beforeByValue(Some(NumericValue(1))).length should be (1)
      beforeByValue(None).length should be (2)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      after.length should be (5)
      after.foreach(_.sideCode should be (SideCode.BothDirections))

      val afterByLinkId = after.groupBy(_.linkId)

      val linearAssets1 = afterByLinkId(newLinkId1)
      linearAssets1.length should be (2)
      linearAssets1.filter(_.startMeasure == 0.0).head.value should be (None)
      linearAssets1.filter(_.startMeasure == 5.0).head.value should be (Some(NumericValue(1)))
      val linearAssets2 = afterByLinkId(newLinkId2)
      linearAssets2.length should be (2)
      linearAssets2.filter(_.startMeasure == 0.0).head.value should be (Some(NumericValue(1)))
      linearAssets2.filter(_.startMeasure == 5.0).head.value should be (None)
      val linearAssets3 = afterByLinkId(newLinkId3)
      linearAssets3.length should be (1)
      linearAssets3.filter(_.startMeasure == 0.0).head.value should be (None)

      dynamicSession.rollback()
    }
  }

  test("Should map linear assets (lit road) of three old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val oldLinkId3 = 5003
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 100
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), 144000000))

    PostGISDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1,$asset1,(select id from property where public_id = 'mittarajoitus'),1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId2, 0, 10.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (2,$asset2,(select id from property where public_id = 'mittarajoitus'),1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId3, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (3,$asset3,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (3)
      before.foreach(_.value should be (Some(NumericValue(1))))
      before.foreach(_.sideCode should be (SideCode.BothDirections))

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (1)
      linearAssets1.head.startMeasure should be (0)
      linearAssets1.head.endMeasure should be (10)
      val linearAssets2 = beforeByLinkId(oldLinkId2)
      linearAssets2.length should be (1)
      linearAssets2.head.startMeasure should be (0)
      linearAssets2.head.endMeasure should be (10)
      val linearAssets3 = beforeByLinkId(oldLinkId3)
      linearAssets3.head.startMeasure should be (0)
      linearAssets3.head.endMeasure should be (5)


      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

//      after.foreach(println)
      after.length should be(1)
      after.head.value should be(Some(NumericValue(1)))
      after.head.sideCode should be (SideCode.BothDirections)
      after.head.startMeasure should be (0)
      after.head.endMeasure should be (25)
      after.head.modifiedBy should be(Some("KX2"))

      val formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
      val latestModifiedDate = DateTime.parse("2016-02-17 10:03:51.047483", formatter)
      after.head.modifiedDateTime should be(Some(latestModifiedDate))

      dynamicSession.rollback()
    }
  }

  test("Should map winter speed limits of two old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 180
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000))

    PostGISDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1,$asset1,(select id from property where public_id = 'mittarajoitus'),40)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset2,$asset2,(select id from property where public_id = 'mittarajoitus'),50)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset3,$asset3,(select id from property where public_id = 'mittarajoitus'),60)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (4)
      before.count(_.value.nonEmpty) should be (3)

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (2)
      linearAssets1.head.startMeasure should be (0)
      linearAssets1.head.endMeasure should be (10)
      val linearAssets2 = beforeByLinkId(oldLinkId2)
      linearAssets2.length should be (2)
      linearAssets2.filter(l => l.id > 0).head.startMeasure should be (0)
      linearAssets2.filter(l => l.id > 0).head.endMeasure should be (5)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten
//      after.foreach(println)
      after.length should be(4)
      after.count(_.value.nonEmpty) should be (3)
      after.count(l => l.startMeasure == 0.0 && l.endMeasure == 10.0) should be (2)
      after.count(l => l.startMeasure == 10.0 && l.endMeasure == 15.0 && l.value.get.equals(NumericValue(60))) should be (1)
      after.count(l => l.startMeasure == 15.0 && l.endMeasure == 25.0 && l.value.isEmpty) should be (1)

      dynamicSession.rollback()
    }
  }

  test("Create new assets on update when exist sideCode adjustmen") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp(-5)
    when(mockRoadLinkService.vvhClient).thenReturn(mockVVHClient)
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)

    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val service = new LinearAssetService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 1234
    val oldLinkId2 = 1235
    val assetTypeId = 100
    val vvhTimeStamp = 14440000

    val roadLinkWithLinkSource = RoadLink(
      oldLinkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))


    PostGISDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1,$asset1,(select id from property where public_id = 'mittarajoitus'),40)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2,$lrm2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset2,$asset2,(select id from property where public_id = 'mittarajoitus'),50)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3,$lrm3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset3,$asset3,(select id from property where public_id = 'mittarajoitus'),60)""".execute

      val original = service.getPersistedAssetsByIds(assetTypeId, Set(asset1)).head
      val projectedLinearAssets = Seq(original.copy(startMeasure = 0.1, endMeasure = 10.1, sideCode = 1, vvhTimeStamp = vvhTimeStamp))

      val changeSet = projectedLinearAssets.foldLeft(ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil)) {
        case (acc, proj) =>
          acc.copy(adjustedMValues = acc.adjustedMValues ++ Seq(MValueAdjustment(proj.id, proj.linkId, proj.startMeasure, proj.endMeasure)), adjustedSideCodes = acc.adjustedSideCodes ++ Seq(SideCodeAdjustment(proj.id, SideCode.apply(proj.sideCode), original.typeId)))
      }

      service.updateChangeSet(changeSet)
      val all = service.dao.fetchLinearAssetsByLinkIds(assetTypeId, Seq(oldLinkId1, oldLinkId2), "mittarajoitus")
      all.size should be (3)
      val persisted = service.getPersistedAssetsByLinkIds(assetTypeId, Seq(oldLinkId1))
      persisted.size should be (2)
      persisted.exists(_.id == asset2) should be (true)
      persisted.exists(_.id == asset1) should be (false)
      val newAsset = persisted.find(_.id !== asset2).get
      newAsset.id should not be original.id
      newAsset.startMeasure should be (0.1)
      newAsset.endMeasure should be (10.1)
      newAsset.expired should be (false)

      dynamicSession.rollback()
    }
  }

  case class TestAssetInfo(newLinearAsset: NewLinearAsset, typeId: Int)
  test("Should create a new asset with a new sideCode (dupicate the oldAsset)") {

    val assetsInfo = Seq(
      TestAssetInfo(NewLinearAsset(1l, 0, 10, NumericValue(2), SideCode.AgainstDigitizing.value, 0, None), NumberOfLanes.typeId),
      TestAssetInfo(NewLinearAsset(1l, 0, 10, NumericValue(1000), SideCode.AgainstDigitizing.value, 0, None), TotalWeightLimit.typeId),
      TestAssetInfo(NewLinearAsset(1l, 0, 10, NumericValue(1200), SideCode.AgainstDigitizing.value, 0, None), TrailerTruckWeightLimit.typeId),
      TestAssetInfo(NewLinearAsset(1l, 0, 10, NumericValue(1100), SideCode.AgainstDigitizing.value, 0, None), AxleWeightLimit.typeId),
      TestAssetInfo(NewLinearAsset(1l, 0, 10, NumericValue(2000), SideCode.AgainstDigitizing.value, 0, None), BogieWeightLimit.typeId),
      TestAssetInfo(NewLinearAsset(1l, 0, 10, NumericValue(400), SideCode.AgainstDigitizing.value, 0, None), HeightLimit.typeId),
      TestAssetInfo(NewLinearAsset(1l, 0, 10, NumericValue(200), SideCode.AgainstDigitizing.value, 0, None), LengthLimit.typeId),
      TestAssetInfo(NewLinearAsset(1l, 0, 10, NumericValue(300), SideCode.AgainstDigitizing.value, 0, None), WidthLimit.typeId))

    PostGISDatabase.withDynTransaction {
      assetsInfo.foreach(testSideCodeAdjustment)

      dynamicSession.rollback()
    }
  }

  def testSideCodeAdjustment(assetInfo: TestAssetInfo) {
    when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))
    val id = ServiceWithDao.create(Seq(assetInfo.newLinearAsset), assetInfo.typeId, "sideCode_adjust").head
    val original = ServiceWithDao.getPersistedAssetsByIds(assetInfo.typeId, Set(id)).head

    val changeSet =
      ChangeSet(droppedAssetIds = Set.empty[Long],
        expiredAssetIds = Set.empty[Long],
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
        adjustedSideCodes = Seq(SideCodeAdjustment(id, SideCode.BothDirections, original.typeId)),
        valueAdjustments = Seq.empty[ValueAdjustment])

    when(mockAssetDao.getAssetTypeId(Seq(id))).thenReturn(Seq((id, assetInfo.typeId)))

    ServiceWithDao.updateChangeSet(changeSet)
    val expiredAsset = ServiceWithDao.getPersistedAssetsByIds(assetInfo.typeId, Set(id)).head
    val newAsset = ServiceWithDao.dao.fetchLinearAssetsByLinkIds(assetInfo.typeId, Seq(original.linkId), "mittarajoitus")
    withClue("assetName " + AssetTypeInfo.apply(assetInfo.typeId).layerName) {
      newAsset.size should be(1)
      val asset = newAsset.head
      asset.startMeasure should be(original.startMeasure)
      asset.endMeasure should be(original.endMeasure)
      asset.createdBy should not be original.createdBy
      asset.startMeasure should be(original.startMeasure)
      asset.sideCode should be(SideCode.BothDirections.value)
      asset.value should be(original.value)
      asset.expired should be(false)
      expiredAsset.expired should be(true)
    }
  }

  test("Should update asset without override verified by or date info ") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 1234
    val oldLinkId2 = 1235
    val assetTypeId = 100
    val vvhTimeStamp = 14440000
    PostGISDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val asset1 = Sequences.nextPrimaryKeySeqValue
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES ($lrm1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by, verified_by, verified_date) values ($asset1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),
            'KX1', 'testeUser', TO_TIMESTAMP('2017-11-30 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1,$lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1,$asset1,(select id from property where public_id = 'mittarajoitus'),40)""".execute

      val original = service.getPersistedAssetsByIds(assetTypeId, Set(asset1)).head
      val projectedLinearAssets = Seq(original.copy(startMeasure = 0.1, endMeasure = 10.1, sideCode = 1, vvhTimeStamp = vvhTimeStamp))
      service.persistProjectedLinearAssets(projectedLinearAssets)
      val persisted = service.getPersistedAssetsByIds(assetTypeId, Set(asset1)).head
      persisted.verifiedDate.get.toString("yyyy-MM-dd") should be ("2017-11-30")
      persisted.verifiedBy.get should be ("testeUser")
      dynamicSession.rollback()
    }
  }

  test("pseudo vvh timestamp is correctly created") {
    val vvhClient = new VVHRoadLinkClient("")
    val hours = DateTime.now().getHourOfDay
    val yesterday = vvhClient.createVVHTimeStamp(hours + 1)
    val today = vvhClient.createVVHTimeStamp(hours)

    (today % 24*60*60*1000L) should be (0L)
    (yesterday % 24*60*60*1000L) should be (0L)
    today should be > yesterday
    (yesterday + 24*60*60*1000L) should be (today)
  }

  test("Should extend traffic count on segment") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp(-5)
    when(mockRoadLinkService.vvhClient).thenReturn(mockVVHClient)
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val oldLinkId1 = 3056622
    val municipalityCode = 444
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 170
    val geom = List(Point(346005.726,6688024.548),Point(346020.228,6688034.371),Point(346046.054,6688061.11),Point(346067.323,6688080.86))
    val len = GeometryUtils.geometryLength(geom)

    val roadLinks = Seq(RoadLink(oldLinkId1, geom, len, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId1), Some(oldLinkId1), 1204467577, 7, Some(81.53683273), Some(164.92962409), Some(0), Some(83.715056320000002), 1461970812000L),
      ChangeInfo(Some(oldLinkId1), None, 1204467577, 8, Some(0), Some(81.53683273), None, None, 1461970812000L),
      ChangeInfo(Some(oldLinkId1), None, 1204467577, 8, Some(164.92962409), Some(165.37927110999999), None, None, 1461970812000L))

    PostGISDatabase.withDynTransaction {
      val (lrm, asset) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES ($lrm, $oldLinkId1, 0.0, 83.715, ${SideCode.BothDirections.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset,$lrm)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT $asset, $asset, id, 4779 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinks, changeInfo))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(roadLinks)
      val before = service.getByBoundingBox(assetTypeId, boundingBox, Set(municipalityCode))
      before should have size 2

      val newAsset = NewLinearAsset(oldLinkId1, 2.187, len, NumericValue(4779), 1, 234567, None)

      val id = service.create(Seq(newAsset), assetTypeId, "KX2")

      id should have size 1
      id.head should not be 0

      val assets = service.getPersistedAssetsByIds(assetTypeId, Set(asset, id.head))
      assets should have size 2
      assets.forall(_.vvhTimeStamp > 0L) should be (true)

      val after = service.getByBoundingBox(assetTypeId, boundingBox, Set(municipalityCode))
      after should have size 1
      after.flatten.forall(_.id != 0) should be (true)
      dynamicSession.rollback()
    }
  }

  test("Get Municipality Code By Asset Id") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenCallRealMethod()
    val timeStamp = mockVVHRoadLinkClient.createVVHTimeStamp(-5)
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    PostGISDatabase.withDynTransaction {
      val (assetId, assetMunicipalityCode) = sql"""select ID, MUNICIPALITY_CODE from asset where asset_type_id = 10 and valid_to >= current_timestamp offset 1 limit 1""".as[(Int, Int)].first
      val municipalityCode = service.getMunicipalityCodeByAssetId(assetId)
      municipalityCode should be(assetMunicipalityCode)
    }
  }

  test("Should filter out linear assets on walkways from TN-ITS message") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val roadLink1 = RoadLink(100, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 8, TrafficDirection.BothDirections, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink2 = RoadLink(200, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 5, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink3 = RoadLink(300, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 7, TrafficDirection.BothDirections, TractorRoad, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val heightLimitAssetId = 70

    PostGISDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm1, 100)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset1, ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1, $lrm1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset1, $asset1, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm2, 200)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset2,  ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2, $lrm2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset2, $asset2, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm3, 300)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset3,  ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3, $lrm3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values ($asset3, $asset3, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

      val result = service.getChanged(heightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.parse("2016-11-02T12:00Z"))
      result.length should be(1)
      result.head.link.linkType should not be (TractorRoad)
      result.head.link.linkType should not be (CycleOrPedestrianPath)

      dynamicSession.rollback()
    }
  }

  test("Verify if we have all changes between given date after update a NumericValue Field in OTH") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val roadLink1 = RoadLink(1611374, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 8, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val totalWeightLimitAssetId = 30

    PostGISDatabase.withDynTransaction {
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink1))

      //Linear assets that have been changed in OTH between given date values Before Update
      val resultBeforeUpdate = service.getChanged(totalWeightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))

      //Update Numeric Values
      val assetToUpdate = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      when(mockAssetDao.getAssetTypeId(Seq(assetToUpdate.id))).thenReturn(Seq((assetToUpdate.id, totalWeightLimitAssetId)))

      //updated by side code in order to create a new asset
      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(11111l), NumericValue(4000), "UnitTestsUser", sideCode = Some(2))
      val assetUpdated = linearAssetDao.fetchLinearAssetsByIds(newAssetIdCreatedWithUpdate.toSet, "mittarajoitus").head

      //Linear assets that have been changed in OTH between given date values After Update
      val resultAfterUpdate = service.getChanged(totalWeightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))

      val oldAssetInMessage = resultAfterUpdate.find { changedLinearAsset => changedLinearAsset.linearAsset.id == assetToUpdate.id }
      val newAssetInMessage = resultAfterUpdate.find { changedLinearAsset => changedLinearAsset.linearAsset.id == assetUpdated.id }

      resultAfterUpdate.size should be (resultBeforeUpdate.size + 1)
      oldAssetInMessage.size should be (1)
      newAssetInMessage.size should be (1)

      oldAssetInMessage.head.linearAsset.expired should be (true)
      oldAssetInMessage.head.linearAsset.value should be (assetToUpdate.value)

      dynamicSession.rollback()
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
    val linearAssetService = new LinearAssetService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId = 6000
    val municipalityCode = 444
    val functionalClass = 1
    val assetTypeId = 170
    val geom = List(Point(0, 0), Point(300, 0))
    val len = GeometryUtils.geometryLength(geom)

    val roadLinks = Seq(RoadLink(oldLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
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


      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((roadLinks, changeInfo))
      linearAssetService.getByMunicipality(assetTypeId, municipalityCode)

      verify(mockEventBus, times(1))
        .publish("linearAssets:update", ChangeSet(Set.empty[Long], Nil, Nil, Nil, Set.empty[Long], Nil))

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("linearAssets:saveProjectedLinearAssets"), captor.capture())
      val projectedAssets = captor.getValue
      projectedAssets.asInstanceOf[Seq[PersistedLinearAsset]].length should be(1)
      projectedAssets.asInstanceOf[Seq[PersistedLinearAsset]]foreach { proj =>
        proj.id should be (0)
        proj.linkId should be (6000)
      }
      dynamicSession.rollback()
    }
  }
}
