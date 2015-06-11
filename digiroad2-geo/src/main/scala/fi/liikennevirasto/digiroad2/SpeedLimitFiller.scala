package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitDTO, SpeedLimitLink, RoadLinkForSpeedLimit}

object SpeedLimitFiller {
  import GeometryDirection._

  case class AdjustedSpeedLimitSegment(speedLimitSegment: SpeedLimitDTO, adjustedMValue: Option[Double])
  case class MValueAdjustment(assetId: Long, mmlId: Long, endMeasure: Double)
  case class SpeedLimitChangeSet(droppedSpeedLimitIds: Set[Long], adjustedMValues: Seq[MValueAdjustment])

  private val MaxAllowedMValueError = 0.5
  private val MaxAllowedGapSize = 1.0

  private def getLinkEndpoints(link: SpeedLimitDTO): (Point, Point) = {
    GeometryUtils.geometryEndpoints(link.geometry)
  }

  private def toSpeedLimit(linkAndPositionNumber: (Long, Long, Int, Option[Int], Seq[Point], Int, GeometryDirection)): SpeedLimitLink = {
    val (id, mmlId, sideCode, limit, points, positionNumber, geometryDirection) = linkAndPositionNumber

    val towardsLinkChain = geometryDirection match {
      case TowardsLinkChain => true
      case AgainstLinkChain => false
    }

    SpeedLimitLink(id, mmlId, sideCode, limit, points, positionNumber, towardsLinkChain)
  }

