package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, PropertyValue, TotalWeightLimit}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType

class TotalWeightLimitValidator extends MassLimitationValidator {
  override def assetTypeInfo: AssetTypeInfo = TotalWeightLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.FreeHeight, TrafficSignType.MaxHeightExceeding)

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case TrafficSignType.MaxLadenExceeding =>
        getTrafficSignsProperties(trafficSign, "trafficSigns_value").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset)
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }
}

