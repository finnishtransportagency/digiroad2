package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.lane.LaneFiller.{ChangeSet, baseAdjustment}
import fi.liikennevirasto.digiroad2.lane.{LaneFiller, NewLane, PersistedLane, PieceWiseLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.LaneUtils.laneService._
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.math.abs

object ChangeLanesAccordingToVvhChanges {

  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  val logger = LoggerFactory.getLogger(getClass)

  // Main process, gets roadlinks from VVH which have been changed in the past 24 hours.
  // handleChanges changes lanes on these roadlinks according to VVH change info
  def process(): Unit = {
    val since = DateTime.now().minusDays(1)
    val until = DateTime.now()

    logger.info("Getting changed links Since: " + since + " Until: " + until)
    val changes = roadLinkService.getChangeInfoByDates(since, until)
    val linkIds = (changes.flatMap(_.oldId) ++ changes.flatMap(_.newId)).toSet
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIds)
    val mappedChanges = LaneUtils.getMappedChanges(changes)
    val removedLinkIds = LaneUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    val existingAssets = fetchExistingLanesByLinkIds(roadLinks.map(_.linkId).distinct, removedLinkIds)

    val (filteredChangeSet, modifiedLanes) = handleChanges(roadLinks, changes, existingAssets)
    saveChanges(filteredChangeSet, modifiedLanes)
  }

  def handleChanges(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], existingAssets: Seq[PersistedLane]): (ChangeSet, Seq[PersistedLane]) = {
    val mappedChanges = LaneUtils.getMappedChanges(changes)
    val removedLinkIds = LaneUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)

    val timing = System.currentTimeMillis
    val (assetsOnChangedLinks, lanesWithoutChangedLinks) = existingAssets.partition(a => LaneUtils.newChangeInfoDetected(a, mappedChanges))

    val initChangeSet = ChangeSet(
      expiredLaneIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)) // Get only the assets marked to remove
        .map(_.id)                                             // Get only the Ids
        .toSet
        .filterNot( _ == 0L)                                   // Remove the new assets (ID == 0 )
    )

    val (projectedLanes, changedSet) = fillNewRoadLinksWithPreviousAssetsData(roadLinks, assetsOnChangedLinks, assetsOnChangedLinks, changes, initChangeSet)
    val newLanes = projectedLanes ++ lanesWithoutChangedLinks

    if (newLanes.nonEmpty) {
      logger.info("Finnish transfer %d assets at %d ms after start".format(newLanes.length, System.currentTimeMillis - timing))
    }

    val allLanes = assetsOnChangedLinks.filterNot(a => projectedLanes.exists(_.linkId == a.linkId)) ++ projectedLanes ++ lanesWithoutChangedLinks
    val groupedAssets = allLanes.groupBy(_.linkId)
    val (changedLanes, changeSet) = laneFiller.fillTopology(roadLinks, groupedAssets, Some(changedSet))
    val persistedChangedLanes = pieceWiseLanestoPersistedLane(changedLanes)

