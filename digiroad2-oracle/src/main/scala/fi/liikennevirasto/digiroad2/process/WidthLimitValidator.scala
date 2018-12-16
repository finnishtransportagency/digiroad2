package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{FreeWidth, NoWidthExceeding, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, PropertyValue, TextPropertyValue, WidthLimit}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset

class WidthLimitValidator extends SevenRestrictionsLimitationValidator{
  override def assetTypeInfo: AssetTypeInfo = WidthLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(NoWidthExceeding, FreeWidth)

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    val assetValue = getAssetValue(asset)
    TrafficSignType.applyvalue(trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.asInstanceOf[TextPropertyValue].propertyValue.toInt) match {
      case NoWidthExceeding =>
        trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_value").getOrElse(PropertyValue("")).asInstanceOf[TextPropertyValue].propertyValue == getAssetValue(asset)
      case FreeWidth =>
        trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_info").getOrElse(PropertyValue("")).asInstanceOf[TextPropertyValue].propertyValue == getAssetValue(asset)
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }

}

