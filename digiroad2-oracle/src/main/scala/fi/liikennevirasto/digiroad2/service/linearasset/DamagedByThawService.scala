package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils

class DamagedByThawService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {

  override def enrichPersistedLinearAssetProperties(persistedLinearAsset: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val assetIds = persistedLinearAsset.map(_.id)
    if (assetIds.nonEmpty) {
      val properties = dynamicLinearAssetDao.getDatePeriodPropertyValue(assetIds.toSet, persistedLinearAsset.head.typeId)
      enrichWithProperties(properties, persistedLinearAsset)
    } else {
      Seq.empty[PersistedLinearAsset]
    }
  }

  override def getAssetsByMunicipality(typeId: Int, municipality: Int, newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementaryAndChanges(municipality)
    val linkIds = roadLinks.map(_.linkId)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    if(newTransaction) withDynTransaction {
      dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds)
    } else dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds)
  }

}
