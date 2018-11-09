package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.OracleUserProvider
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, RoadLink, SpeedLimit}
import fi.liikennevirasto.digiroad2.process.SpeedLimitValidator
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class SpeedLimitValidatorSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]

  val validator = new SpeedLimitValidator(mockTrafficSignService)
  val simpleProp50 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("5")))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("50")))))
  val simpleProp70 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("1")))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("70")))))
  val speedLimitEndsProp70 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("2")))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("70")))))
  val urbanAreaEndsProp70 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("6")))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("70")))))
  val speedLimitAreaEndsProp70 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("4")))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("70")))))
  val simpleProp80 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("1")))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TrafficSignPropertyValue(TextPropertyValue("80")))))

  test("get inaccurate SpeedLimit when speed limit traffic sign value is different of the speed limit value") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      0.0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSign = Seq(PersistedTrafficSign(1, speedLimit.linkId, 100, 0, 50, false, 0, 235, simpleProp80, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSign, roadLink, Seq(speedLimit))
    inaccurateSpeedLimits.size should be (1)
    inaccurateSpeedLimits.head should be(speedLimit)
  }

  test("when existing two speed limit traffic signs at on speed limit, and one sign it's ok the other not") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      0.0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, speedLimit.linkId, 100, 0, 50, false, 0, 235, simpleProp80, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(2, speedLimit.linkId, 50, 0, 25, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, Seq(speedLimit))

    inaccurateSpeedLimits.size should be (1)
    inaccurateSpeedLimits.head should be(speedLimit)
  }

  test("when existing speed limit traffic signs, with different direction of the Speed limit asset") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      0.0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, speedLimit.linkId, 100, 0, 50, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, Seq(speedLimit))

    inaccurateSpeedLimits.size should be (1)
    inaccurateSpeedLimits.head should be(speedLimit)
  }

  test("when existing speed limit traffic sign, with valid asset") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      0.0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, speedLimit.linkId, 100, 0, 50, false, 0, 235, simpleProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, Seq(speedLimit))

    inaccurateSpeedLimits.size should be (0)
  }

  test("verify two Speed Limits in one road link, with a valid traffic sign for one of them") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
          0, 90, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
        SpeedLimit(2, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(80)), Seq(Point(0.0, 0.0)),
          90, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 50, false, 0, 235, simpleProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)

    inaccurateSpeedLimits.size should be(0)
  }

  test("validate Speed Limit when exist a urban area traffic sign") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
          0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 50, false, 0, 235, simpleProp50, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)
    inaccurateSpeedLimits.size should be(1)
    inaccurateSpeedLimits should be(speedLimitsSeq)
  }

  test("validate Speed Limit when exist a Speed limit ends traffic sign with different direction") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
          0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 50, false, 0, 235, speedLimitEndsProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)
    inaccurateSpeedLimits.size should be(0)
  }

  test("validate Speed Limit when exist a Speed limit ends traffic sign with valid conditions") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
          0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 50, false, 0, 235, speedLimitEndsProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)
    inaccurateSpeedLimits.size should be(1)
    inaccurateSpeedLimits should be(speedLimitsSeq)
  }

  test("validate Speed Limit when exist a Speed limit ends traffic sign with valid conditions but value different") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(40)), Seq(Point(0.0, 0.0)),
          0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 50, false, 0, 235, speedLimitEndsProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)
    inaccurateSpeedLimits.size should be(1)
    inaccurateSpeedLimits should be(speedLimitsSeq)
  }

  test("validate Speed Limit when exist a Urban area ends traffic sign with valid conditions and value different of 80") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(40)), Seq(Point(0.0, 0.0)),
          0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 50, false, 0, 235, urbanAreaEndsProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)
    inaccurateSpeedLimits.size should be(1)
    inaccurateSpeedLimits should be(speedLimitsSeq)
  }

  test("validate Speed Limit when exist a Speed limit area ends traffic sign with valid values but at 30 meters or less of the beginning of road link") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
          0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 20, false, 0, 235, speedLimitAreaEndsProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)
    inaccurateSpeedLimits.size should be(0)
  }

  test("validate Speed Limit when exist a Speed limit area ends traffic sign with valid values but at 30 meters or less of the end of road link") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
          0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 190, false, 0, 235, speedLimitAreaEndsProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)
    inaccurateSpeedLimits.size should be(0)
  }

  test("validate Speed Limit when exist a Speed limit area ends traffic sign with valid values") {
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val linkIdForTests = 1000

    val speedLimitsSeq =
      Seq(
        SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
          0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
      )

    val trafficSigns =
      Seq(
        PersistedTrafficSign(1, linkIdForTests, 80, 0, 50, false, 0, 235, speedLimitAreaEndsProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      )

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkIdForTests)).thenReturn(trafficSigns)

    val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimitsSeq)
    inaccurateSpeedLimits.size should be(1)
    inaccurateSpeedLimits should be(speedLimitsSeq)
  }
}
