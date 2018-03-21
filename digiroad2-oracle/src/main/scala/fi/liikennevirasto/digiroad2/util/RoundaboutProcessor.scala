package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Roundabout, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}

case class RoundaboutChangeSet(trafficDirectionChanges: Seq[RoadLink] = Seq.empty)

object RoundaboutProcessor {

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
          setRoundaboutChangeSet(pRoadLinks, roadLink, TrafficDirection.AgainstDigitizing, changeSet)
        else
          setRoundaboutChangeSet(pRoadLinks, roadLink, TrafficDirection.TowardsDigitizing, changeSet)
    }
  }

  def isRoundaboutLink(roadLink: RoadLink) = {
    roadLink.administrativeClass == State && roadLink.linkType == Roundabout
  }

  def groupByRoundabout(roadlinks: Seq[RoadLink]): Unit = {

  }
}
