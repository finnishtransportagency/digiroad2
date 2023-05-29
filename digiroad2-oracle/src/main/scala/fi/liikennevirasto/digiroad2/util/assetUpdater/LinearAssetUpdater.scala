package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.ConstructionType.UnknownConstructionType
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.{AdministrativeClassDao, LinkTypeDao, TrafficDirectionDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadLinkValue}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetOperations, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinearAssetUtils}
import org.json4s.jackson.compactJson
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
case class OperationStep(assets:Seq[PersistedLinearAsset]=Seq(),changeInfo:Option[ChangeSet] = None)

//TODO remove updater call from service level

/**
  *  override  [[filterChanges]], [[operationForNewLink]], [[additionalRemoveOperation]], [[additionalRemoveOperationMass]] and [[additionalUpdateOrChange]]
  *  with your needed additional logic
  * @param service
  */
class LinearAssetUpdater(service: LinearAssetOperations) {

  def eventBus: DigiroadEventBus = new DummyEventBus
  def roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  def roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)
  def assetFiller: AssetFiller = service.assetFiller
  def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  val logger = LoggerFactory.getLogger(getClass)
  val roadLinkChangeClient = new RoadLinkChangeClient
  
  val changesForReport:mutable.ListBuffer[ChangedAsset] = ListBuffer() 
  
  protected val removePart: Int = -1 

  def updateLinearAssets(typeId: Int): Unit = {
    withDynTransaction {
      val latestSuccessful = Queries.getLatestSuccessfulSamuutus(typeId)
      val changes = roadLinkChangeClient.getRoadLinkChanges(latestSuccessful)
      Queries.updateLatestSuccessfulSamuutus(typeId)
      generateAndSaveReport(typeId)
    }
  }

  protected val isDeleted: RoadLinkChange => Boolean = (change: RoadLinkChange) => {
    change.changeType.value == RoadLinkChangeType.Remove.value
  }

  protected val isDeletedOrNew: RoadLinkChange => Boolean = (change: RoadLinkChange) => {
    change.changeType.value == RoadLinkChangeType.Remove.value || change.changeType.value == RoadLinkChangeType.Add.value
  }

  /**
    * order list by oldToMValue and start looking for right replace info by looking first highest number
    * and then checking lower number until correct one is found.
    */
  private def sortAndFind(change: RoadLinkChange, asset: PersistedLinearAsset, finder: (ReplaceInfo, PersistedLinearAsset) => Boolean): Option[ReplaceInfo] = {
    change.replaceInfo.sortBy(_.oldToMValue).reverse.find(finder(_, asset))
  }
  def fallInWhenSlicing(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
    asset.linkId == replaceInfo.oldLinkId && asset.endMeasure >= replaceInfo.oldToMValue && asset.startMeasure >= replaceInfo.oldFromMValue
  }

  def fallInReplaceInfo(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
    if (replaceInfo.digitizationChange && replaceInfo.oldFromMValue> replaceInfo.oldToMValue) {
      replaceInfo.oldLinkId == asset.linkId && replaceInfo.oldFromMValue >= asset.startMeasure && replaceInfo.oldToMValue <= asset.endMeasure
    } else {
      replaceInfo.oldLinkId == asset.linkId && replaceInfo.oldFromMValue <= asset.startMeasure && replaceInfo.oldToMValue >= asset.endMeasure
    }
    
  }

  protected def toRoadLinkForFillTopology(roadLink: RoadLinkInfo)(trafficDirectionOverrideds: Seq[RoadLinkValue], adminClassOverrideds: Seq[RoadLinkValue], linkTypes: Seq[RoadLinkValue]): RoadLinkForFillTopology = {
    val overridedAdminClass = adminClassOverrideds.find(_.linkId == roadLink.linkId)
    val overridedDirection = trafficDirectionOverrideds.find(_.linkId == roadLink.linkId)
    val linkType = linkTypes.find(_.linkId == roadLink.linkId)

    val adminClass = if (overridedAdminClass.nonEmpty) AdministrativeClass.apply(overridedAdminClass.get.value.get) else roadLink.adminClass
    val trafficDirection = if (overridedDirection.nonEmpty) TrafficDirection.apply(overridedDirection.get.value) else roadLink.trafficDirection
    val linkTypeExtract = if (linkType.nonEmpty)  LinkType.apply(linkType.get.value.get) else UnknownLinkType

    RoadLinkForFillTopology(linkId = roadLink.linkId, length = roadLink.linkLength, trafficDirection = trafficDirection, administrativeClass = adminClass,
      linkSource = NormalLinkInterface, linkType = linkTypeExtract, constructionType = UnknownConstructionType, geometry = roadLink.geometry, municipalityCode = roadLink.municipality)
  }
  protected def convertToPersisted(asset: PieceWiseLinearAsset): PersistedLinearAsset = {
    PersistedLinearAsset(asset.id, asset.linkId, asset.sideCode.value,
      asset.value, asset.startMeasure, asset.endMeasure, asset.createdBy,
      asset.createdDateTime, asset.modifiedBy, asset.modifiedDateTime, asset.expired, asset.typeId, asset.timeStamp,
      asset.geomModifiedDate, asset.linkSource, asset.verifiedBy, asset.verifiedDate, asset.informationSource)
  }
  
  def reportAssetChanges(oldAssets: Option[PersistedLinearAsset], roadLinkChanges: Seq[RoadLinkChange],operationSteps:OperationStep, rowType:ChangeType): OperationStep = {
    val newAssets = operationSteps.assets

    if (oldAssets.isEmpty && newAssets.isEmpty)
      return operationSteps
    val linkId = if (oldAssets.nonEmpty) oldAssets.get.linkId else newAssets.head.linkId
    val assetId = if (oldAssets.nonEmpty) oldAssets.get.id else 0

    val relevantRoadLinkChange = roadLinkChanges.find(change => {
      val oldLinkId = change.oldLink match {
        case Some(oldLink) => Some(oldLink.linkId)
        case None => None
      }
      val assetInOldLinkId = oldAssets match {
        case Some(asset) => Some(asset.linkId)
        case None => None
      }
      val newLinkIds = change.newLinks.map(_.linkId).sorted
      val assetsInNewLinkIds = newAssets.map(_.linkId).sorted

      ((oldLinkId.nonEmpty && assetInOldLinkId.nonEmpty) && oldLinkId == assetInOldLinkId) || newLinkIds == assetsInNewLinkIds
    }).get

    val before = oldAssets match {
      case Some(ol) =>
        val values = compactJson(ol.toJson)
        val linkOld = relevantRoadLinkChange.oldLink.get
        val assetGeometry = GeometryUtils.truncateGeometry3D(linkOld.geometry, ol.startMeasure, ol.endMeasure)
        val measures = Measures(ol.startMeasure, ol.endMeasure).roundMeasures()
        val linearReference = LinearReference(ol.linkId, measures.startMeasure, Some(measures.endMeasure), Some(ol.sideCode), None, measures.length())
        Some(Asset(ol.id, values, Some(linkOld.municipality), Some(assetGeometry), Some(linearReference)))
      case None => None
    }
    
    val after = newAssets.map(asset => {
      val newLink = relevantRoadLinkChange.newLinks.find(_.linkId == asset.linkId).get
      val values = compactJson(asset.toJson)
      val assetGeometry = GeometryUtils.truncateGeometry3D(newLink.geometry, asset.startMeasure, asset.endMeasure)
      val measures = Measures(asset.startMeasure, asset.endMeasure).roundMeasures()
      val linearReference = LinearReference(asset.linkId, measures.startMeasure, Some(measures.endMeasure), Some(asset.sideCode), None, measures.length())
      Asset(asset.id, values, Some(newLink.municipality), Some(assetGeometry), Some(linearReference))
    })
    
    changesForReport.append(ChangedAsset(linkId, assetId, rowType, relevantRoadLinkChange.changeType, before, after))
    operationSteps
  }

  def generateAndSaveReport(typeId:Int): Unit = {
    val changeReport = ChangeReport(typeId, changesForReport)
    val (reportBody, contentRowCount) = ChangeReporter.generateCSV(changeReport)
    ChangeReporter.saveReportToLocalFile(AssetTypeInfo(changeReport.assetType).label, reportBody, contentRowCount)
    val (reportBodyWithGeom, _) = ChangeReporter.generateCSV(changeReport, withGeometry = true)
    ChangeReporter.saveReportToLocalFile(AssetTypeInfo(changeReport.assetType).label, reportBodyWithGeom, contentRowCount, hasGeometry = true)
  }
  
  def filterChanges(changes: Seq[RoadLinkChange]): Seq[RoadLinkChange] = {
    changes
  }

  def operationForNewLink(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = {
    None
  }
  def additionalRemoveOperation(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = {
  None
  }

  def additionalRemoveOperationMass(expiredLinks: Seq[String]): Unit = {}

  def additionalUpdateOrChange(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = {
    change.changeType match {
      case _ => None
    }
  }

  def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]):Unit= {
    val changes = filterChanges(changesAll)
    val oldIds = changes.filterNot(isDeletedOrNew).map(_.oldLink.get.linkId)
    val newIds = changes.filterNot(isDeletedOrNew).flatMap(_.newLinks)
    val deletedLinks = changes.filter(isDeleted).map(_.oldLink.get.linkId)
    // here we assume that RoadLinkProperties updater has already remove override if KMTK version traffic direction is same.
    // still valid overrided has also been samuuted
    val overridedAdmin = AdministrativeClassDao.getExistingValues(newIds.map(_.linkId))
    val overridedTrafficDirection = TrafficDirectionDao.getExistingValues(newIds.map(_.linkId))
    val linkTypes = LinkTypeDao.getExistingValues(newIds.map(_.linkId))

    val existingAssets = service.fetchExistingAssetsByLinksIdsString(typeId, oldIds.toSet, deletedLinks.toSet, newTransaction = false)

    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
      expiredAssetIds = existingAssets.filter(asset => deletedLinks.contains(asset.linkId)).map(_.id).toSet.filterNot(_ == 0L),
      adjustedMValues = Seq.empty[MValueAdjustment],
      adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
      adjustedSideCodes = Seq.empty[SideCodeAdjustment],
      valueAdjustments = Seq.empty[ValueAdjustment])

    additionalRemoveOperationMass(deletedLinks)
    val convertedLink = changes.flatMap(_.newLinks.map(toRoadLinkForFillTopology(_)(overridedAdmin, overridedTrafficDirection, linkTypes)))

    val (projectedAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(typeId)(convertedLink)(existingAssets, changes, initChangeSet)
    val persistedAssets = projectedAssets.map(convertToPersisted)
    
    updateChangeSet(changedSet)
    persistProjectedLinearAssets(persistedAssets.filter(_.id == 0L))
  }

  protected def fillNewRoadLinksWithPreviousAssetsData(typeId: Int)(convertedLink: Seq[RoadLinkForFillTopology])(assetsAll: Seq[PersistedLinearAsset], changes: Seq[RoadLinkChange], changeSets: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val mergeTarget = Some(OperationStep(Seq(), Some(changeSets)))
    val finalResult2 = changes.map(change => {
      val defaultOperationResult = change.changeType match {
        case RoadLinkChangeType.Add => 
          val operation = operationForNewLink(change, assetsAll, changeSets)
          Some(reportAssetChanges(None, Seq(change), operation.getOrElse(OperationStep(Seq(), Some(changeSets))),ChangeTypeReport.Creation))
        case RoadLinkChangeType.Remove => additionalRemoveOperation(change, assetsAll, changeSets)
        case _ =>
          val assets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
          change.changeType match {
            case RoadLinkChangeType.Replace =>
              val operation = assets.map(projecting(changeSets, change, _)).
                filter(_.nonEmpty).
                foldLeft(Some(OperationStep(Seq(), Some(changeSets))))(mergerOperations)
              Some(reportAssetChanges(Some(assets.head), Seq(change), operation.get,ChangeTypeReport.Replaced))
            case RoadLinkChangeType.Split =>
              val operation =  operationForSplit(change, assets, changeSets)
              Some(reportAssetChanges(Some(assets.head), Seq(change), operation.get,ChangeTypeReport.Divided))
            case _ => None
          }
      }
      
      val additionalChanges = if (defaultOperationResult.nonEmpty) {
        additionalUpdateOrChange(change, defaultOperationResult.get.assets, defaultOperationResult.get.changeInfo.getOrElse(LinearAssetFiller.emptyChangeSet))
      } else None
      if (additionalChanges.nonEmpty) additionalChanges else defaultOperationResult
    })
    
    val finalResult = finalResult2.filter(_.nonEmpty).foldLeft(mergeTarget)(mergerOperations).get

    val projectedAssets = finalResult.assets.filterNot(_.id == removePart)
    val groupedAssets = assetFiller.toLinearAssetsOnMultipleLinks(projectedAssets, convertedLink).groupBy(_.linkId)
    val (adjusted, changeSet) = adjustLinearAssetsOnChangesGeometry(convertedLink, groupedAssets, typeId, finalResult.changeInfo)
    
    (adjusted, changeSet)
  }
  private def projecting(changeSets: ChangeSet, change: RoadLinkChange, asset: PersistedLinearAsset):Some[OperationStep] = {
    
    val info = sortAndFind(change, asset, fallInReplaceInfo).getOrElse(throw new Exception("Did not found replace info for asset"))
    val link = change.newLinks.find(_.linkId == info.newLinkId).get
    
    val (projected, changeSet) = projectLinearAsset(asset.copy(linkId = info.newLinkId),
      Projection(
        info.oldFromMValue, info.oldToMValue,
        info.newFromMValue, info.newToMValue,
        LinearAssetUtils.createTimeStamp(), info.newLinkId, link.linkLength)
      , changeSets, info.digitizationChange)
    Some(OperationStep(Seq(projected), Some(changeSet)))
  }
  private def mergerOperations(a: Option[OperationStep], b: Option[OperationStep]): Some[OperationStep] = {
    val (assetsA, changeInfoA) = (a.get.assets, a.get.changeInfo)
    val (assetsB, changeInfoB) = (b.get.assets, b.get.changeInfo)
    Some(OperationStep(assetsA ++ assetsB, Some(LinearAssetFiller.combineChangeSets(changeInfoA.get, changeInfoB.get))))
  }

  private def operationForSplit(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Option[OperationStep] = {
    val assets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
    val sliced: Seq[PersistedLinearAsset] = slicer(assets, Seq(), change)
    sliced.map(projecting(changeSets, change, _)).foldLeft(Some(OperationStep(Seq(), Some(changeSets))))(mergerOperations)
  }
 
  @tailrec
  private def slicer(assets: Seq[PersistedLinearAsset], fitIntRoadLinkPrevious: Seq[PersistedLinearAsset], change: RoadLinkChange): Seq[PersistedLinearAsset] = {
    def slice(change: RoadLinkChange, asset: PersistedLinearAsset): Seq[PersistedLinearAsset] = {
      val selectInfo = sortAndFind(change, asset, fallInWhenSlicing).get
      val shorted = asset.copy(endMeasure = selectInfo.oldToMValue)
      val newPart = asset.copy(id = 0, startMeasure = selectInfo.oldToMValue)
      val shortedLength = shorted.endMeasure - shorted.startMeasure
      val newPartLength = newPart.endMeasure - newPart.startMeasure

      val shortedFilter = if (shortedLength > 0) {
        Option(shorted)
      } else None

      val newPartFilter = if (newPartLength > 0) {
        Option(newPart)
      } else None

      logger.debug(s"asset old part start ${shorted.startMeasure}, asset end ${shorted.endMeasure}")
      logger.debug(s"asset new part start ${newPart.startMeasure} asset end ${newPart.endMeasure}")

      (shortedFilter, newPartFilter) match {
        case (None, None) => Seq()
        case (Some(shortedSome), None) => Seq(shortedSome)
        case (None, Some(newParSome)) => Seq(newParSome)
        case (Some(shortedSome), Some(newPartSome)) => Seq(shortedSome, newPartSome)
      }
    }

    def assetIsAlreadySlicedOrFitIn(asset: PersistedLinearAsset, selectInfo: ReplaceInfo): Boolean = {
      asset.endMeasure <= selectInfo.oldToMValue && asset.startMeasure >= selectInfo.oldFromMValue
    }

    def partitioner(asset: PersistedLinearAsset, change: RoadLinkChange): Boolean = {
      val selectInfoOpt = sortAndFind(change, asset, fallInReplaceInfo)
      if (selectInfoOpt.nonEmpty)
        assetIsAlreadySlicedOrFitIn(asset, selectInfoOpt.get)
      else false

    }

    val sliced = assets.flatMap(slice(change,_))
    val (fitIntoRoadLink, assetGoOver) = sliced.partition(partitioner(_, change))
    if (assetGoOver.nonEmpty) {
      slicer(assetGoOver, fitIntoRoadLink ++ fitIntRoadLinkPrevious, change)
    } else {
      fitIntRoadLinkPrevious ++ fitIntoRoadLink
    }
  }

  protected def adjustLinearAssetsOnChangesGeometry(roadLinks: Seq[RoadLinkForFillTopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]],
                                                    typeId: Int, changeSet: Option[ChangeSet] = None, counter: Int = 1): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    assetFiller.fillTopologyChangesGeometry(roadLinks, linearAssets, typeId, changeSet)
  }

  def updateChangeSet(changeSet: ChangeSet): Unit = {
    dao.floatLinearAssets(changeSet.droppedAssetIds)

    if (changeSet.adjustedMValues.nonEmpty)
      logger.info(s"Saving adjustments for asset/link ids=${changeSet.adjustedMValues.map(a => s"${a.assetId}/${a.linkId} startmeasure: ${a.startMeasure} endmeasure: ${a.endMeasure}").mkString(", ")}")
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
    
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
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

    val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(oldAsset.linkId, newTransaction = false)
      .getOrElse(throw new IllegalStateException(s"Road link ${oldAsset.linkId} no longer available"))

    service.expireAsset(oldAsset.typeId, oldAsset.id, AutoGeneratedUsername.generatedInUpdate, expired = true, newTransaction = false)

    val oldAssetValue = oldAsset.value.getOrElse(throw new IllegalStateException(s"Value of the old asset ${oldAsset.id} of type ${oldAsset.typeId} is not available"))

    service.createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, oldAssetValue, adjustment.sideCode.value,
      Measures(oldAsset.startMeasure, oldAsset.endMeasure).roundMeasures(), AutoGeneratedUsername.generatedInUpdate, LinearAssetUtils.createTimeStamp(),
      Some(roadLink), false, Some(AutoGeneratedUsername.generatedInUpdate), None, oldAsset.verifiedBy, oldAsset.informationSource.map(_.value))

  }

  protected def projectLinearAsset(asset: PersistedLinearAsset, projection: Projection, changedSet: ChangeSet, digitizationChanges: Boolean): (PersistedLinearAsset, ChangeSet) = {
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
      informationSource = asset.informationSource), changeSet)
  }
}
