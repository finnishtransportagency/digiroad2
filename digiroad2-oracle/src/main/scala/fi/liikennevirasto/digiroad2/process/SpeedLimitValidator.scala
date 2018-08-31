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
  val startUrbanAreaSpeedLimit: Int = 50
  val endUrbanAreaSpeedLimit: Int = 80
  val minimumAllowedLength = 2
  val minimumAllowedEndSpeedLimitArea = 30

  private def speedLimitNormalAndZoneValidator(speedLimits: Seq[SpeedLimit], trafficSign: PersistedTrafficSign, roadLink: RoadLink): Seq[SpeedLimit] = {
    speedLimits.flatMap {
      speedLimit =>
        println(s"Proncessing speedlimit ${speedLimit.id} at linkId ${speedLimit.linkId}")

        val hasSameDirection =
          speedLimit.sideCode match {
            case SideCode.TowardsDigitizing | SideCode.AgainstDigitizing =>
              trafficSign.validityDirection == speedLimit.sideCode.value
            case _ =>
              if (roadLink.trafficDirection == TrafficDirection.BothDirections)
                true
              else
                TrafficDirection.toSideCode(roadLink.trafficDirection) == SideCode.apply(trafficSign.validityDirection)
          }

        val hasSameSpeedLimitValue =
          speedLimit.value match {
            case Some(NumericValue(speedLimitValue)) =>
              getTrafficSignsProperties(trafficSign, "trafficSigns_value") match {
                case Some(trafficSignValue) if trafficSignValue.propertyValue == speedLimitValue.toString => true
                case _ => false
              }
            case _ => false
          }

        (hasSameDirection, hasSameSpeedLimitValue) match {
          case (true, true) => Seq(speedLimit)
          case (_, _) => Seq()
        }
    }
  }

  private def speedLimitUrbanAreaValidator(speedLimits: Seq[SpeedLimit], trafficSign: PersistedTrafficSign, roadLink: RoadLink): Seq[SpeedLimit] = {
    speedLimits.flatMap {
      speedLimit =>
        println(s"Proncessing speedlimit ${speedLimit.id} at linkId ${speedLimit.linkId}")

        val hasSameDirection =
          speedLimit.sideCode match {
            case SideCode.TowardsDigitizing | SideCode.AgainstDigitizing =>
              trafficSign.validityDirection == speedLimit.sideCode.value
            case _ =>
              if (roadLink.trafficDirection == TrafficDirection.BothDirections)
                true
              else
                TrafficDirection.toSideCode(roadLink.trafficDirection) == SideCode.apply(trafficSign.validityDirection)
          }

        val hasSameSpeedLimitValue =
          speedLimit.value match {
            case Some(NumericValue(speedLimitValue)) =>
              if (speedLimitValue == startUrbanAreaSpeedLimit) true else false
            case _ => false
          }

        (hasSameDirection, hasSameSpeedLimitValue) match {
          case (true, true) => Seq(speedLimit)
          case (_, _) => Seq()
        }
    }
  }

  private def endSpeedLimitValidator(speedLimits: Seq[SpeedLimit], trafficSign: PersistedTrafficSign, roadLink: RoadLink): Seq[SpeedLimit] = {
    speedLimits.flatMap {
      speedLimit =>
        println(s"Proncessing speedlimit ${speedLimit.id} at linkId ${speedLimit.linkId}")

        val hasSameDirection =
          speedLimit.sideCode match {
            case SideCode.TowardsDigitizing | SideCode.AgainstDigitizing =>
              trafficSign.validityDirection == speedLimit.sideCode.value
            case _ =>
              if (roadLink.trafficDirection == TrafficDirection.BothDirections)
                true
              else
                TrafficDirection.toSideCode(roadLink.trafficDirection) == SideCode.apply(trafficSign.validityDirection)
          }

        val hasSameSpeedLimitValue =
          speedLimit.value match {
            case Some(NumericValue(speedLimitValue)) =>
              getTrafficSignsProperties(trafficSign, "trafficSigns_value") match {
                case Some(trafficSignValue)
                  if trafficSignValue.propertyValue == speedLimitValue.toString
                    || speedLimitValue != startUrbanAreaSpeedLimit
                    || speedLimitValue != endUrbanAreaSpeedLimit => true
                case _ => false
              }
            case _ => false
          }

        (hasSameDirection, hasSameSpeedLimitValue) match {
          case (true, true) => Seq(speedLimit)
          case (_, _) => Seq()
        }
    }
  }

  private def endUrbanAreaValidator(speedLimits: Seq[SpeedLimit], trafficSign: PersistedTrafficSign, roadLink: RoadLink): Seq[SpeedLimit] = {
    speedLimits.flatMap {
      speedLimit =>
        println(s"Proncessing speedlimit ${speedLimit.id} at linkId ${speedLimit.linkId}")

        val hasSameDirection =
          speedLimit.sideCode match {
            case SideCode.TowardsDigitizing | SideCode.AgainstDigitizing =>
              trafficSign.validityDirection == speedLimit.sideCode.value
            case _ =>
              if (roadLink.trafficDirection == TrafficDirection.BothDirections)
                true
              else
                TrafficDirection.toSideCode(roadLink.trafficDirection) == SideCode.apply(trafficSign.validityDirection)
          }

        val hasSameSpeedLimitValue =
          speedLimit.value match {
            case Some(NumericValue(speedLimitValue)) =>
              if (speedLimitValue != endUrbanAreaSpeedLimit) true else false
            case _ => false
          }

        (hasSameDirection, hasSameSpeedLimitValue) match {
          case (true, true) => Seq(speedLimit)
          case (_, _) => Seq()
        }
    }
  }

  def checkInaccurateSpeedLimitValues(speedLimit: SpeedLimit, roadLink: RoadLink) = {
    val trafficSigns = trafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)
    checkInaccurateSpeedLimitValuesWithTrafficSigns(speedLimit, roadLink, trafficSigns)
  }


  def checkSpeedLimitUsingTrafficSign(trafficSign: PersistedTrafficSign, roadLink: RoadLink, speedLimits: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    val trafficSignType = TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt)

    val speedLimitInRadiusDistance =
      speedLimits.filter(
        speedLimit =>
          ((trafficSign.mValue - radiusDistance) >= (speedLimit.startMeasure + minimumAllowedLength) && (trafficSign.mValue - radiusDistance) <= (speedLimit.endMeasure - minimumAllowedLength))
            || ((trafficSign.mValue + radiusDistance) >= (speedLimit.startMeasure + minimumAllowedLength) && (trafficSign.mValue + radiusDistance) <= (speedLimit.endMeasure - minimumAllowedLength))
      )

    trafficSignType match {
      case TrafficSignType.SpeedLimit | TrafficSignType.SpeedLimitZone =>
        val validSpeedLimits = speedLimitNormalAndZoneValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
        speedLimitInRadiusDistance.diff(validSpeedLimits)
      case TrafficSignType.UrbanArea =>
        val validSpeedLimits = speedLimitUrbanAreaValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
        speedLimitInRadiusDistance.diff(validSpeedLimits)
      case TrafficSignType.EndSpeedLimit =>
        endSpeedLimitValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
      case TrafficSignType.EndUrbanArea =>
        endUrbanAreaValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
      case TrafficSignType.EndSpeedLimitZone if trafficSign.mValue > minimumAllowedEndSpeedLimitArea && (trafficSign.mValue - roadLink.length) < minimumAllowedEndSpeedLimitArea =>
        //TODO FAZER VALIDACAO DOS 30 METROS
        endSpeedLimitValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
      case _ => Seq()
    }
  }


  private def getTrafficSignsProperties(trafficSign: PersistedTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.headOption
  }
}
