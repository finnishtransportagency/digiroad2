package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.masslimitation.oracle.OracleMassLimitationDao
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class LinearMassLimitationSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockMassLimitationDao = MockitoSugar.mock[OracleMassLimitationDao]
  val TotalWeightLimits = 30
  val TrailerTruckWeightLimits = 40
  val AxleWeightLimits = 50
  val BogieWeightLimits = 60
  val MassLimitationAssetTypes = List(TotalWeightLimits, TrailerTruckWeightLimits, AxleWeightLimits, BogieWeightLimits)

  val assetsTotalWeightLimits = Seq(PersistedLinearAsset(1l, 1000l, SideCode.BothDirections.value, Some(NumericValue(1)), 1.0, 5.0, None, None, None, None, false, TotalWeightLimits, 0, None, linkSource = NormalLinkInterface))
  val assetsTotalWeightLimits2 = Seq(PersistedLinearAsset(2l, 1002l, SideCode.TowardsDigitizing.value, Some(NumericValue(1)), 1.0, 5.0, None, None, None, None, false, TotalWeightLimits, 0, None, linkSource = NormalLinkInterface))

  val assetsTrailerTruckWeightLimits = Seq(PersistedLinearAsset(3l, 1000l, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 2.0, 4.0, None, None, None, None, false, TrailerTruckWeightLimits, 0, None, linkSource = NormalLinkInterface))
  val assetsTrailerTruckWeightLimits1 = Seq(PersistedLinearAsset(4l, 1001l, 3, Some(NumericValue(2)), 5.0, 8.0, None, None, None, None, false, TrailerTruckWeightLimits, 0, None, linkSource = NormalLinkInterface))

  val assetsAxleWeightLimits2 = Seq(PersistedLinearAsset(3l, 1002l, SideCode.BothDirections.value, Some(NumericValue(2)), 2.0, 4.0, None, None, None, None, false, AxleWeightLimits, 0, None, linkSource = NormalLinkInterface))
  val assetsAxleWeightLimits1 = Seq(PersistedLinearAsset(5l, 1001l, SideCode.AgainstDigitizing.value, Some(NumericValue(2)), 5.0, 8.0, None, None, None, None, false, AxleWeightLimits, 0, None, linkSource = NormalLinkInterface))

  val assetsBogieWeightLimits2 = Seq(PersistedLinearAsset(3l, 1002l, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 2.0, 6.0, None, None, None, None, false, BogieWeightLimits, 0, None, linkSource = NormalLinkInterface),
  PersistedLinearAsset(6l, 1002l, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 4.0, 20.0, None, None, None, None, false, BogieWeightLimits, 0, None, linkSource = NormalLinkInterface))

  val assets = assetsTotalWeightLimits ++ assetsTrailerTruckWeightLimits
  val assets1 = assetsTrailerTruckWeightLimits1 ++ assetsAxleWeightLimits1
  val assets2 = assetsTotalWeightLimits2 ++ assetsAxleWeightLimits2 ++ assetsBogieWeightLimits2
  when(mockMassLimitationDao.fetchLinearAssetsByLinkIds(MassLimitationAssetTypes, Seq(1000), LinearAssetTypes.numericValuePropertyId)).thenReturn(assets)
  when(mockMassLimitationDao.fetchLinearAssetsByLinkIds(MassLimitationAssetTypes, Seq(1001), LinearAssetTypes.numericValuePropertyId)).thenReturn(assets)
  when(mockMassLimitationDao.fetchLinearAssetsByLinkIds(MassLimitationAssetTypes, Seq(1002), LinearAssetTypes.numericValuePropertyId)).thenReturn(assets)
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test(" fetch dispatches to dao based on asset type id") {
    val service = new LinearMassLimitationService(mockRoadLinkService, mockMassLimitationDao)

    runWithRollback {
      val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
      val geometry1 = List(Point(0.0, 0.0), Point(10.0, 0.0))
      val geometry2 = List(Point(10.0, 0.0), Point(20.0, 0.0))
      val geometry3 = List(Point(30.0, 0.0), Point(55.0, 0.0))
      val administrativeClass = Municipality
      val roadLink = Seq(RoadLink(1000l, geometry1, GeometryUtils.geometryLength(geometry1), administrativeClass, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode)),
        RoadLink(1001l, geometry2, GeometryUtils.geometryLength(geometry2), administrativeClass, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode)),
        RoadLink(1002l, geometry3, GeometryUtils.geometryLength(geometry3), administrativeClass, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode)))

      val test = service.getMassLimitationByRoadLinks(MassLimitationAssetTypes, roadLink)
      println("")
    }
  }

}
