package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.asset.UnknownLinkType
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment, ValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, SpeedLimit, SpeedLimitFiller, SpeedLimitValue, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures, SpeedLimitService}

class SpeedLimitUpdateProcess(eventbusImpl: DigiroadEventBus, vvhClient: VVHClient, roadLinkServiceImpl: RoadLinkService) extends SpeedLimitService(eventbusImpl, vvhClient, roadLinkServiceImpl){

  def updateSpeedLimits() = {
    withDynTransaction {
      val municipalities = Queries.getMunicipalities
      municipalities.foreach { municipality =>
        val (roadLinks, changes) = roadLinkServiceImpl.getRoadLinksAndChangesFromVVHByMunicipality(municipality)
        updateByRoadLinks(municipality, roadLinks, changes)
      }
    }
  }

  def updateByRoadLinks(municipality: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) = {
    val (speedLimitLinks, topology) = dao.getSpeedLimitLinksByRoadLinks(roadLinks.filter(_.isCarTrafficRoad))
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val oldRoadLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    val oldSpeedLimits = dao.getCurrentSpeedLimitsByLinkIds(Some(oldRoadLinkIds.toSet))

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
    val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits, Some(projectedChangeSet))

    val newSpeedLimitsWithValue = filledTopology.filter(sl => sl.id <= 0 && sl.value.nonEmpty)
    // Expire all assets that are dropped or expired. No more floating speed limits.
    updateChangeSet(changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ changeSet.droppedAssetIds, droppedAssetIds = Set()))
    persistProjectedLimit(newSpeedLimitsWithValue)
    purgeUnknown(changeSet.adjustedMValues.map(_.linkId).toSet, oldRoadLinkIds)
    val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)
    persistUnknown(unknownLimits)
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

    //NOTE the order between expire and sideCode adjustment can't be changed
    if (changeSet.expiredAssetIds.toSeq.nonEmpty)
      logger.info("Expiring ids " + changeSet.expiredAssetIds.toSeq.mkString(", "))
    changeSet.expiredAssetIds.toSeq.foreach(dao.updateExpiration(_, expired = true, LinearAssetTypes.VvhGenerated))

    if (changeSet.adjustedSideCodes.nonEmpty)
      logger.info("Side Code adjustments ids " + changeSet.adjustedSideCodes.map(a => "" + a.assetId + "/" + a.sideCode).mkString(", "))

    changeSet.adjustedSideCodes.foreach { adjustment =>
      adjustedSideCode(adjustment)
    }
  }

  override def persistProjectedLimit(limits: Seq[SpeedLimit]): Unit = {
    val (newlimits, changedlimits) = limits.partition(_.id <= 0)
    newlimits.foreach { limit =>
      dao.createSpeedLimit(limit.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), limit.linkId, Measures(limit.startMeasure, limit.endMeasure),
        limit.sideCode, SpeedLimitValue(limit.value.get.value, limit.value.get.isSuggested), Some(limit.vvhTimeStamp), limit.createdDateTime, limit.modifiedBy,
        limit.modifiedDateTime, limit.linkSource)
    }
    purgeUnknown(limits.map(_.linkId).toSet, Seq())

  }

  override def purgeUnknown(linkIds: Set[Long], expiredLinkIds: Seq[Long]): Unit = {
    val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(linkIds)
    roadLinks.foreach { rl =>
      dao.purgeFromUnknownSpeedLimits(rl.linkId, GeometryUtils.geometryLength(rl.geometry))
    }
    if (expiredLinkIds.nonEmpty)
      dao.deleteUnknownSpeedLimits(expiredLinkIds)
  }

  override def persistUnknown(limits: Seq[UnknownSpeedLimit]): Unit = {
    dao.persistUnknownSpeedLimits(limits)
  }
}
