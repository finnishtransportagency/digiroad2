package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, NumericValue, PersistedLinearAsset, Value}
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.json4s.{DefaultFormats, Formats}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}


class OnOffLinearAssetServiceSpec  extends FunSuite with Matchers {
  protected implicit val jsonFormats: Formats = DefaultFormats

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val onOffLinearAsset = new OnOffLinearAssetService(mockRoadLinkService, mockEventBus, mockLinearAssetDao)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Expire on-off asset with start and end measure by update - should create two assets"){
    runWithRollback {
      when(mockLinearAssetDao.fetchLinearAssetsByIds(any[Set[Long]], any[String])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 200, None, None, None, None, false, 100, 1400000000, None, LinkGeomSource.NormalLinkInterface, None, None)))
      when(mockLinearAssetDao.updateExpiration(any[Long], any[Boolean],  any[String])).thenReturn(Some(1.toLong))

      onOffLinearAsset.updateValueByExpiration(100, NumericValue(0), LinearAssetTypes.numericValuePropertyId, "test", Some(Measures(2, 100)), Some(1400000000), Some(1))

      onOffLinearAsset.dao.fetchLinearAssetsByIds(Set(1), LinearAssetTypes.numericValuePropertyId).length should be (0)

      val assets = onOffLinearAsset.dao.fetchLinearAssetsByLinkIds(100, Seq(100), LinearAssetTypes.numericValuePropertyId).filter(!_.expired)
      assets.length should be(2)
      assets.count(_.startMeasure == 0) should be (1)
      assets.count(_.startMeasure == 100) should be (1)
      assets.count(_.endMeasure == 2) should be (1)
      assets.count(_.endMeasure == 200) should be (1)
    }
  }

  test("Expire on-off asset with start and end measure by update - should create one asset"){
    runWithRollback {
      when(mockLinearAssetDao.fetchLinearAssetsByIds(any[Set[Long]], any[String])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 200, None, None, None, None, false, 100, 1400000000, None, LinkGeomSource.NormalLinkInterface, None, None)))
      when(mockLinearAssetDao.updateExpiration(any[Long], any[Boolean],  any[String])).thenReturn(Some(1.toLong))

      onOffLinearAsset.updateValueByExpiration(100, NumericValue(0), LinearAssetTypes.numericValuePropertyId, "test", Some(Measures(0, 100)), Some(1400000000), Some(1))

      onOffLinearAsset.dao.fetchLinearAssetsByIds(Set(1), LinearAssetTypes.numericValuePropertyId).length should be (0)

      val asset = onOffLinearAsset.dao.fetchLinearAssetsByLinkIds(100, Seq(100), LinearAssetTypes.numericValuePropertyId).filter(!_.expired)
      asset.length should be(1)
      asset.count(_.startMeasure == 100) should be (1)
      asset.count(_.endMeasure == 200) should be (1)
    }
  }

  test("Expire on-off asset with start and end measure by update - should not create assets"){
    runWithRollback {
      when(mockLinearAssetDao.fetchLinearAssetsByIds(any[Set[Long]], any[String])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 200, None, None, None, None, false, 100, 1400000000, None, LinkGeomSource.NormalLinkInterface, None, None)))
      when(mockLinearAssetDao.updateExpiration(any[Long], any[Boolean],  any[String])).thenReturn(Some(1.toLong))

      onOffLinearAsset.updateValueByExpiration(100, NumericValue(0), LinearAssetTypes.numericValuePropertyId, "test", Some(Measures(0, 200)), Some(1400000000), Some(1))

      onOffLinearAsset.dao.fetchLinearAssetsByIds(Set(1), LinearAssetTypes.numericValuePropertyId).length should be (0)

      val asset = onOffLinearAsset.dao.fetchLinearAssetsByLinkIds(100, Seq(100), LinearAssetTypes.numericValuePropertyId).filter(!_.expired)
      asset.length should be(0)
    }
  }
}
