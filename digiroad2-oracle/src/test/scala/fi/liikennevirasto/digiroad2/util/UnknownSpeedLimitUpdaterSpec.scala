package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.linearasset.{NewLimit, RoadLink, SpeedLimitValue, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.{DummyEventBus, GeometryUtils}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class UnknownSpeedLimitUpdaterSpec extends FunSuite with Matchers {

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  val testUnknownSpeedLimitUpdater = new UnknownSpeedLimitUpdater() {
    override val roadLinkService: RoadLinkService = mockRoadLinkService
    val speedLimitService: SpeedLimitService = new SpeedLimitService(new DummyEventBus, mockRoadLinkService) {
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

  test("Remove unknown speed limits that have been replaced by an ordinary speed limit") {
    val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
    val municipalityCode = 235

    val roadLink = RoadLinkFetched(linkId1, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)

    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId1)).thenReturn(Some(roadLink))

    runWithRollback {
      testUnknownSpeedLimitUpdater.dao.persistUnknownSpeedLimits(Seq(UnknownSpeedLimit(linkId1, municipalityCode, Municipality), UnknownSpeedLimit(linkId2, municipalityCode, Municipality), UnknownSpeedLimit(linkId3, 235, Municipality)))
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId1, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.BothDirections)
      val unknownLimitsBefore = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode).map(_._1)
      unknownLimitsBefore.sorted should be (Seq(linkId1, linkId2, linkId3).sorted)
      testUnknownSpeedLimitUpdater.removeByMunicipality(municipalityCode)
      val unknownLimitsAfter = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode).map(_._1)
      unknownLimitsAfter.sorted should be (Seq(linkId2, linkId3).sorted)
    }
  }

  test("Remove unknown speed limit only if speed limits cover both sides of a two way road link") {
    val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
    val municipalityCode = 235

    val roadLink1 = RoadLinkFetched(linkId1, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLink2 = RoadLinkFetched(linkId2, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLink3 = RoadLinkFetched(linkId3, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)

    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId1)).thenReturn(Some(roadLink1))
    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId2)).thenReturn(Some(roadLink2))
    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId3)).thenReturn(Some(roadLink3))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId1, linkId2, linkId3)))
      .thenReturn(Seq(toRoadLink(roadLink1), toRoadLink(roadLink2), toRoadLink(roadLink3)))

    runWithRollback {
      testUnknownSpeedLimitUpdater.dao.persistUnknownSpeedLimits(Seq(UnknownSpeedLimit(linkId1, municipalityCode, Municipality), UnknownSpeedLimit(linkId2, municipalityCode, Municipality), UnknownSpeedLimit(linkId3, municipalityCode, Municipality)))
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId1, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.BothDirections)
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId2, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.AgainstDigitizing)
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId2, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.TowardsDigitizing)
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId3, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.AgainstDigitizing)
      val unknownLimitsBefore = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode).map(_._1)
      unknownLimitsBefore.sorted should be(Seq(linkId1, linkId2, linkId3).sorted)
      testUnknownSpeedLimitUpdater.removeByMunicipality(municipalityCode)
      val unknownLimitsAfter = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode).map(_._1)
      unknownLimitsAfter should be(Seq(linkId3))
    }
  }

  test("Unknown speed limit is removed if a one way road link is covered by a speed limit") {
    val linkId = LinkIdGenerator.generateRandom()
    val municipalityCode = 235

    val roadLink = RoadLinkFetched(linkId, municipalityCode, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers)

    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId)).thenReturn(Some(roadLink))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId)))
      .thenReturn(Seq(toRoadLink(roadLink)))

    runWithRollback {
      testUnknownSpeedLimitUpdater.dao.persistUnknownSpeedLimits(Seq(UnknownSpeedLimit(linkId, municipalityCode, Municipality)))
      testUnknownSpeedLimitUpdater.speedLimitService.createWithoutTransaction(Seq(NewLimit(linkId, 0.0, 8.0)), SpeedLimitValue(100), "test", SideCode.TowardsDigitizing)
      val unknownLimitsBefore = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode).map(_._1)
      unknownLimitsBefore should be(Seq(linkId))
      testUnknownSpeedLimitUpdater.removeByMunicipality(municipalityCode)
      val unknownLimitsAfter = testUnknownSpeedLimitUpdater.dao.getMunicipalitiesWithUnknown(municipalityCode)
      unknownLimitsAfter.sorted should be(Seq.empty)
    }
  }

  test("municipality codes ans admin classes of an unknown link are updated") {
    val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
    val municipalityCode = 235

    val roadLink1 = RoadLinkFetched(linkId1, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLink2 = RoadLinkFetched(linkId2, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLink3 = RoadLinkFetched(linkId3, municipalityCode, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)

    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId1)).thenReturn(Some(roadLink1))
    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId2)).thenReturn(Some(roadLink2))
    when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId3)).thenReturn(Some(roadLink3))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId2))).thenReturn(Seq(toRoadLink(roadLink2)))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId1, linkId3))).thenReturn(Seq(toRoadLink(roadLink1), toRoadLink(roadLink3)))

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
}
