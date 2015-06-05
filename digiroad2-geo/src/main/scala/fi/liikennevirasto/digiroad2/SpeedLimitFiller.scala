package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.GeometryDirection.GeometryDirection
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitDTO, SpeedLimitLink, RoadLinkForSpeedLimit}

object SpeedLimitFiller {
  import GeometryDirection._

  case class AdjustedSpeedLimitSegment(speedLimitSegment: SpeedLimitDTO, adjustedMValue: Option[Double])
  case class MValueAdjustment(assetId: Long, mmlId: Long, endMeasure: Double)
  case class SpeedLimitChangeSet(droppedSpeedLimitIds: Set[Long], adjustedMValues: Seq[MValueAdjustment])

  private val MaxAllowedMValueError = 0.5

  private def hasEmptySegments(speedLimit: (Long, Seq[SpeedLimitDTO])): Boolean = {
    val (_, links) = speedLimit
    links.exists { _.geometry.isEmpty}
  }

  private def getLinkEndpoints(link: SpeedLimitDTO): (Point, Point) = {
    GeometryUtils.geometryEndpoints(link.geometry)
  }

  private def adjustSpeedLimit(linkGeometries: Map[Long, RoadLinkForSpeedLimit])(speedLimit: (Long, Seq[SpeedLimitDTO])):
  (Long, Seq[AdjustedSpeedLimitSegment]) = {
    val (id, links) = speedLimit
    if (links.length > 2) {
      val linkChain = LinkChain(links, getLinkEndpoints)
      val middleSegments = linkChain.withoutEndSegments()
      val adjustedSegments = middleSegments.map { chainedLink =>
        val optionalRoadLinkGeometry = linkGeometries.get(chainedLink.rawLink.mmlId).map(_.geometry)
        optionalRoadLinkGeometry.map { roadLinkGeometry =>
          val mValueError = Math.abs(GeometryUtils.geometryLength(roadLinkGeometry) - GeometryUtils.geometryLength(chainedLink.rawLink.geometry))
          if (mValueError > MaxAllowedMValueError)
            AdjustedSpeedLimitSegment(chainedLink.rawLink.copy(geometry = roadLinkGeometry), Some(GeometryUtils.geometryLength(roadLinkGeometry)))
          else
            AdjustedSpeedLimitSegment(chainedLink.rawLink, None)
        }.getOrElse(AdjustedSpeedLimitSegment(chainedLink.rawLink, None))
      }
      val headSegment = AdjustedSpeedLimitSegment(linkChain.head().rawLink, None)
      val lastSegment = AdjustedSpeedLimitSegment(linkChain.last().rawLink, None)
      id -> (Seq(headSegment) ++ adjustedSegments ++ Seq(lastSegment))
    }
    else {
      (id, links.map(AdjustedSpeedLimitSegment(_, None)))
    }
  }

  private def hasGaps(speedLimit: (Long, Seq[SpeedLimitDTO])) = {
    val (_, links) = speedLimit
    val maximumGapThreshold = 1
    LinkChain(links, getLinkEndpoints).linkGaps().exists(_ > maximumGapThreshold)
  }

  private def toSpeedLimit(linkAndPositionNumber: (Long, Long, Int, Option[Int], Seq[Point], Int, GeometryDirection)): SpeedLimitLink = {
    val (id, roadLinkId, sideCode, limit, points, positionNumber, geometryDirection) = linkAndPositionNumber

    val towardsLinkChain = geometryDirection match {
      case TowardsLinkChain => true
      case AgainstLinkChain => false
    }

    SpeedLimitLink(id, roadLinkId, sideCode, limit, points, positionNumber, towardsLinkChain)
  }

