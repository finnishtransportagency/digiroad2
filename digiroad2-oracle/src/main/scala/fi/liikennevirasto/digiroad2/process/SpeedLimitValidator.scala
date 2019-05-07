package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{SideCode, PropertyValue, TrafficDirection}
import fi.liikennevirasto.digiroad2.dao.InaccurateAssetDAO
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, RoadLink, SpeedLimit, SpeedLimitValue}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
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
    val validatorValues =
      speedLimits.flatMap {
        speedLimit =>
          println(s"Proncessing traffic sign ${trafficSign.id} at speedlimit ${speedLimit.id} at linkId ${speedLimit.linkId}")

          val hasSameSpeedLimitValue =
            speedLimit.value match {
              case Some(SpeedLimitValue(false, speedLimitValue)) =>
                trafficSignService.getProperty(trafficSign, "trafficSigns_value") match {
                  case Some(trafficSignValue) if trafficSignValue.asInstanceOf[PropertyValue].propertyValue == speedLimitValue.toString => true
                  case _ => false
                }
              case _ => false
            }

          validateSpeedLimitDirectionAndValue(hasSameDirection(speedLimit, trafficSign, roadLink), hasSameSpeedLimitValue, speedLimit)
      }

    if (validatorValues.exists(_._2 == true)) Seq() else validatorValues.map(_._1)
  }

  private def speedLimitUrbanAreaValidator(speedLimits: Seq[SpeedLimit], trafficSign: PersistedTrafficSign, roadLink: RoadLink): Seq[SpeedLimit] = {
    val validatorValues =
      speedLimits.flatMap {
        speedLimit =>
          println(s"Proncessing traffic sign ${trafficSign.id} at speedlimit ${speedLimit.id} at linkId ${speedLimit.linkId}")

          val hasSameSpeedLimitValue =
            speedLimit.value match {
              case Some(SpeedLimitValue(false, speedLimitValue)) =>
                if (speedLimitValue == startUrbanAreaSpeedLimit) true else false
              case _ => false
            }

          validateSpeedLimitDirectionAndValue(hasSameDirection(speedLimit, trafficSign, roadLink), hasSameSpeedLimitValue, speedLimit)
      }

    if (validatorValues.exists(_._2 == true)) Seq() else validatorValues.map(_._1)
  }

  private def endSpeedLimitValidator(speedLimits: Seq[SpeedLimit], trafficSign: PersistedTrafficSign, roadLink: RoadLink): Seq[SpeedLimit] = {
    val validatorValues =
      speedLimits.flatMap {
        speedLimit =>
          println(s"Proncessing traffic sign ${trafficSign.id} at speedlimit ${speedLimit.id} at linkId ${speedLimit.linkId}")

          val hasSameSpeedLimitValue =
            speedLimit.value match {
              case Some(SpeedLimitValue(false, speedLimitValue)) =>
                trafficSignService.getProperty(trafficSign, "trafficSigns_value") match {
                  case Some(trafficSignValue)
                    if trafficSignValue.asInstanceOf[PropertyValue].propertyValue.toInt == speedLimitValue
                      || (speedLimitValue != startUrbanAreaSpeedLimit && speedLimitValue != endUrbanAreaSpeedLimit) => true
                  case _ => false
                }
              case _ => false
            }

          validateSpeedLimitDirectionAndValue(hasSameDirection(speedLimit, trafficSign, roadLink), hasSameSpeedLimitValue, speedLimit)
      }

    if (validatorValues.exists(_._2 == true)) validatorValues.map(_._1) else Seq()
  }

  private def endUrbanAreaValidator(speedLimits: Seq[SpeedLimit], trafficSign: PersistedTrafficSign, roadLink: RoadLink): Seq[SpeedLimit] = {
    val validatorValues =
      speedLimits.flatMap {
        speedLimit =>
          println(s"Proncessing traffic sign ${trafficSign.id} at speedlimit ${speedLimit.id} at linkId ${speedLimit.linkId}")

          val hasSameSpeedLimitValue =
            speedLimit.value match {
              case Some(SpeedLimitValue(false, speedLimitValue)) =>
                if (speedLimitValue != endUrbanAreaSpeedLimit) true else false
              case _ => false
            }

          validateSpeedLimitDirectionAndValue(hasSameDirection(speedLimit, trafficSign, roadLink), hasSameSpeedLimitValue, speedLimit)
      }

    if (validatorValues.exists(_._2 == true)) validatorValues.map(_._1) else Seq()
  }

  def checkSpeedLimitUsingTrafficSign(trafficSigns: Seq[PersistedTrafficSign], roadLink: RoadLink, speedLimits: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    trafficSigns.flatMap { trafficSign =>
      val trafficSignType = TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt)

      val speedLimitInRadiusDistance =
        speedLimits.filter(
          speedLimit =>
            ((trafficSign.mValue - radiusDistance) >= (speedLimit.startMeasure + minimumAllowedLength) || (trafficSign.mValue - radiusDistance) <= (speedLimit.endMeasure - minimumAllowedLength))
              || ((trafficSign.mValue + radiusDistance) >= (speedLimit.startMeasure + minimumAllowedLength) || (trafficSign.mValue + radiusDistance) <= (speedLimit.endMeasure - minimumAllowedLength))
        )

      trafficSignType match {
        case SpeedLimitSign | SpeedLimitZone =>
          speedLimitNormalAndZoneValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
        case UrbanArea =>
          speedLimitUrbanAreaValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
        case EndSpeedLimit =>
          endSpeedLimitValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
        case EndUrbanArea =>
          endUrbanAreaValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
        case EndSpeedLimitZone if trafficSign.mValue > minimumAllowedEndSpeedLimitArea && (roadLink.length - trafficSign.mValue) > minimumAllowedEndSpeedLimitArea =>
          endSpeedLimitValidator(speedLimitInRadiusDistance, trafficSign, roadLink)
        case _ => Seq()
      }
    }.distinct
  }

  private def hasSameDirection(speedLimit: SpeedLimit, trafficSign: PersistedTrafficSign, roadLink: RoadLink): Boolean = {
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

  private def validateSpeedLimitDirectionAndValue(sameDirection: Boolean, sameValue: Boolean, speedLimit: SpeedLimit): Seq[(SpeedLimit, Boolean)] = {
    (sameDirection, sameValue) match {
      case (true, true) => Seq((speedLimit, true))
      case (_, _) => Seq((speedLimit, false))
    }
  }

}
