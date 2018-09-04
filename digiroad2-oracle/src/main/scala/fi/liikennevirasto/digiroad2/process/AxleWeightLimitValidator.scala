package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, AxleWeightLimit, PropertyValue}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.PersistedLinearAsset
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType

class AxleWeightLimitValidator extends MassLimitationValidator{
  override def assetTypeInfo: AssetTypeInfo = AxleWeightLimit
  override def assetName: String = "axleWeightLimit"
  override val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.MaxTonsOneAxleExceeding)

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case TrafficSignType.MaxTonsOneAxleExceeding =>
        getTrafficSignsProperties(trafficSign, "trafficSigns_value").getOrElse(PropertyValue("")).propertyValue == getAssetValue(asset)
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }
}
