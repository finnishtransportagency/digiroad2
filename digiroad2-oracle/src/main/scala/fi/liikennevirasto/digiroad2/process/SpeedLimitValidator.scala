package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.dao.InaccurateAssetDAO
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, RoadLink, SpeedLimit}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignTypeGroup.SpeedLimits
import fi.liikennevirasto.digiroad2.util.PolygonTools

class SpeedLimitValidator(trafficSignService: TrafficSignService) {
  val inaccurateAssetDAO: InaccurateAssetDAO = new InaccurateAssetDAO
  val polygonTools: PolygonTools = new PolygonTools

  private def minDistance(trafficSign: PersistedTrafficSign, point: Point) = Math.sqrt(Math.pow(trafficSign.lon - point.x, 2) + Math.pow(trafficSign.lat - point.y, 2))
  private def min(s1: (Double, Seq[PersistedTrafficSign]), s2: (Double, Seq[PersistedTrafficSign])): (Double, Seq[PersistedTrafficSign]) = if (s1._1 < s2._1) s1 else s2

  private def getTrafficSingsByRadius(speedLimit: SpeedLimit, roadLink: RoadLink): Seq[PersistedTrafficSign] = {
    val speedLimitGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimitGeometry)

    val trafficSingsByRadius =
          (speedLimit.sideCode match {
            case SideCode.TowardsDigitizing =>
              trafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits)).filter (_.validityDirection == speedLimit.sideCode.value)

            case SideCode.AgainstDigitizing =>
              trafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits)).filter (_.validityDirection == speedLimit.sideCode.value)

            case _ =>
              roadLink.trafficDirection match {
                case TrafficDirection.TowardsDigitizing =>
                  trafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits))
                    .filter (trafficSign => SideCode.apply(trafficSign.validityDirection) == TrafficDirection.toSideCode(TrafficDirection.TowardsDigitizing))

                case TrafficDirection.AgainstDigitizing =>
                  trafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))
                    .filter (trafficSign => SideCode.apply(trafficSign.validityDirection) == TrafficDirection.toSideCode(TrafficDirection.AgainstDigitizing))

                case _ =>
              trafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits)).filter(_.validityDirection == SideCode.TowardsDigitizing.value) ++
                trafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits)).filter(_.validityDirection == SideCode.AgainstDigitizing.value)

          }}).filter(_.linkId == speedLimit.linkId)

    if (trafficSingsByRadius.nonEmpty) {
      (trafficSingsByRadius.groupBy(minDistance(_, first)) ++ trafficSingsByRadius.groupBy(minDistance(_, last))).reduceLeft(min)._2
    }else
      Seq()
  }

  private def speedLimitValueValidator(speedLimit: SpeedLimit, trafficSign: PersistedTrafficSign): Option[SpeedLimit] = {
    val startUrbanAreaSpeedLimit: Int = 50
    val endUrbanAreaSpeedLimit: Int = 80

    speedLimit.value match {
      case Some(NumericValue(speedLimitValue)) =>
        TrafficSignType.apply(trafficSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) match {
          case TrafficSignType.SpeedLimit | TrafficSignType.SpeedLimitZone =>
            trafficSign.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.headOption match {
              case Some(trafficSignValue) if trafficSignValue.propertyValue != speedLimitValue.toString =>
                Some(speedLimit)
              case _ => None
            }

          case TrafficSignType.EndSpeedLimitZone | TrafficSignType.EndSpeedLimit =>
            trafficSign.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.headOption match {
              case Some(trafficSignValue) if trafficSignValue.propertyValue == speedLimitValue.toString =>
                Some(speedLimit)
              case _ => None
            }

          case TrafficSignType.UrbanArea if speedLimitValue != startUrbanAreaSpeedLimit =>
            Some(speedLimit)

          case TrafficSignType.EndUrbanArea if speedLimitValue != endUrbanAreaSpeedLimit =>
            Some(speedLimit)

          case _ => None
        }
      case _ => None
    }
  }

  private def filterBySide(trafficSign: PersistedTrafficSign, speedLimit: SpeedLimit, roadLink: RoadLink) : Boolean = {
    speedLimit.sideCode match {
      case SideCode.TowardsDigitizing | SideCode.AgainstDigitizing =>
        trafficSign.validityDirection == speedLimit.sideCode.value
      case _ =>
        if (roadLink.trafficDirection == TrafficDirection.BothDirections)
          true
        else
          TrafficDirection.toSideCode(roadLink.trafficDirection) == SideCode.apply(trafficSign.validityDirection)
    }
  }

  def checkInaccurateSpeedLimitValues(speedLimit: SpeedLimit, roadLink: RoadLink): Option[SpeedLimit]= {
    val minimumAllowedLength = 2

    val trafficSignsOnLinkId = trafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)
      .filter(trafficSign => trafficSign.mValue >= (speedLimit.startMeasure + minimumAllowedLength) && trafficSign.mValue <= (speedLimit.endMeasure - minimumAllowedLength))
      .filter(filterBySide(_, speedLimit, roadLink))

    val trafficSigns = if (trafficSignsOnLinkId.nonEmpty) {
      trafficSignsOnLinkId
    } else {
      getTrafficSingsByRadius(speedLimit, roadLink)
    }
    trafficSigns.flatMap { trafficSign =>
      speedLimitValueValidator(speedLimit, trafficSign)
    }.headOption
  }
}
