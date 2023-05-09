package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.{AssetLinearReference, GeometryUtils, MValueCalculator, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeType}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.New
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType}
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.{AdministrativeClassDao, LinkTypeDao, TrafficDirectionDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimitFiller.{fillTopology, toRoadLinkForFiltopology}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils
import org.joda.time.DateTime

class SpeedLimitUpdater(service: SpeedLimitService) extends DynamicLinearAssetUpdater(service) {

  val speedLimitDao = service.speedLimitDao

  override def operationForNewLink(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    // TODO here logic to generate speedlimi road, it seems there is no such functionlity?
    // hint newPieceWiseChangeAsset

    //      newPieceWiseChangeAsset(roadLinks, speedLimits ++ existingSpeedLimit, changes) //Temporarily disabled according to DROTH-2327
    persistUnknown(Seq(UnknownSpeedLimit(change.newLinks.head.linkId, change.newLinks.head.municipality, change.newLinks.head.adminClass)))
    
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }

  override def additionalRemoveOperationMass(expiredLinks:Seq[String]): Unit = {
  //Todo here purge unknown speedlimit logic // if uknow links is still valid uodate reference

    service.purgeUnknown(Set(),expiredLinks)
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }

  override def additionalUpdateOrChange(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    
    
    change.changeType match {
      case RoadLinkChangeType.Replace |RoadLinkChangeType.Split => {
        //TODO update unknowSpeedlimit into new version
        Seq.empty[(PersistedLinearAsset, ChangeSet)]
      }
      case _ =>Seq.empty[(PersistedLinearAsset, ChangeSet)]
    }
  }
  
  override def filterChanges(changes: Seq[RoadLinkChange]): Seq[RoadLinkChange] = {
    val linksOther = changes.filter(_.changeType != RoadLinkChangeType.Add).map(_.oldLink.get.linkId).toSet
    // assume than in add case there is always one links.
    val linksNew = changes.filter(_.changeType == RoadLinkChangeType.Add).map(_.newLinks.head.linkId).toSet
    val links = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linksNew ++ linksOther,expiredAlso = true)
    val filteredLinks = links.filter(_.functionalClass > 4).map(_.linkId)
    val (add, other) = changes.partition(_.changeType == RoadLinkChangeType.Add)
    val filterChanges = other.filter(p => filteredLinks.contains(p.oldLink.get.linkId))
    val filterChangesNews = add.filter(p => filteredLinks.contains(p.newLinks.head.linkId))
    filterChanges ++ filterChangesNews
  }
  
  // TODO standardize speedlimit
   def updateByRoadLinks(typeId: Int, municipality: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Unit = {
  /*  val speedLimitLinks = speedLimitDao.getSpeedLimitLinksByRoadLinks(roadLinks.filter(_.isCarTrafficRoad))
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val oldRoadLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    val oldSpeedLimits = speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(oldRoadLinkIds.toSet))

    // filter road links that have already been projected to avoid projecting twice
    val speedLimitsOnChangedLinks = speedLimitLinks.filter(sl => LinearAssetUtils.newChangeInfoDetected(sl, mappedChanges))

    val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
      expiredAssetIds = oldSpeedLimits.map(_.id).toSet,
      adjustedMValues = Seq.empty[MValueAdjustment],
      adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
      adjustedSideCodes = Seq.empty[SideCodeAdjustment],
      valueAdjustments = Seq.empty[ValueAdjustment])

    val (newSpeedLimits, projectedChangeSet) = fillNewRoadLinksWithPreviousSpeedLimitData(projectableTargetRoadLinks, oldSpeedLimits ++ speedLimitsOnChangedLinks,
      speedLimitsOnChangedLinks, changes, initChangeSet, speedLimitLinks)

    val speedLimits = (speedLimitLinks ++ newSpeedLimits).groupBy(_.linkId)
    handleChangesAndUnknowns(roadLinks, speedLimits, Some(projectedChangeSet), oldRoadLinkIds, geometryChanged = true)*/
  }

/*  override def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]): Unit = {
    val changes = filterChanges(changesAll)
    val oldIds = changes.filterNot(isDeletedOrNew).map(_.oldLink.get.linkId)
    val newIds = changes.filterNot(isDeletedOrNew).flatMap(_.newLinks)
    val deletedLinks = changes.filter(isDeleted).map(_.oldLink.get.linkId)
    // here we assume that RoadLinkProperties updater has already remove override if KMTK version traffic direction is same.
    // still valid overrided has been samuuted also
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

  }*/


  
  // TODO check override
  override def adjustLinearAssetsOnChangesGeometry(roadLinks: Seq[RoadLinkForFiltopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]],
                                                   typeId: Int, changeSet: Option[ChangeSet] = None, counter: Int = 1): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val asset = linearAssets.map(p => {
      val links = roadLinks.find(_.linkId == p._1).get
      val step0 = SpeedLimitFiller.fuse(links, p._2, changeSet.get)
      val step1 = SpeedLimitFiller.adjustSegmentMValues(links, step0._1, step0._2)
      val step2 = SpeedLimitFiller.adjustLopsidedLimit(links, step1._1, step1._2)
      val step3 = SpeedLimitFiller.dropShortSegments(links, step2._1, step2._2)
      val step4 = SpeedLimitFiller.adjustAssets(links, step3._1, step3._2)
      val step5 = SpeedLimitFiller.expireOverlappingSegments(links, step4._1, step4._2)
      val step6 = SpeedLimitFiller.droppedSegmentWrongDirection(links, step5._1, step5._2)
      val step7 = adjustSegmentSideCodes(links, step6._1, step6._2)
      val step8 = SpeedLimitFiller.fillHoles(links, step7._1, step7._2)
      val step9 = SpeedLimitFiller.clean(links, step8._1, step8._2)
      
      // TODO Check for small 0.001 wholes fill theses
      step9._2.copy(adjustedMValues = step9._2.adjustedMValues.filterNot(p => step9._2.droppedAssetIds.contains(p.assetId)))
      step9
    }).toSeq


    val changeSetFolded = super.foldChangeSet(asset.map(_._2), changeSet.get)
    val assetOnly = asset.flatMap(_._1)
    val roadLinksByLinkId = roadLinks.groupBy(_.linkId).mapValues(_.head)
    val newSpeedLimitsWithValue = assetOnly.filter(sl => sl.id <= 0 && sl.value.nonEmpty)
    val unknownLimits = createUnknownLimits(assetOnly, roadLinksByLinkId)

    updateChangeSet(changeSetFolded)
    (assetOnly, changeSetFolded)
  }
