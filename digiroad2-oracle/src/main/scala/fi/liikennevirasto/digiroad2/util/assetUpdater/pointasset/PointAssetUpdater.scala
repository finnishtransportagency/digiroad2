package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, KgvUtil, LogUtils}
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Floating, Move}
import fi.liikennevirasto.digiroad2.util.assetUpdater.{Asset, ChangeReport, ChangeReporter, ChangeType, ChangedAsset, LinearReferenceForReport, SamuutusFailed, ValidateSamuutus}
import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s.jackson.compactJson
import org.slf4j.{Logger, LoggerFactory}

class PointAssetUpdater(service: PointAssetOperations) {
  val roadLinkChangeClient = new RoadLinkChangeClient
  val logger: Logger = LoggerFactory.getLogger(getClass)

  lazy val eventBus: DigiroadEventBus = new DummyEventBus
  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val roadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)

  private var reportedChanges: Set[Some[ChangedAsset]] = Set()

  val MaxDistanceDiffAllowed = 3.0 // Meters

  def adjustValidityDirection(assetDirection: Option[Int], digitizationChange: Boolean): Option[Int] = None
  def calculateBearing(point: Point, geometry: Seq[Point]): Option[Int] = None
  def getRoadLink(linkInfo: Option[RoadLinkInfo]): Option[RoadLink] = {
    if (linkInfo.isDefined)
      roadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkInfo.get.linkId, newTransaction = false)
    else None
  }

  def updatePointAssets(typeId: Int): Unit = {
    val latestSuccess = PostGISDatabase.withDynSession ( Queries.getLatestSuccessfulSamuutus(typeId) )
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(latestSuccess)

    changeSets.foreach(changeSet => {
      logger.info(s"Started processing change set ${changeSet.key}")

      try {
        PostGISDatabase.withDynTransaction {
          updateByRoadLinks(typeId, changeSet)
          ValidateSamuutus.validate(typeId, changeSet)
          generateAndSaveReport(typeId, changeSet)
        }
      } catch {
        case e: SamuutusFailed =>
          generateAndSaveReport(typeId, changeSet)
          throw e
      }
    })
  }

  protected def updateByRoadLinks(typeId: Int, changeSet: RoadLinkChangeSet): Unit = {
    val changes = changeSet.changes
    val linkChanges = changes.filterNot(_.changeType == RoadLinkChangeType.Add)
    val changedLinkIds = linkChanges.flatMap(change => change.oldLink).map(_.linkId).toSet
    logger.info("Fetching assets")
    val changedAssets = service.getPersistedAssetsByLinkIdsWithoutTransaction(changedLinkIds).toSet

    logger.info("Starting to process changes")
    reportedChanges = LogUtils.time(logger, s"Samuuting logic finished: "){processChanges(changedAssets,linkChanges)}
  }
  /**
    * Each report saving array [[PointAssetUpdater.reportedChanges]] is erased.
    */
  private def generateAndSaveReport(typeId: Int, changeSet: RoadLinkChangeSet): Unit = {
    val (reportBody, contentRowCount) = ChangeReporter.generateCSV(ChangeReport(typeId, reportedChanges.toSeq.flatten))
    ChangeReporter.saveReportToS3(AssetTypeInfo(typeId).label, changeSet.targetDate, reportBody, contentRowCount)
    val (reportBodyWithGeom, _) = ChangeReporter.generateCSV(ChangeReport(typeId, reportedChanges.toSeq.flatten), true)
    ChangeReporter.saveReportToS3(AssetTypeInfo(typeId).label, changeSet.targetDate, reportBodyWithGeom, contentRowCount, true)
    reportedChanges = Set()
  }
  private def processChanges(changedAssets: Set[service.PersistedAsset], linkChanges: Seq[RoadLinkChange]): Set[Some[ChangedAsset]] = {
    changedAssets.map(asset => {
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
  }
  
  def reportChange(oldPersistedAsset: PersistedPointAsset, newPersistedAsset: PersistedPointAsset,
                   changeType: ChangeType, roadLinkChange: RoadLinkChange, assetUpdate: AssetUpdate): ChangedAsset = {
    val oldLinearReference = LinearReferenceForReport(oldPersistedAsset.linkId,oldPersistedAsset.mValue, None, None, oldPersistedAsset.getValidityDirection, 0.0)
    val oldValues = compactJson(oldPersistedAsset.propertyData.map(_.toJson))
    val oldAsset = Asset(oldPersistedAsset.id, oldValues, Some(oldPersistedAsset.municipalityCode),
      Some(Seq(Point(oldPersistedAsset.lon, oldPersistedAsset.lat))), Some(oldLinearReference), true, None)

    val newValues = compactJson(newPersistedAsset.propertyData.map(_.toJson))
    val newAsset = changeType match {
      case Floating =>
        Asset(newPersistedAsset.id, newValues, Some(newPersistedAsset.municipalityCode), None, None, true, assetUpdate.floatingReason)
      case _ =>
        val newLink = roadLinkChange.newLinks.find(_.linkId == newPersistedAsset.linkId).get
        val newLinearReference = LinearReferenceForReport(newLink.linkId, newPersistedAsset.mValue, None, None, assetUpdate.validityDirection, 0.0)
        Asset(newPersistedAsset.id, newValues, Some(newPersistedAsset.municipalityCode),
          Some(Seq(Point(newPersistedAsset.lon, newPersistedAsset.lat))), Some(newLinearReference), true, None)
    }
    ChangedAsset(oldPersistedAsset.linkId, oldPersistedAsset.id, changeType, roadLinkChange.changeType, Some(oldAsset), Seq(newAsset))
  }

  def correctPersistedAsset(asset: PersistedPointAsset, roadLinkChange: RoadLinkChange): AssetUpdate = {
    val possibleReplaces = roadLinkChange.replaceInfo.filter(change =>
      (change.oldFromMValue.getOrElse(0.0) <= asset.mValue && asset.mValue <= change.oldToMValue.getOrElse(0.0)) ||
        (change.oldFromMValue.getOrElse(0.0) >= asset.mValue && asset.mValue >= change.oldToMValue.getOrElse(0.0)))
    val bestReplace = if (possibleReplaces.size > 1) possibleReplaces.find(_.newLinkId.isDefined) else possibleReplaces.headOption
    (roadLinkChange.changeType, bestReplace) match {
      case (RoadLinkChangeType.Remove, _) => setAssetAsFloating(asset, Some(FloatingReason.NoRoadLinkFound))
      case (_, Some(replace)) =>
        (roadLinkChange.oldLink, roadLinkChange.newLinks.find(_.linkId == replace.newLinkId.getOrElse(""))) match {
          case (Some(oldLink), Some(newLink)) =>
            val (floating, floatingReason) = shouldFloat(asset, replace, Some(newLink), getRoadLink(Some(newLink)))
            if (floating)   setAssetAsFloating(asset, floatingReason)
            else            snapAssetToNewLink(asset, newLink, oldLink, replace.digitizationChange)
          case _ => setAssetAsFloating(asset, Some(FloatingReason.NoRoadLinkFound))
        }
      case _ => setAssetAsFloating(asset, Some(FloatingReason.NoRoadLinkFound))
    }
  }

  protected def shouldFloat(asset: PersistedPointAsset, replaceInfo: ReplaceInfo, newLinkInfo: Option[RoadLinkInfo],
                            newLink: Option[RoadLink]): (Boolean, Option[FloatingReason]) = {
    newLinkInfo match {
      case Some(linkInfo) if linkInfo.municipality != asset.municipalityCode =>
        (true, Some(FloatingReason.DifferentMunicipalityCode))
      case Some(linkInfo) if newLink.isEmpty =>
        (true, Some(FloatingReason.NoRoadLinkFound))
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