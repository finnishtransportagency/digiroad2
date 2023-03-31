package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.{AssetUpdate, FloatingReason, GeometryUtils, PersistedPointAsset, Point, PointAssetOperations}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.joda.time.DateTime

class PointAssetUpdater(service: PointAssetOperations) {
  val roadLinkChangeClient = new RoadLinkChangeClient
  val MaxDistanceDiffAllowed = 3.0 // Meters

  def adjustValidityDirection(assetDirection: Option[Int], digitizationChange: Boolean): Option[Int] = None
  def calculateBearing(point: Point, geometry: Seq[Point]): Option[Int] = None

  def getRoadLinkChanges(typeId: Int): Seq[RoadLinkChange] = {
    val latestSuccess = Queries.getLatestSuccessfulSamuutus(typeId)
    roadLinkChangeClient.getRoadLinkChanges(latestSuccess)
  }

  def updatePointAssets(typeId: Int): Unit = {
    PostGISDatabase.withDynTransaction {
      val linkChanges = getRoadLinkChanges(typeId).filterNot(_.changeType == RoadLinkChangeType.Add)
      val changedLinkIds = linkChanges.flatMap(change => change.oldLink).map(_.linkId).toSet

      val changedAssets = service.getPersistedAssetsByLinkIdsWithoutTransaction(changedLinkIds).toSet
      changedAssets.map(asset => {
        val linkChange = linkChanges.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == asset.linkId).get
        correctPersistedAsset(asset, linkChange) match {
          case adjustment if adjustment.floating =>
            service.floatingUpdate(asset.id, adjustment.floating, adjustment.floatingReason)
            service.createOperation(asset, adjustment)
          case adjustment =>
            val link = linkChange.newLinks.find(_.linkId == adjustment.linkId)
            val assetId = service.adjustmentOperation(asset, adjustment, link.get)
            service.createOperation(asset, adjustment.copy(assetId = assetId))
        }
      })
      Queries.updateLatestSuccessfulSamuutus(typeId)
    }
  }

  def correctPersistedAsset(asset: PersistedPointAsset, roadLinkChange: RoadLinkChange): AssetUpdate = {
    val nearestReplace = roadLinkChange.replaceInfo.find(change =>
      change.oldFromMValue <= asset.mValue && asset.mValue <= change.oldToMValue)
    (roadLinkChange.changeType, nearestReplace) match {
      case (RoadLinkChangeType.Remove, _) => setAssetAsFloating(asset, Some(FloatingReason.NoRoadLinkFound))
      case (_, Some(replace)) =>
        (roadLinkChange.oldLink, roadLinkChange.newLinks.find(_.linkId == replace.newLinkId)) match {
          case (Some(oldLink), Some(newLink)) =>
            val (floating, floatingReason) = shouldFloat(asset, replace, Some(newLink))
            if (floating)   setAssetAsFloating(asset, floatingReason)
            else            snapAssetToNewLink(asset, newLink, oldLink, replace.digitizationChange)
          case _ => setAssetAsFloating(asset, Some(FloatingReason.NoRoadLinkFound))
        }
      case _ => setAssetAsFloating(asset, Some(FloatingReason.NoRoadLinkFound))
    }
  }

  protected def shouldFloat(asset: PersistedPointAsset, replaceInfo: ReplaceInfo,
                            newLink: Option[RoadLinkInfo]): (Boolean, Option[FloatingReason]) = {
    newLink match {
      case Some(link) if link.municipality != asset.municipalityCode =>
        (true, Some(FloatingReason.DifferentMunicipalityCode))
      case None =>
        (true, Some(FloatingReason.NoRoadLinkFound))
      case _ =>
        (false, None)
    }
  }

  private def snapAssetToNewLink(asset: PersistedPointAsset, link: RoadLinkInfo, oldLink: RoadLinkInfo,
                                 digitizationChange: Boolean): AssetUpdate = {
    if (isVersionChange(asset.linkId, link.linkId) && link.linkLength == oldLink.linkLength) {
      toAssetUpdate(asset.id, Point(asset.lon, asset.lat), link.linkId, asset.mValue,
                    asset.getValidityDirection, asset.getBearing)
    } else {
      val oldLocation = Point(asset.lon, asset.lat)
      val newMValue = GeometryUtils.calculateLinearReferenceFromPoint(oldLocation, link.geometry)
      val newPoint = GeometryUtils.calculatePointFromLinearReference(link.geometry, newMValue)
      newPoint match {
        case Some(point) if point.distance2DTo(oldLocation) <= MaxDistanceDiffAllowed =>
          val calculatedBearing = calculateBearing(point, link.geometry)
          val fixedValidityDirection = adjustValidityDirection(asset.getValidityDirection, digitizationChange)
          toAssetUpdate(asset.id, point, link.linkId, newMValue, fixedValidityDirection, calculatedBearing)
        case _ =>
          setAssetAsFloating(asset, Some(FloatingReason.DistanceToRoad))
      }
    }
  }

  private def isVersionChange(oldId: String, newId: String): Boolean = {
    def linkIdWithoutVersion(id: String): String = id.split(":").head
    linkIdWithoutVersion(oldId) == linkIdWithoutVersion(newId)
  }

  private def setAssetAsFloating(asset: PersistedPointAsset, floatingReason: Option[FloatingReason]): AssetUpdate = {
    toAssetUpdate(asset.id, Point(asset.lon, asset.lat), asset.linkId, asset.mValue, asset.getValidityDirection,
                  asset.getBearing, floating = true, floatingReason)
  }

  private def toAssetUpdate(assetId: Long, point: Point, linkId: String, mValue: Double,
                            validityDirection: Option[Int], bearing: Option[Int],
                            floating: Boolean = false, floatingReason: Option[FloatingReason] = None): AssetUpdate = {
    AssetUpdate(assetId, point.x, point.y, linkId, mValue, validityDirection, bearing, DateTime.now().getMillis,
                floating, floatingReason)
  }
}