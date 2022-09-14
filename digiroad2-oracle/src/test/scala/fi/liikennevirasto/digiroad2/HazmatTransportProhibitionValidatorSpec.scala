package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Weekday
import fi.liikennevirasto.digiroad2.util.LinkIdGenerator
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class HazmatTransportProhibitionValidatorSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockLinearAssetDao: PostGISLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]

  class TestHazmatProhibitionValidator extends HazmatTransportProhibitionValidator {
    override lazy val dao: PostGISLinearAssetDao = mockLinearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  val prohibitionValidator = new TestHazmatProhibitionValidator
  
  val linkId1: String = LinkIdGenerator.generateRandom()
  val linkId2: String = LinkIdGenerator.generateRandom()
  val linkId3: String = LinkIdGenerator.generateRandom()
  val linkId4: String = LinkIdGenerator.generateRandom()

  test("prohibition traffic sign validation should return false") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(0.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(HazmatProhibitionA.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId2, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(linkId3))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId1, linkId2))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(linkId1, linkId2, linkId3), false))
        .thenReturn(Seq())

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should have size 1
      result.head.linkId should be(Some(linkId2))

      dynamicSession.rollback()
    }
  }

  test("prohibition traffic sign validation should find match asset") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(0.0, .0), Point(0, 10.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(0.0, 10.0), Point(0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(0.0, 20.0), Point(0.0, 30.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(linkId4, Seq(Point(0.0, 30.0), Point(0.0, 40.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(HazmatProhibitionA.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId2, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))

      val value = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null), ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null)))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(linkId1, linkId2, linkId3, linkId4), false))
        .thenReturn(Seq(PersistedLinearAsset(1, linkId4, 1, Some(value), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should have size 0

      dynamicSession.rollback()
    }
  }

  test("prohibition traffic validation should have all with asset") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(10.0, 0.0), Point(10, 10.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(10.0, 10.0), Point(10, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(10.0, 20.0), Point(0.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(linkId4, Seq(Point(10.0, 20.0), Point(20.0, 20.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(HazmatProhibitionA.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(linkId3), false)).thenReturn(Seq())

      val value = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null), ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null)))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(linkId1, linkId2, linkId3, linkId4), false))
        .thenReturn(Seq(PersistedLinearAsset(1, LinkIdGenerator.generateRandom(), 1, Some(value), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should have size 1
      result.head.linkId should be(Some(linkId1))

      dynamicSession.rollback()
    }
  }
}