/*  def handleChangesAndUnknowns(topology: Seq[RoadLink], speedLimits: Map[String, Seq[PieceWiseLinearAsset]],
                               changeSet:Option[ChangeSet] = None, oldRoadLinkIds: Seq[String], geometryChanged: Boolean, counter: Int = 1): Seq[PieceWiseLinearAsset] = {
    val (filledTopology, changedSet) = fillTopology(topology.map(toRoadLinkForFiltopology), speedLimits, SpeedLimitAsset.typeId, changeSet, geometryChanged)
    val cleanedChangeSet = cleanRedundantMValueAdjustments(changedSet, speedLimits.values.flatten.toSeq).filterGeneratedAssets
    val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)
    val newSpeedLimitsWithValue = filledTopology.filter(sl => sl.id <= 0 && sl.value.nonEmpty)
    val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)

    cleanedChangeSet.isEmpty match {
      case true =>
        persistProjectedLimit(newSpeedLimitsWithValue)
        persistUnknown(unknownLimits)
        filledTopology
      case false if counter > 3 =>
        updateChangeSet(cleanedChangeSet)
        persistProjectedLimit(newSpeedLimitsWithValue)
        purgeUnknown(cleanedChangeSet.adjustedMValues.map(_.linkId).toSet, oldRoadLinkIds)
        persistUnknown(unknownLimits)
        filledTopology
      case false if counter <= 3 =>
        updateChangeSet(cleanedChangeSet)
        purgeUnknown(cleanedChangeSet.adjustedMValues.map(_.linkId).toSet, oldRoadLinkIds)
        val speedLimitsToAdjust = filledTopology.filterNot(speedLimit => speedLimit.id <= 0 && speedLimit.value.isEmpty).groupBy(_.linkId)
        handleChangesAndUnknowns(topology, speedLimitsToAdjust, None ,oldRoadLinkIds, geometryChanged, counter + 1)
    }
  }*/

  

  override def updateChangeSet(changeSet: ChangeSet) : Unit = {
    speedLimitDao.floatLinearAssets(changeSet.droppedAssetIds)

    if (changeSet.adjustedMValues.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedMValues.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

    changeSet.adjustedMValues.foreach { adjustment =>
      speedLimitDao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
    }

    if (changeSet.adjustedVVHChanges.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

    changeSet.adjustedVVHChanges.foreach { adjustment =>
      speedLimitDao.updateMValuesChangeInfo(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure), adjustment.timeStamp, AutoGeneratedUsername.generatedInUpdate)
    }

    //NOTE the order between expire and sideCode adjustment cant be changed
    if (changeSet.expiredAssetIds.toSeq.nonEmpty)
      logger.info("Expiring ids " + changeSet.expiredAssetIds.toSeq.mkString(", "))
    changeSet.expiredAssetIds.toSeq.foreach(speedLimitDao.updateExpiration(_, expired = true, AutoGeneratedUsername.generatedInUpdate))

    if (changeSet.adjustedSideCodes.nonEmpty)
      logger.info("Side Code adjustments ids " + changeSet.adjustedSideCodes.map(a => "" + a.assetId + "/" + a.sideCode).mkString(", "))

    changeSet.adjustedSideCodes.foreach { adjustment =>
      adjustedSideCode(adjustment)
    }
  }

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    val (newlimits, changedlimits) = newLinearAssets.partition(_.id <= 0)
    newlimits.foreach { limit =>

      speedLimitDao.createSpeedLimit(limit.createdBy.getOrElse(AutoGeneratedUsername.generatedInUpdate), limit.linkId, Measures(limit.startMeasure, limit.endMeasure).roundMeasures(),
        SideCode(limit.sideCode), service.getSpeedLimitValue(limit.value).get, Some(limit.timeStamp), limit.createdDateTime, limit.modifiedBy,
        limit.modifiedDateTime, limit.linkSource)
    }
    purgeUnknown(newLinearAssets.map(_.linkId).toSet, Seq())

  }
