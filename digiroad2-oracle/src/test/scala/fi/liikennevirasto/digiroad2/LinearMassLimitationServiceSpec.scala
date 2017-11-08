package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.masslimitation.oracle.OracleMassLimitationDao
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.jdbc.{StaticQuery => Q}

class LinearMassLimitationServiceSpec extends FunSuite with Matchers {
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
  val assetsTotalWeightLimits2 = Seq(PersistedLinearAsset(2l, 1002l, SideCode.TowardsDigitizing.value, Some(NumericValue(1)), 3.0, 5.0, None, None, None, None, false, TotalWeightLimits, 0, None, linkSource = NormalLinkInterface))

  val assetsTrailerTruckWeightLimits = Seq(PersistedLinearAsset(3l, 1000l, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 2.0, 6.0, None, None, None, None, false, TrailerTruckWeightLimits, 0, None, linkSource = NormalLinkInterface))
  val assetsTrailerTruckWeightLimits1 = Seq(PersistedLinearAsset(4l, 1001l, SideCode.AgainstDigitizing.value, Some(NumericValue(2)), 5.0, 8.0, None, None, None, None, false, TrailerTruckWeightLimits, 0, None, linkSource = NormalLinkInterface))

  val assetsAxleWeightLimits2 = Seq(PersistedLinearAsset(3l, 1002l, SideCode.BothDirections.value, Some(NumericValue(2)), 2.0, 4.0, None, None, None, None, false, AxleWeightLimits, 0, None, linkSource = NormalLinkInterface))
  val assetsAxleWeightLimits1 = Seq(PersistedLinearAsset(5l, 1001l, SideCode.AgainstDigitizing.value, Some(NumericValue(2)), 5.0, 8.0, None, None, None, None, false, AxleWeightLimits, 0, None, linkSource = NormalLinkInterface))

  val assetsBogieWeightLimits2 = Seq(PersistedLinearAsset(3l, 1002l, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 5.0, 9.0, None, None, None, None, false, BogieWeightLimits, 0, None, linkSource = NormalLinkInterface),
  PersistedLinearAsset(6l, 1002l, SideCode.TowardsDigitizing.value, Some(NumericValue(3)), 9.0, 20.0, None, None, None, None, false, BogieWeightLimits, 0, None, linkSource = NormalLinkInterface))

  val assets = assetsTotalWeightLimits ++ assetsTrailerTruckWeightLimits
  val assets1 = assetsTrailerTruckWeightLimits1 ++ assetsAxleWeightLimits1
  val assets2 = assetsTotalWeightLimits2 ++ assetsAxleWeightLimits2 ++ assetsBogieWeightLimits2
  when(mockMassLimitationDao.fetchLinearAssetsByLinkIds(MassLimitationAssetTypes, Seq(1000), LinearAssetTypes.numericValuePropertyId)).thenReturn(assets)
  when(mockMassLimitationDao.fetchLinearAssetsByLinkIds(MassLimitationAssetTypes, Seq(1001), LinearAssetTypes.numericValuePropertyId)).thenReturn(assets1)
  when(mockMassLimitationDao.fetchLinearAssetsByLinkIds(MassLimitationAssetTypes, Seq(1002), LinearAssetTypes.numericValuePropertyId)).thenReturn(assets2)
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test(" get assets with BothDirection split in TowardsDigitizing and AgainstDigitizing") {
    val service = new LinearMassLimitationService(mockRoadLinkService, mockMassLimitationDao)

    runWithRollback {
      val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
      val geometry = List(Point(0.0, 0.0), Point(10.0, 0.0))
      val administrativeClass = Municipality
      val roadLink = Seq(RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), administrativeClass, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode)))

      val test = service.getByRoadLinks(MassLimitationAssetTypes, roadLink)
      val roadLinkSide2 = test.filter(_.sideCode == SideCode.TowardsDigitizing.value)
      val roadLinkSide3 = test.filter(_.sideCode == SideCode.AgainstDigitizing.value)
      roadLinkSide2.size should be (1)
      roadLinkSide3.size should be (1)
      roadLinkSide2.map(_.geometry) should be (roadLinkSide3.map(_.geometry))
      roadLinkSide2.flatMap(_.geometry) should be (Seq(Point(1, 0), Point(6, 0)))
      val limitations = MassLimitationValue(Seq(AssetTypes(TotalWeightLimits,"1"), AssetTypes(TrailerTruckWeightLimits,"2")))
      val result2 = roadLinkSide2.flatMap(_.value).head.asInstanceOf[MassLimitationValue]
      limitations.massLimitation.foreach{
        limitation =>
          result2.massLimitation.contains(limitation) should be (true)
      }
    }
  }

  test(" get max length geometry when the same asset is split on the same road link ") {
    val service = new LinearMassLimitationService(mockRoadLinkService, mockMassLimitationDao)

    runWithRollback {
      val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
      val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
      val administrativeClass = Municipality
      val roadLink = Seq(RoadLink(1002l, geometry, GeometryUtils.geometryLength(geometry), administrativeClass, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode)))

      val test = service.getByRoadLinks(MassLimitationAssetTypes, roadLink)
      val roadLinkSide2 = test.filter(_.sideCode == SideCode.TowardsDigitizing.value)
      val roadLinkSide3 = test.filter(_.sideCode == SideCode.AgainstDigitizing.value)
      roadLinkSide2.map(_.geometry) should be (roadLinkSide3.map(_.geometry))
      roadLinkSide2.flatMap(_.geometry) should be (Seq(Point(2, 0), Point(20, 0)))
      roadLinkSide3.flatMap(_.value).head should be (MassLimitationValue(Seq(AssetTypes(AxleWeightLimits,"2"))))
      val limitations = MassLimitationValue(Seq(AssetTypes(TotalWeightLimits,"1"), AssetTypes(AxleWeightLimits,"2"), AssetTypes(BogieWeightLimits,"2"), AssetTypes(BogieWeightLimits,"3")))
      val result2 = roadLinkSide2.flatMap(_.value).head.asInstanceOf[MassLimitationValue]
      limitations.massLimitation.foreach{
        limitation =>
          result2.massLimitation.contains(limitation) should be (true)
      }
    }
  }

  test(" only create a list of mass Limitation for one sideCode (AgainstDigitizing) ") {
    val service = new LinearMassLimitationService(mockRoadLinkService, mockMassLimitationDao)

    runWithRollback {
      val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
      val geometry = List(Point(0.0, 0.0), Point(10.0, 0.0))
      val administrativeClass = Municipality
      val roadLink = Seq(RoadLink(1001l, geometry, GeometryUtils.geometryLength(geometry), administrativeClass, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode)))

      val test = service.getByRoadLinks(MassLimitationAssetTypes, roadLink)
      val roadLinkSide2 = test.filter(_.sideCode == SideCode.TowardsDigitizing.value)
      val roadLinkSide3 = test.filter(_.sideCode == SideCode.AgainstDigitizing.value)
      roadLinkSide2.size should be (0)
      roadLinkSide3.size should be (1)
      roadLinkSide3.flatMap(_.geometry) should be (Seq(Point(5, 0), Point(8, 0)))
      val result = roadLinkSide3.flatMap(_.value).head.asInstanceOf[MassLimitationValue]
      val limitations = MassLimitationValue(Seq(AssetTypes(AxleWeightLimits,"2"), AssetTypes(TrailerTruckWeightLimits,"2")))
      limitations.massLimitation.foreach{
        limitation =>
          result.massLimitation.contains(limitation) should be (true)
      }
    }
  }
}
