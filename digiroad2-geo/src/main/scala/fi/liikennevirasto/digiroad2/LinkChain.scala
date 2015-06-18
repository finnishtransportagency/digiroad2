package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.GeometryDirection.GeometryDirection
import fi.liikennevirasto.digiroad2.LinkChain.{LinkIndex, LinkPosition}

object GeometryDirection extends Enumeration {
  type GeometryDirection = Value
  val TowardsLinkChain, AgainstLinkChain = Value
}

object LinkChain {
  type PointIndex = Int
  type PointPosition = Int
  type SegmentIndex = Int
  type LinkPosition = Int
  type LinkIndex = Int

  def apply[T](rawLinks: Seq[T], fetchLinkEndPoints: (T) => (Point, Point)): LinkChain[T] = {
    val endPoints: Seq[(Point, Point)] = rawLinks.map(fetchLinkEndPoints)
    val (segmentPositions, geometryRunningDirections) = generateLinkPositionsAndGeometryRunningDirections(endPoints)
    val chainedLinks: Seq[ChainedLink[T]] = rawLinks
      .zipWithIndex
      .zip(segmentPositions)
      .sortBy(_._2)
      .zip(geometryRunningDirections).map { case (((link, linkIndex), linkPosition), geometryRunningDirection) => new ChainedLink[T](link, linkIndex, linkPosition, geometryRunningDirection) }

    new LinkChain[T](chainedLinks, fetchLinkEndPoints)
  }

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

  private def shorterDistance(referencePoint: Point, distance: Option[(PointIndex, Double)], pointIndex: PointIndex, indexedSegments: Seq[((Point, Point), Int)]): Option[(PointIndex, Double)] = {
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

  // TODO: Return both parameters in same order and embed in one segment
  private def generateLinkPositionsAndGeometryRunningDirections(segments: Seq[(Point, Point)]): (Seq[LinkPosition], Seq[GeometryDirection]) = {
    def findFriendIndex(pointIndex: PointIndex): PointIndex = {
      pointIndex % 2 match {
        case 0 => pointIndex + 1
        case 1 => pointIndex - 1
      }
    }

    val indexedSegments = segments.zipWithIndex
    if (indexedSegments.length == 1) (Seq(0), Seq(GeometryDirection.TowardsLinkChain))
    else {
      val shortestDistances: Seq[(PointIndex, (PointIndex, Double))] = shortestDistancesBetweenLinkEndPoints(indexedSegments)

      val startingPoint: PointIndex = shortestDistances.sortWith { (point1, point2) =>
        point1._2._2 > point2._2._2
      }.head._1

      val segmentPositionsAndRunningDirections: (Seq[(SegmentIndex, LinkPosition)], Seq[GeometryDirection]) = indexedSegments.foldLeft((startingPoint, (Seq.empty[(SegmentIndex, LinkPosition)], Seq.empty[GeometryDirection]))) { (acc, indexedSegment) =>
        val (_, segmentPosition) = indexedSegment
        val (pointIndex, (segmentIndices, geometryDirections)) = acc
        val friend = findFriendIndex(pointIndex)
        val geometryDirection = if (friend > pointIndex) GeometryDirection.TowardsLinkChain else GeometryDirection.AgainstLinkChain
        val closestNeighbourOfFriend = shortestDistances(friend)._2._1
        (closestNeighbourOfFriend, (segmentIndices :+ (pointIndexToSegmentIndex(pointIndex) -> segmentPosition), geometryDirections :+ geometryDirection))
      }._2

      val segmentPositions = segmentPositionsAndRunningDirections._1.sortBy(_._1).map(_._2)
      (segmentPositions, segmentPositionsAndRunningDirections._2)
    }
  }
}

class ChainedLink[T](val rawLink: T, val linkIndex: LinkIndex, val linkPosition: LinkPosition, val geometryDirection: GeometryDirection)

class LinkChain[T](val links: Seq[ChainedLink[T]], val fetchLinkEndPoints: (T) => (Point, Point)) {
  import GeometryDirection._

  def endPoints(): (Point, Point) = {
    val firstLinkEndPoints = fetchLinkEndPoints(links.head.rawLink)
    val lastLinkEndPoints = fetchLinkEndPoints(links.last.rawLink)
    (links.head.geometryDirection, links.last.geometryDirection) match {
      case (TowardsLinkChain, TowardsLinkChain) => (firstLinkEndPoints._1, lastLinkEndPoints._2)
      case (TowardsLinkChain, AgainstLinkChain) => (firstLinkEndPoints._1, lastLinkEndPoints._1)
      case (AgainstLinkChain, TowardsLinkChain) => (firstLinkEndPoints._2, lastLinkEndPoints._2)
      case (AgainstLinkChain, AgainstLinkChain) => (firstLinkEndPoints._2, lastLinkEndPoints._1)
    }
  }

  def linkGaps(): Seq[Double] = {
    links.zip(links.tail).map { case (current, next) =>
      val firstLinkEndPoints = fetchLinkEndPoints(current.rawLink)
      val secondLinkEndPoints = fetchLinkEndPoints(next.rawLink)

      val points = (current.geometryDirection, next.geometryDirection) match {
        case (TowardsLinkChain, TowardsLinkChain) => (firstLinkEndPoints._2, secondLinkEndPoints._1)
        case (TowardsLinkChain, AgainstLinkChain) => (firstLinkEndPoints._2, secondLinkEndPoints._2)
        case (AgainstLinkChain, TowardsLinkChain) => (firstLinkEndPoints._1, secondLinkEndPoints._1)
        case (AgainstLinkChain, AgainstLinkChain) => (firstLinkEndPoints._1, secondLinkEndPoints._2)
      }
      points._1.distanceTo(points._2)
    }
  }

  def withoutEndSegments(): LinkChain[T] = {
    val tailLinks = links match {
      case x :: xs => xs
      case _ => Nil
    }
    val middleLinks = tailLinks match {
      case Nil => Nil
      case xs => xs.init
    }
    new LinkChain[T](middleLinks, fetchLinkEndPoints)
  }

  def existsOnMiddleSegments(p: (ChainedLink[T]) => Boolean) : Boolean = {
    withoutEndSegments().links.exists(p)
  }

  def find(p: (ChainedLink[T]) => Boolean) : Option[ChainedLink[T]] = {
    links.find(p)
  }

  def head(): ChainedLink[T] = links.head

  def last(): ChainedLink[T] = links.last

  def map[A](transformation: (ChainedLink[T]) => A): Seq[A] = {
    links.map(transformation)
  }

  def splitBy(isSplitLink: (T) => Boolean): (LinkChain[T], ChainedLink[T], LinkChain[T]) = {
    val linksBeforeSplit: LinkChain[T] = new LinkChain[T](links.takeWhile { link => !isSplitLink(link.rawLink) }, fetchLinkEndPoints)
    val linksAfterSplit: LinkChain[T] = new LinkChain[T](links.dropWhile { link => !isSplitLink(link.rawLink) }.tail, fetchLinkEndPoints)
    val splitLink: ChainedLink[T] = links.find { link => isSplitLink(link.rawLink) }.get

    (linksBeforeSplit, splitLink, linksAfterSplit)
  }

  def length(linkLength: (T) => Double): Double = {
    links.foldLeft(0.0) { case (acc, link) => acc + linkLength(link.rawLink)}
  }
}

