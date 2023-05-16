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
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

/*val tableNames = Seq (
"lane_history_position", 
"lane_position",
"lane_work_list",

"lrm_position",
"lrm_position_history", 

"inaccurate_asset", ??

"unknown_speed_limit",
InaccurateAssetDAO, this table is created every time again each day so need to adjust


)*/
case class ReplaceInfoForAsset(assetId: Long, 
                               oldId: Option[String], newId: Option[String],
                               oldLinksLength: Option[Double],
                               newLinksLength: Option[Double],
                               oldStart: Double, oldEnd: Double,
                               newStart: Double, newEnd: Double, digitizationChange: Boolean
                              )
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

  def updateLinearAssets(typeId: Int): Unit = {
    withDynTransaction {
      val latestSuccessful = Queries.getLatestSuccessfulSamuutus(typeId)
      val changes = roadLinkChangeClient.getRoadLinkChanges(latestSuccessful)
      updateByRoadLinks(typeId, changes)
      Queries.updateLatestSuccessfulSamuutus(typeId)
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
    *
    * @param change
    * @param asset
    * @param finder
    * @return
    */
  private def sortAndFind(change: RoadLinkChange, asset: PersistedLinearAsset, finder: (ReplaceInfo, PersistedLinearAsset) => Boolean): Option[ReplaceInfo] = {
    change.replaceInfo.sortBy(_.oldToMValue).reverse.find(finder(_, asset))
  }
  def fallInWhenSlicing(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
    asset.linkId == replaceInfo.oldLinkId && asset.endMeasure >= replaceInfo.oldToMValue && asset.startMeasure >= replaceInfo.oldFromMValue
  }

  def fallInReplaceInfo(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
    replaceInfo.oldLinkId == asset.linkId && replaceInfo.oldFromMValue <= asset.startMeasure && replaceInfo.oldToMValue >= asset.endMeasure
  }

  protected def toRoadLinkForFilltopology(roadLink: RoadLinkInfo)(trafficDirectionOverrideds: Seq[RoadLinkValue], adminClassOverrideds: Seq[RoadLinkValue], linkTypes: Seq[RoadLinkValue]): RoadLinkForFiltopology = {
    val overridedAdminClass = adminClassOverrideds.find(_.linkId == roadLink.linkId)
    val overridedDirection = trafficDirectionOverrideds.find(_.linkId == roadLink.linkId)
    val linkType = linkTypes.find(_.linkId == roadLink.linkId)

    val adminClass = if (overridedAdminClass.nonEmpty) AdministrativeClass.apply(overridedAdminClass.get.value.get) else roadLink.adminClass
    val trafficDirection = if (overridedDirection.nonEmpty) TrafficDirection.apply(overridedDirection.get.value) else roadLink.trafficDirection
    val linkTypeExtract = if (linkType.nonEmpty)  LinkType.apply(linkType.get.value.get) else UnknownLinkType

    RoadLinkForFiltopology(linkId = roadLink.linkId, length = roadLink.linkLength, trafficDirection = trafficDirection, administrativeClass = adminClass,
      linkSource = NormalLinkInterface, linkType = linkTypeExtract, constructionType = UnknownConstructionType, geometry = roadLink.geometry, municipalityCode = roadLink.municipality)
  }
  protected def convertToPersisted(asset: PieceWiseLinearAsset): PersistedLinearAsset = {
    PersistedLinearAsset(asset.id, asset.linkId, asset.sideCode.value,
      asset.value, asset.startMeasure, asset.endMeasure, asset.createdBy,
      asset.createdDateTime, asset.modifiedBy, asset.modifiedDateTime, asset.expired, asset.typeId, asset.timeStamp,
      asset.geomModifiedDate, asset.linkSource, asset.verifiedBy, asset.verifiedDate, asset.informationSource)
  }

  
  def filterChanges(changes: Seq[RoadLinkChange]): Seq[RoadLinkChange] = {
    changes
  }

  def operationForNewLink(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }
  def additionalRemoveOperation(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }

  def additionalRemoveOperationMass(expiredLinks: Seq[String]): Unit = {}

  def additionalUpdateOrChange(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    change.changeType match {
      case _ => Seq.empty[(PersistedLinearAsset, ChangeSet)]
    }
  }

  protected def foldChangeSet(mergedChangeSet: Seq[ChangeSet], foldTo: ChangeSet): ChangeSet = {
    mergedChangeSet.foldLeft(foldTo) { (a, z) =>
      a.copy(
        droppedAssetIds = a.droppedAssetIds ++ z.droppedAssetIds,
        expiredAssetIds = (a.expiredAssetIds ++ z.expiredAssetIds),
        adjustedMValues = (a.adjustedMValues ++ z.adjustedMValues).distinct,
        adjustedVVHChanges = (a.adjustedVVHChanges ++ z.adjustedVVHChanges).distinct,
        adjustedSideCodes = (a.adjustedSideCodes ++ z.adjustedSideCodes).distinct,
        valueAdjustments = (a.valueAdjustments ++ z.valueAdjustments).distinct
      );
    }
  }

  def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]): Unit = {
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

    val (projectedAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(existingAssets, changes, initChangeSet)
    val convertedLink = changes.flatMap(_.newLinks.map(toRoadLinkForFilltopology(_)(overridedAdmin, overridedTrafficDirection, linkTypes)))
    val groupedAssets = assetFiller.toLinearAssetsOnMultipleLinks(projectedAssets, convertedLink).groupBy(_.linkId)
    val adjusted = adjustLinearAssetsOnChangesGeometry(convertedLink, groupedAssets, typeId, Some(changedSet))
    persistProjectedLinearAssets(adjusted._1.map(convertToPersisted).filter(_.id == 0L))

  }

  private def mapChangeInfoToAsset(changes: RoadLinkChange, asset: PersistedLinearAsset) = {
    val info = sortAndFind(changes, asset, fallInReplaceInfo).getOrElse(throw new Exception("Did not found replace info for asset"))
    val link = changes.newLinks.find(_.linkId == info.newLinkId).get
    ReplaceInfoForAsset(
      asset.id,
      Some(info.oldLinkId), Some(info.newLinkId),
      Some(changes.oldLink.get.linkLength), Some(link.linkLength),
      info.oldFromMValue, info.oldToMValue,
      info.newFromMValue, info.newToMValue, info.digitizationChange
    )
  }

  protected def fillNewRoadLinksWithPreviousAssetsData(assetsAll: Seq[PersistedLinearAsset], changes: Seq[RoadLinkChange], changeSets: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val finalResult = changes.flatMap(change => {
      val defaultOperationResult = change.changeType match {
        case RoadLinkChangeType.Add => operationForNewLink(change, assetsAll, changeSets)
        case RoadLinkChangeType.Remove => additionalRemoveOperation(change, assetsAll, changeSets)
        case _ =>
          val assets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
          change.changeType match {
            case RoadLinkChangeType.Replace => assets.map(projecting(changeSets, _, change))
            case RoadLinkChangeType.Split => sliceLoop(change, assets, changeSets)
            case _ => Seq.empty[(PersistedLinearAsset, ChangeSet)]
          }
      }

      val additionalChanges = additionalUpdateOrChange(change, defaultOperationResult.map(_._1), foldChangeSet(defaultOperationResult.map(_._2), changeSets))
      
      if (additionalChanges.nonEmpty ) additionalChanges else defaultOperationResult
      
    })

    (finalResult.map(_._1), foldChangeSet(finalResult.map(_._2), changeSets))
  }

  private def sliceLoop(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    val assets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
    val sliced: Seq[PersistedLinearAsset] = slicer(assets, Seq(), change)
    sliced.map(projecting(changeSets, _, change))
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

      logger.debug(s"asset old start ${shorted.startMeasure}, asset end ${shorted.endMeasure}")
      logger.debug(s"asset new start ${newPart.startMeasure} asset end ${newPart.endMeasure}")

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

  private def projecting(changeSets: ChangeSet, asset: PersistedLinearAsset, change: RoadLinkChange): (PersistedLinearAsset, ChangeSet) = {
    val info = mapChangeInfoToAsset(change, asset)
    projectLinearAsset(asset.copy(linkId = info.newId.get),
      Projection(
        info.oldStart, info.oldEnd,
        info.newStart, info.newEnd,
        LinearAssetUtils.createTimeStamp(), info.newId.get, info.newLinksLength.get)
      , changeSets, info.digitizationChange)
  }

  protected def adjustLinearAssetsOnChangesGeometry(roadLinks: Seq[RoadLinkForFiltopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]],
                                                    typeId: Int, changeSet: Option[ChangeSet] = None, counter: Int = 1): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (assetOnly, filterExpiredAway) = assetFiller.fillTopologyChangesGeometry(roadLinks, linearAssets, typeId, changeSet)
    updateChangeSet(filterExpiredAway)
    (assetOnly, filterExpiredAway)
  }

  def updateChangeSet(changeSet: ChangeSet): Unit = {
    dao.floatLinearAssets(changeSet.droppedAssetIds)

    if (changeSet.adjustedMValues.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedMValues.map(a => "" + a.assetId + "/" + a.linkId + " startmeasure: " + a.startMeasure + " endmeasure: " + a.endMeasure).mkString(", "))
    changeSet.adjustedMValues.foreach { adjustment =>
      dao.updateMValuesChangeInfo(adjustment.assetId, adjustment.linkId, Measures(adjustment.startMeasure, adjustment.endMeasure).roundMeasures(), adjustment.timeStamp, AutoGeneratedUsername.generatedInUpdate)
    }

    val ids = changeSet.expiredAssetIds.toSeq

    if (ids.nonEmpty)
      logger.info("Expiring ids " + ids.mkString(", "))
    ids.foreach(dao.updateExpiration(_, expired = true, AutoGeneratedUsername.generatedInUpdate))

    if (changeSet.adjustedSideCodes.nonEmpty)
      logger.info("Saving SideCode adjustments for asset/link ids=" + changeSet.adjustedSideCodes.map(a => "" + a.assetId).mkString(", "))

    changeSet.adjustedSideCodes.foreach { adjustment =>
      adjustedSideCode(adjustment)
    }

    if (changeSet.valueAdjustments.nonEmpty)
      logger.info("Saving value adjustments for assets: " + changeSet.valueAdjustments.map(a => "" + a.asset.id).mkString(", "))
    changeSet.valueAdjustments.foreach { adjustment =>
      service.updateWithoutTransaction(Seq(adjustment.asset.id), adjustment.asset.value.get, adjustment.asset.modifiedBy.get)
    }
  }

  protected def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected linear assets")

    def getValuePropertyId(value: Option[Value], typeId: Int) = {
      value match {
        case Some(NumericValue(intValue)) =>
          LinearAssetTypes.numericValuePropertyId
        case Some(TextualValue(textValue)) =>
          LinearAssetTypes.getValuePropertyId(typeId)
        case _ => ""
      }
    }

    logger.debug(s"new assets count: ${newLinearAssets.size}")

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
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
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
      logger.info("Added assets for linkids " + newLinearAssets.map(_.linkId))
  }

  protected def updateProjected(toUpdate: Seq[PersistedLinearAsset], persisted: Map[Long, Seq[PersistedLinearAsset]]) = {
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
      .getOrElse(throw new IllegalStateException("Old asset " + adjustment.assetId + " of type " + adjustment.typeId + " no longer available"))

    val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(oldAsset.linkId, newTransaction = false)
      .getOrElse(throw new IllegalStateException("Road link " + oldAsset.linkId + " no longer available"))

    service.expireAsset(oldAsset.typeId, oldAsset.id, AutoGeneratedUsername.generatedInUpdate, expired = true, newTransaction = false)

    val oldAssetValue = oldAsset.value.getOrElse(throw new IllegalStateException("Value of the old asset " + oldAsset.id + " of type " + oldAsset.typeId + " is not available"))

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
