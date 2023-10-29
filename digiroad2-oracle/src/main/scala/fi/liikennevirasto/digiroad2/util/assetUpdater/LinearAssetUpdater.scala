package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetOperations, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinearAssetUtils, LogUtils}
import org.joda.time.DateTime
import org.json4s.jackson.compactJson
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.collection.{Seq, mutable}

sealed case class OperationStep(assetsAfter: Seq[PersistedLinearAsset] = Seq(), 
                                changeInfo: Option[ChangeSet] = None,
                                assetsBefore: Seq[PersistedLinearAsset] = Seq())

sealed case class OperationStepSplit(assetsAfter: Seq[PersistedLinearAsset] = Seq(),
                                changeInfo: Option[ChangeSet] = None,
                                newLinkId: String = "",
                                assetsBefore: Seq[PersistedLinearAsset] = Seq())
sealed case class Pair(oldAsset: Option[PersistedLinearAsset], newAsset: Option[PersistedLinearAsset])

sealed case class PairAsset(oldAsset: Option[Asset], newAsset: Option[Asset], changeType: ChangeType)

sealed case class LinkAndOperation(newLinkId: String, operation: OperationStepSplit)

/**
  * Samuutus loops is :
  * <br> 1) fetch changes 
  * <br> 2) [[filterChanges]] Apply only needed changes to assets by filtering unneeded away.
  * <br> 3) [[additionalRemoveOperationMass]] Mass operation based on list of removed links.
  * <br> 4) Start projecting everything into new links based on replace info.
  * <br> 4.1) [[nonAssetUpdate]] Add additional logic if something additional also need updating.
  * <br> 4.2) [[operationForNewLink]] Additional operation for new link.
  * <br> 4.3) [[additionalRemoveOperation]] Additional operation based on removed link.
  * <br> 5) All assets is projected into new links.
  * <br> 6) Run fillTopology to adjust assets based on link length and other assets on link.
  * <br> 7) [[adjustLinearAssets]] Override if asset need totally different fillTopology implementation.
  * <br> 8) [[additionalOperations]]  Additional logic after projecting everything in right place .
  * <br> 9) Start creating report row.
  * <br> 10) Save all projected 
  * <br> 11) Generate report
  * <br> Generally add additional logic only by overriding [[filterChanges]],[[additionalRemoveOperationMass]],
  * [[nonAssetUpdate]], [[operationForNewLink]],[[additionalRemoveOperation]] or [[additionalOperations]]
  * @param service Inject needed linear asset service which implement [[LinearAssetOperations]]
  */
class LinearAssetUpdater(service: LinearAssetOperations) {