/*  def persistProjectedLimit(limits: Seq[PieceWiseLinearAsset]): Unit = {
    val (newlimits, changedlimits) = limits.partition(_.id <= 0)
    newlimits.foreach { limit =>

      speedLimitDao.createSpeedLimit(limit.createdBy.getOrElse(AutoGeneratedUsername.generatedInUpdate), limit.linkId, Measures(limit.startMeasure, limit.endMeasure),
        limit.sideCode, service.getSpeedLimitValue(limit.value).get, Some(limit.timeStamp), limit.createdDateTime, limit.modifiedBy,
        limit.modifiedDateTime, limit.linkSource)
    }
    purgeUnknown(limits.map(_.linkId).toSet, Seq())

  }*/

  def purgeUnknown(linkIds: Set[String], expiredLinkIds: Seq[String]): Unit = {
    val roadLinks = roadLinkService.fetchRoadlinksByIds(linkIds)
    roadLinks.foreach { rl =>
      speedLimitDao.purgeFromUnknownSpeedLimits(rl.linkId, GeometryUtils.geometryLength(rl.geometry))
    }
    if (expiredLinkIds.nonEmpty)
      speedLimitDao.deleteUnknownSpeedLimits(expiredLinkIds)
  }

  def persistUnknown(limits: Seq[UnknownSpeedLimit]): Unit = {
    speedLimitDao.persistUnknownSpeedLimits(limits)
  }
  
  protected def createUnknownLimits(speedLimits: Seq[PieceWiseLinearAsset], roadLinksByLinkId: Map[String, RoadLinkForFiltopology]): Seq[UnknownSpeedLimit] = {
    val generatedLimits = speedLimits.filter(speedLimit => speedLimit.id == 0 && speedLimit.value.isEmpty)
    generatedLimits.map { limit =>
      val roadLink = roadLinksByLinkId(limit.linkId)
      UnknownSpeedLimit(roadLink.linkId, roadLink.municipalityCode, roadLink.administrativeClass)
    }
  }

  override def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldSpeedLimit = service.getPersistedSpeedLimitById(adjustment.assetId, newTransaction = false).getOrElse(throw new IllegalStateException("Asset no longer available"))

    service.expireAsset(SpeedLimitAsset.typeId, oldSpeedLimit.id, AutoGeneratedUsername.generatedInUpdate,true,  false)
    val newId = service.createWithoutTransaction(Seq(NewLimit(oldSpeedLimit.linkId, oldSpeedLimit.startMeasure, oldSpeedLimit.endMeasure)), service.getSpeedLimitValue(oldSpeedLimit.value).get, AutoGeneratedUsername.generatedInUpdate, adjustment.sideCode)
    logger.info("SideCodeAdjustment newID" + newId)
  }

  override def projectLinearAsset(asset: PersistedLinearAsset, to: LinkAndLength, projection: Projection, changedSet: ChangeSet, digitizationChanges: Boolean): (PersistedLinearAsset, ChangeSet) = {
    val newLinkId = to.linkId
    val assetId = asset.linkId match {
      case to.linkId => asset.id
      case _ => 0
    }
    val (newStart, newEnd, newSideCode) = MValueCalculator.calculateNewMValues(AssetLinearReference(asset.id, asset.startMeasure, asset.endMeasure, asset.sideCode), projection, to.length, digitizationChanges)

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
