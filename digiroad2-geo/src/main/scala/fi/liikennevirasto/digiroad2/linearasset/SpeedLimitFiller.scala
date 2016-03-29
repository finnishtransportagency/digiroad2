package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment}

object SpeedLimitFiller {
  private val MaxAllowedMValueError = 0.5

  private def adjustSegment(segment: SpeedLimit, roadLink: RoadLink): (SpeedLimit, Seq[MValueAdjustment]) = {
    val startError = segment.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - segment.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(segment.id, segment.linkId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = segment.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }

  private def adjustTwoWaySegments(roadLink: RoadLink,
                                   segments: Seq[SpeedLimit]):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val twoWaySegments = segments.filter(_.sideCode == SideCode.BothDirections)
    if (twoWaySegments.length == 1 && segments.forall(_.sideCode == SideCode.BothDirections)) {
      val segment = segments.head
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (twoWaySegments, Nil)
    }
  }

  private def adjustOneWaySegments(roadLink: RoadLink,
                                   segments: Seq[SpeedLimit],
                                   runningDirection: SideCode):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = segments.filter(_.sideCode == runningDirection)
    if (segmentsTowardsRunningDirection.length == 1 && segments.filter(_.sideCode == SideCode.BothDirections).isEmpty) {
      val segment = segmentsTowardsRunningDirection.head
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (segmentsTowardsRunningDirection, Nil)
    }
  }

  private def adjustSegmentMValues(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.TowardsDigitizing)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.AgainstDigitizing)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(roadLink, segments)
    val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
  }

  private def capToGeometry(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val (overflowingSegments, passThroughSegments) = segments.partition(_.endMeasure - MaxAllowedMValueError > linkLength)
    val cappedSegments = overflowingSegments.map { s =>
      (s.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, s.startMeasure, linkLength), endMeasure = linkLength),
        MValueAdjustment(s.id, roadLink.linkId, s.startMeasure, linkLength))
    }
    (passThroughSegments ++ cappedSegments.map(_._1), changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ cappedSegments.map(_._2)))
  }

  private def adjustLopsidedLimit(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val onlyLimitOnLink = segments.length == 1 && segments.head.sideCode != SideCode.BothDirections
    if (onlyLimitOnLink) {
      val segment = segments.head
      val sideCodeAdjustments = Seq(SideCodeAdjustment(segment.id, SideCode.BothDirections))
      (Seq(segment.copy(sideCode = SideCode.BothDirections)), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments))
    } else {
      (segments, changeSet)
    }
  }

  private def adjustSideCodeOnOneWayLink(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
      (segments, changeSet)
    } else {
      val (twoSided, oneSided) = segments.partition { s => s.sideCode == SideCode.BothDirections }
      val adjusted = oneSided.map { s => (s.copy(sideCode = SideCode.BothDirections), SideCodeAdjustment(s.id, SideCode.BothDirections)) }
      (twoSided ++ adjusted.map(_._1), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ adjusted.map(_._2)))
    }
  }

  private def dropRedundantSegments(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val headOption = segments.headOption
    val valueShared = segments.length > 1 && headOption.exists(first => segments.forall(_.value == first.value))
    valueShared match {
      case true =>
        val first = headOption.get
        val rest = segments.tail
        val segmentDrops = rest.map(_.id).toSet
        (Seq(first), changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ segmentDrops))
      case false => (segments, changeSet)
    }
  }

  private def dropShortLimits(roadLinks: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val limitsToDrop = speedLimits.filter { limit => GeometryUtils.geometryLength(limit.geometry) < MaxAllowedMValueError }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ limitsToDrop))
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: RoadLink, segmentsOnLink: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MaxAllowedMValueError}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, segment._1, segment._2)
      SpeedLimit(0, roadLink.linkId, SideCode.BothDirections, roadLink.trafficDirection, None, geometry, segment._1, segment._2, None, None, None, None, 0, None)
    }
  }

  private def dropSegmentsOutsideGeometry(roadLink: RoadLink, assets: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val (segmentsWithinGeometry, segmentsOutsideGeometry) = assets.partition(_.startMeasure < roadLink.length)
    val droppedAssetIds = segmentsOutsideGeometry.map(_.id).toSet
    (segmentsWithinGeometry, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedAssetIds))
  }

  def fillTopology(roadLinks: Seq[RoadLink], speedLimits: Map[Long, Seq[SpeedLimit]]): (Seq[SpeedLimit], ChangeSet) = {
    val fillOperations: Seq[(RoadLink, Seq[SpeedLimit], ChangeSet) => (Seq[SpeedLimit], ChangeSet)] = Seq(
      dropSegmentsOutsideGeometry,
      dropRedundantSegments,
      adjustSegmentMValues,
      capToGeometry,
      adjustLopsidedLimit,
      adjustSideCodeOnOneWayLink,
      dropShortLimits
    )

    val initialChangeSet = ChangeSet(Set.empty, Nil, Nil)

    roadLinks.foldLeft(Seq.empty[SpeedLimit], initialChangeSet) { case (acc, roadLink) =>
      val (existingSegments, changeSet) = acc
      val segments = speedLimits.getOrElse(roadLink.linkId, Nil)
      val validSegments = segments.filterNot { segment => changeSet.droppedAssetIds.contains(segment.id) }

      val (adjustedSegments, segmentAdjustments) = fillOperations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }

      val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, adjustedSegments)
      (existingSegments ++ adjustedSegments ++ generatedSpeedLimits, segmentAdjustments)
    }
  }

  def projectSpeedLimit(asset: SpeedLimit, to: RoadLink, projection: Projection) = {
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
    if (oldLength * newLength < 0) {
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
      newStart = projection.newStart + (asset.startMeasure - projection.oldEnd) * Math.abs(newLength/oldLength)
      newEnd = projection.newEnd + (asset.endMeasure - projection.oldStart) * Math.abs(newLength/oldLength)
    }

    newStart = Math.min(to.length, Math.max(0.0, newStart))
    newEnd = Math.max(0.0, Math.min(to.length, newEnd))

    val geometry = GeometryUtils.truncateGeometry(
      Seq(GeometryUtils.calculatePointFromLinearReference(to.geometry, newStart).getOrElse(to.geometry.head),
        GeometryUtils.calculatePointFromLinearReference(to.geometry, newEnd).getOrElse(to.geometry.last)),
      0, to.length)

    SpeedLimit(id = assetId, linkId = newLinkId, sideCode = newSideCode, trafficDirection = newDirection,
      asset.value, geometry, newStart, newEnd,
      modifiedBy = asset.modifiedBy,
      modifiedDateTime = asset.modifiedDateTime, createdBy = asset.createdBy, createdDateTime = asset.createdDateTime,
      vvhTimeStamp = projection.vvhTimeStamp, vvhModifiedDate = None
    )
  }

  case class Projection(oldStart: Double, oldEnd: Double, newStart: Double, newEnd: Double, vvhTimeStamp: Long)
}
