package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{CycleOrPedestrianPath, ServiceAccess, SideCode, SpecialTransportWithGate, SpecialTransportWithoutGate, TractorRoad}
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.lane.{LaneNumberOneDigit, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.math.abs

object ValidateLaneChangesAccordingToVvhChanges {

  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  lazy val roadAddressService: RoadAddressService = {
    val viiteClient = new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
    new RoadAddressService(viiteClient)
  }

  lazy val laneService: LaneService = {
    new LaneService(roadLinkService, new DummyEventBus, roadAddressService)
  }

  val logger = LoggerFactory.getLogger(getClass)

  def checkBothDirectionsConsistency(lanesOnLink: Seq[PersistedLane]): Seq[PersistedLane] = {
    val hasBothDirectionLanes = lanesOnLink.exists(_.sideCode == SideCode.BothDirections.value)
    val hasOneDirectionLanes = lanesOnLink.exists(_.sideCode == SideCode.TowardsDigitizing.value) ||
      lanesOnLink.exists(_.sideCode == SideCode.AgainstDigitizing.value)

    if (hasBothDirectionLanes && hasOneDirectionLanes) lanesOnLink
    else Seq()
  }

  def checkLaneSideCodeConsistency(roadLink: RoadLink, lanesOnLink: Seq[PersistedLane]): Seq[PersistedLane] = {
    roadLink.trafficDirection match {
      case TowardsDigitizing => lanesOnLink.filter(_.sideCode != SideCode.TowardsDigitizing.value)
      case AgainstDigitizing => lanesOnLink.filter(_.sideCode != SideCode.AgainstDigitizing.value)
      case BothDirections => checkBothDirectionsConsistency(lanesOnLink)
      case _ => Seq()
    }
  }

  def checkLinksMainLanes(roadLink: RoadLink, mainLanes: Seq[PersistedLane]): Option[RoadLink] = {
    roadLink.trafficDirection match {

      case BothDirections => roadLink.linkType match {
        case ServiceAccess | SpecialTransportWithoutGate | SpecialTransportWithGate | CycleOrPedestrianPath | TractorRoad  =>
          if (mainLanes.size != 1)  Some(roadLink)
          else None
        case _ => if (mainLanes.size != 2) Some(roadLink)
        else None
      }
      case TowardsDigitizing | AgainstDigitizing if mainLanes.size != 1 => Some(roadLink)
      case _ => None
    }

  }

  def checkForDuplicateLanes(lanesOnLink: Seq[PersistedLane]): Seq[PersistedLane] = {
    val duplicateLanes = for (laneToCompare <- lanesOnLink) yield
      lanesOnLink.filter(lane => (lane.laneCode == laneToCompare.laneCode) &&
        (lane.sideCode == laneToCompare.sideCode) && lane.id != laneToCompare.id)

    duplicateLanes.flatten
  }

  def validateMainLaneAmount(roadLinks: Seq[RoadLink], mainLanesOnRoadLinks: Seq[PersistedLane]): Seq[RoadLink] = {
    val lanesMapped = mainLanesOnRoadLinks.groupBy(_.linkId)
    val roadLinksWithoutMainLanes = roadLinks.filterNot(rl => lanesMapped.keys.toSeq.contains(rl.linkId))
    lanesMapped.flatMap(pair => {
      val roadLink = roadLinks.find(_.linkId == pair._1)
      val lanesOnLink = pair._2
      roadLink match {
        case Some(rl) => checkLinksMainLanes(rl, lanesOnLink)
      }
    }).toSeq ++ roadLinksWithoutMainLanes

  }

  def validateLaneSideCodeConsistency(roadLinks: Seq[RoadLink], lanes: Seq[PersistedLane]): Seq[PersistedLane] = {
    val lanesWithInconsistentSideCodes = for (roadLink <- roadLinks) yield
      checkLaneSideCodeConsistency(roadLink, lanes.filter(_.linkId == roadLink.linkId))
    lanesWithInconsistentSideCodes.flatten
  }

  def validateForDuplicateLanes(roadLinks: Seq[RoadLink], lanes: Seq[PersistedLane]): Seq[PersistedLane] = {
    val duplicateLanes = for (roadLink <- roadLinks) yield checkForDuplicateLanes(lanes.filter(_.linkId == roadLink.linkId))
    duplicateLanes.filterNot(_.isEmpty).flatten
  }

  def validateMainLaneLengths(roadLinks: Seq[RoadLink], lanes: Seq[PersistedLane]): Seq[PersistedLane] = {
    lanes.filter(lane => {
      val roadLink = roadLinks.find(_.linkId == lane.linkId)
      roadLink match {
        case Some(rl) =>
          val laneLength = lane.endMeasure - lane.startMeasure
          val difference = abs(rl.length - laneLength)
          difference > 1

        case _ => false
      }
    })
  }

  //Process to find any errors on lanes caused by ChangeLanesAccordingToVVHChanges.
  //Runs a series of validations on lanes located on changed roadlinks
  def process(): Unit = {
    val since = DateTime.now().minusDays(1)
    val until = DateTime.now()

    logger.info("Getting changed links Since: " + since + " Until: " + until)

    val roadLinks = LogUtils.time(logger, "Get changed roadlinks")(
      roadLinkService.getChanged(since, until).map(_.link)
    )

    logger.info(roadLinks.size + " roadlinks changed since " + since)

    val roadLinkIds = roadLinks.map(_.linkId)
    val allLanesOnRoadLinks = laneService.fetchAllLanesByLinkIds(roadLinkIds)
    val mainLanesOnRoadLinks = allLanesOnRoadLinks.filter(lane => lane.laneCode == LaneNumberOneDigit.MainLane.laneCode)

    val duplicateLanes = validateForDuplicateLanes(roadLinks, allLanesOnRoadLinks)
    val roadLinksWithInvalidAmountOfMl = validateMainLaneAmount(roadLinks, mainLanesOnRoadLinks)
    val lanesWithInconsistentSideCodes = validateLaneSideCodeConsistency(roadLinks, allLanesOnRoadLinks)
    val mainLanesWithInvalidLength = validateMainLaneLengths(roadLinks, mainLanesOnRoadLinks)

    logger.info("Duplicate lanes: " + duplicateLanes.map(_.id) + "\n" +
      "Roadlinks with invalid amount of main lanes: " + roadLinksWithInvalidAmountOfMl.map(_.linkId) + "\n" +
      "Lanes with inconsistent side codes: " + lanesWithInconsistentSideCodes.map(_.id)+ "\n" +
      "Main lanes with invalid length: " + mainLanesWithInvalidLength.map(_.id))
  }
}
