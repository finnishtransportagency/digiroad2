package fi.liikennevirasto.digiroad2.process


import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection
import fi.liikennevirasto.digiroad2.dao.InaccurateAssetDAO
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimit
import fi.liikennevirasto.digiroad2.service.linearasset.SpeedLimitService
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignTypeGroup.SpeedLimits

class SpeedLimitValidator(speedLimitService: SpeedLimitService, trafficSignService: TrafficSignService) {
  val inaccurateAssetDao: InaccurateAssetDAO = new InaccurateAssetDAO

  private val startUrbanAreaSpeedLimit: Int = 50
  private val endUrbanAreaSpeedLimit: Int = 80

  def checkInaccurateSpeedLimitValues(speedLimit: SpeedLimit): Option[Long]= {
    def minDistance(trafficSign: PersistedTrafficSign, point: Point) = Math.sqrt(Math.pow(trafficSign.lon - point.x, 2) + Math.pow(trafficSign.lat - point.y, 2))
    def min(s1: (Double, Seq[PersistedTrafficSign]), s2: (Double, Seq[PersistedTrafficSign])): (Double, Seq[PersistedTrafficSign]) = if (s1._1 < s2._1) s1 else s2

    val trafficSignsOnLinkId = trafficSignService.getPersistedAssetsByLinkId(speedLimit.linkId)
      .filter(trafficSign => trafficSign.mValue >= speedLimit.startMeasure && trafficSign.mValue < speedLimit.endMeasure)
      .filter(_.validityDirection == speedLimit.trafficDirection.value || speedLimit.trafficDirection == TrafficDirection.BothDirections)

    val trafficSigns = if (trafficSignsOnLinkId.nonEmpty) {
      trafficSignsOnLinkId
    } else {
      val (first, last) = GeometryUtils.geometryEndpoints(speedLimit.geometry)

      val trafficSingsByRadius =
        (speedLimit.trafficDirection match {
          case TrafficDirection.TowardsDigitizing =>
            trafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits))
              .filter(_.validityDirection == speedLimit.trafficDirection.value)
              .filter(trafficSign => TrafficSignType.apply(trafficSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) != TrafficSignType.EndUrbanArea)

          case TrafficDirection.AgainstDigitizing =>
            trafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))
              .filter(_.validityDirection == speedLimit.trafficDirection.value)
              .filter(trafficSign => TrafficSignType.apply(trafficSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) != TrafficSignType.EndUrbanArea)
          case _ =>
            trafficSignService.getTrafficSignByRadius(first, 50, Some(SpeedLimits)) ++ trafficSignService.getTrafficSignByRadius(last, 50, Some(SpeedLimits))

        }).filter(trafficSign => trafficSign.linkId == speedLimit.linkId)

      if (trafficSingsByRadius.nonEmpty)
        (trafficSingsByRadius.groupBy(minDistance(_, first)) ++ trafficSingsByRadius.groupBy(minDistance(_, last))).reduceLeft(min)._2
      else
        Seq()
    }

    trafficSigns.flatMap { trafficSign =>
      speedLimit.value match {
        case Some(speedLimitValue) =>
          val trafficSignValue = trafficSign.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.propertyValue
          TrafficSignType.apply(trafficSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) match {
            case TrafficSignType.SpeedLimit if trafficSignValue != speedLimitValue.value.toString =>
              Some(speedLimit.id)
            case TrafficSignType.SpeedLimitZone if trafficSignValue != speedLimitValue.value.toString =>
              Some(speedLimit.id)
            case TrafficSignType.UrbanArea if speedLimitValue.value != startUrbanAreaSpeedLimit =>
              Some(speedLimit.id)
            case TrafficSignType.EndUrbanArea if speedLimitValue.value != endUrbanAreaSpeedLimit =>
              Some(speedLimit.id)
            case _ => None
          }
        case _ => None
      }
    }.headOption
  }
}
