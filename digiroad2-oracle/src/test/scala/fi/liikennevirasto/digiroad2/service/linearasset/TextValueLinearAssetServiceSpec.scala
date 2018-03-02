package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink, TextualValue}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.mockito.Matchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class TextValueLinearAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)

  object ServiceWithDao extends TextValueLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(ServiceWithDao.dataSource)(test)

  test("Update Exit number Text Field") {
    runWithRollback {
      //Update Text Values By Expiring the Old Asset
      val assetToUpdate = linearAssetDao.fetchAssetsWithTextualValuesByIds(Set(600068), "liittymänumero").head
      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(600068), TextualValue("Value for Test"), "UnitTestsUser")

      //Verify if the new data of the new asset is equal to old asset
      val assetUpdated = linearAssetDao.fetchAssetsWithTextualValuesByIds(newAssetIdCreatedWithUpdate.toSet, "liittymänumero").head

      assetUpdated.id should not be (assetToUpdate.id)
      assetUpdated.linkId should be(assetToUpdate.linkId)
      assetUpdated.sideCode should be(assetToUpdate.sideCode)
      assetUpdated.value should be(Some(TextualValue("Value for Test")))
      assetUpdated.startMeasure should be(assetToUpdate.startMeasure)
      assetUpdated.endMeasure should be(assetToUpdate.endMeasure)
      assetUpdated.createdBy should be(assetToUpdate.createdBy)
      assetUpdated.createdDateTime should be(assetToUpdate.createdDateTime)
      assetUpdated.modifiedBy should be(Some("UnitTestsUser"))
      assetUpdated.modifiedDateTime should not be empty
      assetUpdated.expired should be(false)
      assetUpdated.typeId should be(assetToUpdate.typeId)
      assetUpdated.vvhTimeStamp should be(assetToUpdate.vvhTimeStamp)

      //Verify if old asset is expired
      val assetExpired = linearAssetDao.fetchLinearAssetsByIds(Set(600068), "liittymänumero").head
      assetExpired.expired should be(true)
    }
  }

  test("Should map linear asset with textual value of old link to three new road links, asset covers the whole road link") {

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
    val assetTypeId = 260 // european road
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    OracleDatabase.withDynTransaction {
      val lrm = Sequences.nextLrmPositionPrimaryKeySeqValue
      val assetId = Sequences.nextPrimaryKeySeqValue
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) values ($lrm, $oldLinkId, 0, 25.000, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, created_date, created_by) values ($assetId, $assetTypeId, SYSDATE, 'dr2_test_data')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($assetId, $lrm)""".execute
      sqlu"""insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values ($assetId, $assetId, (select id from property where public_id='eurooppatienumero'), 'E666' || chr(10) || 'E667', sysdate, 'dr2_test_data')""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = ServiceWithDao.getByBoundingBox(assetTypeId, boundingBox).toList

      before.length should be (1)
      before.head.map(_.value should be (Some(TextualValue("E666\nE667"))))
      before.head.map(_.sideCode should be (SideCode.BothDirections))
      before.head.map(_.startMeasure should be (0))
      before.head.map(_.endMeasure should be (25))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = ServiceWithDao.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      after.length should be (3)
      after.foreach(_.value should be (Some(TextualValue("E666\nE667"))))
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

      dynamicSession.rollback()
    }
  }



}
