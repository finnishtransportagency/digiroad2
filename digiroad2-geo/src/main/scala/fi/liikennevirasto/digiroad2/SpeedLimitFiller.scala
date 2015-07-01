package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitDTO, SpeedLimitLink, RoadLinkForSpeedLimit}

object SpeedLimitFiller {
  import GeometryDirection._

  case class AdjustedSpeedLimitSegment(speedLimitSegment: SpeedLimitDTO, adjustedMValue: Option[Double])
  case class MValueAdjustment(assetId: Long, mmlId: Long, startMeasure: Double, endMeasure: Double)
  case class SpeedLimitChangeSet(droppedSpeedLimitIds: Set[Long], adjustedMValues: Seq[MValueAdjustment])

  private val MaxAllowedMValueError = 0.5
  private val MaxAllowedGapSize = 1.0

  private def getLinkEndpoints(link: SpeedLimitDTO): (Point, Point) = {
    GeometryUtils.geometryEndpoints(link.geometry)
  }

  private def getLinksWithPositions(links: Seq[SpeedLimitDTO]): Seq[SpeedLimitLink] = {
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val link = chainedLink.rawLink
      SpeedLimitLink(link.assetId, link.mmlId, link.sideCode, link.value,
        link.geometry, link.startMeasure, link.endMeasure,
        chainedLink.linkPosition, chainedLink.geometryDirection == TowardsLinkChain)
    }
  }

  private def adjustMiddleSegments(middleSegments: Seq[ChainedLink[SpeedLimitDTO]], topology: Map[Long, RoadLinkForSpeedLimit]): (Seq[ChainedLink[SpeedLimitDTO]], Seq[MValueAdjustment]) = {
    middleSegments.foldLeft(Seq.empty[ChainedLink[SpeedLimitDTO]], Seq.empty[MValueAdjustment]) { case (acc, segment) =>
      val (accModifiedMiddleLinks, accMValueAdjustments) = acc
      val optionalRoadLinkGeometry = topology.get(segment.rawLink.mmlId).map(_.geometry)

      val (newGeometry, mValueAdjustment) = optionalRoadLinkGeometry.map { roadLinkGeometry =>
        val mValueError = Math.abs(GeometryUtils.geometryLength(roadLinkGeometry) - GeometryUtils.geometryLength(segment.rawLink.geometry))
        if (mValueError > MaxAllowedMValueError) {
          (roadLinkGeometry, Some(MValueAdjustment(segment.rawLink.assetId, segment.rawLink.mmlId, 0, GeometryUtils.geometryLength(roadLinkGeometry))))
        } else {
          (roadLinkGeometry, None)
        }
      }.getOrElse((segment.rawLink.geometry, None))

      val newDTO = segment.rawLink.copy(geometry = newGeometry, endMeasure = GeometryUtils.geometryLength(newGeometry))
      val newMiddleLink = new ChainedLink[SpeedLimitDTO](newDTO, segment.linkIndex, segment.linkPosition, segment.geometryDirection)
      (accModifiedMiddleLinks ++ Seq(newMiddleLink), accMValueAdjustments ++ mValueAdjustment)
    }
  }

  private def adjustEndSegment(link: ChainedLink[SpeedLimitDTO], roadLink: RoadLinkForSpeedLimit): (ChainedLink[SpeedLimitDTO], Seq[MValueAdjustment]) = {
    val rawLink = link.rawLink
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val mError = roadLinkLength - rawLink.endMeasure
    val mAdjustment =
      if (mError > MaxAllowedMValueError)
        Seq(MValueAdjustment(rawLink.assetId, rawLink.mmlId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = rawLink.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, rawLink.startMeasure, roadLinkLength), endMeasure = roadLinkLength)
    val modifiedLink = new ChainedLink[SpeedLimitDTO](modifiedSegment, link.linkIndex, link.linkPosition, link.geometryDirection)
    (modifiedLink, mAdjustment)
  }

  private def adjustSegment(link: SpeedLimitDTO, roadLink: RoadLinkForSpeedLimit): (ChainedLink[SpeedLimitDTO], Seq[MValueAdjustment]) = {
    val startError = link.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - link.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(link.assetId, link.mmlId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = link.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    val modifiedLink = new ChainedLink[SpeedLimitDTO](modifiedSegment, 0, 0, TowardsLinkChain)
    (modifiedLink, mAdjustment)
  }

  private def adjustSpeedLimit(speedLimit: SpeedLimitDTO, topology: Map[Long, RoadLinkForSpeedLimit]): (LinkChain[SpeedLimitDTO], Seq[MValueAdjustment]) = {
    val (modifiedLink: ChainedLink[SpeedLimitDTO], mValueAdjustments: Seq[MValueAdjustment]) = adjustSegment(speedLimit, topology.get(speedLimit.mmlId).get)
    (new LinkChain[SpeedLimitDTO](Seq(modifiedLink), getLinkEndpoints), mValueAdjustments)
  }

  private def segmentToLinkChain(segment: SpeedLimitDTO): (ChainedLink[SpeedLimitDTO], LinkChain[SpeedLimitDTO]) = {
    val linkChain = LinkChain(Seq(segment), getLinkEndpoints)
    val chainedLink = linkChain.head()
    (chainedLink, linkChain)
  }

  private def adjustTwoWaySegments(topology: Map[Long, RoadLinkForSpeedLimit],
                                   speedLimits: Map[Long, Seq[SpeedLimitDTO]],
                                   segments: Seq[SpeedLimitDTO]):
  (Seq[ChainedLink[SpeedLimitDTO]], Seq[LinkChain[SpeedLimitDTO]], Seq[MValueAdjustment]) = {
    val twoWaySegments = segments.filter(_.sideCode == 1)
    if (twoWaySegments.length == 1 && segments.forall(_.sideCode == 1)) {
      val (adjustedSpeedLimit, mValueAdjustments) = adjustSpeedLimit(segments.head, topology)
      (Seq(adjustedSpeedLimit.head()), Seq(adjustedSpeedLimit), mValueAdjustments)
    } else {
      val (adjustedSegments, adjustedSpeedLimits) = twoWaySegments
        .foldLeft(Seq.empty[ChainedLink[SpeedLimitDTO]], Seq.empty[LinkChain[SpeedLimitDTO]]) { case(acc, segment) =>
        val (accAdjustedSegments, accAdjustedSpeedLimits) = acc
        val (chainedLink, linkChain) = segmentToLinkChain(segment)
        (chainedLink +: accAdjustedSegments, linkChain +: accAdjustedSpeedLimits)
      }
      (adjustedSegments, adjustedSpeedLimits, Nil)
    }
  }

  private def adjustOneWaySegments(topology: Map[Long, RoadLinkForSpeedLimit],
                                   speedLimits: Map[Long, Seq[SpeedLimitDTO]],
                                   segments: Seq[SpeedLimitDTO],
                                   runningDirection: Int):
  (Seq[ChainedLink[SpeedLimitDTO]], Seq[LinkChain[SpeedLimitDTO]], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = segments.filter(_.sideCode == runningDirection)
    if (segmentsTowardsRunningDirection.length == 1 && segments.filter(_.sideCode == 1).isEmpty) {
      val (adjustedSpeedLimit, mValueAdjustments) = adjustSpeedLimit(segmentsTowardsRunningDirection.head, topology)
      (Seq(adjustedSpeedLimit.head()), Seq(adjustedSpeedLimit), mValueAdjustments)
    } else {
      val (adjustedSegments, adjustedSpeedLimits) = segmentsTowardsRunningDirection
        .foldLeft(Seq.empty[ChainedLink[SpeedLimitDTO]], Seq.empty[LinkChain[SpeedLimitDTO]]) { case (acc, segment) =>
        val (accAdjustedSegments, accAdjustedSpeedLimits) = acc
        val (chainedLink, linkChain) = segmentToLinkChain(segment)
        (chainedLink +: accAdjustedSegments, linkChain +: accAdjustedSpeedLimits)
      }
      (adjustedSegments, adjustedSpeedLimits, Nil)
    }
  }

  private def adjustSpeedLimits(topology: Map[Long, RoadLinkForSpeedLimit],
                         speedLimits: Map[Long, Seq[SpeedLimitDTO]],
                         segments: Seq[SpeedLimitDTO]):
  (Seq[ChainedLink[SpeedLimitDTO]], Seq[LinkChain[SpeedLimitDTO]], Seq[MValueAdjustment]) = {
    val (towardsGeometrySegments, towardsGeometrySpeedLimits, towardsGeometryAdjustments) = adjustOneWaySegments(topology, speedLimits, segments, 2)
    val (againstGeometrySegments, againstGeometrySpeedLimits, againstGeometryAdjustments) = adjustOneWaySegments(topology, speedLimits, segments, 3)
    val (twoWayGeometrySegments, twoWayGeometrySpeedLimits, twoWayGeometryAdjustments) = adjustTwoWaySegments(topology, speedLimits, segments)
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      towardsGeometrySpeedLimits ++ againstGeometrySpeedLimits ++ twoWayGeometrySpeedLimits,
      towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments)
  }

  private def dropSpeedLimits(adjustedSpeedLimitsOnLink: Seq[LinkChain[SpeedLimitDTO]], adjustedSegments: Seq[ChainedLink[SpeedLimitDTO]]): (Seq[ChainedLink[SpeedLimitDTO]], Set[Long]) = {
    val droppedSpeedLimits = adjustedSpeedLimitsOnLink.filter { sl => sl.linkGaps().exists(_ > MaxAllowedGapSize) }.map(_.head().rawLink.assetId).toSet
    val maintainedSegments = adjustedSegments.filterNot { segment => droppedSpeedLimits.contains(segment.rawLink.assetId) }
    (maintainedSegments, droppedSpeedLimits)
  }

  private def dropSpeedLimitsWithEmptySegments(speedLimits: Map[Long, Seq[SpeedLimitDTO]]): Set[Long] = {
    speedLimits.filter { case (id, segments) => segments.exists(_.geometry.isEmpty) }.keySet
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: RoadLinkForSpeedLimit, segmentsOnLink: Seq[SpeedLimitDTO]): Seq[SpeedLimitDTO] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, segment._1, segment._2)
      SpeedLimitDTO(0, roadLink.mmlId, 1, None, geometry, segment._1, segment._2)
    }
  }

  def fillTopology(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimitDTO]]): (Seq[SpeedLimitLink], SpeedLimitChangeSet) = {
    val roadLinks = topology.values
    val speedLimitSegments: Seq[SpeedLimitDTO] = speedLimits.values.flatten.toSeq

    val speedLimitsWithEmptySegments = dropSpeedLimitsWithEmptySegments(speedLimits)
    val initialChangeSet = SpeedLimitChangeSet(speedLimitsWithEmptySegments, Nil)
    val (fittedSpeedLimitSegments: Seq[SpeedLimitDTO], changeSet: SpeedLimitChangeSet, _) =
      roadLinks.foldLeft(Seq.empty[SpeedLimitDTO], initialChangeSet, Map.empty[Long, LinkChain[SpeedLimitDTO]]) { case (acc, roadLink) =>
        val (existingSegments, changeSet, adjustedSpeedLimits) = acc
        val segments = speedLimitSegments.filter(_.mmlId == roadLink.mmlId)
        val validSegments = segments.filterNot { segment => changeSet.droppedSpeedLimitIds.contains(segment.assetId) }
        val validLimits = speedLimits.filterNot { sl => changeSet.droppedSpeedLimitIds.contains(sl._1) }

        val (adjustedSegments: Seq[ChainedLink[SpeedLimitDTO]],
        adjustedSpeedLimitsOnLink: Seq[LinkChain[SpeedLimitDTO]],
        mValueAdjustments: Seq[MValueAdjustment]) = adjustSpeedLimits(topology, validLimits, validSegments)

        val (maintainedSegments: Seq[ChainedLink[SpeedLimitDTO]], speedLimitDrops: Set[Long]) = dropSpeedLimits(adjustedSpeedLimitsOnLink, adjustedSegments)

        val newChangeSet = changeSet.copy(
          droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ speedLimitDrops,
          adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments)
        val newAdjustedSpeedLimits = adjustedSpeedLimits ++ adjustedSpeedLimitsOnLink.map { sl => sl.head().rawLink.assetId -> sl }

        val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, maintainedSegments.map(_.rawLink))
        (existingSegments ++ maintainedSegments.map(_.rawLink) ++ generatedSpeedLimits, newChangeSet, newAdjustedSpeedLimits)
    }

    val (generatedLimits, existingLimits) = fittedSpeedLimitSegments.partition(_.assetId == 0)
    val generatedTopology = generatedLimits.map { link =>
      SpeedLimitLink(link.assetId, link.mmlId, link.sideCode, link.value, link.geometry, link.startMeasure, link.endMeasure, 0, true)
    }
    val fittedTopology = existingLimits.groupBy(_.assetId).values.map(getLinksWithPositions).flatten.toSeq

    (fittedTopology ++ generatedTopology, changeSet)
  }
}
