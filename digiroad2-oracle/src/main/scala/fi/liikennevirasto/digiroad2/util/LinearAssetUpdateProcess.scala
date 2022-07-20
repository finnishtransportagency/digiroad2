package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{EuropeanRoads, ExitNumbers, NumberOfLanes, TrafficVolume, UnknownLinkType, WinterSpeedLimit}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment, ValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink, TextualValue, Value}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, LinearAssetTypes, Measures}

class LinearAssetUpdateProcess(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetService(roadLinkServiceImpl, eventBusImpl) {

  def updateLinearAssets(typeId: Int) = {
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

      val timing = System.currentTimeMillis
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

      val newAssets = projectedAssets ++ assetsWithoutChangedLinks

      if (newAssets.nonEmpty) {
        logger.info("Finish transfer %d assets at %d ms after start".format(newAssets.length, System.currentTimeMillis - timing))
      }
      val groupedAssets = (assetsOnChangedLinks.filterNot(a => projectedAssets.exists(_.linkId == a.linkId)) ++ projectedAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
      val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))

      updateChangeSet(changeSet)
      persistProjectedLinearAssets(projectedAssets.filter(_.id == 0L))
    } catch {
      case e => logger.error(s"Updating asset $typeId in municipality $municipality failed due to ${e.getMessage}.")
    }
  }

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
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

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
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
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
              getLinkSource(roadlink), fromUpdate = true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate, geometry = getGeometry(roadlink))
          case _ =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
              getLinkSource(roadlink), geometry = getGeometry(roadlink))
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
}
