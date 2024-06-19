package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink, TextualValue}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools, TestTransactions}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class TextValueLinearAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  private val (linkId, linkId2) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
  
  when(mockRoadLinkService.fetchByLinkId(linkId)).thenReturn(Some(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(any[String])).thenReturn(Some(RoadLinkFetched(linkId, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = RoadLink(
    linkId2, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinks(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(roadLinkWithLinkSource)))
  when(mockRoadLinkService.getRoadLinksWithComplementary(any[Int])).thenReturn((List(roadLinkWithLinkSource)))
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao()

  object ServiceWithDao extends TextValueLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Update Exit number Text Field") {
    runWithRollback {
      //Update Text Values By Expiring the Old Asset
      val assetToUpdate = linearAssetDao.fetchAssetsWithTextualValuesByIds(Set(600068), "liittymänumero").head
      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(600068), TextualValue("Value for Test"), "UnitTestsUser")

      //Verify if the new data of the new asset is equal to old asset
      val assetUpdated = linearAssetDao.fetchAssetsWithTextualValuesByIds(newAssetIdCreatedWithUpdate.toSet, "liittymänumero").head

      assetUpdated.id should be (assetToUpdate.id)
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
      assetUpdated.timeStamp should be(assetToUpdate.timeStamp)

      //Verify if old asset is not expired, since the changes were made on value
      val assetExpired = linearAssetDao.fetchLinearAssetsByIds(Set(600068), "liittymänumero").head
      assetExpired.expired should be(false)
    }
  }
}