package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.lane.PersistedLane
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.util.ValidateLaneChangesAccordingToVvhChanges._
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.scalatest.{FunSuite, Matchers}

class ValidateLaneChangesAccordingToVvhChangesSpec extends FunSuite with Matchers {

  def createRoadLink(linkId: Long, trafficDirection: TrafficDirection, linkType: LinkType = Motorway): RoadLink ={
    val geometry = Seq(Point(0.0, 0.0), Point(200, 0.0))
    RoadLink(linkId, geometry, GeometryUtils.geometryLength(geometry), State, 1, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  }

  def createLane(id: Long, linkId: Long, sideCode: Int, laneCode: Int): PersistedLane = {
    PersistedLane(id,linkId, sideCode, laneCode, municipalityCode = 235, startMeasure = 0.0, endMeasure = 200.0, createdBy = Some("Test"), createdDateTime = None, modifiedBy = None, modifiedDateTime = None, expiredBy = None, expiredDateTime = None, expired = false, vvhTimeStamp = 0l, geomModifiedDate = None, attributes = Seq())
  }

  def createRoadLinks(): Seq[RoadLink] = {
    val rlBothDirections = createRoadLink(1, TrafficDirection.BothDirections)
    val rlAgainst = createRoadLink(2, TrafficDirection.AgainstDigitizing)
    val rlTowards = createRoadLink(3, TrafficDirection.TowardsDigitizing)
    val rlCyclingAndWalking = createRoadLink(4, TrafficDirection.BothDirections, CycleOrPedestrianPath)
    val rlSpecialTransport = createRoadLink(5, TrafficDirection.BothDirections, SpecialTransportWithGate)
    val rlUnknown = createRoadLink(6, TrafficDirection.BothDirections, UnknownLinkType)

    Seq(rlBothDirections, rlAgainst, rlTowards, rlCyclingAndWalking, rlSpecialTransport, rlUnknown)
  }

  def createLanes() : Seq[PersistedLane] = {
    val mainLaneTowards = createLane(1, 1, 2, 1)
    val mainLaneAgainst1 = createLane(2, 1, 3, 1)
    val mainLaneAgainst2 = createLane(3,1,3,1)

    val additionalLaneAgainstInconsistent = createLane(4, 3, 3, 2)
    val mainLaneOnCycling = createLane(5, 4, 1, 1)

    val mainLaneOnUnknown1 = createLane(6, 6, 1, 1)
    val mainLaneOnUnknown2 = createLane(7, 6, 1, 1)

    Seq(mainLaneTowards, mainLaneAgainst1, mainLaneAgainst2, mainLaneOnCycling, additionalLaneAgainstInconsistent, mainLaneOnUnknown1, mainLaneOnUnknown2)
  }

  test("Four duplicate lanes should be found") {
    val roadLinks = createRoadLinks()
    val lanes = createLanes()

    val duplicateLanes = validateForDuplicateLanes(roadLinks, lanes)
    duplicateLanes.size should equal(4)
  }

  test("Five roadlinks with invalid amount of mainlanes should be found"){
    val roadLinks = createRoadLinks()
    val lanes = createLanes()

    val roadLinksWithInvalidAmountOfMl = validateMainLaneAmount(roadLinks, lanes)
    roadLinksWithInvalidAmountOfMl.size should equal(4)
  }

  test("One inconsistent lane should be found"){
    val roadLinks = createRoadLinks()
    val lanes = createLanes()

    val inconsistentLanes = validateLaneSideCodeConsistency(roadLinks, lanes)
    inconsistentLanes.size should equal(1)
  }



}
