package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}

trait MassLimitationValidator extends AssetServiceValidatorOperations {

  override type AssetType = PersistedLinearAsset
  override val radiusDistance: Int = 50
  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)
  def assetTypeInfo: AssetTypeInfo

  def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign) : Boolean

  def getAssetValue(asset: PersistedLinearAsset) : String = {
    asset.value match {
      case Some(NumericValue(intValue)) => intValue.toString
      case _ => ""
    }
  }

  override def getAsset(roadLink: RoadLink): Seq[AssetType] = {
    dao.fetchLinearAssetsByLinkIds(assetTypeInfo.typeId ,Seq(roadLink.linkId), LinearAssetTypes.numericValuePropertyId, false)
  }

  override def verifyAsset(assets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Boolean = {
    assets.forall { asset =>
      comparingAssetAndTrafficValue(asset, trafficSign)
    }
  }

  override def verifyAssetX(asset: AssetType, roadLink: RoadLink, trafficSign: Seq[PersistedTrafficSign]): Boolean = {
    false
  }

  def verifyAssetXX(asset: AssetType, pointOfInterest: Point, roadLink: RoadLink, trafficSign: Seq[PersistedTrafficSign], assetsOnRadius: Seq[PersistedLinearAsset]): Boolean = {
    trafficSign.exists(comparingAssetAndTrafficValue(asset, _)) || assetsOnRadius.exists(_.value == asset.value)
  }

  override def assetValidatorX(asset: AssetType, pointOfInterest: Point, defaultRoadLink: RoadLink): Boolean = {

    val roadLinks = getLinkIdsByRadius(pointOfInterest)
    val assetsOnRadius = dao.fetchLinearAssetsByLinkIds(asset.typeId, roadLinks.map(_.linkId), LinearAssetTypes.numericValuePropertyId, false)
    val trafficSigns = trafficSignService.getTrafficSign(roadLinks.map(_.linkId))

    validatorXX(pointOfInterest, defaultRoadLink, roadLinks, asset, assetsOnRadius ,trafficSigns)
  }

  def validatorXX(point: Point, prevRoadLink: RoadLink, roadLinks: Seq[RoadLink], asset: PersistedLinearAsset, assetsOnRadius: Seq[PersistedLinearAsset], trafficSigns: Seq[PersistedTrafficSign]) : Boolean = {
    val filteredRoadLink = roadLinks.filterNot(_.linkId == prevRoadLink.linkId)
    getAdjacentRoadLink(point, prevRoadLink, filteredRoadLink).exists { case (newRoadLink, (_, oppositePoint)) =>
      val filterTrafficSigns = trafficSigns.filter(_.linkId == newRoadLink)
      val existingAsset = assetsOnRadius.filter { asset =>
        asset.linkId == newRoadLink.linkId && GeometryUtils.areAdjacent(GeometryUtils.truncateGeometry2D(newRoadLink.geometry, asset.startMeasure, asset.endMeasure), point)
      }

      if (filterTrafficSigns.isEmpty || existingAsset.isEmpty) {
        validatorXX(oppositePoint, newRoadLink, filteredRoadLink, asset, assetsOnRadius, trafficSigns)
      } else {
        verifyAssetXX(asset, point, newRoadLink, filterTrafficSigns, existingAsset )
      }
    }
  }

  val allowedTrafficSign: Set[TrafficSignType]

  override def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign] = {
    trafficSignService.getTrafficSign(Seq(roadLink.linkId)).filter(trafficSign =>
      allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt)))
  }
}
