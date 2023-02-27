package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.util.MainLanePopulationProcess
import org.slf4j.LoggerFactory

class LaneUpdater(roadLinkChangeClient: RoadLinkChangeClient, roadLinkService: RoadLinkService, laneService: LaneService) {
  val username: String = "samuutus"
  private val logger = LoggerFactory.getLogger(getClass)

  def expireLanesOnDeletedLinks(laneIds: Set[Long]): Set[Long] = {
    laneIds.map(id => laneService.moveToHistory(id, None, expireHistoryLane = true, deleteFromLanes = true, username))
  }

  def updateLanes(): Unit = {
    val roadLinkChanges = roadLinkChangeClient.getRoadLinkChanges()
    handleChanges(roadLinkChanges)
  }

  def handleChanges(roadLinkChanges: Seq[RoadLinkChange]): Unit = {
    val newLinkIds = roadLinkChanges.flatMap(_.newLinks.map(_.linkId))
    val oldLinkIds = roadLinkChanges.flatMap(_.oldLink).map(_.linkId)

    val lanesOnChangedLinks = laneService.fetchExistingLanesByLinkIds(oldLinkIds)

    val deletionChanges = roadLinkChanges.filter(_.changeType == RoadLinkChangeType.Remove)
    val addChanges = roadLinkChanges.filter(_.changeType == RoadLinkChangeType.Add)
    val replacementChanges = roadLinkChanges.filter(_.changeType == RoadLinkChangeType.Replace)
    val splitChanges = roadLinkChanges.filter(_.changeType == RoadLinkChangeType.Split)

    val removedLinkIds = deletionChanges.map(_.oldLink.get.linkId)
    val removedLaneIds = lanesOnChangedLinks.filter(lane => removedLinkIds.contains(lane.linkId)).map(_.id)
    val addedLinkIds = addChanges.flatMap(_.newLinks.map(_.linkId))
    val addedRoadLinks = roadLinkService.getRoadLinksByLinkIds(addedLinkIds.toSet)

    //Move lanes from deleted links to history
    val expiredLaneIds = expireLanesOnDeletedLinks(removedLaneIds.toSet)

    //Add main lanes for completely new road links
    val createdMainLanes = MainLanePopulationProcess.createMainLanesForRoadLinks(addedRoadLinks)

    val lanesOnReplacementLinks = lanesOnChangedLinks.filter(lane => replacementChanges.flatMap(_.oldLink).map(_.linkId).contains(lane.linkId))


  }


}
