package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._

object SpeedLimitFiller extends AssetFiller {
  private val MaxAllowedMValueError = 0.1
  private val Epsilon = 1E-6 /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
                                See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
                             */
  private val MinAllowedSpeedLimitLength = 2.0

  /*      updateValues,
  printlnOperation("updateValues")*/
  def getOperations(geometryChanged: Boolean): Seq[(RoadLinkForFiltopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = {
    val fillOperations: Seq[(RoadLinkForFiltopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = Seq(
      printlnOperation("start running fillTopology state now"),
      expireSegmentsOutsideGeometry,
      printlnOperation("expireSegmentsOutsideGeometry"),
      combine,
      printlnOperation("combine"),
      fuse,
      printlnOperation("fuse"),
      //adjustSegmentMValues,
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
      clean,
      printlnOperation("clean")
    )

    val adjustmentOperations: Seq[(RoadLinkForFiltopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = Seq(
      combine,
      fuse,
      adjustAssets,
      droppedSegmentWrongDirection,
      adjustSegmentSideCodes,
      dropShortSegments,
      fillHoles,
      clean)

    if(geometryChanged) fillOperations
    else adjustmentOperations
  }

  private def adjustSegment(segment: PieceWiseLinearAsset, roadLink: RoadLinkForFiltopology): (PieceWiseLinearAsset, Seq[MValueAdjustment]) = {
    val startError = segment.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - segment.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(segment.id, segment.linkId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = segment.copy(geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }
  
  private def adjustTwoWaySegments(roadLink: RoadLinkForFiltopology,
                                   segments: Seq[PieceWiseLinearAsset]):
  (Seq[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    val twoWaySegments = segments.filter(_.sideCode == SideCode.BothDirections).sortWith(modifiedSort)
    if (twoWaySegments.length == 1 && segments.forall(_.sideCode == SideCode.BothDirections)) {
      val segment = segments.last
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (twoWaySegments, Nil)
    }
  }

  private def adjustOneWaySegments(roadLink: RoadLinkForFiltopology,
                                   segments: Seq[PieceWiseLinearAsset],
                                   runningDirection: SideCode):
  (Seq[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = segments.filter(_.sideCode == runningDirection).sortWith(modifiedSort)
    if (segmentsTowardsRunningDirection.length == 1 && !segments.exists(_.sideCode == SideCode.BothDirections)) {
      val segment = segmentsTowardsRunningDirection.last
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (segmentsTowardsRunningDirection, Nil)
    }
  }

  override def adjustAssets(roadLink: RoadLinkForFiltopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.TowardsDigitizing)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.AgainstDigitizing)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(roadLink, segments)
    val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
    val (asset,changeSetCopy)=(towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
    adjustLopsidedLimit(roadLink,asset,changeSetCopy)
  }
  
  private  def adjustLopsidedLimit(roadLink: RoadLinkForFiltopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val onlyLimitOnLink = segments.length == 1 && segments.head.sideCode != SideCode.BothDirections
    if (onlyLimitOnLink) {
      val segment = segments.head
      val sideCodeAdjustments = Seq(SideCodeAdjustment(segment.id, SideCode.BothDirections, SpeedLimitAsset.typeId))
      (Seq(segment.copy(sideCode = SideCode.BothDirections)), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments))
    } else {
      (segments, changeSet)
    }
  }
  override def dropShortSegments(roadLink: RoadLinkForFiltopology, speedLimits: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val limitsToDrop = speedLimits.filter { limit =>
      GeometryUtils.geometryLength(limit.geometry) < MinAllowedSpeedLimitLength &&
        roadLink.length > MinAllowedSpeedLimitLength
    }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ limitsToDrop))
  }
  
  def generateUnknownSpeedLimitsForLink(roadLink: RoadLinkForFiltopology, segmentsOnLink: Seq[PieceWiseLinearAsset]): Seq[PieceWiseLinearAsset] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }

    if(roadLink.isSimpleCarTrafficRoad) {
      val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MinAllowedSpeedLimitLength }
      remainders.map { segment =>
        val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, segment._1, segment._2)
        PieceWiseLinearAsset(0, roadLink.linkId, SideCode.BothDirections, None, geometry, false,
          segment._1, segment._2, geometry.toSet, None, None, None, None,
          SpeedLimitAsset.typeId, roadLink.trafficDirection, 0, None,
          roadLink.linkSource, roadLink.administrativeClass, Map(), None, None, None)
      }
    } else
      Seq()
  }
  
  override def fillTopology(roadLinks: Seq[RoadLinkForFiltopology], speedLimits: Map[String, Seq[PieceWiseLinearAsset]], typeId:Int, changedSet: Option[ChangeSet] = None,
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
      val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, adjustedSegments)
      (existingSegments ++ adjustedSegments ++ generatedSpeedLimits, segmentAdjustments)
    }
  }

/*  override def fillTopologyChangesGeometry(topology: Seq[RoadLinkForFiltopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]], typeId: Int,
                                           changedSet: Option[ChangeSet] = None): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val operations: Seq[(RoadLinkForFiltopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = Seq(
      expireSegmentsOutsideGeometry,
      combine,
      fuse,
      adjustSegmentMValues,
      capToGeometry,
      adjustLopsidedLimit,
      droppedSegmentWrongDirection,
      adjustSegmentSideCodes,
      dropShortSegments,
      fillHoles,
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

    topology.foldLeft(Seq.empty[PieceWiseLinearAsset], changeSet) { case (acc, roadLink) =>
      val (existingSegments, changeSet) = acc
      val segments = linearAssets.getOrElse(roadLink.linkId, Nil)
      val validSegments = segments.filterNot { segment => changeSet.droppedAssetIds.contains(segment.id) }

      val (adjustedSegments, segmentAdjustments) = operations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, adjustedSegments)
      (existingSegments ++ adjustedSegments ++ generatedSpeedLimits, segmentAdjustments)
    }
  }*/

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
// TODO remove
  def projectSpeedLimit(asset: PieceWiseLinearAsset, to: RoadLink, projection: Projection , changedSet: ChangeSet): (PieceWiseLinearAsset, ChangeSet)= {
    val newLinkId = to.linkId
    val assetId = asset.linkId match {
      case to.linkId => asset.id
      case _ => 0
    }
    val oldLength = projection.oldEnd - projection.oldStart
    val newLength = projection.newEnd - projection.newStart
    var newSideCode = asset.sideCode
    var newDirection = asset.trafficDirection
    var newStart = projection.newStart + (asset.startMeasure - projection.oldStart) * Math.abs(newLength/oldLength)
    var newEnd = projection.newEnd + (asset.endMeasure - projection.oldEnd) * Math.abs(newLength/oldLength)

    // Test if the direction has changed - side code will be affected, too
    if (GeometryUtils.isDirectionChangeProjection(projection)) {
      newSideCode = newSideCode match {
        case (SideCode.AgainstDigitizing) => SideCode.TowardsDigitizing
        case (SideCode.TowardsDigitizing) => SideCode.AgainstDigitizing
        case _ => newSideCode
      }
      newDirection = newDirection match {
        case (TrafficDirection.AgainstDigitizing) => TrafficDirection.TowardsDigitizing
        case (TrafficDirection.TowardsDigitizing) => TrafficDirection.AgainstDigitizing
        case _ => newDirection
      }
      newStart = projection.newStart - (asset.endMeasure - projection.oldStart) * Math.abs(newLength/oldLength)
      newEnd = projection.newEnd - (asset.startMeasure - projection.oldEnd) * Math.abs(newLength/oldLength)
    }

    newStart = Math.min(to.length, Math.max(0.0, newStart))
    newEnd = Math.max(0.0, Math.min(to.length, newEnd))

    val geometry = GeometryUtils.truncateGeometry3D(
      Seq(GeometryUtils.calculatePointFromLinearReference(to.geometry, newStart).getOrElse(to.geometry.head),
        GeometryUtils.calculatePointFromLinearReference(to.geometry, newEnd).getOrElse(to.geometry.last)),
      0, to.length)

    val changeSet =
      if ((Math.abs(newStart - newEnd) > 0) && assetId != 0) {
        changedSet.copy(
          adjustedVVHChanges =  changedSet.adjustedVVHChanges ++ Seq(VVHChangesAdjustment(assetId, newLinkId, newStart, newEnd, projection.timeStamp)),
          adjustedSideCodes = changedSet.adjustedSideCodes ++ Seq(SideCodeAdjustment(assetId, newSideCode, SpeedLimitAsset.typeId))
        )
      }
      else
        changedSet

    (PieceWiseLinearAsset(id = assetId, linkId = newLinkId, sideCode = newSideCode, asset.value, geometry, false,
      newStart, newEnd, geometry.toSet, asset.modifiedBy, asset.modifiedDateTime, asset.createdBy, asset.createdDateTime,
      SpeedLimitAsset.typeId, newDirection, projection.timeStamp, None,
      asset.linkSource, to.administrativeClass, Map(), None, None, None), changeSet)
  }
}
