package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{SideCode, SpeedLimitAsset}
import fi.liikennevirasto.digiroad2.dao.{InaccurateAsset, InaccurateAssetDAO}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, RoadLink, SpeedLimit}
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignTypeGroup.SpeedLimits
import fi.liikennevirasto.digiroad2.util.PolygonTools


class SpeedLimitValidator(speedLimitService: SpeedLimitService, trafficSignService: TrafficSignService) {
  val inaccurateAssetDAO: InaccurateAssetDAO = new InaccurateAssetDAO
  val polygonTools: PolygonTools = new PolygonTools

  private def minDistance(trafficSign: PersistedTrafficSign, point: Point) = Math.sqrt(Math.pow(trafficSign.lon - point.x, 2) + Math.pow(trafficSign.lat - point.y, 2))
  private def min(s1: (Double, Seq[PersistedTrafficSign]), s2: (Double, Seq[PersistedTrafficSign])): (Double, Seq[PersistedTrafficSign]) = if (s1._1 < s2._1) s1 else s2

  private def getTrafficSingsByRadius(speedLimit: SpeedLimit): Seq[PersistedTrafficSign] = {
    val (first, last) = GeometryUtils.geometryEndpoints(speedLimit.geometry)

    val trafficSingsByRadius = (speedLimit.sideCode match {
      case SideCode.TowardsDigitizing =>
        trafficSignService.getTrafficSignByRadius (first, 50, Some (SpeedLimits) )
          .filter (_.validityDirection == speedLimit.trafficDirection.value)
          .filter (trafficSign => TrafficSignType.apply (trafficSign.propertyData.find (p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) != TrafficSignType.EndUrbanArea)

      case SideCode.AgainstDigitizing =>
        trafficSignService.getTrafficSignByRadius (last, 50, Some (SpeedLimits) )
          .filter (_.validityDirection == speedLimit.trafficDirection.value)
          .filter (trafficSign => TrafficSignType.apply (trafficSign.propertyData.find (p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) != TrafficSignType.EndUrbanArea)
      case _ =>
        trafficSignService.getTrafficSignByRadius (first, 50, Some (SpeedLimits) ) ++ trafficSignService.getTrafficSignByRadius (last, 50, Some (SpeedLimits) )

    }).filter(trafficSign => trafficSign.linkId == speedLimit.linkId)

    if (trafficSingsByRadius.nonEmpty) {
      println("traffic signs on radius")
      (trafficSingsByRadius.groupBy(minDistance(_, first)) ++ trafficSingsByRadius.groupBy(minDistance(_, last))).reduceLeft(min)._2
    }else
      Seq()
  }

  private def speedLimitValueValidator(speedLimit: SpeedLimit, trafficSign: PersistedTrafficSign): Option[SpeedLimit] = {
    val startUrbanAreaSpeedLimit: Int = 50
    val endUrbanAreaSpeedLimit: Int = 80

    speedLimit.value match {
      case Some(NumericValue(speedLimitValue)) =>
        println("speedLimitValue " + speedLimitValue)
        val trafficSignValue = trafficSign.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.headOption
        TrafficSignType.apply(trafficSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) match {
          case TrafficSignType.SpeedLimit if trafficSignValue.get.propertyValue != speedLimitValue.toString =>
            println("SpeedLimit type trafficValue " + trafficSignValue.get.propertyValue + "/ SpeedLimitValue " + speedLimitValue.toString)
            Some(speedLimit)
          case TrafficSignType.SpeedLimitZone if trafficSignValue.get.propertyValue != speedLimitValue.toString =>
            println("SpeedLimitZone type trafficValue " + trafficSignValue.get.propertyValue + "/ SpeedLimitValue " + speedLimitValue.toString)
            Some(speedLimit)
          case TrafficSignType.EndSpeedLimitZone if trafficSignValue.get.propertyValue == speedLimitValue.toString =>
            println("EndSpeedLimitZone type trafficValue " + trafficSignValue.get.propertyValue + "/ SpeedLimitValue " + speedLimitValue.toString)
            Some(speedLimit)
          case TrafficSignType.UrbanArea if speedLimitValue != startUrbanAreaSpeedLimit =>
            println("UrbanArea type defaultstart " + startUrbanAreaSpeedLimit + "/ SpeedLimitValue " + speedLimitValue.toString)
            Some(speedLimit)
          case TrafficSignType.EndUrbanArea if speedLimitValue != endUrbanAreaSpeedLimit =>
            println("EndUrbanArea type defaultend " + endUrbanAreaSpeedLimit + "/ SpeedLimitValue " + speedLimitValue.toString)
            Some(speedLimit)
          case TrafficSignType.EndSpeedLimit if trafficSignValue.get.propertyValue == speedLimitValue.toString =>
            println("EndSpeedLimit type trafficValue " + trafficSignValue.get.propertyValue + "/ SpeedLimitValue " + speedLimitValue.toString)
            Some(speedLimit)
          case _ => None
        }
      case _ => None
    }
  }

  def checkInaccurateSpeedLimitValues(speedLimit: SpeedLimit): Option[SpeedLimit]= {
    val trafficSignsOnLinkId = trafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(speedLimit.linkId)
      .filter(trafficSign => trafficSign.mValue >= speedLimit.startMeasure && trafficSign.mValue < speedLimit.endMeasure)
      .filter(_.validityDirection == speedLimit.sideCode.value || speedLimit.sideCode == SideCode.BothDirections)

    val trafficSigns = if (trafficSignsOnLinkId.nonEmpty) {
      println("traffic signs on linkId " + speedLimit.linkId)
      trafficSignsOnLinkId
    } else {
      val (first, last) = GeometryUtils.geometryEndpoints(speedLimit.geometry)
      getTrafficSingsByRadius(speedLimit)
    }
    trafficSigns.flatMap { trafficSign =>
      speedLimitValueValidator(speedLimit, trafficSign)
    }.headOption
  }

  def checkAndAddInaccurateSpeedLimitInfo(speedLimit: SpeedLimit, roadLink: RoadLink) = {
    checkInaccurateSpeedLimitValues(speedLimit) match {
      case Some(speedLimitAsset) =>
        val area = polygonTools.getAreaByGeometry(roadLink.geometry, Measures(speedLimitAsset.startMeasure, speedLimitAsset.endMeasure), None)
//        val inaccurateInfo = InaccurateAsset(speedLimitAsset.id, SpeedLimitAsset.typeId, roadLink.municipalityCode, area, roadLink.administrativeClass)
        inaccurateAssetDAO.createInaccurateAsset(speedLimitAsset.id, SpeedLimitAsset.typeId, roadLink.municipalityCode, area, roadLink.administrativeClass.value)
      case _ => None
    }
  }
}
