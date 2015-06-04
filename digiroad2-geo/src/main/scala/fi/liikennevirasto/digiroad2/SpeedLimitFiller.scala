package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.GeometryDirection
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitLink, RoadLinkForSpeedLimit}
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.TowardsLinkChain
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.AgainstLinkChain

object SpeedLimitFiller {
  private def hasEmptySegments(speedLimit: (Long, Seq[(Long, Long, Int, Option[Int], Seq[Point])])): Boolean = {
    val (_, links) = speedLimit
    links.exists { case (_, _, _, _, geometry) => geometry.isEmpty}
  }

  private def getLinkEndpoints(link: (Long, Long, Int, Option[Int], Seq[Point])): (Point, Point) = {
    val (_, _, _, _, points) = link
    GeometryUtils.geometryEndpoints(points)
  }

  private def adjustSpeedLimit(linkGeometries: Map[Long, RoadLinkForSpeedLimit])(speedLimit: (Long, Seq[(Long, Long, Int, Option[Int], Seq[Point])])):
  (Long, Seq[(Long, Long, Int, Option[Int], Seq[Point])]) = {
    val (id, links) = speedLimit
    if (links.length > 2) {
      val linkChain = LinkChain(links, getLinkEndpoints)
      val middleSegments = linkChain.withoutEndSegments()
      val adjustedSegments = middleSegments.map { chainedLink =>
        val roadLinkGeometry = linkGeometries.get(chainedLink.rawLink._2).map(_.geometry)
        roadLinkGeometry.map { newGeometry =>
          chainedLink.rawLink.copy(_5 = newGeometry)
        }.getOrElse(chainedLink.rawLink)
      }
      id -> (Seq(linkChain.head().rawLink) ++ adjustedSegments ++ Seq(linkChain.last().rawLink))
    }
    else {
      speedLimit
    }
  }

  private def hasGaps(speedLimit: (Long, Seq[(Long, Long, Int, Option[Int], Seq[Point])])) = {
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

  private def getLinksWithPositions(links: Seq[(Long, Long, Int, Option[Int], Seq[Point])]): Seq[SpeedLimitLink] = {
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val (id, roadLinkId, sideCode, limit, points) = chainedLink.rawLink
      toSpeedLimit((id, roadLinkId, sideCode, limit, points, chainedLink.linkPosition, chainedLink.geometryDirection))
    }
  }

  def fillTopology(topology: Map[Long, RoadLinkForSpeedLimit], speedLimits: Map[Long, Seq[(Long, Long, Int, Option[Int], Seq[Point])]]): (Seq[SpeedLimitLink], Set[Long]) = {
    val (speedLimitsWithEmptySegments, speedLimitsWithoutEmptySegments) = speedLimits.partition(hasEmptySegments)
    val adjustedSpeedLimits = speedLimitsWithoutEmptySegments.map(adjustSpeedLimit(topology))
    val (speedLimitsWithGaps, validLimits) = adjustedSpeedLimits.partition(hasGaps)

    val filledTopology = validLimits.mapValues(getLinksWithPositions).values.flatten.toSeq
    val droppedSpeedLimitIds = speedLimitsWithEmptySegments.keySet ++ speedLimitsWithGaps.keySet

    (filledTopology, droppedSpeedLimitIds)
  }
}
