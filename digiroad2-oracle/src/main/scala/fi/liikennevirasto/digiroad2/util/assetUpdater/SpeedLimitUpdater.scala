package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.New
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimitFiller.fillTopology
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils

class SpeedLimitUpdater(service: SpeedLimitService) extends DynamicLinearAssetUpdater(service) {

  val speedLimitDao = service.speedLimitDao


  override def updateByRoadLinks(typeId: Int, municipality: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Unit = {
    val speedLimitLinks = speedLimitDao.getSpeedLimitLinksByRoadLinks(roadLinks.filter(_.isCarTrafficRoad))
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val oldRoadLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    val oldSpeedLimits = speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(oldRoadLinkIds.toSet))

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
    handleChangesAndUnknowns(roadLinks, speedLimits, Some(projectedChangeSet), oldRoadLinkIds, geometryChanged = true)
  }

  def handleChangesAndUnknowns(topology: Seq[RoadLink], speedLimits: Map[String, Seq[PieceWiseLinearAsset]],
                               changeSet:Option[ChangeSet] = None, oldRoadLinkIds: Seq[String], geometryChanged: Boolean, counter: Int = 1): Seq[PieceWiseLinearAsset] = {
    val (filledTopology, changedSet) = fillTopology(topology, speedLimits, SpeedLimitAsset.typeId, changeSet, geometryChanged)
    val cleanedChangeSet = cleanRedundantMValueAdjustments(changedSet, speedLimits.values.flatten.toSeq).filterGeneratedAssets
    val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)
    val newSpeedLimitsWithValue = filledTopology.filter(sl => sl.id <= 0 && sl.value.nonEmpty)
    val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)

    cleanedChangeSet.isEmpty match {
      case true =>
        persistProjectedLimit(newSpeedLimitsWithValue)
        persistUnknown(unknownLimits)
        filledTopology
      case false if counter > 3 =>
        updateChangeSet(cleanedChangeSet)
        persistProjectedLimit(newSpeedLimitsWithValue)
        purgeUnknown(cleanedChangeSet.adjustedMValues.map(_.linkId).toSet, oldRoadLinkIds)
        persistUnknown(unknownLimits)
        filledTopology
      case false if counter <= 3 =>
        updateChangeSet(cleanedChangeSet)
        purgeUnknown(cleanedChangeSet.adjustedMValues.map(_.linkId).toSet, oldRoadLinkIds)
        val speedLimitsToAdjust = filledTopology.filterNot(speedLimit => speedLimit.id <= 0 && speedLimit.value.isEmpty).groupBy(_.linkId)
        handleChangesAndUnknowns(topology, speedLimitsToAdjust, None ,oldRoadLinkIds, geometryChanged, counter + 1)
    }
  }

  override def updateChangeSet(changeSet: ChangeSet) : Unit = {
    speedLimitDao.floatLinearAssets(changeSet.droppedAssetIds)

    if (changeSet.adjustedMValues.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedMValues.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

    changeSet.adjustedMValues.foreach { adjustment =>
      speedLimitDao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
    }

    if (changeSet.adjustedVVHChanges.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

    changeSet.adjustedVVHChanges.foreach { adjustment =>
      speedLimitDao.updateMValuesChangeInfo(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure), adjustment.timeStamp, AutoGeneratedUsername.generatedInUpdate)
    }

    //NOTE the order between expire and sideCode adjustment cant be changed
    if (changeSet.expiredAssetIds.toSeq.nonEmpty)
      logger.info("Expiring ids " + changeSet.expiredAssetIds.toSeq.mkString(", "))
    changeSet.expiredAssetIds.toSeq.foreach(speedLimitDao.updateExpiration(_, expired = true, AutoGeneratedUsername.generatedInUpdate))

    if (changeSet.adjustedSideCodes.nonEmpty)
      logger.info("Side Code adjustments ids " + changeSet.adjustedSideCodes.map(a => "" + a.assetId + "/" + a.sideCode).mkString(", "))

    changeSet.adjustedSideCodes.foreach { adjustment =>
      adjustedSideCode(adjustment)
    }
  }


  def persistProjectedLimit(limits: Seq[PieceWiseLinearAsset]): Unit = {
    val (newlimits, changedlimits) = limits.partition(_.id <= 0)
    newlimits.foreach { limit =>

      speedLimitDao.createSpeedLimit(limit.createdBy.getOrElse(AutoGeneratedUsername.generatedInUpdate), limit.linkId, Measures(limit.startMeasure, limit.endMeasure),
        limit.sideCode, service.getSpeedLimitValue(limit.value).get, Some(limit.timeStamp), limit.createdDateTime, limit.modifiedBy,
        limit.modifiedDateTime, limit.linkSource)
    }
    purgeUnknown(limits.map(_.linkId).toSet, Seq())

  }

  def purgeUnknown(linkIds: Set[String], expiredLinkIds: Seq[String]): Unit = {
    val roadLinks = roadLinkService.fetchRoadlinksByIds(linkIds)
    roadLinks.foreach { rl =>
      speedLimitDao.purgeFromUnknownSpeedLimits(rl.linkId, GeometryUtils.geometryLength(rl.geometry))
    }
    if (expiredLinkIds.nonEmpty)
      speedLimitDao.deleteUnknownSpeedLimits(expiredLinkIds)
  }

  def persistUnknown(limits: Seq[UnknownSpeedLimit]): Unit = {
    speedLimitDao.persistUnknownSpeedLimits(limits)
  }

  protected def fillNewRoadLinksWithPreviousSpeedLimitData(roadLinks: Seq[RoadLink], speedLimitsToUpdate: Seq[PieceWiseLinearAsset], currentSpeedLimits: Seq[PieceWiseLinearAsset], changes: Seq[ChangeInfo],
                                                           changeSet: ChangeSet, existingSpeedLimit: Seq[PieceWiseLinearAsset]) : (Seq[PieceWiseLinearAsset], ChangeSet) = {

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
      //      newPieceWiseChangeAsset(roadLinks, speedLimits ++ existingSpeedLimit, changes) //Temporarily disabled according to DROTH-2327
      Seq()
    } else Seq()

    (speedLimits ++ newLinearAsset, changeSetF)
  }

  private def mapReplacementProjections(oldSpeedLimits: Seq[PieceWiseLinearAsset], currentSpeedLimits: Seq[PieceWiseLinearAsset], roadLinks: Seq[RoadLink],
                                        changes: Seq[ChangeInfo]) : Seq[(PieceWiseLinearAsset, (Option[RoadLink], Option[Projection]))] = {
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
                                       speedLimitsToUpdate: Seq[PieceWiseLinearAsset], currentSpeedLimits: Seq[PieceWiseLinearAsset]) = {
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

  private def mapChangeToProjection(change: ChangeInfo, speedLimits: Seq[PieceWiseLinearAsset]): Option[Projection] = {
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

  private def testNoSpeedLimitExists(speedLimits: Seq[PieceWiseLinearAsset], linkId: String, mStart: Double, mEnd: Double, timeStamp: Long) = {
    !speedLimits.exists(l => l.linkId == linkId && GeometryUtils.overlaps((l.startMeasure,l.endMeasure),(mStart,mEnd)))
  }

  private def testSpeedLimitOutdated(speedLimits: Seq[PieceWiseLinearAsset], linkId: String, mStart: Double, mEnd: Double, timeStamp: Long) = {
    val targetLimits = speedLimits.filter(l => l.linkId == linkId)
    targetLimits.nonEmpty && !targetLimits.exists(l => l.timeStamp >= timeStamp)
  }

  private def projectSpeedLimitConditionally(change: ChangeInfo, limits: Seq[PieceWiseLinearAsset], condition: (Seq[PieceWiseLinearAsset], String, Double, Double, Long) => Boolean) = {
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

  protected def createUnknownLimits(speedLimits: Seq[PieceWiseLinearAsset], roadLinksByLinkId: Map[String, RoadLink]): Seq[UnknownSpeedLimit] = {
    val generatedLimits = speedLimits.filter(speedLimit => speedLimit.id == 0 && speedLimit.value.isEmpty)
    generatedLimits.map { limit =>
      val roadLink = roadLinksByLinkId(limit.linkId)
      UnknownSpeedLimit(roadLink.linkId, roadLink.municipalityCode, roadLink.administrativeClass)
    }
  }

  override def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldSpeedLimit = service.getPersistedSpeedLimitById(adjustment.assetId, newTransaction = false).getOrElse(throw new IllegalStateException("Asset no longer available"))

    service.expireAsset(SpeedLimitAsset.typeId, oldSpeedLimit.id, AutoGeneratedUsername.generatedInUpdate,true,  false)
    val newId = service.createWithoutTransaction(Seq(NewLimit(oldSpeedLimit.linkId, oldSpeedLimit.startMeasure, oldSpeedLimit.endMeasure)), service.getSpeedLimitValue(oldSpeedLimit.value).get, AutoGeneratedUsername.generatedInUpdate, adjustment.sideCode)
    logger.info("SideCodeAdjustment newID" + newId)
  }

  def newPieceWiseChangeAsset(roadLinks: Seq[RoadLink], existingAssets: Seq[PieceWiseLinearAsset], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {
    val changesNew = changes.filter(_.changeType == New.value)
    changesNew.filterNot(chg => existingAssets.exists(_.linkId == chg.newId.get)).flatMap { change =>
      roadLinks.find(_.linkId == change.newId.get).map { changeRoadLink =>
        val assetAndPoints : Seq[(Point, PieceWiseLinearAsset)] = getAssetsAndPoints(existingAssets, roadLinks, (change, changeRoadLink))
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

  def getAssetsAndPoints(existingAssets: Seq[PieceWiseLinearAsset], roadLinks: Seq[RoadLink], changeInfo: (ChangeInfo, RoadLink)): Seq[(Point, PieceWiseLinearAsset)] = {
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

  def getAdjacentAssetByPoint(assets: Seq[(Point, PieceWiseLinearAsset)], point: Point) : Seq[PieceWiseLinearAsset] = {
    assets.filter{case (assetPt, _) => GeometryUtils.areAdjacent(assetPt, point)}.map(_._2)
  }
}
