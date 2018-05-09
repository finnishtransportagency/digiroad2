package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Roundabout, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.ImportLogService.getClass
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.slf4j.LoggerFactory

case class RoundaboutChangeSet(trafficDirectionChanges: Seq[RoadLink] = Seq.empty)

object RoundaboutProcessor {

  val logger = LoggerFactory.getLogger(getClass)

  private def isCompletedRoundabout(firstLink: RoadLink, nextLink: RoadLink, acc: Seq[RoadLink]): Boolean = {
    if(acc.size < 3){
      (firstLink.linkId == nextLink.linkId && GeometryUtils.areAdjacent(firstLink.geometry.head, firstLink.geometry.last)) ||
      (firstLink.linkId != nextLink.linkId && GeometryUtils.areAdjacent(firstLink.geometry, nextLink.geometry.head) && GeometryUtils.areAdjacent(firstLink.geometry, nextLink.geometry.last))
    }else{
      firstLink.linkId != nextLink.linkId && GeometryUtils.areAdjacent(firstLink.geometry, nextLink.geometry)
    }
  }

  private def findRoundaboutRecursive(firstLink: RoadLink, nextLink: RoadLink, roadLinks: Seq[RoadLink], acc: Seq[RoadLink] = Seq.empty, withIncomplete: Boolean = true): (Seq[RoadLink], Seq[RoadLink]) = {
    val (adjacents, rest) = roadLinks.partition(r => GeometryUtils.areAdjacent(r.geometry, nextLink.geometry))
    adjacents match{
      case Seq() if isCompletedRoundabout(firstLink, nextLink, acc) =>
        (acc :+ nextLink, rest)
      case Seq() if !withIncomplete => {
        logger.info(s"The roundabout was ignored, with the following road links ${acc.map(_.linkId).mkString(", ")}")
        (Seq.empty, rest)
      }
      case Seq() =>
        (acc :+ nextLink, rest)
      case adjs =>
          findRoundaboutRecursive(firstLink, adjs.head, rest ++ adjs.tail, acc :+ nextLink, withIncomplete)
    }
  }

  private def groupByRoundaboutRecursive(roadLinks: Seq[RoadLink], acc: Seq[Seq[RoadLink]] = Seq.empty, withIncomplete: Boolean = true): Seq[Seq[RoadLink]] = {
    roadLinks match {
      case Seq() => acc
      case rls =>
        val (adjacents, rest) = findRoundaboutRecursive(rls.head, rls.head, rls.tail, withIncomplete = withIncomplete)
        groupByRoundaboutRecursive(rest, if(adjacents.isEmpty) acc else acc :+ adjacents, withIncomplete)
    }
  }

  private def getRoundaboutLinkEndpoints(roadLink: RoadLink, center: Point): (Point, Point) = {
    val firstPoint: Point = roadLink.geometry.head
    val otherPoint: Point = roadLink.geometry.tail.head
    val lastPoint: Point = roadLink.geometry.last

    val firstAngle = GeometryUtils.calculateAngle(firstPoint, center)
    val otherAngle = GeometryUtils.calculateAngle(otherPoint, center)
    val lastAngle = GeometryUtils.calculateAngle(lastPoint, center)

    val sortedAngles = Seq(firstAngle, lastAngle).sorted
    if(otherAngle < sortedAngles.head || otherAngle > sortedAngles.last){
      if(firstAngle > lastAngle) (firstPoint, lastPoint) else (lastPoint, firstPoint)
    } else {
      if(firstAngle < lastAngle) (firstPoint, lastPoint) else (lastPoint, firstPoint)
    }
  }

  private def setRoundaboutChangeSet(roadLinks : Seq[RoadLink], roadLink: RoadLink, trafficDirection: TrafficDirection, changeSet: RoundaboutChangeSet) : (Seq[RoadLink], RoundaboutChangeSet) = {
    if(roadLink.trafficDirection == trafficDirection)
      (roadLinks :+ roadLink, changeSet)
    else
      (roadLinks :+ roadLink.copy(trafficDirection = trafficDirection), changeSet.copy(trafficDirectionChanges = changeSet.trafficDirectionChanges :+ roadLink))
  }

  def setTrafficDirection(roadLinks : Seq[RoadLink]): (Seq[RoadLink], RoundaboutChangeSet) = {
    val center = GeometryUtils.middlePoint(roadLinks.map(_.geometry))

    roadLinks.foldLeft((Seq[RoadLink](), RoundaboutChangeSet())) {
      case ((pRoadLinks, changeSet), roadLink) =>
        val (startPoint, endPoint) = getRoundaboutLinkEndpoints(roadLink, center)

        if(startPoint.y > endPoint.y)
          setRoundaboutChangeSet(pRoadLinks, roadLink, TrafficDirection.AgainstDigitizing, changeSet)
        else if(startPoint.y < endPoint.y)
          setRoundaboutChangeSet(pRoadLinks, roadLink, TrafficDirection.TowardsDigitizing, changeSet)
        else if(startPoint.x < endPoint.x)
          setRoundaboutChangeSet(pRoadLinks, roadLink, TrafficDirection.TowardsDigitizing, changeSet)
        else
          setRoundaboutChangeSet(pRoadLinks, roadLink, TrafficDirection.AgainstDigitizing, changeSet)
    }
  }

  def isRoundaboutLink(roadLink: RoadLink) = {
    roadLink.administrativeClass == State && roadLink.linkType == Roundabout
  }

  def groupByRoundabout(roadlinks: Seq[RoadLink], withIncomplete: Boolean = true): Seq[Seq[RoadLink]] = {
    val roundabouts = roadlinks.filter(isRoundaboutLink)
    groupByRoundaboutRecursive(roundabouts, withIncomplete = withIncomplete)
  }
}
