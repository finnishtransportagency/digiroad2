package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.TrafficDirection
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}

case class RoundaboutChangeSet(trafficDirectionChanges: Seq[VVHRoadlink] = Seq.empty)

object RoundaboutProcessor {

  private def getRoundaboutLinkEndpoints(roadLink: VVHRoadlink, center: Point): (Point, Point) = {
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

  private def setRoundaboutChangeSet(roadlinks : Seq[VVHRoadlink], roadLink: VVHRoadlink, trafficDirection: TrafficDirection, changeSet: RoundaboutChangeSet) : (Seq[VVHRoadlink], RoundaboutChangeSet) = {
    if(roadLink.trafficDirection == trafficDirection)
      (roadlinks :+ roadLink, changeSet)
    else
      (roadlinks :+ roadLink.copy(trafficDirection = trafficDirection), changeSet.copy(trafficDirectionChanges = changeSet.trafficDirectionChanges :+ roadLink))
  }

  def setTrafficDirection(roadLinks : Seq[VVHRoadlink]): (Seq[VVHRoadlink], RoundaboutChangeSet) = {
    val center = GeometryUtils.middlePoint(roadLinks.map(_.geometry))

    roadLinks.foldLeft((Seq[VVHRoadlink](), RoundaboutChangeSet())) {
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
}
