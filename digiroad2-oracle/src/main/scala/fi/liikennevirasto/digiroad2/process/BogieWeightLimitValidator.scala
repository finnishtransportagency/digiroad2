package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{MaxTonsOnBogieExceeding, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, BogieWeightLimit, PropertyValue, TextPropertyValue}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset

class BogieWeightLimitValidator extends SevenRestrictionsLimitationValidator {
  override def assetTypeInfo: AssetTypeInfo = BogieWeightLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(MaxTonsOnBogieExceeding)
  override val radiusDistance: Int = 500

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case MaxTonsOnBogieExceeding =>
        trafficSignService.getProperty(trafficSign, "trafficSigns_value").getOrElse(TextPropertyValue("")).propertyValue == getAssetValue(asset)
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }
}
