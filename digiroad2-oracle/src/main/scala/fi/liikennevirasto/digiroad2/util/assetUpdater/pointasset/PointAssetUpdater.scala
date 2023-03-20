package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.{AssetUpdate, FloatingReason, GeometryUtils, PersistedPointAsset, Point, PointAssetOperations}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.joda.time.DateTime

class PointAssetUpdater(service: PointAssetOperations) {
  val roadLinkChangeClient = new RoadLinkChangeClient
  val MaxDistanceDiffAllowed = 3.0 // Meters

  def getRoadLinkChanges(typeId: Int): Seq[RoadLinkChange] = {
    val latestSuccess = Queries.getLatestSuccessfulSamuutus(typeId)
    roadLinkChangeClient.getRoadLinkChanges(latestSuccess)
  }

  def updatePointAssets(typeId: Int): Unit = {
    PostGISDatabase.withDynTransaction {
      val linkChanges = getRoadLinkChanges(typeId)
      val changedLinkIds = linkChanges.flatMap(change => change.oldLink).map(_.linkId).toSet

      val changedAssets = service.getPersistedAssetsByLinkIdsWithoutTransaction(changedLinkIds)
      val updatedAssets = changedAssets.map(asset => {
        val linkChange = linkChanges.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == asset.linkId).get
        correctPersistedAsset(asset, linkChange) match {
          case Some(adjustment) if adjustment.floating =>
            service.floatingUpdate(asset.id, adjustment.floating, adjustment.floatingReason)
            Some(service.createOperation(asset, adjustment))
          case Some(adjustment) =>
            val link = linkChange.newLinks.find(_.linkId == adjustment.linkId)
            service.adjustmentOperation(asset, adjustment, link.get)
            Some(service.createOperation(asset, adjustment))
          case _ => None
        }})
      Queries.updateLatestSuccessfulSamuutus(typeId)
    }
  }

  def correctPersistedAsset(asset: PersistedPointAsset, roadLinkChange: RoadLinkChange): Option[AssetUpdate] = {
    val nearestReplace = roadLinkChange.replaceInfo.find(change =>
      change.oldFromMValue <= asset.mValue && asset.mValue <= change.oldToMValue)
    (roadLinkChange.changeType, nearestReplace) match {
      case (RoadLinkChangeType.Remove, _) => setAssetAsFloating(asset, Some(FloatingReason.NoRoadLinkFound))
      case (_, Some(replace)) =>
        (roadLinkChange.oldLink, roadLinkChange.newLinks.find(_.linkId == replace.newLinkId)) match {
          case (Some(oldLink), Some(newLink)) =>
            val (floating, floatingReason) = shouldFloat(asset, Some(newLink), Some(oldLink))
            if (floating)   setAssetAsFloating(asset, floatingReason)
            else            snapAssetToNewLink(asset, newLink)
          case _ => setAssetAsFloating(asset, Some(FloatingReason.NoRoadLinkFound))
        }
      case _ => None
    }
  }

  protected def shouldFloat(asset: PersistedPointAsset, replacingLink: Option[RoadLinkInfo],
                 replacedLink: Option[RoadLinkInfo]): (Boolean, Option[FloatingReason]) = {
    (replacedLink, replacingLink) match {
      case (_, Some(replacing)) if replacing.municipality != asset.municipalityCode =>
        (true, Some(FloatingReason.DifferentMunicipalityCode))
      case (_, None) =>
        (true, Some(FloatingReason.NoRoadLinkFound))
      case _ =>
        (false, None)
    }
  }

  private def snapAssetToNewLink(asset: PersistedPointAsset, link: RoadLinkInfo): Option[AssetUpdate] = {
    if (asset.linkId.split(":").head == link.linkId.split(":").head)
      Some(AssetUpdate(asset.id, asset.lon, asset.lat, link.linkId, asset.mValue, DateTime.now().getMillis, floating = false))
    else {
      val oldLocation = Point(asset.lon, asset.lat)
      val newMValue = GeometryUtils.calculateLinearReferenceFromPoint(oldLocation, link.geometry)
      val newPoint = GeometryUtils.calculatePointFromLinearReference(link.geometry, newMValue)
      newPoint match {
        case Some(point) if point.distance2DTo(oldLocation) <= MaxDistanceDiffAllowed =>
          Some(AssetUpdate(asset.id, point.x, point.y, link.linkId, newMValue, DateTime.now().getMillis, floating = false))
        case _ =>
          setAssetAsFloating(asset, Some(FloatingReason.DistanceToRoad))
      }
    }
  }

  private def setAssetAsFloating(asset: PersistedPointAsset, floatingReason: Option[FloatingReason]): Option[AssetUpdate] = {
    Some(AssetUpdate(asset.id, asset.lon, asset.lat, asset.linkId, asset.mValue, asset.timeStamp,
      floating = true, floatingReason = floatingReason))
  }
}