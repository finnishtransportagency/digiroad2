package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import org.mockito.ArgumentMatchers.any

class SevenRestrictionsLimitationValidatorSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockLinearAssetDao: OracleLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]

  case class SevenRestrictionsValidation(typeId: Int, service: SevenRestrictionsLimitationValidator, trafficSign: TrafficSignType, value: String )
  class TestWidthLimitValidator extends WidthLimitValidator {
    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  class TestHeightLimitValidator extends HeightLimitValidator {
    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  class TestTotalWeightLimitValidator extends TotalWeightLimitValidator {
    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  class TestTrailerTruckWeightLimitValidator extends TrailerTruckWeightLimitValidator {
    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  class TestAxleWeightLimitValidator extends AxleWeightLimitValidator {
    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  class TestBogieWeightLimitValidator extends BogieWeightLimitValidator {
    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  class TestLengthLimitValidator extends LengthLimitValidator {
    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  val widthLimitValidator = new TestWidthLimitValidator
  val heightLimitValidator = new TestHeightLimitValidator
  val totalWeightLimitValidator = new TestTotalWeightLimitValidator
  val trailerTruckWeightLimitValidator = new TestTrailerTruckWeightLimitValidator
  val axleWeightLimitValidator = new TestAxleWeightLimitValidator
  val bogieWeightLimitValidator = new TestBogieWeightLimitValidator
  val lengthLimitValidator = new TestLengthLimitValidator

  val sevenRestrictionsAssets = Seq(
    SevenRestrictionsValidation(WidthLimit.typeId, widthLimitValidator, TrafficSignType.FreeWidth, "200"),
    SevenRestrictionsValidation(WidthLimit.typeId, widthLimitValidator,  TrafficSignType.NoWidthExceeding, "100"),
    SevenRestrictionsValidation(HeightLimit.typeId, heightLimitValidator, TrafficSignType.MaxHeightExceeding, "100"),
    SevenRestrictionsValidation(TotalWeightLimit.typeId, totalWeightLimitValidator, TrafficSignType.MaxLadenExceeding, "100"),
    SevenRestrictionsValidation(TrailerTruckWeightLimit.typeId, trailerTruckWeightLimitValidator,TrafficSignType.MaxMassCombineVehiclesExceeding, "100"),
    SevenRestrictionsValidation(AxleWeightLimit.typeId, axleWeightLimitValidator, TrafficSignType.MaxTonsOneAxleExceeding, "100"),
    SevenRestrictionsValidation(BogieWeightLimit.typeId, bogieWeightLimitValidator, TrafficSignType.MaxTonsOnBogieExceeding, "100"),
    SevenRestrictionsValidation(LengthLimit.typeId, lengthLimitValidator, TrafficSignType.MaximumLength, "100")
  )

  val roadLink1 = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(10, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 0.0), Point(20, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink3 = RoadLink(1003l, Seq(Point(20.0, 0.0), Point(50.0, 0.0)), 30.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink4 = RoadLink(1004l, Seq(Point(50.0, 0.0), Point(70.0, 0.0)), 20.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

  when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

  def massLimitationWithoutMatchedAsset(sevenRestrictionsAsset: SevenRestrictionsValidation): Unit = {
    OracleDatabase.withDynTransaction {

      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.value.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(sevenRestrictionsAsset.service.dao.fetchLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l), LinearAssetTypes.numericValuePropertyId, false)).thenReturn(Seq())

      val result = sevenRestrictionsAsset.service.assetValidator(trafficSign)
      withClue("assetName " + AssetTypeInfo.apply(sevenRestrictionsAsset.typeId).toString) {
        result should have size 1
        result.head.linkId should be (Some(roadLink2.linkId))
      }

      dynamicSession.rollback()
    }
  }

  def massLimitationWithMatchedAsset(sevenRestrictionsAsset: SevenRestrictionsValidation): Unit = {
    OracleDatabase.withDynTransaction {
      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.value.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(sevenRestrictionsAsset.service.dao.fetchLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l), LinearAssetTypes.numericValuePropertyId, false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1003l, 1, Some(NumericValue(sevenRestrictionsAsset.value.toInt)), 0.4, 9.6, None, None, None, None, false, sevenRestrictionsAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = sevenRestrictionsAsset.service.assetValidator(trafficSign)
      withClue("assetName " + AssetTypeInfo.apply(sevenRestrictionsAsset.typeId).toString) {
        result should have size 0
      }

      dynamicSession.rollback()
    }
  }

  def massLimitationWithMismatchedAsset(sevenRestrictionsAsset: SevenRestrictionsValidation): Unit = {
    OracleDatabase.withDynTransaction {
      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.value.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("300"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("300"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(sevenRestrictionsAsset.service.dao.fetchLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l), LinearAssetTypes.numericValuePropertyId, false))
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
    OracleDatabase.withDynTransaction {
      val propTrafficSign = Seq(
        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sevenRestrictionsAsset.trafficSign.value.toString))),
        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))

      val trafficSign = PersistedTrafficSign(1, 1002l, 12, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(sevenRestrictionsAsset.service.dao.fetchLinearAssetsByLinkIds(sevenRestrictionsAsset.typeId, Seq(1001l,1002l, 1003l, 1004l), LinearAssetTypes.numericValuePropertyId, false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1004l, 1, Some(NumericValue(sevenRestrictionsAsset.value.toInt)), 13, 20, None, None, None, None, false, sevenRestrictionsAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

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

  test("sevenRestiction traffic sign without a match asset only after 50m") {
    sevenRestrictionsAssets.foreach { sevenRestrictionsAsset =>
      massLimitationWithMatchedAssetAfter50meter(sevenRestrictionsAsset)
    }
  }
}