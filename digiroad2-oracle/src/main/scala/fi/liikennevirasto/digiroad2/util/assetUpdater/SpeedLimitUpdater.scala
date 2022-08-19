package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.UnknownLinkType
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.New
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType, RoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.slf4j.LoggerFactory

class SpeedLimitUpdater(eventbus: DigiroadEventBus, roadLinkClient: RoadLinkClient, roadLinkService: RoadLinkService, service: SpeedLimitService) {

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  val dao = service.dao
  val logger = LoggerFactory.getLogger(getClass)

  def updateSpeedLimits() = {
    withDynTransaction {
      val municipalities = Queries.getMunicipalities
      municipalities.foreach { municipality =>
        val (roadLinks, changes) = roadLinkService.getRoadLinksAndChangesFromVVHByMunicipality(municipality)
        updateByRoadLinks(municipality, roadLinks, changes)
      }
    }
  }

  def updateByRoadLinks(municipality: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) = {
    val (speedLimitLinks, topology) = dao.getSpeedLimitLinksByRoadLinks(roadLinks.filter(_.isCarTrafficRoad))
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val oldRoadLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    val oldSpeedLimits = dao.getCurrentSpeedLimitsByLinkIds(Some(oldRoadLinkIds.toSet))

    // filter road links that have already been projected to avoid projecting twice
    val speedLimitsOnChangedLinks = speedLimitLinks.filter(sl => LinearAssetUtils.newChangeInfoDetected(sl, mappedChanges))

    val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
      expiredAssetIds = oldSpeedLimits.map(_.id).toSet,
      adjustedMValues = Seq.empty[MValueAdjustment],
      adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
      adjustedSideCodes = Seq.empty[SideCodeAdjustment],
      valueAdjustments = Seq.empty[ValueAdjustment])

    val (newSpeedLimits, projectedChangeSet) = fillNewRoadLinksWithPreviousSpeedLimitData(projectableTargetRoadLinks, oldSpeedLimits ++ speedLimitsOnChangedLinks,
      speedLimitsOnChangedLinks, changes, initChangeSet, speedLimitLinks)

