package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.{client, _}
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

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable.ListBuffer
import scala.collection.{Seq, mutable}
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

  var changesHashMap: ChangeMap = null
  def findChange(id: String): Changes = {
    Try(this.changesHashMap(id)) match {
      case Success(value) => value.head
      case Failure(_) => throw new Exception("cannot find change")
    }
  }

  case class ReplaceInfoSet(allNeededReplaced: Set[ReplaceInfosWithHash])

  case class RoadLinkChangeWithHash(infos: RoadLinkChange, sourceId: ChangeId)

  case class ReplaceInfosWithHash(infos: ReplaceInfo, sourceId: ChangeId, sourceChangeType: RoadLinkChangeType)
  case class ReplaceInfosWithHashRowID(rowId: String, infos: ReplaceInfo, sourceId: ChangeId, sourceChangeType: RoadLinkChangeType)

  
  type ChangeId = String
  type Changes = RoadLinkChangeWithHash

  type ChangeMap = mutable.HashMap[ChangeId, Set[Changes]]
  
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
    level
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
  private def sortAndFind(change: Seq[ReplaceInfosWithHash], asset: PersistedLinearAsset, finder: (ReplaceInfosWithHash, PersistedLinearAsset) => Boolean): Option[ReplaceInfosWithHash] = {
    change.sortBy(_.infos.oldToMValue).reverse.find(finder(_, asset))
  }
  /**
    * Checks if an asset falls within the specified slicing criteria.
    *
    * This method evaluates whether the given asset meets specific criteria defined by a `ReplaceInfo` object.
    * The criteria include matching `linkId`, ensuring that the `endMeasure` is greater than or equal to
    * the specified value, and verifying that the `startMeasure` is greater than or equal to another specified value.
    *
    * @param replaceInfoInput The criteria for replacement.
    * @param asset       The asset to be evaluated.
    * @return `true` if the asset meets the slicing criteria, `false` otherwise.
    */
  private def fallInWhenSlicing(replaceInfoInput: ReplaceInfosWithHash, asset: PersistedLinearAsset): Boolean = {
    val replaceInfo =replaceInfoInput.infos
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
  private def fallInReplaceInfoOld(replaceInfoInput: ReplaceInfosWithHash, asset: PersistedLinearAsset): Boolean = {
    val replaceInfo =replaceInfoInput.infos
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


  protected def mergeAfterAndChangeSets(assetsUnderReplace: Seq[Option[OperationStep]]): (List[PersistedLinearAsset], Some[ChangeSet]) = {
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

    (after.toList, Some(changeSet))
  }
  
  private def mergeChangeSets(changeSets: Seq[Option[ChangeSet]]): Some[ChangeSet] = {
    val droppedAssetIds = new ListBuffer[Long]
    val adjustedMValues = new ListBuffer[MValueAdjustment]
    val adjustedSideCodes = new ListBuffer[SideCodeAdjustment]
    val expiredAssetIds = new ListBuffer[Long]
    val valueAdjustments = new ListBuffer[ValueAdjustment]
    for (a <- changeSets) {
      if (a.nonEmpty) {
        droppedAssetIds.appendAll(a.get.droppedAssetIds)
        adjustedMValues.appendAll(a.get.adjustedMValues.toList)
        adjustedSideCodes.appendAll(a.get.adjustedSideCodes.toList)
        expiredAssetIds.appendAll(a.get.expiredAssetIds)
        valueAdjustments.appendAll(a.get.valueAdjustments.toList)
      }
    }

    val changeSet = ChangeSet(droppedAssetIds = droppedAssetIds.toSet,
      adjustedMValues = adjustedMValues.distinct, adjustedSideCodes = adjustedSideCodes.distinct,
      expiredAssetIds = expiredAssetIds.toSet, valueAdjustments = valueAdjustments.distinct)

    Some(changeSet)
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
    LogUtils.time(logger, "Loop and create pair") {
      assetsToReport.foreach(a1=>pairList.add(createPair(Some(a1), assetsBefore)))
    }

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
    val pairs = LogUtils.time(logger, "partitionAndAddPairs", startLogging = false) {
      partitionAndAddPairs(assetsInNewLink.assetsAfter, assetsInNewLink.assetsBefore, assetsInNewLink.changeInfo.get.expiredAssetIds)
    }
    LogUtils.time(logger, s"Adding ${pairs.size}to changesForReport", startLogging = false) {
      reportLoop(changes, pairs)
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
   val pair = if (oldAsset.isDefined) Set(Pair(oldAsset, updatedAsset)) else findByOldId(updatedAsset, oldAssets)
    println(oldAssets)
    println(pair)
    pair
    
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

      val (key, statusDate, targetDate) = (changeSet.key, changeSet.statusDate, changeSet.targetDate)
      try {
        withDynTransaction {
          updateByRoadLinks(typeId, changeSet.changes)
          ValidateSamuutus.validate(typeId, RoadLinkChangeSet(key, statusDate, targetDate, changeSet.changes))
          generateAndSaveReport(typeId, targetDate)
        }
      } catch {
        case e:SamuutusFailed =>
          generateAndSaveReport(typeId, targetDate)
          throw e
      }
    })
  }

  /**
    * Group related replace info into one set 
    * Assume than changes are split or Replace
    * @param changes
    * @return
    */
  private def groupChanges(changes: Seq[RoadLinkChange]): Set[ReplaceInfoSet] = {
    val replaceInfos = new ListBuffer[ReplaceInfosWithHashRowID]
    val changesWithHash = new ListBuffer[RoadLinkChangeWithHash]
    changes.foreach(a => {
      val changSetId = s"${UUID.randomUUID()}"
      a.replaceInfo.foreach(r => {
        val rowId = s"${UUID.randomUUID()}"
        replaceInfos.append(ReplaceInfosWithHashRowID(rowId, r, changSetId, a.changeType))
      })
      changesWithHash.append(RoadLinkChangeWithHash(a, changSetId))
    })

    val groupByHash = IterableOperation.groupByPropertyHashMap(changesWithHash, (elem: RoadLinkChangeWithHash) => elem.sourceId)
    changesHashMap = groupByHash
    
    
    val (replace, split) = replaceInfos.partition(a => a.sourceChangeType == RoadLinkChangeType.Replace)

    val groupByOldSplit = IterableOperation.groupByPropertyHashMap(split, (elem: ReplaceInfosWithHashRowID) => elem.infos.oldLinkId.get)
    val partOfReplace = new ListBuffer[ReplaceInfosWithHashRowID]
    val replaceGrouped= replace.groupBy(_.infos.newLinkId.get)

    def findSplitByOldLinkId(a: ChangeId) = {
      Try(groupByOldSplit(a)) match {
        case Success(value) => value
        case Failure(_) => Set.empty[ReplaceInfosWithHashRowID]
      }
    }

    val replaceInfoSet = replaceGrouped.map(a => {
      val oldIds = a._2.toList.map(_.infos.oldLinkId.get)
      val newIds = a._2.toList.map(_.infos.newLinkId.get)
      val byOld = LogUtils.time(logger, s"find by related replace info by oldLinkId") {
        oldIds.flatMap(a => findSplitByOldLinkId(a))
      }
      val byNew = LogUtils.time(logger, s"find by related replace info by newLinkId") {
        split.filter(b => newIds.contains(b.infos.newLinkId.getOrElse("")))
      }
      val splitPart = byOld ++ byNew
      val splitOldIds = splitPart.map(_.infos.oldLinkId.get)
      val splitPartFromOld = splitOldIds.flatMap(a => findSplitByOldLinkId(a))
      val splitPartFinal = byOld ++ byNew ++ splitPartFromOld
      partOfReplace.appendAll(splitPartFinal)

      if (splitPartFinal.nonEmpty) {
        println(splitPartFinal)
      }

      val converted = (a._2 ++ splitPartFinal).map(a => ReplaceInfosWithHash(infos = a.infos, sourceId = a.sourceId, sourceChangeType = a.sourceChangeType))

      ReplaceInfoSet(converted.toSet)
    })

    val rowsInReplicase: Set[String] = partOfReplace.map(_.rowId).toSet
    val filtered = split.toSeq.filterNot(a => rowsInReplicase.contains(a.rowId))
    val splitSet = filtered.groupBy(a => a.infos.oldLinkId.get).map(a =>
      ReplaceInfoSet(a._2.
        map(a => 
          ReplaceInfosWithHash(infos = a.infos, sourceId = a.sourceId, sourceChangeType = a.sourceChangeType)).toSet)
    )
    (replaceInfoSet ++ splitSet).toSet
  }

  def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]): Unit = {
    val (oldLinkIds, deletedLinks, addedLinksCount, newLinkIds,
    addAndRemoveChanges, changeSetForProjecting,initChangeSet) = operateNonAssetChangeAndInitializeHashMap(typeId, changesAll)

    // here we assume that RoadLinkProperties updater has already remove override if KMTK version traffic direction is same.
    // still valid overrided has also been samuuted
    
    logger.info("Fetching road links and assets")
    // FeatureClass HardShoulder or WinterRoads, and ExpiringSoon filtered away 
    val onlyNeededNewRoadLinks = roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(newLinkIds.toSet, false)

    val (_, assetsGrouped) = prepareAssets(typeId, oldLinkIds, deletedLinks, addedLinksCount)
    val (projectedAssets, changedSet) = LogUtils.time(logger, s"Samuuting logic finished: ") {
      fillNewRoadLinksWithPreviousAssetsData(typeId, onlyNeededNewRoadLinks,assetsGrouped, addAndRemoveChanges,changeSetForProjecting, initChangeSet)
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

  private def operateNonAssetChangeAndInitializeHashMap(typeId: Int, changesAll: Seq[RoadLinkChange]):
  (Seq[String], Seq[String], Int, Seq[String], Seq[RoadLinkChange], Set[ReplaceInfoSet], ChangeSet) = {
    val changes = filterChanges(typeId, changesAll)
    logger.info(s"Number of changes in the sets: ${changes.size}")
    changes.foreach(a => nonAssetUpdate(a, Seq(), null))

    val oldLinkIds = changes.filterNot(isDeletedOrNew).map(_.oldLink.get.linkId)
    val deletedLinks = changes.filter(isDeleted).map(_.oldLink.get.linkId)
    
    val assetsOnRemovedLinks = service.fetchExistingAssetsByLinksIdsString(typeId, Set(), deletedLinks.toSet, newTransaction = false)
    val initChangeSet = LinearAssetFiller.initWithExpiredIn(assetsOnRemovedLinks, deletedLinks)

    LogUtils.time(logger, "Reporting removed") {
      initChangeSet.expiredAssetIds.map(asset => {
        val alreadyReported = changesForReport.asScala.iterator.toList.map(_.before).filter(_.nonEmpty).map(_.get.assetId)
        if (!alreadyReported.contains(asset)) {
          val expiringAsset = assetsOnRemovedLinks.find(_.id == asset)
          reportAssetChanges(expiringAsset, None, changes.filter(isDeleted), emptyStep, Some(ChangeTypeReport.Deletion))
        }
      })
    }
    
    val addedLinksCount = changes.filter(isNew).flatMap(_.newLinks).size
    val newLinkIds = changes.flatMap(_.newLinks.map(_.linkId))
    val (addAndRemoveChanges, replaceAndSplitChanges) = LogUtils.time(logger, "Partition changes") {
      changesAll.partition(change => Seq(Add, Remove).contains(change.changeType))
    }
    
    val changeSetForProjecting = LogUtils.time(logger, "Organizing changes", startLogging = true) {groupChanges(replaceAndSplitChanges)}

    (oldLinkIds, deletedLinks, addedLinksCount, newLinkIds, addAndRemoveChanges, changeSetForProjecting,initChangeSet)
  }
  private def prepareAssets(typeId: Int, oldLinkIds: Seq[String], deletedLinks: Seq[String], addedLinksCount: Int) = {
    val existingAssets = service.fetchExistingAssetsByLinksIdsString(typeId, oldLinkIds.toSet, deletedLinks.toSet, newTransaction = false)
    logger.info(s"Processing assets: ${typeId}, assets count: ${existingAssets.size}")
    logger.info(s"Deleted links count: ${deletedLinks.size}, new links count: ${addedLinksCount}")
    logger.info("Starting to process changes")
    val assetsGrouped = IterableOperation.groupByPropertyHashMap(existingAssets, (elem: PersistedLinearAsset) => elem.linkId)
    (existingAssets.size, assetsGrouped)
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
                                                     assetsGroup: mutable.HashMap[String, Set[PersistedLinearAsset]],
                                                     addAndDelete: Seq[RoadLinkChange],
                                                     changeSetForProjecting:Set[ReplaceInfoSet],
                                                     changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val operated: OperationStep = operatesChangesNewAndDelete(onlyNeededNewRoadLinks, assetsGroup, addAndDelete, changeSet)
    val (adjusted, changesetUpdate )= operatesChanges(typeId,onlyNeededNewRoadLinks, assetsGroup,changeSetForProjecting, changeSet)
    val finalChangSet = mergeChangeSets(Seq(Some(changesetUpdate))++Seq(operated.changeInfo)++Seq(Some(changeSet))).get

   /* LogUtils.time(logger, "Reporting removed") { // mieti tää
      finalChangSet.expiredAssetIds.map(asset => {
        val alreadyReported = changesForReport.asScala.iterator.toList.map(_.before).filter(_.nonEmpty).map(_.get.assetId)
        if (!alreadyReported.contains(asset)) {
          val expiringAsset = assetsGroup.values.toList.toSeq.filter(_.id == asset)
          reportAssetChanges(expiringAsset, None, addAndDelete.filterNot(isNew), emptyStep, Some(ChangeTypeReport.Deletion))
        }
      })
    }*/
    
    (adjusted++operated.assetsAfter, finalChangSet )
  }
  private def operatesChangesNewAndDelete(onlyNeededNewRoadLinks: Seq[RoadLink], assetsGroup: mutable.HashMap[String, Set[PersistedLinearAsset]], changes: Seq[RoadLinkChange], changeSet: ChangeSet) = {
    var percentageProcessed = 0
    val oldLinkdIds = changes.filter(_.oldLink.isDefined).map(_.oldLink.get.linkId)
    val assets = oldLinkdIds.flatMap(a3 => {
      Try(assetsGroup(a3)) match {
        case Success(value) => value.toSeq
        case Failure(_) => Seq()
      }
    })
    val projectedToNewLinks = changes.zipWithIndex.map(changeWithIndex => {
      val (change, index) = changeWithIndex
      percentageProcessed = LogUtils.logArrayProgress(logger, "Projecting assets to new links", changes.size, index, percentageProcessed)
      goThroughAddAndDeletion(assetsGroup, onlyNeededNewRoadLinks, changeSet, OperationStep(Seq(), Some(changeSet)), change)
    })

    val (after, changeInfoM) = LogUtils.time(logger, "Merging operation steps before adjustment") {
      mergeAfterAndChangeSets(projectedToNewLinks)
    }
    OperationStep(assetsAfter = after, changeInfo = changeInfoM, assetsBefore = assets) // tarkista tämä
  }
  private def operatesChanges(typeId: Int, onlyNeededNewRoadLinks: Seq[RoadLink], assetsGroup: mutable.HashMap[String, Set[PersistedLinearAsset]],
                              replaceInfos: Set[ReplaceInfoSet],
                              changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val initStep = OperationStep(Seq(), Some(changeSet))
    val operated = replaceInfos.size match {
      case a if a >= parallelizationThreshold =>
        val g = replaceInfos.grouped(groupSizeForParallelRun).toList.par
        val level = setParallelismLevel(g.size)
        LogUtils.time(logger, s"Projecting link, multithreaded") {
          new Parallel().operation(g, level) { tasks =>
            tasks.flatMap(a1 => a1.map(a2 => goThroughWholeLinks(typeId, onlyNeededNewRoadLinks, assetsGroup, changeSet, initStep, a2)))
          }.toList.toSet
        }
      case _ => LogUtils.time(logger, s"goThroughWholeLinks") {replaceInfos.map(a => goThroughWholeLinks(typeId, onlyNeededNewRoadLinks, assetsGroup, changeSet, initStep, a))}
    }
    val adjusted = operated.flatMap(_._1).toSeq
    val changeSetUpdated = mergeChangeSets(operated.map(_._2).toSeq)
    (adjusted, changeSetUpdated.get)
  }
  private def goThroughWholeLinks(typeId: Int, onlyNeededNewRoadLinks: Seq[RoadLink], assetsGroup: mutable.HashMap[ChangeId, Set[PersistedLinearAsset]]
                                  , changeSet: ChangeSet, initStep: OperationStep, a: ReplaceInfoSet): (Seq[PersistedLinearAsset], Option[ChangeSet]) = {
    val changes = a.allNeededReplaced
    val assets = changes.flatMap(a3 => {
      Try(assetsGroup(a3.infos.oldLinkId.getOrElse(""))) match {
        case Success(value) => value.toSeq
        case Failure(_) => Seq()
      }
    })

    val projected = changes.groupBy(_.sourceChangeType).map(a => {
      handleReplaceInfo(onlyNeededNewRoadLinks, changeSet, initStep, assets, a)
    }).toSet.filter(_.isDefined)


     if (projected.nonEmpty) {
       val operation = projected.foldLeft(Some(initStep))(mergerOperations)
       
       val finalOperation = OperationStep(assetsAfter = operation.get.assetsAfter, changeInfo = operation.get.changeInfo, assetsBefore = assets.toSeq)
       val changesFromMap = changes.map(a => findChange(a.sourceId).infos)
       val adjusted = adjustAssets(typeId, onlyNeededNewRoadLinks, additionalOperations(finalOperation, changesFromMap.toSeq).get)
       reportingAdjusted(adjusted, changesFromMap.toSeq)
     }else (Seq(),Some(changeSet))
  }
  private def handleReplaceInfo(onlyNeededNewRoadLinks: Seq[RoadLink], 
                                changeSet: ChangeSet, initStep: OperationStep, 
                                assets: Set[PersistedLinearAsset], a: (RoadLinkChangeType, Set[ReplaceInfosWithHash])) = {
    val replaceInfos = a._2
    val oldLinks = replaceInfos.map(_.infos.oldLinkId.getOrElse(""))
    val assetsForProjecting = assets.filter(a=>oldLinks.contains(a.linkId))
    if (assetsForProjecting.nonEmpty){
      a._1 match {
        case RoadLinkChangeType.Replace => try {
          handleReplacements(changeSet, initStep, replaceInfos, assetsForProjecting.toSeq, onlyNeededNewRoadLinks)
        } catch {
          case _: FailedToFindReplaceInfo => None
          case e: Throwable => logErrorAndReturnNone(findChange(replaceInfos.head.sourceId).infos, assets.toSeq, e);
            throw e
        }
        case RoadLinkChangeType.Split => try {
          operationForSplit(replaceInfos, initStep, assetsForProjecting.toSeq, changeSet, onlyNeededNewRoadLinks)
        } catch {
          case _: FailedToFindReplaceInfo => None
          case e: Throwable => logErrorAndReturnNone(findChange(replaceInfos.head.sourceId).infos, assets.toSeq, e);
            throw e
        }
        case _ => None
      }
    }else None
  }
  private def operationForSplit(change:  Set[ReplaceInfosWithHash], initStep: OperationStep, relevantAssets: Seq[PersistedLinearAsset], changeSet: ChangeSet,
                                onlyNeededNewRoadLinks: Seq[RoadLink]): Option[OperationStep] = {
    def checkForExpire(splits: Seq[OperationStepSplit]): Boolean = {
      val newLinkIds = splits.map(_.newLinkId)
      val someRoadLinksFound = onlyNeededNewRoadLinks.map(_.linkId).exists(newLinkIds.contains)
      newLinkIds.forall(_ == "") || !someRoadLinksFound
    }
    val divided  = relevantAssets.flatMap(sliceFrom=>{
      
      val sliced = slicer(Seq(sliceFrom), Seq(), change)
      val splits = sliced.map(part => projectingSplit(changeSet, change, part, sliceFrom).get)
      if (checkForExpire(splits)) {
        val changes = change.map(a=>findChange(a.sourceId).infos)
        reportAssetChanges(Some(sliceFrom), None, changes.toSeq, emptyStep, Some(ChangeTypeReport.Deletion), useGivenChange = true)
        splits.map(split => updateSplitOperationWithExpiredIds(split, sliceFrom.id))
      } else {
        splits
      }
    })
    
    val splitOperation = divided.foldLeft(OperationStepSplit(changeInfo = initStep.changeInfo))(mergerOperationsSplit)
    Some(OperationStep(assetsAfter = splitOperation.assetsAfter, changeInfo = splitOperation.changeInfo, assetsBefore = Seq()))
  }

  private def goThroughAddAndDeletion(assetsAllG: mutable.HashMap[String, Set[PersistedLinearAsset]], onlyNeededNewRoadLinks: Seq[RoadLink], changeSets: ChangeSet,
                               initStep: OperationStep, change: RoadLinkChange): Option[OperationStep] = {
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
        case _ => None
      }
    }
  }
  private def logErrorAndReturnNone(change: RoadLinkChange, assets: Seq[PersistedLinearAsset], e: Throwable) = {
    logger.error(s"Samuutus failled with RoadlinkChange: $change", e)
    assets.foreach(a => logger.error(s"Samuutus failled for asset ${a.id} with start measure ${a.startMeasure} and end measure ${a.endMeasure} on link ${a.linkId}"))
    None
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
    val (adjusted, changeSetUpdated) = LogUtils.time(logger, "Run fillTopology") {
      val assets = groupedAssets.flatMap(_._2.assets).toSeq.groupBy(_.linkId)
      val roadLinks = groupedAssets.map(_._2.link).toList
      adjustLinearAssets(typeId, roadLinks, assets,changeSetFromOperation)
    }
    operationStep.copy(
      assetsAfter = adjusted.map(convertToPersisted),
      changeInfo = Some(changeSetUpdated)
    )
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

  private def handleReplacements(changeSets: ChangeSet, initStep: OperationStep, replaceInfos: Set[ReplaceInfosWithHash], assets: Seq[PersistedLinearAsset], onlyNeededNewRoadLinks: Seq[RoadLink]): Option[OperationStep] = {
    val roadLinkInfo = findChange(replaceInfos.head.sourceId).infos.newLinks.head
    val assetInInvalidLink = !onlyNeededNewRoadLinks.exists(_.linkId == roadLinkInfo.linkId)
    if(assetInInvalidLink) { // assets is now in invalid link, expire
      assets.map(a => {
        val expireStep = OperationStep(Seq(), Some(changeSets.copy(expiredAssetIds = changeSets.expiredAssetIds ++ Set(a.id))), assetsBefore = Seq())
        Some(reportAssetChanges(Some(a), None, Seq(findChange(replaceInfos.head.sourceId).infos), expireStep, Some(ChangeTypeReport.Deletion),useGivenChange = true))
      }).foldLeft(Some(initStep))(mergerOperations)
    } else {
      val projected = assets.map(a => projecting(changeSets, replaceInfos, a, a))
      LogUtils.time(logger, "Merging steps after projecting") {
        projected.filter(_.nonEmpty).foldLeft(Some(initStep))(mergerOperations)
      }
    }
  }
  
  def slice(asset: PersistedLinearAsset,replaceInfo:  Set[ReplaceInfosWithHash]): Seq[PersistedLinearAsset] = {
    val selectInfoOpt = sortAndFind(replaceInfo.toSeq, asset, fallInWhenSlicing).getOrElse(throw FailedToFindReplaceInfo(errorMessage(findChange(replaceInfo.head.sourceId).infos, asset)))
    val selectInfo = selectInfoOpt.infos
    val (shortedFilter, newPartFilter) = if (fallInWhenSlicing(selectInfoOpt, asset)) {
      val shorted = asset.copy(endMeasure = selectInfo.oldToMValue.getOrElse(0.0))
      val oldId = if (asset.id != 0) asset.id else asset.oldId
      val newPart = asset.copy(id = 0, startMeasure = selectInfo.oldToMValue.getOrElse(0.0), oldId = oldId)

      val shortedLength = shorted.endMeasure - shorted.startMeasure
      val newPartLength = newPart.endMeasure - newPart.startMeasure

      val shortedFilter = if (shortedLength > 0) Option(shorted) else None

      val newPartFilter = if (newPartLength > 0) Option(newPart) else None

      println(s"asset old part start ${shorted.startMeasure}, asset end ${shorted.endMeasure}")
      println(s"asset new part start ${newPart.startMeasure}, asset end ${newPart.endMeasure}")
      (shortedFilter, newPartFilter)
    } else (None, None)
    (shortedFilter, newPartFilter) match {
      case (None, None) => Seq()
      case (Some(shortedSome), None) => Seq(shortedSome)
      case (None, Some(newParSome)) => Seq(newParSome)
      case (Some(shortedSome), Some(newPartSome)) => Seq(shortedSome, newPartSome)
    }
  }

  def partitioner(asset: PersistedLinearAsset, replaceInfos: Set[ReplaceInfosWithHash]): Boolean = {
    def assetIsAlreadySlicedOrFitIn(asset: PersistedLinearAsset, selectInfo: ReplaceInfo): Boolean = {
      asset.endMeasure <= selectInfo.oldToMValue.getOrElse(0.0) && asset.startMeasure >= selectInfo.oldFromMValue.getOrElse(0.0)
    }

    val selectInfoOpt = sortAndFind(replaceInfos.toSeq, asset, fallInReplaceInfoOld)
    if (selectInfoOpt.nonEmpty) assetIsAlreadySlicedOrFitIn(asset, selectInfoOpt.get.infos)
    else false
  }
  @tailrec
  private def slicer(assets: Seq[PersistedLinearAsset], fitIntoRoadLinkPrevious: Seq[PersistedLinearAsset], replaceInfos:  Set[ReplaceInfosWithHash]): Seq[PersistedLinearAsset] = {
    
    val (fitIntoRoadLink, assetGoOver) = assets.partition(a=>partitioner(a,replaceInfos))
    println("assetGoOver: "+assetGoOver.size)
    if (assetGoOver.nonEmpty) {
      val sliced = assetGoOver.flatMap(a=>slice(a,replaceInfos))
      slicer(sliced, fitIntoRoadLink ++ fitIntoRoadLinkPrevious,replaceInfos)
    } else {
      fitIntoRoadLinkPrevious ++ fitIntoRoadLink
    }
  }

  /**
    * @param changeSets
    * @param replaceInfo
    * @param asset asset which will be projected
    * @param beforeAsset asset before projection
    * @return
    */
  private def projecting(changeSets: ChangeSet, replaceInfo: Set[ReplaceInfosWithHash], asset: PersistedLinearAsset, beforeAsset: PersistedLinearAsset) = {
    val (_, projected, changeSet) = projectByUsingReplaceInfo(changeSets,replaceInfo,asset)
    Some(OperationStep(Seq(projected), Some(changeSet), assetsBefore = Seq()))
  }
  /**
    * @param changeSets
    * @param replaceInfo
    * @param asset       asset which will be projected
    * @param beforeAsset asset before projection
    * @return
    */
  private def projectingSplit(changeSets:ChangeSet, replaceInfo: Set[ReplaceInfosWithHash], asset: PersistedLinearAsset, beforeAsset: PersistedLinearAsset) = {
    val (newId, projected, changeSet) = projectByUsingReplaceInfo(changeSets, replaceInfo, asset)
    Some(OperationStepSplit(Seq(projected), Some(changeSet),newLinkId = newId, assetsBefore = Seq()))
  }

  private def projectByUsingReplaceInfo(changeSets: ChangeSet, replaceInfo: Set[ReplaceInfosWithHash], asset: PersistedLinearAsset) = {
      val infoOpt = sortAndFind(replaceInfo.toSeq, asset, fallInReplaceInfoOld).getOrElse(// TODO tarkista tämä
        throw FailedToFindReplaceInfo(errorMessage(findChange(replaceInfo.head.sourceId).infos, asset)))
      val info = infoOpt.infos
      val newId = info.newLinkId.getOrElse("")
      val maybeLink = findChange(infoOpt.sourceId).infos.newLinks.find(_.linkId == newId)
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
   * @param expiredId set of expired asset Ids
   * @return Option[OperationStep] returns the updated OperationStep
   */
  private def updateSplitOperationWithExpiredIds(operationStep: OperationStepSplit, expiredId: Long): OperationStepSplit = {
    val updatedChangeSet = updateChangeSetWithExpiredId(operationStep.changeInfo, expiredId)
    operationStep.copy(newLinkId = "", changeInfo = updatedChangeSet, assetsAfter = Seq())
  }
}
