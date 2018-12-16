package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Weekday
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class HazmatTransportProhibitionValidatorSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockLinearAssetDao: OracleLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]

  class TestHazmatProhibitionValidator extends HazmatTransportProhibitionValidator {
    override lazy val dao: OracleLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  val prohibitionValidator = new TestHazmatProhibitionValidator

  test("prohibition traffic sign validation should return false") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(HazmatProhibitionA.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1003l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(1001l, 1002l, 1003l), false))
        .thenReturn(Seq())

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should have size 1
      result.head.linkId should be(Some(1002l))

      dynamicSession.rollback()
    }
  }

  test("prohibition traffic sign validation should find match asset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(0.0, .0), Point(0, 10.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(0.0, 20.0), Point(0.0, 30.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(0.0, 30.0), Point(0.0, 40.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(HazmatProhibitionA.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))

      val value = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null), ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null)))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(1001l, 1002l, 1003l, 1004l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1004l, 1, Some(value), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should have size 0

      dynamicSession.rollback()
    }
  }

  test("prohibition traffic validation should have all with asset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 0.0), Point(10, 10.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 10.0), Point(10, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 20.0), Point(0.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(10.0, 20.0), Point(20.0, 20.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(HazmatProhibitionA.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(1003l), false)).thenReturn(Seq())

      val value = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null), ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null)))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(1001l, 1002l, 1003l, 1004l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(value), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should have size 1
      result.head.linkId should be(Some(1001l))

      dynamicSession.rollback()
    }
  }
}
