package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, HeightLimit, PropertyValue}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType

class HeightLimitValidator extends SevenRestrictionsLimitationValidator {
  override def assetTypeInfo: AssetTypeInfo = HeightLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.FreeHeight, TrafficSignType.MaxHeightExceeding)
  override val radiusDistance: Int = 50

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.apply(trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case TrafficSignType.FreeHeight =>
        trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_info").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset)
      case TrafficSignType.MaxHeightExceeding =>
        trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_value").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset)
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }
}

