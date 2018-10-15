package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, NumericValue, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.json4s.{DefaultFormats, Formats}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}


class OnOffLinearAssetServiceSpec  extends FunSuite with Matchers {
  protected implicit val jsonFormats: Formats = DefaultFormats

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockLinearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockAssetDao = MockitoSugar.mock[OracleAssetDao]

  val roadLinkWithLinkSource = RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  object onOffLinearAsset extends OnOffLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Expire on-off asset with start and end measure by update - should create one asset"){
    runWithRollback {
      when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(100, 235, Seq(Point(0, 0), Point(0, 200)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

      val newAssets = onOffLinearAsset.create(Seq(NewLinearAsset(388562360l, 0, 200, NumericValue(1), 1, 0, None)), 30, "testuser", 1400000000)
      val ids =  onOffLinearAsset.update(newAssets, NumericValue(0), "test", Some(1400000000), Some(1), Some(Measures(0, 100)))

      val assets = mockLinearAssetDao.fetchLinearAssetsByIds(ids.toSet ++ newAssets, LinearAssetTypes.numericValuePropertyId)
      assets.length should be(2)

      assets.count(asset => asset.startMeasure == 100 && asset.endMeasure == 200) should be(1)
      assets.find(asset => asset.startMeasure == 100 && asset.endMeasure == 200).get.expired  should be(false)
      assets.find( asset => asset.startMeasure == 0 && asset.endMeasure == 200).get.expired should be (true)

      assets.find(_.expired).get.modifiedBy should be (None)
      assets.find(!_.expired).get.modifiedBy should be (Some("test"))
      assets.find(!_.expired).get.createdBy should be (Some("testuser"))
    }
  }

  test("Expire on-off asset with start and end measure by update - should create two assets"){
    runWithRollback {
      when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(100, 235, Seq(Point(0, 0), Point(0, 200)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

      val newAssets = onOffLinearAsset.create(Seq(NewLinearAsset(388562360l, 0, 200, NumericValue(1), 1, 0, None)), 30, "testuser", 1400000000)
      val ids =  onOffLinearAsset.update(newAssets, NumericValue(0), "test", Some(1400000000), Some(1), Some(Measures(50, 100)))

      val assets = mockLinearAssetDao.fetchLinearAssetsByIds(ids.toSet ++ newAssets, LinearAssetTypes.numericValuePropertyId)
      assets.length should be(3)

      assets.count(asset => asset.startMeasure == 0 && asset.endMeasure == 50) should be(1)
      assets.count(asset => asset.startMeasure == 100 && asset.endMeasure == 200) should be(1)
      assets.find( asset => asset.startMeasure == 0 && asset.endMeasure == 50).get.expired should be (false)
      assets.find( asset => asset.startMeasure == 100 && asset.endMeasure == 200).get.expired should be (false)

      assets.find( asset => asset.startMeasure == 0 && asset.endMeasure == 200).get.expired should be (true)
      assets.find(_.expired).get.modifiedBy should be (None)
      assets.find(!_.expired).get.modifiedBy should be (Some("test"))
      assets.find(!_.expired).get.createdBy should be (Some("testuser"))
    }
  }

  test("Expire on-off asset with start and end measure by update - should not create assets"){
    runWithRollback {
      when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(100, 235, Seq(Point(0, 0), Point(0, 200)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

      val newAssets = onOffLinearAsset.create(Seq(NewLinearAsset(388562360l, 0, 200, NumericValue(1), 1, 0, None)), 30, "testuser", 1400000000)
      val ids =  onOffLinearAsset.update(newAssets, NumericValue(0), "test", Some(1400000000), Some(1), Some(Measures(0, 200)))

      val assets = mockLinearAssetDao.fetchLinearAssetsByIds(ids.toSet ++ newAssets, LinearAssetTypes.numericValuePropertyId)
      assets.length should be(1)

      assets.find( asset => asset.startMeasure == 0 && asset.endMeasure == 200).get.expired should be (true)
      assets.find(_.expired).get.modifiedBy should be (Some("test"))
    }
  }
}
