package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{AdditionalPanelWithText, GeometryUtils, MaxTonsOnBogieExceeding, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{DynamicValue, PersistedLinearAsset, RoadLink}

class BogieWeightLimitValidator extends SevenRestrictionsLimitationValidator {
  override def assetTypeInfo: AssetTypeInfo = BogieWeightLimit
  override val allowedTrafficSign: Set[TrafficSignType] = Set(MaxTonsOnBogieExceeding)
  override val radiusDistance: Int = 500

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case MaxTonsOnBogieExceeding =>
        trafficSignService.getProperty(trafficSign, trafficSignService.valuePublicId).getOrElse(TextPropertyValue("")).propertyValue == getAssetValue(asset, publicId = "bogie_weight_2_axel") &&
          trafficSignService.getAdditionalPanelProperty(trafficSign, trafficSignService.additionalPublicId).forall { additionalPanelProperties =>
          !is3AxleBogieProperty(additionalPanelProperties) ||
          is3AxleBogieProperty(additionalPanelProperties) && getAdditionalPanelValue(additionalPanelProperties.panelInfo) == getAssetValue(asset, publicId = "bogie_weight_3_axel" )
        }
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }

  def getAdditionalPanelValue(info: String): String =  {
    val regexGetNumber = "\\d.?\\d+(?=\\st\\s*$)".r

    regexGetNumber.findFirstMatchIn(info) match {
      case Some(matchedValue) => (matchedValue.toString.toDouble*1000).toInt.toString
      case _ => ""
    }
  }

  def is3AxleBogieProperty(additionalPanelProperty: AdditionalPanel) : Boolean = {
    val regexFor3AxleBoggieMatch = "\\w*3\\s*-\\s*axlad\\s*boggi\\s*\\w*\\d*\\s*t$|\\w*3\\s*-\\s*akseliselle\\s*telille\\s*\\w*\\s*\\d+\\s*t$".r
    TrafficSignType.applyOTHValue(additionalPanelProperty.panelType) == AdditionalPanelWithText && regexFor3AxleBoggieMatch.findFirstMatchIn(additionalPanelProperty.panelInfo).nonEmpty
  }
}
