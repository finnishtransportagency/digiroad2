package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimit, RoadLinkForSpeedLimit}

object SpeedLimitFiller {
  case class AdjustedSpeedLimitSegment(speedLimitSegment: SpeedLimit, adjustedMValue: Option[Double])
  case class MValueAdjustment(assetId: Long, mmlId: Long, startMeasure: Double, endMeasure: Double)
  case class SideCodeAdjustment(assetId: Long, sideCode: Int)
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
                                   speedLimits: Map[Long, Seq[SpeedLimit]],
                                   segments: Seq[SpeedLimit]):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val twoWaySegments = segments.filter(_.sideCode == 1)
    if (twoWaySegments.length == 1 && segments.forall(_.sideCode == 1)) {
      val segment = segments.head
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, topology.get(segment.mmlId).get)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (twoWaySegments, Nil)
    }
  }

  private def adjustOneWaySegments(topology: Map[Long, RoadLinkForSpeedLimit],
                                   speedLimits: Map[Long, Seq[SpeedLimit]],
                                   segments: Seq[SpeedLimit],
                                   runningDirection: Int):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = segments.filter(_.sideCode == runningDirection)
    if (segmentsTowardsRunningDirection.length == 1 && segments.filter(_.sideCode == 1).isEmpty) {
      val segment = segmentsTowardsRunningDirection.head
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, topology.get(segment.mmlId).get)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (segmentsTowardsRunningDirection, Nil)
    }
  }

  private def adjustSpeedLimits(topology: Map[Long, RoadLinkForSpeedLimit],
                         speedLimits: Map[Long, Seq[SpeedLimit]],
                         segments: Seq[SpeedLimit]):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(topology, speedLimits, segments, 2)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(topology, speedLimits, segments, 3)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(topology, speedLimits, segments)
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments)
  }

  private def adjustLimitSideCodes(segments: Seq[SpeedLimit]): (Seq[SpeedLimit], Seq[SideCodeAdjustment]) = {
    segments match {
      case s :: Nil =>
        val adjustedSegments = Seq(s.copy(sideCode = 1))
        val adjustments = if (s.sideCode != 1) Seq(SideCodeAdjustment(s.id, 1)) else Nil
        (adjustedSegments, adjustments)
      case _ => (segments, Nil)
    }
  }

  private def dropSpeedLimitsWithEmptySegments(speedLimits: Map[Long, Seq[SpeedLimit]]): Set[Long] = {
    speedLimits.filter { case (id, segments) => segments.exists(_.points.isEmpty) }.keySet
  }

  private def dropShortLimits(speedLimits: Seq[SpeedLimit]): (Seq[SpeedLimit], Set[Long]) = {
    val limitsToDrop = speedLimits.filter { limit => GeometryUtils.geometryLength(limit.points) < MaxAllowedMValueError }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, limitsToDrop)
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: RoadLinkForSpeedLimit, segmentsOnLink: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MaxAllowedMValueError}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, segment._1, segment._2)
      SpeedLimit(0, roadLink.mmlId, 1, None, geometry, segment._1, segment._2, None, None, None, None)
    }
  }

  def fillTopology(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimit]]): (Seq[SpeedLimit], SpeedLimitChangeSet) = {
    val roadLinks = topology.values
    val speedLimitSegments: Seq[SpeedLimit] = speedLimits.values.flatten.toSeq

    val initialChangeSet = SpeedLimitChangeSet(dropSpeedLimitsWithEmptySegments(speedLimits), Nil, Nil)
    val (fittedSpeedLimitSegments: Seq[SpeedLimit], changeSet: SpeedLimitChangeSet) =
      roadLinks.foldLeft(Seq.empty[SpeedLimit], initialChangeSet) { case (acc, roadLink) =>
        val (existingSegments, changeSet) = acc
        val segments = speedLimitSegments.filter(_.mmlId == roadLink.mmlId)
        val validSegments = segments.filterNot { segment => changeSet.droppedSpeedLimitIds.contains(segment.id) }
        val validLimits = speedLimits.filterNot { sl => changeSet.droppedSpeedLimitIds.contains(sl._1) }

        val (adjustedSegments: Seq[SpeedLimit], mValueAdjustments: Seq[MValueAdjustment]) = adjustSpeedLimits(topology, validLimits, validSegments)
        val (sideCodeAdjustedSegments, sideCodeAdjustments) = adjustLimitSideCodes(adjustedSegments)

        val (maintainedSegments, speedLimitDrops) = dropShortLimits(sideCodeAdjustedSegments)

        val newChangeSet = changeSet.copy(
          droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ speedLimitDrops,
          adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments,
          adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments)

        val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, maintainedSegments)
        (existingSegments ++ maintainedSegments ++ generatedSpeedLimits, newChangeSet)
      }

    val (generatedLimits, existingLimits) = fittedSpeedLimitSegments.partition(_.id == 0)
    (existingLimits ++ generatedLimits, changeSet)
  }
}
