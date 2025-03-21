package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.ConstructionType.{ExpiringSoon, Planned}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.FeatureClass.CarRoad_IIIa
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.linearasset.{NewLimit, RoadLink, SpeedLimitValue, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.{DummyEventBus, GeometryUtils, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class UnknownSpeedLimitUpdaterSpec extends FunSuite with Matchers {

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadLinkDAO = MockitoSugar.mock[RoadLinkDAO]

  val testUnknownSpeedLimitUpdater = new UnknownSpeedLimitUpdater() {
    override val roadLinkService: RoadLinkService = mockRoadLinkService
    override val roadLinkDAO: RoadLinkDAO = mockRoadLinkDAO
    override val speedLimitService: SpeedLimitService = new SpeedLimitService(new DummyEventBus, mockRoadLinkService) {
      override def create(newLimits: Seq[NewLimit], value: SpeedLimitValue, username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
        withDynTransaction {
          val createdIds = newLimits.flatMap { limit =>
            speedLimitDao.createSpeedLimit(username, limit.linkId, Measures(limit.startMeasure, limit.endMeasure), SideCode.BothDirections, value, createTimeStamp(), municipalityValidation)
          }
          createdIds
        }
      }
    }
  }

  def toRoadLink(l: RoadLinkFetched) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Remove unknown speed limits that have been replaced by an ordinary speed limit or are not located on car roads") {
    val linkIds = (1 to 9).map(_ => LinkIdGenerator.generateRandom())
    val municipalityCode = 235

    val carRoad1 = RoadLink(linkIds(0), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val carRoad2 = RoadLink(linkIds(1), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val walkingAndCycling = RoadLink(linkIds(2), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 8, BothDirections, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val specialTransPort = RoadLink(linkIds(3), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 8, BothDirections, SpecialTransportWithGate, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val privateRoad = RoadLink(linkIds(4), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, Private, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val plannedRoad = RoadLink(linkIds(5), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)), constructionType = Planned)
    val expiredRoad = RoadLinkFetched(linkIds(6), 235, Seq(Point(0.0, 0.0), Point(8.0, 0.0)), State, BothDirections, CarRoad_IIIa, None)
    val roadExpiringSoon = RoadLink(linkIds(7), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)), constructionType = ExpiringSoon)
    val tractorRoad = RoadLink(linkIds(8), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 8, BothDirections, TractorRoad, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val roadLinkFetched = RoadLinkFetched(linkIds(0), municipalityCode, Seq(Point(0.0, 0.0), Point(8.0, 0.0)), State, TrafficDirection.BothDirections, FeatureClass.CarRoad_IIIa)
    val roadLink = RoadLink(linkId = roadLinkFetched.linkId, geometry = roadLinkFetched.geometry, length = roadLinkFetched.length,
      administrativeClass = roadLinkFetched.administrativeClass, functionalClass = FunctionalClass3.value,
      trafficDirection = roadLinkFetched.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
      modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched.constructionType, linkSource = roadLinkFetched.linkSource)

    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkIds(0))).thenReturn(Some(roadLinkFetched))
    when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched))).thenReturn(Seq(roadLink))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(linkIds.toSet)).thenReturn(Seq(carRoad1, carRoad2, walkingAndCycling, specialTransPort, privateRoad, plannedRoad, roadExpiringSoon, tractorRoad))
    when(mockRoadLinkDAO.fetchExpiredByLinkIds(linkIds.toSet)).thenReturn(Seq(expiredRoad))

    runWithRollback {
      val unknowsToCreate = linkIds.map ( linkId => UnknownSpeedLimit(linkId, municipalityCode, State))
      testUnknownSpeedLimitUpdater.dao.persistUnknownSpeedLimits(unknowsToCreate)
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkIds(0), 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.BothDirections)
      val unknownLimitsBefore = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode).map(_._1)
      unknownLimitsBefore.sorted should be (linkIds.sorted)
      testUnknownSpeedLimitUpdater.removeByMunicipality(municipalityCode)
      val unknownLimitsAfter = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode).map(_._1)
      unknownLimitsAfter.sorted should be (Seq(linkIds(1)))
    }
  }

  test("municipality codes ans admin classes of an unknown link are updated") {
    val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
    val municipalityCode = 235

    val roadLinkFetched1 = RoadLinkFetched(linkId1, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinkFetched2 = RoadLinkFetched(linkId2, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinkFetched3 = RoadLinkFetched(linkId3, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)

    val roadLink1 = RoadLink(linkId = roadLinkFetched1.linkId, geometry = roadLinkFetched1.geometry, length = roadLinkFetched1.length,
      administrativeClass = roadLinkFetched1.administrativeClass, functionalClass = FunctionalClass3.value,
      trafficDirection = roadLinkFetched1.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
      modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched1.constructionType, linkSource = roadLinkFetched1.linkSource)

    val roadLink2 = RoadLink(linkId = roadLinkFetched2.linkId, geometry = roadLinkFetched2.geometry, length = roadLinkFetched2.length,
      administrativeClass = roadLinkFetched2.administrativeClass, functionalClass = FunctionalClass3.value,
      trafficDirection = roadLinkFetched2.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
      modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched2.constructionType, linkSource = roadLinkFetched2.linkSource)


    val roadLink3 = RoadLink(linkId = roadLinkFetched3.linkId, geometry = roadLinkFetched3.geometry, length = roadLinkFetched3.length,
      administrativeClass = roadLinkFetched3.administrativeClass, functionalClass = FunctionalClass3.value,
      trafficDirection = roadLinkFetched3.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
      modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched3.constructionType, linkSource = roadLinkFetched3.linkSource)


    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId1)).thenReturn(Some(roadLinkFetched1))
    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId2)).thenReturn(Some(roadLinkFetched2))
    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId3)).thenReturn(Some(roadLinkFetched3))
    when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched1))).thenReturn(Seq(roadLink1))
    when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched2))).thenReturn(Seq(roadLink2))
    when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched3))).thenReturn(Seq(roadLink3))

    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId2))).thenReturn(Seq(toRoadLink(roadLinkFetched2)))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId1, linkId3))).thenReturn(Seq(toRoadLink(roadLinkFetched1), toRoadLink(roadLinkFetched3)))

    runWithRollback {
      testUnknownSpeedLimitUpdater.dao.persistUnknownSpeedLimits(Seq(UnknownSpeedLimit(linkId1, 49, State), UnknownSpeedLimit(linkId2, municipalityCode, State), UnknownSpeedLimit(linkId3, 49, Municipality)))
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId1, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.BothDirections)
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId2, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.AgainstDigitizing)
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId2, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.TowardsDigitizing)
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId3, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.AgainstDigitizing)
      val unknownLimits235before = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode)
      unknownLimits235before.size should be(1)
      unknownLimits235before.head._2 should be(State.value)
      val unknownLimits49before = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(49)
      unknownLimits49before.size should be(2)
      unknownLimits49before.map(_._2).sorted should be(Seq(State.value, Municipality.value))
      testUnknownSpeedLimitUpdater.updateAdminClassAndMunicipalityCode(235)
      testUnknownSpeedLimitUpdater.updateAdminClassAndMunicipalityCode(49)
      val unknownLimits235after = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode)
      unknownLimits235after.map(_._1).sorted should be(Seq(linkId1, linkId2, linkId3).sorted)
      unknownLimits235after.map(_._2).toSet should be(Set(Municipality.value))
      val unknownLimits49 = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(49)
      unknownLimits49 should be(Seq.empty)
    }
  }

  test("Generate necessary unknown limits") {
    val linkIds = (1 to 8).map(_ => LinkIdGenerator.generateRandom())
    val municipalityCode = 235

    val carRoad1 = RoadLink(linkIds(0), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val carRoad2 = RoadLink(linkIds(1), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val walkingAndCycling = RoadLink(linkIds(2), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 8, BothDirections, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val specialTransPort = RoadLink(linkIds(3), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 8, BothDirections, SpecialTransportWithGate, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val privateRoad = RoadLink(linkIds(4), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, Private, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    val plannedRoad = RoadLink(linkIds(5), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)), constructionType = Planned)
    val roadExpiringSoon = RoadLink(linkIds(6), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 1, BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)), constructionType = ExpiringSoon)
    val tractorRoad = RoadLink(linkIds(7), Seq(Point(0.0, 0.0), Point(8.0, 0.0)), 8.0, State, 8, BothDirections, TractorRoad, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val roadLinkFetched = RoadLinkFetched(linkIds(0), municipalityCode, Seq(Point(0.0, 0.0), Point(8.0, 0.0)), State, TrafficDirection.BothDirections, FeatureClass.CarRoad_IIIa)
    val roadLink = RoadLink(linkId = roadLinkFetched.linkId, geometry = roadLinkFetched.geometry, length = roadLinkFetched.length,
      administrativeClass = roadLinkFetched.administrativeClass, functionalClass = FunctionalClass3.value,
      trafficDirection = roadLinkFetched.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
      modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched.constructionType, linkSource = roadLinkFetched.linkSource)


    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkIds(0))).thenReturn(Some(roadLinkFetched))
    when(mockRoadLinkService.getRoadLinksAndComplementaryLinksByMunicipality(municipalityCode)).thenReturn(Seq(carRoad1, carRoad2, walkingAndCycling, specialTransPort, privateRoad, plannedRoad, roadExpiringSoon, tractorRoad))
    when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched))).thenReturn(Seq(roadLink))

    runWithRollback {
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkIds(0), 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.BothDirections)
      val unknownLimitsBefore = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode)
      unknownLimitsBefore.isEmpty should be(true)
      testUnknownSpeedLimitUpdater.generateUnknownSpeedLimitsByMunicipality(municipalityCode)
      val unknownLimitsAfter = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode).map(_._1)
      unknownLimitsAfter.sorted should be(Seq(linkIds(1)))
    }
  }

}
