package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{DynamicValue, NumericValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, TrafficSignType}

trait SevenRestrictionsLimitationValidator extends AssetServiceValidatorOperations {

  override type AssetType = PersistedLinearAsset
  lazy val dynamicAssetDao = new DynamicLinearAssetDao
  override def assetTypeInfo: AssetTypeInfo
  override val radiusDistance: Int

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {true}

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double, trafficSign: Option[PersistedTrafficSign] = None): Seq[AssetType] = {
    def assetDistance(assets: Seq[AssetType]): (AssetType, Double) = {
      val (first, _) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      if (GeometryUtils.areAdjacent(pointOfInterest, first)) {
        val nearestAsset = assets.minBy(_.startMeasure)
        (nearestAsset, nearestAsset.startMeasure)
      } else {
        val nearestAsset = assets.maxBy(_.endMeasure)
        (nearestAsset, GeometryUtils.geometryLength(roadLink.geometry) - nearestAsset.endMeasure)
      }
    }

    val assetOnLink = assets.filter(_.linkId == roadLink.linkId)
    if (assetOnLink.nonEmpty && assetDistance(assetOnLink)._2 + distance <= radiusDistance) {
      Seq(assetDistance(assets)._1)
    } else
      Seq()
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

  override def verifyAsset(assets: Seq[PersistedLinearAsset], roadLink: RoadLink, trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    assets.flatMap { asset =>

      if (!comparingAssetAndTrafficValue(asset, trafficSign))
        Seq(Inaccurate(Some(asset.id), None, roadLink.municipalityCode, roadLink.administrativeClass))
      else
        Seq()
    }.toSet
  }

  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {
    if (assetInfo.ids.toSeq.nonEmpty) {
      withDynTransaction {

        val assets = dynamicAssetDao.fetchDynamicLinearAssetsByIds(assetInfo.ids).filterNot(_.expired).filterNot(_.expired)
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
}

