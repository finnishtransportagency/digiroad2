package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{MaxLadenExceeding, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, PropertyValue, TotalWeightLimit}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset

class TotalWeightLimitValidator extends SevenRestrictionsLimitationValidator {
  override def assetTypeInfo: AssetTypeInfo = TotalWeightLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(MaxLadenExceeding)
  override val radiusDistance: Int = 500

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case MaxLadenExceeding =>
        trafficSignService.getProperty(trafficSign, "trafficSigns_value").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset, "weight")
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }
}

