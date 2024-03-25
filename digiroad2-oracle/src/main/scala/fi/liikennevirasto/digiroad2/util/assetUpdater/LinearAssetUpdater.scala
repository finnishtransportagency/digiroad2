package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Add, Remove}
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.dao.linearasset.{MValueUpdate, PostGISLinearAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetOperations, LinearAssetTypes, Measures, NewLinearAssetMassOperation}
import fi.liikennevirasto.digiroad2.util.CustomIterableOperations.IterableOperation
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinearAssetUtils, LogUtils, Parallel}
import org.joda.time.DateTime
import org.json4s.jackson.compactJson
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable.ListBuffer
import scala.collection.{GenIterable, Seq, mutable}
import scala.util.{Failure, Success, Try}

/**
  *  This act as state object. Keep record of samuutus process.
  * @param assetsAfter after samuutus record
  * @param changeInfo ChangeSet for saving [[LinearAssetUpdater.updateChangeSet]]
  * @param assetsBefore before samuutus record
  */
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

case class FailedToFindReplaceInfo(msg:String) extends  NoSuchElementException(msg)

/**
  * System use [[OperationStep]] class as state object when doing samuutus process. Each operation step or phase update [[OperationStep]] or gives [[OperationStep]] as return object.
  * This way we can add more steps easily as needed.
  * Samuutus logic is :
  * <br> 1) fetch changes
  * <br> 2) [[filterChanges]] Apply only needed changes to assets by writing your own filtering logic.
  * <br> 3) [[additionalRemoveOperationMass]] Mass operation based on list of removed links.
  * <br> 4) Start projecting everything into new links based on replace info.
  * <br> 4.1) [[nonAssetUpdate]] Add additional logic if something more also need updating like some other table.
  * <br> 4.2) [[operationForNewLink]] Add logic to create assets when link is created.
  * <br> 4.3) [[additionalRemoveOperation]] Add additional operation based on removed link when something more also need updating like some other table.
  * <br> 5) All assets is projected into new links.
  * <br> 6) Run fillTopology to adjust assets based on link length and other assets on link.
  * <br> 7) [[adjustLinearAssets]] Override if asset need totally different fillTopology implementation.
  * <br> 8) [[additionalOperations]]  Additional logic after projecting everything in right place.
  * <br> 9) Start creating report row.
  * <br> 10) Save all projected.
  * <br> 11) Generate report.
  * Generally add additional logic only by overriding [[filterChanges]]
  * [[nonAssetUpdate]], [[operationForNewLink]] or [[additionalOperations]].
  * [[additionalOperations]] is used to add additional operation based on new position of links. For example based on roadclass changes.
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

  private val changesForReport: ConcurrentLinkedQueue[ChangedAsset] = new ConcurrentLinkedQueue[ChangedAsset]()

  private val emptyStep: OperationStep = OperationStep(Seq(), None, Seq())
  val groupSizeForParallelRun = 1500
  val parallelizationThreshold = 20000
  val maximumParallelismLevel = 30

  // Mark generated part to be removed. Used when removing pavement in PaveRoadUpdater
  protected val removePart: Int = -1

  def resetReport(): Unit = {
    changesForReport.clear()
  }

  def getReport(): Seq[ChangedAsset] = {
    LogUtils.time(logger, "Get and filter out duplicate changes.") {
      changesForReport.asScala.toList.distinct
    }
  }

  val logger: Logger = LoggerFactory.getLogger(getClass)

  private def setParallelismLevel(numberOfSets: Int) = {
    val totalTasks = numberOfSets
    val level = if (totalTasks < maximumParallelismLevel) totalTasks else maximumParallelismLevel
    logger.info(s"Asset groups: $totalTasks, parallelism level used: $level")
    (totalTasks, level)
  }

  def logChangeSetSizes(changeSet: ChangeSet): Unit = {
    logger.info(s"adjustedMValues size: ${changeSet.adjustedMValues.size}")
    logger.info(s"adjustedSideCodes size: ${changeSet.adjustedSideCodes.size}")
    logger.info(s"expiredAssetIds size: ${changeSet.expiredAssetIds.size}")
    logger.info(s"valueAdjustments size: ${changeSet.valueAdjustments.size}")
    logger.info(s"droppedAssetIds size: ${changeSet.droppedAssetIds.size}")
    
    changeSet.adjustedMValues.groupBy(_.assetId).filter(_._2.size>=2).foreach(a1=> {
      logger.error(s"More than one M-Value adjustment for asset ids=${a1._2.sortBy(_.linkId).map(a => s"${a.assetId}/${a.linkId} start measure: ${a.startMeasure} end measure: ${a.endMeasure}").mkString(", ")}")
    })

    changeSet.adjustedSideCodes.groupBy(_.assetId).filter(_._2.size >= 2).foreach(a1 => {
      logger.error(s"More than one sideCode change for asset/sidecode ids=${a1._2.map(a => s"${a.assetId}/${a.sideCode}").mkString(", ")}")
    })
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
      /** if asset is longer than replaceInfo.oldToMValue, it need to be split in [[LinearAssetUpdater.slicer]] */
      def roundMeasures(value: Double): Double = {
        val exponentOfTen = Math.pow(10, 3)
        Math.round(value * exponentOfTen).toDouble / exponentOfTen
      }
      val difference = roundMeasures(Math.abs(replaceInfo.oldToMValue.getOrElse(0.0)-asset.endMeasure))
      // See [AssetFiller.MaxAllowedMValueError]
      val inTolerance = difference <= 0.1
      val endMeasureIsGreaterThanOldToValue = replaceInfo.oldToMValue.getOrElse(0.0) >= asset.endMeasure || inTolerance
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
  private def mergerOperationsSplit(a: OperationStepSplit, b: OperationStepSplit): OperationStepSplit = {
    val (aBefore, newLinkIdA, assetsA, changeInfoA) = (a.assetsBefore, a.newLinkId, a.assetsAfter, a.changeInfo)
    val (bBefore, newLinkIdB, assetsB, changeInfoB) = (b.assetsBefore, b.newLinkId, b.assetsAfter, b.changeInfo)
    val newLinkId = if (newLinkIdA.isEmpty) newLinkIdB else newLinkIdA
    OperationStepSplit((assetsA ++ assetsB).distinct, Some(LinearAssetFiller.combineChangeSets(changeInfoA.get, changeInfoB.get)), newLinkId, (aBefore ++ bBefore).distinct)
  }

  /**
    * Merge assetsBefore, assetsAfter and changeInfo from given list of [[OperationStep]]
    * @param assetsUnderReplace List of [[OperationStep]] objects containing changes to assets under replacement
    * @return [[OperationStep]] containing merged info
    */
  private def mergerOperations(a: Option[OperationStep], b: Option[OperationStep]): Some[OperationStep] = {
    val (aBefore, assetsA, changeInfoA) = (a.get.assetsBefore, a.get.assetsAfter, a.get.changeInfo)
    val (bBefore, assetsB, changeInfoB) = (b.get.assetsBefore, b.get.assetsAfter, b.get.changeInfo)
    Some(OperationStep((assetsA ++ assetsB).distinct, Some(LinearAssetFiller.combineChangeSets(changeInfoA.get, changeInfoB.get)), (aBefore ++ bBefore).distinct))
  }
  

  protected def mergeAfterAndChangeSets(assetsUnderReplace: Seq[Option[OperationStep]]): (ListBuffer[PersistedLinearAsset], Some[ChangeSet]) = {
    val after = new ListBuffer[PersistedLinearAsset]
    val droppedAssetIds = new ListBuffer[Long]
    val adjustedMValues = new ListBuffer[MValueAdjustment]
    val adjustedSideCodes = new ListBuffer[SideCodeAdjustment]
    val expiredAssetIds = new ListBuffer[Long]
    val valueAdjustments = new ListBuffer[ValueAdjustment]
    for (a <- assetsUnderReplace) {
      if (a.nonEmpty) {
        after.appendAll(a.get.assetsAfter)
        if (a.get.changeInfo.nonEmpty) {
          droppedAssetIds.appendAll(a.get.changeInfo.get.droppedAssetIds)
          adjustedMValues.appendAll(a.get.changeInfo.get.adjustedMValues.toList)
          adjustedSideCodes.appendAll(a.get.changeInfo.get.adjustedSideCodes.toList)
          expiredAssetIds.appendAll(a.get.changeInfo.get.expiredAssetIds)
          valueAdjustments.appendAll(a.get.changeInfo.get.valueAdjustments.toList)
        }
      }
    }

    val changeSet = ChangeSet(droppedAssetIds = droppedAssetIds.toSet,
      adjustedMValues = adjustedMValues.distinct, adjustedSideCodes = adjustedSideCodes.distinct,
      expiredAssetIds = expiredAssetIds.toSet, valueAdjustments = valueAdjustments.distinct)

    (after, Some(changeSet))
  }

  def reportAssetChanges(oldAsset: Option[PersistedLinearAsset], newAsset: Option[PersistedLinearAsset],
                         roadLinkChanges: Seq[RoadLinkChange],
                         passThroughStep: OperationStep, rowType: Option[ChangeType] = None, useGivenChange: Boolean = false): OperationStep = {

    def propertyChange: Boolean = {
      val assetInherit = oldAsset.isDefined && newAsset.isDefined && oldAsset.get.id == newAsset.get.oldId
      assetInherit && !oldAsset.get.value.get.equals(newAsset.get.value.get)
    }

    def findRelevantRoadLinkChange(tryToMatchNewAndOldLinkId: Boolean): Option[RoadLinkChange] = {
      roadLinkChanges.find(change => {
        val roadLinkChangeOldLinkId = change.oldLink match {
          case Some(oldLink) => Some(oldLink.linkId)
          case None => None
        }
        val assetOldLinkId = oldAsset match {
          case Some(asset) => Some(asset.linkId)
          case None => None
        }
        val roadLinkChangeNewLinkIds = change.newLinks.map(_.linkId)

        val checkByOldAsset = (roadLinkChangeOldLinkId.nonEmpty && assetOldLinkId.nonEmpty) && roadLinkChangeOldLinkId == assetOldLinkId

        if(tryToMatchNewAndOldLinkId){
          if(newAsset.isDefined){
            checkByOldAsset && roadLinkChangeNewLinkIds.contains(newAsset.get.linkId)
          }
          else checkByOldAsset
        }
        else if (newAsset.isDefined) checkByOldAsset || roadLinkChangeNewLinkIds.contains(newAsset.get.linkId)
        else checkByOldAsset

      })
    }

    if (oldAsset.isEmpty && newAsset.isEmpty) return passThroughStep

    val linkId = if (oldAsset.nonEmpty) oldAsset.get.linkId else newAsset.head.linkId
    val assetId = if (oldAsset.nonEmpty) oldAsset.get.id else 0

    val relevantRoadLinkChange = if (useGivenChange) {
      roadLinkChanges.head
    } else {
      val fullMatch = findRelevantRoadLinkChange(tryToMatchNewAndOldLinkId = true)
      val result = if(fullMatch.isEmpty) findRelevantRoadLinkChange(tryToMatchNewAndOldLinkId = false)
      else fullMatch
      result.getOrElse({
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
    }

    val before = oldAsset match {
      case Some(ol) =>
        val values = compactJson(ol.toJson)
        val linkOld = relevantRoadLinkChange.oldLink.get
        val linkInfo = Some(LinkInfo(linkOld.lifeCycleStatus))
        val assetGeometry = GeometryUtils.truncateGeometry3D(linkOld.geometry, ol.startMeasure, ol.endMeasure)
        val measures = Measures(ol.startMeasure, ol.endMeasure).roundMeasures()
        val linearReference = LinearReferenceForReport(ol.linkId, measures.startMeasure, Some(measures.endMeasure), Some(ol.sideCode), None, None, measures.length())
        Some(Asset(ol.id, values, Some(linkOld.municipality.getOrElse(throw new NoSuchElementException(s"${linkOld.linkId} does not have municipality code"))), Some(assetGeometry), Some(linearReference),linkInfo))
      case None =>
        val linkOld = relevantRoadLinkChange.oldLink
        if (linkOld.nonEmpty) {
          val linkInfo = Some(LinkInfo(linkOld.get.lifeCycleStatus))
          val linearReference = LinearReferenceForReport(linkOld.get.linkId, 0, None, None, None, None, 0)
          Some(Asset(0, "", Some(linkOld.get.municipality.getOrElse(throw new NoSuchElementException(s"${linkOld.get.linkId} does not have municipality code"))), None, Some(linearReference),linkInfo))
        } else None
    }

    val after = newAsset.map(asset => {
      val newLink = relevantRoadLinkChange.newLinks.find(_.linkId == asset.linkId).get
      val linkInfo = Some(LinkInfo(newLink.lifeCycleStatus))
      val values = compactJson(asset.toJson)
      val assetGeometry = GeometryUtils.truncateGeometry3D(newLink.geometry, asset.startMeasure, asset.endMeasure)
      val measures = Measures(asset.startMeasure, asset.endMeasure).roundMeasures()
      val linearReference = LinearReferenceForReport(asset.linkId, measures.startMeasure, Some(measures.endMeasure), Some(asset.sideCode), None, None, measures.length())
      Asset(asset.id, values, Some(newLink.municipality.getOrElse(throw new NoSuchElementException(s"${newLink.linkId} does not have municipality code"))), Some(assetGeometry), Some(linearReference),linkInfo)
    })

    if (propertyChange) {
      changesForReport.add(ChangedAsset(linkId, assetId, ChangeTypeReport.PropertyChange,
        relevantRoadLinkChange.changeType, before, after.toSeq))
    } else {
      if (rowType.isDefined) {
        changesForReport.add(ChangedAsset(linkId, assetId, rowType.get,
          relevantRoadLinkChange.changeType, before, after.toSeq))
      }else {
        if (relevantRoadLinkChange.changeType == RoadLinkChangeType.Split) {
          changesForReport.add(ChangedAsset(linkId, assetId, ChangeTypeReport.Divided,
            relevantRoadLinkChange.changeType, before, after.toSeq))
        } else {
          changesForReport.add(ChangedAsset(linkId, assetId, ChangeTypeReport.Replaced,
            relevantRoadLinkChange.changeType, before, after.toSeq))
        }
      }
    }
    passThroughStep
  }
  
  private def partitionAndAddPairs(assetsAfter: Seq[PersistedLinearAsset], assetsBefore: Seq[PersistedLinearAsset], expiredIds: Set[Long]): Set[Pair] = {
    val alreadyReportedLinkIds = LogUtils.time(logger,"Check already reported changes to be filtered out."){
      changesForReport.asScala.iterator.toList.flatMap(_.after).map(_.linearReference.get.linkId)
    }
    val assetsToReport = assetsAfter.filterNot(asset => {
      alreadyReportedLinkIds.contains(asset.linkId) || expiredIds.contains(asset.id)
    })
    val pairList = new ConcurrentLinkedQueue[Set[Pair]]
    val grouped = assetsToReport.grouped(groupSizeForParallelRun).toList.par
    val (totalTasks: Int, level: Int) = setParallelismLevel(grouped.size)
    logger.info(s"Change groups: $totalTasks, parallelism level used: $level")
    LogUtils.time(logger, "Loop and create pair") {
      assetsToReport.size match {
        case a if a >= parallelizationThreshold =>
          val g = assetsToReport.grouped(groupSizeForParallelRun).toList.par
          val (_, level: Int) = setParallelismLevel(g.size)
            new Parallel().operation(g, level) {
              tasks =>tasks.map { al =>al.foreach(a1=>pairList.add(createPair(Some(a1), assetsBefore)))}
          }
        case _ => assetsToReport.foreach(a1=>pairList.add(createPair(Some(a1), assetsBefore)))
    }}
    
    val distinct = LogUtils.time(logger, "Remove duplicate in pair list") {
      pairList.asScala.toSet
    }

    LogUtils.time(logger, "Flatten pair list") {
      distinct.flatten
    }
  }

  /**
    * 9) start creating report row
    *
    * @param assetsInNewLink
    * @param changes
    * @return
    */
  private def reportingAdjusted(assetsInNewLink: OperationStep, changes: Seq[RoadLinkChange]): (Seq[PersistedLinearAsset], Option[ChangeSet]) = {
    // Assets on totally new links is already reported.
    val pairs = LogUtils.time(logger, "partitionAndAddPairs", startLogging = true) {
      partitionAndAddPairs(assetsInNewLink.assetsAfter, assetsInNewLink.assetsBefore, assetsInNewLink.changeInfo.get.expiredAssetIds)
    }
    LogUtils.time(logger, s"Adding ${pairs.size}to changesForReport", startLogging = true) {
      val grouped = pairs.grouped(groupSizeForParallelRun).toList.par
      val (totalTasks: Int, level: Int) = setParallelismLevel(grouped.size)
      logger.info(s"Change groups: $totalTasks, parallelism level used: $level")
      pairs.size match {
        case a if a >= parallelizationThreshold =>
          val g = pairs.grouped(groupSizeForParallelRun).toList.par
          val (_, level: Int) = setParallelismLevel(g.size)
          LogUtils.time(logger, s"Reporting assets, multithreaded") {
            new Parallel().operation(g, level) { tasks => tasks.map { al => reportLoop(changes, al) }
            }
          }
        case _ => reportLoop(changes, pairs)
      }
    }

    LogUtils.time(logger, "Reporting removed") {
      assetsInNewLink.changeInfo.get.expiredAssetIds.map(asset => {
        val alreadyReported = changesForReport.asScala.iterator.toList.map(_.before).filter(_.nonEmpty).map(_.get.assetId)
        if (!alreadyReported.contains(asset)) {
          val expiringAsset = assetsInNewLink.assetsBefore.find(_.id == asset)
          reportAssetChanges(expiringAsset, None, changes.filterNot(isNew), emptyStep, Some(ChangeTypeReport.Deletion))
        }
      })
    }
    (assetsInNewLink.assetsAfter, assetsInNewLink.changeInfo)
  }

  private def reportLoop(changes: Seq[RoadLinkChange], pairs: Set[Pair]): Unit = {
    LogUtils.time(logger, s"Reporting assets task with items count ${pairs.size} ") {
      pairs.foreach(asset => createRow(changes, asset))
    }
  }
  private def createRow(changes: Seq[RoadLinkChange], pair: Pair)= {
    LogUtils.time(logger, "Creating reporting rows") {
      pair.newAsset match {
        case Some(_) => Some(reportAssetChanges(pair.oldAsset, pair.newAsset, changes, null))
        case None => None
      }
    }
  }
  /**
    * Create pair by using asset id or old asset id when id is 0.
    * @param updatedAsset asset after samuutus
    * @param oldAssets assets before samuutus
    * @return
    */
  private def createPair(updatedAsset: Option[PersistedLinearAsset], oldAssets: Seq[PersistedLinearAsset]): Set[Pair] = {
    def findByOldId(updatedAssets: Option[PersistedLinearAsset], oldAssets: Seq[PersistedLinearAsset]): Set[Pair] = {
      val oldAssetByOldId = oldAssets.find(_.id == updatedAssets.get.oldId)
      if (oldAssetByOldId.isDefined) Set(Pair(oldAssetByOldId, updatedAssets)) else useGivenIfPossible(updatedAssets, oldAssets)
    }

    def useGivenIfPossible(updatedAssets: Option[PersistedLinearAsset], oldAssets: Seq[PersistedLinearAsset]): Set[Pair] = {
      if (oldAssets.size == 1) Set(Pair(oldAssets.headOption, updatedAssets)) else Set(Pair(None, updatedAssets))
    }

    val oldAsset = oldAssets.find(_.id == updatedAsset.get.id)
    if (oldAsset.isDefined) Set(Pair(oldAsset, updatedAsset)) else findByOldId(updatedAsset, oldAssets)
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
  /**
    * 2) [[filterChanges]] Apply only needed changes to assets by writing your own filtering logic. Default is to remove
    * new links that already have assets, so that duplicate assets will not be created.
    * @param typeId
    * @param changes
    * @return filtered changes
    */
  def filterChanges(typeId: Int, changes: Seq[RoadLinkChange]): Seq[RoadLinkChange] = {
    val newLinkIds = changes.flatMap(_.newLinks.map(_.linkId))
    val linkIdsWithExistingAsset = service.fetchExistingAssetsByLinksIdsString(typeId, newLinkIds.toSet, Set(), newTransaction = false).map(_.linkId)
    if (linkIdsWithExistingAsset.nonEmpty) logger.info(s"found already created assets on new links ${linkIdsWithExistingAsset.mkString(",")}")
    changes.filterNot(c => c.changeType == Add && linkIdsWithExistingAsset.contains(c.newLinks.head.linkId))
  }
  /**
    * 4.2) Add logic to create assets when link is created. Default is do nothing.
    * @param change
    * @param changeSets
    * @return
    */
  def operationForNewLink(change: RoadLinkChange, onlyNeededNewRoadLinks: Seq[RoadLink], changeSets: ChangeSet): Option[OperationStep] = None
  /**
    * 4.3) Add additional operation based on removed link when something more also need updating like some other table. Default is do nothing.
    * @param change
    * @param assetsAll
    * @param changeSets
    * @return
    */
  def additionalRemoveOperation(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = None
  /**
    * 3) Mass operation based on list of removed links. Default is do nothing.
    * @param expiredLinks
    */
  def additionalRemoveOperationMass(expiredLinks: Seq[String]): Unit = {}
  /**
    * 8) This is used to add additional operation based on new position of links. For example based on roadclass changes. Default is do nothing.
    * @param operationStep
    * @param changes
    * @return
    */
  def additionalOperations(operationStep: OperationStep, changes: Seq[RoadLinkChange]): Option[OperationStep] = Some(operationStep)
  /**
    * 4.1) Add additional logic if something more also need updating like some other table. Default is do nothing.
    * @param change
    * @param assetsAll
    * @param changeSets
    * @return
    */
  def nonAssetUpdate(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = None

  def updateLinearAssets(typeId: Int): Unit = {
    val latestSuccess = PostGISDatabase.withDynSession(Queries.getLatestSuccessfulSamuutus(typeId))
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(latestSuccess)
    logger.info(s"Processing ${changeSets.size} road link changes set")
    changeSets.foreach(changeSet => {
      logger.info(s"Started processing change set ${changeSet.key}")

      val (addAndRemoveChanges, replaceAndSplitChanges) = LogUtils.time(logger, "Partition changes") {
        changeSet.changes.partition(change => Seq(Add, Remove).contains(change.changeType))
      }

      val (key, statusDate, targetDate) = (changeSet.key, changeSet.statusDate, changeSet.targetDate)
      try {
        withDynTransaction {
          updateByRoadLinks(typeId, addAndRemoveChanges)
          updateByRoadLinks(typeId, replaceAndSplitChanges)
          ValidateSamuutus.validate(typeId, RoadLinkChangeSet(key, statusDate, targetDate, addAndRemoveChanges ++ replaceAndSplitChanges))
          generateAndSaveReport(typeId, targetDate)
        }
      } catch {
        case e:SamuutusFailed =>
          generateAndSaveReport(typeId, targetDate)
          throw e
      }
    })
  }

  def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]): Unit = {
    val changes = filterChanges(typeId, changesAll)
    val oldIds = changes.filterNot(isDeletedOrNew).map(_.oldLink.get.linkId)
    val deletedLinks = changes.filter(isDeleted).map(_.oldLink.get.linkId)
    val addedLinksCount = changes.filter(isNew).flatMap(_.newLinks).size
    // here we assume that RoadLinkProperties updater has already remove override if KMTK version traffic direction is same.
    // still valid overrided has also been samuuted

    val newLinkIds = changes.flatMap(_.newLinks.map(_.linkId))
    logger.info("Fetching road links and assets")
    // FeatureClass HardShoulder or WinterRoads, and ExpiringSoon filtered away 
    val onlyNeededNewRoadLinks = roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(newLinkIds.toSet, false)
    
    val (existingAssetsSize:Int, initChangeSet: ChangeSet, assetsGrouped: mutable.HashMap[String, Set[PersistedLinearAsset]]) = prepareAssets(typeId, changes, oldIds, deletedLinks, addedLinksCount)
    val (projectedAssets, changedSet) = LogUtils.time(logger, s"Samuuting logic finished: ") {
      fillNewRoadLinksWithPreviousAssetsData(typeId, onlyNeededNewRoadLinks,assetsGrouped, changes, initChangeSet,existingAssetsSize)
    }

    additionalRemoveOperationMass(deletedLinks)
    logger.info("Starting to save changeSets")
    logChangeSetSizes(changedSet)
    LogUtils.time(logger, s"Saving changeSets took: ") {
      updateChangeSet(changedSet)
    }
    logger.info("Starting to save generated")
    LogUtils.time(logger, s"Saving generated took: ") {
      persistProjectedLinearAssets(projectedAssets.filter(_.id == 0L),onlyNeededNewRoadLinks)
    }
  }

  private def prepareAssets(typeId: Int, changes: Seq[RoadLinkChange], oldIds: Seq[String], deletedLinks: Seq[String], addedLinksCount: Int) = {
    val existingAssets = service.fetchExistingAssetsByLinksIdsString(typeId, oldIds.toSet, deletedLinks.toSet, newTransaction = false)
    val initChangeSet = LinearAssetFiller.initWithExpiredIn(existingAssets, deletedLinks)

    logger.info(s"Processing assets: ${typeId}, assets count: ${existingAssets.size}, number of changes in the sets: ${changes.size}")
    logger.info(s"Deleted links count: ${deletedLinks.size}, new links count: ${addedLinksCount}")
    logger.info("Starting to process changes")
    val assetsGrouped = IterableOperation.groupByPropertyHashMap(existingAssets, (elem: PersistedLinearAsset) => elem.linkId)
    (existingAssets.size, initChangeSet, assetsGrouped)
  }

  private def goTroughChangesParallelLoop(onlyNeededNewRoadLinks: Seq[RoadLink], assetsGroup: mutable.HashMap[String, Set[PersistedLinearAsset]],
                                          changes: Seq[RoadLinkChange], changeSet: ChangeSet) = {
    val changesGrouped = changes.grouped(groupSizeForParallelRun).toList.par
    val (totalTasks: Int, level: Int) = setParallelismLevel(changesGrouped.size)
    logger.info(s"Change groups: $totalTasks, parallelism level used: $level")

    val initStep = OperationStep(Seq(), Some(changeSet))
    new Parallel().operation(changesGrouped, level) { tasks =>
      tasks.flatMap {
        changesPerThread =>
          LogUtils.time(logger, s"Processing ${changesPerThread.size} changes in a single thread") {
            changesPerThread.map(change => {
              goThroughChanges(assetsGroup, onlyNeededNewRoadLinks, changeSet, initStep, change, OperationStepSplit(Seq(), Some(changeSet)))
            })
          }
      }
    }.seq.toSeq
  }

  private def goTroughChangesLinearly(onlyNeededNewRoadLinks: Seq[RoadLink], assetsGroup: mutable.HashMap[String, Set[PersistedLinearAsset]],
                                      changes: Seq[RoadLinkChange], changeSet: ChangeSet) = {
    var percentageProcessed = 0
    val initStep = OperationStep(Seq(), Some(changeSet))
    changes.zipWithIndex.map(changeWithIndex => {
      val (change, index) = changeWithIndex
      percentageProcessed = LogUtils.logArrayProgress(logger, "Projecting assets to new links", changes.size, index, percentageProcessed)
      goThroughChanges(assetsGroup, onlyNeededNewRoadLinks, changeSet, initStep, change, OperationStepSplit(Seq(), Some(changeSet)))
    })
  }
  
  /**
    * 4) Start projecting everything into new links based on replace info.
    * @param typeId Asset typeId
    * @param onlyNeededNewRoadLinks Filtered new road links from changes
    * @param assetsGroup Assets grouped by linkId
    * @param changes Road link changes to process
    * @param changeSet changeSet with expired asset ids
    * @return Assets moved to new links and adjusted, changeSet for saving adjustments
    */
  private def fillNewRoadLinksWithPreviousAssetsData(typeId: Int, onlyNeededNewRoadLinks: Seq[RoadLink], 
                                                     assetsGroup: mutable.HashMap[String, Set[PersistedLinearAsset]], changes: Seq[RoadLinkChange],
                                                     changeSet: ChangeSet,existingAssetsSize:Int): (Seq[PersistedLinearAsset], ChangeSet) = {
    val initStep = Some(OperationStep(Seq(), Some(changeSet)))
    logger.info(s"Projecting ${existingAssetsSize} assets to new links")
    val projectedToNewLinks = LogUtils.time(logger, "Projecting assets to new links") {
      val rawData = changes.size match {
        case a if a >= parallelizationThreshold => goTroughChangesParallelLoop(onlyNeededNewRoadLinks, assetsGroup, changes, changeSet)
        case _ => goTroughChangesLinearly(onlyNeededNewRoadLinks, assetsGroup, changes, changeSet)
      }
      LogUtils.time(logger, "Filter empty and empty afters away from projected") {
        rawData.filter(_.nonEmpty)
      }
    }
    
    val (after, changeInfoM) = LogUtils.time(logger, "Merging operation steps before adjustment") {
      mergeAfterAndChangeSets(projectedToNewLinks :+ initStep)
    }

    val operated: OperationStep = OperationStep(assetsAfter = after, changeInfo = changeInfoM, assetsBefore = assetsGroup.values.toSeq.flatten)

    logger.info(s"Adjusting ${operated.assetsAfter.size} projected assets")
    val (assetsOperated, changeInfo) = LogUtils.time(logger, "Adjusting and reporting projected assets") {
      adjustAndReport(typeId, onlyNeededNewRoadLinks, changes,operated)
    }
    (assetsOperated, changeInfo.get)
  }

  private def goThroughChanges(assetsAllG: mutable.HashMap[String,Set[PersistedLinearAsset]], onlyNeededNewRoadLinks: Seq[RoadLink], changeSets: ChangeSet,
                               initStep: OperationStep, change: RoadLinkChange, initStepSplit:OperationStepSplit): Option[OperationStep] = {

    nonAssetUpdate(change, Seq(), null)
    LogUtils.time(logger, s"Change type: ${change.changeType.value}, Operating changes") {
      val assets = Try(assetsAllG(change.oldLink.get.linkId)) match {
        case Success(value) => value.toSeq
        case Failure(_) => Seq()
      }
      change.changeType match {
        case RoadLinkChangeType.Add =>
          val operation = operationForNewLink(change, onlyNeededNewRoadLinks, changeSets).getOrElse(initStep)
          Some(reportAssetChanges(None, operation.assetsAfter.headOption, Seq(change), operation, Some(ChangeTypeReport.Creation), true))
        case RoadLinkChangeType.Remove => additionalRemoveOperation(change, assets, changeSets)
        case _ =>
          if (assets.nonEmpty) {
            change.changeType match {
              case RoadLinkChangeType.Replace =>
                try {handleReplacements(changeSets, initStep, change, assets, onlyNeededNewRoadLinks)} catch {
                  case _: FailedToFindReplaceInfo=> None
                  case e: Throwable => logErrorAndReturnNone(change, assets, e); throw e
                }
              case RoadLinkChangeType.Split =>
                try {handleSplits(changeSets, initStepSplit, change, assets, onlyNeededNewRoadLinks)} catch {
                  case _: FailedToFindReplaceInfo=> None
                  case e: Throwable => logErrorAndReturnNone(change, assets, e); throw e
                }
              case _ => None
            }
          } else None
      }
    }
  }
  private def logErrorAndReturnNone(change: RoadLinkChange, assets: Seq[PersistedLinearAsset], e: Throwable) = {
    logger.error(s"Samuutus failled with RoadlinkChange: $change", e)
    assets.foreach(a => logger.error(s"Samuutus failled for asset ${a.id} with start measure ${a.startMeasure} and end measure ${a.endMeasure} on link ${a.linkId}"))
    None
  }
  private def adjustAndReport(typeId: Int, onlyNeededNewRoadLinks: Seq[RoadLink],
                              changes: Seq[RoadLinkChange], merged: OperationStep): (Seq[PersistedLinearAsset], Option[ChangeSet]) = {
    val adjusted = LogUtils.time(logger, "Adjusting assets") {
      adjustAndAdditionalOperations(typeId, onlyNeededNewRoadLinks, Some(merged), changes)
    }
    LogUtils.time(logger, "Reporting assets") {
      reportingAdjusted(adjusted, changes)
    }
  }

  private def adjustAndAdditionalOperations(typeId: Int, onlyNeededNewRoadLinks: Seq[RoadLink],
                                            operationStep: Option[OperationStep], changes: Seq[RoadLinkChange]): OperationStep = {
    val additionalSteps = LogUtils.time(logger, s"Performing additional operations for ${AssetTypeInfo.apply(typeId)}") {
      additionalOperations(operationStep.get, changes)
    }

    adjustAssets(typeId, onlyNeededNewRoadLinks, additionalSteps.get)
  }
  /**
    * 6) Run fillTopology to adjust assets based on link length and other assets on link.
    * @param typeId
    * @param onlyNeededNewRoadLinks
    * @param operationStep
    * @return
    */
  private def adjustAssets(typeId: Int, onlyNeededNewRoadLinks: Seq[RoadLink], operationStep: OperationStep): OperationStep = {
    val OperationStep(assetsAfter, changeSetFromOperation,_) = operationStep
    val assetsOperated = assetsAfter.filterNot(a => changeSetFromOperation.get.expiredAssetIds.contains(a.id))
    val groupedAssets = LogUtils.time(logger, "Convert to right format") {
      assetFiller.mapLinkAndAssets(assetsOperated, onlyNeededNewRoadLinks)
    }
    val (adjusted, changeSet) = LogUtils.time(logger, "Run fillTopology") {
      adjustLinearAssetsLoop(typeId, groupedAssets, changeSetFromOperation)
    }

    operationStep.copy(
      assetsAfter = adjusted.map(convertToPersisted),
      changeInfo = Some(changeSet)
    )
  }

  private def adjustLinearAssetsLoop(typeId: Int,
                                     assetsByLink: mutable.HashMap[String, LinkAndAssets],
                                     changeSet: Option[ChangeSet] = None): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    def adjusting(typeId: Int, changeSet: Option[ChangeSet], assetsByLink: mutable.HashMap[String, LinkAndAssets]): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      val assets = assetsByLink.flatMap(_._2.assets).toSeq.groupBy(_.linkId)
      val roadLinks = assetsByLink.map(_._2.link).toList
      adjustLinearAssets(typeId,roadLinks, assets, changeSet)
    }

    def parallelLoop(): List[(Seq[PieceWiseLinearAsset], ChangeSet)] = {
      val grouped = assetsByLink.grouped(groupSizeForParallelRun).toList.par
      val (totalTasks: Int, level: Int) = setParallelismLevel(grouped.size)
      logger.info(s"Asset groups: $totalTasks, parallelism level used: $level")

      new Parallel().operation(grouped, level) { tasks =>
        tasks.map { al =>
          LogUtils.time(logger, s"Adjusting assets on ${al.size} links in a single thread") {
            val ids = al.flatMap(_._2.assets.map(_.id)).toSet
            val links = al.keys.toSet
            val excludeUnneededChangeSetItems = changeSet match {
              case Some(x) => Some(ChangeSet(
                droppedAssetIds = x.droppedAssetIds.intersect(ids),
                adjustedMValues = x.adjustedMValues.filter(a => links.contains(a.linkId)),
                adjustedSideCodes = x.adjustedSideCodes.filter(a => ids.contains(a.assetId)),
                expiredAssetIds = x.expiredAssetIds.intersect(ids),
                valueAdjustments = x.valueAdjustments
              ))
              case None => None
            }
            adjusting(typeId, excludeUnneededChangeSetItems, al)
          }
        }
      }.toList
    }

    val linksCount = assetsByLink.size

    val result = linksCount match {
      case a if a >= parallelizationThreshold => parallelLoop()
      case _ => List(adjusting(typeId, changeSet, assetsByLink))
    }

    val changeSetResult = result.foldLeft(LinearAssetFiller.useOrEmpty(None)) { (a, b) =>
      LinearAssetFiller.combineChangeSets(a, b._2)
    }
    (result.flatMap(_._1), LinearAssetFiller.removeExpiredMValuesAdjustments(changeSetResult))
  }

  /**
    * 7) Override if asset need totally different fillTopology implementation.
    *
    * @param typeId
    * @param roadLinks
    * @param assets
    * @param changeSet
    * @return
    */
  protected def adjustLinearAssets(typeId: Int,roadLinks: Seq[RoadLinkForFillTopology], assets: Map[String, Seq[PieceWiseLinearAsset]],
                                   changeSet: Option[ChangeSet] = None): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    assetFiller.fillTopologyChangesGeometry(roadLinks, assets, typeId, changeSet)
  }

  private def handleReplacements(changeSets: ChangeSet, initStep: OperationStep, change: RoadLinkChange, assets: Seq[PersistedLinearAsset], onlyNeededNewRoadLinks: Seq[RoadLink]): Option[OperationStep] = {
    val roadLinkInfo = change.newLinks.head
    val assetInInvalidLink = !onlyNeededNewRoadLinks.exists(_.linkId == roadLinkInfo.linkId)
    if(assetInInvalidLink) { // assets is now in invalid link, expire
      assets.map(a => {
        val expireStep = OperationStep(Seq(), Some(changeSets.copy(expiredAssetIds = changeSets.expiredAssetIds ++ Set(a.id))), assetsBefore = Seq())
        Some(reportAssetChanges(Some(a), None, Seq(change), expireStep, Some(ChangeTypeReport.Deletion),useGivenChange = true))
      }).foldLeft(Some(initStep))(mergerOperations)
    } else {
      val projected = assets.map(a => projecting(changeSets, change, a, a))
      LogUtils.time(logger, "Merging steps after projecting") {
        projected.filter(_.nonEmpty).foldLeft(Some(initStep))(mergerOperations)
      }
    }
  }

  private def handleSplits(changeSet: ChangeSet, initStep: OperationStepSplit, change: RoadLinkChange, assets: Seq[PersistedLinearAsset], onlyNeededNewRoadLinks: Seq[RoadLink]): Option[OperationStep] = {
    val divided = operationForSplit(change, assets, changeSet, onlyNeededNewRoadLinks)
    val splitOperation = divided.foldLeft(initStep)(mergerOperationsSplit)

    Some(OperationStep(assetsAfter = splitOperation.assetsAfter, changeInfo = splitOperation.changeInfo, assetsBefore = splitOperation.assetsBefore))
  }

  private def operationForSplit(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSet: ChangeSet,
                                onlyNeededNewRoadLinks: Seq[RoadLink]): Seq[OperationStepSplit]= {
    def checkForExpire(splits: Seq[OperationStepSplit]): Boolean = {
      val newLinkIds = splits.map(_.newLinkId)
      val someRoadLinksFound = onlyNeededNewRoadLinks.map(_.linkId).exists(newLinkIds.contains)
      newLinkIds.forall(_ == "") || !someRoadLinksFound
    }

    val relevantAssets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
    relevantAssets.flatMap(sliceFrom => {
      val sliced = slicer(Seq(sliceFrom), Seq(), change)
      val splits = sliced.map(part => projectingSplit(changeSet, change, part, sliceFrom).get)
      if (checkForExpire(splits)) {
        reportAssetChanges(Some(sliceFrom), None, Seq(change), emptyStep, Some(ChangeTypeReport.Deletion), useGivenChange = true)
        splits.map(split => updateSplitOperationWithExpiredIds(split, sliceFrom.id))
      } else {
        splits
      }
    })
  }

  @tailrec
  private def slicer(assets: Seq[PersistedLinearAsset], fitIntoRoadLinkPrevious: Seq[PersistedLinearAsset], change: RoadLinkChange): Seq[PersistedLinearAsset] = {
    def slice(change: RoadLinkChange, asset: PersistedLinearAsset): Seq[PersistedLinearAsset] = {
      val selectInfo = sortAndFind(change, asset, fallInWhenSlicing).getOrElse(throw FailedToFindReplaceInfo(errorMessage(change, asset)))

      val shorted = asset.copy(endMeasure = selectInfo.oldToMValue.getOrElse(0.0))
      val oldId = if(asset.id != 0) asset.id else asset.oldId
      val newPart = asset.copy(id = 0, startMeasure = selectInfo.oldToMValue.getOrElse(0.0), oldId = oldId)

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
    Some(OperationStep(Seq(projected), Some(changeSet), assetsBefore = Seq()))
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
    Some(OperationStepSplit(Seq(projected), Some(changeSet),newLinkId = newId, assetsBefore = Seq()))
  }

  private def projectByUsingReplaceInfo(changeSets: ChangeSet, change: RoadLinkChange, asset: PersistedLinearAsset) = {
    val info = sortAndFind(change, asset, fallInReplaceInfoOld).getOrElse(throw FailedToFindReplaceInfo(errorMessage(change, asset)))
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
  private def errorMessage(c: RoadLinkChange, a: PersistedLinearAsset): String = {
    s"Replace info for asset ${a.id} with start measure ${a.startMeasure} and end measure ${a.endMeasure} on link ${a.linkId} not found from change ${c}"
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
            changedSet.adjustedSideCodes ++ Seq(SideCodeAdjustment(assetId, newLinkId, SideCode.apply(newSideCode), asset.typeId))
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
    
    if (changeSet.adjustedMValues.nonEmpty) {
      //TODO remove after finding what looking for
      logger.info(s"Saving adjustments for asset/link ids=${changeSet.adjustedMValues.sortBy(_.linkId).map(a => s"${a.assetId}/${a.linkId} start measure: ${a.startMeasure} end measure: ${a.endMeasure}").mkString(", ")}")
      dao.updateMValuesChangeInfos(changeSet.adjustedMValues.map(a => MValueUpdate(a.assetId, a.linkId, Measures(a.startMeasure, a.endMeasure).roundMeasures())))
    }
    
    val ids = changeSet.expiredAssetIds.toSeq
    if (ids.nonEmpty) {
      logger.debug(s"Expiring ids ${ids.mkString(", ")}")
      dao.updateExpirations(ids,expired = true,AutoGeneratedUsername.generatedInUpdate)
    }
    
    if (changeSet.adjustedSideCodes.nonEmpty) {
      logger.debug(s"Saving SideCode adjustments for asset/link ids=${changeSet.adjustedSideCodes.map(a => s"${a.assetId}").mkString(", ")}")
      dao.updateSideCodes(changeSet.adjustedSideCodes)
    }

    if (changeSet.valueAdjustments.nonEmpty) {
      logger.debug(s"Saving value adjustments for assets: ${changeSet.valueAdjustments.map(a => s"${a.assetId}").mkString(", ")}")
      dao.updateValueAdjustments(changeSet.valueAdjustments)
    }
  }

  protected def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset], onlyNeededNewRoadLinks: Seq[RoadLink]): Unit = {
    if (newLinearAssets.nonEmpty)
      logger.info(s"Saving projected linear assets, count: ${newLinearAssets.size}")
    
    logger.info(s"insert assets count: ${newLinearAssets.size}")
    newLinearAssets.foreach { linearAsset =>
      val roadlink = onlyNeededNewRoadLinks.find(_.linkId == linearAsset.linkId)
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
    if (newLinearAssets.nonEmpty)
      logger.debug(s"Added assets for linkids ${newLinearAssets.map(_.linkId)}")
  }


  protected def createMassOperationRow(adjustedSideCodes: Seq[SideCodeAdjustment], roadLinks: Seq[RoadLink], a: PersistedLinearAsset): NewLinearAssetMassOperation = {
    val oldAssetValue = a.value.getOrElse(throw new IllegalStateException(s"Value of the old asset ${a.id} of type ${a.typeId} is not available"))
    val roadLink = roadLinks.find(_.linkId == a.linkId).getOrElse(throw new IllegalStateException(s"Road link ${a.linkId} no longer available"))
    NewLinearAssetMassOperation(
      a.typeId, a.linkId, oldAssetValue, adjustedSideCodes.find(_.assetId == a.id).get.sideCode.value,
      Measures(a.startMeasure, a.endMeasure).roundMeasures(), AutoGeneratedUsername.generatedInUpdate, LinearAssetUtils.createTimeStamp(),
      Some(roadLink), fromUpdate = true, a.createdBy, a.createdDateTime, a.modifiedBy, a.modifiedDateTime, a.verifiedBy, a.verifiedDate, a.informationSource.map(_.value),
      linkSource = service.getLinkSource(Some(roadLink)),
      geometry = service.getGeometry(Some(roadLink)), expired = false
    )
  }

  /**
   * Looks for assets that remain within split-deleted link
   *
   * @param linksAndOperations sequence of RoadLink and related OperationStep pairs that contain post-split information
   * @return Set[PersistedLinearAsset] returns the found assets
   */
  private def getOldAssetsToExpireAfterSplit(linksAndOperations: Seq[LinkAndOperation]): Set[PersistedLinearAsset] = {
    val oldAssetsInsideGeometry = linksAndOperations.filter(_.newLinkId.nonEmpty).flatMap(_.operation.assetsAfter).toSet
    val oldAssetsOutsideGeometry = linksAndOperations
      .filter(_.newLinkId.isEmpty).flatMap(_.operation.assetsBefore)
      .filter(asset => !oldAssetsInsideGeometry.map(_.id).contains(asset.id))
    oldAssetsOutsideGeometry.toSet
  }

  /**
   * Updates ChangeSet by adding expiredIds
   * @param changeSet ChangeSet to be updated
   * @param expiredId Id to add to expiredIds in changeSet
   * @return updated ChangeSet
   */
  private def updateChangeSetWithExpiredId(changeSet: Option[ChangeSet], expiredId: Long): Option[ChangeSet] = {
    changeSet match {
      case Some(info) =>
        val copiedInfo = Some(info.copy(expiredAssetIds = info.expiredAssetIds + expiredId))
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
  private def updateSplitOperationWithExpiredIds(operationStep: OperationStepSplit, expiredId: Long): OperationStepSplit = {
    val updatedChangeSet = updateChangeSetWithExpiredId(operationStep.changeInfo, expiredId)
    operationStep.copy(newLinkId = "", changeInfo = updatedChangeSet, assetsAfter = Seq())
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
      val oldAssetsToExpire = getOldAssetsToExpireAfterSplit(linksAndOperations)
      oldAssetsToExpire.foreach(asset => Some(reportAssetChanges(Some(asset), None, Seq(change),
        toOperationStep(emptyLinkOperation), Some(ChangeTypeReport.Deletion),useGivenChange = true)))
      oldAssetsToExpire
    } else
      Set.empty[PersistedLinearAsset]
  }
}
