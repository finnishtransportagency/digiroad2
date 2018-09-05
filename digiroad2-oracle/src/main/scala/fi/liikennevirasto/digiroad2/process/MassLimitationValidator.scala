package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, HazmatTransportProhibition, SideCode}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import fi.liikennevirasto.digiroad2.util.AssetValidatorProcess.inaccurateAssetDAO
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}

trait MassLimitationValidator extends AssetServiceValidatorOperations {

  override type AssetType = PersistedLinearAsset
  override val radiusDistance: Int = 50
  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)

  def assetTypeInfo: AssetTypeInfo
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double): Seq[AssetType] = {
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

  def getAssetValue(asset: PersistedLinearAsset): String = {
    asset.value match {
      case Some(NumericValue(intValue)) => intValue.toString
      case _ => ""
    }
  }

  override def getAsset(roadLink: Seq[RoadLink]): Seq[AssetType] = {
    dao.fetchLinearAssetsByLinkIds(assetTypeInfo.typeId, roadLink.map(_.linkId), LinearAssetTypes.numericValuePropertyId, false)
  }

  override def verifyAsset(assets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    assets.flatMap { asset =>
      val roadLink = roadLinks.find(_.linkId == asset.linkId).get

      if (!comparingAssetAndTrafficValue(asset, trafficSign))
        Seq(Inaccurate(Some(asset.id), None, roadLink.municipalityCode, roadLink.administrativeClass))
      else
        Seq()
    }.toSet
  }

  //  override def verifyAssetX(asset: AssetType, roadLink: RoadLink, trafficSign: Seq[PersistedTrafficSign]): Boolean = {
  //    false
  //  }
  //
  //  def verifyAssetXX(asset: AssetType, pointOfInterest: Point, roadLink: RoadLink, trafficSign: Seq[PersistedTrafficSign], assetsOnRadius: Seq[PersistedLinearAsset]): Boolean = {
  //    trafficSign.exists(comparingAssetAndTrafficValue(asset, _)) || assetsOnRadius.exists(_.value == asset.value)
  //  }

  //  override def assetValidatorX(asset: AssetType, pointOfInterest: Point, defaultRoadLink: RoadLink): Boolean = {
  //
  //    val roadLinks = getLinkIdsByRadius(pointOfInterest)
  //    val assetsOnRadius = dao.fetchLinearAssetsByLinkIds(asset.typeId, roadLinks.map(_.linkId), LinearAssetTypes.numericValuePropertyId, false)
  //    val trafficSigns = trafficSignService.getTrafficSign(roadLinks.map(_.linkId))
  //
  //    validatorXX(pointOfInterest, defaultRoadLink, roadLinks, asset, assetsOnRadius ,trafficSigns)
  //  }

  //  def validatorXX(point: Point, prevRoadLink: RoadLink, roadLinks: Seq[RoadLink], asset: PersistedLinearAsset, assetsOnRadius: Seq[PersistedLinearAsset], trafficSigns: Seq[PersistedTrafficSign]) : Boolean = {
  //    val filteredRoadLink = roadLinks.filterNot(_.linkId == prevRoadLink.linkId)
  //    getAdjacentRoadLink(point, prevRoadLink, filteredRoadLink).exists { case (newRoadLink, (_, oppositePoint)) =>
  //      val filterTrafficSigns = trafficSigns.filter(_.linkId == newRoadLink)
  //      val existingAsset = assetsOnRadius.filter { asset =>
  //        asset.linkId == newRoadLink.linkId && GeometryUtils.areAdjacent(GeometryUtils.truncateGeometry2D(newRoadLink.geometry, asset.startMeasure, asset.endMeasure), point)
  //      }
  //
  //      if (filterTrafficSigns.isEmpty || existingAsset.isEmpty) {
  //        validatorXX(oppositePoint, newRoadLink, filteredRoadLink, asset, assetsOnRadius, trafficSigns)
  //      } else {
  //        verifyAssetXX(asset, point, newRoadLink, filterTrafficSigns, existingAsset )
  //      }
  //    }
  //  }


//  override def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign] = {
//    trafficSignService.getTrafficSign(Seq(roadLink.linkId)).filter(trafficSign =>
//      allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt)))
//  }

  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {

    withDynTransaction {
      inaccurateAssetDAO.deleteInaccurateAssetByIds(assetInfo.oldIds.toSeq)

      val assets = dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, assetInfo.newIds)
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(assets.map(_.linkId).toSet, newTransaction = false)

      assets.foreach { asset =>
        val roadLink = roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new NoSuchElementException)
        val assetGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
        val (first, last) = GeometryUtils.geometryEndpoints(assetGeometry)

        val trafficSingsByRadius: Seq[PersistedTrafficSign] = getPointOfInterest(first, last, SideCode.apply(asset.sideCode)).flatMap {
          trafficSignService.getTrafficSignByRadius(_, radiusDistance)
            .filter(sign => allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(sign, "trafficSigns_type").get.propertyValue.toInt)))
        }

        trafficSingsByRadius.foreach { trafficSign =>
          assetValidator(trafficSign).foreach {
            inaccurate =>
              (inaccurate.assetId, inaccurate.linkId) match {
                case (Some(assetId), _) => inaccurateAssetDAO.createInaccurateAsset(assetId, assetType, inaccurate.municipalityCode, inaccurate.administrativeClass)
                case (_, Some(linkId)) => inaccurateAssetDAO.createInaccurateLink(linkId, assetType, inaccurate.municipalityCode, roadLink.administrativeClass)
                case _ => None
              }
          }
        }
      }
    }
  }
}
