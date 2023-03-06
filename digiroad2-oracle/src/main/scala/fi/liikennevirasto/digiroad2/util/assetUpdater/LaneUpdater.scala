package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType}
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType}
import fi.liikennevirasto.digiroad2.lane.LaneFiller.{ChangeSet, SideCodeAdjustment, VVHChangesAdjustment}
import fi.liikennevirasto.digiroad2.lane.{LaneFiller, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.{LinkId, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.util.MainLanePopulationProcess
import org.slf4j.LoggerFactory

class LaneUpdater(roadLinkChangeClient: RoadLinkChangeClient, roadLinkService: RoadLinkService, laneService: LaneService) {
  val username: String = "samuutus"
  private val logger = LoggerFactory.getLogger(getClass)
  def laneFiller: LaneFiller = new LaneFiller

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
    val addedRoadLinks = if(addedLinkIds.nonEmpty) {
      roadLinkService.getRoadLinksByLinkIds(addedLinkIds.toSet)
    }
    else Seq()

    //Add main lanes for completely new road links
    val createdMainLanes = MainLanePopulationProcess.createMainLanesForRoadLinks(addedRoadLinks)

    //Move lanes from deleted links to history
    val expiredLaneIds = expireLanesOnDeletedLinks(removedLaneIds.toSet)

    val initChangeSet = ChangeSet(expiredLaneIds = expiredLaneIds)

    // Project lanes to new replaced road links
    val lanesOnReplacementLinks = lanesOnChangedLinks.filter(lane => replacementChanges.flatMap(_.oldLink).map(_.linkId).contains(lane.linkId))


  }


  def fillNewRoadLinksWithPreviousAssetsData(roadLinks: Seq[RoadLink], historyRoadLinks: Seq[RoadLink], lanesToUpdate: Seq[PersistedLane],
                                             currentLanes: Seq[PersistedLane], changes: Seq[ChangeInfo], changeSet: ChangeSet) : (Seq[PersistedLane], ChangeSet) ={

    val (replacementChanges, otherChanges) = changes.partition( ChangeType.isReplacementChange)
    val reverseLookupMap = replacementChanges.filterNot(c=>c.oldId.isEmpty || c.newId.isEmpty).map(c => c.newId.get -> c).groupBy(_._1).mapValues(_.map(_._2))

    val extensionChanges = otherChanges.filter(ChangeType.isExtensionChange).flatMap(
      ext => reverseLookupMap.getOrElse(ext.newId.getOrElse(LinkId.Unknown.value), Seq()).flatMap(
        rep => addSourceRoadLinkToChangeInfo(ext, rep)))

    val fullChanges = extensionChanges ++ replacementChanges
    val projections = mapReplacementProjections(lanesToUpdate, currentLanes, roadLinks, fullChanges).filterNot(p => p._2._1.isEmpty || p._2._2.isEmpty)

    val (projectedLanesMapped, newChangeSet) = projections.foldLeft((Seq.empty[Option[PersistedLane]], changeSet)) {
      case ((persistedAssets, cs), (asset, (Some(roadLink), Some(projection)))) =>
        val historyRoadLink = historyRoadLinks.find(_.linkId == asset.linkId)
        val relevantChange = fullChanges.find(_.newId.contains(roadLink.linkId))
        relevantChange match {
          case Some(change) =>
            val (linearAsset, changes) = projectLinearAsset(asset, roadLink, historyRoadLink, projection, cs, change)
            (persistedAssets ++ Seq(linearAsset), changes)
          case _ => (Seq.empty[Option[PersistedLane]], changeSet)
        }
      case _ => (Seq.empty[Option[PersistedLane]], changeSet)
    }

    val projectedLanes = projectedLanesMapped.flatten
    (projectedLanes, newChangeSet)
  }

  def projectLinearAsset(lane: PersistedLane, targetRoadLink: RoadLink, historyRoadLink: Option[RoadLink], projection: Projection, changedSet: ChangeSet, change: ChangeInfo) : (Option[PersistedLane], ChangeSet)= {
    val newLinkId = targetRoadLink.linkId
    val laneId = lane.linkId match {
      case targetRoadLink.linkId => lane.id
      case _ => 0
    }
    val typed = ChangeType.apply(change.changeType)

    val (newStart, newEnd, newSideCode) = typed match {
      case ChangeType.LengthenedCommonPart | ChangeType.LengthenedNewPart | ChangeType.ReplacedNewPart =>
        laneFiller.calculateNewMValuesAndSideCode(lane, historyRoadLink, projection, targetRoadLink.length, true)
      case ChangeType.DividedModifiedPart | ChangeType.DividedNewPart if (lane.endMeasure < projection.oldStart ||
        lane.startMeasure > projection.oldEnd) =>
        (0.0, 0.0, 99)
      case _ =>
        laneFiller.calculateNewMValuesAndSideCode(lane, historyRoadLink, projection, targetRoadLink.length)
    }
    val projectedLane = Some(PersistedLane(laneId, newLinkId, newSideCode, lane.laneCode, lane.municipalityCode, newStart, newEnd, lane.createdBy,
      lane.createdDateTime, lane.modifiedBy, lane.modifiedDateTime, lane.expiredBy, lane.expiredDateTime,
      expired = false, projection.timeStamp, lane.geomModifiedDate, lane.attributes))


    val changeSet = laneId match {
      case 0 => changedSet
      case _ if(newSideCode != lane.sideCode) => changedSet.copy(adjustedVVHChanges =  changedSet.adjustedVVHChanges ++
        Seq(VVHChangesAdjustment(laneId, newLinkId, newStart, newEnd, projection.timeStamp)),
        adjustedSideCodes = changedSet.adjustedSideCodes ++ Seq(SideCodeAdjustment(laneId, SideCode.apply(newSideCode))))

      case _ => changedSet.copy(adjustedVVHChanges =  changedSet.adjustedVVHChanges ++
        Seq(VVHChangesAdjustment(laneId, newLinkId, newStart, newEnd, projection.timeStamp)))
    }

    (projectedLane, changeSet)

  }


  private def mapReplacementProjections(oldLinearAssets: Seq[PersistedLane], currentLinearAssets: Seq[PersistedLane], roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) : Seq[(PersistedLane, (Option[RoadLink], Option[Projection]))] = {

    val targetLinks = changes.flatMap(_.newId).toSet
    val newRoadLinks = roadLinks.filter(rl => targetLinks.contains(rl.linkId)).groupBy(_.linkId)
    val changeMap = changes.filterNot(c => c.newId.isEmpty || c.oldId.isEmpty).map(c => (c.oldId.get, c.newId.get)).groupBy(_._1)
    val targetRoadLinks = changeMap.mapValues(a => a.flatMap(b => newRoadLinks.getOrElse(b._2, Seq())).distinct)
    val groupedLinearAssets = currentLinearAssets.groupBy(_.linkId)
    val groupedOldLinearAssets = oldLinearAssets.groupBy(_.linkId)
    oldLinearAssets.flatMap{asset =>
      targetRoadLinks.getOrElse(asset.linkId, Seq()).map(newRoadLink =>
        (asset,
          getRoadLinkAndProjection(roadLinks, changes, asset.linkId, newRoadLink.linkId, groupedOldLinearAssets, groupedLinearAssets))
      )}
  }

  private def getRoadLinkAndProjection(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], oldId: String, newId: String,
                                       linearAssetsToUpdate: Map[String, Seq[PersistedLane]],
                                       currentLinearAssets: Map[String, Seq[PersistedLane]]): (Option[RoadLink], Option[Projection]) = {
    val roadLink = roadLinks.find(rl => newId == rl.linkId)
    val changeInfo = changes.find(c => c.oldId.getOrElse(LinkId.Unknown.value) == oldId && c.newId.getOrElse(LinkId.Unknown.value) == newId)
    val projection = changeInfo match {
      case Some(changedPart) =>
        // ChangeInfo object related assets; either mentioned in oldId or in newId
        val linearAssets = (linearAssetsToUpdate.getOrElse(changedPart.oldId.getOrElse(LinkId.Unknown.value), Seq()) ++
          currentLinearAssets.getOrElse(changedPart.newId.getOrElse(LinkId.Unknown.value), Seq())).distinct
        mapChangeToProjection(changedPart, linearAssets)
      case _ => None
    }
    (roadLink,projection)
  }

  private def mapChangeToProjection(change: ChangeInfo, linearAssets: Seq[PersistedLane]): Option[Projection] = {
    val typed = ChangeType.apply(change.changeType)
    typed match {
      // cases 5, 6, 1, 2
      case ChangeType.DividedModifiedPart  | ChangeType.DividedNewPart | ChangeType.CombinedModifiedPart |
           ChangeType.CombinedRemovedPart => projectAssetsConditionally(change, linearAssets, testNoAssetExistsOnTarget, useOldId=false)
      // cases 3, 7, 13, 14
      case ChangeType.LengthenedCommonPart | ChangeType.ShortenedCommonPart | ChangeType.ReplacedCommonPart |
           ChangeType.ReplacedNewPart =>
        projectAssetsConditionally(change, linearAssets, testAssetOutdated, useOldId=false)

      case ChangeType.LengthenedNewPart | ChangeType.ReplacedNewPart =>
        projectAssetsConditionally(change, linearAssets, testAssetsContainSegment, useOldId=true)
      case _ =>
        None
    }
  }

  private def testNoAssetExistsOnTarget(lanes: Seq[PersistedLane], linkId: String, mStart: Double, mEnd: Double,
                                        timeStamp: Long): Boolean = {
    !lanes.exists(l => l.linkId == linkId && GeometryUtils.overlaps((l.startMeasure,l.endMeasure),(mStart,mEnd)))
  }

  private def testAssetOutdated(lanes: Seq[PersistedLane], linkId: String, mStart: Double, mEnd: Double,
                                timeStamp: Long): Boolean = {
    val targetLanes = lanes.filter(a => a.linkId == linkId)
    targetLanes.nonEmpty && !targetLanes.exists(a => a.timeStamp >= timeStamp)
  }

  private def projectAssetsConditionally(change: ChangeInfo, lanes: Seq[PersistedLane],
                                         condition: (Seq[PersistedLane], String, Double, Double, Long) => Boolean,
                                         useOldId: Boolean): Option[Projection] = {
    val id = if (useOldId) {
      change.oldId
    } else {
      change.newId
    }

    (id, change.oldStartMeasure, change.oldEndMeasure, change.newStartMeasure, change.newEndMeasure, change.timeStamp) match {
      case (Some(targetId), Some(oldStart:Double), Some(oldEnd:Double),
      Some(newStart:Double), Some(newEnd:Double), timeStamp) =>

        if (condition(lanes, targetId, oldStart, oldEnd, timeStamp)) {
          Some(Projection(oldStart, oldEnd, newStart, newEnd, timeStamp))
        } else {
          None
        }

      case _ => None
    }
  }

  private def testAssetsContainSegment(lanes: Seq[PersistedLane], linkId: String, mStart: Double, mEnd: Double,
                                       timeStamp: Long): Boolean = {
    val targetAssets = lanes.filter(a => a.linkId == linkId)
    targetAssets.nonEmpty && !targetAssets.exists(a => a.timeStamp >= timeStamp) && targetAssets.exists(
      a => GeometryUtils.covered((a.startMeasure, a.endMeasure),(mStart,mEnd)))
  }

  private def addSourceRoadLinkToChangeInfo(extensionChangeInfo: ChangeInfo, replacementChangeInfo: ChangeInfo) = {
    def givenAndEqualDoubles(v1: Option[Double], v2: Option[Double]) = {
      (v1, v2) match {
        case (Some(d1), Some(d2)) => d1 == d2
        case _ => false
      }
    }

    // Test if these change infos extend each other. Then take the small little piece just after tolerance value to test if it is true there
    val (mStart, mEnd) = (givenAndEqualDoubles(replacementChangeInfo.newStartMeasure, extensionChangeInfo.newEndMeasure),
      givenAndEqualDoubles(replacementChangeInfo.newEndMeasure, extensionChangeInfo.newStartMeasure)) match {
      case (true, false) =>
        (replacementChangeInfo.oldStartMeasure.get + laneFiller.AllowedTolerance,
          replacementChangeInfo.oldStartMeasure.get + laneFiller.AllowedTolerance + laneFiller.MaxAllowedError)
      case (false, true) =>
        (Math.max(0.0, replacementChangeInfo.oldEndMeasure.get - laneFiller.AllowedTolerance - laneFiller.MaxAllowedError),
          Math.max(0.0, replacementChangeInfo.oldEndMeasure.get - laneFiller.AllowedTolerance))
      case (_, _) => (0.0, 0.0)
    }

    if (mStart != mEnd && extensionChangeInfo.timeStamp == replacementChangeInfo.timeStamp)
      Option(extensionChangeInfo.copy(oldId = replacementChangeInfo.oldId, oldStartMeasure = Option(mStart), oldEndMeasure = Option(mEnd)))
    else
      None
  }



}
