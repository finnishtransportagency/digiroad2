package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._

object SpeedLimitFiller extends AssetFiller {
  
  def getOperations(geometryChanged: Boolean): Seq[(RoadLinkForFilTopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = {
    val fillOperations: Seq[(RoadLinkForFilTopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = Seq(
      printlnOperation("start running fillTopology state now"),
      expireSegmentsOutsideGeometry,
      printlnOperation("expireSegmentsOutsideGeometry"),
      combine,
      printlnOperation("combine"),
      fuse,
      printlnOperation("fuse"),
      adjustAssets,
      printlnOperation("adjustAssets"),
      capToGeometry,
      printlnOperation("capToGeometry"),
      droppedSegmentWrongDirection,
      printlnOperation("droppedSegmentWrongDirection"),
      adjustSegmentSideCodes,
      printlnOperation("adjustSegmentSideCodes"),
      dropShortSegments,
      printlnOperation("dropShortSegments"),
      fillHoles,
      printlnOperation("fillHoles"),
      generateTwoSidedNonExistingLinearAssets(SpeedLimitAsset.typeId),
      printlnOperation("generateTwoSidedNonExistingLinearAssets"),
      generateOneSidedNonExistingLinearAssets(SideCode.TowardsDigitizing, SpeedLimitAsset.typeId),
      printlnOperation("generateOneSidedNonExistingLinearAssets"),
      generateOneSidedNonExistingLinearAssets(SideCode.AgainstDigitizing, SpeedLimitAsset.typeId),
      printlnOperation("generateOneSidedNonExistingLinearAssets"),
      clean,
      printlnOperation("clean")
    )

    val adjustmentOperations: Seq[(RoadLinkForFilTopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = Seq(
      combine,
      fuse,
      adjustAssets,
      droppedSegmentWrongDirection,
      adjustSegmentSideCodes,
      dropShortSegments,
      fillHoles,
      generateTwoSidedNonExistingLinearAssets(SpeedLimitAsset.typeId),
      printlnOperation("generateTwoSidedNonExistingLinearAssets"),
      generateOneSidedNonExistingLinearAssets(SideCode.TowardsDigitizing, SpeedLimitAsset.typeId),
      printlnOperation("generateOneSidedNonExistingLinearAssets"),
      generateOneSidedNonExistingLinearAssets(SideCode.AgainstDigitizing, SpeedLimitAsset.typeId),
      printlnOperation("generateOneSidedNonExistingLinearAssets"),
      clean)

    if(geometryChanged) fillOperations
    else adjustmentOperations
  }
// TODO can we generalize this for all other assets also
  override def adjustAsset(asset: PieceWiseLinearAsset, roadLink: RoadLinkForFilTopology): (PieceWiseLinearAsset, Seq[MValueAdjustment]) = {
    val startError = asset.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endMeasureDifference = roadLinkLength - asset.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endMeasureDifference > MaxAllowedMValueError)
        Seq(MValueAdjustment(asset.id, asset.linkId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = asset.copy(geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }
  
  private def adjustTwoWaySegments(roadLink: RoadLinkForFilTopology,
                                   assets: Seq[PieceWiseLinearAsset]):
  (Seq[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    val twoWaySegments = assets.filter(_.sideCode == SideCode.BothDirections).sortWith(modifiedSort)
    if (twoWaySegments.length == 1 && assets.forall(_.sideCode == SideCode.BothDirections)) {
      val segment = assets.last
      val (adjustedSegment, mValueAdjustments) = adjustAsset(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (twoWaySegments, Nil)
    }
  }

  private def adjustOneWaySegments(roadLink: RoadLinkForFilTopology,
                                   assets: Seq[PieceWiseLinearAsset],
                                   runningDirection: SideCode):
  (Seq[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = assets.filter(_.sideCode == runningDirection).sortWith(modifiedSort)
    if (segmentsTowardsRunningDirection.length == 1 && !assets.exists(_.sideCode == SideCode.BothDirections)) {
      val segment = segmentsTowardsRunningDirection.last
      val (adjustedSegment, mValueAdjustments) = adjustAsset(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (segmentsTowardsRunningDirection, Nil)
    }
  }

  override def adjustAssets(roadLink: RoadLinkForFilTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(roadLink, assets, SideCode.TowardsDigitizing)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(roadLink, assets, SideCode.AgainstDigitizing)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(roadLink, assets)
    val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
    val (asset,changeSetCopy)=(towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
    adjustLopsidedLimit(roadLink,asset,changeSetCopy)
  }
  
  private  def adjustLopsidedLimit(roadLink: RoadLinkForFilTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val onlyLimitOnLink = assets.length == 1 && assets.head.sideCode != SideCode.BothDirections
    if (onlyLimitOnLink) {
      val segment = assets.head
      val sideCodeAdjustments = Seq(SideCodeAdjustment(segment.id, SideCode.BothDirections, SpeedLimitAsset.typeId))
      (Seq(segment.copy(sideCode = SideCode.BothDirections)), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments))
    } else {
      (assets, changeSet)
    }
  }
  override def dropShortSegments(roadLink: RoadLinkForFilTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val limitsToDrop = assets.filter { limit =>
      GeometryUtils.geometryLength(limit.geometry) < MinAllowedLength && roadLink.length > MinAllowedLength
    }.map(_.id).toSet
    val limits = assets.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ limitsToDrop))
  }
  
  override protected def generateTwoSidedNonExistingLinearAssets(typeId: Int)(roadLink: RoadLinkForFilTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val lrmPositions: Seq[(Double, Double)] = segments.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5 }
    val generatedLinearAssets =   if (roadLink.isSimpleCarTrafficRoad) {
      val generated = remainders.map { segment =>
        PersistedLinearAsset(0L, roadLink.linkId, 1, None, segment._1, segment._2, None, None, None, None, false, typeId, 0, None, roadLink.linkSource, None, None, None)
      }
      toLinearAsset(generated, roadLink)
    } else Seq.empty[PieceWiseLinearAsset]
    (segments ++ generatedLinearAssets, changeSet)
  }
  
  override protected def generateOneSidedNonExistingLinearAssets(sideCode: SideCode, typeId: Int)(roadLink: RoadLinkForFilTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val generated = if (roadLink.trafficDirection == TrafficDirection.BothDirections && roadLink.isSimpleCarTrafficRoad) {
      val lrmPositions: Seq[(Double, Double)] = segments
        .filter { s => s.sideCode == sideCode || s.sideCode == SideCode.BothDirections }
        .map { x => (x.startMeasure, x.endMeasure) }
      val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5 }
      val persisted = remainders.map { segment =>
        PersistedLinearAsset(0L, roadLink.linkId, sideCode.value, None, segment._1, segment._2, None, None, None, None, false, typeId, 0, None, roadLink.linkSource, None, None, None)
      }
      toLinearAsset(persisted, roadLink)
    } else {
      Nil
    }
    (segments ++ generated, changeSet)
  }
  
  override def fillTopology(roadLinks: Seq[RoadLinkForFilTopology], speedLimits: Map[String, Seq[PieceWiseLinearAsset]], typeId:Int, changedSet: Option[ChangeSet] = None,
                            geometryChanged: Boolean = true): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val operations = getOperations(geometryChanged)
    // TODO: Do not create dropped asset ids but mark them expired when they are no longer valid or relevant
    val changeSet = changedSet match {
      case Some(change) => change
      case None => ChangeSet( droppedAssetIds = Set.empty[Long],
        expiredAssetIds = Set.empty[Long],
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
        adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])
    }

    roadLinks.foldLeft(Seq.empty[PieceWiseLinearAsset], changeSet) { case (acc, roadLink) =>
      val (existingSegments, changeSet) = acc
      val segments = speedLimits.getOrElse(roadLink.linkId, Nil)
      val validSegments = segments.filterNot { segment => changeSet.droppedAssetIds.contains(segment.id) }

      val (adjustedSegments, segmentAdjustments) = operations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      (existingSegments ++ adjustedSegments, segmentAdjustments)
    }
  }

  override def fillTopologyChangesGeometry(topology: Seq[RoadLinkForFilTopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]], typeId: Int,
                                           changedSet: Option[ChangeSet] = None): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val operations: Seq[(RoadLinkForFilTopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = Seq(
      printlnOperation("operation start"),
      fuse,
      printlnOperation("fuse"),
      dropShortSegments,
      printlnOperation("dropShortSegments"),
      adjustAssets,
      printlnOperation("adjustAssets"),
      expireOverlappingSegments,
      printlnOperation("expireOverlappingSegments"),
      droppedSegmentWrongDirection,
      printlnOperation("droppedSegmentWrongDirection"),
      adjustSegmentSideCodes,
      printlnOperation("adjustSegmentSideCodes"),
      fillHoles,
      printlnOperation("fillHoles"),
      clean
    )
    val changeSet = changedSet match {
      case Some(change) => change
      case None => ChangeSet(droppedAssetIds = Set.empty[Long],
        expiredAssetIds = Set.empty[Long],
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
        adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])
    }
    // if links does not have any asset filter it away 
    topology.filter(p => linearAssets.keySet.contains(p.linkId)).foldLeft(Seq.empty[PieceWiseLinearAsset], changeSet) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink.linkId, Nil)

      val (adjustedAssets, assetAdjustments) = operations.foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      val filterExpiredAway = assetAdjustments.copy(adjustedMValues = assetAdjustments.adjustedMValues.filterNot(p =>
        assetAdjustments.droppedAssetIds.contains(p.assetId) ||
          assetAdjustments.expiredAssetIds.contains(p.assetId)))

      val noDuplicate = filterExpiredAway.copy(
        adjustedMValues = filterExpiredAway.adjustedMValues.distinct,
        adjustedVVHChanges = filterExpiredAway.adjustedVVHChanges.distinct,
        adjustedSideCodes = filterExpiredAway.adjustedSideCodes.distinct,
        valueAdjustments = filterExpiredAway.valueAdjustments.distinct
      )

      (existingAssets ++ adjustedAssets, noDuplicate)
    }
  }
  
  /**
    * For debugging; print speed limit relevant data
    * @param speedLimit speedlimit to print
    */
  def printSL(speedLimit: PieceWiseLinearAsset) = {
    val ids = "%d (%d)".format(speedLimit.id, speedLimit.linkId)
    val dir = speedLimit.sideCode match {
      case SideCode.BothDirections => "⇅"
      case SideCode.TowardsDigitizing => "↑"
      case SideCode.AgainstDigitizing => "↓"
      case _ => "?"
    }
    val details = "%d %.4f %.4f %s".format(speedLimit.value.getOrElse(SpeedLimitValue(0)), speedLimit.startMeasure, speedLimit.endMeasure, speedLimit.timeStamp.toString)
    if (speedLimit.expired) {
      println("N/A")
    } else {
      println("%s %s %s".format(ids, dir, details))
    }
  }
  
}
