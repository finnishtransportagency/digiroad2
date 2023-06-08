package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Floating, Move}
import fi.liikennevirasto.digiroad2.util.assetUpdater._
import fi.liikennevirasto.digiroad2._
import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s.jackson.compactJson
import org.slf4j.{Logger, LoggerFactory}

class PointAssetUpdater(service: PointAssetOperations) {
  val roadLinkChangeClient = new RoadLinkChangeClient
  val logger: Logger = LoggerFactory.getLogger(getClass)

  val MaxDistanceDiffAllowed = 3.0 // Meters

  def adjustValidityDirection(assetDirection: Option[Int], digitizationChange: Boolean): Option[Int] = None
  def calculateBearing(point: Point, geometry: Seq[Point]): Option[Int] = None

  def updatePointAssets(typeId: Int): Unit = {
    val latestSuccess = PostGISDatabase.withDynSession ( Queries.getLatestSuccessfulSamuutus(typeId) )
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(latestSuccess)

    changeSets.foreach(changeSet => {
      logger.info(s"Started processing change set ${changeSet.key}")
      PostGISDatabase.withDynTransaction {
        updateByRoadLinks(typeId, changeSet.changes)
        Queries.updateLatestSuccessfulSamuutus(typeId, changeSet.targetDate)
      }
    })
  }

  protected def updateByRoadLinks(typeId: Int, changes: Seq[RoadLinkChange]): Unit = {
    val linkChanges = changes.filterNot(_.changeType == RoadLinkChangeType.Add)
    val changedLinkIds = linkChanges.flatMap(change => change.oldLink).map(_.linkId).toSet

    val changedAssets = service.getPersistedAssetsByLinkIdsWithoutTransaction(changedLinkIds).toSet
    val reportedChanges = changedAssets.map(asset => {
      val linkChange = linkChanges.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == asset.linkId).get
      correctPersistedAsset(asset, linkChange) match {
        case adjustment if adjustment.floating =>
          service.floatingUpdate(asset.id, adjustment.floating, adjustment.floatingReason)
          val newAsset = service.createOperation(asset, adjustment)
          Some(reportChange(asset, newAsset, Floating, linkChange, adjustment))
        case adjustment =>
          val link = linkChange.newLinks.find(_.linkId == adjustment.linkId)
          val assetId = service.adjustmentOperation(asset, adjustment, link.get)
          val newAsset = service.createOperation(asset, adjustment.copy(assetId = assetId))
          Some(reportChange(asset, newAsset, Move, linkChange, adjustment))
      }
    })
    val (reportBody, contentRowCount) = ChangeReporter.generateCSV(ChangeReport(typeId, reportedChanges.toSeq.flatten))
    ChangeReporter.saveReportToS3(AssetTypeInfo(typeId).label, reportBody, contentRowCount)
    val (reportBodyWithGeom, _) = ChangeReporter.generateCSV(ChangeReport(typeId, reportedChanges.toSeq.flatten), true)
    ChangeReporter.saveReportToS3(AssetTypeInfo(typeId).label, reportBodyWithGeom, contentRowCount, true)
  }

  def reportChange(oldPersistedAsset: PersistedPointAsset, newPersistedAsset: PersistedPointAsset,
                   changeType: ChangeType, roadLinkChange: RoadLinkChange, assetUpdate: AssetUpdate): ChangedAsset = {
    val oldLinearReference = LinearReference(oldPersistedAsset.linkId,oldPersistedAsset.mValue, None, None, oldPersistedAsset.getValidityDirection, 0.0)
    val oldValues = compactJson(oldPersistedAsset.propertyData.map(_.toJson))
    val oldAsset = Asset(oldPersistedAsset.id, oldValues, Some(oldPersistedAsset.municipalityCode),
      Some(Seq(Point(oldPersistedAsset.lon, oldPersistedAsset.lat))), Some(oldLinearReference), true, None)

    val newValues = compactJson(newPersistedAsset.propertyData.map(_.toJson))
    val newAsset = changeType match {
      case Floating =>
        Asset(newPersistedAsset.id, newValues, Some(newPersistedAsset.municipalityCode), None, None, true, assetUpdate.floatingReason)
      case _ =>
        val newLink = roadLinkChange.newLinks.find(_.linkId == newPersistedAsset.linkId).get
        val newLinearReference = LinearReference(newLink.linkId, newPersistedAsset.mValue, None, None, assetUpdate.validityDirection, 0.0)
        Asset(newPersistedAsset.id, newValues, Some(newPersistedAsset.municipalityCode),
          Some(Seq(Point(newPersistedAsset.lon, newPersistedAsset.lat))), Some(newLinearReference), true, None)
    }
    ChangedAsset(oldPersistedAsset.linkId, oldPersistedAsset.id, changeType, roadLinkChange.changeType, Some(oldAsset), Seq(newAsset))
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

  private def snapAssetToNewLink(asset: PersistedPointAsset, newLink: RoadLinkInfo, oldLink: RoadLinkInfo,
                                 digitizationChange: Boolean): AssetUpdate = {
    if (isVersionChange(asset.linkId, newLink.linkId) && newLink.linkLength == oldLink.linkLength) {
      toAssetUpdate(asset.id, Point(asset.lon, asset.lat), newLink.linkId, asset.mValue,
                    asset.getValidityDirection, asset.getBearing)
    } else {
      val oldLocation = Point(asset.lon, asset.lat)
      val newMValue = GeometryUtils.calculateLinearReferenceFromPoint(oldLocation, newLink.geometry)
      val newPoint = GeometryUtils.calculatePointFromLinearReference(newLink.geometry, newMValue)
      newPoint match {
        case Some(point) if point.distance2DTo(oldLocation) <= MaxDistanceDiffAllowed =>
          val calculatedBearing = calculateBearing(point, newLink.geometry)
          val fixedValidityDirection = adjustValidityDirection(asset.getValidityDirection, digitizationChange)
          toAssetUpdate(asset.id, point, newLink.linkId, newMValue, fixedValidityDirection, calculatedBearing)
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