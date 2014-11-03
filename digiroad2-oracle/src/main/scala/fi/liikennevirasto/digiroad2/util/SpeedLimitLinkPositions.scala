package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.util.LinkChain.{GeometryRunningDirection, LinkPosition}

object LinkChain {
  type LinkPosition = Int
  type GeometryRunningDirection = Boolean
  
  def apply[T](rawLinks: Seq[T], fetchLinkEndPoints: (T) => (Point, Point)) = {
    val endPoints: Seq[(Point, Point)] = rawLinks.map(fetchLinkEndPoints)
    val (segmentPositions, geometryRunningDirections) = SpeedLimitLinkPositions.generateLinkPositionsAndGeometryRunningDirections(endPoints)
    val chainedLinks: Seq[ChainedLink[T]] = rawLinks
      .zip(segmentPositions)
      .sortBy(_._2)
      .zip(geometryRunningDirections).map { case ((link, segmentIndex), geometryRunningDirection) => new ChainedLink[T](link, segmentIndex, geometryRunningDirection) }

    new LinkChain[T](chainedLinks)
  }
}

class ChainedLink[T](val rawLink: T, val linkPosition: LinkPosition, val geometryRunningDirection: GeometryRunningDirection) {}

class LinkChain[T](val links: Seq[ChainedLink[T]]) {
  def linkPositions(): Seq[LinkPosition] = {
    links.map(_.linkPosition)
  }

  def splitBy(isSplitLink: (T) => Boolean): (LinkChain[T], ChainedLink[T], LinkChain[T]) = {
    val linksBeforeSplit: LinkChain[T] = new LinkChain[T](links.takeWhile { link => !isSplitLink(link.rawLink) })
    val linksAfterSplit: LinkChain[T] = new LinkChain[T](links.dropWhile { link => !isSplitLink(link.rawLink) }.tail)
    val splitLink: ChainedLink[T] = links.find { link => isSplitLink(link.rawLink) }.get

    (linksBeforeSplit, splitLink, linksAfterSplit)
  }

  def length(linkLength: (T) => Double): Double = {
    links.foldLeft(0.0) { case (acc, link) => acc + linkLength(link.rawLink)}
  }

  def rawLinks(): Seq[T] = {
    links.map(_.rawLink)
  }
}

object SpeedLimitLinkPositions {
  type PointIndex = Int
  type PointPosition = Int
  type SegmentIndex = Int
  type SegmentPosition = Int
  type GeometryRunningDirection = Boolean

  private def pointIndexToSegmentIndex(pointIndex: PointIndex): SegmentIndex = {
    pointIndex / 2
  }

  private def fetchFromIndexedSegments(indexedSegments: Seq[((Point, Point), SegmentIndex)], index: PointIndex): Point = {
    val segment = indexedSegments(pointIndexToSegmentIndex(index))
    index % 2 match {
      case 0 => segment._1._1
      case 1 => segment._1._2
    }
  }

  private def segmentIndexToPointIndices(segmentIndex: SegmentIndex): (PointIndex, PointIndex) = {
    (2 * segmentIndex, 2 * segmentIndex + 1)
  }

  def shorterDistance(referencePoint: Point, distance: Option[(PointIndex, Double)], pointIndex: PointIndex, indexedSegments: Seq[((Point, Point), Int)]): Option[(PointIndex, Double)] = {
    val point = fetchFromIndexedSegments(indexedSegments, pointIndex)
    distance match {
      case Some((_, minDistance)) => if (referencePoint.distanceTo(point) < minDistance) Some(pointIndex, referencePoint.distanceTo(point)) else distance
      case None => Some(pointIndex, referencePoint.distanceTo(point))
    }
  }

