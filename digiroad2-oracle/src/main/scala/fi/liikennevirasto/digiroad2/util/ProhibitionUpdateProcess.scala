package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{HazmatTransportProhibition, Prohibition, UnknownLinkType}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment, ValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures, ProhibitionService}

class ProhibitionUpdateProcess(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends ProhibitionService(roadLinkServiceImpl, eventBusImpl) {

  def updateProhibitions(typeId: Int) = {
    withDynTransaction {
      val municipalities = Queries.getMunicipalities
      municipalities.foreach { municipality =>
        val (roadLinks, changes) = roadLinkService.getRoadLinksAndChangesFromVVHByMunicipality(municipality)
        updateByRoadLinks(typeId, municipality, roadLinks, changes)
      }
    }
  }

  def updateByRoadLinks(typeId: Int, municipality: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) = {
    try {
      val linkIds = roadLinks.map(_.linkId)
      val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
      val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)
      val existingAssets = dao.fetchProhibitionsByLinkIds(Prohibition.typeId, linkIds ++ removedLinkIds, includeFloating = false).filterNot(_.expired)

      val timing = System.currentTimeMillis

      val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))
      val projectableTargetRoadLinks = roadLinks.filter(
        rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

      val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
        expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet.filterNot(_ == 0L),
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
        adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])

      val combinedAssets = existingAssets.filterNot(a => assetsWithoutChangedLinks.exists(_.id == a.id))

      val (newAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
        combinedAssets, assetsOnChangedLinks, changes, initChangeSet, existingAssets)

      if (newAssets.nonEmpty) {
        logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
      }
      val groupedAssets = (existingAssets.filterNot(a => newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
      val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))
      updateChangeSet(changeSet)

      if (typeId == HazmatTransportProhibition.typeId) {
        persistProjectedHazMatAsset(newAssets.filter(_.id == 0))
      } else {
        persistProjectedLinearAssets(newAssets.filter(_.id == 0))
      }

      logger.info(s"Updated asset $typeId in municipality $municipality.")
    } catch {
      case e => logger.error(s"Updating asset $typeId in municipality $municipality failed due to ${e.getMessage}.")
    }
  }

  override def updateChangeSet(changeSet: ChangeSet) : Unit = {
    dao.floatLinearAssets(changeSet.droppedAssetIds)

    if (changeSet.adjustedMValues.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedMValues.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

    changeSet.adjustedMValues.foreach { adjustment =>
      dao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
    }

    if (changeSet.adjustedVVHChanges.nonEmpty)
      logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

    changeSet.adjustedVVHChanges.foreach { adjustment =>
      dao.updateMValuesChangeInfo(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure), adjustment.vvhTimestamp, LinearAssetTypes.VvhGenerated)
    }
    val ids = changeSet.expiredAssetIds.toSeq
    if (ids.nonEmpty)
      logger.info("Expiring ids " + ids.mkString(", "))
    ids.foreach(dao.updateExpiration(_, expired = true, LinearAssetTypes.VvhGenerated))

    if (changeSet.adjustedSideCodes.nonEmpty)
      logger.info("Saving SideCode adjustments for asset/link ids=" + changeSet.adjustedSideCodes.map(a => "" + a.assetId).mkString(", "))

    changeSet.adjustedSideCodes.foreach { adjustment =>
      adjustedSideCode(adjustment)
    }

    if (changeSet.valueAdjustments.nonEmpty)
      logger.info("Saving value adjustments for assets: " + changeSet.valueAdjustments.map(a => "" + a.asset.id).mkString(", "))
    changeSet.valueAdjustments.foreach { adjustment =>
      updateWithoutTransaction(Seq(adjustment.asset.id), adjustment.asset.value.get, adjustment.asset.modifiedBy.get)

    }
  }

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
    if (toUpdate.nonEmpty) {
      val prohibitions = toUpdate.filter(a => Set(Prohibition.typeId).contains(a.typeId))
      val persisted = dao.fetchProhibitionsByIds(Prohibition.typeId, prohibitions.map(_.id).toSet).groupBy(_.id)
      updateProjected(toUpdate, persisted)
      if (newLinearAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
    }
    toInsert.foreach { linearAsset =>
      val id =
        (linearAsset.createdBy, linearAsset.createdDateTime) match {
          case (Some(createdBy), Some(createdDateTime)) =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
              getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)), true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate)
          case _ =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp, getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)))
        }
      linearAsset.value match {
        case Some(prohibitions: Prohibitions) =>
          dao.insertProhibitionValue(id, Prohibition.typeId, prohibitions)
        case _ => None
      }
    }
    if (newLinearAssets.nonEmpty)
      logger.info("Added assets for linkids " + toInsert.map(_.linkId))
  }

  private def persistProjectedHazMatAsset(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected prohibition assets")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
    if (toUpdate.nonEmpty) {
      val prohibitions = toUpdate.filter(a => Set(HazmatTransportProhibition.typeId).contains(a.typeId))
      val persisted = dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, prohibitions.map(_.id).toSet).groupBy(_.id)
      updateProjected(toUpdate, persisted)
      if (newLinearAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
    }
    toInsert.foreach { linearAsset =>
      val id =
        (linearAsset.createdBy, linearAsset.createdDateTime) match {
          case (Some(createdBy), Some(createdDateTime)) =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
              getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)), true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate)
          case _ =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp, getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)))
        }
      linearAsset.value match {
        case Some(prohibitions: Prohibitions) =>
          dao.insertProhibitionValue(id, HazmatTransportProhibition.typeId, prohibitions)
        case _ => None
      }
    }
    if (newLinearAssets.nonEmpty)
      logger.info("Added assets for linkids " + toInsert.map(_.linkId))
  }
}
