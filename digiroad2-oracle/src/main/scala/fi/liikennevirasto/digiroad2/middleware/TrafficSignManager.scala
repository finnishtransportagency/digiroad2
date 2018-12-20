package fi.liikennevirasto.digiroad2.middleware


import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.TrafficSignType
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreService, ProhibitionService}

class TrafficSignManager(manoeuvreService: ManoeuvreService, prohibitionService: ProhibitionService) {

  def trafficSignsCreateAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true ): Unit = {
    if (TrafficSignType.belongsToManoeuvre(trafficSignInfo.signType)) {
      manoeuvreService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
    else if (TrafficSignType.belongsToProhibition(trafficSignInfo.signType)) {
      prohibitionService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
  }

  def trafficSignsDeleteAssets(id: Long, trafficSignType: Int): Unit = {
    val username = Some("automatic_trafficSign_deleted")
    if (TrafficSignType.belongsToManoeuvre(trafficSignType)) {
      manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(Set(id)), username)
    }
    else if (TrafficSignType.belongsToProhibition(trafficSignType)) {
      prohibitionService.deleteAssetBasedOnSign(prohibitionService.withId(id), username)
    }
  }
}
