package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{MaximumLength, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, LengthLimit, TextPropertyValue}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset

class LengthLimitValidator extends SevenRestrictionsLimitationValidator {
  override def assetTypeInfo: AssetTypeInfo =  LengthLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(MaximumLength)
  override val radiusDistance: Int = 50

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case MaximumLength =>
        trafficSignService.getProperty(trafficSign, "trafficSigns_value").getOrElse(TextPropertyValue("")).propertyValue == getAssetValue(asset, "length")
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }
}