//    val changeSetLanes = changeSet.expiredLaneIds ++ changeSet.adjustedMValues.map(_.laneId) ++
//      changeSet.adjustedVVHChanges.map(_.laneId) ++ changeSet.adjustedSideCodes.map(_.laneId)
//    val generatedMappedById = changeSet.generatedPersistedLanes.groupBy(_.id)
//
//    val modifiedLanes = projectedLanes.filterNot(lane => {
//      val generatedLane = generatedMappedById.getOrElse(lane.id, Seq())
//      if (generatedLane.isEmpty) logger.info("No generated lane found for key: " + lane.id)
//      generatedLane.nonEmpty || changeSetLanes.contains(lane.id)
//    }) ++ changeSet.generatedPersistedLanes

    val changeSetFilteredExpired = filterExpiredIds(changeSet)
    (changeSetFilteredExpired, persistedChangedLanes)
  }

  def filterExpiredIds(set: ChangeSet): ChangeSet = {
    val expiredIds = set.expiredLaneIds
    val adjustedMValuesFiltered = set.adjustedMValues.filterNot(adj => expiredIds.contains(adj.laneId))
    val adjustedVVHChangesFiltered = set.adjustedVVHChanges.filterNot(adj => expiredIds.contains(adj.laneId))
    val adjustedSideCodesFiltered = set.adjustedSideCodes.filterNot(adj => expiredIds.contains(adj.laneId))
    val generatedPersistedLanesFiltered = set.generatedPersistedLanes.filterNot(lane => expiredIds.contains(lane.id))

    set.copy(adjustedMValues = adjustedMValuesFiltered, adjustedVVHChanges = adjustedVVHChangesFiltered,
      adjustedSideCodes = adjustedSideCodesFiltered, generatedPersistedLanes = generatedPersistedLanesFiltered)
  }

  def saveChanges(changeSet: ChangeSet, modifiedLanes: Seq[PersistedLane]): Unit ={
    updateChangeSet(changeSet)
    persistModifiedLinearAssets(modifiedLanes)
  }

  def updateChangeSet(changeSet: ChangeSet) : Unit = {
    def treatChangeSetData(changeSetToTreat: Seq[baseAdjustment]): Unit = {
      val toAdjustLanes = getPersistedLanesByIds(changeSetToTreat.map(_.laneId).toSet, false)

      changeSetToTreat.foreach { adjustment =>
        val oldLane = toAdjustLanes.find(_.id == adjustment.laneId)
        if(oldLane.nonEmpty){
          val newLane = persistedToNewLaneWithNewMeasures(oldLane.get, adjustment.startMeasure, adjustment.endMeasure)
          val newLaneID = create(Seq(newLane), Set(oldLane.get.linkId), oldLane.get.sideCode, VvhGenerated)
          moveToHistory(oldLane.get.id, Some(newLaneID.head), true, true, VvhGenerated)
        }
        else{
          logger.error("Old lane not found with ID: " + adjustment.laneId + " Adjustment link ID: " + adjustment.linkId)
        }

      }
    }

    def persistedToNewLaneWithNewMeasures(persistedLane: PersistedLane, newStartMeasure: Double, newEndMeasure: Double): NewLane = {
      NewLane(0, newStartMeasure, newEndMeasure, persistedLane.municipalityCode, false, false, persistedLane.attributes)
    }

    PostGISDatabase.withDynTransaction {
      if (changeSet.adjustedSideCodes.nonEmpty)
        logger.info("Saving SideCode adjustments for lane/link ids=" + changeSet.adjustedSideCodes.map(a => "" + a.laneId).mkString(", "))

      changeSet.adjustedSideCodes.foreach { adjustment =>
        moveToHistory(adjustment.laneId, None, false, false, VvhGenerated)
        dao.updateSideCode(adjustment.laneId, adjustment.sideCode.value, VvhGenerated)
      }

      if (changeSet.adjustedMValues.nonEmpty)
        logger.info("Saving adjustments for lane/link ids=" + changeSet.adjustedMValues.map(a => "" + a.laneId + "/" + a.linkId).mkString(", "))

      treatChangeSetData(changeSet.adjustedMValues)

      if (changeSet.adjustedVVHChanges.nonEmpty)
        logger.info("Saving adjustments for lane/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.laneId + "/" + a.linkId).mkString(", "))

      treatChangeSetData(changeSet.adjustedVVHChanges)

      val ids = changeSet.expiredLaneIds.toSeq
      if (ids.nonEmpty)
        logger.info("Expiring ids " + ids.mkString(", "))
      ids.foreach(moveToHistory(_, None, true, true, VvhGenerated))
    }
  }

  def persistModifiedLinearAssets(newLanes: Seq[PersistedLane]): Unit = {
    if (newLanes.nonEmpty) {
      logger.info("Saving modified lanes")

      val username = "modifiedLanes"
      val (toInsert, toUpdate) = newLanes.partition(_.id == 0L)

      PostGISDatabase.withDynTransaction {
        if(toUpdate.nonEmpty) {
          toUpdate.foreach{ lane =>
            moveToHistory(lane.id, None, false, false, username)
            dao.updateEntryLane(lane, username)
          }
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
        }
        if (toInsert.nonEmpty){
          toInsert.foreach(createWithoutTransaction(_ , username))
          logger.info("Added lanes for linkids " + toInsert.map(_.linkId))
        }
      }
    }
  }

}
