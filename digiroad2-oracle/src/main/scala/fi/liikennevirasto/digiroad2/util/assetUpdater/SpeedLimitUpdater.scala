package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeType}
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.{FunctionalClassDao, TrafficDirectionDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}

class SpeedLimitUpdater(service: SpeedLimitService) extends DynamicLinearAssetUpdater(service) {

  val speedLimitDao = service.speedLimitDao

  override def assetFiller: AssetFiller = SpeedLimitFiller
  
  override def operationForNewLink(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    service.persistUnknown(Seq(UnknownSpeedLimit(change.newLinks.head.linkId, change.newLinks.head.municipality, change.newLinks.head.adminClass)),newTransaction = false)
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }

  override def additionalRemoveOperationMass(expiredLinks:Seq[String]): Unit = {
    service.purgeUnknown(Set(),expiredLinks,newTransaction = false)
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }

  override def additionalUpdateOrChange(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    change.changeType match {
      // if links become two directional rise into  unknow Speed limit worklist
      case RoadLinkChangeType.Replace |RoadLinkChangeType.Split => {
        val oldLinkIds = change.oldLink.get.linkId
        val trafficDirectionChanged = isRealTrafficDirectionChange(change)
        val oldUnknownLSpeedLimit = service.getUnknownByLinkIds(Set(oldLinkIds),newTransaction = false)
        if (oldUnknownLSpeedLimit.nonEmpty || trafficDirectionChanged) {
          service.purgeUnknown(Set(),oldUnknownLSpeedLimit.map(_.linkId),newTransaction = false) // recreate unknown speedlimit
          service.persistUnknown(change.newLinks.map(l=>UnknownSpeedLimit(l.linkId, l.municipality, l.adminClass)),newTransaction = false)
        }
        Seq.empty[(PersistedLinearAsset, ChangeSet)]
      }
      case _ =>Seq.empty[(PersistedLinearAsset, ChangeSet)]
    }
  }
  
  def isRealTrafficDirectionChange(change: RoadLinkChange): Boolean = {
    change.newLinks.exists(newLink => {
      val oldOriginalTrafficDirection = change.oldLink.get.trafficDirection
      val newOriginalTrafficDirection = newLink.trafficDirection
      val replaceInfo = change.replaceInfo.find(_.newLinkId == newLink.linkId).get
      val isDigitizationChange = replaceInfo.digitizationChange
      val overWrittenTdValueOnNewLink = TrafficDirectionDao.getExistingValue(newLink.linkId)

      if (overWrittenTdValueOnNewLink.nonEmpty) false
      else {
        if (isDigitizationChange) oldOriginalTrafficDirection != TrafficDirection.switch(newOriginalTrafficDirection)
        else oldOriginalTrafficDirection != newOriginalTrafficDirection
      }
    })
  }
  
  override def filterChanges(changes: Seq[RoadLinkChange]): Seq[RoadLinkChange] = {
    val linksOther = changes.filter(_.changeType != RoadLinkChangeType.Add).map(_.oldLink.get.linkId).toSet
    // assume than in add case there is always one links.
    val linksNew = changes.filter(_.changeType == RoadLinkChangeType.Add).map(_.newLinks.head.linkId).toSet
    val links =  FunctionalClassDao.getExistingValues((linksNew ++ linksOther).toSeq)
    val filteredLinks = links.filter(_.value.get > 4).map(_.linkId)
    val (add, other) = changes.partition(_.changeType == RoadLinkChangeType.Add)
    val filterChanges = other.filter(p => filteredLinks.contains(p.oldLink.get.linkId))
    val filterChangesNews = add.filter(p => filteredLinks.contains(p.newLinks.head.linkId))
    filterChanges ++ filterChangesNews
  }
  
  override def adjustLinearAssetsOnChangesGeometry(roadLinks: Seq[RoadLinkForFiltopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]],
                                                   typeId: Int, changeSet: Option[ChangeSet] = None, counter: Int = 1): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (assetOnly, filterExpiredAway) = assetFiller.fillTopologyChangesGeometry(roadLinks, linearAssets, typeId, changeSet)
    updateChangeSet(filterExpiredAway)
    (assetOnly, filterExpiredAway)
  }
  
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
// create service level method ?
      speedLimitDao.createSpeedLimit(limit.createdBy.getOrElse(AutoGeneratedUsername.generatedInUpdate), limit.linkId, Measures(limit.startMeasure, limit.endMeasure).roundMeasures(),
        SideCode(limit.sideCode), service.getSpeedLimitValue(limit.value).get, Some(limit.timeStamp), limit.createdDateTime, limit.modifiedBy,
        limit.modifiedDateTime, limit.linkSource)
    }
    service.purgeUnknown(newLinearAssets.map(_.linkId).toSet, Seq())

  }
  
  override def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldSpeedLimit = service.getPersistedSpeedLimitById(adjustment.assetId, newTransaction = false).getOrElse(throw new IllegalStateException("Asset no longer available"))

    service.expireAsset(SpeedLimitAsset.typeId, oldSpeedLimit.id, AutoGeneratedUsername.generatedInUpdate,true,  false)
    val newId = service.createWithoutTransaction(Seq(NewLimit(oldSpeedLimit.linkId, oldSpeedLimit.startMeasure, oldSpeedLimit.endMeasure)), service.getSpeedLimitValue(oldSpeedLimit.value).get, AutoGeneratedUsername.generatedInUpdate, adjustment.sideCode)
    logger.info("SideCodeAdjustment newID" + newId)
  }
}
