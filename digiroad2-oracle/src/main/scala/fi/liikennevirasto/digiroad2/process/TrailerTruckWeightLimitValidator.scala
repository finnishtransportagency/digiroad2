package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{MaxMassCombineVehiclesExceeding, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, TextPropertyValue, TrailerTruckWeightLimit}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset

class TrailerTruckWeightLimitValidator extends SevenRestrictionsLimitationValidator{
  override def assetTypeInfo: AssetTypeInfo = TrailerTruckWeightLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(MaxMassCombineVehiclesExceeding)
  override val radiusDistance: Int = 500

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case MaxMassCombineVehiclesExceeding =>
        trafficSignService.getProperty(trafficSign, "trafficSigns_value").getOrElse(TextPropertyValue("")).propertyValue == getAssetValue(asset)
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }
}
