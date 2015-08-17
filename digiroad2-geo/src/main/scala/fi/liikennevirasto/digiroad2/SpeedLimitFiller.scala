package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimit, RoadLinkForSpeedLimit}

object SpeedLimitFiller {
  case class AdjustedSpeedLimitSegment(speedLimitSegment: SpeedLimit, adjustedMValue: Option[Double])
  case class MValueAdjustment(assetId: Long, mmlId: Long, startMeasure: Double, endMeasure: Double)
  case class SideCodeAdjustment(assetId: Long, sideCode: SideCode)
  case class SpeedLimitChangeSet(droppedSpeedLimitIds: Set[Long], adjustedMValues: Seq[MValueAdjustment], adjustedSideCodes: Seq[SideCodeAdjustment])

  private val MaxAllowedMValueError = 0.5

  private def adjustSegment(link: SpeedLimit, roadLink: RoadLinkForSpeedLimit): (SpeedLimit, Seq[MValueAdjustment]) = {
    val startError = link.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - link.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(link.id, link.mmlId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = link.copy(points = GeometryUtils.truncateGeometry(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }

  private def adjustTwoWaySegments(topology: Map[Long, RoadLinkForSpeedLimit],
                                   segments: Seq[SpeedLimit]):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val twoWaySegments = segments.filter(_.sideCode == SideCode.BothDirections)
    if (twoWaySegments.length == 1 && segments.forall(_.sideCode == SideCode.BothDirections)) {
      val segment = segments.head
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, topology.get(segment.mmlId).get)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (twoWaySegments, Nil)
    }
  }

  private def adjustOneWaySegments(topology: Map[Long, RoadLinkForSpeedLimit],
                                   segments: Seq[SpeedLimit],
                                   runningDirection: SideCode):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = segments.filter(_.sideCode == runningDirection)
    if (segmentsTowardsRunningDirection.length == 1 && segments.filter(_.sideCode == SideCode.BothDirections).isEmpty) {
      val segment = segmentsTowardsRunningDirection.head
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, topology.get(segment.mmlId).get)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (segmentsTowardsRunningDirection, Nil)
    }
  }

  private def adjustSegmentMValues(topology: Map[Long, RoadLinkForSpeedLimit], segments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(topology, segments, SideCode.TowardsDigitizing)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(topology, segments, SideCode.AgainstDigitizing)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(topology, segments)
    val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
  }

  private def adjustSegmentSideCodes(topology: Map[Long, RoadLinkForSpeedLimit], segments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    if (segments.length == 1 && segments.head.sideCode != SideCode.BothDirections) {
      val segment = segments.head
      val sideCodeAdjustments = Seq(SideCodeAdjustment(segment.id, SideCode.BothDirections))
      (Seq(segment.copy(sideCode = SideCode.BothDirections)), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments))
    } else {
      (segments, changeSet)
    }
  }

  private def dropRedundantSegments(topology: Map[Long, RoadLinkForSpeedLimit], segments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val headOption = segments.headOption
    val valueShared = segments.length > 1 && headOption.exists(first => segments.forall(_.value == first.value))
    valueShared match {
      case true =>
        val first = headOption.get
        val rest = segments.tail
        val segmentDrops = rest.map(_.id).toSet
        (Seq(first), changeSet.copy(droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ segmentDrops))
      case false => (segments, changeSet)
    }
  }

  private def dropSpeedLimitsWithEmptySegments(speedLimits: Map[Long, Seq[SpeedLimit]]): Set[Long] = {
    speedLimits.filter { case (id, segments) => segments.exists(_.points.isEmpty) }.keySet
  }

  private def dropShortLimits(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val limitsToDrop = speedLimits.filter { limit => GeometryUtils.geometryLength(limit.points) < MaxAllowedMValueError }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ limitsToDrop))
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: RoadLinkForSpeedLimit, segmentsOnLink: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MaxAllowedMValueError}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, segment._1, segment._2)
      SpeedLimit(0, roadLink.mmlId, SideCode.BothDirections, roadLink.trafficDirection, None, geometry, segment._1, segment._2, None, None, None, None)
    }
  }

  def fillTopology(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimit]]): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val roadLinks = topology.values
    val speedLimitSegments: Seq[SpeedLimit] = speedLimits.values.flatten.toSeq
    val fillOperations: Seq[(Map[Long, RoadLinkForSpeedLimit], Seq[SpeedLimit], SpeedLimitChangeSet) => (Seq[SpeedLimit], SpeedLimitChangeSet)] = Seq(
      dropRedundantSegments,
      adjustSegmentMValues,
      adjustSegmentSideCodes,
      dropShortLimits
    )

    val initialChangeSet = SpeedLimitChangeSet(dropSpeedLimitsWithEmptySegments(speedLimits), Nil, Nil)
    val (fittedSpeedLimitSegments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet) =
      roadLinks.foldLeft(Seq.empty[SpeedLimit], initialChangeSet) { case (acc, roadLink) =>
        val (existingSegments, changeSet) = acc
        val segments = speedLimitSegments.filter(_.mmlId == roadLink.mmlId)
        val validSegments = segments.filterNot { segment => changeSet.droppedSpeedLimitIds.contains(segment.id) }

        val (adjustedSegments, segmentAdjustments) = fillOperations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
          operation(topology, currentSegments, currentAdjustments)
        }

        val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, adjustedSegments)
        (existingSegments ++ adjustedSegments ++ generatedSpeedLimits, segmentAdjustments)
      }

    val (generatedLimits, existingLimits) = fittedSpeedLimitSegments.partition(_.id == 0)
    (existingLimits ++ generatedLimits, changeSet)
  }
}
