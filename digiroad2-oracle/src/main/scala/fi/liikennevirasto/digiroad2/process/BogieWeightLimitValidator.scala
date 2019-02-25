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
  lazy val dynamicAssetDao = new DynamicLinearAssetDao

  override def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {
    TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case MaxTonsOnBogieExceeding =>
        trafficSignService.getProperty(trafficSign, trafficSignService.valuePublicId).getOrElse(TextPropertyValue("")).propertyValue == getAssetValue(asset, publicId = "bogie_weight_2_axel") &&
          trafficSignService.getProperty(trafficSign, trafficSignService.additionalPublicId).forall { property =>
          val additionalPanelProperties = property.asInstanceOf[AdditionalPanel]
          !is3AxleBogieProperty(additionalPanelProperties) ||
          is3AxleBogieProperty(additionalPanelProperties) && getAdditionalPanelValue(additionalPanelProperties.panelInfo) == getAssetValue(asset, publicId = "bogie_weight_3_axel" )
        }
      case _ => throw new NumberFormatException(s"Not supported trafficSign on ${assetTypeInfo.label} asset")
    }
  }

  def getAssetValue(asset: PersistedLinearAsset, publicId: String): String = {
    asset.value match {
      case Some(DynamicValue(value)) => value.properties.find(_.publicId == publicId).map(_.values) match {
        case Some(values) => values.map(_.value).head.toString
        case _ => ""
      }
      case _ => ""
    }
  }

  override def getAsset(roadLink: Seq[RoadLink]): Seq[AssetType] = {
    dynamicAssetDao.fetchDynamicLinearAssetsByLinkIds(assetTypeInfo.typeId, roadLink.map(_.linkId))
  }

  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {
    if (assetInfo.ids.toSeq.nonEmpty) {
      withDynTransaction {

        val assets = dynamicAssetDao.fetchDynamicLinearAssetsByIds(assetInfo.ids).filterNot(_.expired)
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(assets.map(_.linkId).toSet, newTransaction = false).filterNot(_.administrativeClass == Private)

        assets.foreach { asset =>
          roadLinks.find(_.linkId == asset.linkId) match {
            case Some(roadLink) =>
              val assetGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
              val (first, last) = GeometryUtils.geometryEndpoints(assetGeometry)

              val trafficSigns: Set[PersistedTrafficSign] = getPointOfInterest(first, last, SideCode.apply(asset.sideCode)).flatMap { position =>
                splitBothDirectionTrafficSignInTwo(trafficSignService.getTrafficSignByRadius(position, radiusDistance) ++ trafficSignService.getTrafficSign(Seq(asset.linkId)))
                  .filter(sign => allowedTrafficSign.contains(TrafficSignType.applyOTHValue(trafficSignService.getProperty(sign, "trafficSigns_type").get.propertyValue.toInt)))
                  .filterNot(_.floating)
              }.toSet
              val allLinkIds = assetInfo.newLinkIds ++ trafficSigns.map(_.linkId)

              inaccurateAssetDAO.deleteInaccurateAssetByIds(assetInfo.ids)
              if(allLinkIds.nonEmpty)
                inaccurateAssetDAO.deleteInaccurateAssetByLinkIds(allLinkIds, assetTypeInfo.typeId)

              trafficSigns.foreach(validateAndInsert)

            case _ =>
          }
        }
      }
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
