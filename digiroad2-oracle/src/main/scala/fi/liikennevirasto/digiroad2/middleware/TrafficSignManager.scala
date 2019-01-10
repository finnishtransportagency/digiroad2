package fi.liikennevirasto.digiroad2.middleware


import fi.liikennevirasto.digiroad2.asset.{TextPropertyValue, TrafficSignProperty}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.{NoVehiclesWithDangerGoods, TrafficSignType}
import fi.liikennevirasto.digiroad2.service.linearasset.{HazmatTransportProhibitionService, ManoeuvreService, ProhibitionService}

class TrafficSignManager(manoeuvreService: ManoeuvreService, prohibitionService: ProhibitionService, hazmatTransportProhibitionService: HazmatTransportProhibitionService) {

  def trafficSignsCreateAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true ): Unit = {
    if (TrafficSignType.belongsToManoeuvre(trafficSignInfo.signType)) {
      manoeuvreService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
    else if (TrafficSignType.belongsToProhibition(trafficSignInfo.signType)) {
      prohibitionService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
    else if (TrafficSignType.applyOTHValue(trafficSignInfo.signType).OTHvalue == NoVehiclesWithDangerGoods.OTHvalue) {
      hazmatTransportProhibitionService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
  }

  def trafficSignsDeleteAssets(id: Long, propertyData: Seq[TrafficSignProperty] = Seq()): Unit = {
    val username = Some("automatic_trafficSign_deleted")
    val trafficSignType = propertyData.find(p => p.publicId == "trafficSigns_type").get.values.map(_.asInstanceOf[TextPropertyValue]).head.propertyValue.toInt

    if (TrafficSignType.belongsToManoeuvre(trafficSignType)) {
      manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(Set(id)), username)
    }
    else if (TrafficSignType.belongsToProhibition(trafficSignType)) {
      prohibitionService.deleteOrUpdateAssetBasedOnSign(prohibitionService.withId(id), username)
    }
    else if (trafficSignType == NoVehiclesWithDangerGoods.OTHvalue) {
      hazmatTransportProhibitionService.deleteOrUpdateAssetBasedOnSign(id, propertyData = propertyData, username = username)
    }
  }
}
