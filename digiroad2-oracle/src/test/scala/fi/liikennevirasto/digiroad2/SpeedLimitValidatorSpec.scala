package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.OracleUserProvider
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, RoadLink, SpeedLimit}
import fi.liikennevirasto.digiroad2.process.SpeedLimitValidator
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignTypeGroup.SpeedLimits
import fi.liikennevirasto.digiroad2.util.TestTransactions
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
  val simpleProp70 = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue("1"))), Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("70"))))
  val simpleProp80 = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue("1"))), Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("80"))))

  test("add new inaccurate SpeedLimit when traffic sign inside the asset") {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      0.0, 20, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSign = Seq(PersistedTrafficSign(1, speedLimit.linkId, 10, 0, 5, false, 0, 235, simpleProp80, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadLink).getOrElse(0)
    inaccurateId should be (speedLimit)
  }

  test("when exist more than one trafficSign on same linkId and the value of one doesn't match") {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadlink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(70)), Seq(Point(0.0, 0.0),
      Point(20, 0.0)), 0.0, 20, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

     val trafficSign = Seq(PersistedTrafficSign(1, speedLimit.linkId, 10, 0, 10, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface),
                          PersistedTrafficSign(2, speedLimit.linkId, 15, 0, 15, false, 0, 235, simpleProp80, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadlink).getOrElse(0)
    inaccurateId should be (speedLimit)
  }

  test("when traffic Signs has the same linkId out of asset length geometry and value doesn't match") {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      10.0, 20.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSign = Seq(PersistedTrafficSign(2, speedLimit.linkId, 5, 5, 5, false, 0, 235, simpleProp80, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)

    val speedLimitGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimitGeometry)
    when(mockTrafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits))).thenReturn(trafficSign)
    when(mockTrafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))).thenReturn(Seq())

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadLink).getOrElse(0)
    inaccurateId should be (speedLimit)
  }

  test("when traffic Signs has the same linkId but out of asset geometry")  {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      10, 20, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSign = Seq(PersistedTrafficSign(2, speedLimit.linkId, 9, 1, 9, false, 0, 235, simpleProp80, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)

    val speedLimitGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimitGeometry)

    when(mockTrafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits))).thenReturn(trafficSign)
    when(mockTrafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))).thenReturn(Seq())

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadLink).getOrElse(0)
    inaccurateId should be (speedLimit)
  }

  test("when traffic Signs has the same linkId but out of asset geometry choose the nearest trafficSign (begin asset)")  {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      10.0, 20, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSign = Seq(PersistedTrafficSign(1, speedLimit.linkId, 5, 5, 1, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface),
      PersistedTrafficSign(2, speedLimit.linkId, 9, 1, 9, false, 0, 235, simpleProp80, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)

    val speedLimitGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimitGeometry)

    when(mockTrafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits))).thenReturn(trafficSign)
    when(mockTrafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))).thenReturn(Seq())

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadLink).getOrElse(0)
    inaccurateId should be (speedLimit)
  }

  test("when traffic Signs has the same linkId but out of asset geometry choose the nearest trafficSign") {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      10.0, 20, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSignFirst = Seq(PersistedTrafficSign(1, speedLimit.linkId, 5, 5, 1, false, 0, 235, simpleProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
      PersistedTrafficSign(2, speedLimit.linkId, 8, 1, 9, false, 0, 235, simpleProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface))
    val trafficSignLast = Seq(PersistedTrafficSign(3, speedLimit.linkId, 22, 1, 1, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface),
      PersistedTrafficSign(4, speedLimit.linkId, 21, 1, 9, false, 0, 235, simpleProp80, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSignFirst ++ trafficSignLast)

    val speedLimitGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimitGeometry)

    when(mockTrafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits))).thenReturn(trafficSignFirst)
    when(mockTrafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))).thenReturn(trafficSignLast)

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadLink).getOrElse(0)
    inaccurateId should be (speedLimit)
  }

  test("should exclude traffic Signs with different direction") {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      0.0, 20, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSign = Seq(PersistedTrafficSign(1, speedLimit.linkId, 5, 5, 2, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface),
      PersistedTrafficSign(2, speedLimit.linkId, 5, 1, 2, false, 0, 235, simpleProp80, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadLink).getOrElse(0)
    inaccurateId should be (speedLimit)
  }

  test("should exclude traffic Signs with different direction byRadius") {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      1.0, 20, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    val trafficSign = Seq(PersistedTrafficSign(1, speedLimit.linkId, 5, 5, 1, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface),
      PersistedTrafficSign(2, speedLimit.linkId, 5, 1, 1, false, 0, 235, simpleProp80, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface))

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)

    val speedLimitGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimitGeometry)

    when(mockTrafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits))).thenReturn(trafficSign)
    when(mockTrafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))).thenReturn(Seq())

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadLink).getOrElse(0)
    inaccurateId should be (speedLimit)
  }

  test("without traffic Sign shouldn't return asset id") {
    val geometry = Seq(Point(0.0, 0.0), Point(20, 0.0))
    val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.TowardsDigitizing, Some(NumericValue(70)), Seq(Point(0.0, 0.0)),
      1.0, 20, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

    when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(Seq())

    val speedLimitGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimitGeometry)

    when(mockTrafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits))).thenReturn(Seq())
    when(mockTrafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))).thenReturn(Seq())

    val inaccurateId = validator.checkInaccurateSpeedLimitValues(speedLimit, roadLink).getOrElse(0)
    inaccurateId should be (0)
  }
}
