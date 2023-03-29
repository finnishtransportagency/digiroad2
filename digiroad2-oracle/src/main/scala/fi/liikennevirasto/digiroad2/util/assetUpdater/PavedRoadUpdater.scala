package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.{PavedRoad, UnknownLinkType}
import fi.liikennevirasto.digiroad2.client.RoadLinkChange
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils

class PavedRoadUpdater(service: PavedRoadService) extends DynamicLinearAssetUpdater(service) {


  override def operationForNewLink(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    // TODO here logic to generate paved road
    // hint getPavedRoadAssetChanges
    
    Seq.empty[(PersistedLinearAsset, ChangeSet)]
  }
  // TODO remove this method
  def updateByRoadLinks(typeId: Int, municipality: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) = {
/*    try {
      val linkIds = roadLinks.map(_.linkId)
      val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
      val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)
      val existingAssets = dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(PavedRoad.typeId, linkIds ++ removedLinkIds).filterNot(_.expired)

      val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))

      val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)
      val (expiredIds, newAndUpdatedPavedRoadAssets) = service.getPavedRoadAssetChanges(existingAssets, roadLinks, changes, PavedRoad.typeId)

      val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
        expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet ++ expiredIds,
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = newAndUpdatedPavedRoadAssets.filter(_.id != 0).map(a => VVHChangesAdjustment(a.id, a.linkId, a.startMeasure, a.endMeasure, a.timeStamp)),
        adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])

      val combinedAssets = existingAssets.filterNot(a => expiredIds.contains(a.id) || newAndUpdatedPavedRoadAssets.exists(_.id == a.id) || assetsWithoutChangedLinks.exists(_.id == a.id)
      ) ++ newAndUpdatedPavedRoadAssets

      val (projectedAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks, combinedAssets, assetsOnChangedLinks, changes, initChangeSet, existingAssets)

      val newAssets = newAndUpdatedPavedRoadAssets.filterNot(a => projectedAssets.exists(f => f.linkId == a.linkId)) ++ projectedAssets

      val groupedAssets = assetFiller.toLinearAssetsOnMultipleLinks((existingAssets.filterNot(a => expiredIds.contains(a.id) || newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks), roadLinks).groupBy(_.linkId)
      adjustLinearAssetsOnChangesGeometry(roadLinks, groupedAssets, typeId, Some(changedSet))
      persistProjectedLinearAssets(newAssets.filter(_.id == 0))
      logger.info(s"Updated paved roads in municipality $municipality.")
    } catch {
      case e => logger.error(s"Updating paved roads in municipality $municipality failed due to ${e.getMessage}.")
    }*/
  }

}
