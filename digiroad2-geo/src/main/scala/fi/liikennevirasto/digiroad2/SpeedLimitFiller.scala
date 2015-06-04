package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.GeometryDirection
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitDTO, SpeedLimitLink, RoadLinkForSpeedLimit}
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.TowardsLinkChain
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.AgainstLinkChain

object SpeedLimitFiller {
  private def hasEmptySegments(speedLimit: (Long, Seq[SpeedLimitDTO])): Boolean = {
    val (_, links) = speedLimit
    links.exists { _.geometry.isEmpty}
  }

  private def getLinkEndpoints(link: SpeedLimitDTO): (Point, Point) = {
    GeometryUtils.geometryEndpoints(link.geometry)
  }

  private def adjustSpeedLimit(linkGeometries: Map[Long, RoadLinkForSpeedLimit])(speedLimit: (Long, Seq[SpeedLimitDTO])):
  (Long, Seq[SpeedLimitDTO]) = {
    val (id, links) = speedLimit
    if (links.length > 2) {
      val linkChain = LinkChain(links, getLinkEndpoints)
      val middleSegments = linkChain.withoutEndSegments()
      val adjustedSegments = middleSegments.map { chainedLink =>
        val roadLinkGeometry = linkGeometries.get(chainedLink.rawLink.mmlId).map(_.geometry)
        roadLinkGeometry.map { newGeometry =>
          chainedLink.rawLink.copy(geometry = newGeometry)
        }.getOrElse(chainedLink.rawLink)
      }
      id -> (Seq(linkChain.head().rawLink) ++ adjustedSegments ++ Seq(linkChain.last().rawLink))
    }
    else {
      speedLimit
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

  def fillTopology(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[SpeedLimitDTO]]): (Seq[SpeedLimitLink], Set[Long]) = {
    val (speedLimitsWithEmptySegments, speedLimitsWithoutEmptySegments) = speedLimits.partition(hasEmptySegments)
    val adjustedSpeedLimits = speedLimitsWithoutEmptySegments.map(adjustSpeedLimit(topology))
    val (speedLimitsWithGaps, validLimits) = adjustedSpeedLimits.partition(hasGaps)

    val filledTopology = validLimits.mapValues(getLinksWithPositions).values.flatten.toSeq
    val droppedSpeedLimitIds = speedLimitsWithEmptySegments.keySet ++ speedLimitsWithGaps.keySet

    (filledTopology, droppedSpeedLimitIds)
  }
}
