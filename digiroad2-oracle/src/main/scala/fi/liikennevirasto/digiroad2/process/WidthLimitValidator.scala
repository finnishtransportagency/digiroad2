package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, PropertyValue, WidthLimit}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType

class WidthLimitValidator extends SevenRestrictionsLimitationValidator{
  override def assetTypeInfo: AssetTypeInfo = WidthLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.NoWidthExceeding, TrafficSignType.FreeWidth)
  override val radiusDistance: Int = 50

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    val assetValue = getAssetValue(asset)
    TrafficSignType.apply(trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case TrafficSignType.NoWidthExceeding =>
        trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_value").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset)
      case TrafficSignType.FreeWidth =>
        trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_info").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset)
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }

}