  private def shortestDistancesBetweenLinkEndPoints(indexedSegments: Seq[((Point, Point), Int)]): Seq[(PointIndex, (PointIndex, Double))] = {
    indexedSegments.foldLeft(Seq.empty[(PointIndex, (PointIndex, Double))]) { (acc, indexedSegment) =>
      val (segment, index) = indexedSegment
      val pointsToCompare: List[PointIndex] = indexedSegments.foldLeft(List.empty[PointIndex]) { (acc, otherIndexedSegment) =>
        val (_, otherIndex) = otherIndexedSegment
        if (index == otherIndex) acc
        else {
          val (firstIndex, secondIndex) = segmentIndexToPointIndices(otherIndex)
          acc ++ List(firstIndex, secondIndex)
        }
      }

      val shortestDistances: (Option[(PointIndex, Double)], Option[(PointIndex, Double)]) = pointsToCompare.foldLeft((None, None): (Option[(PointIndex, Double)], Option[(PointIndex, Double)])) { (acc, pointIndex) =>
        val firstEndPointOfSegment = segment._1
        val secondEndPointOfSegment = segment._2
        (shorterDistance(firstEndPointOfSegment, acc._1, pointIndex, indexedSegments), shorterDistance(secondEndPointOfSegment, acc._2, pointIndex, indexedSegments))
      }
      val (firstIndex, secondIndex) = segmentIndexToPointIndices(index)
      acc ++ Seq((firstIndex, shortestDistances._1.get), (secondIndex, shortestDistances._2.get))
    }
  }

  def generateLinkPositionsAndGeometryRunningDirections(segments: Seq[(Point, Point)]): (Seq[SegmentPosition], Seq[GeometryRunningDirection]) = {
   def findFriendIndex(pointIndex: PointIndex): PointIndex = {
      pointIndex % 2 match {
        case 0 => pointIndex + 1
        case 1 => pointIndex - 1
      }
    }

    val indexedSegments = segments.zipWithIndex
    if (indexedSegments.length == 1) (Seq(0), Seq(true))
    else {
      val shortestDistances: Seq[(PointIndex, (PointIndex, Double))] = shortestDistancesBetweenLinkEndPoints(indexedSegments)

      val startingPoint: PointIndex = shortestDistances.sortWith { (point1, point2) =>
        point1._2._2 > point2._2._2
      }.head._1

      val segmentPositionsAndRunningDirections: (Seq[(SegmentIndex, SegmentPosition)], Seq[GeometryRunningDirection]) = indexedSegments.foldLeft((startingPoint, (Seq.empty[(SegmentIndex, SegmentPosition)], Seq.empty[GeometryRunningDirection]))) { (acc, indexedSegment) =>
        val (_, segmentPosition) = indexedSegment
        val (pointIndex, (segmentIndices, geometryDirections)) = acc
        val friend = findFriendIndex(pointIndex)
        val geometryRunningDirection = friend > pointIndex
        val closestNeighbourOfFriend = shortestDistances(friend)._2._1
        (closestNeighbourOfFriend, (segmentIndices :+ (pointIndexToSegmentIndex(pointIndex) -> segmentPosition), geometryDirections :+ geometryRunningDirection))
      }._2

      val segmentPositions = segmentPositionsAndRunningDirections._1.sortBy(_._1).map(_._2)
      (segmentPositions, segmentPositionsAndRunningDirections._2)
    }
  }

  def calculateLinkChainEndPoints(segments: Seq[(Point, Point)]): (Point, Point) = {
    val indexedSegments = segments.zipWithIndex
    if (indexedSegments.length == 1) segments.head
    else {
      val shortestDistances: Seq[(PointIndex, (PointIndex, Double))] = shortestDistancesBetweenLinkEndPoints(indexedSegments)

      val sortedPoints: Seq[(PointIndex, (PointIndex, Double))] = shortestDistances.sortWith { (point1, point2) =>
        point1._2._2 > point2._2._2
      }

      (fetchFromIndexedSegments(indexedSegments, sortedPoints(0)._1), fetchFromIndexedSegments(indexedSegments, sortedPoints(1)._1))
    }
  }
}
