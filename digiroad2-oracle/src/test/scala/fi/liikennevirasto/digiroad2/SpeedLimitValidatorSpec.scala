package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.OracleUserProvider
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, RoadLink, SpeedLimit, SpeedLimitValue}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.process.SpeedLimitValidator
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.ArgumentMatchers.any

class SpeedLimitValidatorSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockManoeuvreService = MockitoSugar.mock[ManoeuvreService]
  val mockProhibitionService = MockitoSugar.mock[ProhibitionService]

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  object testTrafficSignService extends TrafficSignService(mockRoadLinkService, new DummyEventBus){
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(dataSource)(test)
  val validator = new SpeedLimitValidator(testTrafficSignService)
  val simpleProp50 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue("5"))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TextPropertyValue("50"))))
  val simpleProp70 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue("1"))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TextPropertyValue("70"))))
  val speedLimitEndsProp70 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue("2"))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TextPropertyValue("70"))))
  val urbanAreaEndsProp70 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue("6"))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TextPropertyValue("70"))))
  val speedLimitAreaEndsProp70 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue("4"))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TextPropertyValue("70"))))
  val simpleProp80 = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue("1"))), TrafficSignProperty(1, "trafficSigns_value", "", false, Seq(TextPropertyValue("80"))))

  test("get inaccurate SpeedLimit when speed limit traffic sign value is different of the speed limit value") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
        0.0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

      val trafficSign = Seq(PersistedTrafficSign(1, speedLimit.linkId, 100, 0, 50, false, 0, 235, simpleProp80, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface))

      when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSign)
      val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSign, roadLink, Seq(speedLimit))
      inaccurateSpeedLimits.size should be(1)
      inaccurateSpeedLimits.head should be(speedLimit)
    }
  }

  test("when existing two speed limit traffic signs at on speed limit, and one sign it's ok the other not") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val speedLimit = SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
        0.0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

      val trafficSigns =
        Seq(
          PersistedTrafficSign(1, speedLimit.linkId, 100, 0, 50, false, 0, 235, simpleProp80, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface),
          PersistedTrafficSign(2, speedLimit.linkId, 50, 0, 25, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
        )

      when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSigns)

      val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, Seq(speedLimit))

      inaccurateSpeedLimits.size should be(1)
      inaccurateSpeedLimits.head should be(speedLimit)
    }
  }

  test("when existing speed limit traffic signs, with different direction of the Speed limit asset") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val speedLimit = SpeedLimit(1, 1000, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
        0.0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

      val trafficSigns =
        Seq(
          PersistedTrafficSign(1, speedLimit.linkId, 100, 0, 50, false, 0, 235, simpleProp70, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
        )

      when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSigns)

      val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, Seq(speedLimit))

      inaccurateSpeedLimits.size should be(1)
      inaccurateSpeedLimits.head should be(speedLimit)
    }
  }

  test("when existing speed limit traffic sign, with valid asset") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val speedLimit = SpeedLimit(1, 1000, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
        0.0, 200, None, None, None, None, 0, None, linkSource = NormalLinkInterface)

      val trafficSigns =
        Seq(
          PersistedTrafficSign(1, speedLimit.linkId, 100, 0, 50, false, 0, 235, simpleProp70, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
        )

      when(mockTrafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)).thenReturn(trafficSigns)

      val inaccurateSpeedLimits = validator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, Seq(speedLimit))

      inaccurateSpeedLimits.size should be(0)
    }
  }

  test("verify two Speed Limits in one road link, with a valid traffic sign for one of them") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
            0, 90, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
          SpeedLimit(2, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,80)), Seq(Point(0.0, 0.0)),
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
  }

  test("validate Speed Limit when exist a urban area traffic sign") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
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
  }

  test("validate Speed Limit when exist a Speed limit ends traffic sign with different direction") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
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
  }

  test("validate Speed Limit when exist a Speed limit ends traffic sign with valid conditions") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
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
  }

  test("validate Speed Limit when exist a Speed limit ends traffic sign with valid conditions but value different") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,40)), Seq(Point(0.0, 0.0)),
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
  }

  test("validate Speed Limit when exist a Urban area ends traffic sign with valid conditions and value different of 80") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,40)), Seq(Point(0.0, 0.0)),
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
  }

  test("validate Speed Limit when exist a Speed limit area ends traffic sign with valid values but at 30 meters or less of the beginning of road link") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
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
  }

  test("validate Speed Limit when exist a Speed limit area ends traffic sign with valid values but at 30 meters or less of the end of road link") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
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
  }

  test("validate Speed Limit when exist a Speed limit area ends traffic sign with valid values") {
    runWithRollback {
      val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
      val roadLink = RoadLink(1000l, geometry, GeometryUtils.geometryLength(geometry), State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val linkIdForTests = 1000

      val speedLimitsSeq =
        Seq(
          SpeedLimit(1, linkIdForTests, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(false,70)), Seq(Point(0.0, 0.0)),
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
}
