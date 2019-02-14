package fi.liikennevirasto.digiroad2.middleware


import java.security.InvalidParameterException

import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{AdditionalPanel, TextPropertyValue, TrafficSignProperty}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{HazmatTransportProhibitionService, ManoeuvreCreationException, ManoeuvreService, ProhibitionService}

object TrafficSignManager {
  val manoeuvreRelatedSigns : Seq[TrafficSignType] =  Seq(NoLeftTurn, NoRightTurn, NoUTurn)
  def belongsToManoeuvre(intValue: Int) : Boolean = {
    manoeuvreRelatedSigns.contains(TrafficSignType.applyOTHValue(intValue))
  }

  val prohibitionRelatedSigns : Seq[TrafficSignType] = Seq(ClosedToAllVehicles,  NoPowerDrivenVehicles,  NoLorriesAndVans,  NoVehicleCombinations, NoAgriculturalVehicles,
    NoMotorCycles,  NoMotorSledges, NoBuses,  NoMopeds,  NoCyclesOrMopeds,  NoPedestrians,  NoPedestriansCyclesMopeds,  NoRidersOnHorseback)
  def belongsToProhibition(intValue: Int) : Boolean = {
    prohibitionRelatedSigns.contains(TrafficSignType.applyOTHValue(intValue))
  }

  val hazmatRelatedSigns : Seq[TrafficSignType] = Seq(NoVehiclesWithDangerGoods)
  def belongsToHazmat(intValue: Int) : Boolean = {
    hazmatRelatedSigns.contains(TrafficSignType.applyOTHValue(intValue))
  }
}

case class TrafficSignManager(manoeuvreService: ManoeuvreService, prohibitionService: ProhibitionService, hazmatTransportProhibitionService: HazmatTransportProhibitionService) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def createAssetsByActor(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true): Unit = {
    if (TrafficSignManager.belongsToManoeuvre(trafficSignInfo.signType)) {
      manoeuvreService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
  }

  def createAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true): Unit = {
    if (TrafficSignManager.belongsToProhibition(trafficSignInfo.signType)) {
      prohibitionService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
    else if (TrafficSignManager.belongsToHazmat(trafficSignInfo.signType)) {
      hazmatTransportProhibitionService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
  }

  def deleteAssets(signInfo: Seq[(Long, Seq[TrafficSignProperty])]): Unit = {
    val username = Some("automatic_trafficSign_deleted")

    val (turnRestrictionSigns, others) = signInfo.partition{
      case (id, propertyData) =>
        val trafficSignType = propertyData.find(p => p.publicId == "trafficSigns_type").get.values.map(_.asInstanceOf[TextPropertyValue]).head.propertyValue.toInt
        TrafficSignManager.belongsToManoeuvre(trafficSignType)
    }

    if(turnRestrictionSigns.map(_._1).nonEmpty)
      manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(turnRestrictionSigns.map(_._1).toSet), username)
  }

  def deleteAssetsRelatedWithLinears(trafficSigns: Seq[PersistedTrafficSign]): Unit = {
    val username = Some("automatic_trafficSign_deleted")

    trafficSigns.foreach { sign =>
      val trafficSignType = sign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.map(_.asInstanceOf[TextPropertyValue]).head.propertyValue.toInt

      if (TrafficSignManager.belongsToProhibition(trafficSignType)) {
        prohibitionService.deleteOrUpdateAssetBasedOnSign(sign, username)
      }
    }
  }

  def trafficSignsExpireAndCreateAssets(signInfo: (Long, TrafficSignInfo)): Unit = {
    val username = Some("automatic_trafficSign_deleted")
    val (expireId, trafficSignInfo) = signInfo

    if (TrafficSignManager.belongsToManoeuvre(trafficSignInfo.signType)) {
      manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withId(expireId), username)
      manoeuvreService.createBasedOnTrafficSign(trafficSignInfo)
    }
  }
}
