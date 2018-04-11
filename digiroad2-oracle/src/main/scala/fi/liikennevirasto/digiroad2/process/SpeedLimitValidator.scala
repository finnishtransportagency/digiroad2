package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.dao.InaccurateAssetDAO
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, RoadLink, SpeedLimit}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignTypeGroup.SpeedLimits
import fi.liikennevirasto.digiroad2.util.PolygonTools

class SpeedLimitValidator(trafficSignService: TrafficSignService) {
  val inaccurateAssetDAO: InaccurateAssetDAO = new InaccurateAssetDAO
  val polygonTools: PolygonTools = new PolygonTools
  val radiusDistance: Int = 50

  private def length(point: Point)(trafficSign: PersistedTrafficSign) = GeometryUtils.geometryLength(Seq(Point(trafficSign.lon, trafficSign.lat),point))
  private def min(s1: (Double, Seq[PersistedTrafficSign]), s2: (Double, Seq[PersistedTrafficSign])): (Double, Seq[PersistedTrafficSign]) = if (s1._1 < s2._1) s1 else s2

  private def getGeometryPosition(first: Point, last: Point, sideCode: SideCode): Seq[(Point, SideCode)] = {
    sideCode match {
      case SideCode.TowardsDigitizing => Seq((first, SideCode.TowardsDigitizing))
      case SideCode.AgainstDigitizing => Seq((last, SideCode.AgainstDigitizing))
      case _ => Seq((first, SideCode.TowardsDigitizing), (last, SideCode.AgainstDigitizing))
    }
  }

  private def getTrafficSingsByRadius(speedLimit: SpeedLimit, roadLink: RoadLink): Seq[PersistedTrafficSign] = {
    val speedLimitGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimitGeometry)

    val speedLimitSideCode =
      speedLimit.sideCode match {
        case SideCode.BothDirections =>
          TrafficDirection.toSideCode(roadLink.trafficDirection)
        case _ =>
          speedLimit.sideCode
      }

    val trafficSingsByRadius = getGeometryPosition(first, last, speedLimitSideCode).flatMap { case (position, sideCode) =>
      trafficSignService.getTrafficSignByRadius(position, radiusDistance, Some(SpeedLimits))
        .filter(sign => SideCode.apply(sign.validityDirection) == sideCode)
    }.filter(_.linkId == speedLimit.linkId)

    if (trafficSingsByRadius.nonEmpty) {
      (trafficSingsByRadius.groupBy(length(first)) ++ trafficSingsByRadius.groupBy(length(last))).reduceLeft(min)._2
    }else
      Seq()

  }

  private def speedLimitValueValidator(speedLimit: SpeedLimit, trafficSign: PersistedTrafficSign): Option[SpeedLimit] = {
    val startUrbanAreaSpeedLimit: Int = 50
    val endUrbanAreaSpeedLimit: Int = 80

    val trafficSignType = TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt)
    val trafficSignValueOption = getTrafficSignsProperties(trafficSign, "trafficSigns_value")

    speedLimit.value match {
      case Some(NumericValue(speedLimitValue)) =>
        trafficSignType match {
          case TrafficSignType.SpeedLimit | TrafficSignType.SpeedLimitZone =>
            trafficSignValueOption match {
              case Some(trafficSignValue) if trafficSignValue.propertyValue != speedLimitValue.toString =>
                Some(speedLimit)
              case _ => None
            }
          case TrafficSignType.EndSpeedLimitZone | TrafficSignType.EndSpeedLimit =>
            trafficSignValueOption match {
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

  def checkInaccurateSpeedLimitValues(speedLimit: SpeedLimit, roadLink: RoadLink) = {
    val trafficSigns = trafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)
    checkInaccurateSpeedLimitValuesWithTrafficSigns(speedLimit, roadLink, trafficSigns)
  }


  def checkInaccurateSpeedLimitValuesWithTrafficSigns(speedLimit: SpeedLimit, roadLink: RoadLink, persistedTrafficSigns: Seq[PersistedTrafficSign]): Option[SpeedLimit]= {
    val minimumAllowedLength = 2

    val trafficSignsOnLinkId = persistedTrafficSigns
      .filter(trafficSign => trafficSign.mValue >= (speedLimit.startMeasure + minimumAllowedLength) && trafficSign.mValue <= (speedLimit.endMeasure - minimumAllowedLength))
      .filter(filterBySide(_, speedLimit, roadLink))

    val trafficSigns = if (trafficSignsOnLinkId.nonEmpty) {
      trafficSignsOnLinkId
    } else {
      persistedTrafficSigns match {
        case Seq() => Seq()
        case _ =>
          getTrafficSingsByRadius(speedLimit, roadLink)
      }
    }
    trafficSigns.flatMap { trafficSign =>
      speedLimitValueValidator(speedLimit, trafficSign)
    }.headOption
  }


  private def getTrafficSignsProperties(trafficSign: PersistedTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.headOption
  }
}