    val speedLimits = (speedLimitLinks ++ newSpeedLimits).groupBy(_.linkId)
    val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits, Some(projectedChangeSet))

    val newSpeedLimitsWithValue = filledTopology.filter(sl => sl.id <= 0 && sl.value.nonEmpty)
    // Expire all assets that are dropped or expired. No more floating speed limits.
    updateChangeSet(changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ changeSet.droppedAssetIds, droppedAssetIds = Set()))
    persistProjectedLimit(newSpeedLimitsWithValue)
    purgeUnknown(changeSet.adjustedMValues.map(_.linkId).toSet, oldRoadLinkIds)
    val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)
    persistUnknown(unknownLimits)
  }

  def updateChangeSet(changeSet: ChangeSet) : Unit = {
    dao.floatLinearAssets(changeSet.droppedAssetIds)

    if (changeSet.adjustedMValues.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedMValues.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

    changeSet.adjustedMValues.foreach { adjustment =>
      dao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
    }

    if (changeSet.adjustedVVHChanges.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

    changeSet.adjustedVVHChanges.foreach { adjustment =>
      dao.updateMValuesChangeInfo(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure), adjustment.timeStamp, LinearAssetTypes.VvhGenerated)
    }

    //NOTE the order between expire and sideCode adjustment cant be changed
    if (changeSet.expiredAssetIds.toSeq.nonEmpty)
      logger.info("Expiring ids " + changeSet.expiredAssetIds.toSeq.mkString(", "))
    changeSet.expiredAssetIds.toSeq.foreach(dao.updateExpiration(_, expired = true, LinearAssetTypes.VvhGenerated))

    if (changeSet.adjustedSideCodes.nonEmpty)
      logger.info("Side Code adjustments ids " + changeSet.adjustedSideCodes.map(a => "" + a.assetId + "/" + a.sideCode).mkString(", "))

    changeSet.adjustedSideCodes.foreach { adjustment =>
      adjustedSideCode(adjustment)
    }
  }


  def persistProjectedLimit(limits: Seq[SpeedLimit]): Unit = {
    val (newlimits, changedlimits) = limits.partition(_.id <= 0)
    newlimits.foreach { limit =>
      dao.createSpeedLimit(limit.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), limit.linkId, Measures(limit.startMeasure, limit.endMeasure),
        limit.sideCode, SpeedLimitValue(limit.value.get.value, limit.value.get.isSuggested), Some(limit.timeStamp), limit.createdDateTime, limit.modifiedBy,
        limit.modifiedDateTime, limit.linkSource)
    }
    purgeUnknown(limits.map(_.linkId).toSet, Seq())

  }

  def purgeUnknown(linkIds: Set[String], expiredLinkIds: Seq[String]): Unit = {
    val roadLinks = roadLinkClient.roadLinkData.fetchByLinkIds(linkIds)
    roadLinks.foreach { rl =>
      dao.purgeFromUnknownSpeedLimits(rl.linkId, GeometryUtils.geometryLength(rl.geometry))
    }
    if (expiredLinkIds.nonEmpty)
      dao.deleteUnknownSpeedLimits(expiredLinkIds)
  }

  def persistUnknown(limits: Seq[UnknownSpeedLimit]): Unit = {
    dao.persistUnknownSpeedLimits(limits)
  }

  protected def fillNewRoadLinksWithPreviousSpeedLimitData(roadLinks: Seq[RoadLink], speedLimitsToUpdate: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit], changes: Seq[ChangeInfo],
                                                           changeSet: ChangeSet, existingSpeedLimit: Seq[SpeedLimit]) : (Seq[SpeedLimit], ChangeSet) = {

    val speedLimitsAndChanges = mapReplacementProjections(speedLimitsToUpdate, currentSpeedLimits, roadLinks, changes).flatMap {
      case (asset, (Some(roadLink), Some(projection))) =>
        val (speedLimit, changes) = SpeedLimitFiller.projectSpeedLimit(asset, roadLink, projection, changeSet)
        if (Math.abs(speedLimit.startMeasure - speedLimit.endMeasure) > 0)
          Some((speedLimit, changes))
        else
          None
      case _ =>
        None
    }

    val speedLimits = speedLimitsAndChanges.map(_._1)
    val generatedChangeSet = speedLimitsAndChanges.map(_._2)
    val changeSetF = if (generatedChangeSet.nonEmpty) { generatedChangeSet.last } else { changeSet }

    val newLinearAsset = if((speedLimits ++ existingSpeedLimit).nonEmpty) {
      //      newChangeAsset(roadLinks, speedLimits ++ existingSpeedLimit, changes) //Temporarily disabled according to DROTH-2327
      Seq()
    } else Seq()

    (speedLimits ++ newLinearAsset, changeSetF)
  }

  private def mapReplacementProjections(oldSpeedLimits: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit], roadLinks: Seq[RoadLink],
                                        changes: Seq[ChangeInfo]) : Seq[(SpeedLimit, (Option[RoadLink], Option[Projection]))] = {
    val targetLinks = changes.flatMap(_.newId).toSet
    val newRoadLinks = roadLinks.filter(rl => targetLinks.contains(rl.linkId)).groupBy(_.linkId)
    val changeMap = changes.filterNot(c => c.newId.isEmpty || c.oldId.isEmpty).map(c => (c.oldId.get, c.newId.get)).groupBy(_._1)
    val targetRoadLinks = changeMap.mapValues(a => a.flatMap(b => newRoadLinks.getOrElse(b._2, Seq())))
    oldSpeedLimits.flatMap{limit =>
      targetRoadLinks.getOrElse(limit.linkId, Seq()).map(newRoadLink =>
        (limit,
          getRoadLinkAndProjection(roadLinks, changes, limit.linkId, newRoadLink.linkId, oldSpeedLimits, currentSpeedLimits))
      )}
  }

  private def getRoadLinkAndProjection(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], oldId: String, newId: String,
                                       speedLimitsToUpdate: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit]) = {
    val roadLink = roadLinks.find(rl => newId == rl.linkId)
    val changeInfo = changes.find(c => c.oldId.getOrElse("") == oldId && c.newId.getOrElse("") == newId)
    val projection = changeInfo match {
      case Some(info) =>
        // ChangeInfo object related speed limits; either mentioned in oldId or in newId
        val speedLimits = speedLimitsToUpdate.filter(_.linkId == info.oldId.getOrElse("")) ++
          currentSpeedLimits.filter(_.linkId == info.newId.getOrElse(""))
        mapChangeToProjection(info, speedLimits)
      case _ => None
    }
    (roadLink,projection)
  }

  private def mapChangeToProjection(change: ChangeInfo, speedLimits: Seq[SpeedLimit]): Option[Projection] = {
    val typed = ChangeType.apply(change.changeType)
    typed match {
      // cases 5, 6, 1, 2
      case ChangeType.DividedModifiedPart  | ChangeType.DividedNewPart | ChangeType.CombinedModifiedPart |
           ChangeType.CombinedRemovedPart => projectSpeedLimitConditionally(change, speedLimits, testNoSpeedLimitExists)
      // cases 3, 7, 13, 14
      case ChangeType.LengthenedCommonPart | ChangeType.ShortenedCommonPart | ChangeType.ReplacedCommonPart |
           ChangeType.ReplacedNewPart =>
        projectSpeedLimitConditionally(change, speedLimits, testSpeedLimitOutdated)
      case _ => None
    }
  }

  private def testNoSpeedLimitExists(speedLimits: Seq[SpeedLimit], linkId: String, mStart: Double, mEnd: Double, timeStamp: Long) = {
    !speedLimits.exists(l => l.linkId == linkId && GeometryUtils.overlaps((l.startMeasure,l.endMeasure),(mStart,mEnd)))
  }

  private def testSpeedLimitOutdated(speedLimits: Seq[SpeedLimit], linkId: String, mStart: Double, mEnd: Double, timeStamp: Long) = {
    val targetLimits = speedLimits.filter(l => l.linkId == linkId)
    targetLimits.nonEmpty && !targetLimits.exists(l => l.timeStamp >= timeStamp)
  }

  private def projectSpeedLimitConditionally(change: ChangeInfo, limits: Seq[SpeedLimit], condition: (Seq[SpeedLimit], String, Double, Double, Long) => Boolean) = {
    (change.newId, change.oldStartMeasure, change.oldEndMeasure, change.newStartMeasure, change.newEndMeasure, change.timeStamp) match {
      case (Some(newId), Some(oldStart:Double), Some(oldEnd:Double),
      Some(newStart:Double), Some(newEnd:Double), timeStamp) =>
        condition(limits, newId, newStart, newEnd, timeStamp) match {
          case true => Some(Projection(oldStart, oldEnd, newStart, newEnd, timeStamp))
          case false => None
        }
      case _ => None
    }
  }

  protected def createUnknownLimits(speedLimits: Seq[SpeedLimit], roadLinksByLinkId: Map[String, RoadLink]): Seq[UnknownSpeedLimit] = {
    val generatedLimits = speedLimits.filter(speedLimit => speedLimit.id == 0 && speedLimit.value.isEmpty)
    generatedLimits.map { limit =>
      val roadLink = roadLinksByLinkId(limit.linkId)
      UnknownSpeedLimit(roadLink.linkId, roadLink.municipalityCode, roadLink.administrativeClass)
    }
  }

  def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldSpeedLimit = service.getPersistedSpeedLimitById(adjustment.assetId, newTransaction = false).getOrElse(throw new IllegalStateException("Asset no longer available"))

    service.updateByExpiration(oldSpeedLimit.id, true, LinearAssetTypes.VvhGenerated, false)
    val newId = service.createWithoutTransaction(Seq(NewLimit(oldSpeedLimit.linkId, oldSpeedLimit.startMeasure, oldSpeedLimit.endMeasure)), SpeedLimitValue(oldSpeedLimit.value.get.value, oldSpeedLimit.value.get.isSuggested), LinearAssetTypes.VvhGenerated, adjustment.sideCode)
    logger.info("SideCodeAdjustment newID" + newId)
  }

  def newChangeAsset(roadLinks: Seq[RoadLink], existingAssets: Seq[SpeedLimit], changes: Seq[ChangeInfo]): Seq[SpeedLimit] = {
    val changesNew = changes.filter(_.changeType == New.value)
    changesNew.filterNot(chg => existingAssets.exists(_.linkId == chg.newId.get)).flatMap { change =>
      roadLinks.find(_.linkId == change.newId.get).map { changeRoadLink =>
        val assetAndPoints : Seq[(Point, SpeedLimit)] = getAssetsAndPoints(existingAssets, roadLinks, (change, changeRoadLink))
        val (first, last) = GeometryUtils.geometryEndpoints(changeRoadLink.geometry)

        if (assetAndPoints.nonEmpty) {
          val assetAdjFirst = getAdjacentAssetByPoint(assetAndPoints, first)
          val assetAdjLast = getAdjacentAssetByPoint(assetAndPoints, last)

          val groupBySideCodeFirst = assetAdjFirst.groupBy(_.sideCode)
          val groupBySideCodeLast = assetAdjLast.groupBy(_.sideCode)

          if (assetAdjFirst.nonEmpty && assetAdjLast.nonEmpty) {
            groupBySideCodeFirst.keys.flatMap { sideCode =>
              groupBySideCodeFirst(sideCode).find{asset =>
                val lastAdjsWithFirstSideCode = groupBySideCodeLast.get(sideCode)
                lastAdjsWithFirstSideCode.isDefined && lastAdjsWithFirstSideCode.get.exists(_.value.equals(asset.value))
              }.map { asset =>
                asset.copy(id = 0, linkId = changeRoadLink.linkId, startMeasure = 0L.toDouble, endMeasure = GeometryUtils.geometryLength(changeRoadLink.geometry))
              }
            }
          } else
            Seq()
        } else
          Seq()
      }
    }.flatten
  }

  def getAssetsAndPoints(existingAssets: Seq[SpeedLimit], roadLinks: Seq[RoadLink], changeInfo: (ChangeInfo, RoadLink)): Seq[(Point, SpeedLimit)] = {
    existingAssets.filter { asset => asset.createdDateTime.get.isBefore(changeInfo._1.timeStamp)}
      .flatMap { asset =>
        val roadLink = roadLinks.find(_.linkId == asset.linkId)
        if (roadLink.nonEmpty && roadLink.get.administrativeClass == changeInfo._2.administrativeClass) {
          GeometryUtils.calculatePointFromLinearReference(roadLink.get.geometry, asset.endMeasure).map(point => (point, asset)) ++
            (if (asset.startMeasure == 0)
              GeometryUtils.calculatePointFromLinearReference(roadLink.get.geometry, asset.startMeasure).map(point => (point, asset))
            else
              Seq())
        } else
          Seq()
      }
  }

  def getAdjacentAssetByPoint(assets: Seq[(Point, SpeedLimit)], point: Point) : Seq[SpeedLimit] = {
    assets.filter{case (assetPt, _) => GeometryUtils.areAdjacent(assetPt, point)}.map(_._2)
  }
}