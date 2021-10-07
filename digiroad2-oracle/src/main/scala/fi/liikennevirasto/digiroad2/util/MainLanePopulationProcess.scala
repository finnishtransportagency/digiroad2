package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{CycleOrPedestrianPath, LinkType, PrimitiveRoad, SpecialTransportWithGate, SpecialTransportWithoutGate, TractorRoad, TrafficDirection, UnknownFunctionalClass, WalkingAndCyclingPath}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.toSideCode
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, LaneType, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import org.joda.time.DateTime

object MainLanePopulationProcess {

  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  lazy val laneService: LaneService = {
    new LaneService(roadLinkService, new DummyEventBus)
  }

  lazy val username = "auto_generated_lane"

  lazy val twoWayLanes: Seq[(Int, LinkType)] = Seq(
      (UnknownFunctionalClass.value, SpecialTransportWithoutGate),
      (UnknownFunctionalClass.value, SpecialTransportWithGate),
      (PrimitiveRoad.value, TractorRoad),
      (WalkingAndCyclingPath.value, CycleOrPedestrianPath))

  private def addMainLane(roadLink: RoadLink): PersistedLane = {
    val createdVVHTimeStamp = vvhClient.roadLinkData.createVVHTimeStamp()
    val sideCode = toSideCode(roadLink.trafficDirection).value
    val laneCode = 1
    val startMeasure = 0
    val endMeasure = roadLink.length

    val laneProperties = Seq(
      LaneProperty("lane_code", Seq(LanePropertyValue(laneCode))),
      LaneProperty("lane_type", Seq(LanePropertyValue(LaneType.Main.value)))
    )

    PersistedLane(0, roadLink.linkId, sideCode, laneCode, roadLink.municipalityCode, startMeasure, endMeasure,
      Some(username), Some(DateTime.now()), None, None, None, None, expired = false,
      createdVVHTimeStamp, None, laneProperties)
  }

  // Split main lanes applicable for both directions
  // Two way lanes allowed only for service openings, cycleOrPedestrianPath and TractorRoad
  private def splitLinksApplicableForBothDirections(roadLink: RoadLink): Seq[RoadLink] = {
    roadLink.trafficDirection match {
      case TrafficDirection.BothDirections if !twoWayLanes.contains((roadLink.functionalClass, roadLink.linkType)) =>
        Seq(roadLink.copy(trafficDirection = TrafficDirection.TowardsDigitizing),
            roadLink.copy(trafficDirection = TrafficDirection.AgainstDigitizing))
      case _ if twoWayLanes.contains((roadLink.functionalClass, roadLink.linkType)) =>
        Seq(roadLink.copy(trafficDirection = TrafficDirection.BothDirections))
      case _ =>
        Seq(roadLink)
    }
  }

  private def mainLanesForMunicipality(municipality: Int, initialProcessing: Boolean): Unit = {
    println("Working on municipality -> " + municipality)

    val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality)

    // If not initial process, filter out roadLinks that already have main lanes
    val roadLinksWithoutMainLanes =
      if (initialProcessing) roadLinks
      else roadLinks.filterNot(link => laneService.fetchExistingMainLanesByRoadLinks(roadLinks, Seq())
                                                  .exists(_.linkId == link.linkId))

    val municipalityMainLanes = roadLinksWithoutMainLanes.flatMap { roadLink =>
      splitLinksApplicableForBothDirections(roadLink).map { linkWithDirection =>
        addMainLane(linkWithDirection)
      }
    }
    PostGISDatabase.withDynTransaction(municipalityMainLanes.foreach(laneService.createWithoutTransaction(_, username)))
  }

  // Main process
  def process(initialProcessing: Boolean = false): Unit = {
    println("Start to populate main lanes from road links\n")
    println(DateTime.now())

    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach { municipality =>
      mainLanesForMunicipality(municipality, initialProcessing)
    }

    println("")
    println("Finished populating main lanes\n")
    println(DateTime.now())
  }

  // Moves all existing lanes to history and creates new main lanes from vvh roadlinks
  def initialProcess(): Unit = {
    println("Start to remove existing lanes\n")
    println(DateTime.now())

    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach { municipality =>
      println("Deleting lanes from municipality -> " + municipality)
      PostGISDatabase.withDynTransaction(laneService.expireAllMunicipalityLanes(municipality, username))
    }

    println("")
    println("Finished removing lanes\n")
    println(DateTime.now())

    // Populate main lanes from road links
    process(initialProcessing = true)
  }
}