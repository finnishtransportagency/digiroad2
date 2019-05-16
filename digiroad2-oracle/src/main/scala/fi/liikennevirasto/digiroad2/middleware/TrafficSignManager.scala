package fi.liikennevirasto.digiroad2.middleware


import java.security.InvalidParameterException

import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.TrafficSignType
import fi.liikennevirasto.digiroad2.asset.{AdditionalPanel, TextPropertyValue, TrafficSignProperty}
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreCreationException, ManoeuvreService, ProhibitionService}

class TrafficSignManager(manoeuvreService: ManoeuvreService) {

  def createAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true ): Unit = {
    if (TrafficSignType.belongsToManoeuvre(trafficSignInfo.signType)) {
      try{
        manoeuvreService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
      } catch {
        case ex: ManoeuvreCreationException =>
          println(s"""creation of manoeuvre on link id ${trafficSignInfo.linkId} from traffic sign ${trafficSignInfo.id} failed with the following exception ${ex.getMessage}""")
        case ex: InvalidParameterException =>
          println(s"""creation of manoeuvre on link id ${trafficSignInfo.linkId} from traffic sign ${trafficSignInfo.id} failed with the Invalid Parameter exception ${ex.getMessage}""")
      }
    }
  }

  def deleteAssets(signInfo: Seq[(Long, Seq[TrafficSignProperty])]): Unit = {
    val username = Some("automatic_trafficSign_deleted")

    val (turnRestrictionSigns, others) = signInfo.partition{
      case (id, propertyData) =>
        val trafficSignType = propertyData.find(p => p.publicId == "trafficSigns_type").get.values.map(_.asInstanceOf[TextPropertyValue]).head.propertyValue.toInt
        TrafficSignType.belongsToManoeuvre(trafficSignType)
    }

    if(turnRestrictionSigns.map(_._1).nonEmpty)
      manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(turnRestrictionSigns.map(_._1).toSet), username)
  }

  def trafficSignsExpireAndCreateAssets(signInfo: (Long, TrafficSignInfo)): Unit = {
    val username = Some("automatic_trafficSign_deleted")
    val (expireId, trafficSignInfo) = signInfo

    if (TrafficSignType.belongsToManoeuvre(trafficSignInfo.signType)) {
      try{
        manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withId(expireId), username)
        manoeuvreService.createBasedOnTrafficSign(trafficSignInfo)
      }catch{
        case ex: ManoeuvreCreationException =>
          println(s"""creation of manoeuvre on link id ${trafficSignInfo.linkId} from traffic sign ${trafficSignInfo.id} failed with the following exception ${ex.getMessage}""")
        case ex: InvalidParameterException =>
          println(s"""creation of manoeuvre on link id ${trafficSignInfo.linkId} from traffic sign ${trafficSignInfo.id} failed with the Invalid Parameter exception ${ex.getMessage}""")
      }
    }
  }
}