  def eventBus: DigiroadEventBus = new DummyEventBus
  def roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  def roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)
  def assetFiller: AssetFiller = service.assetFiller
  def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  
  private val roadLinkChangeClient = new RoadLinkChangeClient

  private val changesForReport: mutable.ListBuffer[ChangedAsset] = ListBuffer()

  private val emptyStep: OperationStep = OperationStep(Seq(), None, Seq())

  // Mark generated part to be removed. Used when removing pavement in PaveRoadUpdater
  protected val removePart: Int = -1

  def resetReport(): Unit = {
    changesForReport.clear
  }
  def getReport(): mutable.Seq[ChangedAsset] = {
    changesForReport.distinct
  }

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def logChangeSetSizes(changeSet: ChangeSet): Unit = {
    logger.info(s"adjustedMValues size: ${changeSet.adjustedMValues.size}")
    logger.info(s"adjustedSideCodes size: ${changeSet.adjustedSideCodes.size}")
    logger.info(s"expiredAssetIds size: ${changeSet.expiredAssetIds.size}")
    logger.info(s"valueAdjustments size: ${changeSet.valueAdjustments.size}")
    logger.info(s"droppedAssetIds size: ${changeSet.droppedAssetIds.size}")
  }
  
  private val isDeleted: RoadLinkChange => Boolean = (change: RoadLinkChange) => {
    change.changeType.value == RoadLinkChangeType.Remove.value
  }
  private val isNew: RoadLinkChange => Boolean = (change: RoadLinkChange) => {
    change.changeType.value == RoadLinkChangeType.Add.value
  }

  private val isDeletedOrNew: RoadLinkChange => Boolean = (change: RoadLinkChange) => {
    isDeleted(change) || isNew(change)
  }

  /**
    * order list by oldToMValue and start looking for right replace info by looking first highest number
    * and then checking lower number until correct one is found.
    */
  private def sortAndFind(change: RoadLinkChange, asset: PersistedLinearAsset, finder: (ReplaceInfo, PersistedLinearAsset) => Boolean): Option[ReplaceInfo] = {
    change.replaceInfo.sortBy(_.oldToMValue).reverse.find(finder(_, asset))
  }
  /**
    * Checks if an asset falls within the specified slicing criteria.
    *
    * This method evaluates whether the given asset meets specific criteria defined by a `ReplaceInfo` object.
    * The criteria include matching `linkId`, ensuring that the `endMeasure` is greater than or equal to
    * the specified value, and verifying that the `startMeasure` is greater than or equal to another specified value.
    *
    * @param replaceInfo The criteria for replacement.
    * @param asset       The asset to be evaluated.
    * @return `true` if the asset meets the slicing criteria, `false` otherwise.
    */
  private def fallInWhenSlicing(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
    val hasMatchingLinkId = asset.linkId == replaceInfo.oldLinkId.getOrElse("")
    val endMeasureIsGreaterThanOldToValue = asset.endMeasure >= replaceInfo.oldToMValue.getOrElse(0.0)
    val startMeasureIsGreaterThanOldFromValue = asset.startMeasure >= replaceInfo.oldFromMValue.getOrElse(0.0)
    hasMatchingLinkId && endMeasureIsGreaterThanOldToValue && startMeasureIsGreaterThanOldFromValue
  }
  
  /**
    * Check if asset is positioned inside replace info
    * @param replaceInfo The criteria for replacement.
    * @param asset       The asset to be evaluated.
    * @return `true` if the asset meets the fallIn criteria, `false` otherwise.
    */
  private def fallInReplaceInfoOld(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
    val hasMatchingLinkId = replaceInfo.oldLinkId.getOrElse("") == asset.linkId
    if (replaceInfo.digitizationChange && replaceInfo.oldFromMValue.getOrElse(0.0) > replaceInfo.oldToMValue.getOrElse(0.0)) {
      val startMeasureIsSmallerThanOldFomValue = replaceInfo.oldFromMValue.getOrElse(0.0) >= asset.startMeasure
      val endMeasureIsSmallerThanOldToValue = replaceInfo.oldToMValue.getOrElse(0.0) <= asset.endMeasure
      hasMatchingLinkId && startMeasureIsSmallerThanOldFomValue && endMeasureIsSmallerThanOldToValue
    } else {
      val startMeasureIsGreaterThanOldFomValue = replaceInfo.oldFromMValue.getOrElse(0.0) <= asset.startMeasure
      /**  if asset is longer than replaceInfo.oldToMValue, it need to be split in [[LinearAssetUpdater.slicer]] */
      val endMeasureIsGreaterThanOldToValue = replaceInfo.oldToMValue.getOrElse(0.0) >= asset.endMeasure
      hasMatchingLinkId && startMeasureIsGreaterThanOldFomValue && endMeasureIsGreaterThanOldToValue
    }
  }
  
  private def toRoadLinkForFillTopology(roadLink: RoadLink): RoadLinkForFillTopology = {
    RoadLinkForFillTopology(linkId = roadLink.linkId, length = roadLink.length, 
      trafficDirection = roadLink.trafficDirection, administrativeClass = roadLink.administrativeClass,
      linkSource = roadLink.linkSource, linkType = roadLink.linkType, 
      constructionType = roadLink.constructionType, 
      geometry = roadLink.geometry, municipalityCode = roadLink.municipalityCode)
  }
  private def toOperationStep(step: OperationStepSplit): OperationStep = {
    OperationStep(assetsAfter = step.assetsAfter, changeInfo = step.changeInfo, assetsBefore = step.assetsBefore)
  }
  
  private def convertToPersisted(asset: PieceWiseLinearAsset): PersistedLinearAsset = {
    PersistedLinearAsset(asset.id, asset.linkId, asset.sideCode.value,
      asset.value, asset.startMeasure, asset.endMeasure, asset.createdBy,
      asset.createdDateTime, asset.modifiedBy, asset.modifiedDateTime, asset.expired, asset.typeId, asset.timeStamp,
      asset.geomModifiedDate, asset.linkSource, asset.verifiedBy, asset.verifiedDate, asset.informationSource, asset.oldId)
  }

  /**
    * Merge assetsBefore, assetsAfter and changeInfo from two given [[OperationStepSplit]]
    *
    * @param a
    * @param b
    * @return [[Some[OperationStep]]]
    */
  private def mergerOperationsSplit(a: Option[OperationStepSplit], b: Option[OperationStepSplit]): Some[OperationStepSplit] = {
    val (aBefore, newLinkIdA, assetsA, changeInfoA) = (a.get.assetsBefore, a.get.newLinkId, a.get.assetsAfter, a.get.changeInfo)
    val (bBefore, newLinkIdB, assetsB, changeInfoB) = (b.get.assetsBefore, b.get.newLinkId, b.get.assetsAfter, b.get.changeInfo)
    val newLinkId = if (newLinkIdA.isEmpty) newLinkIdB else newLinkIdA
    Some(OperationStepSplit((assetsA ++ assetsB).distinct, Some(LinearAssetFiller.combineChangeSets(changeInfoA.get, changeInfoB.get)), newLinkId, (aBefore ++ bBefore).distinct))
  }

  /**
    * Merge assetsBefore, assetsAfter and changeInfo from two given [[OperationStep]]
    * @param a
    * @param b
    * @return [[Some[OperationStep]]]
    */
  private def mergerOperations(a: Option[OperationStep], b: Option[OperationStep]): Some[OperationStep] = {
    val (aBefore, assetsA, changeInfoA) = (a.get.assetsBefore, a.get.assetsAfter, a.get.changeInfo)
    val (bBefore, assetsB, changeInfoB) = (b.get.assetsBefore, b.get.assetsAfter, b.get.changeInfo)
    Some(OperationStep((assetsA ++ assetsB).distinct, Some(LinearAssetFiller.combineChangeSets(changeInfoA.get, changeInfoB.get)), (aBefore ++ bBefore).distinct))
  }

  def reportAssetChanges(oldAsset: Option[PersistedLinearAsset], newAsset: Option[PersistedLinearAsset],
                         roadLinkChanges: Seq[RoadLinkChange],
                         operationSteps: OperationStep, rowType: Option[ChangeType] = None, useGivenChange: Boolean = false): OperationStep = {

    def propertyChange: Boolean = {
      val assetInherit = oldAsset.isDefined && newAsset.isDefined && oldAsset.get.id == newAsset.get.oldId
      assetInherit && !oldAsset.get.value.get.equals(newAsset.get.value.get)
    }
    
    if (oldAsset.isEmpty && newAsset.isEmpty) return operationSteps

    val linkId = if (oldAsset.nonEmpty) oldAsset.get.linkId else newAsset.head.linkId
    val assetId = if (oldAsset.nonEmpty) oldAsset.get.id else 0

    val relevantRoadLinkChange = if (useGivenChange) {
      roadLinkChanges.head
    } else roadLinkChanges.find(change => {
      val roadLinkChangeOldLinkId = change.oldLink match {
        case Some(oldLink) => Some(oldLink.linkId)
        case None => None
      }
      val assetOldLinkId = oldAsset match {
        case Some(asset) => Some(asset.linkId)
        case None => None
      }
      val roadLinkChangeNewLinkIds = change.newLinks.map(_.linkId).sorted
      val checkByOldAsset = (roadLinkChangeOldLinkId.nonEmpty && assetOldLinkId.nonEmpty) && roadLinkChangeOldLinkId == assetOldLinkId
      if (newAsset.isDefined) checkByOldAsset || roadLinkChangeNewLinkIds.contains(newAsset.get.linkId)
      else checkByOldAsset
     
    }).getOrElse({
      val oldAssetLinkId = oldAsset match {
        case Some(asset) => asset.linkId
        case _ => "Old asset not defined"
      }
      val newAssetLinkId = newAsset match {
        case Some(asset) => asset.linkId
        case _ => "New asset not defined"
      }
      throw new NoSuchElementException(s"Could not find relevant road link change. Asset old linkId: $oldAssetLinkId Asset new linkId: $newAssetLinkId")
    })

    val before = oldAsset match {
      case Some(ol) =>
        val values = compactJson(ol.toJson)
        val linkOld = relevantRoadLinkChange.oldLink.get
        val assetGeometry = GeometryUtils.truncateGeometry3D(linkOld.geometry, ol.startMeasure, ol.endMeasure)
        val measures = Measures(ol.startMeasure, ol.endMeasure).roundMeasures()
        val linearReference = LinearReference(ol.linkId, measures.startMeasure, Some(measures.endMeasure), Some(ol.sideCode), None, measures.length())
        Some(Asset(ol.id, values, Some(linkOld.municipality), Some(assetGeometry), Some(linearReference)))
      case None =>
        val linkOld = relevantRoadLinkChange.oldLink
        if (linkOld.nonEmpty) {
          val linearReference = LinearReference(linkOld.get.linkId, 0, None, None, None, 0)
          Some(Asset(0, "", Some(linkOld.get.municipality), None, Some(linearReference)))
        } else None
    }

    val after = newAsset.map(asset => {
      val newLink = relevantRoadLinkChange.newLinks.find(_.linkId == asset.linkId).get
      val values = compactJson(asset.toJson)
      val assetGeometry = GeometryUtils.truncateGeometry3D(newLink.geometry, asset.startMeasure, asset.endMeasure)
      val measures = Measures(asset.startMeasure, asset.endMeasure).roundMeasures()
      val linearReference = LinearReference(asset.linkId, measures.startMeasure, Some(measures.endMeasure), Some(asset.sideCode), None, measures.length())
      Asset(asset.id, values, Some(newLink.municipality), Some(assetGeometry), Some(linearReference))
    })
    
    if (propertyChange) {
      changesForReport.append(ChangedAsset(linkId, assetId, ChangeTypeReport.PropertyChange, 
        relevantRoadLinkChange.changeType, before, after.toSeq))
    } else {
      if (rowType.isDefined) {
        changesForReport.append(ChangedAsset(linkId, assetId, rowType.get,
          relevantRoadLinkChange.changeType, before, after.toSeq))
      }else {
        if (relevantRoadLinkChange.changeType == RoadLinkChangeType.Split) {
          changesForReport.append(ChangedAsset(linkId, assetId, ChangeTypeReport.Divided,
            relevantRoadLinkChange.changeType, before, after.toSeq))
        } else {
          changesForReport.append(ChangedAsset(linkId, assetId, ChangeTypeReport.Replaced,
            relevantRoadLinkChange.changeType, before, after.toSeq))
        }
      }
    }
    operationSteps
  }
  /**
    * 9) start creating report row
    * @param initStep
    * @param assetsInNewLink
    * @param changes
    * @return
    */
  private def reportingAdjusted(initStep: OperationStep, assetsInNewLink: OperationStep,changes: Seq[RoadLinkChange]): Some[OperationStep] = {
    val newLinks = changes.filter(isNew).flatMap(_.newLinks).map(_.linkId)
    val (assetsOnNew,assetsWhichAreMoved) = assetsInNewLink.assetsAfter.partition(a=>newLinks.contains(a.linkId))
    val pairs = assetsWhichAreMoved.flatMap(asset => createPair(Some(asset), assetsInNewLink.assetsBefore)).distinct
    val report = pairs.filter(_.newAsset.isDefined).map(pair => {
      if (!assetsInNewLink.changeInfo.get.expiredAssetIds.contains(pair.newAsset.get.id)) {
        Some(reportAssetChanges(pair.oldAsset, pair.newAsset, changes, assetsInNewLink))
      } else Some(assetsInNewLink)
    }).foldLeft(Some(initStep))(mergerOperations)
    Some(report.get.copy(assetsAfter = report.get.assetsAfter ++ assetsOnNew))
  }
  
  /**
    * Create pair by using asset id or old asset id when id is 0.
    * @param updatedAsset asset after samuutus
    * @param oldAssets assets before samuutus
    * @return
    */
  private def createPair(updatedAsset: Option[PersistedLinearAsset], oldAssets: Seq[PersistedLinearAsset]): Seq[Pair] = {
    def findByOldId(updatedAssets: Option[PersistedLinearAsset], oldAssets: Seq[PersistedLinearAsset]): Seq[Pair] = {
      val oldAssetByOldId = oldAssets.find(_.id == updatedAssets.get.oldId)
      if (oldAssetByOldId.isDefined) Seq(Pair(oldAssetByOldId, updatedAssets)) else useGivenIfPossible(updatedAssets, oldAssets)
    }

    def useGivenIfPossible(updatedAssets: Option[PersistedLinearAsset], oldAssets: Seq[PersistedLinearAsset]): Seq[Pair] = {
      if (oldAssets.size == 1) Seq(Pair(oldAssets.headOption, updatedAssets)) else Seq(Pair(None, updatedAssets))
    }

    val oldAsset = oldAssets.find(_.id == updatedAsset.get.id)
    if (oldAsset.isDefined) Seq(Pair(oldAsset, updatedAsset)) else findByOldId(updatedAsset, oldAssets)
  }


  /**
    * Each report saving array [[LinearAssetUpdater.changesForReport]] is erased.
    */
  def generateAndSaveReport(typeId: Int, processedTo: DateTime = DateTime.now()): Unit = {
    val changeReport = ChangeReport(typeId, getReport())
    val (reportBody, contentRowCount) = ChangeReporter.generateCSV(changeReport)
    ChangeReporter.saveReportToS3(AssetTypeInfo(changeReport.assetType).label, processedTo, reportBody, contentRowCount)
    val (reportBodyWithGeom, _) = ChangeReporter.generateCSV(changeReport, withGeometry = true)
    ChangeReporter.saveReportToS3(AssetTypeInfo(changeReport.assetType).label, processedTo, reportBodyWithGeom, contentRowCount, hasGeometry = true)
    resetReport()
  }

  def filterChanges(changes: Seq[RoadLinkChange]): Seq[RoadLinkChange] = changes
  def operationForNewLink(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = None
  def additionalRemoveOperation(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = None
  def additionalRemoveOperationMass(expiredLinks: Seq[String]): Unit = {}
  def additionalOperations(operationStep: OperationStep, changes: Seq[RoadLinkChange]): Option[OperationStep] = None
  def nonAssetUpdate(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = None
  
  def updateLinearAssets(typeId: Int): Unit = {
    val latestSuccess = PostGISDatabase.withDynSession(Queries.getLatestSuccessfulSamuutus(typeId))
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(latestSuccess)
    logger.info(s"Processing ${changeSets.size}} road link changes set")
    changeSets.foreach(changeSet => {
      logger.info(s"Started processing change set ${changeSet.key}")
      withDynTransaction {
        updateByRoadLinks(typeId, changeSet.changes)
        Queries.updateLatestSuccessfulSamuutus(typeId, changeSet.targetDate)
      }
      generateAndSaveReport(typeId, changeSet.targetDate)
    })
  }

  def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]): Unit = {
    val changes = filterChanges(changesAll)
    val oldIds = changes.filterNot(isDeletedOrNew).map(_.oldLink.get.linkId)
    val deletedLinks = changes.filter(isDeleted).map(_.oldLink.get.linkId)
    val addedLinksCount = changes.filter(isNew).flatMap(_.newLinks).size
    // here we assume that RoadLinkProperties updater has already remove override if KMTK version traffic direction is same.
    // still valid overrided has also been samuuted
    
    val newLinkIds = changes.flatMap(_.newLinks.map(_.linkId))
    logger.info("Fetching road links and assets")
    val newRoadLinks = roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(newLinkIds.toSet, false)
    
    val existingAssets = service.fetchExistingAssetsByLinksIdsString(typeId, oldIds.toSet, deletedLinks.toSet, newTransaction = false)
    val initChangeSet = LinearAssetFiller.initWithExpiredIn(existingAssets, deletedLinks)
    
    logger.info(s"Processing assets: ${typeId}, assets count: ${existingAssets.size}, number of changes in the sets: ${changes.size}")
    logger.info(s"Deleted links count: ${deletedLinks.size}, new links count: ${addedLinksCount}")
    logger.info("Starting to process changes")
    val (projectedAssets, changedSet) = LogUtils.time(logger, s"Samuuting logic finished: ") {
      fillNewRoadLinksWithPreviousAssetsData(typeId, newRoadLinks, existingAssets, changes, initChangeSet)
    }
    
    additionalRemoveOperationMass(deletedLinks)
    logger.info("Starting to save changeSets")
    logChangeSetSizes(changedSet)
    LogUtils.time(logger, s"Saving changeSets took: ") {
      updateChangeSet(changedSet)
    }
    logger.info("Starting to save generated or updated")
    LogUtils.time(logger, s"Saving generated or updated took: ") {
      persistProjectedLinearAssets(projectedAssets.filterNot(_.id == removePart).filter(_.id == 0L))
    }
  }
  /**
    * 4) Start projecting everything into new links based on replace info.
    * @param typeId
    * @param links
    * @param assetsAll
    * @param changes
    * @param changeSet
    * @return
    */
  private def fillNewRoadLinksWithPreviousAssetsData(typeId: Int, links: Seq[RoadLink],
                                                       assetsAll: Seq[PersistedLinearAsset], changes: Seq[RoadLinkChange],
                                                       changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val initStep = OperationStep(Seq(), Some(changeSet))
    logger.info(s"Projecting ${assetsAll.size} assets to new links")
    val projectedToNewLinks = LogUtils.time(logger, "Projecting assets to new links") {
      changes.map(goThroughChanges(assetsAll, changeSet, initStep, _,OperationStepSplit(Seq(), Some(changeSet))))
        .filter(_.nonEmpty).filter(_.get.assetsAfter.nonEmpty)
    }

    logger.info(s"Adjusting ${projectedToNewLinks.size} projected assets")
    val OperationStep(assetsOperated, changeInfo,_) = LogUtils.time(logger, "Adjusting projected assets") {
      adjustAndReport(typeId, links, projectedToNewLinks, initStep,changes).get
    }

    changeInfo.get.expiredAssetIds.map(asset => {
      val alreadyReported = changesForReport.map(_.before).filter(_.nonEmpty).map(_.get.assetId)
      if (!alreadyReported.contains(asset)) {
        val expiringAsset = assetsAll.find(_.id == asset)
        reportAssetChanges(expiringAsset, None, changes.filterNot(isNew), emptyStep, Some(ChangeTypeReport.Deletion))
      }
    })

    (assetsOperated, changeInfo.get)
  }
  
  private def goThroughChanges(assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet,
                               initStep: OperationStep, change: RoadLinkChange,initStepSplit:OperationStepSplit): Option[OperationStep] = {
    nonAssetUpdate(change, Seq(), null)
    change.changeType match {
      case RoadLinkChangeType.Add =>
        val operation = operationForNewLink(change, assetsAll, changeSets).getOrElse(initStep)
        Some(reportAssetChanges(None, operation.assetsAfter.headOption, Seq(change), operation, Some(ChangeTypeReport.Creation), true))
      case RoadLinkChangeType.Remove => additionalRemoveOperation(change, assetsAll, changeSets)
      case _ =>
        val assets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
        if (assets.nonEmpty) {
          change.changeType match {
            case RoadLinkChangeType.Replace =>
              assets.map(a => projecting(changeSets, change, a, a)).
                filter(_.nonEmpty).foldLeft(Some(initStep))(mergerOperations)
            case RoadLinkChangeType.Split =>
              handleSplits(changeSets, initStepSplit, change, assets)
            case _ => None
          }
        } else None
    }
  }
  private def adjustAndReport(typeId: Int, links: Seq[RoadLink],
                              assetUnderReplace: Seq[Option[OperationStep]], initStep: OperationStep,changes: Seq[RoadLinkChange]): Option[OperationStep] = {
    val merged = assetUnderReplace.foldLeft(Some(OperationStep(Seq(), Some(LinearAssetFiller.emptyChangeSet))))(mergerOperations)
    val adjusted = adjustAndAdditionalOperations(typeId, links, merged,changes)
    reportingAdjusted(initStep, adjusted,changes)
  }

  private def adjustAndAdditionalOperations(typeId: Int, links: Seq[RoadLink],
                                            assets: Option[OperationStep], changes: Seq[RoadLinkChange]): OperationStep = {
    val adjusted = adjustAssets(typeId, links, assets.get)
    val additionalSteps = additionalOperations(adjusted, changes)
    if (additionalSteps.isDefined) additionalSteps.get else adjusted
  }
  /**
    * 6) Run fillTopology to adjust assets based on link length and other assets on link.
    * @param typeId
    * @param links
    * @param operationStep
    * @return
    */
  private def adjustAssets(typeId: Int, links: Seq[RoadLink], operationStep: OperationStep): OperationStep = {
    val OperationStep(assetsAfter,changeSetFromOperation,assetsBefore) = operationStep
    val assetsOperated = assetsAfter.filterNot(a => changeSetFromOperation.get.expiredAssetIds.contains(a.id))
    val convert = links.map(toRoadLinkForFillTopology)
    val groupedAssets = assetFiller.toLinearAssetsOnMultipleLinks(assetsOperated, convert).groupBy(_.linkId)
    val (adjusted, changeSet) = adjustLinearAssets(typeId,convert, groupedAssets, changeSetFromOperation)
    OperationStep(adjusted.map(convertToPersisted), Some(changeSet), assetsBefore)
  }
  
  protected def adjustLinearAssets(typeId: Int,roadLinks: Seq[RoadLinkForFillTopology], assets: Map[String, Seq[PieceWiseLinearAsset]],
                                   changeSet: Option[ChangeSet] = None): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    assetFiller.fillTopologyChangesGeometry(roadLinks, assets, typeId, changeSet)
  }

  private def handleSplits(changeSet: ChangeSet, initStep:  OperationStepSplit, change: RoadLinkChange, assets: Seq[PersistedLinearAsset]): Option[OperationStep] = {
    val divided = operationForSplit(change, assets, changeSet).map(a=>LinkAndOperation(a.newLinkId,a))
    val updatedAdjustments = updateSplitsWithExpiredIds(divided)
    val splitOperation = updatedAdjustments.map(a=>Some(a.operation)).foldLeft(Some(initStep))(mergerOperationsSplit)
    val updatedSplitOperation = updateSplitOperationWithExpiredIds(splitOperation, getIdsForAssetsOutsideSplitGeometry(updatedAdjustments))
    reportAssetExpirationAfterSplit(updatedAdjustments,change)
    if (updatedSplitOperation.isDefined){
      Some(OperationStep(
        assetsAfter   = updatedSplitOperation.get.assetsAfter, 
        changeInfo    = updatedSplitOperation.get.changeInfo, 
        assetsBefore  = updatedSplitOperation.get.assetsBefore))
    } else None
  }

  private def operationForSplit(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSet: ChangeSet): Seq[OperationStepSplit] = {
    val relevantAssets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
    relevantAssets.flatMap(sliceFrom => {
      val sliced = slicer(Seq(sliceFrom), Seq(), change)
      sliced.map(part => projectingSplit(changeSet, change, part, sliceFrom).get)
    })
  }

  @tailrec
  private def slicer(assets: Seq[PersistedLinearAsset], fitIntoRoadLinkPrevious: Seq[PersistedLinearAsset], change: RoadLinkChange): Seq[PersistedLinearAsset] = {
    def slice(change: RoadLinkChange, asset: PersistedLinearAsset): Seq[PersistedLinearAsset] = {
      val selectInfo = sortAndFind(change, asset, fallInWhenSlicing).getOrElse(throw new NoSuchElementException(s"Replace info for asset ${asset.id} on link ${asset.linkId} not found from change ${change}"))

      val shorted = asset.copy(endMeasure = selectInfo.oldToMValue.getOrElse(0.0))
      val newPart = asset.copy(id = 0, startMeasure = selectInfo.oldToMValue.getOrElse(0.0), oldId = asset.id)

      val shortedLength = shorted.endMeasure - shorted.startMeasure
      val newPartLength = newPart.endMeasure - newPart.startMeasure

      val shortedFilter = if (shortedLength > 0) Option(shorted) else None

      val newPartFilter = if (newPartLength > 0) Option(newPart) else None

      logger.debug(s"asset old part start ${shorted.startMeasure}, asset end ${shorted.endMeasure}")
      logger.debug(s"asset new part start ${newPart.startMeasure}, asset end ${newPart.endMeasure}")

      (shortedFilter, newPartFilter) match {
        case (None, None) => Seq()
        case (Some(shortedSome), None) => Seq(shortedSome)
        case (None, Some(newParSome)) => Seq(newParSome)
        case (Some(shortedSome), Some(newPartSome)) => Seq(shortedSome, newPartSome)
      }
    }

    def partitioner(asset: PersistedLinearAsset, change: RoadLinkChange): Boolean = {
      def assetIsAlreadySlicedOrFitIn(asset: PersistedLinearAsset, selectInfo: ReplaceInfo): Boolean = {
        asset.endMeasure <= selectInfo.oldToMValue.getOrElse(0.0) && asset.startMeasure >= selectInfo.oldFromMValue.getOrElse(0.0)
      }

      val selectInfoOpt = sortAndFind(change, asset, fallInReplaceInfoOld)
      if (selectInfoOpt.nonEmpty) assetIsAlreadySlicedOrFitIn(asset, selectInfoOpt.get)
      else false
    }

    val (fitIntoRoadLink, assetGoOver) = assets.partition(partitioner(_, change))
    if (assetGoOver.nonEmpty) {
      val sliced = assetGoOver.flatMap(slice(change, _))
      slicer(sliced, fitIntoRoadLink ++ fitIntoRoadLinkPrevious, change)
    } else {
      fitIntoRoadLinkPrevious ++ fitIntoRoadLink
    }
  }

  /**
    * @param changeSets
    * @param change
    * @param asset asset which will be projected
    * @param beforeAsset asset before projection
    * @return
    */
  private def projecting(changeSets: ChangeSet, change: RoadLinkChange, asset: PersistedLinearAsset, beforeAsset: PersistedLinearAsset) = {
    val (_, projected, changeSet) = projectByUsingReplaceInfo(changeSets, change, asset)
    Some(OperationStep(Seq(projected), Some(changeSet), assetsBefore = Seq(beforeAsset)))
  }
  /**
    * @param changeSets
    * @param change
    * @param asset       asset which will be projected
    * @param beforeAsset asset before projection
    * @return
    */
  private def projectingSplit(changeSets: ChangeSet, change: RoadLinkChange, asset: PersistedLinearAsset, beforeAsset: PersistedLinearAsset) = {
    val (newId, projected, changeSet) = projectByUsingReplaceInfo(changeSets, change, asset)
    Some(OperationStepSplit(Seq(projected), Some(changeSet),newLinkId = newId, assetsBefore = Seq(beforeAsset)))
  }

  private def projectByUsingReplaceInfo(changeSets: ChangeSet, change: RoadLinkChange, asset: PersistedLinearAsset) = {
    val info = sortAndFind(change, asset, fallInReplaceInfoOld).getOrElse(throw new NoSuchElementException(s"Replace info for asset ${asset.id} on link ${asset.linkId} not found from change ${change}"))
    val newId = info.newLinkId.getOrElse("")
    val maybeLink = change.newLinks.find(_.linkId == newId)
    val maybeLinkLength = if (maybeLink.nonEmpty) maybeLink.get.linkLength else 0
    val (projected, changeSet) = projectLinearAsset(asset.copy(linkId = newId),
      Projection(
        info.oldFromMValue.getOrElse(0.0), info.oldToMValue.getOrElse(0.0),
        info.newFromMValue.getOrElse(0), info.newToMValue.getOrElse(0),
        LinearAssetUtils.createTimeStamp(),
        newId, maybeLinkLength),
      changeSets, info.digitizationChange)
    (newId, projected, changeSet)
  }
  private def projectLinearAsset(asset: PersistedLinearAsset, projection: Projection, changedSet: ChangeSet, digitizationChanges: Boolean): (PersistedLinearAsset, ChangeSet) = {
    val newLinkId = projection.linkId
    val assetId = asset.linkId match {
      case projection.linkId => asset.id
      case _ => 0
    }
    val (newStart, newEnd, newSideCode) = MValueCalculator.calculateNewMValues(
      AssetLinearReference(asset.id, asset.startMeasure, asset.endMeasure, asset.sideCode),
      projection, projection.linkLength, digitizationChanges)

    val changeSet = assetId match {
      case 0 => changedSet
      case _ => changedSet.copy(adjustedMValues = changedSet.adjustedMValues ++ Seq(MValueAdjustment(assetId, newLinkId, newStart, newEnd, projection.timeStamp)),
        adjustedSideCodes =
          if (asset.sideCode == newSideCode) {
            changedSet.adjustedSideCodes
          } else {
            changedSet.adjustedSideCodes ++ Seq(SideCodeAdjustment(assetId, SideCode.apply(newSideCode), asset.typeId))
          }
      )
    }

    (PersistedLinearAsset(id = assetId, linkId = newLinkId, sideCode = newSideCode,
      value = asset.value, startMeasure = newStart, endMeasure = newEnd,
      createdBy = asset.createdBy, createdDateTime = asset.createdDateTime, modifiedBy = asset.modifiedBy,
      modifiedDateTime = asset.modifiedDateTime, expired = false, typeId = asset.typeId,
      timeStamp = projection.timeStamp, geomModifiedDate = None, linkSource = asset.linkSource, verifiedBy = asset.verifiedBy, verifiedDate = asset.verifiedDate,
      informationSource = asset.informationSource, asset.oldId), changeSet)
  }

  def updateChangeSet(changeSet: ChangeSet): Unit = {

    if (changeSet.adjustedMValues.nonEmpty)
      logger.info(s"Saving adjustments for asset/link ids=${changeSet.adjustedMValues.map(a => s"${a.assetId}/${a.linkId}").mkString(", ")}")
      logger.debug(s"Saving adjustments for asset/link ids=${changeSet.adjustedMValues.map(a => s"${a.assetId}/${a.linkId} start measure: ${a.startMeasure} end measure: ${a.endMeasure}").mkString(", ")}")
    changeSet.adjustedMValues.foreach { adjustment =>
      dao.updateMValuesChangeInfo(adjustment.assetId, adjustment.linkId, Measures(adjustment.startMeasure, adjustment.endMeasure).roundMeasures(), LinearAssetUtils.createTimeStamp())
    }

    val ids = changeSet.expiredAssetIds.toSeq

    if (ids.nonEmpty)
      logger.info(s"Expiring ids ${ids.mkString(", ")}")
    ids.foreach(dao.updateExpiration(_, expired = true, AutoGeneratedUsername.generatedInUpdate))

    if (changeSet.adjustedSideCodes.nonEmpty)
      logger.info(s"Saving SideCode adjustments for asset/link ids=${changeSet.adjustedSideCodes.map(a => "" + a.assetId).mkString(", ")}")

    changeSet.adjustedSideCodes.foreach { adjustment =>
      adjustedSideCode(adjustment)
    }

    if (changeSet.valueAdjustments.nonEmpty)
      logger.info(s"Saving value adjustments for assets: ${changeSet.valueAdjustments.map(a => "" + a.asset.id).mkString(", ")}")
    changeSet.valueAdjustments.foreach { adjustment =>
      service.updateWithoutTransaction(Seq(adjustment.asset.id), adjustment.asset.value.get, adjustment.asset.modifiedBy.get)
    }
  }

  protected def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newLinearAssets.nonEmpty)
      logger.info(s"Saving projected linear assets, count: ${newLinearAssets.size}")

    def getValuePropertyId(value: Option[Value], typeId: Int) = {
      value match {
        case Some(NumericValue(intValue)) =>
          LinearAssetTypes.numericValuePropertyId
        case Some(TextualValue(textValue)) =>
          LinearAssetTypes.getValuePropertyId(typeId)
        case _ => ""
      }
    }

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    logger.info(s"insert assets count: ${toInsert.size}")
    logger.info(s"update assets count: ${toUpdate.size}")

    val roadLinks = roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
    if (toUpdate.nonEmpty) {
      val toUpdateText = toUpdate.filter(a =>
        Set(EuropeanRoads.typeId, ExitNumbers.typeId).contains(a.typeId))

      val groupedNum = toUpdate.filterNot(a => toUpdateText.contains(a)).groupBy(a => getValuePropertyId(a.value, a.typeId)).filterKeys(!_.equals(""))
      val groupedText = toUpdateText.groupBy(a => getValuePropertyId(a.value, a.typeId)).filterKeys(!_.equals(""))

      val persisted = (groupedNum.flatMap(group => dao.fetchLinearAssetsByIds(group._2.map(_.id).toSet, group._1)).toSeq ++
        groupedText.flatMap(group => dao.fetchAssetsWithTextualValuesByIds(group._2.map(_.id).toSet, group._1)).toSeq).groupBy(_.id)

      updateProjected(toUpdate, persisted)
      if (newLinearAssets.nonEmpty)
        logger.info(s"Updated ids/linkids ${toUpdate.map(a => (a.id, a.linkId))}")
    }
    toInsert.foreach { linearAsset =>
      val roadlink = roadLinks.find(_.linkId == linearAsset.linkId)
      val id =
        (linearAsset.createdBy, linearAsset.createdDateTime) match {
          case (Some(createdBy), Some(createdDateTime)) =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure).roundMeasures(), AutoGeneratedUsername.generatedInUpdate, linearAsset.timeStamp,
              service.getLinkSource(roadlink), fromUpdate = true, Some(createdBy), Some(createdDateTime), linearAsset.modifiedBy, linearAsset.modifiedDateTime, linearAsset.verifiedBy, linearAsset.verifiedDate, geometry = service.getGeometry(roadlink))
          case _ =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure).roundMeasures(), AutoGeneratedUsername.generatedInUpdate, linearAsset.timeStamp,
              service.getLinkSource(roadlink), geometry = service.getGeometry(roadlink))
        }

      linearAsset.value match {
        case Some(NumericValue(intValue)) =>
          dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
        case Some(TextualValue(textValue)) =>
          dao.insertValue(id, LinearAssetTypes.getValuePropertyId(linearAsset.typeId), textValue)
        case _ => None
      }
    }
    if (toInsert.nonEmpty)
      logger.info(s"Added assets for linkids ${newLinearAssets.map(_.linkId)}")
  }

  protected def updateProjected(toUpdate: Seq[PersistedLinearAsset], persisted: Map[Long, Seq[PersistedLinearAsset]]): Unit = {
    def valueChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(_.value == assetToPersist.value)
    }

    toUpdate.foreach { linearAsset =>
      val persistedLinearAsset = persisted.getOrElse(linearAsset.id, Seq()).headOption
      val id = linearAsset.id
      if (valueChanged(linearAsset, persistedLinearAsset)) {
        linearAsset.value match {
          case Some(NumericValue(intValue)) =>
            dao.updateValue(id, intValue, LinearAssetTypes.numericValuePropertyId, AutoGeneratedUsername.generatedInUpdate)
          case Some(TextualValue(textValue)) =>
            dao.updateValue(id, textValue, LinearAssetTypes.getValuePropertyId(linearAsset.typeId), AutoGeneratedUsername.generatedInUpdate)
          case _ => None
        }
      }
    }
  }

  protected def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldAsset = service.getPersistedAssetsByIds(adjustment.typeId, Set(adjustment.assetId), newTransaction = false).headOption
      .getOrElse(throw new IllegalStateException(s"Old asset ${adjustment.assetId} of type ${adjustment.typeId} no longer available"))

    val roadLink = roadLinkService.getExistingOrExpiredRoadLinkByLinkId(oldAsset.linkId, newTransaction = false)
      .getOrElse(throw new IllegalStateException(s"Road link ${oldAsset.linkId} no longer available"))

    service.expireAsset(oldAsset.typeId, oldAsset.id, AutoGeneratedUsername.generatedInUpdate, expired = true, newTransaction = false)

    val oldAssetValue = oldAsset.value.getOrElse(throw new IllegalStateException(s"Value of the old asset ${oldAsset.id} of type ${oldAsset.typeId} is not available"))

    service.createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, oldAssetValue, adjustment.sideCode.value,
      Measures(oldAsset.startMeasure, oldAsset.endMeasure).roundMeasures(), AutoGeneratedUsername.generatedInUpdate, LinearAssetUtils.createTimeStamp(),
      Some(roadLink), true, oldAsset.createdBy, oldAsset.createdDateTime, oldAsset.modifiedBy, oldAsset.modifiedDateTime, oldAsset.verifiedBy, oldAsset.informationSource.map(_.value))

  }

  /**
   * Updates previous asset adjustments with expired ids of those assets that have fallen outside geometry after split.
   *
   * @param linksAndOperations sequence of RoadLink and related OperationStep pairs
   * @return Seq[LinkAndOperation] returns an updated copy of original parameter
   */
  private def updateSplitsWithExpiredIds(linksAndOperations: Seq[LinkAndOperation]): Seq[LinkAndOperation] = {
    def updateLinkAndOperationWithExpiredIds(linkAndOperation: LinkAndOperation, expiredIds: Set[Long]): LinkAndOperation = {
      val updatedOperation = updateSplitOperationWithExpiredIds(Some(linkAndOperation.operation),expiredIds).get
      val updatedLinkAndOperation = linkAndOperation.copy(operation = updatedOperation)
      updatedLinkAndOperation
    }

    linksAndOperations.map(linkAndOperation =>
      if (linkAndOperation.newLinkId.isEmpty)
        updateLinkAndOperationWithExpiredIds(linkAndOperation, getIdsForAssetsOutsideSplitGeometry(linksAndOperations))
      else
        linkAndOperation
    )
  }

  /**
   * Looks for assets that remain within split-deleted link
   *
   * @param linksAndOperations sequence of RoadLink and related OperationStep pairs that contain post-split information
   * @return Set[PersistedLinearAsset] returns the found assets
   */
  private def getAssetsOutsideSplitGeometry(linksAndOperations: Seq[LinkAndOperation]): Set[PersistedLinearAsset] = {
    val oldAssetsInsideGeometry = linksAndOperations.filter(_.newLinkId.nonEmpty).flatMap(_.operation.assetsAfter).toSet
    val oldAssetsOutsideGeometry = linksAndOperations
      .filter(_.newLinkId.isEmpty).flatMap(_.operation.assetsBefore)
      .filter(asset => !oldAssetsInsideGeometry.map(_.id).contains(asset.id))
    oldAssetsOutsideGeometry.toSet
  }

  /**
   * Looks for Ids of assets that remain within split-deleted link
   *
   * @param linksAndOperations sequence of RoadLink and related OperationStepSplit pairs that contain post-split information
   * @return Set[Long] returns the found assets' Ids
   */
  private def getIdsForAssetsOutsideSplitGeometry(linksAndOperations: Seq[LinkAndOperation]): Set[Long] = {
    getAssetsOutsideSplitGeometry(linksAndOperations)
      .map(oldAssetToExpire => oldAssetToExpire.id)
  }

  /**
   * Updates ChangeSet by adding expiredIds
   * @param changeSet ChangeSet to be updated
   * @param expiredIds set of Ids to be added to changeSet
   * @return updated ChangeSet
   */
  private def updateChangeSetWithExpiredIds(changeSet: Option[ChangeSet], expiredIds: Set[Long]): Option[ChangeSet] = {
    changeSet match {
      case Some(info) =>
        val copiedInfo = Some(info.copy(expiredAssetIds = info.expiredAssetIds ++ expiredIds))
        copiedInfo
      case None => None
    }
  }

  /**
   * Updates an OperationStepSplit with expired Ids of those assets that have fallen outside geometry after split.
   *
   * @param operationStep post-split information of assets
   * @param expiredIds set of expired asset Ids
   * @return Option[OperationStep] returns the updated OperationStep
   */
  private def updateSplitOperationWithExpiredIds(operationStep: Option[ OperationStepSplit], expiredIds: Set[Long]): Option[OperationStepSplit] = {
    operationStep match {
      case Some(_) =>
        val updatedChangeSet = updateChangeSetWithExpiredIds(operationStep.get.changeInfo,expiredIds)
        Some(operationStep.get.copy(changeInfo = updatedChangeSet))
      case None => None
    }
  }

  /**
   * Reports change for each asset that has fallen outside of geometry after split-delete
   *
   * @param linksAndOperations sequence of RoadLink and related OperationStep pairs that contain post-split information
   * @param change Change case information
   * @return Set[PersistedLinearAsset] returns the reported assets, or no assets if no empty link was presented
   */
  private def reportAssetExpirationAfterSplit(linksAndOperations: Seq[LinkAndOperation], change: RoadLinkChange): Set[PersistedLinearAsset] = {
    val emptyLink = linksAndOperations.filter(linkAndOperation => linkAndOperation.newLinkId.isEmpty)
    if (emptyLink.nonEmpty) {
      val emptyLinkOperation = emptyLink.head.operation
      val assetsOutsideGeometry = getAssetsOutsideSplitGeometry(linksAndOperations)
      assetsOutsideGeometry.foreach(asset => Some(reportAssetChanges(Some(asset), None, Seq(change), toOperationStep(emptyLinkOperation), Some(ChangeTypeReport.Deletion),useGivenChange = true)))
      assetsOutsideGeometry
    } else
      Set.empty[PersistedLinearAsset]

  }
}