  private def getLinksWithPositions(links: Seq[SpeedLimitDTO]): Seq[SpeedLimitLink] = {
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val link = chainedLink.rawLink
      toSpeedLimit((link.assetId, link.mmlId, link.sideCode, link.value, link.geometry, chainedLink.linkPosition, chainedLink.geometryDirection))
    }
  }

  def fillTopology(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimitDTO]]): (Seq[SpeedLimitLink], SpeedLimitChangeSet) = {
    val (speedLimitsWithEmptySegments, speedLimitsWithoutEmptySegments) = speedLimits.partition(hasEmptySegments)
    val (adjustedSpeedLimits, adjustedMValues) =  speedLimitsWithoutEmptySegments
      .foldLeft(Map.empty[Long, Seq[SpeedLimitDTO]], Seq.empty[MValueAdjustment]) { case (acc, speedLimit) =>
      val (id, adjustedSegments) = adjustSpeedLimit(topology)(speedLimit)
      val (speedLimits, mValues) = acc
      val mValueAdjustments = adjustedSegments.flatMap { segment =>
        segment.adjustedMValue.map { mValue =>
          MValueAdjustment(segment.speedLimitSegment.assetId, segment.speedLimitSegment.mmlId, mValue)
        }
      }
      (speedLimits + (id -> adjustedSegments.map(_.speedLimitSegment)), mValues ++ mValueAdjustments)
    }
    val (speedLimitsWithGaps, validLimits) = adjustedSpeedLimits.partition(hasGaps)

    val filledTopology = validLimits.mapValues(getLinksWithPositions).values.flatten.toSeq

    val droppedSpeedLimitIds = speedLimitsWithEmptySegments.keySet ++ speedLimitsWithGaps.keySet
    val changeSet = SpeedLimitChangeSet(droppedSpeedLimitIds, adjustedMValues)

    (filledTopology, changeSet)
  }

  // TODO: Implement me.
  def adjustSpeedLimit2(speedLimitId: Long, speedLimitSegments: Seq[SpeedLimitDTO], topology: Map[Long, RoadLinkForSpeedLimit]): (LinkChain[SpeedLimitDTO], Seq[MValueAdjustment]) = {
    (LinkChain[SpeedLimitDTO](Nil, { _ => (Point(0.0, 0.0), Point(0.0, 0.0))}), Nil)
  }

  // TODO: Implement me.
  def adjustSpeedLimits2(topology: Map[Long, RoadLinkForSpeedLimit],
                         speedLimits: Map[Long, Seq[SpeedLimitDTO]],
                         segments: Seq[SpeedLimitDTO],
                         adjustedSpeedLimits: Map[Long, LinkChain[SpeedLimitDTO]]):
  (Seq[ChainedLink[SpeedLimitDTO]], Seq[LinkChain[SpeedLimitDTO]], Seq[MValueAdjustment]) = {
    /*
    val (adjustedSegments: Seq[ChainedLink[SpeedLimitDTO]], adjustedSpeedLimit: Option[LinkChain[SpeedLimitDTO]], mValueAdjustments: Seq[MValueAdjustment]) =
      if (validSegments.length == 1) {
        val segment = validSegments.head
        val alreadyAdjustedSpeedLimit: Option[LinkChain[SpeedLimitDTO]] = adjustedSpeedLimits.get(segment.assetId)
        alreadyAdjustedSpeedLimit
          .flatMap(_.find(_.rawLink.mmlId == segment.mmlId))
          .map { s => (Seq(s), None, Nil)}
          .getOrElse {
          val (speedLimit, adjustments) = adjustSpeedLimit2(segment.assetId, speedLimitSegments, topology)
          (Seq(speedLimit.find(_.rawLink.mmlId == segment.mmlId)), Some(speedLimit), adjustments)
        }
      } else {
        val adjustedSegments: Seq[ChainedLink[SpeedLimitDTO]] = validSegments.map { s => new ChainedLink[SpeedLimitDTO](s, 0, 0, TowardsLinkChain)}
        (adjustedSegments, None, Nil)
      }*/
    (Nil, Nil, Nil)
  }

  // TODO: Implement me.
  def dropSpeedLimits2(adjustedSpeedLimitsOnLink: Seq[LinkChain[SpeedLimitDTO]], adjustedSegments: Seq[ChainedLink[SpeedLimitDTO]]): (Seq[ChainedLink[SpeedLimitDTO]], Set[Long]) = {
    (Nil, Set.empty[Long])
  }

  def fillTopology2(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimitDTO]]): (Seq[SpeedLimitLink], SpeedLimitChangeSet) = {
    val roadLinks = topology.values
    val speedLimitSegments: Seq[SpeedLimitDTO] = speedLimits.values.flatten.toSeq

    val (fittedSpeedLimitSegments: Seq[SpeedLimitDTO], changeSet: SpeedLimitChangeSet, adjustedSpeedLimits: Map[Long, LinkChain[SpeedLimitDTO]]) =
      roadLinks.foldLeft(Seq.empty[SpeedLimitDTO], SpeedLimitChangeSet(Set.empty[Long], Nil), Map.empty[Long, LinkChain[SpeedLimitDTO]]) { case (acc, roadLink) =>
        val (existingSegments, changeSet, adjustedSpeedLimits) = acc
        val segments = speedLimitSegments.filter(_.mmlId == roadLink.mmlId)
        val validSegments = segments.filterNot { segment => changeSet.droppedSpeedLimitIds.contains(segment.assetId) }

        val (adjustedSegments: Seq[ChainedLink[SpeedLimitDTO]],
        adjustedSpeedLimitsOnLink: Seq[LinkChain[SpeedLimitDTO]],
        mValueAdjustments: Seq[MValueAdjustment]) = adjustSpeedLimits2(topology, speedLimits, validSegments, adjustedSpeedLimits)

        val (maintainedSegments: Seq[ChainedLink[SpeedLimitDTO]], speedLimitDrops: Set[Long]) = dropSpeedLimits2(adjustedSpeedLimitsOnLink, adjustedSegments)

        val newChangeSet = changeSet.copy(
          droppedSpeedLimitIds = changeSet.droppedSpeedLimitIds ++ speedLimitDrops,
          adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments)
        val newAdjustedSpeedLimits = adjustedSpeedLimits ++ adjustedSpeedLimitsOnLink.map { sl => sl.head().rawLink.assetId -> sl }
        (existingSegments ++ maintainedSegments.map(_.rawLink), newChangeSet, newAdjustedSpeedLimits)
    }

    (getLinksWithPositions(fittedSpeedLimitSegments), changeSet)
  }
}