  private def getLinksWithPositions(links: Seq[SpeedLimitDTO]): Seq[SpeedLimitLink] = {
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val link = chainedLink.rawLink
      toSpeedLimit((link.assetId, link.mmlId, link.sideCode, link.value, link.geometry, chainedLink.linkPosition, chainedLink.geometryDirection))
    }
  }

  private def adjustMiddleSegments(middleSegments: Seq[ChainedLink[SpeedLimitDTO]], topology: Map[Long, RoadLinkForSpeedLimit]): (Seq[ChainedLink[SpeedLimitDTO]], Seq[MValueAdjustment]) = {
    middleSegments.foldLeft(Seq.empty[ChainedLink[SpeedLimitDTO]], Seq.empty[MValueAdjustment]) { case (acc, segment) =>
      val (accModifiedMiddleLinks, accMValueAdjustments) = acc
      val optionalRoadLinkGeometry = topology.get(segment.rawLink.mmlId).map(_.geometry)

      val (newGeometry, mValueAdjustment) = optionalRoadLinkGeometry.map { roadLinkGeometry =>
        val mValueError = Math.abs(GeometryUtils.geometryLength(roadLinkGeometry) - GeometryUtils.geometryLength(segment.rawLink.geometry))
        if (mValueError > MaxAllowedMValueError) {
          (roadLinkGeometry, Some(MValueAdjustment(segment.rawLink.assetId, segment.rawLink.mmlId, GeometryUtils.geometryLength(roadLinkGeometry))))
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
        Seq(MValueAdjustment(rawLink.assetId, rawLink.mmlId, roadLinkLength))
      else
        Nil
    val modifiedSegment = rawLink.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, rawLink.startMeasure, roadLinkLength), endMeasure = roadLinkLength)
    val modifiedLink = new ChainedLink[SpeedLimitDTO](modifiedSegment, link.linkIndex, link.linkPosition, link.geometryDirection)
    (modifiedLink, mAdjustment)
  }

  private def adjustEndSegments(headLink: ChainedLink[SpeedLimitDTO], lastLink: ChainedLink[SpeedLimitDTO], topology: Map[Long, RoadLinkForSpeedLimit]): (ChainedLink[SpeedLimitDTO], ChainedLink[SpeedLimitDTO], Seq[MValueAdjustment]) = {
    val (modifiedHeadLink, mValueAdjustments) = headLink.geometryDirection match {
      case AgainstLinkChain => (headLink, Nil)
      case TowardsLinkChain => adjustEndSegment(headLink, topology.get(headLink.rawLink.mmlId).get)
    }
    val (modifiedLastLink, lastLinkMValueAdjustments) = lastLink.geometryDirection match {
      case TowardsLinkChain => (lastLink, Nil)
      case AgainstLinkChain => adjustEndSegment(lastLink, topology.get(lastLink.rawLink.mmlId).get)
    }
    (modifiedHeadLink, modifiedLastLink, mValueAdjustments ++ lastLinkMValueAdjustments)
  }

  private def adjustSpeedLimit(speedLimit: LinkChain[SpeedLimitDTO], topology: Map[Long, RoadLinkForSpeedLimit]): (LinkChain[SpeedLimitDTO], Seq[MValueAdjustment]) = {
    if (speedLimit.links.length > 1) {
      val headLink = speedLimit.head()
      val lastLink = speedLimit.last()
      val middleSegments = speedLimit.withoutEndSegments().links

      val (modifiedHeadLink: ChainedLink[SpeedLimitDTO], modifiedLastLink: ChainedLink[SpeedLimitDTO], mValueAdjustments: Seq[MValueAdjustment]) = adjustEndSegments(headLink, lastLink, topology)
      val (modifiedMiddleLinks: Seq[ChainedLink[SpeedLimitDTO]], middleSegmentMValueAdjustments: Seq[MValueAdjustment]) = adjustMiddleSegments(middleSegments, topology)

      (new LinkChain[SpeedLimitDTO](Seq(modifiedHeadLink) ++ modifiedMiddleLinks ++ Seq(modifiedLastLink), speedLimit.fetchLinkEndPoints), mValueAdjustments ++ middleSegmentMValueAdjustments)
    } else {
      (speedLimit, Nil)
    }
  }

  private def adjustSpeedLimits(topology: Map[Long, RoadLinkForSpeedLimit],
                         speedLimits: Map[Long, Seq[SpeedLimitDTO]],
                         segments: Seq[SpeedLimitDTO],
                         adjustedSpeedLimits: Map[Long, LinkChain[SpeedLimitDTO]]):
  (Seq[ChainedLink[SpeedLimitDTO]], Seq[LinkChain[SpeedLimitDTO]], Seq[MValueAdjustment]) = {

    segments.foldLeft(Seq.empty[ChainedLink[SpeedLimitDTO]], Seq.empty[LinkChain[SpeedLimitDTO]], Seq.empty[MValueAdjustment]) { case(acc, segment) =>
      val (accAdjustedSegments, accAdjustedSpeedLimits, accMValueAdjustments) = acc
      val (adjustedSegment, adjustedSpeedLimit, mValueAdjustments) = adjustedSpeedLimits
        .get(segment.assetId)
        .map { sl => (sl.find(_.rawLink.mmlId == segment.mmlId), sl, Nil) }
        .getOrElse {
        val speedLimit = LinkChain(speedLimits.get(segment.assetId).get, getLinkEndpoints)
        val (adjustedSpeedLimit, mValueAdjustments) = adjustSpeedLimit(speedLimit, topology)
        (adjustedSpeedLimit.find(_.rawLink.mmlId == segment.mmlId), adjustedSpeedLimit, mValueAdjustments)
      }
      (accAdjustedSegments ++ adjustedSegment.toList, accAdjustedSpeedLimits ++ Seq(adjustedSpeedLimit), accMValueAdjustments ++ mValueAdjustments)
    }
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
        mValueAdjustments: Seq[MValueAdjustment]) = adjustSpeedLimits(topology, validLimits, validSegments, adjustedSpeedLimits)

        val (maintainedSegments: Seq[ChainedLink[SpeedLimitDTO]], speedLimitDrops: Set[Long]) = dropSpeedLimits(adjustedSpeedLimitsOnLink, adjustedSegments)

        val newChangeSet = changeSet.copy(
          droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ speedLimitDrops,
          adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments)
        val newAdjustedSpeedLimits = adjustedSpeedLimits ++ adjustedSpeedLimitsOnLink.map { sl => sl.head().rawLink.assetId -> sl }

        val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, maintainedSegments.map(_.rawLink))
        (existingSegments ++ maintainedSegments.map(_.rawLink) ++ generatedSpeedLimits, newChangeSet, newAdjustedSpeedLimits)
    }

    val (generatedLimits, existingLimits) = fittedSpeedLimitSegments.partition(_.assetId == 0)
    val generatedTopology = generatedLimits.map(link => toSpeedLimit((link.assetId, link.mmlId, link.sideCode, link.value, link.geometry, 0, TowardsLinkChain)))
    val fittedTopology = existingLimits.groupBy(_.assetId).values.map(getLinksWithPositions).flatten.toSeq
    val filledTopology = fittedTopology ++ generatedTopology

    (filledTopology, changeSet)
  }
}
