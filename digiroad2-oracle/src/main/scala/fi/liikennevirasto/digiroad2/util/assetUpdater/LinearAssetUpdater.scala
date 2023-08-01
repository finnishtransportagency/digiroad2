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

sealed case class OperationStep(assetsAfter: Seq[PersistedLinearAsset] = Seq(), changeInfo: Option[ChangeSet] = None,
                                roadLinkChange: Option[RoadLinkChange] = None, newLinkId: String = "", 
                                assetsBefore: Seq[PersistedLinearAsset] = Seq())

sealed case class Pair(oldAsset: Option[PersistedLinearAsset], newAsset: Option[PersistedLinearAsset])

sealed case class PairAsset(oldAsset: Option[Asset], newAsset: Option[Asset], changeType: Option[ChangeType] = None)

sealed case class LinkAndOperation(newLinkId: String, operation: OperationStep)

/**
  * override  
  * [[filterChanges]],
  * [[operationForNewLink]], <br>
  * [[additionalRemoveOperation]],  <br>
  * [[additionalRemoveOperationMass]], <br> 
  * [[nonAssetUpdate]] ,<br>
  * [[additionalUpdateOrChangeReplace]] and [[additionalUpdateOrChangeSplit]] <br>
  * with your needed additional logic
  *
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

  private val emptyStep: OperationStep = OperationStep(Seq(), None, None, "", Seq())

  // Mark generated part to be removed. Used when removing pavement in PaveRoadUpdater
  protected val removePart: Int = -1

  def resetReport(): Unit = {
    changesForReport.clear
  }
  def getReport(): mutable.Seq[ChangedAsset] = {
    changesForReport.distinct
  }

  val logger: Logger = LoggerFactory.getLogger(getClass)

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
  private def fallInWhenSlicing(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
    asset.linkId == replaceInfo.oldLinkId && asset.endMeasure >= replaceInfo.oldToMValue && asset.startMeasure >= replaceInfo.oldFromMValue
  }

  private def fallInReplaceInfoOld(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
    if (replaceInfo.digitizationChange && replaceInfo.oldFromMValue > replaceInfo.oldToMValue) {
      replaceInfo.oldLinkId == asset.linkId && replaceInfo.oldFromMValue >= asset.startMeasure && replaceInfo.oldToMValue <= asset.endMeasure
    } else {
      replaceInfo.oldLinkId == asset.linkId && replaceInfo.oldFromMValue <= asset.startMeasure && replaceInfo.oldToMValue >= asset.endMeasure
    }
  }
  
  private def toRoadLinkForFillTopology(roadLink: RoadLink): RoadLinkForFillTopology = {
    RoadLinkForFillTopology(linkId = roadLink.linkId, length = roadLink.length, 
      trafficDirection = roadLink.trafficDirection, administrativeClass = roadLink.administrativeClass,
      linkSource = roadLink.linkSource, linkType = roadLink.linkType, 
      constructionType = roadLink.constructionType, 
      geometry = roadLink.geometry, municipalityCode = roadLink.municipalityCode)
  }
  
  private def convertToPersisted(asset: PieceWiseLinearAsset): PersistedLinearAsset = {
    PersistedLinearAsset(asset.id, asset.linkId, asset.sideCode.value,
      asset.value, asset.startMeasure, asset.endMeasure, asset.createdBy,
      asset.createdDateTime, asset.modifiedBy, asset.modifiedDateTime, asset.expired, asset.typeId, asset.timeStamp,
      asset.geomModifiedDate, asset.linkSource, asset.verifiedBy, asset.verifiedDate, asset.informationSource, asset.oldId)
  }

  /**
    * Merge assetsBefore, assetsAfter and changeInfo from two given [[OperationStep]]
    * @param a
    * @param b
    * @return [[Some[OperationStep]]]
    */
  private def mergerOperations(a: Option[OperationStep], b: Option[OperationStep]): Some[OperationStep] = {
    val (aBefore, newLinkIdA, assetsA, changeInfoA, roadLinkChangeA) = (a.get.assetsBefore, a.get.newLinkId, a.get.assetsAfter, a.get.changeInfo, a.get.roadLinkChange)
    val (bBefore, newLinkIdB, assetsB, changeInfoB, roadLinkChangeB) = (b.get.assetsBefore, b.get.newLinkId, b.get.assetsAfter, b.get.changeInfo, b.get.roadLinkChange)
    val roadLinkChange = if (roadLinkChangeA.isEmpty) roadLinkChangeB else roadLinkChangeA
    val newLinkId = if (newLinkIdA.isEmpty) newLinkIdB else newLinkIdA
    Some(OperationStep((assetsA ++ assetsB).distinct, Some(LinearAssetFiller.combineChangeSets(changeInfoA.get, changeInfoB.get)), roadLinkChange, newLinkId, (aBefore ++ bBefore).distinct))
  }
  /**
    * Merge assetsBefore, assetsAfter and changeInfo from two given [[OperationStep]]. Drop RoadLinkChange
    *
    * @param a
    * @param b
    * @return [[Some[OperationStep]]]
    */
  private def mergerOperationsDropLinksChange(a: Option[OperationStep], b: Option[OperationStep]): Some[OperationStep] = {
    val (assetsA, changeInfoA) = (a.get.assetsAfter, a.get.changeInfo)
    val (assetsB, changeInfoB) = (b.get.assetsAfter, b.get.changeInfo)
    Some(OperationStep((assetsA ++ assetsB).distinct, Some(LinearAssetFiller.combineChangeSets(changeInfoA.get, changeInfoB.get))))
  }

  def reportAssetChanges(oldAsset: Option[PersistedLinearAsset], newAsset: Option[PersistedLinearAsset],
                         roadLinkChanges: Seq[RoadLinkChange],
                         operationSteps: OperationStep, rowType: ChangeType, useGivenChange: Boolean = true): OperationStep = {

    def propertyChange: Boolean = {
      oldAsset.isDefined && newAsset.isDefined &&
        !oldAsset.get.value.get.equals(newAsset.get.value.get)
    }
    
    if (oldAsset.isEmpty && newAsset.isEmpty) return operationSteps

    val linkId = if (oldAsset.nonEmpty) oldAsset.get.linkId else newAsset.head.linkId
    val assetId = if (oldAsset.nonEmpty) oldAsset.get.id else 0

    val relevantRoadLinkChange = if (useGivenChange) {
      roadLinkChanges.head
    } else roadLinkChanges.find(_.oldLink.get.linkId == oldAsset.get.linkId).get

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
      changesForReport.append(ChangedAsset(linkId, assetId, rowType, 
        relevantRoadLinkChange.changeType, before, after.toSeq))
    }
    operationSteps
  }

  private def reportingReplacement(initStep: OperationStep, assetsInNewLink: LinkAndOperation): Some[OperationStep] = {
    val pairs = assetsInNewLink.operation.assetsAfter.flatMap(asset => createPair(Some(asset), assetsInNewLink.operation.assetsBefore)).distinct
    val report = pairs.filter(_.newAsset.isDefined).map(pair => {
      if (!assetsInNewLink.operation.changeInfo.get.expiredAssetIds.contains(pair.newAsset.get.id)) {
        Some(reportAssetChanges(pair.oldAsset, pair.newAsset, Seq(assetsInNewLink.operation.roadLinkChange.get), assetsInNewLink.operation, ChangeTypeReport.Replaced))
      } else Some(assetsInNewLink.operation)
    }).foldLeft(Some(initStep))(mergerOperations)
    report
  }

  private def reportingSplit(initStep: OperationStep, assetsInNewLink: LinkAndOperation, change: RoadLinkChange): Some[OperationStep] = {
    val pairs = assetsInNewLink.operation.assetsAfter.flatMap(asset => {
      val info = change.replaceInfo.find(_.newLinkId.get == asset.linkId).get
      createPair(Some(asset), assetsInNewLink.operation.assetsBefore.filter(_.linkId == info.oldLinkId))
    }).distinct
    val report = pairs.filter(_.newAsset.isDefined).map(pair => {
      if (!assetsInNewLink.operation.changeInfo.get.expiredAssetIds.contains(pair.newAsset.get.id)) {
        Some(reportAssetChanges(pair.oldAsset, pair.newAsset, Seq(change), assetsInNewLink.operation, ChangeTypeReport.Divided))
      } else Some(assetsInNewLink.operation)
    }).foldLeft(Some(initStep))(mergerOperations)
    report
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
  def additionalUpdateOrChangeReplace(operationStep: OperationStep): Option[OperationStep] = None
  def additionalUpdateOrChangeSplit(operationStep: OperationStep): Option[OperationStep] = None
  def nonAssetUpdate(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = None


  /**
    * Run additional operation if [[additionalUpdateOrChangeSplit]] or [[additionalUpdateOrChangeReplace]] has been override
    * @param operated already operated assets
    * @param operation function which will be run
    * @return
    */
  private def additionalOperation(operated: LinkAndOperation, operation: OperationStep => Option[OperationStep]): OperationStep = {
    if (operated.operation.assetsAfter.nonEmpty) {
      val additionalChange2 = operation(operated.operation)
      if (additionalChange2.nonEmpty) additionalChange2.get else operated.operation
    } else operated.operation
  }
  
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
    val newLinks = changes.filter(isNew).flatMap(_.newLinks).size
    // here we assume that RoadLinkProperties updater has already remove override if KMTK version traffic direction is same.
    // still valid overrided has also been samuuted
    val newLinkIds = changes.flatMap(_.newLinks.map(_.linkId))
    val newRoadLinks = roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(newLinkIds.toSet)
    
    val existingAssets = service.fetchExistingAssetsByLinksIdsString(typeId, oldIds.toSet, deletedLinks.toSet, newTransaction = false)
    val initChangeSet = LinearAssetFiller.initWithExpiredIn(existingAssets, deletedLinks)
    
    logger.info(s"Processing assets: ${typeId}, assets count: ${existingAssets.size}, number of changes in the sets: ${changes.size}")
    logger.info(s"Deleted links count: ${deletedLinks}, new links count: ${newLinks}")
    val (projectedAssets, changedSet) = LogUtils.time(logger, s"Samuuting logic finished: ") {
      fillNewRoadLinksWithPreviousAssetsData(typeId, newRoadLinks, existingAssets, changes, initChangeSet)
    }
    
    additionalRemoveOperationMass(deletedLinks)
    LogUtils.time(logger, s"Saving changeSets took: ") {
      updateChangeSet(changedSet)
    }
    LogUtils.time(logger, s"Saving generated or updated took: ") {
      persistProjectedLinearAssets(projectedAssets.filterNot(_.id == removePart).filter(_.id == 0L))
    }
  }

  private def fillNewRoadLinksWithPreviousAssetsData(typeId: Int, links: Seq[RoadLink],
                                                       assetsAll: Seq[PersistedLinearAsset], changes: Seq[RoadLinkChange],
                                                       changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val initStep = OperationStep(Seq(), Some(changeSet))
    val adjustAllButReplacement = changes.map(goThroughChanges(typeId, links
      , assetsAll, changeSet, initStep, _))
      .filter(_.nonEmpty).filter(_.get.assetsAfter.nonEmpty)

    val (otherAdjustments, replace) = adjustAllButReplacement.partition(p => p.get.roadLinkChange.get.changeType != RoadLinkChangeType.Replace)
    val adjustments = adjustAndReportReplacement(typeId, links, replace, initStep)
    val mergingOperations = (otherAdjustments ++ adjustments.map(a => Some(a)).toSeq).foldLeft(Some(initStep))(mergerOperationsDropLinksChange)
    val OperationStep(assetsOperated, changeInfo, _, _, _) = mergingOperations.get

    changeInfo.get.expiredAssetIds.map(asset => {
      val alreadyReported = changesForReport.map(_.before).filter(_.nonEmpty).map(_.get.assetId)
      if (!alreadyReported.contains(asset)) {
        val expiringAsset = assetsAll.find(_.id == asset)
        reportAssetChanges(expiringAsset, None, changes.filterNot(isNew), emptyStep, ChangeTypeReport.Deletion, useGivenChange = false)
      }
    })

    (assetsOperated, changeInfo.get)
  }
  private def goThroughChanges(typeId: Int, links: Seq[RoadLink], assetsAll: Seq[PersistedLinearAsset],
                                changeSets: ChangeSet, initStep: OperationStep, change: RoadLinkChange): Option[OperationStep] = {
    nonAssetUpdate(change, Seq(), null)
    change.changeType match {
      case RoadLinkChangeType.Add =>
        val operation = operationForNewLink(change, assetsAll, changeSets).getOrElse(initStep).copy(roadLinkChange = Some(change))
        Some(reportAssetChanges(None, operation.assetsAfter.headOption, Seq(change), operation, ChangeTypeReport.Creation))
      case RoadLinkChangeType.Remove => additionalRemoveOperation(change, assetsAll, changeSets)
      case _ =>
        val assets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
        if (assets.nonEmpty) {
          change.changeType match {
            case RoadLinkChangeType.Replace =>
              assets.map(a => projecting(changeSets, change, a, a)).
                filter(_.nonEmpty).map(p => Some(p.get.copy(roadLinkChange = Some(change))))
                .foldLeft(Some(initStep))(mergerOperations)
            case RoadLinkChangeType.Split =>
              handleSplits(typeId, links
                , changeSets, initStep, change, assets)
            case _ => None
          }
        } else None
    }
  }
  private def adjustAndReportReplacement(typeId: Int, links: Seq[RoadLink],
                                         assetUnderReplace: Seq[Option[OperationStep]], initStep: OperationStep): Option[OperationStep] = {
    val groupByNewLink = assetUnderReplace.groupBy(a => a.get.newLinkId)
    val adjusted = adjustAndAdditionalOperations(typeId, links, groupByNewLink, additionalUpdateOrChangeReplace)
    adjusted.map(reportingReplacement(initStep, _)).foldLeft(Some(initStep))(mergerOperations)
  }

  private def adjustAndAdditionalOperations(typeId: Int, links: Seq[RoadLink],
                                            assets: Map[String, Seq[Option[OperationStep]]],
                                            operation: OperationStep => Option[OperationStep]) = {
    val mergeAndAdjust = adjust(typeId, links, assets)
    val additionalSteps = mergeAndAdjust.map(a => {
      val additionalChanges = additionalOperation(a, operation)
      LinkAndOperation(a.newLinkId, additionalChanges)
    })
    additionalSteps
  }
  /**
    * Merge all grouped assets and adjust
    * @param typeId assets type
    * @param links road link
    * @param assets assets grouped new link id 
    * @return
    */
  private def adjust(typeId: Int, links: Seq[RoadLink], assets: Map[String, Seq[Option[OperationStep]]]): Seq[LinkAndOperation] = {
    assets.map(linkAndAssets => 
      LinkAndOperation(linkAndAssets._1, linkAndAssets._2.foldLeft(Some(OperationStep(Seq(), Some(LinearAssetFiller.emptyChangeSet))))(mergerOperations).get)
    ).map(a => LinkAndOperation(a.newLinkId, adjustAssets(typeId, links, a.operation))).toSeq
  }
  
  private def adjustAssets(typeId: Int, links: Seq[RoadLink], operationStep: OperationStep): OperationStep = {
    val OperationStep(assetsAfter,changeSetFromOperation,roadLinkChange,newLinkId,assetsBefore) = operationStep
    val assetsOperated = assetsAfter.filterNot(a => changeSetFromOperation.get.expiredAssetIds.contains(a.id))
    val convert = links.map(toRoadLinkForFillTopology)
    val groupedAssets = assetFiller.toLinearAssetsOnMultipleLinks(assetsOperated, convert).groupBy(_.linkId)
    val (adjusted, changeSet) = adjustLinearAssetsOnChangesGeometry(convert, groupedAssets, typeId, changeSetFromOperation)
    OperationStep(adjusted.toSet.map(convertToPersisted).toSeq, Some(changeSet), roadLinkChange, newLinkId, assetsBefore)
  }

  protected def adjustLinearAssetsOnChangesGeometry(roadLinks: Seq[RoadLinkForFillTopology], assets: Map[String, Seq[PieceWiseLinearAsset]],
                                                    typeId: Int, changeSet: Option[ChangeSet] = None): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    assetFiller.fillTopologyChangesGeometry(roadLinks, assets, typeId, changeSet)
  }

  private def handleSplits(typeId: Int, links: Seq[RoadLink], changeSet: ChangeSet,
                           initStep: OperationStep, change: RoadLinkChange, assets: Seq[PersistedLinearAsset]): Option[OperationStep] = {
    val groupByNewLink = operationForSplit(change, assets, changeSet).map(a => Some(a)).groupBy(a => a.get.newLinkId)
    val adjusted = adjustAndAdditionalOperations(typeId, links, groupByNewLink, additionalUpdateOrChangeSplit)
    val updatedAdjustments = updateSplitAdjustmentsWithExpiredIds(adjusted)
    val operationStep = updatedAdjustments.map(reportingSplit(initStep, _, change)).foldLeft(Some(initStep))(mergerOperations)
    val updatedOperationStep = updateOperationStepWithExpiredIds(operationStep, getIdsForAssetsOutsideSplitGeometry(updatedAdjustments))
    reportAssetExpirationAfterSplit(updatedAdjustments,change)
    updatedOperationStep
  }

  private def operationForSplit(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSet: ChangeSet): Seq[OperationStep] = {
    val relevantAssets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
    relevantAssets.flatMap(sliceFrom => {
      val sliced = slicer(Seq(sliceFrom), Seq(), change)
      sliced.map(part => projecting(changeSet, change, part, sliceFrom))
    }).map(p => p.get.copy(roadLinkChange = Some(change)))
  }

  @tailrec
  private def slicer(assets: Seq[PersistedLinearAsset], fitIntRoadLinkPrevious: Seq[PersistedLinearAsset], change: RoadLinkChange): Seq[PersistedLinearAsset] = {
    def slice(change: RoadLinkChange, asset: PersistedLinearAsset): Seq[PersistedLinearAsset] = {
      val selectInfo = sortAndFind(change, asset, fallInWhenSlicing).get
      
      val shorted = asset.copy(endMeasure = selectInfo.oldToMValue)
      val newPart = asset.copy(id = 0, startMeasure = selectInfo.oldToMValue, oldId = asset.id)
      
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
        asset.endMeasure <= selectInfo.oldToMValue && asset.startMeasure >= selectInfo.oldFromMValue
      }

      val selectInfoOpt = sortAndFind(change, asset, fallInReplaceInfoOld)
      if (selectInfoOpt.nonEmpty) assetIsAlreadySlicedOrFitIn(asset, selectInfoOpt.get)
      else false
    }

    val sliced = assets.flatMap(slice(change, _))
    val (fitIntoRoadLink, assetGoOver) = sliced.partition(partitioner(_, change))
    if (assetGoOver.nonEmpty) slicer(assetGoOver, fitIntoRoadLink ++ fitIntRoadLinkPrevious, change) 
    else fitIntRoadLinkPrevious ++ fitIntoRoadLink
  }

  /**
    * @param changeSets
    * @param change
    * @param asset asset which will be projected
    * @param beforeAsset asset before projection
    * @return
    */
  private def projecting(changeSets: ChangeSet, change: RoadLinkChange, asset: PersistedLinearAsset, beforeAsset: PersistedLinearAsset) = {
    val info = sortAndFind(change, asset, fallInReplaceInfoOld).getOrElse(throw new Exception("Did not found replace info for asset"))
    val newId = info.newLinkId.getOrElse("")
    val maybeLink = change.newLinks.find(_.linkId == newId)
    val maybeLinkLength = if (maybeLink.nonEmpty) maybeLink.get.linkLength else 0

    val (projected, changeSet) = projectLinearAsset(asset.copy(linkId = newId),
      Projection(
        info.oldFromMValue, info.oldToMValue,
        info.newFromMValue.getOrElse(0), info.newToMValue.getOrElse(0),
        LinearAssetUtils.createTimeStamp(),
        newId, maybeLinkLength),
      changeSets, info.digitizationChange)
    Some(OperationStep(Seq(projected), Some(changeSet), newLinkId = newId, assetsBefore = Seq(beforeAsset)))
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
      createdBy = asset.createdBy, createdDateTime = asset.createdDateTime, modifiedBy = Some(AutoGeneratedUsername.generatedInUpdate),
      modifiedDateTime = asset.modifiedDateTime, expired = false, typeId = asset.typeId,
      timeStamp = projection.timeStamp, geomModifiedDate = None, linkSource = asset.linkSource, verifiedBy = asset.verifiedBy, verifiedDate = asset.verifiedDate,
      informationSource = asset.informationSource, asset.oldId), changeSet)
  }

  def updateChangeSet(changeSet: ChangeSet): Unit = {

    if (changeSet.adjustedMValues.nonEmpty)
      logger.info(s"Saving adjustments for asset/link ids=${changeSet.adjustedMValues.map(a => s"${a.assetId}/${a.linkId}").mkString(", ")}")
      logger.debug(s"Saving adjustments for asset/link ids=${changeSet.adjustedMValues.map(a => s"${a.assetId}/${a.linkId} start measure: ${a.startMeasure} end measure: ${a.endMeasure}").mkString(", ")}")
    changeSet.adjustedMValues.foreach { adjustment =>
      dao.updateMValuesChangeInfo(adjustment.assetId, adjustment.linkId, Measures(adjustment.startMeasure, adjustment.endMeasure).roundMeasures(), adjustment.timeStamp, AutoGeneratedUsername.generatedInUpdate)
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
              service.getLinkSource(roadlink), fromUpdate = true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate, geometry = service.getGeometry(roadlink))
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
      Some(roadLink), false, Some(AutoGeneratedUsername.generatedInUpdate), None, oldAsset.verifiedBy, oldAsset.informationSource.map(_.value))

  }

  /**
   * Updates previous asset adjustments with expired ids of those assets that have fallen outside geometry after split.
   *
   * @param linksAndOperations sequence of RoadLink and related OperationStep pairs
   * @return Seq[LinkAndOperation] returns an updated copy of original parameter
   */
  private def updateSplitAdjustmentsWithExpiredIds(linksAndOperations: Seq[LinkAndOperation]): Seq[LinkAndOperation] = {
    def updateLinkAndOperationIfEmptyNewLinkId(linkAndOperation: LinkAndOperation, expiredIds: Set[Long]): LinkAndOperation = {
      if (linkAndOperation.newLinkId.isEmpty) {
        val updatedChangeSet = updateChangeSetWithExpiredIds(linkAndOperation.operation.changeInfo,expiredIds)
        val updatedOperation = OperationStep(linkAndOperation.operation.assetsAfter, updatedChangeSet, linkAndOperation.operation.roadLinkChange,linkAndOperation.operation.newLinkId,linkAndOperation.operation.assetsBefore)
        val updatedLinkAndOperation = LinkAndOperation(linkAndOperation.newLinkId,updatedOperation)
        updatedLinkAndOperation
      } else {
        linkAndOperation
      }
    }

    linksAndOperations.map(linkAndOperation =>
      updateLinkAndOperationIfEmptyNewLinkId(linkAndOperation, getIdsForAssetsOutsideSplitGeometry(linksAndOperations)))
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
      .filter(asset => !oldAssetsInsideGeometry.map(_.id).contains(asset.id)).toSet
    oldAssetsOutsideGeometry
  }

  /**
   * Looks for Ids of assets that remain within split-deleted link
   *
   * @param linksAndOperations sequence of RoadLink and related OperationStep pairs that contain post-split information
   * @return Set[Option[Long]] returns the found assets' Ids
   */
  private def getIdsForAssetsOutsideSplitGeometry(linksAndOperations: Seq[LinkAndOperation]): Set[Long] = {
    getAssetsOutsideSplitGeometry(linksAndOperations)
      .map(oldAssetToExpire => oldAssetToExpire.id).toSet
  }

  /**
   * Updates ChangeSet by adding expiredIds
   * @param changeSet ChangeSet to be updated
   * @param expiredIds set of Ids to be added to changeSet
   * @return updated ChangeSet
   */
  private def updateChangeSetWithExpiredIds(changeSet: Option[ChangeSet], expiredIds: Set[Long]): Option[ChangeSet] = {
    val updatedChangeSet = changeSet match {
      case Some(info) =>
        val copiedInfo = Some(info.copy(expiredAssetIds = info.expiredAssetIds ++ expiredIds))
        copiedInfo
      case None => None
    }
    updatedChangeSet
  }

  /**
   * Updates an OperationStep with expired Ids of those assets that have fallen outside geometry after split.
   *
   * @param operationStep post-split information of assets
   * @param expiredIds set of expired asset Ids
   * @return Option[OperationStep] returns the updated OperationStep
   */
  private def updateOperationStepWithExpiredIds(operationStep: Option[OperationStep], expiredIds: Set[Long]): Option[OperationStep] = {
    if (operationStep.isEmpty) return operationStep
    val updatedChangeSet = updateChangeSetWithExpiredIds(operationStep.get.changeInfo,expiredIds)
    Some(operationStep.get.copy(changeInfo = updatedChangeSet))
  }

  /**
   * Reports change for each asset that has fallen outside of geometry after split-delete
   *
   * @param linksAndOperations sequence of RoadLink and related OperationStep pairs that contain post-split information
   * @param change Change case information
   * @return Set[PersistedLinearAsset] returns the reported assets
   */
  private def reportAssetExpirationAfterSplit(linksAndOperations: Seq[LinkAndOperation], change: RoadLinkChange): Set[PersistedLinearAsset] = {
    val emptyLink = linksAndOperations.filter(linkAndOperation => linkAndOperation.newLinkId.isEmpty)
    val emptyLinkOperation = if (emptyLink.nonEmpty) emptyLink.head.operation else return Set.empty[PersistedLinearAsset]
    val assetsOutsideGeometry = getAssetsOutsideSplitGeometry(linksAndOperations)
    assetsOutsideGeometry.map(asset => Some(reportAssetChanges(Some(asset), None, Seq(change), emptyLinkOperation, ChangeTypeReport.Deletion)))
    assetsOutsideGeometry
  }
}
