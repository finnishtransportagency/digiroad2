package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{MmlNls, UnknownLinkType}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset.{DynamicValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, LinearAssetTypes, Measures}

class DynamicLinearAssetUpdateProcess(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl){

  def updateDynamicLinearAssets(typeId: Int) = {
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

      val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
      val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
      val existingAssets = fetchExistingAssetsByLinksIds(typeId, roadLinks, removedLinkIds)

      val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))

      val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

      val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
        expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet.filterNot(_ == 0L),
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
        adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])

      val (projectedAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
        assetsOnChangedLinks, assetsOnChangedLinks, changes, initChangeSet, existingAssets)

      val groupedAssets = (assetsOnChangedLinks.filterNot(a => projectedAssets.exists(_.linkId == a.linkId)) ++ projectedAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
      val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))

      updateChangeSet(changeSet)
      persistProjectedLinearAssets(projectedAssets.filter(_.id == 0))
      logger.info(s"Updated asset $typeId in municipality $municipality.")
      filledTopology
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
      val persisted = dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(toUpdate.map(_.id).toSet).groupBy(_.id)
      updateProjected(toUpdate, persisted, roadLinks)

      if (newLinearAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
    }

    toInsert.foreach { linearAsset =>
      val roadLink = roadLinks.find(_.linkId == linearAsset.linkId)

      val id = dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
        Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), linearAsset.vvhTimeStamp, getLinkSource(roadLink), informationSource = Some(MmlNls.value))
      linearAsset.value match {
        case Some(DynamicValue(multiTypeProps)) =>
          val props = setDefaultAndFilterProperties(multiTypeProps, roadLink, linearAsset.typeId)
          validateRequiredProperties(linearAsset.typeId, props)
          dynamicLinearAssetDao.updateAssetProperties(id, props, linearAsset.typeId)
        case _ => None
      }
    }
    if (newLinearAssets.nonEmpty)
      logger.info("Added assets for linkids " + toInsert.map(_.linkId))
  }
}
