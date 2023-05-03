package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.ConstructionType.UnknownConstructionType
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetOperations, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinearAssetUtils}
import org.slf4j.LoggerFactory

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
case class CalculateMValueChangesInfo(assetId: Long, oldId: Option[String], newId: Option[String],
                                      oldLinksLength: Option[Double],
                                      newLinksLength: Option[Double],
                                      oldStart: Double, oldEnd: Double, newStart: Double, newEnd: Double,digitizationChange:Boolean
                                     ) {}

case class LinkAndLength(linkId: String, length: Double)

class LinearAssetUpdater(service: LinearAssetOperations) {

  def eventBus: DigiroadEventBus = new DummyEventBus
  def roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  def roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)
  def assetFiller: AssetFiller = new AssetFiller
  def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  val logger = LoggerFactory.getLogger(getClass)
  val roadLinkChangeClient = new RoadLinkChangeClient

  def updateLinearAssets(typeId: Int): Unit = {
    withDynTransaction {
      val latesSuccess = Queries.getLatestSuccessfulSamuutus(typeId)
      val changes = roadLinkChangeClient.getRoadLinkChanges(latesSuccess)
      updateByRoadLinks(typeId, changes)
    }
  }

  //TODO Remove after bug in combine and fuse operations is fixed
  def cleanRedundantMValueAdjustments(changeSet: ChangeSet, originalAssets: Seq[PieceWiseLinearAsset]): ChangeSet = {
    val redundantFiltered = changeSet.adjustedMValues.filterNot(adjustment => {
      val originalAsset = originalAssets.find(_.id == adjustment.assetId).get
      originalAsset.startMeasure == adjustment.startMeasure && originalAsset.endMeasure == adjustment.endMeasure
    })
    changeSet.copy(adjustedMValues = redundantFiltered)
  }

  protected val isDeleted: RoadLinkChange => Boolean = (change: RoadLinkChange) => {
    change.changeType.value == RoadLinkChangeType.Remove.value
  }

  protected val isDeletedOrNew: RoadLinkChange => Boolean = (change: RoadLinkChange) => {
    change.changeType.value == RoadLinkChangeType.Remove.value || change.changeType.value == RoadLinkChangeType.Add.value
  }

  private  def splitLinkId(linkId: String): (String, Int) = {
    val split = linkId.split(":")
    (split(0), split(1).toInt)
  }

  private def recognizeVersionUpgrade(change: RoadLinkChange): Boolean = {
    if (change.newLinks.size == 1)
      change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkLength == change.newLinks.head.linkLength && checkId(change)
    else false
  }
  private def checkId(change: RoadLinkChange): Boolean = {
    val oldId = splitLinkId(change.oldLink.get.linkId)._1
    val newId = splitLinkId(change.newLinks.head.linkId)._1
    if (oldId == newId) {
      true
    } else false
  }
  protected def toRoadLinkForFilltopology(roadLink: RoadLinkInfo): RoadLinkForFiltopology = {
    RoadLinkForFiltopology(linkId = roadLink.linkId, length = roadLink.linkLength, trafficDirection = roadLink.trafficDirection /*non override version */ , administrativeClass = roadLink.adminClass /*non override version */ ,
      linkSource = NormalLinkInterface, linkType = UnknownLinkType, constructionType = UnknownConstructionType, geometry = roadLink.geometry) // can there be link of different sourse ?
  }
  
  // TODO where shoud we do rounding,when creating or when updating?


  def filterChanges(changes: Seq[RoadLinkChange]): Seq[RoadLinkChange] = {
    changes
  }
  def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]): Unit = {
    val changes = filterChanges(changesAll)
    val oldIds = changes.filterNot(isDeletedOrNew).map(_.oldLink.get.linkId)
    val deletedLinks = changes.filter(isDeleted).map(_.oldLink.get.linkId)
    val existingAssets = service.fetchExistingAssetsByLinksIdsString(typeId, oldIds.toSet, deletedLinks.toSet, newTransaction = false)

    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
      expiredAssetIds = existingAssets.filter(asset => deletedLinks.contains(asset.linkId)).map(_.id).toSet.filterNot(_ == 0L),
      adjustedMValues = Seq.empty[MValueAdjustment],
      adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
      adjustedSideCodes = Seq.empty[SideCodeAdjustment],
      valueAdjustments = Seq.empty[ValueAdjustment])
    additionalRemoveOperationMass(deletedLinks)
    val (projectedAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(existingAssets, changes, initChangeSet)
    val convertedLink = changes.flatMap(_.newLinks.map(toRoadLinkForFilltopology))
    val groupedAssets = assetFiller.toLinearAssetsOnMultipleLinks(projectedAssets, convertedLink).groupBy(_.linkId)
    val adjusted = adjustLinearAssetsOnChangesGeometry(convertedLink, groupedAssets, typeId, Some(changedSet))
    persistProjectedLinearAssets(adjusted._1.map(convertToPersisted).filter(_.id == 0L))

  }
  
 private def isFullLinkLength(asset: PersistedLinearAsset, linkMeasure: Double): Boolean = {
    asset.startMeasure == 0 && asset.endMeasure == linkMeasure
  }

  def roundMeasure(measure: Double, numberOfDecimals: Int = 3): Double = {
    val exponentOfTen = Math.pow(10, numberOfDecimals)
    Math.round(measure * exponentOfTen).toDouble / exponentOfTen
  }

  private def sliceLoop(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    val linkWhichIsSplitted = change.oldLink.get.linkId
    val assets = assetsAll.filter(p => linkWhichIsSplitted.contains(p.linkId))

    val valuesAndSideCodesAreSame = assets.flatMap(_.value).toSet.size == 1 && assets.map(_.sideCode).toSet.size == 1
    val assetLengthStatus = assets.map(p2 => isFullLinkLength(p2, change.oldLink.get.linkLength)).toSet

    val allAreFullLinkLength = assetLengthStatus.size == 1 && assetLengthStatus.head
    if (valuesAndSideCodesAreSame && allAreFullLinkLength) {
      val assets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)
      val projected = change.replaceInfo.map(replaceInfo => { // slicing
        assets.head.copy(id = 0, linkId = replaceInfo.oldLinkId, startMeasure = replaceInfo.oldFromMValue, endMeasure = replaceInfo.oldToMValue)
      }).flatMap(asset => {
        val mapping = convertToForCalculation(change, asset)
        val findProjection = mapping.head
        Seq(projecting(changeSets, asset, findProjection))
      })
      val returnedChangeSet = foldChangeSet(projected.map(_._2), changeSets)
      val update = returnedChangeSet.copy(expiredAssetIds = returnedChangeSet.expiredAssetIds ++ assets.map(_.id))
      // optimize so that there is no nested seq , check in end
      projected.map(p => (p._1, update))
    } else {
      // partition asset which endMeasure is greater than oldFromMValue
      // slice these asset to end at oldFromMValue
      // create new part which has startMValue as oldFromMValue
      // shift these into next links
      // Repeat operation on next links

      val assets = assetsAll.filter(_.linkId == change.oldLink.get.linkId)

      def selectNearestReplaceInfo(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
        val condition = asset.linkId == replaceInfo.oldLinkId && asset.endMeasure >= replaceInfo.oldToMValue || asset.endMeasure <= replaceInfo.oldToMValue && asset.startMeasure >= replaceInfo.oldFromMValue
        if (!condition) condition else condition
      }

      def partitioner(asset: PersistedLinearAsset, change: RoadLinkChange): Boolean = {
        // order list by oldToMValue and start looking for right replace info by looking first highest number
        // and then checking lower number until correct one is found.
        val sortedInfo = change.replaceInfo.sortBy(_.oldToMValue).reverse
        val selectInfo = sortedInfo.find(selectNearestReplaceInfo(_, asset)).get
        val condition = asset.endMeasure >= selectInfo.oldToMValue && asset.startMeasure >= selectInfo.oldFromMValue
        if (condition) condition else condition
      }
      
      def slicer(assets: Seq[PersistedLinearAsset], change: RoadLinkChange, cycle: Int = 0): Seq[PersistedLinearAsset] = {
        val assetGoOver = assets.partition(partitioner(_, change))
        val sliced = assetGoOver._1.flatMap(a1 => {
          // order list by oldToMValue and start looking for right replace info by looking first highest number
          // and then checking lower number until correct one is found.
          val selectInfo = change.replaceInfo.sortBy(_.oldToMValue).reverse.find(r => a1.linkId == r.oldLinkId && a1.endMeasure >= r.oldToMValue && a1.startMeasure >= r.oldFromMValue).get
          val shorted = a1.copy(endMeasure = selectInfo.oldToMValue)
          val newPart = a1.copy(id = 0, startMeasure = selectInfo.oldToMValue)
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
        })
        sliced ++ assetGoOver._2.filterNot(a => sliced.map(_.id).toSet.contains(a.id))
      }

      val sliced: Seq[PersistedLinearAsset] = slicer(assets, change)

      sliced.flatMap(asset => {
        convertToForCalculation(change, asset).map(dataForCalculation => {
          projecting(changeSets, asset, (dataForCalculation))
        })
      })
    }
  }

  protected def fillNewRoadLinksWithPreviousAssetsData(assetsAll: Seq[PersistedLinearAsset], changes: Seq[RoadLinkChange], changeSets: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val result = changes.flatMap(change => {
      val result2 = change.changeType match {
        case RoadLinkChangeType.Add => operationForNewLink(change, assetsAll, changeSets) //TODO own add method  which can be override
        case RoadLinkChangeType.Remove => additionalRemoveOperation(change, assetsAll, changeSets)
        case _ => {
          val oldLink = change.oldLink.get
          val oldId = oldLink.linkId
          val assets = assetsAll.filter(_.linkId == oldId)
          change.changeType match {
            //  case RoadLinkChangeType.Replace if recognizeVersionUpgrade(change)  =>  Seq.empty[(PersistedLinearAsset, ChangeSet)] // TODO just update version
            case RoadLinkChangeType.Replace =>
              assets.flatMap(asset => {
                convertToForCalculation(change, asset).map(dataForCalculation => {
                  projecting(changeSets, asset, dataForCalculation)
                })
              })
            case RoadLinkChangeType.Split => sliceLoop(change, assetsAll, changeSets)
            case _ => Seq.empty[(PersistedLinearAsset, ChangeSet)]
          }
        }
      }
      
      val additionalChanges = additionalUpdateOrChange(change, result2.map(_._1), foldChangeSet(result2.map(_._2), changeSets))
      result2 ++ additionalChanges
    })
    
    (result.map(_._1), foldChangeSet(result.map(_._2), changeSets))
  }

  def recognizeMerger(change: (RoadLinkChangeType, Seq[RoadLinkChange])): Boolean = {
    //change._1 == RoadLinkChangeType.Replace && change._2.map(_.newLinks)
    true
  }
  
  def operationForNewLink(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }
  def additionalRemoveOperation(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }

  def additionalRemoveOperationMass(expiredLinks:Seq[String]): Unit ={
    
  }
  // TODO override with your needed additional logic
  def additionalUpdateOrChange(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    change.changeType match {
      case _ => Seq.empty[(PersistedLinearAsset, ChangeSet)]
    }
  }
  private def foldChangeSet(mergedChangeSet: Seq[ChangeSet], foldTo: ChangeSet): ChangeSet = {
    mergedChangeSet.foldLeft(foldTo) { (a, z) =>
      a.copy(
        droppedAssetIds     = a.droppedAssetIds ++ z.droppedAssetIds,
        expiredAssetIds     = (a.expiredAssetIds ++ z.expiredAssetIds),
        adjustedMValues     = (a.adjustedMValues ++ z.adjustedMValues).distinct,
        adjustedVVHChanges  = (a.adjustedVVHChanges ++ z.adjustedVVHChanges).distinct,
        adjustedSideCodes   = (a.adjustedSideCodes ++ z.adjustedSideCodes).distinct,
        valueAdjustments    = (a.valueAdjustments ++ z.valueAdjustments).distinct
      );
    }
  }

  private def projecting(changeSets: ChangeSet, asset: PersistedLinearAsset, info: CalculateMValueChangesInfo) = {
    projectLinearAsset(asset.copy(linkId = info.newId.get), LinkAndLength(info.newId.get, info.newLinksLength.get),
      Projection( 
        info.oldStart, info.oldEnd, info.newStart, info.newEnd,LinearAssetUtils.createTimeStamp()
      ), changeSets,info.digitizationChange)
  }
  private def selectCorrectReplaceInfo(replaceInfo: ReplaceInfo, asset: PersistedLinearAsset): Boolean = {
   replaceInfo.oldLinkId == asset.linkId && replaceInfo.oldFromMValue <= asset.startMeasure && replaceInfo.oldToMValue >= asset.endMeasure
  }

  private def convertToForCalculation(changes: RoadLinkChange, asset: PersistedLinearAsset) = {
    val info = changes.replaceInfo.find(selectCorrectReplaceInfo(_, asset)).getOrElse(throw new Exception("Did not found replace info for asset"))
    val link = changes.newLinks.find(_.linkId == info.newLinkId).get
    Some(CalculateMValueChangesInfo(
      asset.id,
      Some(info.oldLinkId), Some(info.newLinkId),
      Some(changes.oldLink.get.linkLength), Some(link.linkLength),
      info.oldFromMValue, info.oldToMValue,
      info.newFromMValue, info.newToMValue, info.digitizationChange
    ))
  }
  def adjustLinearAssetsOnChangesGeometry(roadLinks: Seq[RoadLinkForFiltopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]],
                                          typeId: Int, changeSet: Option[ChangeSet] = None, counter: Int = 1): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val asset = linearAssets.map(p => {
      val links = roadLinks.find(_.linkId == p._1).get
      val step0 = assetFiller.fuse(links, p._2, changeSet.get)
      val step1 = assetFiller.dropShortSegments(links, step0._1, step0._2)
      val step2 = assetFiller.adjustAssets(links, step1._1, step1._2)
      val step3 = assetFiller.expireOverlappingSegments(links, step2._1, step2._2)
      val step4 = droppedSegmentWrongDirection(links, step3._1, step3._2)
      val step5 = adjustSegmentSideCodes(links, step4._1, step4._2)

      // TODO Check for small 0.001 wholes fill theses
      step5._2.copy(adjustedMValues = step5._2.adjustedMValues.filterNot(p => step3._2.droppedAssetIds.contains(p.assetId)))
      step5
    }).toSeq

    val changeSetFolded = foldChangeSet(asset.map(_._2), changeSet.get)
    val assetOnly = asset.flatMap(_._1)

    updateChangeSet(changeSetFolded)
    (assetOnly, changeSetFolded)
  }
  
  protected def droppedSegmentWrongDirection(roadLink: RoadLinkForFiltopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
      (segments, changeSet)
    } else {
      val droppedAssetIds = (roadLink.trafficDirection match {
        case TrafficDirection.TowardsDigitizing => segments.filter(s => s.sideCode == SideCode.AgainstDigitizing)
        case _ => segments.filter(s => s.sideCode == SideCode.TowardsDigitizing)
      }).map(_.id)

      (segments.filterNot(s => droppedAssetIds.contains(s.id)), changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedAssetIds))
    }
  }
  protected def adjustSegmentSideCodes(roadLink: RoadLinkForFiltopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val oneWayTrafficDirection =
      (roadLink.trafficDirection == TrafficDirection.TowardsDigitizing) || (roadLink.trafficDirection == TrafficDirection.AgainstDigitizing)
    if (!oneWayTrafficDirection) {
      (segments, changeSet)
    } else {
      //val (twoSided, oneSided) = segments.filter(_.id!=0).partition { s => s.sideCode == SideCode.BothDirections }
      // this assume that asset on wrong side is already dropped
      val (generated,exist) = segments.partition(_.id==0)
      
      val adjusted = roadLink.trafficDirection match {
        case TrafficDirection.BothDirections => exist.map { s => (s.copy(sideCode = SideCode.BothDirections), SideCodeAdjustment(s.id, SideCode.BothDirections, s.typeId)) }
        case TrafficDirection.AgainstDigitizing => exist.map { s => (s.copy(sideCode = SideCode.AgainstDigitizing), SideCodeAdjustment(s.id, SideCode.AgainstDigitizing, s.typeId)) }
        case TrafficDirection.TowardsDigitizing => exist.map { s => (s.copy(sideCode = SideCode.TowardsDigitizing), SideCodeAdjustment(s.id, SideCode.TowardsDigitizing, s.typeId)) }
      }
      //val adjusted = oneSided.map { s => (s.copy(sideCode = SideCode.BothDirections), SideCodeAdjustment(s.id, SideCode.BothDirections, s.typeId)) }
      // TODO in old implementation side code there has not been correction when link changes to one direction, douple check do we want create it
      (generated++adjusted.map(_._1), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ adjusted.map(_._2)))
    }
  }
  
  protected def convertToPersisted(asset: PieceWiseLinearAsset): PersistedLinearAsset = {
    PersistedLinearAsset(asset.id, asset.linkId, asset.sideCode.value,
      asset.value, asset.startMeasure, asset.endMeasure, asset.createdBy,
      asset.createdDateTime, asset.modifiedBy, asset.modifiedDateTime, asset.expired, asset.typeId, asset.timeStamp,
      asset.geomModifiedDate, asset.linkSource, asset.verifiedBy, asset.verifiedDate, asset.informationSource)
  }
  
  def updateChangeSet(changeSet: ChangeSet): Unit = {
    dao.floatLinearAssets(changeSet.droppedAssetIds)
    // TODO there is possibility that one change overtake other. , merge these change
    if (changeSet.adjustedMValues.nonEmpty)
      println("Saving adjustments for asset/link ids=" + changeSet.adjustedMValues.map(a => "" + a.assetId + "/" + a.linkId + " startmeasure: " + a.startMeasure + " endmeasure: " + a.endMeasure).mkString(", "))
    changeSet.adjustedMValues.foreach { adjustment =>
      dao.updateMValuesChangeInfo(adjustment.assetId, adjustment.linkId, Measures(adjustment.startMeasure, adjustment.endMeasure).roundMeasures(), adjustment.timeStamp, AutoGeneratedUsername.generatedInUpdate)
    }
    
    val ids = changeSet.expiredAssetIds.toSeq
    
    if (ids.nonEmpty)
      println("Expiring ids " + ids.mkString(", "))
    ids.foreach(dao.updateExpiration(_, expired = true, AutoGeneratedUsername.generatedInUpdate))

    if (changeSet.adjustedSideCodes.nonEmpty)
      println("Saving SideCode adjustments for asset/link ids=" + changeSet.adjustedSideCodes.map(a => "" + a.assetId).mkString(", "))

    changeSet.adjustedSideCodes.foreach { adjustment =>
      adjustedSideCode(adjustment)
    }

    if (changeSet.valueAdjustments.nonEmpty)
      println("Saving value adjustments for assets: " + changeSet.valueAdjustments.map(a => "" + a.asset.id).mkString(", "))
    changeSet.valueAdjustments.foreach { adjustment =>
      service.updateWithoutTransaction(Seq(adjustment.asset.id), adjustment.asset.value.get, adjustment.asset.modifiedBy.get)
    }
  }

  def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
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

    println(s"new assets count: ${newLinearAssets.size}")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    println(s"insert assets count: ${toInsert.size}")
    println(s"update assets count: ${toUpdate.size}")
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
  
  def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
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

  def projectLinearAsset(asset: PersistedLinearAsset, to: LinkAndLength, projection: Projection, changedSet: ChangeSet,digitizationChanges:Boolean): (PersistedLinearAsset, ChangeSet) = {
    val newLinkId = to.linkId
    val assetId = asset.linkId match {
      case to.linkId => asset.id
      case _ => 0
    }
    val (newStart, newEnd, newSideCode) = MValueCalculator.calculateNewMValues(AssetLinearReference(asset.id, asset.startMeasure, asset.endMeasure, asset.sideCode), projection, to.length,digitizationChanges)

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
