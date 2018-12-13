package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{FreeHeight, MaxHeightExceeding, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, HeightLimit, PropertyValue, TextPropertyValue}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset


class HeightLimitValidator extends SevenRestrictionsLimitationValidator {
  override def assetTypeInfo: AssetTypeInfo = HeightLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(FreeHeight, MaxHeightExceeding)

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.applyOTHValue(trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.asInstanceOf[TextPropertyValue].propertyValue.toInt) match {
      case FreeHeight =>
        trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_info").getOrElse(PropertyValue("")).asInstanceOf[TextPropertyValue].propertyValue == getAssetValue(asset)
      case MaxHeightExceeding =>
        trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_value").getOrElse(PropertyValue("")).asInstanceOf[TextPropertyValue].propertyValue == getAssetValue(asset)
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }
}

