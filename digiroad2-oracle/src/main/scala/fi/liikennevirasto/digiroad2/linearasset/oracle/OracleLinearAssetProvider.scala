package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, Queries}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.user.UserProvider

import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}

class OracleLinearAssetProvider extends LinearAssetProvider {
  type PointIndex = Int
  type PointPosition = Int
  type SegmentIndex = Int

  def generatePositionIndices(segments: Seq[(Point, Point)]): Seq[Int] = {
    def fetchFromIndexedSegments(indexedSegments: Seq[((Point, Point), SegmentIndex)], index: PointIndex): Point = {
      val segment = indexedSegments(pointIndexToSegmentIndex(index))
      index % 2 match {
        case 0 => segment._1._1
        case 1 => segment._1._2
      }
    }

    def segmentIndexToPointIndices(segmentIndex: SegmentIndex): (PointIndex, PointIndex) = {
      (2 * segmentIndex, 2 * segmentIndex + 1)
    }

    def pointIndexToSegmentIndex(pointIndex: PointIndex): SegmentIndex = {
      pointIndex / 2
    }

    def findFriendIndex(pointIndex: PointIndex): PointIndex = {
      pointIndex % 2 match {
        case 0 => pointIndex + 1
        case 1 => pointIndex - 1
      }
    }

    def isSegmentStartPoint(point: (PointIndex, (PointIndex, Double))): Boolean = {
      val (index, _) = point
      index % 2 == 0
    }

    val indexedSegments = segments.zipWithIndex
    if (indexedSegments.length == 1) Seq(0)
    else {
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

      val shortestDistances: Seq[(PointIndex, (PointIndex, Double))] = distances.map { distancesFromPoint =>
        val (point, distances) = distancesFromPoint
        val shortestDistance = distances.toList.sortBy(_._2).head
        (point, shortestDistance)
      }

      val startingPoint: PointIndex = shortestDistances.filter(isSegmentStartPoint).sortWith { (point1, point2) =>
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

  private def toSpeedLimit(linkAndPositionNumber: ((Long, Long, Int, Int, Seq[(Double, Double)]), Int)): SpeedLimitLink = {
    val ((id, roadLinkId, sideCode, limit, points), positionNumber) = linkAndPositionNumber
    SpeedLimitLink(id, roadLinkId, sideCode, limit, points.map { case (x, y) => Point(x, y) }, positionNumber)
  }

  private def getLinkEndpoints(link: (Long, Long, Int, Int, Seq[(Double, Double)])): (Point, Point) = {
    val (_, _, _, _, points) = link
    val firstPoint: Point = points.head match { case (x, y) => Point(x, y) }
    val lastPoint: Point = points.last match { case (x, y) => Point(x, y) }
    (firstPoint, lastPoint)
  }

  private def getLinksWithPositions(links: Seq[(Long, Long, Int, Int, Seq[(Double, Double)])]): Seq[SpeedLimitLink] = {
    val linkEndpoints: Seq[(Point, Point)] = links.map(getLinkEndpoints)
    val positionNumbers = generatePositionIndices(linkEndpoints)
    links.zip(positionNumbers).map(toSpeedLimit)
  }

  override def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimitLink] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.getSpeedLimitLinksByBoundingBox(bounds).groupBy(_._1).mapValues(getLinksWithPositions).values.flatten.toSeq
    }
  }

  def calculateSpeedLimitEndPoints(links: List[(Point, Point)]): Set[Point] = {
    val positionIndices = generatePositionIndices(links)
    val linksWithPositionIndices: Seq[((Point, Point), Int)] = links.zip(positionIndices)
    val orderedLinks = linksWithPositionIndices.sortBy(_._2)
    Set(orderedLinks.head._1._1, orderedLinks.last._1._2)
 }

  override def getSpeedLimit(segmentId: Long): Option[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      val links = OracleLinearAssetDao.getSpeedLimits(segmentId)
      if (links.isEmpty) None
      else {
        val points: List[(Point, Point)] = links.map { link =>
          val first = link._2.head
          val last = link._2.last
          (Point(first._1, first._2), Point(last._1, last._2))
        }.toList
        val endpoints = calculateSpeedLimitEndPoints(points)
        val (modifiedBy, modifiedDateTime, createdBy, createdDateTime, limit, speedLimitLinks) = OracleLinearAssetDao.getSpeedLimitDetails(segmentId)
        Some(SpeedLimit(segmentId, limit, endpoints,
                        modifiedBy, modifiedDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
                        createdBy, createdDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
                        getLinksWithPositions(speedLimitLinks)))
      }
    }
  }

  override def updateSpeedLimitValue(id: Long, value: Int, username: String): Option[Long] = {
    Database.forDataSource(ds).withDynTransaction {
      val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption("rajoitus").get
      val assetsUpdated = Queries.updateAssetModified(id, username).first
      val propertiesUpdated = Queries.updateSingleChoiceProperty(id, propertyId, value.toLong).first
      if (assetsUpdated == 1 && propertiesUpdated == 1) {
        Some(id)
      } else {
        dynamicSession.rollback()
        None
      }
    }
  }

  override def splitSpeedLimit(id: Long, roadLinkId: Long, splitMeasure: Double, username: String): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.splitSpeedLimit(id, roadLinkId, splitMeasure, username)
    }
  }
}
