package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, SideCode}

object SpeedLimitFiller extends LinearAssetFiller {
  private val MaxAllowedMValueError = 0.5

  private def adjustSegment(link: SpeedLimit, roadLink: VVHRoadLinkWithProperties): (SpeedLimit, Seq[MValueAdjustment]) = {
    val startError = link.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - link.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(link.id, link.mmlId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = link.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }

  private def adjustTwoWaySegments(roadLink: VVHRoadLinkWithProperties,
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

  private def adjustOneWaySegments(roadLink: VVHRoadLinkWithProperties,
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

  private def adjustSegmentMValues(roadLink: VVHRoadLinkWithProperties, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.TowardsDigitizing)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.AgainstDigitizing)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(roadLink, segments)
    val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
  }

  private def adjustSegmentSideCodes(roadLink: VVHRoadLinkWithProperties, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    if (segments.length == 1 && segments.head.sideCode != SideCode.BothDirections) {
      val segment = segments.head
      val sideCodeAdjustments = Seq(SideCodeAdjustment(segment.id, SideCode.BothDirections))
      (Seq(segment.copy(sideCode = SideCode.BothDirections)), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments))
    } else {
      (segments, changeSet)
    }
  }

  private def dropRedundantSegments(roadLink: VVHRoadLinkWithProperties, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
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

  private def dropSpeedLimitsWithEmptySegments(speedLimits: Map[Long, Seq[SpeedLimit]]): Set[Long] = {
    speedLimits.filter { case (id, segments) => segments.exists(_.geometry.isEmpty) }.keySet
  }

  private def dropShortLimits(roadLinks: VVHRoadLinkWithProperties, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val limitsToDrop = speedLimits.filter { limit => GeometryUtils.geometryLength(limit.geometry) < MaxAllowedMValueError }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ limitsToDrop))
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: VVHRoadLinkWithProperties, segmentsOnLink: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MaxAllowedMValueError}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, segment._1, segment._2)
      SpeedLimit(0, roadLink.mmlId, SideCode.BothDirections, roadLink.trafficDirection, None, geometry, segment._1, segment._2, None, None, None, None)
    }
  }

  def fillTopology(roadLinks: Seq[VVHRoadLinkWithProperties], speedLimits: Map[Long, Seq[SpeedLimit]]): (Seq[SpeedLimit], ChangeSet) = {
    val fillOperations: Seq[(VVHRoadLinkWithProperties, Seq[SpeedLimit], ChangeSet) => (Seq[SpeedLimit], ChangeSet)] = Seq(
      dropRedundantSegments,
      adjustSegmentMValues,
      adjustSegmentSideCodes,
      dropShortLimits
    )

    val initialChangeSet = ChangeSet(dropSpeedLimitsWithEmptySegments(speedLimits), Nil, Nil, Nil)
    val (fittedSpeedLimitSegments: Seq[SpeedLimit], changeSet: ChangeSet) =
      roadLinks.foldLeft(Seq.empty[SpeedLimit], initialChangeSet) { case (acc, roadLink) =>
        val (existingSegments, changeSet) = acc
        val segments = speedLimits.getOrElse(roadLink.mmlId, Nil)
        val validSegments = segments.filterNot { segment => changeSet.droppedAssetIds.contains(segment.id) }

        val (adjustedSegments, segmentAdjustments) = fillOperations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
          operation(roadLink, currentSegments, currentAdjustments)
        }

        val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, adjustedSegments)
        val municipalityCode = RoadLinkUtility.municipalityCodeFromAttributes(roadLink.attributes)
        val unknownLimits = generatedSpeedLimits.map(_ => UnknownLimit(roadLink.mmlId, municipalityCode, roadLink.administrativeClass))
        (existingSegments ++ adjustedSegments ++ generatedSpeedLimits,
          segmentAdjustments.copy(generatedUnknownLimits = segmentAdjustments.generatedUnknownLimits ++ unknownLimits))
      }

    val (generatedLimits, existingLimits) = fittedSpeedLimitSegments.partition(_.id == 0)
    (existingLimits ++ generatedLimits, changeSet)
  }
}
