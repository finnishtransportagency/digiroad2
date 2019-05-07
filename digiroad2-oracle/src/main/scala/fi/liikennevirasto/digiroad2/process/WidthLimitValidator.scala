package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{FreeWidth, NoWidthExceeding, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, PropertyValue, WidthLimit}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset

class WidthLimitValidator extends SevenRestrictionsLimitationValidator{
  override def assetTypeInfo: AssetTypeInfo = WidthLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(NoWidthExceeding, FreeWidth)
  override val radiusDistance: Int = 50

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case NoWidthExceeding =>
        trafficSignService.getProperty(trafficSign, "trafficSigns_value").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset, "width")
      case FreeWidth =>
        trafficSignService.getProperty(trafficSign, "trafficSigns_info").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset, "width")
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }

}

