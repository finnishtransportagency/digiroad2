package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point

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

  private def calculateShortestDistancesBetweenLinkEndPoints(indexedSegments: Seq[((Point, Point), Int)]): Seq[(PointIndex, (PointIndex, Double))] = {
    val distances: Seq[(PointIndex, Map[PointIndex, Double])] = indexedSegments.foldLeft(Seq.empty[(PointIndex, Map[PointIndex, Double])]) { (acc, indexedSegment) =>
      val (segment, index) = indexedSegment
      val pointsToCompare: List[PointIndex] = indexedSegments.foldLeft(List.empty[PointIndex]) { (acc, otherIndexedSegment) =>
        val (_, otherIndex) = otherIndexedSegment
        if (index == otherIndex) acc
        else {
          val (firstIndex, secondIndex) = segmentIndexToPointIndices(otherIndex)
          acc ++ List(firstIndex, secondIndex)
        }
      }

      val indexedPoints = pointsToCompare.foldLeft(Map.empty[PointIndex, Point]) { (acc, idx) =>
        acc + (idx -> fetchFromIndexedSegments(indexedSegments, idx))
      }

      val distancesFromFirstPoint: Map[PointIndex, Double] = indexedPoints.mapValues(segment._1.distanceTo)
      val distancesFromSecondPoint: Map[PointIndex, Double] = indexedPoints.mapValues(segment._2.distanceTo)
      val (firstIndex, secondIndex) = segmentIndexToPointIndices(index)
      acc ++ Seq((firstIndex, distancesFromFirstPoint), (secondIndex, distancesFromSecondPoint))
    }

    distances.map { distancesFromPoint =>
      val (point, distances) = distancesFromPoint
      val shortestDistance = distances.toList.sortBy(_._2).head
      (point, shortestDistance)
    }
  }

  def generate(segments: Seq[(Point, Point)]): Seq[Int] = {
   def findFriendIndex(pointIndex: PointIndex): PointIndex = {
      pointIndex % 2 match {
        case 0 => pointIndex + 1
        case 1 => pointIndex - 1
      }
    }

    val indexedSegments = segments.zipWithIndex
    if (indexedSegments.length == 1) Seq(0)
    else {
      val shortestDistances: Seq[(PointIndex, (PointIndex, Double))] = calculateShortestDistancesBetweenLinkEndPoints(indexedSegments)

      val startingPoint: PointIndex = shortestDistances.sortWith { (point1, point2) =>
        point1._2._2 > point2._2._2
      }.head._1

      val pointsWithPositionNumbers: Map[PointIndex, PointPosition] = indexedSegments.foldLeft((startingPoint, Map.empty[Int, Int])) { (acc, indexedSegment) =>
        val (_, segmentIndex) = indexedSegment
        val (pointIndex, positionNumbers) = acc
        val friend = findFriendIndex(pointIndex)
        val closestNeighbourOfFriend = shortestDistances(friend)._2._1
        val (pointPositionNumber, friendPositionNumber) = segmentIndexToPointIndices(segmentIndex)
        (closestNeighbourOfFriend, positionNumbers + (pointIndex -> pointPositionNumber) + (friend -> friendPositionNumber))
      }._2

      val positionNumbersInIndexOrder: Seq[(PointIndex, PointPosition)] = pointsWithPositionNumbers.toList.sortBy(_._1)
      val segmentPositionNumbers: Seq[Int] = positionNumbersInIndexOrder.sliding(2, 2).map(_.head).map(_._2).map(pointIndexToSegmentIndex).toList

      segmentPositionNumbers
    }
  }

  case class ChainedLink(linkIndex: Int, geometryRunningTowardsNextLink: Boolean)
  def generate2(segments: Seq[(Point, Point)]): (Seq[SegmentPosition], Seq[GeometryRunningDirection]) = {
   def findFriendIndex(pointIndex: PointIndex): PointIndex = {
      pointIndex % 2 match {
        case 0 => pointIndex + 1
        case 1 => pointIndex - 1
      }
    }

    val indexedSegments = segments.zipWithIndex
    if (indexedSegments.length == 1) (Seq(0), Seq(true))
    else {
      val shortestDistances: Seq[(PointIndex, (PointIndex, Double))] = calculateShortestDistancesBetweenLinkEndPoints(indexedSegments)

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
      val shortestDistances: Seq[(PointIndex, (PointIndex, Double))] = calculateShortestDistancesBetweenLinkEndPoints(indexedSegments)

      val sortedPoints: Seq[(PointIndex, (PointIndex, Double))] = shortestDistances.sortWith { (point1, point2) =>
        point1._2._2 > point2._2._2
      }

      (fetchFromIndexedSegments(indexedSegments, sortedPoints(0)._1), fetchFromIndexedSegments(indexedSegments, sortedPoints(1)._1))
    }
  }
}
