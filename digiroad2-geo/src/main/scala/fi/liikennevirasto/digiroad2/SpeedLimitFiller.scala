package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.GeometryDirection.GeometryDirection
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

  def adjustSpeedLimit(speedLimit: LinkChain[SpeedLimitDTO], topology: Map[Long, RoadLinkForSpeedLimit]): (LinkChain[SpeedLimitDTO], Seq[MValueAdjustment]) = {
    if (speedLimit.links.length > 2) {
      val headLink = speedLimit.head()
      val lastLink = speedLimit.last()
      val middleSegments = speedLimit.withoutEndSegments().links

      val (modifiedMiddleLinks: Seq[ChainedLink[SpeedLimitDTO]], mValueAdjustments: Seq[MValueAdjustment]) = middleSegments
        .foldLeft(Seq.empty[ChainedLink[SpeedLimitDTO]], Seq.empty[MValueAdjustment]) { case (acc, segment) =>
        val (accModifiedMiddleLinks, accMValueAdjustments) = acc
        val optionalRoadLinkGeometry = topology.get(segment.rawLink.mmlId).map(_.geometry)

        val (newGeometry, mValueAdjustments) = optionalRoadLinkGeometry.map { roadLinkGeometry =>
          val mValueError = Math.abs(GeometryUtils.geometryLength(roadLinkGeometry) - GeometryUtils.geometryLength(segment.rawLink.geometry))
          if (mValueError > MaxAllowedMValueError) {
            (roadLinkGeometry, Seq(MValueAdjustment(segment.rawLink.assetId, segment.rawLink.mmlId, GeometryUtils.geometryLength(roadLinkGeometry))))
          } else {
            (roadLinkGeometry, Nil)
          }
        }.getOrElse((segment.rawLink.geometry, Nil))

        val newDTO = segment.rawLink.copy(geometry = newGeometry)
        val newMiddleLink = new ChainedLink[SpeedLimitDTO](newDTO, segment.linkIndex, segment.linkPosition, segment.geometryDirection)
        (accModifiedMiddleLinks ++ Seq(newMiddleLink), accMValueAdjustments ++ mValueAdjustments)
      }

      (new LinkChain[SpeedLimitDTO](Seq(headLink) ++ modifiedMiddleLinks ++ Seq(lastLink), speedLimit.fetchLinkEndPoints), mValueAdjustments)
    } else {
      (speedLimit, Nil)
    }
  }

  def adjustSpeedLimits(topology: Map[Long, RoadLinkForSpeedLimit],
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

  def hasEmptyGeometry(segment: ChainedLink[SpeedLimitDTO]): Boolean = {
    segment.rawLink.geometry.isEmpty
  }

  def dropSpeedLimits(adjustedSpeedLimitsOnLink: Seq[LinkChain[SpeedLimitDTO]], adjustedSegments: Seq[ChainedLink[SpeedLimitDTO]]): (Seq[ChainedLink[SpeedLimitDTO]], Set[Long]) = {
    val droppedSpeedLimits = adjustedSpeedLimitsOnLink.filter { sl => sl.linkGaps().exists(_ > MaxAllowedGapSize) }.map(_.head().rawLink.assetId).toSet
    val maintainedSegments = adjustedSegments.filterNot { segment => droppedSpeedLimits.contains(segment.rawLink.assetId) }
    (maintainedSegments, droppedSpeedLimits)
  }

  def dropSpeedLimitsWithEmptySegments(speedLimits: Map[Long, Seq[SpeedLimitDTO]]): Set[Long] = {
    speedLimits.filter { case (id, segments) => segments.exists(_.geometry.isEmpty) }.keySet
  }

  def fillTopology(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimitDTO]]): (Seq[SpeedLimitLink], SpeedLimitChangeSet) = {
    val roadLinks = topology.values
    val speedLimitSegments: Seq[SpeedLimitDTO] = speedLimits.values.flatten.toSeq

    val speedLimitsWithEmptySegments = dropSpeedLimitsWithEmptySegments(speedLimits)
    val initialChangeSet = SpeedLimitChangeSet(speedLimitsWithEmptySegments, Nil)
    val (fittedSpeedLimitSegments: Seq[SpeedLimitDTO], changeSet: SpeedLimitChangeSet, adjustedSpeedLimits: Map[Long, LinkChain[SpeedLimitDTO]]) =
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
        (existingSegments ++ maintainedSegments.map(_.rawLink), newChangeSet, newAdjustedSpeedLimits)
    }

    (fittedSpeedLimitSegments.groupBy(_.assetId).values.map(getLinksWithPositions).flatten.toSeq, changeSet)
  }
}
