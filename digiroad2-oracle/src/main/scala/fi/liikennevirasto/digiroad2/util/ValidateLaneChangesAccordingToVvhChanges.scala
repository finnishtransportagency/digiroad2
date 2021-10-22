package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{CycleOrPedestrianPath, MotorwayServiceAccess, SideCode, SpecialTransportWithGate, SpecialTransportWithoutGate, TractorRoad}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.lane.{LaneNumberOneDigit, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

object ValidateLaneChangesAccordingToVvhChanges {

  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  lazy val laneService: LaneService = {
    new LaneService(roadLinkService, new DummyEventBus)
  }

  val logger = LoggerFactory.getLogger(getClass)

  def checkBothDirectionsConsistency(lanesOnLink: Seq[PersistedLane]): Seq[PersistedLane] = {
    val hasBothDirectionLanes = lanesOnLink.exists(_.sideCode == SideCode.BothDirections.value)
    val hasOneDirectionLanes = lanesOnLink.exists(_.sideCode == SideCode.TowardsDigitizing.value) || lanesOnLink.exists(_.sideCode == SideCode.AgainstDigitizing.value)

    if (hasBothDirectionLanes && hasOneDirectionLanes) lanesOnLink
    else Seq()
  }

  def checkLaneSideCodeConsistency(rl: RoadLink, lanesOnLink: Seq[PersistedLane]): Seq[PersistedLane] = {
    rl.trafficDirection match {

      case TowardsDigitizing => lanesOnLink.filter(_.sideCode != SideCode.TowardsDigitizing.value)

      case AgainstDigitizing => lanesOnLink.filter(_.sideCode != SideCode.AgainstDigitizing.value)

      case BothDirections => checkBothDirectionsConsistency(lanesOnLink)

      case _ => Seq()
    }
  }

  def checkLinksMainLanes(roadLink: RoadLink, mainLanes: Seq[PersistedLane]): Option[RoadLink] = {
    roadLink.trafficDirection match {

      case BothDirections => roadLink.linkType match {
        case MotorwayServiceAccess | SpecialTransportWithoutGate | SpecialTransportWithGate | CycleOrPedestrianPath | TractorRoad
          if mainLanes.size != 1 => Some(roadLink)

        case _ => if (mainLanes.size != 2) Some(roadLink)
        else None
      }

      case TowardsDigitizing | AgainstDigitizing if mainLanes.size != 1 => Some(roadLink)

      case _ => None
    }

  }

  def checkForDuplicateLanes(rl: RoadLink, lanesOnLink: Seq[PersistedLane]): Seq[PersistedLane] = {
    val duplicateLanes = for (laneToCompare <- lanesOnLink) yield
      lanesOnLink.filter(lane => (lane.laneCode == laneToCompare.laneCode) && (lane.sideCode == laneToCompare.sideCode) && lane.id != laneToCompare.id)

    duplicateLanes.flatten
  }

  def validateMainLaneAmount(roadLinks: Seq[RoadLink], mainLanesOnRoadLinks: Seq[PersistedLane]): Seq[RoadLink] = {
    val roadLinksWithInvalidAmount = for (rl <- roadLinks) yield
      checkLinksMainLanes(rl, mainLanesOnRoadLinks.filter(mainLane => mainLane.linkId == rl.linkId))

    roadLinksWithInvalidAmount.flatten
  }

  def validateLaneSideCodeConsistency(roadLinks: Seq[RoadLink], lanes: Seq[PersistedLane]): Seq[PersistedLane] = {
    val lanesWithInconsistentSideCodes = for (rl <- roadLinks) yield checkLaneSideCodeConsistency(rl, lanes.filter(_.linkId == rl.linkId))
    lanesWithInconsistentSideCodes.flatten
  }

  def validateForDuplicateLanes(roadLinks: Seq[RoadLink], lanes: Seq[PersistedLane]): Seq[Seq[PersistedLane]] = {
    val DuplicateLanes = for (rl <- roadLinks) yield checkForDuplicateLanes(rl, lanes.filter(_.linkId == rl.linkId))
    DuplicateLanes.filterNot(_.isEmpty)
  }

  //Process to find any errors on lanes caused by ChangeLanesAccordingToVVHChanges.
  //Runs a series of validations on lanes located on changed roadlinks
  def process(): Unit = {
    val since = DateTime.now().minusDays(2)
    val until = DateTime.now()

    logger.info("Getting changed links Since: " + since + " Until: " + until)

    val roadLinks = LogUtils.time(logger, "Get changed roadlinks")(
      roadLinkService.getChanged(since, until).map(_.link)
    )
    val roadLinkIds = roadLinks.map(_.linkId)
    val allLanesOnRoadLinks = laneService.fetchAllLanesByLinkIds(roadLinkIds)
    val mainLanesOnRoadLinks = allLanesOnRoadLinks.filter(lane => lane.laneCode == LaneNumberOneDigit.MainLane.laneCode)

    val duplicateLanes = validateForDuplicateLanes(roadLinks, allLanesOnRoadLinks)
    val roadLinksWithInvalidAmountOfMl = validateMainLaneAmount(roadLinks, mainLanesOnRoadLinks)
    val lanesWithInconsistentSideCodes = validateLaneSideCodeConsistency(roadLinks, allLanesOnRoadLinks)

    logger.info("Duplicate lanes: " + duplicateLanes.flatten.map(_.id) + "\n" +
      "Roadlinks with invalid amount of main lanes: " + roadLinksWithInvalidAmountOfMl.map(_.linkId) + "\n" +
      "Lanes with inconsistent side codes: " + lanesWithInconsistentSideCodes.map(_.id))


  }

}
