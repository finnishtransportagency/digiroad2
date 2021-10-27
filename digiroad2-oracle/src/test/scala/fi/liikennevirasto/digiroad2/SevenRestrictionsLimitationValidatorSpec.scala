package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class SevenRestrictionsLimitationValidatorSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockDynamicAssetDao: DynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]

  case class SevenRestrictionsValidation(typeId: Int, service: SevenRestrictionsLimitationValidator, trafficSign: TrafficSignType, value: String )
  class TestWidthLimitValidator extends WidthLimitValidator {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override lazy val dynamicAssetDao: DynamicLinearAssetDao = mockDynamicAssetDao
  }

  class TestHeightLimitValidator extends HeightLimitValidator {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override lazy val dynamicAssetDao: DynamicLinearAssetDao = mockDynamicAssetDao
  }

  class TestTotalWeightLimitValidator extends TotalWeightLimitValidator {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override lazy val dynamicAssetDao: DynamicLinearAssetDao = mockDynamicAssetDao
  }

  class TestTrailerTruckWeightLimitValidator extends TrailerTruckWeightLimitValidator {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override lazy val dynamicAssetDao: DynamicLinearAssetDao = mockDynamicAssetDao
  }

  class TestAxleWeightLimitValidator extends AxleWeightLimitValidator {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override lazy val dynamicAssetDao: DynamicLinearAssetDao = mockDynamicAssetDao
  }

  class TestBogieWeightLimitValidator extends BogieWeightLimitValidator {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override lazy val dynamicAssetDao: DynamicLinearAssetDao = mockDynamicAssetDao
  }

  class TestLengthLimitValidator extends LengthLimitValidator {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override lazy val dynamicAssetDao: DynamicLinearAssetDao = mockDynamicAssetDao
  }

  val widthLimitValidator = new TestWidthLimitValidator
  val heightLimitValidator = new TestHeightLimitValidator
  val totalWeightLimitValidator = new TestTotalWeightLimitValidator
  val trailerTruckWeightLimitValidator = new TestTrailerTruckWeightLimitValidator
  val axleWeightLimitValidator = new TestAxleWeightLimitValidator
  val bogieWeightLimitValidator = new TestBogieWeightLimitValidator
  val lengthLimitValidator = new TestLengthLimitValidator

  val weightLimitAssets = Seq(
    SevenRestrictionsValidation(TotalWeightLimit.typeId, totalWeightLimitValidator, MaxLadenExceeding, "100"),
    SevenRestrictionsValidation(TrailerTruckWeightLimit.typeId, trailerTruckWeightLimitValidator,MaxMassCombineVehiclesExceeding, "100"),
    SevenRestrictionsValidation(AxleWeightLimit.typeId, axleWeightLimitValidator, MaxTonsOneAxleExceeding, "100"),
    SevenRestrictionsValidation(BogieWeightLimit.typeId, bogieWeightLimitValidator, MaxTonsOnBogieExceeding, "200")
  )

  val otherLimitAssets = Seq(
    SevenRestrictionsValidation(WidthLimit.typeId, widthLimitValidator, FreeWidth, "200"),
    SevenRestrictionsValidation(WidthLimit.typeId, widthLimitValidator,  NoWidthExceeding, "100"),
    SevenRestrictionsValidation(HeightLimit.typeId, heightLimitValidator, MaxHeightExceeding, "100"),
    SevenRestrictionsValidation(LengthLimit.typeId, lengthLimitValidator, MaximumLength, "100")
  )

  val sevenRestrictionsAssets = weightLimitAssets ++ otherLimitAssets

  val roadLink1 = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(10, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 0.0), Point(20, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink3 = RoadLink(1003l, Seq(Point(20.0, 0.0), Point(50.0, 0.0)), 30.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink4 = RoadLink(1004l, Seq(Point(50.0, 0.0), Point(70.0, 0.0)), 20.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

  when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

  def massLimitationWithoutMatchedAsset(sevenRestrictionsAsset: SevenRestrictionsValidation): Unit = {
    PostGISDatabase.withDynTransaction {

      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.OTHvalue.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(sevenRestrictionsAsset.service.dynamicAssetDao.fetchDynamicLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l), false)).thenReturn(Seq())

      val result = sevenRestrictionsAsset.service.assetValidator(trafficSign)
      withClue("assetName " + AssetTypeInfo.apply(sevenRestrictionsAsset.typeId).toString) {
        result should have size 1
        result.head.linkId should be (Some(roadLink2.linkId))
      }

      dynamicSession.rollback()
    }
  }

  def massLimitationWithMatchedAsset(sevenRestrictionsAsset: SevenRestrictionsValidation): Unit = {
    PostGISDatabase.withDynTransaction {
      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.OTHvalue.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(sevenRestrictionsAsset.service.dynamicAssetDao.fetchDynamicLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1003l, 1, Some(NumericValue(sevenRestrictionsAsset.value.toInt)), 0.4, 9.6, None, None, None, None, false, sevenRestrictionsAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = sevenRestrictionsAsset.service.assetValidator(trafficSign)
      withClue("assetName " + AssetTypeInfo.apply(sevenRestrictionsAsset.typeId).toString) {
        result should have size 1
      }

      dynamicSession.rollback()
    }
  }

  def massLimitationWithMismatchedAsset(sevenRestrictionsAsset: SevenRestrictionsValidation): Unit = {
    PostGISDatabase.withDynTransaction {
      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.OTHvalue.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("300"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("300"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(sevenRestrictionsAsset.service.dynamicAssetDao.fetchDynamicLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1003l, 1, Some(NumericValue(sevenRestrictionsAsset.value.toInt)), 0.4, 9.6, None, None, None, None, false, sevenRestrictionsAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = sevenRestrictionsAsset.service.assetValidator(trafficSign)
      withClue("assetName " + AssetTypeInfo.apply(sevenRestrictionsAsset.typeId).toString) {
        result should have size 1
        result.head.assetId should be (Some(1l))
      }

      dynamicSession.rollback()
    }
  }

  def massLimitationWithMatchedAssetAfter50meter(sevenRestrictionsAsset: SevenRestrictionsValidation): Unit = {
    PostGISDatabase.withDynTransaction {
      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.OTHvalue.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 12, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(sevenRestrictionsAsset.service.dynamicAssetDao.fetchDynamicLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l, 1004l),false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1004l, 1, Some(NumericValue(sevenRestrictionsAsset.value.toInt)), 13, 20, None, None, None, None, false, sevenRestrictionsAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = sevenRestrictionsAsset.service.assetValidator(trafficSign)
      withClue("assetName " + AssetTypeInfo.apply(sevenRestrictionsAsset.typeId).toString) {
        result should have size 1
        result.head.linkId should be (Some(roadLink2.linkId))
      }

      dynamicSession.rollback()
    }
  }

  def massLimitationWithMatchedAssetAfter500meter(sevenRestrictionsAsset: SevenRestrictionsValidation): Unit = {
    PostGISDatabase.withDynTransaction {

      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.OTHvalue.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 120, 0, 120, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val roadLink1 = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(100, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(100.0, 0.0), Point(200, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(200.0, 0.0), Point(500.0, 0.0)), 30.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(500.0, 0.0), Point(700.0, 0.0)), 20.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(sevenRestrictionsAsset.service.dynamicAssetDao.fetchDynamicLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l, 1004l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1004l, 1, Some(NumericValue(sevenRestrictionsAsset.value.toInt)), 121, 200, None, None, None, None, false, sevenRestrictionsAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = sevenRestrictionsAsset.service.assetValidator(trafficSign)
      withClue("assetName " + AssetTypeInfo.apply(sevenRestrictionsAsset.typeId).toString) {
        result should have size 1
        result.head.linkId should be (Some(roadLink2.linkId))
      }

      dynamicSession.rollback()
    }
  }

  test("sevenRestiction traffic sign without match asset") {
    sevenRestrictionsAssets.foreach { sevenRestrictionsAsset =>
      massLimitationWithoutMatchedAsset(sevenRestrictionsAsset)
    }
  }

  test("sevenRestiction traffic sign have a correct asset") {
    sevenRestrictionsAssets.foreach { sevenRestrictionsAsset =>
      massLimitationWithMatchedAsset(sevenRestrictionsAsset)
    }
  }

  test("sevenRestiction traffic sign have a mismatched asset") {
    sevenRestrictionsAssets.foreach { sevenRestrictionsAsset =>
      massLimitationWithMismatchedAsset(sevenRestrictionsAsset)
    }
  }

  test("weightLimitAssets traffic sign without a match asset before 500m") {
    weightLimitAssets.foreach { weightLimitLimitAsset =>
      massLimitationWithMatchedAssetAfter500meter(weightLimitLimitAsset)
    }
  }

  test("WidthLimit, heightLimit and lengthLimit traffic sign without a match asset before 50m") {
    otherLimitAssets.foreach { otherLimitAsset =>
      massLimitationWithMatchedAssetAfter50meter(otherLimitAsset)
    }
  }


  test("bogieWeightLimit traffic sign without additional have an asset with 2 and 3 axle") {
    PostGISDatabase.withDynTransaction {
      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(MaxTonsOnBogieExceeding.OTHvalue.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(bogieWeightLimitValidator.dynamicAssetDao.fetchDynamicLinearAssetsByLinkIds(BogieWeightLimit.typeId, Seq(1001l,1002l, 1003l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1003l, 1, Some(DynamicValue(DynamicAssetValue(Seq(DynamicProperty("bogie_weight_2_axel", "integer", false, Seq(DynamicPropertyValue(15000))), DynamicProperty("bogie_weight_3_axel", "integer", false, Seq(DynamicPropertyValue(16000))))))), 0.4, 9.6, None, None, None, None, false, BogieWeightLimit.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = bogieWeightLimitValidator.assetValidator(trafficSign)
      result should have size 1
      result.head.assetId should be (Some(1l))

      dynamicSession.rollback()
    }
  }

  test("bogieWeightLimit traffic sign with additional have an asset with 2 and 3 axle") {
    PostGISDatabase.withDynTransaction {
      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(MaxTonsOnBogieExceeding.OTHvalue.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("15000"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))),
      Property(2, "additional_panel", "", false, Seq(AdditionalPanel(AdditionalPanelWithText.OTHvalue, "3 - akseliselle telille 16 t", "", 1, "", 999, 999, 999))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(bogieWeightLimitValidator.dynamicAssetDao.fetchDynamicLinearAssetsByLinkIds(BogieWeightLimit.typeId, Seq(1001l,1002l, 1003l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1003l, 1, Some(DynamicValue(DynamicAssetValue(Seq(DynamicProperty("bogie_weight_2_axel", "integer", false, Seq(DynamicPropertyValue(15000))), DynamicProperty("bogie_weight_3_axel", "integer", false, Seq(DynamicPropertyValue(16000))))))), 0.4, 9.6, None, None, None, None, false, BogieWeightLimit.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = bogieWeightLimitValidator.assetValidator(trafficSign)
      result should have size 0

      dynamicSession.rollback()
    }
  }

}