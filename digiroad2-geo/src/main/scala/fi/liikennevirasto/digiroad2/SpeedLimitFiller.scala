package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitDTO, RoadLinkForSpeedLimit}

object SpeedLimitFiller {
  case class AdjustedSpeedLimitSegment(speedLimitSegment: SpeedLimitDTO, adjustedMValue: Option[Double])
  case class MValueAdjustment(assetId: Long, mmlId: Long, startMeasure: Double, endMeasure: Double)
  case class SpeedLimitChangeSet(droppedSpeedLimitIds: Set[Long], adjustedMValues: Seq[MValueAdjustment])

  private val MaxAllowedMValueError = 0.5

  private def adjustSegment(link: SpeedLimitDTO, roadLink: RoadLinkForSpeedLimit): (SpeedLimitDTO, Seq[MValueAdjustment]) = {
    val startError = link.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - link.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(link.assetId, link.mmlId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = link.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }

  private def adjustTwoWaySegments(topology: Map[Long, RoadLinkForSpeedLimit],
                                   speedLimits: Map[Long, Seq[SpeedLimitDTO]],
                                   segments: Seq[SpeedLimitDTO]):
  (Seq[SpeedLimitDTO], Seq[MValueAdjustment]) = {
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
                                   speedLimits: Map[Long, Seq[SpeedLimitDTO]],
                                   segments: Seq[SpeedLimitDTO],
                                   runningDirection: Int):
  (Seq[SpeedLimitDTO], Seq[MValueAdjustment]) = {
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
                         speedLimits: Map[Long, Seq[SpeedLimitDTO]],
                         segments: Seq[SpeedLimitDTO]):
  (Seq[SpeedLimitDTO], Seq[MValueAdjustment]) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(topology, speedLimits, segments, 2)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(topology, speedLimits, segments, 3)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(topology, speedLimits, segments)
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments)
  }

  private def dropSpeedLimitsWithEmptySegments(speedLimits: Map[Long, Seq[SpeedLimitDTO]]): Set[Long] = {
    speedLimits.filter { case (id, segments) => segments.exists(_.geometry.isEmpty) }.keySet
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: RoadLinkForSpeedLimit, segmentsOnLink: Seq[SpeedLimitDTO]): Seq[SpeedLimitDTO] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MaxAllowedMValueError}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, segment._1, segment._2)
      SpeedLimitDTO(0, roadLink.mmlId, 1, None, geometry, segment._1, segment._2, None, None, None, None)
    }
  }

  def fillTopology(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimitDTO]]): (Seq[SpeedLimitDTO], SpeedLimitChangeSet) = {
    val roadLinks = topology.values
    val speedLimitSegments: Seq[SpeedLimitDTO] = speedLimits.values.flatten.toSeq

    val speedLimitsWithEmptySegments = dropSpeedLimitsWithEmptySegments(speedLimits)
    val initialChangeSet = SpeedLimitChangeSet(speedLimitsWithEmptySegments, Nil)
    val (fittedSpeedLimitSegments: Seq[SpeedLimitDTO], changeSet: SpeedLimitChangeSet) =
      roadLinks.foldLeft(Seq.empty[SpeedLimitDTO], initialChangeSet) { case (acc, roadLink) =>
        val (existingSegments, changeSet) = acc
        val segments = speedLimitSegments.filter(_.mmlId == roadLink.mmlId)
        val validSegments = segments.filterNot { segment => changeSet.droppedSpeedLimitIds.contains(segment.assetId) }
        val validLimits = speedLimits.filterNot { sl => changeSet.droppedSpeedLimitIds.contains(sl._1) }

        val (adjustedSegments: Seq[SpeedLimitDTO], mValueAdjustments: Seq[MValueAdjustment]) = adjustSpeedLimits(topology, validLimits, validSegments)

        val maintainedSegments = adjustedSegments
        val speedLimitDrops = Set.empty[Long]

        val newChangeSet = changeSet.copy(
          droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ speedLimitDrops,
          adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments)

        val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, maintainedSegments)
        (existingSegments ++ maintainedSegments ++ generatedSpeedLimits, newChangeSet)
      }

    val (generatedLimits, existingLimits) = fittedSpeedLimitSegments.partition(_.assetId == 0)
    (existingLimits ++ generatedLimits, changeSet)
  }
}
